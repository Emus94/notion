package com.example.reminderalarm

import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
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
    private var isPreview: Boolean = false

    companion object {
        const val EXTRA_PREVIEW = "preview"

        /**
         * Static reference to the currently shown AlarmActivity so the
         * service can finish it on auto-snooze. Only set while the
         * activity is alive and not in preview mode.
         */
        @Volatile
        var current: AlarmActivity? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isPreview = intent.getBooleanExtra(EXTRA_PREVIEW, false)
        if (!isPreview) current = this

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        applyImmersive()

        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        populateContent()
        applyUserLayout()

        binding.btnDismiss.setOnClickListener {
            if (isPreview) finish() else dismiss()
        }
        binding.btnSnooze.setOnClickListener {
            if (isPreview) finish() else showSnoozeDialog()
        }
    }

    private fun populateContent() {
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

        if (isPreview) {
            binding.alarmHeader.text = getString(R.string.preview_header)
            binding.alarmLabel.text = getString(R.string.preview_label)
            binding.alarmNotes.text = getString(R.string.preview_notes)
            binding.alarmNotes.visibility = View.VISIBLE
            binding.alarmTime.text = timeFmt.format(Date())
            binding.alarmImage.visibility = View.GONE
            return
        }

        reminderId = intent.getLongExtra(AlarmScheduler.EXTRA_ID, -1L)
        val reminder = ReminderStore.byId(this, reminderId)

        binding.alarmLabel.text = reminder?.label?.takeIf { it.isNotBlank() }
            ?: getString(R.string.app_name)
        binding.alarmTime.text = timeFmt.format(
            Date(reminder?.triggerAtMillis ?: System.currentTimeMillis())
        )

        val notes = reminder?.notes.orEmpty()
        if (notes.isBlank()) {
            binding.alarmNotes.visibility = View.GONE
        } else {
            binding.alarmNotes.visibility = View.VISIBLE
            binding.alarmNotes.text = notes
        }

        val imageUriStr = reminder?.imageUri
        if (imageUriStr != null) {
            val bitmap = runCatching {
                ImageLoader.loadSampled(this, android.net.Uri.parse(imageUriStr), 800)
            }.getOrNull()
            if (bitmap != null) {
                binding.alarmImage.setImageBitmap(bitmap)
                binding.alarmImage.visibility = View.VISIBLE
            } else {
                binding.alarmImage.visibility = View.GONE
            }
        } else {
            binding.alarmImage.visibility = View.GONE
        }
    }

    /**
     * Applies the user-selected background color, text colors (derived
     * from luminance unless overridden) and layout preset. Reorders the
     * three content elements directly in the root LinearLayout so the
     * preset changes what shows up first on screen.
     */
    private fun applyUserLayout() {
        val bg = AlarmScreenSettings.getBackgroundColor(this)
        binding.alarmRoot.setBackgroundColor(bg)

        val lightText = ColorUtils.calculateLuminance(bg) < 0.5
        val autoText = if (lightText) Color.WHITE else Color.BLACK
        val autoSubtle = if (lightText) 0xFFB8D0E7.toInt() else 0xFF555555.toInt()

        // User-picked text color wins if set, else auto-derive from background.
        val textColor = AlarmScreenSettings.getTextColor(this) ?: autoText
        val subtleColor = AlarmScreenSettings.getTextColor(this)?.let { fade(it) } ?: autoSubtle

        binding.alarmHeader.setTextColor(subtleColor)
        binding.alarmTime.setTextColor(textColor)
        binding.alarmLabel.setTextColor(textColor)
        binding.alarmNotes.setTextColor(subtleColor)

        // Buttons honour optional overrides from settings, otherwise keep
        // the existing defaults (theme primary for snooze, red for dismiss).
        val snoozeBg = AlarmScreenSettings.getSnoozeColor(this)
        if (snoozeBg != null) {
            binding.btnSnooze.backgroundTintList =
                android.content.res.ColorStateList.valueOf(snoozeBg)
            val snoozeText =
                if (ColorUtils.calculateLuminance(snoozeBg) < 0.5) Color.WHITE else Color.BLACK
            binding.btnSnooze.setTextColor(snoozeText)
        }
        val dismissBg = AlarmScreenSettings.getDismissColor(this)
            ?: AlarmScreenSettings.DEFAULT_DISMISS_BG
        binding.btnDismiss.backgroundTintList =
            android.content.res.ColorStateList.valueOf(dismissBg)
        val dismissText =
            if (ColorUtils.calculateLuminance(dismissBg) < 0.5) Color.WHITE else Color.BLACK
        binding.btnDismiss.setTextColor(dismissText)

        val preset = AlarmScreenSettings.getLayout(this)
        val root = binding.alarmRoot

        // Remove the three reorderable elements so we can re-add them
        // in the desired order right after the header.
        root.removeView(binding.alarmTime)
        root.removeView(binding.alarmLabel)
        root.removeView(binding.alarmNotes)
        val afterHeader = root.indexOfChild(binding.alarmHeader) + 1

        val order: List<TextView> = when (preset) {
            AlarmScreenSettings.Layout.TIME_FOCUS ->
                listOf(binding.alarmTime, binding.alarmLabel, binding.alarmNotes)
            AlarmScreenSettings.Layout.TASK_FOCUS ->
                listOf(binding.alarmLabel, binding.alarmNotes, binding.alarmTime)
            AlarmScreenSettings.Layout.MINIMAL ->
                listOf(binding.alarmLabel, binding.alarmNotes)
        }
        order.forEachIndexed { i, view -> root.addView(view, afterHeader + i) }
        if (preset == AlarmScreenSettings.Layout.MINIMAL) {
            binding.alarmTime.visibility = View.GONE
        } else {
            binding.alarmTime.visibility = View.VISIBLE
        }

        when (preset) {
            AlarmScreenSettings.Layout.TIME_FOCUS -> {
                binding.alarmTime.textSize = 72f
                binding.alarmLabel.textSize = 24f
                binding.alarmNotes.textSize = 16f
            }
            AlarmScreenSettings.Layout.TASK_FOCUS -> {
                binding.alarmTime.textSize = 26f
                binding.alarmLabel.textSize = 42f
                binding.alarmNotes.textSize = 22f
            }
            AlarmScreenSettings.Layout.MINIMAL -> {
                binding.alarmLabel.textSize = 42f
                binding.alarmNotes.textSize = 22f
            }
        }
    }

    override fun onBackPressed() {
        if (isPreview) {
            super.onBackPressed()
        }
        // Otherwise ignore — user must press a button.
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersive()
    }

    override fun onResume() {
        super.onResume()
        applyImmersive()
    }

    private fun applyImmersive() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun dismiss() {
        AlarmSoundService.stop(this)
        if (reminderId > 0) {
            val reminder = ReminderStore.byId(this, reminderId)
            if (reminder != null && reminder.recurrence == Recurrence.NONE) {
                ReminderStore.save(this, reminder.copy(enabled = false))
            }
            // For recurring reminders the receiver already advanced to
            // the next occurrence — nothing to do on dismiss.
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
        if (reminder.recurrence == Recurrence.NONE) {
            // One-shot: bounce the reminder itself to the snoozed time.
            val updated = reminder.copy(triggerAtMillis = snoozedAt, enabled = true)
            ReminderStore.save(this, updated)
            AlarmScheduler.schedule(this, updated)
        } else {
            // Recurring: schedule a separate side alarm so the regular
            // cadence (already advanced by the receiver) is untouched.
            AlarmScheduler.scheduleSnooze(this, reminder.id, snoozedAt)
        }
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (current == this) current = null
    }

    /** Returns a slightly faded variant of a color for subtitle/secondary text. */
    private fun fade(color: Int): Int {
        val alpha = 0xB3 // ~70%
        return (color and 0x00FFFFFF) or (alpha shl 24)
    }
}
