package com.example.reminderalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object AlarmScheduler {

    const val EXTRA_ID = "reminder_id"
    const val EXTRA_IS_SNOOZE = "is_snooze"

    fun schedule(context: Context, reminder: Reminder) {
        if (!reminder.enabled) return

        // Location-based reminders fire on geofence enter, not at a
        // clock time — hand off to GeofenceHelper and skip AlarmManager.
        if (reminder.isLocationBased()) {
            GeofenceHelper.addFor(context, reminder)
            return
        }

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = mainPendingIntent(context, reminder.id)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAtMillis, pi)
            return
        }

        val showPi = showPendingIntent(context, reminder.id.toInt())
        am.setAlarmClock(
            AlarmManager.AlarmClockInfo(reminder.triggerAtMillis, showPi),
            pi
        )
    }

    /**
     * Schedules a side alarm for the snooze of a recurring reminder.
     * Uses a different PendingIntent request code so the main periodic
     * schedule is not overwritten, and tags the intent with
     * [EXTRA_IS_SNOOZE] so the receiver knows not to advance the cycle.
     */
    fun scheduleSnooze(context: Context, reminderId: Long, triggerMillis: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = snoozePendingIntent(context, reminderId)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
            return
        }

        val showPi = showPendingIntent(context, snoozeRequestCode(reminderId))
        am.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerMillis, showPi),
            pi
        )
    }

    fun cancel(context: Context, id: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(mainPendingIntent(context, id))
        am.cancel(snoozePendingIntent(context, id))
        // Also drop the geofence for this reminder — harmless if none.
        GeofenceHelper.removeFor(context, id)
    }

    private fun mainPendingIntent(context: Context, id: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_ID, id)
        }
        return PendingIntent.getBroadcast(
            context, id.toInt(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun snoozePendingIntent(context: Context, id: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_IS_SNOOZE, true)
        }
        return PendingIntent.getBroadcast(
            context, snoozeRequestCode(id), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun showPendingIntent(context: Context, requestCode: Int): PendingIntent {
        val showIntent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context, requestCode, showIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun snoozeRequestCode(id: Long): Int = id.toInt() xor 0x55555555
}
