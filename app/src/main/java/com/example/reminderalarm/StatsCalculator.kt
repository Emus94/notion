package com.example.reminderalarm

import android.content.Context
import java.util.Calendar

/**
 * Rolls up the raw reminder list into a small bag of numbers for the
 * "Statystyki" dialog. Everything here is pure and derives straight from
 * [ReminderStore], so there's no extra state to keep in sync.
 *
 * Definitions:
 *  - **completed** = `!enabled` (the reminder was fired-and-forgotten,
 *    swiped done, or manually disabled)
 *  - **upcoming** = `enabled` and `triggerAtMillis > now`
 *  - **overdue** = `enabled` and `triggerAtMillis <= now` and non-recurring
 *    (a one-shot whose alarm somehow never reached the user)
 *  - **streak** = number of consecutive calendar days ending today or
 *    yesterday that contain at least one completed reminder. If neither
 *    today nor yesterday has any completions the streak is 0.
 */
object StatsCalculator {

    data class Stats(
        val completedThisWeek: Int,
        val upcomingThisWeek: Int,
        val overdue: Int,
        val completedTotal: Int,
        val currentStreak: Int,
        val longestStreak: Int
    )

    fun compute(context: Context, now: Long = System.currentTimeMillis()): Stats {
        val all = ReminderStore.all(context)

        val startOfWeek = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // Monday as the first day of the Polish week.
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }.timeInMillis
        val endOfWeek = startOfWeek + 7 * 24 * 60 * 60 * 1000L

        var completedThisWeek = 0
        var upcomingThisWeek = 0
        var overdue = 0
        var completedTotal = 0

        for (r in all) {
            if (!r.enabled) {
                completedTotal++
                if (r.triggerAtMillis in startOfWeek until endOfWeek) completedThisWeek++
            } else {
                if (r.triggerAtMillis > now && r.triggerAtMillis < endOfWeek) upcomingThisWeek++
                if (r.triggerAtMillis <= now && !r.isRepeating()) overdue++
            }
        }

        val (currentStreak, longestStreak) = computeStreaks(all, now)
        return Stats(
            completedThisWeek = completedThisWeek,
            upcomingThisWeek = upcomingThisWeek,
            overdue = overdue,
            completedTotal = completedTotal,
            currentStreak = currentStreak,
            longestStreak = longestStreak
        )
    }

    private fun computeStreaks(reminders: List<Reminder>, now: Long): Pair<Int, Int> {
        val completedDays = reminders
            .asSequence()
            .filter { !it.enabled }
            .map { epochDayOf(it.triggerAtMillis) }
            .toSortedSet()
        if (completedDays.isEmpty()) return 0 to 0

        // Longest streak: walk sorted days, reset counter on gap.
        var longest = 1
        var run = 1
        val iter = completedDays.iterator()
        var prev = iter.next()
        while (iter.hasNext()) {
            val curr = iter.next()
            run = if (curr == prev + 1) run + 1 else 1
            if (run > longest) longest = run
            prev = curr
        }

        // Current streak: count back from today; if today has nothing,
        // allow starting at yesterday so an alarm that hasn't fired yet
        // today doesn't break a 10-day run.
        val today = epochDayOf(now)
        var cursor = when {
            today in completedDays -> today
            (today - 1) in completedDays -> today - 1
            else -> return 0 to longest
        }
        var current = 0
        while (cursor in completedDays) {
            current++
            cursor--
        }
        return current to longest
    }

    /** Days since Unix epoch in the *device's* time zone. */
    private fun epochDayOf(millis: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis / (24L * 60 * 60 * 1000)
    }
}
