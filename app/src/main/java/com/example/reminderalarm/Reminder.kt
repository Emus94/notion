package com.example.reminderalarm

data class Reminder(
    val id: Long,
    val label: String,
    val notes: String = "",
    val triggerAtMillis: Long,
    val enabled: Boolean = true,
    val vibrateOnly: Boolean = false,
    val recurrence: Recurrence = Recurrence.NONE,
    val projectId: Long? = null,
    val tagIds: List<Long> = emptyList(),
    val imageUri: String? = null,
    val priority: Priority = Priority.NORMAL
)
