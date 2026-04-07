package com.example.reminderalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-schedules pending reminders after device reboot or app update.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val now = System.currentTimeMillis()
        ReminderStore.all(context)
            .filter { it.enabled && it.triggerAtMillis > now }
            .forEach { AlarmScheduler.schedule(context, it) }
    }
}
