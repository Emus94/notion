package com.example.reminderalarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

/**
 * Serialises / deserialises all app data (reminders, projects, tags) to a
 * single JSON file so the user can share it, archive it, or restore it.
 *
 * Format:
 * {
 *   "version": 1,
 *   "exportedAt": <epoch ms>,
 *   "reminders": [...],
 *   "projects":  [...],
 *   "tags":      [...]
 * }
 *
 * Import merges by id — existing items with the same id are overwritten,
 * new items are added. Alarms are rescheduled for future reminders.
 */
object ExportImportManager {

    const val EXPORT_MIME = "application/json"
    private const val SCHEMA_VERSION = 1

    // -----------------------------------------------------------------
    // Export
    // -----------------------------------------------------------------

    fun exportToStream(context: Context, out: OutputStream) {
        val root = JSONObject()
        root.put("version", SCHEMA_VERSION)
        root.put("exportedAt", System.currentTimeMillis())

        // Reminders
        val remindersArr = JSONArray()
        ReminderStore.all(context).forEach { r ->
            val tagsArr = JSONArray().also { a -> r.tagIds.forEach { t -> a.put(t) } }
            remindersArr.put(
                JSONObject()
                    .put("id", r.id)
                    .put("label", r.label)
                    .put("notes", r.notes)
                    .put("triggerAtMillis", r.triggerAtMillis)
                    .put("enabled", r.enabled)
                    .put("vibrateOnly", r.vibrateOnly)
                    .put("recurrence", r.recurrence.id)
                    .put("projectId", r.projectId ?: JSONObject.NULL)
                    .put("tagIds", tagsArr)
                    .put("imageUri", r.imageUri ?: JSONObject.NULL)
                    .put("priority", r.priority.id)
                    .put("customRepeatDays", r.customRepeatDays ?: JSONObject.NULL)
            )
        }
        root.put("reminders", remindersArr)

        // Projects
        val projectsArr = JSONArray()
        ProjectStore.all(context).forEach { p ->
            projectsArr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("color", p.color)
            )
        }
        root.put("projects", projectsArr)

        // Tags
        val tagsArr = JSONArray()
        TagStore.all(context).forEach { t ->
            tagsArr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("name", t.name)
                    .put("color", t.color)
            )
        }
        root.put("tags", tagsArr)

        out.writer(Charsets.UTF_8).use { it.write(root.toString(2)) }
    }

    // -----------------------------------------------------------------
    // Import
    // -----------------------------------------------------------------

    data class ImportResult(val reminders: Int, val projects: Int, val tags: Int)

    fun importFromStream(context: Context, input: InputStream): ImportResult {
        val json = input.bufferedReader(Charsets.UTF_8).readText()
        val root = JSONObject(json)

        // Projects first — reminders may reference them.
        var projectCount = 0
        root.optJSONArray("projects")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                ProjectStore.save(
                    context, Project(
                        id = o.getLong("id"),
                        name = o.getString("name"),
                        color = o.optInt("color", 0xFF1D3557.toInt())
                    )
                )
                projectCount++
            }
        }

        // Tags
        var tagCount = 0
        root.optJSONArray("tags")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                TagStore.save(
                    context, Tag(
                        id = o.getLong("id"),
                        name = o.getString("name"),
                        color = o.optInt("color", 0xFF0077B6.toInt())
                    )
                )
                tagCount++
            }
        }

        // Reminders
        var reminderCount = 0
        root.optJSONArray("reminders")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val tagIds = mutableListOf<Long>()
                o.optJSONArray("tagIds")?.let { ta ->
                    for (t in 0 until ta.length()) tagIds.add(ta.getLong(t))
                }
                val r = Reminder(
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
                        o.getInt("customRepeatDays") else null
                )
                ReminderStore.save(context, r)
                if (r.enabled && r.triggerAtMillis > System.currentTimeMillis()) {
                    AlarmScheduler.schedule(context, r)
                }
                reminderCount++
            }
        }

        return ImportResult(reminderCount, projectCount, tagCount)
    }
}
