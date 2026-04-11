package com.example.reminderalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

/**
 * Receives geofence enter events for location-based reminders and fires
 * the same full-screen alarm flow as [AlarmReceiver] — wake lock,
 * foreground sound service, alarm activity.
 *
 * One geofence transition can match multiple reminders (user crossed
 * several geofences at once), so we iterate the list and fire each.
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return
        val triggered = event.triggeringGeofences ?: return

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wl = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "ReminderAlarm:geofenceWake"
        )
        wl.acquire(10_000L)

        triggered.forEach { gf ->
            val id = GeofenceHelper.idFromRequestId(gf.requestId) ?: return@forEach
            val reminder = ReminderStore.byId(context, id) ?: return@forEach
            if (!reminder.enabled) return@forEach

            // Start the same sound service path as the time-based alarm.
            val soundIntent = Intent(context, AlarmSoundService::class.java).apply {
                putExtra(AlarmScheduler.EXTRA_ID, id)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(soundIntent)
            } else {
                context.startService(soundIntent)
            }

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

            // Mark one-shot location reminders as done so the geofence
            // doesn't keep re-triggering every time the user walks back.
            // Recurring ones stay enabled and re-register automatically.
            if (!reminder.isRepeating()) {
                ReminderStore.save(context, reminder.copy(enabled = false))
                GeofenceHelper.removeFor(context, id)
            }
        }

        try { wl.release() } catch (_: Throwable) {}
    }
}
