package com.example.reminderalarm

import android.content.Context

/**
 * Persists the user's preferred sort order for the main reminders list.
 */
object ListSortManager {
    private const val PREFS = "list_prefs"
    private const val KEY_SORT = "sort"

    enum class Sort(val id: String, val displayName: String) {
        DATE_ASC("date_asc", "Data — rosnąco"),
        DATE_DESC("date_desc", "Data — malejąco"),
        NAME_ASC("name_asc", "Nazwa — A–Z"),
        PROJECT("project", "Projekt");

        companion object {
            fun fromId(id: String?): Sort = values().firstOrNull { it.id == id } ?: DATE_ASC
        }
    }

    fun current(context: Context): Sort =
        Sort.fromId(prefs(context).getString(KEY_SORT, null))

    fun set(context: Context, sort: Sort) {
        prefs(context).edit().putString(KEY_SORT, sort.id).apply()
    }

    /** Applies the currently selected sort order to a reminder list. */
    fun apply(context: Context, reminders: List<Reminder>): List<Reminder> =
        when (current(context)) {
            Sort.DATE_ASC -> reminders.sortedBy { it.triggerAtMillis }
            Sort.DATE_DESC -> reminders.sortedByDescending { it.triggerAtMillis }
            Sort.NAME_ASC -> reminders.sortedBy { it.label.lowercase() }
            Sort.PROJECT -> reminders.sortedWith(
                compareBy({ it.projectId ?: Long.MAX_VALUE }, { it.triggerAtMillis })
            )
        }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
