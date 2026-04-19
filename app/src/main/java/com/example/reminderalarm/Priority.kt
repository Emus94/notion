package com.example.reminderalarm

/**
 * Urgency level applied to a reminder. Shown in the list as a small
 * colored label next to the time; URGENT also boldens the title. NONE
 * default means "normalny" and is never rendered — the vast majority
 * of reminders fall into this bucket and a badge on every card would
 * just be visual noise.
 */
enum class Priority(
    val id: String,
    val displayName: String,
    val color: Int,
    val marker: String
) {
    LOW("low", "Niski", 0xFF4CAF50.toInt(), "▼"),
    NORMAL("normal", "Normalny", 0xFF9E9E9E.toInt(), ""),
    HIGH("high", "Wysoki", 0xFFFF9800.toInt(), "▲"),
    URGENT("urgent", "Pilny", 0xFFE53935.toInt(), "‼");

    companion object {
        fun fromId(id: String?): Priority =
            values().firstOrNull { it.id == id } ?: NORMAL
    }
}
