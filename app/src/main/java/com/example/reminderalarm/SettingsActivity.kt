package com.example.reminderalarm

import android.content.Intent
import android.content.res.ColorStateList
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.example.reminderalarm.databinding.ActivitySettingsBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsBinding

    // -----------------------------------------------------------------
    // Activity-result launchers
    // -----------------------------------------------------------------

    private val pickRingtoneLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri: Uri? = @Suppress("DEPRECATION") (
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            )
            AlarmScreenSettings.setSoundUri(this, uri)
            updateSoundLabel()
        }
    }

    private val pickCustomAudioLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            AlarmScreenSettings.setSoundUri(this, uri)
            updateSoundLabel()
        }
    }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument(ExportImportManager.EXPORT_MIME)
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.openOutputStream(uri)?.use { out ->
                ExportImportManager.exportToStream(this, out)
            }
            Toast.makeText(this, R.string.export_success, Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, R.string.export_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                val r = ExportImportManager.importFromStream(this, input)
                Toast.makeText(
                    this,
                    getString(R.string.import_success, r.reminders, r.projects, r.tags),
                    Toast.LENGTH_LONG
                ).show()
            }
        }.onFailure {
            Toast.makeText(this, R.string.import_failed, Toast.LENGTH_SHORT).show()
        }
        updateLastBackupLabel()
    }

    private val pickBackupFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        BackupManager.setExternalFolder(this, uri)
        // Kick off an immediate backup so the user sees confirmation
        // that writing to the picked folder actually works.
        BackupManager.backup(this)
        updateBackupFolderLabel()
        updateLastBackupLabel()
        Toast.makeText(this, R.string.backup_folder_saved, Toast.LENGTH_SHORT).show()
    }

    // -----------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        applyPaletteColors()

        setupSoundSection()
        setupQuickInputSection()
        setupThemeColorsSection()
        setupAlarmAppearanceSection()
        setupDataSection()
        refreshAllPreviews()
    }

    // -----------------------------------------------------------------
    // Sound section
    // -----------------------------------------------------------------

    private fun setupSoundSection() {
        updateSoundLabel()
        binding.btnPickSound.setOnClickListener { pickRingtone() }
        binding.btnUploadSound.setOnClickListener {
            pickCustomAudioLauncher.launch(arrayOf("audio/*"))
        }

        val storedVolume = AlarmScreenSettings.getVolume(this)
        binding.volumeBar.progress =
            if (storedVolume >= 0) storedVolume else currentSystemVolumePct()
        updateVolumeLabel(storedVolume)
        binding.volumeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    AlarmScreenSettings.setVolume(this@SettingsActivity, progress)
                    updateVolumeLabel(progress)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        binding.btnSystemVolume.setOnClickListener {
            AlarmScreenSettings.setVolume(this, -1)
            binding.volumeBar.progress = currentSystemVolumePct()
            updateVolumeLabel(-1)
        }
    }

    private fun updateSoundLabel() {
        val uri = AlarmScreenSettings.getSoundUri(this)
        binding.currentSound.text = if (uri == null) {
            getString(R.string.sound_system_default)
        } else {
            runCatching {
                RingtoneManager.getRingtone(this, uri).getTitle(this)
            }.getOrNull() ?: uri.lastPathSegment ?: uri.toString()
        }
    }

    private fun updateVolumeLabel(stored: Int) {
        binding.volumeLabel.text = if (stored < 0) {
            getString(R.string.volume_system)
        } else {
            getString(R.string.volume_pct, stored)
        }
    }

    private fun currentSystemVolumePct(): Int {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM).coerceAtLeast(1)
        return (am.getStreamVolume(AudioManager.STREAM_ALARM) * 100 / max)
    }

    private fun pickRingtone() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.pick_sound))
            val existing = AlarmScreenSettings.getSoundUri(this@SettingsActivity)
            if (existing != null) {
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
            }
        }
        pickRingtoneLauncher.launch(intent)
    }

    // -----------------------------------------------------------------
    // Quick input section
    // -----------------------------------------------------------------

    private fun setupQuickInputSection() {
        binding.editAutoSavePhrase.setText(AppSettings.getAutoSavePhrase(this))
        binding.btnSavePhrase.setOnClickListener {
            val phrase = binding.editAutoSavePhrase.text?.toString().orEmpty().trim()
            AppSettings.setAutoSavePhrase(this, phrase)
            Toast.makeText(this, R.string.phrase_saved, Toast.LENGTH_SHORT).show()
        }

        binding.switchShakeSnooze.isChecked = AppSettings.isShakeSnoozeEnabled(this)
        binding.switchShakeSnooze.setOnCheckedChangeListener { _, checked ->
            AppSettings.setShakeSnoozeEnabled(this, checked)
        }
    }

    // -----------------------------------------------------------------
    // Theme colors section (applies to CUSTOM palette)
    // -----------------------------------------------------------------

    private fun setupThemeColorsSection() {
        binding.btnThemePrimary.setOnClickListener {
            ColorPickerHelper.show(
                this,
                getString(R.string.theme_primary),
                ThemeManager.primaryColor(this)
            ) { color ->
                ThemeManager.setCustomPrimary(this, color)
                recreate()
            }
        }
        binding.btnThemeDark.setOnClickListener {
            ColorPickerHelper.show(
                this,
                getString(R.string.theme_dark),
                ThemeManager.primaryDarkColor(this)
            ) { color ->
                ThemeManager.set(this, ThemeManager.Palette.CUSTOM)
                ThemeManager.setCustomDark(this, color)
                recreate()
            }
        }
        binding.btnThemeDarkAuto.setOnClickListener {
            ThemeManager.setCustomDark(this, null)
            recreate()
        }
        binding.btnThemeAccent.setOnClickListener {
            ColorPickerHelper.show(
                this,
                getString(R.string.theme_accent),
                ThemeManager.accentColor(this)
            ) { color ->
                ThemeManager.set(this, ThemeManager.Palette.CUSTOM)
                ThemeManager.setCustomAccent(this, color)
                recreate()
            }
        }
        binding.btnThemeAccentAuto.setOnClickListener {
            ThemeManager.setCustomAccent(this, null)
            recreate()
        }
    }

    // -----------------------------------------------------------------
    // Alarm appearance section
    // -----------------------------------------------------------------

    private fun setupAlarmAppearanceSection() {
        when (AlarmScreenSettings.getLayout(this)) {
            AlarmScreenSettings.Layout.TIME_FOCUS -> binding.layoutGroup.check(R.id.layoutTimeFocus)
            AlarmScreenSettings.Layout.TASK_FOCUS -> binding.layoutGroup.check(R.id.layoutTaskFocus)
            AlarmScreenSettings.Layout.MINIMAL -> binding.layoutGroup.check(R.id.layoutMinimal)
        }
        binding.layoutGroup.setOnCheckedChangeListener { _, checkedId ->
            val picked = when (checkedId) {
                R.id.layoutTaskFocus -> AlarmScreenSettings.Layout.TASK_FOCUS
                R.id.layoutMinimal -> AlarmScreenSettings.Layout.MINIMAL
                else -> AlarmScreenSettings.Layout.TIME_FOCUS
            }
            AlarmScreenSettings.setLayout(this, picked)
        }

        binding.btnPickBg.setOnClickListener {
            ColorPickerHelper.show(
                this,
                getString(R.string.pick_bg_color),
                AlarmScreenSettings.getBackgroundColor(this)
            ) { color ->
                AlarmScreenSettings.setBackgroundColor(this, color)
                refreshAllPreviews()
            }
        }

        binding.btnPickText.setOnClickListener {
            val current = AlarmScreenSettings.getTextColor(this)
                ?: autoTextFor(AlarmScreenSettings.getBackgroundColor(this))
            ColorPickerHelper.show(
                this,
                getString(R.string.text_color),
                current
            ) { color ->
                AlarmScreenSettings.setTextColor(this, color)
                refreshAllPreviews()
            }
        }
        binding.btnPickTextAuto.setOnClickListener {
            AlarmScreenSettings.setTextColor(this, null)
            refreshAllPreviews()
        }

        binding.btnPickSnooze.setOnClickListener {
            val current = AlarmScreenSettings.getSnoozeColor(this)
                ?: ThemeManager.primaryColor(this)
            ColorPickerHelper.show(
                this,
                getString(R.string.snooze_color),
                current
            ) { color ->
                AlarmScreenSettings.setSnoozeColor(this, color)
                refreshAllPreviews()
            }
        }
        binding.btnPickSnoozeAuto.setOnClickListener {
            AlarmScreenSettings.setSnoozeColor(this, null)
            refreshAllPreviews()
        }

        binding.btnPickDismiss.setOnClickListener {
            val current = AlarmScreenSettings.getDismissColor(this)
                ?: AlarmScreenSettings.DEFAULT_DISMISS_BG
            ColorPickerHelper.show(
                this,
                getString(R.string.dismiss_color),
                current
            ) { color ->
                AlarmScreenSettings.setDismissColor(this, color)
                refreshAllPreviews()
            }
        }
        binding.btnPickDismissAuto.setOnClickListener {
            AlarmScreenSettings.setDismissColor(this, null)
            refreshAllPreviews()
        }

        binding.btnPreview.setOnClickListener {
            val intent = Intent(this, AlarmActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
                putExtra(AlarmActivity.EXTRA_PREVIEW, true)
            }
            startActivity(intent)
        }
    }

    // -----------------------------------------------------------------
    // Data section (export / import / backup)
    // -----------------------------------------------------------------

    private fun setupDataSection() {
        updateLastBackupLabel()
        updateBackupFolderLabel()
        binding.btnExport.setOnClickListener {
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            exportLauncher.launch("forgetmenot_$ts.json")
        }
        binding.btnImport.setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
        }
        binding.btnPickBackupFolder.setOnClickListener {
            runCatching { pickBackupFolderLauncher.launch(null) }
        }
        binding.btnClearBackupFolder.setOnClickListener {
            BackupManager.setExternalFolder(this, null)
            updateBackupFolderLabel()
            Toast.makeText(this, R.string.backup_folder_cleared, Toast.LENGTH_SHORT).show()
        }
        binding.btnShowWelcome.setOnClickListener {
            startActivity(Intent(this, WelcomeActivity::class.java))
        }
    }

    private fun updateLastBackupLabel() {
        val time = BackupManager.lastBackupTime(this)
        binding.lastBackupValue.text = if (time == 0L) {
            getString(R.string.backup_never)
        } else {
            SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(time))
        }
    }

    private fun updateBackupFolderLabel() {
        val name = BackupManager.getExternalFolderName(this)
        binding.backupFolderValue.text = name ?: getString(R.string.backup_folder_none)
    }

    // -----------------------------------------------------------------
    // Previews
    // -----------------------------------------------------------------

    private fun refreshAllPreviews() {
        binding.themePrimaryPreview.setBackgroundColor(ThemeManager.primaryColor(this))
        binding.themeDarkPreview.setBackgroundColor(ThemeManager.primaryDarkColor(this))
        binding.themeAccentPreview.setBackgroundColor(ThemeManager.accentColor(this))

        val bg = AlarmScreenSettings.getBackgroundColor(this)
        binding.bgPreview.setBackgroundColor(bg)
        binding.textPreview.setBackgroundColor(
            AlarmScreenSettings.getTextColor(this) ?: autoTextFor(bg)
        )
        binding.snoozePreview.setBackgroundColor(
            AlarmScreenSettings.getSnoozeColor(this) ?: ThemeManager.primaryColor(this)
        )
        binding.dismissPreview.setBackgroundColor(
            AlarmScreenSettings.getDismissColor(this) ?: AlarmScreenSettings.DEFAULT_DISMISS_BG
        )
    }

    private fun autoTextFor(background: Int): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(background, hsv)
        return if (hsv[2] < 0.5f) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
    }

    // -----------------------------------------------------------------
    // Palette
    // -----------------------------------------------------------------

    private fun applyPaletteColors() {
        val primary = ThemeManager.primaryColor(this)
        val primaryDark = ThemeManager.primaryDarkColor(this)
        binding.toolbar.setBackgroundColor(primary)
        window.statusBarColor = primaryDark
        val tint = ColorStateList.valueOf(primary)
        binding.btnPickSound.backgroundTintList = tint
        binding.btnUploadSound.backgroundTintList = tint
        binding.btnSystemVolume.backgroundTintList = tint
        binding.btnSavePhrase.backgroundTintList = tint
        binding.btnThemePrimary.backgroundTintList = tint
        binding.btnThemeDark.backgroundTintList = tint
        binding.btnThemeDarkAuto.backgroundTintList = tint
        binding.btnThemeAccent.backgroundTintList = tint
        binding.btnThemeAccentAuto.backgroundTintList = tint
        binding.btnPickBg.backgroundTintList = tint
        binding.btnPickText.backgroundTintList = tint
        binding.btnPickTextAuto.backgroundTintList = tint
        binding.btnPickSnooze.backgroundTintList = tint
        binding.btnPickSnoozeAuto.backgroundTintList = tint
        binding.btnPickDismiss.backgroundTintList = tint
        binding.btnPickDismissAuto.backgroundTintList = tint
        binding.btnPreview.backgroundTintList = tint
        binding.btnExport.backgroundTintList = tint
        binding.btnImport.backgroundTintList = tint
        binding.btnPickBackupFolder.backgroundTintList = tint
        binding.btnClearBackupFolder.backgroundTintList = tint
        binding.btnShowWelcome.backgroundTintList = tint
    }
}
