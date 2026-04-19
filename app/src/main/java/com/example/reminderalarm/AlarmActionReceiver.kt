package com.example.reminderalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles "Drzemka 5 min", "Drzemka 1h" and "Wyłącz" tapped from the
 * alarm notification — both on the phone's lockscreen and on the
 * Android Auto head unit.
 */
class AlarmActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ID, -1L)
        if (id == -1L) return

        AlarmSoundService.stop(context)

        when (intent.action) {
            ACTION_SNOOZE_5 -> handleSnooze(context, id, 5)
            ACTION_SNOOZE_60 -> handleSnooze(context, id, 60)
            ACTION_DISMISS -> handleDismiss(context, id)
        }

        AlarmActivity.current?.finish()
    }

    private fun handleSnooze(context: Context, id: Long, minutes: Int) {
        val reminder = ReminderStore.byId(context, id) ?: return
        val snoozedAt = System.currentTimeMillis() + minutes * 60_000L
        if (!reminder.isRepeating()) {
            val updated = reminder.copy(triggerAtMillis = snoozedAt, enabled = true)
            ReminderStore.save(context, updated)
            AlarmScheduler.schedule(context, updated)
        } else {
            AlarmScheduler.scheduleSnooze(context, id, snoozedAt)
        }
    }

    private fun handleDismiss(context: Context, id: Long) {
        val reminder = ReminderStore.byId(context, id) ?: return
        if (!reminder.isRepeating()) {
            ReminderStore.save(context, reminder.copy(enabled = false))
        }
    }

    companion object {
        const val ACTION_SNOOZE_5 = "com.example.reminderalarm.ACTION_SNOOZE_5"
        const val ACTION_SNOOZE_60 = "com.example.reminderalarm.ACTION_SNOOZE_60"
        const val ACTION_DISMISS = "com.example.reminderalarm.ACTION_DISMISS"
    }
}
