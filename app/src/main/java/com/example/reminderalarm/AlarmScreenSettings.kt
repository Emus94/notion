package com.example.reminderalarm

import android.content.Context
import android.net.Uri

/**
 * Persists user preferences for the alarm screen: the ringtone, volume,
 * background color and the layout preset.
 */
object AlarmScreenSettings {
    private const val PREFS = "alarm_screen_prefs"
    private const val KEY_LAYOUT = "layout"
    private const val KEY_BG = "background"
    private const val KEY_SOUND_URI = "sound_uri"
    private const val KEY_VOLUME = "volume"

    const val DEFAULT_BG: Int = 0xFF0D1B2A.toInt()

    /** Layout preset for the alarm screen. */
    enum class Layout(val id: String, val displayName: String) {
        TIME_FOCUS("time", "Godzina jako główna"),
        TASK_FOCUS("task", "Zadanie jako główne"),
        MINIMAL("minimal", "Minimalny (bez godziny)");

        companion object {
            fun fromId(id: String?): Layout =
                values().firstOrNull { it.id == id } ?: TIME_FOCUS
        }
    }

    fun getLayout(context: Context): Layout =
        Layout.fromId(prefs(context).getString(KEY_LAYOUT, null))

    fun setLayout(context: Context, layout: Layout) {
        prefs(context).edit().putString(KEY_LAYOUT, layout.id).apply()
    }

    fun getBackgroundColor(context: Context): Int =
        prefs(context).getInt(KEY_BG, DEFAULT_BG)

    fun setBackgroundColor(context: Context, color: Int) {
        prefs(context).edit().putInt(KEY_BG, color).apply()
    }

    fun getSoundUri(context: Context): Uri? =
        prefs(context).getString(KEY_SOUND_URI, null)?.let { Uri.parse(it) }

    fun setSoundUri(context: Context, uri: Uri?) {
        prefs(context).edit().apply {
            if (uri == null) remove(KEY_SOUND_URI)
            else putString(KEY_SOUND_URI, uri.toString())
        }.apply()
    }

    /** Volume 0..100, or -1 if the system alarm volume should be kept. */
    fun getVolume(context: Context): Int =
        prefs(context).getInt(KEY_VOLUME, -1)

    fun setVolume(context: Context, volume: Int) {
        prefs(context).edit().putInt(KEY_VOLUME, volume).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
