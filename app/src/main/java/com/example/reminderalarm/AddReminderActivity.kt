package com.example.reminderalarm

import android.app.AlertDialog
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
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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
    private var customRepeatDays: Int? = null
    private var priority: Priority = Priority.NORMAL
    private var selectedLatitude: Double? = null
    private var selectedLongitude: Double? = null
    private var selectedRadius: Float? = null
    private var selectedLocationName: String? = null
    private var selectedLocationDelay: Int = 0
    private var selectedProjectId: Long? = null
    private val selectedTagIds: MutableList<Long> = mutableListOf()
    private var selectedImageUri: String? = null

    // Loaded fresh on each TextWatcher call so Settings changes take effect immediately.
    private val autoSavePhrase get() = AppSettings.getAutoSavePhrase(this)

    // Continuation invoked by the location permission prompt once the
    // user has made a decision. Set just before launching the prompt
    // and cleared afterwards so stale callbacks don't leak.
    private var pendingLocationPermissionAction: ((granted: Boolean) -> Unit)? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        pendingLocationPermissionAction?.invoke(granted)
        pendingLocationPermissionAction = null
    }

    private val voiceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val spoken = result.data
            ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
        if (spoken.isNullOrBlank()) return@registerForActivityResult
        // Append to whatever's already in the label so the TextWatcher
        // runs natural-date parsing on the combined string. If the
        // label was empty we get a plain insertion.
        val current = binding.editLabel.text?.toString().orEmpty().trim()
        val merged = if (current.isEmpty()) spoken else "$current $spoken"
        binding.editLabel.setText(merged)
        binding.editLabel.setSelection(merged.length)
    }

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
        val templateId = intent.getLongExtra(EXTRA_TEMPLATE_ID, -1L)
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
                customRepeatDays = existing.customRepeatDays
                priority = existing.priority
                selectedLatitude = existing.latitude
                selectedLongitude = existing.longitude
                selectedRadius = existing.radiusMeters
                selectedLocationName = existing.locationName
                selectedLocationDelay = existing.locationDelayMinutes
                selectedProjectId = existing.projectId
                selectedTagIds.clear()
                selectedTagIds.addAll(existing.tagIds)
                selectedImageUri = existing.imageUri
            } else {
                editingId = -1L
                title = getString(R.string.new_reminder)
            }
        } else if (templateId > 0) {
            // Prefill from a saved template. Time stays at "now + 1 min"
            // so the user only needs to pick the date/time and save.
            val tpl = TemplateStore.byId(this, templateId)
            if (tpl != null) {
                title = getString(R.string.new_reminder)
                suppressParsing = true
                binding.editLabel.setText(tpl.label)
                suppressParsing = false
                binding.editNotes.setText(tpl.notes)
                binding.switchVibrateOnly.isChecked = tpl.vibrateOnly
                recurrence = tpl.recurrence
                customRepeatDays = null
                priority = tpl.priority
                selectedProjectId = tpl.projectId
                selectedTagIds.clear()
                selectedTagIds.addAll(tpl.tagIds)
            } else {
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
                // Magic phrase: typing the configured phrase auto-submits.
                val phrase = autoSavePhrase
                if (!autoSaveTriggered && phrase.isNotEmpty() &&
                    text.contains(phrase, ignoreCase = true)
                ) {
                    autoSaveTriggered = true
                    val cleaned = text.replace(phrase, "", ignoreCase = true).trim()
                    suppressParsing = true
                    binding.editLabel.setText(cleaned)
                    binding.editLabel.setSelection(cleaned.length)
                    suppressParsing = false
                    save()
                    return
                }
                applyNaturalParsing(text)
                // Auto-attach a saved place if its name appears in the
                // label: "kup mleko biedronka" → geofence on Biedronka.
                if (selectedLatitude == null) {
                    val place = PlaceStore.findInText(this@AddReminderActivity, text)
                    if (place != null) {
                        selectedLatitude = place.latitude
                        selectedLongitude = place.longitude
                        selectedRadius = place.radiusMeters
                        selectedLocationName = place.name
                        updateLocationLabel()
                        binding.labelLayout.helperText =
                            (binding.labelLayout.helperText?.toString().orEmpty() +
                                "\n\uD83D\uDCCD ${place.name}").trim()
                    }
                }
            }
        })

        // If launched via "Share to ForgetMeNot" from another app, prefill
        // label/notes/image from the incoming intent — but only on the
        // first creation so rotation / recreation doesn't re-apply it on
        // top of user edits.
        if (editingId <= 0 && savedInstanceState == null) {
            handleShareIntent()
        }

        updateDateTime()
        updateRecurrenceLabel()
        updatePriorityLabel()
        updateLocationLabel()
        updateProjectLabel()
        updateTagsLabel()
        updateImagePreview()

        // Compact row click handlers — Material pickers inherit the
        // app's colorPrimary/colorOnPrimary so they follow the palette
        // automatically, unlike the legacy DatePickerDialog.
        binding.rowDate.setOnClickListener { openDatePicker() }
        binding.rowTime.setOnClickListener { openTimePicker() }
        binding.rowRecurrence.setOnClickListener { showRecurrenceDialog() }
        binding.rowPriority.setOnClickListener { showPriorityDialog() }
        binding.rowLocation.setOnClickListener { showLocationDialog() }
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
    // Voice input
    // ------------------------------------------------------------------

    private fun launchVoiceInput() {
        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "pl-PL")
            putExtra(
                android.speech.RecognizerIntent.EXTRA_PROMPT,
                getString(R.string.voice_prompt)
            )
            putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            voiceLauncher.launch(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(this, R.string.voice_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    // ------------------------------------------------------------------
    // Share intent handling
    // ------------------------------------------------------------------

    /**
     * Populates the form from an incoming [Intent.ACTION_SEND] — the user
     * picked "Udostępnij do ForgetMeNot" in another app. Supports
     * text/plain (SMS, notes, links, email subject+body) and any image
     * MIME (screenshots, photos).
     */
    @Suppress("DEPRECATION")
    private fun handleShareIntent() {
        val action = intent.action
        if (action != Intent.ACTION_SEND) return

        val type = intent.type ?: ""

        // ---------- Image payload ----------
        if (type.startsWith("image/")) {
            val imageUri: Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM)
            if (imageUri != null) {
                val copied = ImageStorage.copyToInternal(this, imageUri)
                if (copied != null) {
                    selectedImageUri = copied
                } else {
                    Toast.makeText(this, R.string.image_load_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }

        // ---------- Text payload ----------
        val rawSubject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        val rawText = intent.getStringExtra(Intent.EXTRA_TEXT)
        val subject = rawSubject?.trim() ?: ""
        val text = rawText?.trim() ?: ""

        var prefilledLabel = ""
        var prefilledNotes = ""
        if (subject.isNotEmpty() && text.isNotEmpty()) {
            // Email-style: subject is a clean title, body goes to notes.
            prefilledLabel = subject
            prefilledNotes = text
        } else if (text.isNotEmpty() && text.length <= 120) {
            // Short SMS/snippet becomes the label directly.
            prefilledLabel = text
        } else if (text.isNotEmpty()) {
            // Long text: first 120 chars as label, full text as notes.
            prefilledLabel = text.take(120)
            prefilledNotes = text
        } else if (subject.isNotEmpty()) {
            // Image only, but with a subject (rare).
            prefilledLabel = subject
        }

        if (prefilledLabel.isNotEmpty()) {
            suppressParsing = true
            binding.editLabel.setText(prefilledLabel)
            binding.editLabel.setSelection(prefilledLabel.length)
            suppressParsing = false
            applyNaturalParsing(prefilledLabel)
        }
        if (prefilledNotes.isNotEmpty()) {
            binding.editNotes.setText(prefilledNotes)
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
        val days = customRepeatDays
        binding.recurrenceValue.text = if (days != null && days >= 2) {
            resources.getQuantityString(R.plurals.every_n_days, days, days)
        } else {
            recurrence.displayName
        }
    }

    private fun updatePriorityLabel() {
        val marker = priority.marker
        val prefix = if (marker.isEmpty()) "" else "$marker  "
        binding.priorityValue.text = prefix + priority.displayName
        if (priority == Priority.NORMAL) {
            binding.priorityValue.setTextColor(
                ThemeManager.accentColor(this)
            )
        } else {
            binding.priorityValue.setTextColor(priority.color)
        }
    }

    private fun updateLocationLabel() {
        val lat = selectedLatitude
        val lng = selectedLongitude
        binding.locationValue.text = when {
            lat == null || lng == null -> getString(R.string.location_none)
            !selectedLocationName.isNullOrBlank() ->
                "${selectedLocationName}  •  ${(selectedRadius ?: 150f).toInt()} m"
            else ->
                String.format("%.4f, %.4f  •  %d m", lat, lng, (selectedRadius ?: 150f).toInt())
        }
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
    // Date / time pickers (Material)
    // ------------------------------------------------------------------

    private fun openDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.pick_date)
            .setSelection(cal.timeInMillis)
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            // MaterialDatePicker returns UTC midnight for the selected
            // local calendar day — read Y/M/D from a UTC calendar so we
            // don't slip a day in Europe/Warsaw.
            val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = millis
            }
            cal.set(Calendar.YEAR, utc.get(Calendar.YEAR))
            cal.set(Calendar.MONTH, utc.get(Calendar.MONTH))
            cal.set(Calendar.DAY_OF_MONTH, utc.get(Calendar.DAY_OF_MONTH))
            updateDateTime()
        }
        picker.show(supportFragmentManager, "datePicker")
    }

    private fun openTimePicker() {
        val tp = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(cal.get(Calendar.HOUR_OF_DAY))
            .setMinute(cal.get(Calendar.MINUTE))
            .setTitleText(R.string.pick_time)
            .build()
        tp.addOnPositiveButtonClickListener {
            cal.set(Calendar.HOUR_OF_DAY, tp.hour)
            cal.set(Calendar.MINUTE, tp.minute)
            cal.set(Calendar.SECOND, 0)
            updateDateTime()
        }
        tp.show(supportFragmentManager, "timePicker")
    }

    // ------------------------------------------------------------------
    // Picker dialogs
    // ------------------------------------------------------------------

    private fun showRecurrenceDialog() {
        val standard = Recurrence.values().toList()
        val names = (standard.map { it.displayName } + getString(R.string.recurrence_custom))
            .toTypedArray()
        // A custom interval is shown as the "custom" row even if the
        // underlying enum is NONE — that's the whole point of override.
        val checked = when {
            customRepeatDays != null && customRepeatDays!! >= 2 -> standard.size
            else -> standard.indexOf(recurrence).coerceAtLeast(0)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.recurrence)
            .setSingleChoiceItems(names, checked) { dialog, which ->
                if (which == standard.size) {
                    dialog.dismiss()
                    showCustomRecurrenceDialog()
                } else {
                    recurrence = standard[which]
                    customRepeatDays = null
                    updateRecurrenceLabel()
                    dialog.dismiss()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showCustomRecurrenceDialog() {
        val input = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.recurrence_custom_hint)
            setPadding(48, 32, 48, 32)
            setText((customRepeatDays ?: 3).toString())
            setSelection(text?.length ?: 0)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.recurrence_custom)
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                val n = input.text.toString().toIntOrNull()
                if (n == null || n < 2 || n > 365) {
                    Toast.makeText(
                        this,
                        R.string.err_custom_days_range,
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }
                customRepeatDays = n
                // Force a non-NONE sentinel so isRepeating() works via
                // either branch; we pick DAILY arbitrarily — the value
                // is only used when customRepeatDays is null.
                recurrence = Recurrence.NONE
                updateRecurrenceLabel()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ------------------------------------------------------------------
    // Location picker
    // ------------------------------------------------------------------

    private fun showLocationDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_location_picker, null)
        val nameInput = view.findViewById<android.widget.EditText>(R.id.locationName)
        val coordsText = view.findViewById<android.widget.TextView>(R.id.locationCoords)
        val btnUse = view.findViewById<android.widget.Button>(R.id.btnUseCurrentLocation)
        val btnSearch = view.findViewById<android.widget.Button>(R.id.btnSearchAddress)
        val radiusLabel = view.findViewById<android.widget.TextView>(R.id.radiusLabel)
        val radiusBar = view.findViewById<android.widget.SeekBar>(R.id.radiusBar)
        val delayLabel = view.findViewById<android.widget.TextView>(R.id.delayLabel)
        val delayBar = view.findViewById<android.widget.SeekBar>(R.id.delayBar)
        val placesContainer = view.findViewById<android.widget.LinearLayout>(R.id.savedPlacesContainer)
        val noPlaces = view.findViewById<android.widget.TextView>(R.id.noSavedPlaces)

        // Working copies so Cancel leaves everything untouched.
        var workingLat = selectedLatitude
        var workingLng = selectedLongitude
        var workingRadius = (selectedRadius ?: 150f).toInt().coerceIn(50, 1000)
        var workingDelay = selectedLocationDelay.coerceIn(0, 30)

        nameInput.setText(selectedLocationName.orEmpty())

        fun repaintCoords() {
            coordsText.text = if (workingLat != null && workingLng != null) {
                String.format("%.5f, %.5f", workingLat, workingLng)
            } else {
                getString(R.string.location_no_coords)
            }
        }
        fun repaintRadius() {
            radiusLabel.text = getString(R.string.location_radius_value, workingRadius)
        }
        fun repaintDelay() {
            delayLabel.text = if (workingDelay == 0) {
                getString(R.string.location_delay_none)
            } else {
                getString(R.string.location_delay_value, workingDelay)
            }
        }
        repaintCoords()
        radiusBar.progress = (workingRadius - 50).coerceAtLeast(0)
        repaintRadius()
        radiusBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                workingRadius = 50 + progress
                repaintRadius()
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })
        delayBar.progress = workingDelay
        repaintDelay()
        delayBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                workingDelay = progress
                repaintDelay()
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })

        // Saved places chips — tap to pick instantly.
        val savedPlaces = PlaceStore.all(this)
        if (savedPlaces.isEmpty()) {
            noPlaces.visibility = android.view.View.VISIBLE
        } else {
            noPlaces.visibility = android.view.View.GONE
            savedPlaces.forEach { place ->
                val chip = com.google.android.material.chip.Chip(this).apply {
                    text = place.name
                    isCheckable = false
                    setOnClickListener {
                        workingLat = place.latitude
                        workingLng = place.longitude
                        workingRadius = place.radiusMeters.toInt()
                        nameInput.setText(place.name)
                        repaintCoords()
                        radiusBar.progress = (workingRadius - 50).coerceAtLeast(0)
                        repaintRadius()
                    }
                }
                placesContainer.addView(chip)
            }
        }

        btnUse.setOnClickListener {
            requestLocationPermissionThen { granted ->
                if (!granted) {
                    Toast.makeText(this, R.string.location_permission_denied, Toast.LENGTH_SHORT).show()
                    return@requestLocationPermissionThen
                }
                fetchCurrentLocation { lat, lng ->
                    workingLat = lat; workingLng = lng; repaintCoords()
                }
            }
        }

        btnSearch.setOnClickListener {
            showAddressSearchDialog { lat, lng ->
                workingLat = lat; workingLng = lng; repaintCoords()
            }
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.location)
            .setView(view)
            .setPositiveButton(R.string.ok) { _, _ ->
                if (workingLat != null && workingLng != null) {
                    selectedLatitude = workingLat
                    selectedLongitude = workingLng
                    selectedRadius = workingRadius.toFloat()
                    selectedLocationName = nameInput.text?.toString()?.trim()
                        .takeIf { !it.isNullOrBlank() }
                    selectedLocationDelay = workingDelay
                } else {
                    selectedLatitude = null
                    selectedLongitude = null
                    selectedRadius = null
                    selectedLocationName = null
                    selectedLocationDelay = 0
                }
                updateLocationLabel()
            }
            .setNeutralButton(R.string.location_clear) { _, _ ->
                selectedLatitude = null
                selectedLongitude = null
                selectedRadius = null
                selectedLocationName = null
                selectedLocationDelay = 0
                updateLocationLabel()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAddressSearchDialog(onResult: (Double, Double) -> Unit) {
        val input = android.widget.EditText(this).apply {
            hint = getString(R.string.search_address_hint)
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.search_address)
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                val query = input.text.toString().trim()
                if (query.isBlank()) return@setPositiveButton
                @Suppress("DEPRECATION")
                try {
                    val gc = android.location.Geocoder(this, java.util.Locale.getDefault())
                    val results = gc.getFromLocationName(query, 1)
                    if (!results.isNullOrEmpty()) {
                        onResult(results[0].latitude, results[0].longitude)
                    } else {
                        Toast.makeText(this, R.string.geocode_no_results, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, R.string.geocode_failed, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun requestLocationPermissionThen(action: (granted: Boolean) -> Unit) {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            action(true)
            return
        }
        pendingLocationPermissionAction = action
        locationPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun fetchCurrentLocation(callback: (Double, Double) -> Unit) {
        val fused = com.google.android.gms.location.LocationServices
            .getFusedLocationProviderClient(this)
        // getCurrentLocation is the right call even if it's a bit
        // heavier than getLastLocation — lastLocation is often stale
        // or null on first device boot.
        fused.getCurrentLocation(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
            null
        ).addOnSuccessListener { loc ->
            if (loc != null) {
                callback(loc.latitude, loc.longitude)
            } else {
                Toast.makeText(
                    this, R.string.location_fetch_failed, Toast.LENGTH_SHORT
                ).show()
            }
        }.addOnFailureListener {
            Toast.makeText(
                this, R.string.location_fetch_failed, Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showPriorityDialog() {
        val options = Priority.values()
        val names = options.map {
            if (it.marker.isEmpty()) it.displayName
            else "${it.marker}  ${it.displayName}"
        }.toTypedArray()
        val checked = options.indexOf(priority)
        AlertDialog.Builder(this)
            .setTitle(R.string.priority)
            .setSingleChoiceItems(names, checked) { dialog, which ->
                priority = options[which]
                updatePriorityLabel()
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
        // Location-based reminders fire on geofence enter, not on a
        // clock — the "in the past" check doesn't apply to them.
        val isLocationBased = selectedLatitude != null && selectedLongitude != null
        val trigger = if (isLocationBased) {
            // Stamp it with "now" so the item sorts naturally in lists.
            System.currentTimeMillis()
        } else {
            cal.timeInMillis
        }
        if (!isLocationBased && trigger <= System.currentTimeMillis()) {
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
                imageUri = selectedImageUri,
                priority = priority,
                customRepeatDays = customRepeatDays,
                latitude = selectedLatitude,
                longitude = selectedLongitude,
                radiusMeters = selectedRadius,
                locationName = selectedLocationName,
                locationDelayMinutes = selectedLocationDelay
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
                imageUri = selectedImageUri,
                priority = priority,
                customRepeatDays = customRepeatDays,
                latitude = selectedLatitude,
                longitude = selectedLongitude,
                radiusMeters = selectedRadius,
                locationName = selectedLocationName,
                locationDelayMinutes = selectedLocationDelay
            )
        }
        ReminderStore.save(this, reminder)
        AlarmScheduler.schedule(this, reminder)
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.add_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_voice_input -> { launchVoiceInput(); true }
            R.id.action_save_as_template -> { showSaveAsTemplateDialog(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    // ------------------------------------------------------------------
    // Save as template
    // ------------------------------------------------------------------

    /**
     * Pops a small name prompt and saves the current form (label, notes,
     * vibrate, recurrence, priority, project, tags — but NOT the trigger
     * time or image) as a reusable [Template].
     */
    private fun showSaveAsTemplateDialog() {
        val rawLabel = binding.editLabel.text?.toString().orEmpty().trim()
        val rawNotes = binding.editNotes.text?.toString().orEmpty().trim()
        if (rawLabel.isBlank()) {
            Toast.makeText(this, R.string.err_empty_label, Toast.LENGTH_SHORT).show()
            return
        }
        val input = android.widget.EditText(this).apply {
            setText(rawLabel.take(40))
            setSelection(text?.length ?: 0)
            setPadding(48, 32, 48, 32)
            hint = getString(R.string.template_name_hint)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.save_as_template)
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(this, R.string.err_empty_name, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val clash = TemplateStore.all(this).any {
                    it.name.equals(name, ignoreCase = true)
                }
                if (clash) {
                    Toast.makeText(this, R.string.err_name_taken, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                TemplateStore.save(
                    this,
                    Template(
                        id = System.currentTimeMillis(),
                        name = name,
                        label = rawLabel,
                        notes = rawNotes,
                        vibrateOnly = binding.switchVibrateOnly.isChecked,
                        recurrence = recurrence,
                        priority = priority,
                        projectId = selectedProjectId,
                        tagIds = selectedTagIds.toList()
                    )
                )
                Toast.makeText(this, R.string.template_saved, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        const val EXTRA_EDIT_ID = "edit_id"
        const val EXTRA_TEMPLATE_ID = "template_id"
    }
}
