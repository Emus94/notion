package com.example.reminderalarm

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.widget.Toast
import com.example.reminderalarm.databinding.ActivityAddBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AddReminderActivity : BaseActivity() {

    private lateinit var binding: ActivityAddBinding
    private val cal = Calendar.getInstance().apply {
        add(Calendar.MINUTE, 1)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    private val fmt = SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale.getDefault())

    private var editingId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        editingId = intent.getLongExtra(EXTRA_EDIT_ID, -1L)
        if (editingId > 0) {
            val existing = ReminderStore.byId(this, editingId)
            if (existing != null) {
                title = getString(R.string.edit_reminder)
                binding.btnSave.setText(R.string.save_changes)
                binding.editLabel.setText(existing.label)
                binding.editNotes.setText(existing.notes)
                binding.switchVibrateOnly.isChecked = existing.vibrateOnly
                cal.timeInMillis = existing.triggerAtMillis
            } else {
                editingId = -1L
                title = getString(R.string.new_reminder)
            }
        } else {
            title = getString(R.string.new_reminder)
        }

        updateDateTimeLabel()

        binding.btnPickDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, y, m, d ->
                    cal.set(Calendar.YEAR, y)
                    cal.set(Calendar.MONTH, m)
                    cal.set(Calendar.DAY_OF_MONTH, d)
                    updateDateTimeLabel()
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        binding.btnPickTime.setOnClickListener {
            TimePickerDialog(
                this,
                { _, h, min ->
                    cal.set(Calendar.HOUR_OF_DAY, h)
                    cal.set(Calendar.MINUTE, min)
                    cal.set(Calendar.SECOND, 0)
                    updateDateTimeLabel()
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                true
            ).show()
        }

        binding.btnSave.setOnClickListener { save() }

        applyPaletteColors()
    }

    private fun applyPaletteColors() {
        val primary = ThemeManager.primaryColor(this)
        val primaryDark = ThemeManager.primaryDarkColor(this)
        binding.toolbar.setBackgroundColor(primary)
        window.statusBarColor = primaryDark
        val tint = ColorStateList.valueOf(primary)
        binding.btnPickDate.backgroundTintList = tint
        binding.btnPickTime.backgroundTintList = tint
        binding.btnSave.backgroundTintList = tint
    }

    private fun updateDateTimeLabel() {
        binding.dateTimeLabel.text = fmt.format(Date(cal.timeInMillis))
    }

    private fun save() {
        val label = binding.editLabel.text?.toString().orEmpty().trim()
        val notes = binding.editNotes.text?.toString().orEmpty().trim()
        val vibrateOnly = binding.switchVibrateOnly.isChecked
        val trigger = cal.timeInMillis
        if (trigger <= System.currentTimeMillis()) {
            Toast.makeText(this, R.string.err_past, Toast.LENGTH_SHORT).show()
            return
        }
        val reminder = if (editingId > 0) {
            AlarmScheduler.cancel(this, editingId)
            Reminder(
                id = editingId,
                label = label,
                notes = notes,
                triggerAtMillis = trigger,
                enabled = true,
                vibrateOnly = vibrateOnly
            )
        } else {
            Reminder(
                id = System.currentTimeMillis(),
                label = label,
                notes = notes,
                triggerAtMillis = trigger,
                enabled = true,
                vibrateOnly = vibrateOnly
            )
        }
        ReminderStore.save(this, reminder)
        AlarmScheduler.schedule(this, reminder)
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish(); return true
    }

    companion object {
        const val EXTRA_EDIT_ID = "edit_id"
    }
}
