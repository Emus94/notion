package com.example.reminderalarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

/**
 * Foreground service that keeps the alarm sound (and vibration) playing
 * until the user dismisses or snoozes it from AlarmActivity.
 */
class AlarmSoundService : Service() {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var autoSnoozeRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getLongExtra(AlarmScheduler.EXTRA_ID, -1L) ?: -1L
        val reminder = ReminderStore.byId(this, id)
        startForeground(NOTIF_ID, buildNotification(id))
        startRinging(vibrateOnly = reminder?.vibrateOnly == true)

        // If the user doesn't react within a minute, automatically snooze
        // for 10 minutes so the alarm keeps nagging instead of disappearing.
        autoSnoozeRunnable = Runnable { performAutoSnooze(id) }
        handler.postDelayed(autoSnoozeRunnable!!, AUTO_SNOOZE_DELAY_MS)

        return START_NOT_STICKY
    }

    private fun performAutoSnooze(id: Long) {
        val reminder = ReminderStore.byId(this, id)
        if (reminder != null) {
            val snoozedAt = System.currentTimeMillis() + AUTO_SNOOZE_MINUTES * 60_000L
            val updated = reminder.copy(triggerAtMillis = snoozedAt, enabled = true)
            ReminderStore.save(this, updated)
            AlarmScheduler.schedule(this, updated)
        }
        AlarmActivity.current?.finish()
        stopSelf()
    }

    private fun startRinging(vibrateOnly: Boolean) {
        if (!vibrateOnly) {
            // Pick the alarm tone (fallback to ringtone/notification).
            val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_NOTIFICATION)

            ringtone = RingtoneManager.getRingtone(this, uri).apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    isLooping = true
                }
                // Make sure alarm volume isn't zero.
                val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                if (am.getStreamVolume(AudioManager.STREAM_ALARM) == 0) {
                    am.setStreamVolume(
                        AudioManager.STREAM_ALARM,
                        am.getStreamMaxVolume(AudioManager.STREAM_ALARM) / 2,
                        0
                    )
                }
                play()
            }
        }

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 800, 600, 800, 600)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        autoSnoozeRunnable?.let { handler.removeCallbacks(it) }
        autoSnoozeRunnable = null
        try { ringtone?.stop() } catch (_: Throwable) {}
        try { vibrator?.cancel() } catch (_: Throwable) {}
    }

    private fun buildNotification(id: Long): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Alarmy przypomnień",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Aktywny alarm przypomnienia"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(ch)
        }

        val reminder = ReminderStore.byId(this, id)
        val title = reminder?.label?.takeIf { it.isNotBlank() } ?: getString(R.string.app_name)

        val openIntent = Intent(this, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(AlarmScheduler.EXTRA_ID, id)
        }
        val openPi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(getString(R.string.alarm_ringing))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setFullScreenIntent(openPi, true)
            .setContentIntent(openPi)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "alarm_ringing"
        private const val NOTIF_ID = 4242
        private const val AUTO_SNOOZE_DELAY_MS = 60_000L
        private const val AUTO_SNOOZE_MINUTES = 10L

        fun stop(context: Context) {
            context.stopService(Intent(context, AlarmSoundService::class.java))
        }
    }
}
