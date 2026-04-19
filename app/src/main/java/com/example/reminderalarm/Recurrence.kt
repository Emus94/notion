package com.example.reminderalarm

import java.util.Calendar

/**
 * How often a reminder repeats. Reminder.triggerAtMillis always points
 * at the next scheduled occurrence; [nextAfter] advances it by one
 * period, looping forward until it's strictly in the future so missed
 * cycles don't stack up as past alarms.
 */
enum class Recurrence(
    val id: String,
    val displayName: String
) {
    NONE("none", "Jednorazowo"),
    DAILY("daily", "Codziennie"),
    WEEKLY("weekly", "Co tydzień"),
    MONTHLY("monthly", "Co miesiąc"),
    YEARLY("yearly", "Co rok");

    fun nextAfter(previousTrigger: Long): Long {
        if (this == NONE) return previousTrigger
        val cal = Calendar.getInstance().apply { timeInMillis = previousTrigger }
        val now = System.currentTimeMillis()
        do {
            when (this) {
                DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
                WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                MONTHLY -> cal.add(Calendar.MONTH, 1)
                YEARLY -> cal.add(Calendar.YEAR, 1)
                NONE -> return cal.timeInMillis
            }
        } while (cal.timeInMillis <= now)
        return cal.timeInMillis
    }

    companion object {
        fun fromId(id: String?): Recurrence =
            values().firstOrNull { it.id == id } ?: NONE
    }
}
