package com.mandlik.andromate.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mandlik.andromate.MainActivity
import com.mandlik.andromate.R

/**
 * AndroMateService — Keeps AndroMate alive 24/7
 *
 * Android kills background apps aggressively.
 * A ForegroundService with a persistent notification
 * tells Android "this app is important, don't kill it."
 *
 * This is how Spotify keeps playing and Google Maps
 * keeps navigating — same technique.
 */
class AndroMateService : Service() {

    companion object {
        private const val TAG = "AndroMate"
        private const val CHANNEL_ID = "andromate_channel"
        private const val NOTIFICATION_ID = 1

        var isRunning = false
            private set

        /**
         * Start the foreground service
         */
        fun start(context: Context) {
            val intent = Intent(context, AndroMateService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Stop the foreground service
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, AndroMateService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "✅ AndroMateService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        isRunning = true
        Log.d(TAG, "✅ AndroMateService running in foreground")

        // START_STICKY = Android will restart the service if it gets killed
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        Log.d(TAG, "❌ AndroMateService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ─────────────────────────────────────────────
    // NOTIFICATION — Required for foreground service
    // ─────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AndroMate Engine",
                NotificationManager.IMPORTANCE_LOW  // Low = no sound, just shows in bar
            ).apply {
                description = "Keeps AndroMate running in the background"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        // Tap notification → open the app
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AndroMate is running")
            .setContentText("Monitoring your automations")
            .setSmallIcon(R.drawable.ic_notification)  // We'll create this icon
            .setOngoing(true)           // Can't swipe away
            .setContentIntent(openIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}