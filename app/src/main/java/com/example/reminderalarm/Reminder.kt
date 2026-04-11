package com.example.reminderalarm

import android.content.Context

data class Reminder(
    val id: Long,
    val label: String,
    val notes: String = "",
    val triggerAtMillis: Long,
    val enabled: Boolean = true,
    val vibrateOnly: Boolean = false,
    val recurrence: Recurrence = Recurrence.NONE,
    val projectId: Long? = null,
    val tagIds: List<Long> = emptyList(),
    val imageUri: String? = null,
    val priority: Priority = Priority.NORMAL,
    /**
     * When non-null and ≥ 2 this overrides [recurrence] and makes the
     * reminder fire every N days from [triggerAtMillis] — the "co 3 dni"
     * custom interval. Stored separately from [recurrence] so the legacy
     * enum stays simple and existing serialisation keeps working.
     */
    val customRepeatDays: Int? = null,
    /** Optional geofence centre — fires on enter instead of by time. */
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** Radius in metres. Defaults to 150 m when a location is set. */
    val radiusMeters: Float? = null,
    /** Human-readable place name ("Dom", "Praca", "Biedronka") shown in the card. */
    val locationName: String? = null
)

/** Convenience: does this reminder repeat at all (standard or custom)? */
fun Reminder.isRepeating(): Boolean =
    recurrence != Recurrence.NONE ||
        (customRepeatDays != null && customRepeatDays >= 2)

/** True if the reminder fires on entering a geofence rather than at a time. */
fun Reminder.isLocationBased(): Boolean =
    latitude != null && longitude != null

/**
 * Advances the trigger time to the next future occurrence. Loops
 * forward until it's strictly in the future so missed cycles don't
 * stack up as past alarms (mirrors [Recurrence.nextAfter]).
 */
fun Reminder.computeNextTrigger(now: Long = System.currentTimeMillis()): Long {
    if (customRepeatDays != null && customRepeatDays >= 2) {
        val step = customRepeatDays.toLong() * 24L * 60L * 60L * 1000L
        var t = triggerAtMillis
        do { t += step } while (t <= now)
        return t
    }
    return recurrence.nextAfter(triggerAtMillis)
}

/** Human-readable repeat description for the current locale. */
fun Reminder.recurrenceDisplayName(context: Context): String {
    if (customRepeatDays != null && customRepeatDays >= 2) {
        return context.resources.getQuantityString(
            R.plurals.every_n_days,
            customRepeatDays,
            customRepeatDays
        )
    }
    return recurrence.displayName
}
