package com.example.reminderalarm

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.example.reminderalarm.databinding.ActivityAlarmBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmBinding
    private var reminderId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lockscreen and turn screen on.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager)
                .requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        reminderId = intent.getLongExtra(AlarmScheduler.EXTRA_ID, -1L)
        val reminder = ReminderStore.byId(this, reminderId)

        binding.alarmLabel.text = reminder?.label?.takeIf { it.isNotBlank() }
            ?: getString(R.string.app_name)
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        binding.alarmTime.text = timeFmt.format(Date(reminder?.triggerAtMillis ?: System.currentTimeMillis()))

        binding.btnDismiss.setOnClickListener { dismiss() }
        binding.btnSnooze.setOnClickListener { snooze() }
    }

    override fun onBackPressed() {
        // Prevent dismissing with back button — must use the buttons.
    }

    private fun dismiss() {
        AlarmSoundService.stop(this)
        if (reminderId > 0) {
            ReminderStore.byId(this, reminderId)?.let {
                ReminderStore.save(this, it.copy(enabled = false))
            }
        }
        finish()
    }

    private fun snooze() {
        AlarmSoundService.stop(this)
        val reminder = ReminderStore.byId(this, reminderId) ?: run { finish(); return }
        val snoozedAt = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L
        val updated = reminder.copy(triggerAtMillis = snoozedAt, enabled = true)
        ReminderStore.save(this, updated)
        AlarmScheduler.schedule(this, updated)
        finish()
    }

    companion object {
        const val SNOOZE_MINUTES = 5L
    }
}
