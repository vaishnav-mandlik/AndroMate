package com.mandlik.andromate

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mandlik.andromate.service.AndroMateAccessibilityService
import com.mandlik.andromate.service.AndroMateService

class MainActivity : AppCompatActivity() {

    // Handler to refresh status every second (like setInterval in JS)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var statusChecker: Runnable

    // UI elements (like useRef in React Native)
    private lateinit var dotAccessibility: View
    private lateinit var dotEngine: View
    private lateinit var dotBattery: View
    private lateinit var textAccessibilityStatus: TextView
    private lateinit var textEngineStatus: TextView
    private lateinit var textBatteryStatus: TextView
    private lateinit var textOutput: TextView
    private lateinit var labelOutput: TextView
    private lateinit var checkAccessibility: TextView
    private lateinit var checkEngine: TextView
    private lateinit var checkBattery: TextView
    private lateinit var textAllDone: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Hide the action bar for clean look
        supportActionBar?.hide()

        // Set the layout (like returning JSX in React Native)
        setContentView(R.layout.activity_main)

        // Find all UI elements by their ID (like document.getElementById)
        dotAccessibility = findViewById(R.id.dot_accessibility)
        dotEngine = findViewById(R.id.dot_engine)
        dotBattery = findViewById(R.id.dot_battery)
        textAccessibilityStatus = findViewById(R.id.text_accessibility_status)
        textEngineStatus = findViewById(R.id.text_engine_status)
        textBatteryStatus = findViewById(R.id.text_battery_status)
        textOutput = findViewById(R.id.text_output)
        labelOutput = findViewById(R.id.label_output)
        checkAccessibility = findViewById(R.id.check_accessibility)
        checkEngine = findViewById(R.id.check_engine)
        checkBattery = findViewById(R.id.check_battery)
        textAllDone = findViewById(R.id.text_all_done)

        // ═══════════════════════════════════════
        // SET UP CLICK HANDLERS (like onPress in RN)
        // ═══════════════════════════════════════

        // Tap Accessibility Card → open Settings
        findViewById<LinearLayout>(R.id.card_accessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // Tap Engine Card → start/stop service
        findViewById<LinearLayout>(R.id.card_engine).setOnClickListener {
            if (!AndroMateService.isRunning) {
                AndroMateService.start(this)
                Toast.makeText(this, "Engine started", Toast.LENGTH_SHORT).show()
            } else {
                AndroMateService.stop(this)
                Toast.makeText(this, "Engine stopped", Toast.LENGTH_SHORT).show()
            }
        }

        // Tap Battery Card → open battery settings
        findViewById<LinearLayout>(R.id.card_battery).setOnClickListener {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }

        // ── Test Action Buttons ──

        // Read Screen Content
        findViewById<Button>(R.id.btn_read_screen).setOnClickListener {
            val service = AndroMateAccessibilityService.instance
            if (service != null) {
                val content = service.getScreenContent()
                showOutput(
                    if (content.isEmpty()) {
                        "(Screen is empty — try opening another app first, then come back and tap this)"
                    } else {
                        content
                    }
                )
            } else {
                showOutput("⚠️ Accessibility Service not connected. Enable it first!")
            }
        }

        // Press Home
        findViewById<Button>(R.id.btn_home).setOnClickListener {
            AndroMateAccessibilityService.instance?.pressHome()
                ?: Toast.makeText(this, "Enable Accessibility first", Toast.LENGTH_SHORT).show()
        }

        // Press Back
        findViewById<Button>(R.id.btn_back).setOnClickListener {
            AndroMateAccessibilityService.instance?.pressBack()
                ?: Toast.makeText(this, "Enable Accessibility first", Toast.LENGTH_SHORT).show()
        }

        // Get Current App
        findViewById<Button>(R.id.btn_current_app).setOnClickListener {
            val pkg = AndroMateAccessibilityService.instance?.getCurrentPackage()
            showOutput("Current app: ${pkg ?: "unknown (enable Accessibility first)"}")
        }

        // Open Notifications
        findViewById<Button>(R.id.btn_notifications).setOnClickListener {
            AndroMateAccessibilityService.instance?.openNotifications()
                ?: Toast.makeText(this, "Enable Accessibility first", Toast.LENGTH_SHORT).show()
        }

        // ═══════════════════════════════════════
        // STATUS CHECKER — runs every 1 second
        // (like setInterval in JavaScript)
        // ═══════════════════════════════════════
        statusChecker = object : Runnable {
            override fun run() {
                updateStatus()
                handler.postDelayed(this, 1000)
            }
        }
        findViewById<Button>(R.id.btn_open_rules).setOnClickListener {
            startActivity(Intent(this, com.mandlik.andromate.rules.RulesActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(statusChecker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(statusChecker)
    }

    // ═══════════════════════════════════════
    // UPDATE STATUS — runs every second
    // ═══════════════════════════════════════

    private fun updateStatus() {
        val isAccessibilityOn = isAccessibilityServiceEnabled()
        val isEngineOn = AndroMateService.isRunning
        val isBatteryOk = isIgnoringBatteryOptimizations()

        // Update Accessibility card
        if (isAccessibilityOn) {
            dotAccessibility.setBackgroundResource(R.drawable.dot_green)
            textAccessibilityStatus.text = "Active — AndroMate can see and control apps"
            textAccessibilityStatus.setTextColor(0xFF00E5A0.toInt())
        } else {
            dotAccessibility.setBackgroundResource(R.drawable.dot_red)
            textAccessibilityStatus.text = "Off — Tap to enable in Settings"
            textAccessibilityStatus.setTextColor(0xFFFF4757.toInt())
        }

        // Update Engine card
        if (isEngineOn) {
            dotEngine.setBackgroundResource(R.drawable.dot_green)
            textEngineStatus.text = "Running — AndroMate stays alive 24/7"
            textEngineStatus.setTextColor(0xFF00E5A0.toInt())
        } else {
            dotEngine.setBackgroundResource(R.drawable.dot_red)
            textEngineStatus.text = "Off — Tap to start"
            textEngineStatus.setTextColor(0xFFFF4757.toInt())
        }

        // Update Battery card
        if (isBatteryOk) {
            dotBattery.setBackgroundResource(R.drawable.dot_green)
            textBatteryStatus.text = "Unrestricted — Won't be killed in background"
            textBatteryStatus.setTextColor(0xFF00E5A0.toInt())
        } else {
            dotBattery.setBackgroundResource(R.drawable.dot_red)
            textBatteryStatus.text = "Restricted — Tap to fix (important!)"
            textBatteryStatus.setTextColor(0xFFFF4757.toInt())
        }

        // Update checklist
        checkAccessibility.text = if (isAccessibilityOn) "✅ Enable Accessibility Service" else "⬜ Enable Accessibility Service"
        checkAccessibility.setTextColor(if (isAccessibilityOn) 0xFF00E5A0.toInt() else 0xFF666680.toInt())

        checkEngine.text = if (isEngineOn) "✅ Start Background Engine" else "⬜ Start Background Engine"
        checkEngine.setTextColor(if (isEngineOn) 0xFF00E5A0.toInt() else 0xFF666680.toInt())

        checkBattery.text = if (isBatteryOk) "✅ Disable Battery Optimization" else "⬜ Disable Battery Optimization"
        checkBattery.setTextColor(if (isBatteryOk) 0xFF00E5A0.toInt() else 0xFF666680.toInt())

        // Show "All Done" banner
        textAllDone.visibility = if (isAccessibilityOn && isEngineOn && isBatteryOk) View.VISIBLE else View.GONE
    }

    // ═══════════════════════════════════════
    // HELPER FUNCTIONS
    // ═══════════════════════════════════════

    private fun showOutput(text: String) {
        labelOutput.visibility = View.VISIBLE
        textOutput.visibility = View.VISIBLE
        textOutput.text = text
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        return enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == packageName
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

}