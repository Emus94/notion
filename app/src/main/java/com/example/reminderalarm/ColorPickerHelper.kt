package com.example.reminderalarm

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

/**
 * Color picker dialog with four ways to choose a color:
 *  - preset swatch grid (curated Material-ish palette)
 *  - user favourites (grow over time, long-press to remove)
 *  - harmony suggestions (complementary / analogous / triadic) derived
 *    from whatever color is currently selected
 *  - fine-tune with the classic hue / saturation / value sliders
 *
 * Tapping any swatch (preset, favourite or harmony) snaps the sliders to
 * that color so the user can keep tweaking from there. The dialog shares
 * the same show() signature as before, so every existing call site works
 * unchanged.
 */
object ColorPickerHelper {

    private val PRESET_COLORS = intArrayOf(
        // Reds / pinks
        0xFFE53935.toInt(), 0xFFD81B60.toInt(), 0xFFC2185B.toInt(),
        0xFF880E4F.toInt(), 0xFFB71C1C.toInt(), 0xFFFF5252.toInt(),
        // Oranges / yellows
        0xFFFF5722.toInt(), 0xFFFF6F00.toInt(), 0xFFFF9800.toInt(),
        0xFFFFC107.toInt(), 0xFFFFEB3B.toInt(), 0xFFF9A825.toInt(),
        // Greens
        0xFF4CAF50.toInt(), 0xFF388E3C.toInt(), 0xFF1B5E20.toInt(),
        0xFF8BC34A.toInt(), 0xFFCDDC39.toInt(), 0xFF26A69A.toInt(),
        // Blues / cyans
        0xFF00ACC1.toInt(), 0xFF0288D1.toInt(), 0xFF1976D2.toInt(),
        0xFF1565C0.toInt(), 0xFF0D47A1.toInt(), 0xFF00838F.toInt(),
        // Purples
        0xFF9C27B0.toInt(), 0xFF7B1FA2.toInt(), 0xFF4A148C.toInt(),
        0xFF673AB7.toInt(), 0xFF5E35B1.toInt(), 0xFF311B92.toInt(),
        // Neutrals
        0xFF5D4037.toInt(), 0xFF795548.toInt(), 0xFF607D8B.toInt(),
        0xFF455A64.toInt(), 0xFF212121.toInt(), 0xFFECEFF1.toInt()
    )

    private const val SWATCH_COLS = 6

    fun show(
        context: Context,
        title: String,
        initialColor: Int,
        onPicked: (Int) -> Unit
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_color_picker, null)
        val preview = view.findViewById<View>(R.id.preview)
        val hexLabel = view.findViewById<TextView>(R.id.hexLabel)
        val hueBar = view.findViewById<SeekBar>(R.id.hueBar)
        val satBar = view.findViewById<SeekBar>(R.id.satBar)
        val valBar = view.findViewById<SeekBar>(R.id.valBar)
        val presetsContainer = view.findViewById<LinearLayout>(R.id.presetsContainer)
        val favoritesContainer = view.findViewById<LinearLayout>(R.id.favoritesContainer)
        val favoritesEmpty = view.findViewById<TextView>(R.id.favoritesEmpty)
        val btnAddFavorite = view.findViewById<Button>(R.id.btnAddFavorite)
        val harmonyContainer = view.findViewById<LinearLayout>(R.id.harmonyContainer)

        val hsv = FloatArray(3)
        Color.colorToHSV(initialColor, hsv)
        hueBar.progress = hsv[0].toInt()
        satBar.progress = (hsv[1] * 100).toInt().coerceAtLeast(5)
        valBar.progress = (hsv[2] * 100).toInt().coerceAtLeast(5)

        var suppressListener = false

        fun currentColor(): Int = Color.HSVToColor(
            floatArrayOf(
                hueBar.progress.toFloat(),
                satBar.progress / 100f,
                valBar.progress / 100f
            )
        )

        fun harmony(hueShift: Float): Int {
            val c = FloatArray(3)
            Color.colorToHSV(currentColor(), c)
            c[0] = (c[0] + hueShift + 360f) % 360f
            return Color.HSVToColor(c)
        }

        // Reassigned below once the real body is ready — the empty default
        // keeps buildHarmony() callable during wiring.
        var setColor: (Int) -> Unit = {}

        fun buildHarmony() {
            harmonyContainer.removeAllViews()
            val base = currentColor()
            harmonyContainer.addView(
                harmonyRow(
                    context,
                    context.getString(R.string.harmony_complementary),
                    listOf(base, harmony(180f))
                ) { setColor(it) }
            )
            harmonyContainer.addView(
                harmonyRow(
                    context,
                    context.getString(R.string.harmony_analogous),
                    listOf(base, harmony(-30f), harmony(30f))
                ) { setColor(it) }
            )
            harmonyContainer.addView(
                harmonyRow(
                    context,
                    context.getString(R.string.harmony_triadic),
                    listOf(base, harmony(120f), harmony(240f))
                ) { setColor(it) }
            )
        }

        fun updatePreview() {
            val color = currentColor()
            preview.setBackgroundColor(color)
            hexLabel.text = String.format("#%06X", 0xFFFFFF and color)
            buildHarmony()
        }

        setColor = { color ->
            val c = FloatArray(3)
            Color.colorToHSV(color, c)
            suppressListener = true
            hueBar.progress = c[0].toInt()
            satBar.progress = (c[1] * 100).toInt()
            valBar.progress = (c[2] * 100).toInt()
            suppressListener = false
            updatePreview()
        }

        // Initial paint
        updatePreview()

        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                if (!suppressListener) updatePreview()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        }
        hueBar.setOnSeekBarChangeListener(listener)
        satBar.setOnSeekBarChangeListener(listener)
        valBar.setOnSeekBarChangeListener(listener)

        // Preset grid (static)
        populateSwatchGrid(
            context,
            presetsContainer,
            PRESET_COLORS.toList(),
            onClick = { setColor(it) },
            onLongClick = null
        )

        fun refreshFavorites() {
            val favs = ColorFavoritesStore.all(context)
            favoritesContainer.removeAllViews()
            if (favs.isEmpty()) {
                favoritesEmpty.visibility = View.VISIBLE
            } else {
                favoritesEmpty.visibility = View.GONE
                populateSwatchGrid(
                    context,
                    favoritesContainer,
                    favs,
                    onClick = { setColor(it) },
                    onLongClick = { color ->
                        ColorFavoritesStore.remove(context, color)
                        refreshFavorites()
                        Toast.makeText(
                            context,
                            R.string.color_fav_removed,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        }
        refreshFavorites()

        btnAddFavorite.setOnClickListener {
            ColorFavoritesStore.add(context, currentColor())
            refreshFavorites()
            Toast.makeText(context, R.string.color_fav_added, Toast.LENGTH_SHORT).show()
        }

        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(view)
            .setPositiveButton(R.string.ok) { _, _ -> onPicked(currentColor()) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ------------------------------------------------------------------
    // Swatch layout helpers
    // ------------------------------------------------------------------

    private fun populateSwatchGrid(
        context: Context,
        container: LinearLayout,
        colors: List<Int>,
        onClick: (Int) -> Unit,
        onLongClick: ((Int) -> Unit)?
    ) {
        container.removeAllViews()
        val density = context.resources.displayMetrics.density
        val swatchSize = (40 * density).toInt()
        val gap = (6 * density).toInt()

        colors.chunked(SWATCH_COLS).forEach { rowColors ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = gap }
            }
            rowColors.forEach { color ->
                row.addView(swatch(context, color, swatchSize, gap, onClick, onLongClick))
            }
            container.addView(row)
        }
    }

    private fun swatch(
        context: Context,
        color: Int,
        size: Int,
        gap: Int,
        onClick: (Int) -> Unit,
        onLongClick: ((Int) -> Unit)?
    ): View {
        val density = context.resources.displayMetrics.density
        return View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10 * density
                setColor(color)
                setStroke((1 * density).toInt(), 0x33000000)
            }
            layoutParams = LinearLayout.LayoutParams(size, size).also {
                it.marginEnd = gap
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick(color) }
            if (onLongClick != null) {
                setOnLongClickListener { onLongClick(color); true }
            }
        }
    }

    private fun harmonyRow(
        context: Context,
        label: String,
        colors: List<Int>,
        onClick: (Int) -> Unit
    ): LinearLayout {
        val density = context.resources.displayMetrics.density
        val swatchSize = (44 * density).toInt()
        val gap = (6 * density).toInt()

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = gap }
        }

        row.addView(TextView(context).apply {
            text = label
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                (96 * density).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        })

        colors.forEachIndexed { index, color ->
            val isBase = index == 0
            val s = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 10 * density
                    setColor(color)
                    setStroke((1 * density).toInt(), 0x33000000)
                }
                layoutParams = LinearLayout.LayoutParams(swatchSize, swatchSize).also {
                    it.marginEnd = gap
                }
                if (isBase) {
                    alpha = 0.55f // dimmed reference — not clickable
                } else {
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { onClick(color) }
                }
            }
            row.addView(s)
        }

        return row
    }
}
