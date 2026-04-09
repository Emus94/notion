package com.example.reminderalarm

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.SeekBar
import android.widget.TextView

/**
 * Reusable HSV color picker shared between the theme picker and the
 * alarm screen background picker.
 */
object ColorPickerHelper {
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

        val hsv = FloatArray(3)
        Color.colorToHSV(initialColor, hsv)
        hueBar.progress = hsv[0].toInt()
        satBar.progress = (hsv[1] * 100).toInt().coerceAtLeast(10)
        valBar.progress = (hsv[2] * 100).toInt().coerceAtLeast(10)

        fun currentColor(): Int = Color.HSVToColor(
            floatArrayOf(
                hueBar.progress.toFloat(),
                satBar.progress / 100f,
                valBar.progress / 100f
            )
        )

        fun update() {
            val color = currentColor()
            preview.setBackgroundColor(color)
            hexLabel.text = String.format("#%06X", 0xFFFFFF and color)
        }
        update()

        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) = update()
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        }
        hueBar.setOnSeekBarChangeListener(listener)
        satBar.setOnSeekBarChangeListener(listener)
        valBar.setOnSeekBarChangeListener(listener)

        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(view)
            .setPositiveButton(R.string.ok) { _, _ -> onPicked(currentColor()) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
