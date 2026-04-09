package com.example.reminderalarm

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.ColorUtils
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
    private var selectedProjectId: Long? = null
    private val selectedTagIds: MutableList<Long> = mutableListOf()
    private var selectedImageUri: String? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            selectedImageUri = uri.toString()
            updateImagePreview()
        }
    }

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
                selectedProjectId = existing.projectId
                selectedTagIds.clear()
                selectedTagIds.addAll(existing.tagIds)
                selectedImageUri = existing.imageUri
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
        updateProjectLabel()
        updateTagsLabel()
        updateImagePreview()

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
        binding.btnProject.setOnClickListener { showProjectDialog() }
        binding.btnTags.setOnClickListener { showTagsDialog() }
        binding.btnPickImage.setOnClickListener {
            pickImageLauncher.launch(arrayOf("image/*"))
        }
        binding.btnClearImage.setOnClickListener {
            selectedImageUri = null
            updateImagePreview()
        }
        binding.btnSave.setOnClickListener { save() }

        applyPaletteColors()

        // When creating a new reminder, put the cursor in the title field
        // and pop the keyboard up immediately so the user can start typing
        // without an extra tap.
        if (editingId <= 0) {
            binding.editLabel.requestFocus()
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        }
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
        val accent = ThemeManager.accentColor(this)
        binding.toolbar.setBackgroundColor(primary)
        window.statusBarColor = primaryDark
        val primaryTint = ColorStateList.valueOf(primary)
        binding.btnPickDate.backgroundTintList = primaryTint
        binding.btnPickTime.backgroundTintList = primaryTint
        binding.btnRecurrence.backgroundTintList = primaryTint
        binding.btnProject.backgroundTintList = primaryTint
        binding.btnTags.backgroundTintList = primaryTint
        binding.btnPickImage.backgroundTintList = primaryTint
        binding.btnClearImage.backgroundTintList = primaryTint

        // Save button stands out with the accent color. Text color is
        // flipped to black/white based on accent luminance so bright
        // accents like yellow/pink stay readable.
        binding.btnSave.backgroundTintList = ColorStateList.valueOf(accent)
        binding.btnSave.setTextColor(
            if (ColorUtils.calculateLuminance(accent) < 0.5) Color.WHITE else Color.BLACK
        )
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

    private fun updateProjectLabel() {
        val project = selectedProjectId?.let { ProjectStore.byId(this, it) }
        binding.btnProject.text = project?.name ?: getString(R.string.no_project)
    }

    private fun showProjectDialog() {
        val projects = ProjectStore.all(this)
        // First item is "(Brak)" so the user can always clear the assignment.
        val ids: List<Long?> = listOf<Long?>(null) + projects.map { it.id }
        val names = Array(ids.size) { i ->
            if (i == 0) getString(R.string.no_project) else projects[i - 1].name
        }
        val checked = ids.indexOfFirst { it == selectedProjectId }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.project)
            .setSingleChoiceItems(names, checked) { dialog, which ->
                selectedProjectId = ids[which]
                updateProjectLabel()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateTagsLabel() {
        if (selectedTagIds.isEmpty()) {
            binding.btnTags.text = getString(R.string.no_tags_selected)
            return
        }
        val names = selectedTagIds.mapNotNull { TagStore.byId(this, it)?.name }
        binding.btnTags.text = if (names.isEmpty()) {
            getString(R.string.no_tags_selected)
        } else {
            names.joinToString(", ")
        }
    }

    private fun updateImagePreview() {
        val uriStr = selectedImageUri
        if (uriStr == null) {
            binding.imagePreview.visibility = View.GONE
            binding.imagePreview.setImageBitmap(null)
            binding.btnClearImage.visibility = View.GONE
            binding.btnPickImage.setText(R.string.add_image)
            return
        }
        val bitmap = runCatching {
            ImageLoader.loadSampled(this, Uri.parse(uriStr), 600)
        }.getOrNull()
        if (bitmap != null) {
            binding.imagePreview.setImageBitmap(bitmap)
            binding.imagePreview.visibility = View.VISIBLE
        } else {
            binding.imagePreview.visibility = View.GONE
        }
        binding.btnClearImage.visibility = View.VISIBLE
        binding.btnPickImage.setText(R.string.change_image)
    }

    private fun showTagsDialog() {
        val tags = TagStore.all(this)
        if (tags.isEmpty()) {
            Toast.makeText(this, R.string.no_tags_defined, Toast.LENGTH_SHORT).show()
            return
        }
        val names = tags.map { it.name }.toTypedArray()
        val initiallyChecked = BooleanArray(tags.size) { tags[it].id in selectedTagIds }
        val workingChecked = initiallyChecked.copyOf()
        AlertDialog.Builder(this)
            .setTitle(R.string.tags)
            .setMultiChoiceItems(names, workingChecked) { _, which, isChecked ->
                workingChecked[which] = isChecked
            }
            .setPositiveButton(R.string.ok) { _, _ ->
                selectedTagIds.clear()
                workingChecked.forEachIndexed { index, checked ->
                    if (checked) selectedTagIds.add(tags[index].id)
                }
                updateTagsLabel()
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
                recurrence = recurrence,
                projectId = selectedProjectId,
                tagIds = selectedTagIds.toList(),
                imageUri = selectedImageUri
            )
        } else {
            Reminder(
                id = System.currentTimeMillis(),
                label = label,
                notes = notes,
                triggerAtMillis = trigger,
                enabled = true,
                vibrateOnly = vibrateOnly,
                recurrence = recurrence,
                projectId = selectedProjectId,
                tagIds = selectedTagIds.toList(),
                imageUri = selectedImageUri
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
