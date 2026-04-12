package com.example.reminderalarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object PlaceStore {
    private const val PREFS = "place_prefs"
    private const val KEY = "places"

    fun all(context: Context): List<Place> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Place(
                id = o.getLong("id"),
                name = o.getString("name"),
                latitude = o.getDouble("latitude"),
                longitude = o.getDouble("longitude"),
                radiusMeters = o.optDouble("radiusMeters", 150.0).toFloat()
            )
        }.sortedBy { it.name.lowercase() }
    }

    fun byId(context: Context, id: Long): Place? =
        all(context).firstOrNull { it.id == id }

    fun save(context: Context, place: Place) {
        val list = all(context).toMutableList()
        val idx = list.indexOfFirst { it.id == place.id }
        if (idx >= 0) list[idx] = place else list.add(place)
        write(context, list)
    }

    fun delete(context: Context, id: Long) {
        write(context, all(context).filterNot { it.id == id })
    }

    /**
     * Finds the first place whose name appears (case-insensitive) as a
     * word boundary match in [text]. Used by the reminder form to auto-
     * attach a location when the user types a known place name.
     */
    fun findInText(context: Context, text: String): Place? {
        if (text.isBlank()) return null
        val lower = text.lowercase()
        return all(context)
            .sortedByDescending { it.name.length } // longest match first
            .firstOrNull { lower.contains(it.name.lowercase()) }
    }

    private fun write(context: Context, list: List<Place>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("latitude", p.latitude)
                    .put("longitude", p.longitude)
                    .put("radiusMeters", p.radiusMeters.toDouble())
            )
        }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
