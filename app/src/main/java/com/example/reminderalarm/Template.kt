package com.example.reminderalarm

/**
 * A saved skeleton the user can reuse to create reminders quickly.
 *
 * Unlike [Reminder] a template has no trigger time, no enabled flag
 * and no image — the user supplies the time at the moment of creation.
 * Everything else (label, notes, recurrence, priority, project, tags,
 * vibrate-only) carries over as a default the user can still tweak.
 */
data class Template(
    val id: Long,
    val name: String,
    val label: String,
    val notes: String = "",
    val vibrateOnly: Boolean = false,
    val recurrence: Recurrence = Recurrence.NONE,
    val priority: Priority = Priority.NORMAL,
    val projectId: Long? = null,
    val tagIds: List<Long> = emptyList()
)
