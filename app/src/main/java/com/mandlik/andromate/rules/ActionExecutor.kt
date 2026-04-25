package com.mandlik.andromate.rules

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.app.NotificationManager
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import java.io.File

/**
 * ActionExecutor v0.2.1 — Now with user-facing feedback
 *
 * Changes from v0.2:
 *   - Shows Toast messages so user knows what happened
 *   - Better error handling with clear messages
 *   - DND permission check before attempting
 */
object ActionExecutor {

    private const val TAG = "AndroMate"
    private var mediaPlayer: MediaPlayer? = null

    // Handler for showing Toasts from background threads
    private val mainHandler = Handler(Looper.getMainLooper())

    fun execute(context: Context, rule: Rule) {
        Log.d(TAG, "⚡ Executing rule: ${rule.name} → ${rule.actionType}")

        try {
            when (rule.actionType) {
                "OPEN_APP" -> openApp(context, rule)
                "TOGGLE_DND" -> toggleDnd(context, rule)
                "SET_VOLUME" -> setVolume(context, rule)
                "PLAY_SPOTIFY" -> playSpotify(context, rule)
                "PLAY_LOCAL_SONG" -> playLocalSong(context, rule)
                "SEND_SMS" -> sendSms(context, rule)
                else -> showToast(context, "❌ Unknown action: ${rule.actionType}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Action failed: ${e.message}", e)
            showToast(context, "❌ Rule '${rule.name}' failed: ${e.message}")
        }
    }

    // ═══════════════════════════════════════
    // ACTIONS
    // ═══════════════════════════════════════

    private fun openApp(context: Context, rule: Rule) {
        val packageName = rule.actionData
        if (packageName.isNullOrEmpty()) {
            showToast(context, "❌ No app selected")
            return
        }
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            showToast(context, "✅ Opened ${rule.actionExtra ?: packageName}")
        } else {
            showToast(context, "❌ App not found: $packageName")
        }
    }

    private fun toggleDnd(context: Context, rule: Rule) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            showToast(context, "❌ DND permission not granted. Open AndroMate to fix.")
            return
        }
        when (rule.actionData?.uppercase()) {
            "ON" -> {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                showToast(context, "✅ DND turned ON")
            }
            "OFF" -> {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                showToast(context, "✅ DND turned OFF")
            }
        }
    }

    private fun setVolume(context: Context, rule: Rule) {
        val percentage = rule.actionData?.toIntOrNull()
        if (percentage == null || percentage < 0 || percentage > 100) {
            showToast(context, "❌ Invalid volume: ${rule.actionData}")
            return
        }
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val targetVolume = (maxVolume * percentage / 100)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
        showToast(context, "✅ Volume set to $percentage%")
    }

    private fun playSpotify(context: Context, rule: Rule) {
        val data = rule.actionData
        if (data.isNullOrEmpty()) {
            // Just open Spotify
            val intent = context.packageManager.getLaunchIntentForPackage("com.spotify.music")
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                showToast(context, "✅ Opened Spotify")
            } else {
                showToast(context, "❌ Spotify is not installed")
            }
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            this.data = Uri.parse(data)
            putExtra(Intent.EXTRA_REFERRER, Uri.parse("android-app://${context.packageName}"))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
            showToast(context, "✅ Playing Spotify")
        } catch (e: Exception) {
            showToast(context, "❌ Could not open Spotify link")
        }
    }

    private fun playLocalSong(context: Context, rule: Rule) {
        val path = rule.actionData
        if (path.isNullOrEmpty()) {
            showToast(context, "❌ No song selected")
            return
        }

        try {
            stopLocalSong()

            // Try as file path first
            val file = File(path)
            if (file.exists()) {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(path)
                    prepare()
                    start()
                }
                showToast(context, "✅ Playing ${rule.actionExtra ?: "song"}")
                return
            }

            // Try as URI (from file picker)
            val uri = Uri.parse(path)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                prepare()
                start()
            }
            showToast(context, "✅ Playing ${rule.actionExtra ?: "song"}")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to play: ${e.message}", e)
            showToast(context, "❌ Could not play song: ${e.message}")
        }
    }

    fun stopLocalSong() {
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null
    }

    private fun sendSms(context: Context, rule: Rule) {
        val phoneNumber = rule.actionData
        val message = rule.actionExtra
        if (phoneNumber.isNullOrEmpty() || message.isNullOrEmpty()) {
            showToast(context, "❌ Missing phone number or message")
            return
        }
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            showToast(context, "✅ SMS sent to $phoneNumber")
        } catch (e: Exception) {
            showToast(context, "❌ SMS failed: ${e.message}")
        }
    }

    // ═══════════════════════════════════════
    // HELPER
    // ═══════════════════════════════════════

    /**
     * Show a Toast from any thread (background or main).
     */
    private fun showToast(context: Context, message: String) {
        Log.d(TAG, message)
        mainHandler.post {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }
}