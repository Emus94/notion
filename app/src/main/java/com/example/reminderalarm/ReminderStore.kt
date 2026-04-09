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
            Reminder(
                id = o.getLong("id"),
                label = o.getString("label"),
                notes = o.optString("notes", ""),
                triggerAtMillis = o.getLong("triggerAtMillis"),
                enabled = o.optBoolean("enabled", true),
                vibrateOnly = o.optBoolean("vibrateOnly", false),
                recurrence = Recurrence.fromId(o.optString("recurrence", null))
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

    private fun write(context: Context, list: List<Reminder>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("label", it.label)
                    .put("notes", it.notes)
                    .put("triggerAtMillis", it.triggerAtMillis)
                    .put("enabled", it.enabled)
                    .put("vibrateOnly", it.vibrateOnly)
                    .put("recurrence", it.recurrence.id)
            )
        }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
