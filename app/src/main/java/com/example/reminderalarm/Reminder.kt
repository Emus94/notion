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
    val tagIds: List<Long> = emptyList()
)
