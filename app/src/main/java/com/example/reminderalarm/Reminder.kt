package com.example.reminderalarm

data class Reminder(
    val id: Long,
    val label: String,
    val triggerAtMillis: Long,
    val enabled: Boolean = true
)
