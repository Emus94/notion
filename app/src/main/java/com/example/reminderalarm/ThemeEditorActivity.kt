package com.example.reminderalarm

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.example.reminderalarm.databinding.ActivityThemeEditorBinding
import com.example.reminderalarm.databinding.ItemThemeElementBinding

/**
 * Interactive theme editor with live preview.
 *
 * The user sees a mock app surface at the top (toolbar, card, FAB,
 * alarm screen). Every colour is tappable — both in the preview
 * itself and in the list of "elements" below. Tapping opens the
 * shared [ColorPickerHelper] dialog, and the chosen colour is
 * applied to the preview immediately. Changes are persisted directly
 * to [ThemeManager] / [AppSettings] / [AlarmScreenSettings] as the
 * user picks (no separate "Save" step — the whole screen is the
 * commit).
 */
class ThemeEditorActivity : BaseActivity() {

    private lateinit var binding: ActivityThemeEditorBinding

    /**
     * One editable colour element. [read] pulls the current value from
     * storage, [write] saves the new value. [apply] is optional —
     * elements that already show up via [refreshPreview] (e.g. primary
     * colour repaints the toolbar) don't need a dedicated apply.
     */
    private data class Element(
        val titleRes: Int,
        val descRes: Int,
        val read: () -> Int,
        val write: (Int) -> Unit,
        val reset: (() -> Unit)? = null
    )

    private val elements = mutableListOf<Element>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThemeEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        buildElements()
        renderElementRows()
        wirePreviewTaps()
        refreshPreview()

        binding.btnResetAll.setOnClickListener { resetAll() }
    }

    // ------------------------------------------------------------------
    // Element definitions
    // ------------------------------------------------------------------

    private fun buildElements() {
        elements.clear()
        elements.add(Element(
            titleRes = R.string.theme_el_primary,
            descRes = R.string.theme_el_primary_desc,
            read = { ThemeManager.primaryColor(this) },
            write = { ThemeManager.setCustomPrimary(this, it) }
        ))
        elements.add(Element(
            titleRes = R.string.theme_el_primary_dark,
            descRes = R.string.theme_el_primary_dark_desc,
            read = { ThemeManager.primaryDarkColor(this) },
            write = {
                ThemeManager.set(this, ThemeManager.Palette.CUSTOM)
                ThemeManager.setCustomDark(this, it)
            },
            reset = { ThemeManager.setCustomDark(this, null) }
        ))
        elements.add(Element(
            titleRes = R.string.theme_el_accent,
            descRes = R.string.theme_el_accent_desc,
            read = { ThemeManager.accentColor(this) },
            write = {
                ThemeManager.set(this, ThemeManager.Palette.CUSTOM)
                ThemeManager.setCustomAccent(this, it)
            },
            reset = { ThemeManager.setCustomAccent(this, null) }
        ))
        elements.add(Element(
            titleRes = R.string.theme_el_light_bg,
            descRes = R.string.theme_el_light_bg_desc,
            read = { AppSettings.getCustomLightBg(this) ?: 0xFFFFFFFF.toInt() },
            write = { AppSettings.setCustomLightBg(this, it) },
            reset = { AppSettings.setCustomLightBg(this, null) }
        ))
        elements.add(Element(
            titleRes = R.string.theme_el_dark_bg,
            descRes = R.string.theme_el_dark_bg_desc,
            read = { AppSettings.getCustomDarkBg(this) ?: 0xFF121212.toInt() },
            write = { AppSettings.setCustomDarkBg(this, it) },
            reset = { AppSettings.setCustomDarkBg(this, null) }
        ))
        elements.add(Element(
            titleRes = R.string.theme_el_alarm_bg,
            descRes = R.string.theme_el_alarm_bg_desc,
            read = { AlarmScreenSettings.getBackgroundColor(this) },
            write = { AlarmScreenSettings.setBackgroundColor(this, it) }
        ))
        elements.add(Element(
            titleRes = R.string.theme_el_alarm_text,
            descRes = R.string.theme_el_alarm_text_desc,
            read = {
                AlarmScreenSettings.getTextColor(this)
                    ?: autoTextFor(AlarmScreenSettings.getBackgroundColor(this))
            },
            write = { AlarmScreenSettings.setTextColor(this, it) },
            reset = { AlarmScreenSettings.setTextColor(this, null) }
        ))
        elements.add(Element(
            titleRes = R.string.theme_el_alarm_snooze,
            descRes = R.string.theme_el_alarm_snooze_desc,
            read = {
                AlarmScreenSettings.getSnoozeColor(this)
                    ?: ThemeManager.primaryColor(this)
            },
            write = { AlarmScreenSettings.setSnoozeColor(this, it) },
            reset = { AlarmScreenSettings.setSnoozeColor(this, null) }
        ))
        elements.add(Element(
            titleRes = R.string.theme_el_alarm_dismiss,
            descRes = R.string.theme_el_alarm_dismiss_desc,
            read = {
                AlarmScreenSettings.getDismissColor(this)
                    ?: AlarmScreenSettings.DEFAULT_DISMISS_BG
            },
            write = { AlarmScreenSettings.setDismissColor(this, it) },
            reset = { AlarmScreenSettings.setDismissColor(this, null) }
        ))
    }

    private fun renderElementRows() {
        binding.rowsContainer.removeAllViews()
        for (el in elements) {
            val row = ItemThemeElementBinding
                .inflate(LayoutInflater.from(this), binding.rowsContainer, false)
            row.elementName.setText(el.titleRes)
            row.elementDesc.setText(el.descRes)
            paintSwatch(row.elementSwatch, el.read())
            row.root.setOnClickListener { pickColor(el) }
            if (el.reset != null) {
                row.elementResetBtn.visibility = View.VISIBLE
                row.elementResetBtn.setOnClickListener {
                    el.reset.invoke()
                    refreshPreview()
                    renderElementRows()
                }
            } else {
                row.elementResetBtn.visibility = View.GONE
            }
            binding.rowsContainer.addView(row.root)
        }
    }

    private fun paintSwatch(view: View, color: Int) {
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(
                (1 * resources.displayMetrics.density).toInt(),
                0x33000000
            )
        }
    }

    private fun pickColor(el: Element) {
        ColorPickerHelper.show(
            this,
            getString(el.titleRes),
            el.read()
        ) { picked ->
            el.write(picked)
            refreshPreview()
            renderElementRows()
        }
    }

    // ------------------------------------------------------------------
    // Preview taps — direct-manipulation shortcuts so the user can tap
    // the fake toolbar / FAB / etc. to change its colour.
    // ------------------------------------------------------------------

    private fun wirePreviewTaps() {
        binding.previewToolbar.setOnClickListener { pickColor(elements[0]) }
        binding.previewStatusBar.setOnClickListener { pickColor(elements[1]) }
        binding.previewFab.setOnClickListener { pickColor(elements[2]) }
        binding.previewContent.setOnClickListener { pickColor(elements[3]) }
        binding.previewAlarm.setOnClickListener { pickColor(elements[5]) }
        binding.previewAlarmTime.setOnClickListener { pickColor(elements[6]) }
        binding.previewAlarmLabel.setOnClickListener { pickColor(elements[6]) }
        binding.previewSnoozeBtn.setOnClickListener { pickColor(elements[7]) }
        binding.previewDismissBtn.setOnClickListener { pickColor(elements[8]) }
    }

    // ------------------------------------------------------------------
    // Preview paint
    // ------------------------------------------------------------------

    private fun refreshPreview() {
        val primary = ThemeManager.primaryColor(this)
        val primaryDark = ThemeManager.primaryDarkColor(this)
        val accent = ThemeManager.accentColor(this)
        val lightBg = AppSettings.getCustomLightBg(this) ?: 0xFFFFFFFF.toInt()
        val alarmBg = AlarmScreenSettings.getBackgroundColor(this)
        val alarmText = AlarmScreenSettings.getTextColor(this) ?: autoTextFor(alarmBg)
        val snooze = AlarmScreenSettings.getSnoozeColor(this) ?: primary
        val dismiss = AlarmScreenSettings.getDismissColor(this)
            ?: AlarmScreenSettings.DEFAULT_DISMISS_BG

        binding.toolbar.setBackgroundColor(primary)
        window.statusBarColor = primaryDark
        binding.previewToolbar.setBackgroundColor(primary)
        binding.previewStatusBar.setBackgroundColor(primaryDark)
        binding.previewFab.backgroundTintList = ColorStateList.valueOf(accent)
        binding.previewContent.setBackgroundColor(lightBg)

        // Alarm card
        binding.previewAlarm.setBackgroundColor(alarmBg)
        binding.previewAlarmTime.setTextColor(alarmText)
        binding.previewAlarmLabel.setTextColor(alarmText)
        paintPill(binding.previewSnoozeBtn, snooze)
        paintPill(binding.previewDismissBtn, dismiss)

        // Card text colour: secondary subtle on top of cardview surface
        binding.previewCardTime.setTextColor(
            ColorUtils.setAlphaComponent(Color.BLACK, 0x99)
        )
        binding.previewCardNotes.setTextColor(
            ColorUtils.setAlphaComponent(Color.BLACK, 0x77)
        )
    }

    private fun paintPill(view: View, color: Int) {
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 18 * resources.displayMetrics.density
            setColor(color)
        }
    }

    private fun autoTextFor(background: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(background, hsv)
        return if (hsv[2] < 0.5f) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
    }

    // ------------------------------------------------------------------
    // Reset everything
    // ------------------------------------------------------------------

    private fun resetAll() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.theme_editor_reset)
            .setMessage(R.string.theme_editor_reset_confirm)
            .setPositiveButton(R.string.ok) { _, _ ->
                // Reset custom theme overrides to a clean palette.
                ThemeManager.setCustomDark(this, null)
                ThemeManager.setCustomAccent(this, null)
                AppSettings.setCustomLightBg(this, null)
                AppSettings.setCustomDarkBg(this, null)
                AlarmScreenSettings.setTextColor(this, null)
                AlarmScreenSettings.setSnoozeColor(this, null)
                AlarmScreenSettings.setDismissColor(this, null)
                refreshPreview()
                renderElementRows()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
