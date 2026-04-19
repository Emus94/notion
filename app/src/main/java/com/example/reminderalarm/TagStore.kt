package com.example.reminderalarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object TagStore {
    private const val PREFS = "tag_prefs"
    private const val KEY = "tags"
    private const val DEFAULT_COLOR: Int = 0xFF0077B6.toInt()

    fun all(context: Context): List<Tag> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Tag(
                id = o.getLong("id"),
                name = o.getString("name"),
                color = o.optInt("color", DEFAULT_COLOR)
            )
        }.sortedBy { it.name.lowercase() }
    }

    fun byId(context: Context, id: Long): Tag? = all(context).firstOrNull { it.id == id }

    fun save(context: Context, tag: Tag) {
        val list = all(context).toMutableList()
        val idx = list.indexOfFirst { it.id == tag.id }
        if (idx >= 0) list[idx] = tag else list.add(tag)
        write(context, list)
    }

    fun delete(context: Context, id: Long) {
        write(context, all(context).filterNot { it.id == id })
    }

    private fun write(context: Context, list: List<Tag>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("color", it.color)
            )
        }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
