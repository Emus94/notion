package com.example.reminderalarm

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ID, -1L)
        if (id == -1L) return

        // Wake the screen so the full-screen alarm activity can show.
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wl = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "ReminderAlarm:wake"
        )
        wl.acquire(10_000L)

        // Start the foreground sound service so the alarm keeps ringing
        // even if the activity is killed. The service also posts the
        // full-screen notification, which is the system-approved way to
        // bring the activity to the front from a background receiver.
        val soundIntent = Intent(context, AlarmSoundService::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_ID, id)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(soundIntent)
        } else {
            context.startService(soundIntent)
        }

        // Try to launch the alarm activity directly. On Android 10+ this
        // only works when we hold SYSTEM_ALERT_WINDOW (the user granted
        // "Display over other apps") OR when the screen is locked.
        // Otherwise the system falls back to the full-screen notification.
        val activityIntent = Intent(context, AlarmActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
            putExtra(AlarmScheduler.EXTRA_ID, id)
        }
        runCatching { context.startActivity(activityIntent) }

        val isSnooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_SNOOZE, false)
        if (!isSnooze) {
            // Normal fire (not a detour from snooze): either advance the
            // recurring schedule or mark one-shot as completed.
            ReminderStore.byId(context, id)?.let { reminder ->
                if (reminder.recurrence != Recurrence.NONE) {
                    val next = reminder.recurrence.nextAfter(reminder.triggerAtMillis)
                    val advanced = reminder.copy(triggerAtMillis = next, enabled = true)
                    ReminderStore.save(context, advanced)
                    AlarmScheduler.schedule(context, advanced)
                } else {
                    ReminderStore.save(context, reminder.copy(enabled = false))
                }
            }
        }

        try { wl.release() } catch (_: Throwable) {}
    }
}
