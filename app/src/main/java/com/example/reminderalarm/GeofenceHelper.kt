package com.example.reminderalarm

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

/**
 * Registers / unregisters geofences for location-based reminders.
 *
 * One geofence per reminder, keyed by the reminder id. Every reminder
 * shares a single broadcast PendingIntent — GeofencingEvent carries the
 * list of matched geofence ids so the receiver can look the right
 * reminder up.
 *
 * Background caveat: on Android 10+, geofences only fire when the app
 * holds ACCESS_BACKGROUND_LOCATION. Without it the geofence is still
 * registered but the event only arrives when ForgetMeNot is visible.
 * We surface this to the user as a hint in the location picker rather
 * than forcing the extra permission dialog.
 */
object GeofenceHelper {

    private const val ACTION_GEOFENCE_EVENT = "com.example.reminderalarm.GEOFENCE_EVENT"
    private const val PENDING_REQUEST_CODE = 0xFE0CE // "feoce" ;)

    private fun client(context: Context): GeofencingClient =
        LocationServices.getGeofencingClient(context.applicationContext)

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java).apply {
            action = ACTION_GEOFENCE_EVENT
        }
        return PendingIntent.getBroadcast(
            context,
            PENDING_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Register (or replace) the geofence for [reminder]. No-op when the
     * reminder doesn't have a location or when the app lacks the fine
     * location permission. Always idempotent because GeofencingClient
     * replaces existing geofences with the same request id.
     */
    @SuppressLint("MissingPermission")
    fun addFor(context: Context, reminder: Reminder) {
        val lat = reminder.latitude ?: return
        val lng = reminder.longitude ?: return
        val radius = reminder.radiusMeters ?: 150f
        if (!hasLocationPermission(context)) return

        val geofence = Geofence.Builder()
            .setRequestId(requestId(reminder.id))
            .setCircularRegion(lat, lng, radius)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .setNotificationResponsiveness(0)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        client(context).addGeofences(request, pendingIntent(context))
    }

    /** Remove the geofence for a given reminder id, if any. */
    fun removeFor(context: Context, reminderId: Long) {
        runCatching {
            client(context).removeGeofences(listOf(requestId(reminderId)))
        }
    }

    /**
     * Looks up the [Reminder] id that owns a GeofencingEvent request
     * string previously produced by [requestId].
     */
    fun idFromRequestId(requestId: String): Long? =
        requestId.removePrefix(REQUEST_PREFIX).toLongOrNull()

    private const val REQUEST_PREFIX = "reminder_"

    private fun requestId(reminderId: Long): String = "$REQUEST_PREFIX$reminderId"
}
