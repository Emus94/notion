package com.example.reminderalarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object ProjectStore {
    private const val PREFS = "project_prefs"
    private const val KEY = "projects"
    private const val DEFAULT_COLOR: Int = 0xFF1D3557.toInt()

    fun all(context: Context): List<Project> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Project(
                id = o.getLong("id"),
                name = o.getString("name"),
                color = o.optInt("color", DEFAULT_COLOR)
            )
        }.sortedBy { it.name.lowercase() }
    }

    fun byId(context: Context, id: Long): Project? = all(context).firstOrNull { it.id == id }

    fun save(context: Context, project: Project) {
        val list = all(context).toMutableList()
        val idx = list.indexOfFirst { it.id == project.id }
        if (idx >= 0) list[idx] = project else list.add(project)
        write(context, list)
    }

    fun delete(context: Context, id: Long) {
        write(context, all(context).filterNot { it.id == id })
    }

    private fun write(context: Context, list: List<Project>) {
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
