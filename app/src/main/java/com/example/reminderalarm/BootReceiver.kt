package com.example.reminderalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-schedules pending reminders after device reboot or app update.
 * Geofences are also cleared at boot, so any location-based reminders
 * need their circular regions re-registered through [AlarmScheduler]
 * (which delegates to [GeofenceHelper]).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val now = System.currentTimeMillis()
        ReminderStore.all(context)
            .filter {
                it.enabled && (it.triggerAtMillis > now || it.isLocationBased())
            }
            .forEach { AlarmScheduler.schedule(context, it) }
    }
}
