package com.example.reminderalarm

import android.content.Context

/**
 * Stores user-configurable app-wide preferences that don't belong to
 * any specific subsystem (theme, alarm, etc.).
 */
object AppSettings {
    private const val PREFS = "app_settings_prefs"
    private const val KEY_AUTO_SAVE_PHRASE = "auto_save_phrase"
    private const val KEY_SHAKE_SNOOZE = "shake_snooze_enabled"
    private const val KEY_SHAKE_SNOOZE_MINUTES = "shake_snooze_minutes"
    private const val KEY_WELCOME_DONE = "welcome_done"
    const val DEFAULT_AUTO_SAVE_PHRASE = "zapisz zapisz"
    const val DEFAULT_SHAKE_SNOOZE_MINUTES = 5

    fun getAutoSavePhrase(context: Context): String =
        prefs(context).getString(KEY_AUTO_SAVE_PHRASE, DEFAULT_AUTO_SAVE_PHRASE)
            ?: DEFAULT_AUTO_SAVE_PHRASE

    fun setAutoSavePhrase(context: Context, phrase: String) {
        prefs(context).edit().putString(KEY_AUTO_SAVE_PHRASE, phrase.trim()).apply()
    }

    /**
     * Opt-in: shake the phone while an alarm is ringing to snooze it
     * for [shakeSnoozeMinutes]. Off by default so users who forget
     * the alarm on a moving surface don't dismiss it accidentally.
     */
    fun isShakeSnoozeEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHAKE_SNOOZE, false)

    fun setShakeSnoozeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHAKE_SNOOZE, enabled).apply()
    }

    fun shakeSnoozeMinutes(context: Context): Int =
        prefs(context).getInt(KEY_SHAKE_SNOOZE_MINUTES, DEFAULT_SHAKE_SNOOZE_MINUTES)

    fun setShakeSnoozeMinutes(context: Context, minutes: Int) {
        prefs(context).edit().putInt(KEY_SHAKE_SNOOZE_MINUTES, minutes).apply()
    }

    // Custom background tints for light/dark display modes.
    private const val KEY_LIGHT_BG = "custom_light_bg"
    private const val KEY_DARK_BG = "custom_dark_bg"

    fun getCustomLightBg(context: Context): Int? {
        val p = prefs(context)
        return if (p.contains(KEY_LIGHT_BG)) p.getInt(KEY_LIGHT_BG, 0) else null
    }

    fun setCustomLightBg(context: Context, color: Int?) {
        val e = prefs(context).edit()
        if (color == null) e.remove(KEY_LIGHT_BG) else e.putInt(KEY_LIGHT_BG, color)
        e.apply()
    }

    fun getCustomDarkBg(context: Context): Int? {
        val p = prefs(context)
        return if (p.contains(KEY_DARK_BG)) p.getInt(KEY_DARK_BG, 0) else null
    }

    fun setCustomDarkBg(context: Context, color: Int?) {
        val e = prefs(context).edit()
        if (color == null) e.remove(KEY_DARK_BG) else e.putInt(KEY_DARK_BG, color)
        e.apply()
    }

    /** First-launch welcome / permission guide flag. */
    fun isWelcomeCompleted(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WELCOME_DONE, false)

    fun setWelcomeCompleted(context: Context, done: Boolean) {
        prefs(context).edit().putBoolean(KEY_WELCOME_DONE, done).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
