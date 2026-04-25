package com.mandlik.andromate.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mandlik.andromate.service.AndroMateService

/**
 * BootReceiver — Auto-starts AndroMate + reschedules rules after reboot
 *
 * Android alarms DON'T survive reboots.
 * So when the phone restarts, we need to:
 *   1. Start the foreground service
 *   2. Re-schedule all saved rules
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("AndroMate", "📱 Phone booted — starting AndroMate")

            // Start the foreground service
            AndroMateService.start(context)

            // Reschedule all saved rules
            RuleScheduler.rescheduleAllRules(context)
        }
    }
}