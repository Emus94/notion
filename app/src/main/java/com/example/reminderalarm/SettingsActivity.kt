package com.example.reminderalarm

import android.content.Intent
import android.content.res.ColorStateList
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import com.example.reminderalarm.databinding.ActivitySettingsBinding

class SettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsBinding

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        applyPaletteColors()

        // Sound section
        updateSoundLabel()
        binding.btnPickSound.setOnClickListener { pickRingtone() }

        // Volume
        val storedVolume = AlarmScreenSettings.getVolume(this)
        binding.volumeBar.progress = if (storedVolume >= 0) storedVolume else currentSystemVolumePct()
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

        // Layout preset
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

        // Background color
        updateBgPreview()
        binding.btnPickBg.setOnClickListener {
            ColorPickerHelper.show(
                context = this,
                title = getString(R.string.pick_bg_color),
                initialColor = AlarmScreenSettings.getBackgroundColor(this)
            ) { color ->
                AlarmScreenSettings.setBackgroundColor(this, color)
                updateBgPreview()
            }
        }

        // Preview
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

    private fun applyPaletteColors() {
        val primary = ThemeManager.primaryColor(this)
        val primaryDark = ThemeManager.primaryDarkColor(this)
        binding.toolbar.setBackgroundColor(primary)
        window.statusBarColor = primaryDark
        val tint = ColorStateList.valueOf(primary)
        binding.btnPickSound.backgroundTintList = tint
        binding.btnSystemVolume.backgroundTintList = tint
        binding.btnPickBg.backgroundTintList = tint
        binding.btnPreview.backgroundTintList = tint
    }

    private fun updateSoundLabel() {
        val uri = AlarmScreenSettings.getSoundUri(this)
        binding.currentSound.text = if (uri == null) {
            getString(R.string.sound_system_default)
        } else {
            runCatching {
                RingtoneManager.getRingtone(this, uri).getTitle(this)
            }.getOrNull() ?: uri.toString()
        }
    }

    private fun updateVolumeLabel(stored: Int) {
        binding.volumeLabel.text = if (stored < 0) {
            getString(R.string.volume_system)
        } else {
            getString(R.string.volume_pct, stored)
        }
    }

    private fun updateBgPreview() {
        binding.bgPreview.setBackgroundColor(AlarmScreenSettings.getBackgroundColor(this))
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
}
