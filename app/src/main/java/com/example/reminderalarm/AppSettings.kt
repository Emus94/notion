package com.example.reminderalarm

import android.content.Context

/**
 * Stores user-configurable app-wide preferences that don't belong to
 * any specific subsystem (theme, alarm, etc.).
 */
object AppSettings {
    private const val PREFS = "app_settings_prefs"
    private const val KEY_AUTO_SAVE_PHRASE = "auto_save_phrase"
    const val DEFAULT_AUTO_SAVE_PHRASE = "zapisz zapisz"

    fun getAutoSavePhrase(context: Context): String =
        prefs(context).getString(KEY_AUTO_SAVE_PHRASE, DEFAULT_AUTO_SAVE_PHRASE)
            ?: DEFAULT_AUTO_SAVE_PHRASE

    fun setAutoSavePhrase(context: Context, phrase: String) {
        prefs(context).edit().putString(KEY_AUTO_SAVE_PHRASE, phrase.trim()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
