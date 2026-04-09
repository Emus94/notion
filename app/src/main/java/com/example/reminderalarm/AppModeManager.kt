package com.example.reminderalarm

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Persists and applies day/night mode independently of the color palette.
 */
object AppModeManager {
    private const val PREFS = "theme_prefs"
    private const val KEY = "mode"

    enum class Mode(
        val id: String,
        val displayName: String,
        val nightMode: Int
    ) {
        LIGHT("light", "Jasny", AppCompatDelegate.MODE_NIGHT_NO),
        DARK("dark", "Ciemny", AppCompatDelegate.MODE_NIGHT_YES),
        SYSTEM("system", "Systemowy", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        companion object {
            fun fromId(id: String?): Mode = values().firstOrNull { it.id == id } ?: SYSTEM
        }
    }

    fun current(context: Context): Mode =
        Mode.fromId(prefs(context).getString(KEY, null))

    fun set(context: Context, mode: Mode) {
        prefs(context).edit().putString(KEY, mode.id).apply()
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
    }

    fun applyAtStartup(context: Context) {
        AppCompatDelegate.setDefaultNightMode(current(context).nightMode)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
