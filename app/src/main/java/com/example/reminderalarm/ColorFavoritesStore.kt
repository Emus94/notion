package com.example.reminderalarm

import android.content.Context

/**
 * Persists a running list of colors the user has marked as favourites in
 * the color picker. Most-recent first; adding a color that's already in
 * the list just bumps it to the front. Capped at [MAX] entries so the
 * grid stays a couple of rows at most.
 */
object ColorFavoritesStore {

    private const val PREFS = "color_favorites_prefs"
    private const val KEY = "favorites"
    private const val MAX = 18

    fun all(context: Context): List<Int> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        if (raw.isEmpty()) return emptyList()
        return raw.split(",").mapNotNull { it.toIntOrNull() }
    }

    fun add(context: Context, color: Int) {
        val list = all(context).toMutableList()
        list.remove(color) // move-to-front if already present
        list.add(0, color)
        while (list.size > MAX) list.removeAt(list.size - 1)
        write(context, list)
    }

    fun remove(context: Context, color: Int) {
        write(context, all(context).filter { it != color })
    }

    private fun write(context: Context, list: List<Int>) {
        prefs(context).edit()
            .putString(KEY, list.joinToString(",") { it.toString() })
            .apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
