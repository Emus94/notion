package com.example.reminderalarm

import android.content.Context
import android.graphics.Color
import androidx.annotation.StyleRes

/**
 * Persists and applies the selected color palette. For built-in palettes the
 * colors come from XML styles; for the CUSTOM palette the user-picked color
 * is stored in prefs and applied programmatically by activities.
 */
object ThemeManager {
    private const val PREFS = "theme_prefs"
    private const val KEY_PALETTE = "palette"
    private const val KEY_CUSTOM_PRIMARY = "custom_primary"

    enum class Palette(
        val id: String,
        val displayName: String,
        @StyleRes val themeRes: Int,
        val primary: Int,
        val primaryDark: Int,
        val accent: Int
    ) {
        BLUE(
            "blue", "Granat", R.style.Theme_ReminderAlarm_Blue,
            0xFF1D3557.toInt(), 0xFF0D1B2A.toInt(), 0xFFE63946.toInt()
        ),
        BOTTLE_GREEN(
            "bottle_green", "Butelkowa zieleń", R.style.Theme_ReminderAlarm_BottleGreen,
            0xFF1B4332.toInt(), 0xFF081C15.toInt(), 0xFFD4A373.toInt()
        ),
        SKY_BLUE(
            "sky_blue", "Błękit", R.style.Theme_ReminderAlarm_SkyBlue,
            0xFF0077B6.toInt(), 0xFF023E8A.toInt(), 0xFFFFB703.toInt()
        ),
        INDIGO(
            "indigo", "Indygo", R.style.Theme_ReminderAlarm_Indigo,
            0xFF3A0CA3.toInt(), 0xFF240046.toInt(), 0xFFF72585.toInt()
        ),
        TEAL(
            "teal", "Turkus", R.style.Theme_ReminderAlarm_Teal,
            0xFF006D77.toInt(), 0xFF023047.toInt(), 0xFFE29578.toInt()
        ),

        /** CUSTOM uses Blue as the XML base; colors are resolved from prefs at runtime. */
        CUSTOM(
            "custom", "Własny…", R.style.Theme_ReminderAlarm_Blue,
            0, 0, 0
        );

        companion object {
            fun fromId(id: String?): Palette = values().firstOrNull { it.id == id } ?: BLUE
        }
    }

    fun current(context: Context): Palette =
        Palette.fromId(prefs(context).getString(KEY_PALETTE, null))

    fun set(context: Context, palette: Palette) {
        prefs(context).edit().putString(KEY_PALETTE, palette.id).apply()
    }

    fun setCustom(context: Context, primaryColor: Int) {
        prefs(context).edit()
            .putString(KEY_PALETTE, Palette.CUSTOM.id)
            .putInt(KEY_CUSTOM_PRIMARY, primaryColor)
            .apply()
    }

    fun customPrimary(context: Context): Int =
        prefs(context).getInt(KEY_CUSTOM_PRIMARY, 0xFF1D3557.toInt())

    fun primaryColor(context: Context): Int {
        val p = current(context)
        return if (p == Palette.CUSTOM) customPrimary(context) else p.primary
    }

    fun primaryDarkColor(context: Context): Int {
        val p = current(context)
        return if (p == Palette.CUSTOM) darken(customPrimary(context), 0.7f) else p.primaryDark
    }

    fun accentColor(context: Context): Int {
        val p = current(context)
        return if (p == Palette.CUSTOM) complement(customPrimary(context)) else p.accent
    }

    /** Produces a darker shade by scaling HSV value. */
    private fun darken(color: Int, factor: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] * factor).coerceIn(0f, 1f)
        return Color.HSVToColor(hsv)
    }

    /** Returns a complementary color for accents. */
    private fun complement(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[0] = (hsv[0] + 180f) % 360f
        hsv[1] = 0.8f
        hsv[2] = 0.9f
        return Color.HSVToColor(hsv)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
