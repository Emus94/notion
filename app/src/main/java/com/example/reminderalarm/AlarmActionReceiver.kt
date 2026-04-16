package com.example.reminderalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles "Drzemka" and "Wyłącz" tapped from the alarm notification —
 * both on the phone's lockscreen and on the Android Auto head unit.
 *
 * Each action stops the ringing service, finishes the full-screen
 * AlarmActivity (if it's showing), and either schedules a snooze or
 * marks the reminder as completed.
 */
class AlarmActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ID, -1L)
        if (id == -1L) return

        // Stop the noise + vibration first, then handle the action.
        AlarmSoundService.stop(context)

        when (intent.action) {
            ACTION_SNOOZE -> handleSnooze(context, id)
            ACTION_DISMISS -> handleDismiss(context, id)
        }

        // Close the full-screen alarm activity if it's showing.
        AlarmActivity.current?.finish()
    }

    private fun handleSnooze(context: Context, id: Long) {
        val reminder = ReminderStore.byId(context, id) ?: return
        val snoozedAt = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L
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
        const val ACTION_SNOOZE = "com.example.reminderalarm.ACTION_SNOOZE"
        const val ACTION_DISMISS = "com.example.reminderalarm.ACTION_DISMISS"
        const val SNOOZE_MINUTES = 5L
    }
}
