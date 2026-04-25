package com.mandlik.andromate.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * AndroMateAccessibilityService — The Eyes & Hands of AndroMate
 *
 * This is the most important class in the entire app.
 * It can:
 *   - SEE everything on any app's screen (read UI tree)
 *   - TAP any button in any app
 *   - TYPE text into any field
 *   - SCROLL any list
 *   - SWIPE in any direction
 *   - PRESS back, home, recents
 *
 * No root needed. User enables it once in Settings > Accessibility.
 */
class AndroMateAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AndroMate"

        // Singleton instance so other parts of the app can call it
        var instance: AndroMateAccessibilityService? = null
            private set

        /**
         * Check if the service is running and ready
         */
        fun isRunning(): Boolean = instance != null
    }

    // ─────────────────────────────────────────────
    // LIFECYCLE
    // ─────────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "✅ AccessibilityService connected — AndroMate is alive!")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "❌ AccessibilityService destroyed")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Called every time something changes on screen
        // (app opened, button clicked, text changed, etc.)
        // We'll use this later for habit learning & triggers
    }

    override fun onInterrupt() {
        Log.d(TAG, "⚠️ AccessibilityService interrupted")
    }

    // ─────────────────────────────────────────────
    // 👁️ EYES — Read the screen
    // ─────────────────────────────────────────────

    /**
     * Get the full UI tree of whatever is currently on screen.
     * Returns the root node that contains ALL elements.
     */
    fun getScreenRoot(): AccessibilityNodeInfo? {
        return rootInActiveWindow
    }

    /**
     * Find all elements on screen that contain specific text.
     * Example: findByText("Send") → finds all "Send" buttons
     */
    fun findByText(text: String): List<AccessibilityNodeInfo> {
        val root = rootInActiveWindow ?: return emptyList()
        val nodes = root.findAccessibilityNodeInfosByText(text)
        return nodes?.toList() ?: emptyList()
    }

    /**
     * Find elements by their View ID (for apps with known IDs).
     * Example: findById("com.whatsapp:id/send") → WhatsApp send button
     */
    fun findById(viewId: String): List<AccessibilityNodeInfo> {
        val root = rootInActiveWindow ?: return emptyList()
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        return nodes?.toList() ?: emptyList()
    }

    /**
     * Get a simple text dump of everything on screen.
     * Useful for debugging and for sending to AI later.
     */
    fun getScreenContent(): String {
        val root = rootInActiveWindow ?: return "No active window"
        val builder = StringBuilder()
        dumpNode(root, builder, 0)
        return builder.toString()
    }

    /**
     * Recursively walk the UI tree and build a readable text dump.
     */
    private fun dumpNode(node: AccessibilityNodeInfo, builder: StringBuilder, depth: Int) {
        val indent = "  ".repeat(depth)
        val className = node.className?.toString()?.substringAfterLast('.') ?: "Unknown"
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val clickable = if (node.isClickable) " [CLICKABLE]" else ""
        val editable = if (node.isEditable) " [EDITABLE]" else ""

        // Only include nodes that have useful info
        if (text.isNotEmpty() || desc.isNotEmpty() || node.isClickable || node.isEditable) {
            builder.append("$indent$className")
            if (text.isNotEmpty()) builder.append(": \"$text\"")
            if (desc.isNotEmpty()) builder.append(" ($desc)")
            builder.append("$clickable$editable")
            builder.append("\n")
        }

        // Recurse into children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            dumpNode(child, builder, depth + 1)
        }
    }

    // ─────────────────────────────────────────────
    // 🖐️ HANDS — Interact with the screen
    // ─────────────────────────────────────────────

    /**
     * Tap a specific node (button, link, etc.)
     * Returns true if the tap was performed.
     */
    fun tapNode(node: AccessibilityNodeInfo): Boolean {
        // Try clicking the node directly first
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        // If node itself isn't clickable, try its parent
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) {
                return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            parent = parent.parent
        }
        return false
    }

    /**
     * Tap at specific screen coordinates (x, y).
     * Used when we can't find a clickable node (custom UIs).
     */
    fun tapAt(x: Float, y: Float, callback: ((Boolean) -> Unit)? = null) {
        val path = Path().apply {
            moveTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "✅ Tap at ($x, $y) completed")
                callback?.invoke(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.d(TAG, "❌ Tap at ($x, $y) cancelled")
                callback?.invoke(false)
            }
        }, null)
    }

    /**
     * Type text into a focused editable field.
     * First tries to set text directly, then falls back to clipboard paste.
     */
    fun typeText(node: AccessibilityNodeInfo, text: String): Boolean {
        if (!node.isEditable) return false

        // Focus the field first
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        // Set the text
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /**
     * Clear text from an editable field.
     */
    fun clearText(node: AccessibilityNodeInfo): Boolean {
        return typeText(node, "")
    }

    /**
     * Scroll a node in a direction.
     * direction: true = forward/down, false = backward/up
     */
    fun scroll(node: AccessibilityNodeInfo, forward: Boolean): Boolean {
        val action = if (forward) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        return node.performAction(action)
    }

    /**
     * Swipe from one point to another.
     * Useful for custom gestures.
     */
    fun swipe(
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        durationMs: Long = 300,
        callback: ((Boolean) -> Unit)? = null
    ) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                callback?.invoke(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                callback?.invoke(false)
            }
        }, null)
    }

    /**
     * Long press on a node.
     */
    fun longPress(node: AccessibilityNodeInfo): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
    }

    // ─────────────────────────────────────────────
    // 🌐 GLOBAL ACTIONS — System-level controls
    // ─────────────────────────────────────────────

    /** Press the Back button */
    fun pressBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    /** Press the Home button */
    fun pressHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    /** Open Recent Apps */
    fun openRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    /** Pull down notification shade */
    fun openNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    /** Open Quick Settings */
    fun openQuickSettings(): Boolean = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)

    /** Lock the screen (API 28+) */
    fun lockScreen(): Boolean = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)

    /** Take a screenshot (API 28+) */
    fun takeScreenshot(): Boolean = performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)

    // ─────────────────────────────────────────────
    // 🔧 UTILITY — Helper functions
    // ─────────────────────────────────────────────

    /**
     * Find and tap a button/element by its text.
     * This is the most common operation — find "Send" and tap it.
     * Returns true if found and tapped.
     */
    fun findAndTap(text: String): Boolean {
        val nodes = findByText(text)
        for (node in nodes) {
            if (tapNode(node)) {
                Log.d(TAG, "✅ Found and tapped: \"$text\"")
                return true
            }
        }
        Log.d(TAG, "❌ Could not find tappable element: \"$text\"")
        return false
    }

    /**
     * Find an editable field and type text into it.
     * Returns true if found and typed.
     */
    fun findAndType(fieldText: String, inputText: String): Boolean {
        val nodes = findByText(fieldText)
        for (node in nodes) {
            if (node.isEditable) {
                return typeText(node, inputText)
            }
        }
        // If no match by text, try finding any editable field
        val root = rootInActiveWindow ?: return false
        val editableNode = findFirstEditable(root)
        if (editableNode != null) {
            return typeText(editableNode, inputText)
        }
        return false
    }

    /**
     * Find the first editable field on screen.
     */
    private fun findFirstEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findFirstEditable(child)
            if (result != null) return result
        }
        return null
    }

    /**
     * Get the currently focused app's package name.
     */
    fun getCurrentPackage(): String? {
        return rootInActiveWindow?.packageName?.toString()
    }
}