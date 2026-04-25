package com.mandlik.andromate.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mandlik.andromate.rules.ActionExecutor
import com.mandlik.andromate.rules.AndroMateDatabase

/**
 * RuleAlarmReceiver — Fires when a scheduled rule's time arrives
 *
 * Flow:
 *   1. User creates rule: "Play Spotify at 6 AM daily"
 *   2. We schedule an alarm using AlarmManager for 6 AM
 *   3. At 6 AM, Android wakes up this receiver
 *   4. We look up the rule in the database
 *   5. We call ActionExecutor to run the action
 *   6. If it's a recurring rule, we schedule the next alarm
 *
 * Like a cron job, but on Android.
 */
class RuleAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_RULE_ID = "rule_id"
        private const val TAG = "AndroMate"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val ruleId = intent.getLongExtra(EXTRA_RULE_ID, -1)

        if (ruleId == -1L) {
            Log.e(TAG, "❌ RuleAlarmReceiver: No rule ID in intent")
            return
        }

        Log.d(TAG, "⏰ Alarm fired for rule ID: $ruleId")

        // Run on a background thread (database access can't be on main thread)
        Thread {
            try {
                val db = AndroMateDatabase.getInstance(context)
                val rule = db.ruleDao().getRuleById(ruleId)

                if (rule == null) {
                    Log.e(TAG, "❌ Rule not found: $ruleId")
                    return@Thread
                }

                if (!rule.enabled) {
                    Log.d(TAG, "⏸️ Rule is disabled, skipping: ${rule.name}")
                    return@Thread
                }

                // Execute the action!
                ActionExecutor.execute(context, rule)

                // If recurring, schedule the next alarm
                if (rule.triggerRepeat == "DAILY" || rule.triggerRepeat == "WEEKLY") {
                    RuleScheduler.scheduleRule(context, rule)
                    Log.d(TAG, "🔄 Rescheduled recurring rule: ${rule.name}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error executing rule: ${e.message}", e)
            }
        }.start()
    }
}