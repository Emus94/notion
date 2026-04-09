package com.example.reminderalarm

import android.content.Context
import android.graphics.Color
import androidx.annotation.StyleRes

/**
 * Persists and applies the selected color palette. For built-in palettes
 * the colors come from XML styles; the CUSTOM palette reads its primary
 * colour from prefs and falls back to derived values for dark/accent —
 * unless the user has overridden those individually too.
 */
object ThemeManager {
    private const val PREFS = "theme_prefs"
    private const val KEY_PALETTE = "palette"
    private const val KEY_CUSTOM_PRIMARY = "custom_primary"
    private const val KEY_CUSTOM_DARK = "custom_dark"
    private const val KEY_CUSTOM_ACCENT = "custom_accent"

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
        MIST(
            "mist", "Mgła", R.style.Theme_ReminderAlarm_Mist,
            0xFFB4A7D6.toInt(), 0xFF7B6BA8.toInt(), 0xFFE8B4BC.toInt()
        ),
        PAPER(
            "paper", "Papier", R.style.Theme_ReminderAlarm_Paper,
            0xFFD4A574.toInt(), 0xFF8B6F47.toInt(), 0xFFC84B31.toInt()
        ),
        DAWN(
            "dawn", "Świt", R.style.Theme_ReminderAlarm_Dawn,
            0xFFFFAD8F.toInt(), 0xFFCC6F5C.toInt(), 0xFFFF5E7A.toInt()
        ),

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

    /** Stored custom dark color, or null if the user hasn't overridden it. */
    fun customDark(context: Context): Int? {
        val p = prefs(context)
        return if (p.contains(KEY_CUSTOM_DARK)) p.getInt(KEY_CUSTOM_DARK, 0) else null
    }

    /** Stored custom accent color, or null if the user hasn't overridden it. */
    fun customAccent(context: Context): Int? {
        val p = prefs(context)
        return if (p.contains(KEY_CUSTOM_ACCENT)) p.getInt(KEY_CUSTOM_ACCENT, 0) else null
    }

    fun setCustomPrimary(context: Context, color: Int) {
        prefs(context).edit()
            .putString(KEY_PALETTE, Palette.CUSTOM.id)
            .putInt(KEY_CUSTOM_PRIMARY, color)
            .apply()
    }

    fun setCustomDark(context: Context, color: Int?) {
        val edit = prefs(context).edit()
        if (color == null) edit.remove(KEY_CUSTOM_DARK) else edit.putInt(KEY_CUSTOM_DARK, color)
        edit.apply()
    }

    fun setCustomAccent(context: Context, color: Int?) {
        val edit = prefs(context).edit()
        if (color == null) edit.remove(KEY_CUSTOM_ACCENT) else edit.putInt(KEY_CUSTOM_ACCENT, color)
        edit.apply()
    }

    fun primaryColor(context: Context): Int {
        val p = current(context)
        return if (p == Palette.CUSTOM) customPrimary(context) else p.primary
    }

    fun primaryDarkColor(context: Context): Int {
        val p = current(context)
        if (p != Palette.CUSTOM) return p.primaryDark
        customDark(context)?.let { return it }
        return darken(customPrimary(context), 0.7f)
    }

    fun accentColor(context: Context): Int {
        val p = current(context)
        if (p != Palette.CUSTOM) return p.accent
        customAccent(context)?.let { return it }
        return complement(customPrimary(context))
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
