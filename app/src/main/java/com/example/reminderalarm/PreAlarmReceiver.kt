package com.example.reminderalarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fires a heads-up "coming up" notification N minutes before a
 * reminder's actual alarm. Lets the user prepare for a meeting,
 * take a pill timer, etc. without being surprised by the full-screen
 * alarm when it finally rings.
 *
 * Notification is silent by default (no sound, gentle vibration) so
 * it's easier to ignore than the real alarm — the full-screen
 * [AlarmActivity] still fires at the scheduled time.
 */
class PreAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ID, -1L)
        if (id == -1L) return
        val reminder = ReminderStore.byId(context, id) ?: return
        if (!reminder.enabled) return

        ensureChannel(context)

        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault()).format(
            Date(reminder.triggerAtMillis)
        )
        val label = reminder.label.ifBlank { context.getString(R.string.untitled) }

        // Tap = open the main app so the user can review or edit the reminder.
        val openIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openPi = PendingIntent.getActivity(
            context,
            (id xor 0xA1A1A1A1L).toInt(),
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(context.getString(R.string.prealarm_title, timeFmt))
            .setContentText(label)
            .setStyle(NotificationCompat.BigTextStyle().bigText(label))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openPi)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify((id xor 0xA1A1A1A1L).toInt(), notif)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.prealarm_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.prealarm_channel_desc)
            enableVibration(true)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "prealarm_channel"
    }
}
