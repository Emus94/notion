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
    private val dateFmt = SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault())
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val fullFmt = SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale.getDefault())

    private var editingId: Long = -1L
    private var suppressParsing: Boolean = false
    private var autoSaveTriggered: Boolean = false
    private var recurrence: Recurrence = Recurrence.NONE
    private var selectedProjectId: Long? = null
    private val selectedTagIds: MutableList<Long> = mutableListOf()
    private var selectedImageUri: String? = null

    private val autoSaveRegex = Regex("""zapisz\s+zapisz""", RegexOption.IGNORE_CASE)

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val localPath = ImageStorage.copyToInternal(this, uri)
            if (localPath != null) {
                val previous = selectedImageUri
                if (previous != null && previous.startsWith("/")) ImageStorage.delete(previous)
                selectedImageUri = localPath
                updateImagePreview()
            } else {
                Toast.makeText(this, R.string.image_load_failed, Toast.LENGTH_SHORT).show()
            }
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
                val text = s?.toString().orEmpty()
                // Magic phrase: typing "zapisz zapisz" auto-submits.
                if (!autoSaveTriggered && autoSaveRegex.containsMatchIn(text)) {
                    autoSaveTriggered = true
                    val cleaned = text.replace(autoSaveRegex, "").trim()
                    suppressParsing = true
                    binding.editLabel.setText(cleaned)
                    binding.editLabel.setSelection(cleaned.length)
                    suppressParsing = false
                    save()
                    return
                }
                applyNaturalParsing(text)
            }
        })

        updateDateTime()
        updateRecurrenceLabel()
        updateProjectLabel()
        updateTagsLabel()
        updateImagePreview()

        // Compact row click handlers
        binding.rowDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(Calendar.YEAR, y)
                cal.set(Calendar.MONTH, m)
                cal.set(Calendar.DAY_OF_MONTH, d)
                updateDateTime()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        binding.rowTime.setOnClickListener {
            TimePickerDialog(this, { _, h, min ->
                cal.set(Calendar.HOUR_OF_DAY, h)
                cal.set(Calendar.MINUTE, min)
                cal.set(Calendar.SECOND, 0)
                updateDateTime()
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }
        binding.rowRecurrence.setOnClickListener { showRecurrenceDialog() }
        binding.rowProject.setOnClickListener { showProjectDialog() }
        binding.rowTags.setOnClickListener { showTagsDialog() }
        binding.rowImage.setOnClickListener { pickImageLauncher.launch(arrayOf("image/*")) }
        binding.btnClearImage.setOnClickListener {
            val prev = selectedImageUri
            if (prev != null && prev.startsWith("/")) ImageStorage.delete(prev)
            selectedImageUri = null
            updateImagePreview()
        }
        binding.btnSave.setOnClickListener { save() }

        applyPaletteColors()

        if (editingId <= 0) {
            binding.editLabel.requestFocus()
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        }
    }

    // ------------------------------------------------------------------
    // Natural language parsing
    // ------------------------------------------------------------------

    private fun applyNaturalParsing(text: String) {
        if (suppressParsing) return
        if (text.isBlank()) { binding.labelLayout.helperText = null; return }
        val parsed = NaturalDateParser.parse(text)
        if (!parsed.hasAny()) { binding.labelLayout.helperText = null; return }

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
                if (cal.timeInMillis <= System.currentTimeMillis()) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
        }
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        updateDateTime()
        binding.labelLayout.helperText =
            getString(R.string.parsed_hint, fullFmt.format(Date(cal.timeInMillis)))
    }

    // ------------------------------------------------------------------
    // Update helpers
    // ------------------------------------------------------------------

    private fun updateDateTime() {
        binding.dateValue.text = dateFmt.format(Date(cal.timeInMillis))
        binding.timeValue.text = timeFmt.format(Date(cal.timeInMillis))
    }

    private fun updateRecurrenceLabel() {
        binding.recurrenceValue.text = recurrence.displayName
    }

    private fun updateProjectLabel() {
        val project = selectedProjectId?.let { ProjectStore.byId(this, it) }
        binding.projectValue.text = project?.name ?: getString(R.string.no_project)
    }

    private fun updateTagsLabel() {
        if (selectedTagIds.isEmpty()) {
            binding.tagsValue.text = getString(R.string.no_tags_selected)
            return
        }
        val names = selectedTagIds.mapNotNull { TagStore.byId(this, it)?.name }
        binding.tagsValue.text =
            if (names.isEmpty()) getString(R.string.no_tags_selected)
            else names.joinToString(", ")
    }

    private fun updateImagePreview() {
        val pathOrUri = selectedImageUri
        if (pathOrUri == null) {
            binding.imagePreview.visibility = View.GONE
            binding.imagePreview.setImageBitmap(null)
            binding.btnClearImage.visibility = View.GONE
            binding.imageStatus.text = getString(R.string.add_image_short)
            return
        }
        val bitmap = ImageLoader.loadSampled(this, pathOrUri, 400)
        if (bitmap != null) {
            binding.imagePreview.setImageBitmap(bitmap)
            binding.imagePreview.visibility = View.VISIBLE
            binding.btnClearImage.visibility = View.VISIBLE
            binding.imageStatus.text = getString(R.string.image_attached)
        } else {
            binding.imagePreview.visibility = View.GONE
            binding.btnClearImage.visibility = View.GONE
            binding.imageStatus.text = getString(R.string.add_image_short)
        }
    }

    // ------------------------------------------------------------------
    // Picker dialogs
    // ------------------------------------------------------------------

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

    private fun showProjectDialog() {
        val projects = ProjectStore.all(this)
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

    private fun showTagsDialog() {
        val tags = TagStore.all(this)
        if (tags.isEmpty()) {
            Toast.makeText(this, R.string.no_tags_defined, Toast.LENGTH_SHORT).show()
            return
        }
        val names = tags.map { it.name }.toTypedArray()
        val workingChecked = BooleanArray(tags.size) { tags[it].id in selectedTagIds }
        AlertDialog.Builder(this)
            .setTitle(R.string.tags)
            .setMultiChoiceItems(names, workingChecked) { _, which, isChecked ->
                workingChecked[which] = isChecked
            }
            .setPositiveButton(R.string.ok) { _, _ ->
                selectedTagIds.clear()
                workingChecked.forEachIndexed { index, c ->
                    if (c) selectedTagIds.add(tags[index].id)
                }
                updateTagsLabel()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ------------------------------------------------------------------
    // Theme
    // ------------------------------------------------------------------

    private fun applyPaletteColors() {
        val primary = ThemeManager.primaryColor(this)
        val primaryDark = ThemeManager.primaryDarkColor(this)
        val accent = ThemeManager.accentColor(this)
        binding.toolbar.setBackgroundColor(primary)
        window.statusBarColor = primaryDark

        val accentColor = accent
        binding.dateValue.setTextColor(accentColor)
        binding.timeValue.setTextColor(accentColor)
        binding.recurrenceValue.setTextColor(accentColor)
        binding.projectValue.setTextColor(accentColor)
        binding.tagsValue.setTextColor(accentColor)
        binding.imageStatus.setTextColor(accentColor)

        binding.btnSave.backgroundTintList = ColorStateList.valueOf(accent)
        binding.btnSave.setTextColor(
            if (ColorUtils.calculateLuminance(accent) < 0.5) Color.WHITE else Color.BLACK
        )
    }

    // ------------------------------------------------------------------
    // Save
    // ------------------------------------------------------------------

    private fun save() {
        val rawLabel = binding.editLabel.text?.toString().orEmpty()
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
            Reminder(editingId, label, notes, trigger, true, vibrateOnly, recurrence,
                selectedProjectId, selectedTagIds.toList(), selectedImageUri)
        } else {
            Reminder(System.currentTimeMillis(), label, notes, trigger, true, vibrateOnly,
                recurrence, selectedProjectId, selectedTagIds.toList(), selectedImageUri)
        }
        ReminderStore.save(this, reminder)
        AlarmScheduler.schedule(this, reminder)
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    companion object {
        const val EXTRA_EDIT_ID = "edit_id"
    }
}
