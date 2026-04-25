package com.mandlik.andromate.rules

/**
 * Rule — The core data model of AndroMate
 *
 * Think of it like a JavaScript object:
 * {
 *   id: 1,
 *   name: "Morning Music",
 *   trigger: { type: "TIME", time: "06:00", repeat: "DAILY" },
 *   action: { type: "PLAY_SPOTIFY", data: "spotify:playlist:xxxxx" },
 *   enabled: true
 * }
 *
 * Room DB (Android's local database) stores these permanently.
 * Like AsyncStorage in React Native, but way more powerful.
 */

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rules")
data class Rule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Human-readable name: "Morning Music", "Night DND"
    val name: String,

    // Is this rule active?
    val enabled: Boolean = true,

    // ═══════════════════════════════════════
    // TRIGGER — When should this rule fire?
    // ═══════════════════════════════════════

    // TIME, EVENT (future: LOCATION, APP_OPEN, etc.)
    val triggerType: String,

    // For TIME trigger: "06:00", "23:00", etc.
    val triggerTime: String? = null,

    // ONCE, DAILY, WEEKLY
    val triggerRepeat: String? = null,

    // For WEEKLY: "MON,WED,FRI" (comma-separated days)
    val triggerDays: String? = null,

    // ═══════════════════════════════════════
    // ACTION — What should happen?
    // ═══════════════════════════════════════

    // OPEN_APP, TOGGLE_DND, SET_VOLUME, PLAY_SPOTIFY,
    // PLAY_LOCAL_SONG, SEND_SMS
    val actionType: String,

    // Depends on actionType:
    //   OPEN_APP       → package name ("com.spotify.music")
    //   TOGGLE_DND     → "ON" or "OFF"
    //   SET_VOLUME     → "0" to "100" (percentage)
    //   PLAY_SPOTIFY   → "spotify:playlist:xxxxx" or "spotify:track:xxxxx"
    //   PLAY_LOCAL_SONG→ "/storage/emulated/0/Music/song.mp3"
    //   SEND_SMS       → phone number
    val actionData: String? = null,

    // Extra data for actions that need it:
    //   SEND_SMS → message body
    //   PLAY_LOCAL_SONG → display name of the song
    val actionExtra: String? = null,

    // Timestamp when rule was created
    val createdAt: Long = System.currentTimeMillis()
)