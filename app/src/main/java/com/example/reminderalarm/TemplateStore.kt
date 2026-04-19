package com.example.reminderalarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON-backed storage for reminder templates. Mirrors the shape of
 * [ReminderStore] but for the [Template] type (no trigger time).
 */
object TemplateStore {
    private const val PREFS = "template_prefs"
    private const val KEY = "templates"

    fun all(context: Context): List<Template> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val tagIds = mutableListOf<Long>()
            o.optJSONArray("tagIds")?.let { ta ->
                for (t in 0 until ta.length()) tagIds.add(ta.getLong(t))
            }
            Template(
                id = o.getLong("id"),
                name = o.getString("name"),
                label = o.optString("label", ""),
                notes = o.optString("notes", ""),
                vibrateOnly = o.optBoolean("vibrateOnly", false),
                recurrence = Recurrence.fromId(o.optString("recurrence", null)),
                priority = Priority.fromId(o.optString("priority", null)),
                projectId = if (o.has("projectId") && !o.isNull("projectId")) o.getLong("projectId") else null,
                tagIds = tagIds
            )
        }.sortedBy { it.name.lowercase() }
    }

    fun byId(context: Context, id: Long): Template? =
        all(context).firstOrNull { it.id == id }

    fun save(context: Context, template: Template) {
        val list = all(context).toMutableList()
        val idx = list.indexOfFirst { it.id == template.id }
        if (idx >= 0) list[idx] = template else list.add(template)
        write(context, list)
    }

    fun delete(context: Context, id: Long) {
        write(context, all(context).filterNot { it.id == id })
    }

    private fun write(context: Context, list: List<Template>) {
        val arr = JSONArray()
        list.forEach { t ->
            val tagsArray = JSONArray()
            t.tagIds.forEach { id -> tagsArray.put(id) }
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("name", t.name)
                    .put("label", t.label)
                    .put("notes", t.notes)
                    .put("vibrateOnly", t.vibrateOnly)
                    .put("recurrence", t.recurrence.id)
                    .put("priority", t.priority.id)
                    .put("projectId", t.projectId ?: JSONObject.NULL)
                    .put("tagIds", tagsArray)
            )
        }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
