package com.example.reminderalarm

import android.app.KeyguardManager
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
        // even if the activity is killed.
        val soundIntent = Intent(context, AlarmSoundService::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_ID, id)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(soundIntent)
        } else {
            context.startService(soundIntent)
        }

        // Launch full-screen alarm UI.
        val activityIntent = Intent(context, AlarmActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
            )
            putExtra(AlarmScheduler.EXTRA_ID, id)
        }
        context.startActivity(activityIntent)

        // Mark this one-shot reminder as done.
        ReminderStore.byId(context, id)?.let {
            ReminderStore.save(context, it.copy(enabled = false))
        }

        try { wl.release() } catch (_: Throwable) {}
    }
}
