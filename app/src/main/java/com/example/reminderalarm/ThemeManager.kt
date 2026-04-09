package com.example.reminderalarm

import android.content.Context
import androidx.annotation.StyleRes

/**
 * Persists and applies the selected color palette. Activities call
 * [current] in onCreate (via [BaseActivity]) before super.onCreate()
 * so the theme applies before the view is inflated.
 */
object ThemeManager {
    private const val PREFS = "theme_prefs"
    private const val KEY = "palette"

    enum class Palette(
        val id: String,
        val displayName: String,
        @StyleRes val themeRes: Int
    ) {
        BLUE("blue", "Granat", R.style.Theme_ReminderAlarm_Blue),
        BOTTLE_GREEN("bottle_green", "Butelkowa zieleń", R.style.Theme_ReminderAlarm_BottleGreen),
        SKY_BLUE("sky_blue", "Błękit", R.style.Theme_ReminderAlarm_SkyBlue),
        INDIGO("indigo", "Indygo", R.style.Theme_ReminderAlarm_Indigo),
        TEAL("teal", "Turkus", R.style.Theme_ReminderAlarm_Teal);

        companion object {
            fun fromId(id: String?): Palette = values().firstOrNull { it.id == id } ?: BLUE
        }
    }

    fun current(context: Context): Palette =
        Palette.fromId(prefs(context).getString(KEY, null))

    fun set(context: Context, palette: Palette) {
        prefs(context).edit().putString(KEY, palette.id).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
