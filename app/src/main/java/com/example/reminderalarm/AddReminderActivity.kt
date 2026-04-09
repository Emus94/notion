package com.example.reminderalarm

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
    private var suppressParsing: Boolean = false
    private var recurrence: Recurrence = Recurrence.NONE

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
                suppressParsing = true
                binding.editLabel.setText(existing.label)
                suppressParsing = false
                binding.editNotes.setText(existing.notes)
                binding.switchVibrateOnly.isChecked = existing.vibrateOnly
                cal.timeInMillis = existing.triggerAtMillis
                recurrence = existing.recurrence
            } else {
                editingId = -1L
                title = getString(R.string.new_reminder)
            }
        } else {
            title = getString(R.string.new_reminder)
        }

        binding.editLabel.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                applyNaturalParsing(s?.toString().orEmpty())
            }
        })

        updateDateTimeLabel()
        updateRecurrenceLabel()

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

        binding.btnRecurrence.setOnClickListener { showRecurrenceDialog() }
        binding.btnSave.setOnClickListener { save() }

        applyPaletteColors()
    }

    private fun applyNaturalParsing(text: String) {
        if (suppressParsing) return
        if (text.isBlank()) {
            binding.labelLayout.helperText = null
            return
        }
        val parsed = NaturalDateParser.parse(text)
        if (!parsed.hasAny()) {
            binding.labelLayout.helperText = null
            return
        }

        val datePart = parsed.datePart
        if (datePart != null) {
            cal.set(Calendar.YEAR, datePart.get(Calendar.YEAR))
            cal.set(Calendar.MONTH, datePart.get(Calendar.MONTH))
            cal.set(Calendar.DAY_OF_MONTH, datePart.get(Calendar.DAY_OF_MONTH))
            parsed.timeHour?.let { h ->
                cal.set(Calendar.HOUR_OF_DAY, h)
                cal.set(Calendar.MINUTE, parsed.timeMinute ?: 0)
            }
        } else {
            parsed.timeHour?.let { h ->
                cal.set(Calendar.HOUR_OF_DAY, h)
                cal.set(Calendar.MINUTE, parsed.timeMinute ?: 0)
                // Only-time parse with a moment already in the past → push
                // to the next day so save() doesn't reject it.
                if (cal.timeInMillis <= System.currentTimeMillis()) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
        }
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        updateDateTimeLabel()
        binding.labelLayout.helperText =
            getString(R.string.parsed_hint, fmt.format(Date(cal.timeInMillis)))
    }

    private fun applyPaletteColors() {
        val primary = ThemeManager.primaryColor(this)
        val primaryDark = ThemeManager.primaryDarkColor(this)
        binding.toolbar.setBackgroundColor(primary)
        window.statusBarColor = primaryDark
        val tint = ColorStateList.valueOf(primary)
        binding.btnPickDate.backgroundTintList = tint
        binding.btnPickTime.backgroundTintList = tint
        binding.btnRecurrence.backgroundTintList = tint
        binding.btnSave.backgroundTintList = tint
    }

    private fun updateDateTimeLabel() {
        binding.dateTimeLabel.text = fmt.format(Date(cal.timeInMillis))
    }

    private fun updateRecurrenceLabel() {
        binding.btnRecurrence.text = recurrence.displayName
    }

    private fun showRecurrenceDialog() {
        val options = Recurrence.values()
        val names = options.map { it.displayName }.toTypedArray()
        val checked = options.indexOf(recurrence)
        AlertDialog.Builder(this)
            .setTitle(R.string.recurrence)
            .setSingleChoiceItems(names, checked) { dialog, which ->
                recurrence = options[which]
                updateRecurrenceLabel()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun save() {
        val rawLabel = binding.editLabel.text?.toString().orEmpty()
        // Strip the date/time tokens the parser recognised so they don't
        // pollute the final title displayed in the list and alarm screen.
        val parsed = NaturalDateParser.parse(rawLabel)
        val cleanLabel = NaturalDateParser.stripRanges(rawLabel, parsed.matchedRanges)
        val label = cleanLabel.ifBlank { rawLabel.trim() }
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
                vibrateOnly = vibrateOnly,
                recurrence = recurrence
            )
        } else {
            Reminder(
                id = System.currentTimeMillis(),
                label = label,
                notes = notes,
                triggerAtMillis = trigger,
                enabled = true,
                vibrateOnly = vibrateOnly,
                recurrence = recurrence
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
