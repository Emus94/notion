package com.example.reminderalarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tiny JSON-backed reminder store. Avoids extra dependencies.
 */
object ReminderStore {
    private const val PREFS = "reminder_alarm_prefs"
    private const val KEY = "reminders"

    fun all(context: Context): List<Reminder> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val tagIds = mutableListOf<Long>()
            val tagsArray = o.optJSONArray("tagIds")
            if (tagsArray != null) {
                for (t in 0 until tagsArray.length()) tagIds.add(tagsArray.getLong(t))
            }
            Reminder(
                id = o.getLong("id"),
                label = o.getString("label"),
                notes = o.optString("notes", ""),
                triggerAtMillis = o.getLong("triggerAtMillis"),
                enabled = o.optBoolean("enabled", true),
                vibrateOnly = o.optBoolean("vibrateOnly", false),
                recurrence = Recurrence.fromId(o.optString("recurrence", null)),
                projectId = if (o.has("projectId") && !o.isNull("projectId")) o.getLong("projectId") else null,
                tagIds = tagIds,
                imageUri = if (o.has("imageUri") && !o.isNull("imageUri")) o.getString("imageUri") else null,
                priority = Priority.fromId(o.optString("priority", null)),
                customRepeatDays = if (o.has("customRepeatDays") && !o.isNull("customRepeatDays"))
                    o.getInt("customRepeatDays") else null,
                latitude = if (o.has("latitude") && !o.isNull("latitude")) o.getDouble("latitude") else null,
                longitude = if (o.has("longitude") && !o.isNull("longitude")) o.getDouble("longitude") else null,
                radiusMeters = if (o.has("radiusMeters") && !o.isNull("radiusMeters")) o.getDouble("radiusMeters").toFloat() else null,
                locationName = if (o.has("locationName") && !o.isNull("locationName")) o.getString("locationName") else null,
                locationDelayMinutes = o.optInt("locationDelayMinutes", 0)
            )
        }.sortedBy { it.triggerAtMillis }
    }

    fun byId(context: Context, id: Long): Reminder? = all(context).firstOrNull { it.id == id }

    fun save(context: Context, reminder: Reminder) {
        val list = all(context).toMutableList()
        val idx = list.indexOfFirst { it.id == reminder.id }
        if (idx >= 0) list[idx] = reminder else list.add(reminder)
        write(context, list)
    }

    fun delete(context: Context, id: Long) {
        write(context, all(context).filterNot { it.id == id })
    }

    /** Clears a stale project reference from every reminder. */
    fun clearProjectReferences(context: Context, projectId: Long) {
        val updated = all(context).map { r ->
            if (r.projectId == projectId) r.copy(projectId = null) else r
        }
        write(context, updated)
    }

    /** Clears a stale tag reference from every reminder. */
    fun clearTagReferences(context: Context, tagId: Long) {
        val updated = all(context).map { r ->
            if (tagId in r.tagIds) r.copy(tagIds = r.tagIds.filter { it != tagId }) else r
        }
        write(context, updated)
    }

    private fun write(context: Context, list: List<Reminder>) {
        val arr = JSONArray()
        list.forEach {
            val tagsArray = JSONArray()
            it.tagIds.forEach { t -> tagsArray.put(t) }
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("label", it.label)
                    .put("notes", it.notes)
                    .put("triggerAtMillis", it.triggerAtMillis)
                    .put("enabled", it.enabled)
                    .put("vibrateOnly", it.vibrateOnly)
                    .put("recurrence", it.recurrence.id)
                    .put("projectId", it.projectId ?: JSONObject.NULL)
                    .put("tagIds", tagsArray)
                    .put("imageUri", it.imageUri ?: JSONObject.NULL)
                    .put("priority", it.priority.id)
                    .put("customRepeatDays", it.customRepeatDays ?: JSONObject.NULL)
                    .put("latitude", it.latitude ?: JSONObject.NULL)
                    .put("longitude", it.longitude ?: JSONObject.NULL)
                    .put("radiusMeters", it.radiusMeters?.toDouble() ?: JSONObject.NULL)
                    .put("locationName", it.locationName ?: JSONObject.NULL)
                    .put("locationDelayMinutes", it.locationDelayMinutes)
            )
        }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
        // Keep the "Dziś" home-screen widget in sync with whatever
        // just changed. No-op if no widget instances are placed.
        ReminderWidget.requestUpdate(context)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
