package com.mandlik.andromate.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.mandlik.andromate.rules.Rule
import java.util.Calendar

/**
 * RuleScheduler — Schedules alarms for time-based rules
 *
 * When a user creates a rule like "Play Spotify at 6 AM daily",
 * this class sets up an alarm that fires at exactly 6:00 AM.
 *
 * Uses AlarmManager.setExactAndAllowWhileIdle() which fires
 * even when the phone is in Doze mode (battery saving).
 *
 * Think of it like setTimeout/setInterval in JavaScript,
 * but it survives app close, screen off, and Doze mode.
 */
object RuleScheduler {

    private const val TAG = "AndroMate"

    /**
     * Schedule an alarm for a rule.
     * Calculates the next fire time and sets the alarm.
     */
    fun scheduleRule(context: Context, rule: Rule) {
        if (rule.triggerType != "TIME" || rule.triggerTime == null) {
            Log.e(TAG, "❌ Not a time-based rule: ${rule.name}")
            return
        }

        val triggerTimeMs = calculateNextFireTime(rule)
        if (triggerTimeMs <= System.currentTimeMillis()) {
            Log.e(TAG, "❌ Calculated fire time is in the past for: ${rule.name}")
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, RuleAlarmReceiver::class.java).apply {
            putExtra(RuleAlarmReceiver.EXTRA_RULE_ID, rule.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            rule.id.toInt(),  // Unique request code per rule
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // setExactAndAllowWhileIdle = fires at EXACT time even in Doze mode
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,  // Wake up the phone if needed
            triggerTimeMs,
            pendingIntent
        )

        val cal = Calendar.getInstance().apply { timeInMillis = triggerTimeMs }
        Log.d(TAG, "⏰ Alarm scheduled: ${rule.name} → ${cal.time}")
    }

    /**
     * Cancel a scheduled alarm for a rule.
     */
    fun cancelRule(context: Context, rule: Rule) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, RuleAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            rule.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "🚫 Alarm cancelled: ${rule.name}")
    }

    /**
     * Reschedule ALL enabled rules.
     * Called after phone reboot (alarms don't survive reboot).
     */
    fun rescheduleAllRules(context: Context) {
        Thread {
            try {
                val db = com.mandlik.andromate.rules.AndroMateDatabase.getInstance(context)
                val rules = db.ruleDao().getEnabledRules()
                for (rule in rules) {
                    if (rule.triggerType == "TIME") {
                        scheduleRule(context, rule)
                    }
                }
                Log.d(TAG, "✅ Rescheduled ${rules.size} rules after boot")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to reschedule rules: ${e.message}", e)
            }
        }.start()
    }

    /**
     * Calculate the next time this rule should fire.
     *
     * If the time already passed today, schedule for tomorrow.
     * For weekly rules, find the next matching day.
     */
    private fun calculateNextFireTime(rule: Rule): Long {
        val timeParts = rule.triggerTime!!.split(":")
        val hour = timeParts[0].toInt()
        val minute = timeParts.getOrElse(1) { "0" }.toInt()

        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        when (rule.triggerRepeat) {
            "ONCE" -> {
                // If time already passed today, schedule for tomorrow
                if (target.before(now)) {
                    target.add(Calendar.DAY_OF_MONTH, 1)
                }
            }
            "DAILY" -> {
                // If time already passed today, schedule for tomorrow
                if (target.before(now)) {
                    target.add(Calendar.DAY_OF_MONTH, 1)
                }
            }
            "WEEKLY" -> {
                // Find the next matching day
                val days = rule.triggerDays?.split(",") ?: listOf()
                val dayMap = mapOf(
                    "SUN" to Calendar.SUNDAY,
                    "MON" to Calendar.MONDAY,
                    "TUE" to Calendar.TUESDAY,
                    "WED" to Calendar.WEDNESDAY,
                    "THU" to Calendar.THURSDAY,
                    "FRI" to Calendar.FRIDAY,
                    "SAT" to Calendar.SATURDAY
                )

                val targetDays = days.mapNotNull { dayMap[it.trim().uppercase()] }
                if (targetDays.isEmpty()) {
                    // No specific days, treat as daily
                    if (target.before(now)) {
                        target.add(Calendar.DAY_OF_MONTH, 1)
                    }
                } else {
                    // Find next matching day
                    var found = false
                    for (i in 0..7) {
                        val checkDay = Calendar.getInstance().apply {
                            timeInMillis = target.timeInMillis
                            add(Calendar.DAY_OF_MONTH, i)
                        }
                        if (checkDay.get(Calendar.DAY_OF_WEEK) in targetDays) {
                            if (checkDay.after(now)) {
                                target.timeInMillis = checkDay.timeInMillis
                                found = true
                                break
                            }
                        }
                    }
                    if (!found) {
                        // Fallback: schedule for next week
                        target.add(Calendar.WEEK_OF_YEAR, 1)
                    }
                }
            }
        }

        return target.timeInMillis
    }
}