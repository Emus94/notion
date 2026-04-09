package com.example.reminderalarm

import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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

        // True immersive fullscreen: hide status bar + navigation bar.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        reminderId = intent.getLongExtra(AlarmScheduler.EXTRA_ID, -1L)
        val reminder = ReminderStore.byId(this, reminderId)

        binding.alarmLabel.text = reminder?.label?.takeIf { it.isNotBlank() }
            ?: getString(R.string.app_name)
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        binding.alarmTime.text = timeFmt.format(
            Date(reminder?.triggerAtMillis ?: System.currentTimeMillis())
        )

        binding.btnDismiss.setOnClickListener { dismiss() }
        binding.btnSnooze.setOnClickListener { showSnoozeDialog() }
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

    private fun showSnoozeDialog() {
        val labels = arrayOf(
            getString(R.string.snooze_5m),
            getString(R.string.snooze_10m),
            getString(R.string.snooze_1h),
            getString(R.string.snooze_4h),
            getString(R.string.snooze_custom)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.snooze_title)
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> snooze(5)
                    1 -> snooze(10)
                    2 -> snooze(60)
                    3 -> snooze(240)
                    4 -> showCustomSnoozeDialog()
                }
            }
            .show()
    }

    private fun showCustomSnoozeDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.custom_minutes_hint)
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.snooze_custom)
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                val minutes = input.text.toString().toLongOrNull() ?: return@setPositiveButton
                if (minutes > 0) snooze(minutes)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun snooze(minutes: Long) {
        AlarmSoundService.stop(this)
        val reminder = ReminderStore.byId(this, reminderId) ?: run { finish(); return }
        val snoozedAt = System.currentTimeMillis() + minutes * 60_000L
        val updated = reminder.copy(triggerAtMillis = snoozedAt, enabled = true)
        ReminderStore.save(this, updated)
        AlarmScheduler.schedule(this, updated)
        finish()
    }
}
