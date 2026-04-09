package com.example.reminderalarm

import java.util.Calendar

/**
 * Extracts a date and/or time from free-form Polish text in the reminder
 * label, similar to Todoist's quick-add. Intentionally forgiving — no
 * match just returns an empty [Parsed].
 */
object NaturalDateParser {

    data class Parsed(
        val datePart: Calendar? = null,
        val timeHour: Int? = null,
        val timeMinute: Int? = null
    ) {
        fun hasAny() = datePart != null || timeHour != null
    }

    private val dayOfWeekMap = mapOf(
        "pon" to Calendar.MONDAY,
        "poniedziałek" to Calendar.MONDAY,
        "poniedzialek" to Calendar.MONDAY,
        "wt" to Calendar.TUESDAY,
        "wtorek" to Calendar.TUESDAY,
        "śr" to Calendar.WEDNESDAY,
        "sr" to Calendar.WEDNESDAY,
        "środa" to Calendar.WEDNESDAY,
        "sroda" to Calendar.WEDNESDAY,
        "czw" to Calendar.THURSDAY,
        "czwartek" to Calendar.THURSDAY,
        "pt" to Calendar.FRIDAY,
        "piątek" to Calendar.FRIDAY,
        "piatek" to Calendar.FRIDAY,
        "sob" to Calendar.SATURDAY,
        "sobota" to Calendar.SATURDAY,
        "nd" to Calendar.SUNDAY,
        "niedz" to Calendar.SUNDAY,
        "niedziela" to Calendar.SUNDAY
    )

    private val timeRegex = Regex("""(?<!\d)(\d{1,2})[:.](\d{2})(?!\d)""")
    private val relMinRegex = Regex(
        """\bza\s+(\d+)\s*(?:min|minut|minutę|minuty|minuta)\b""",
        RegexOption.IGNORE_CASE
    )
    private val relHourRegex = Regex(
        """\bza\s+(\d+)\s*(?:h|godz|godzin|godziny|godzinę|godzina)\b""",
        RegexOption.IGNORE_CASE
    )

    fun parse(text: String): Parsed {
        if (text.isBlank()) return Parsed()

        val lower = text.lowercase()

        // "za N minut" — fully defines the moment; return immediately.
        relMinRegex.find(lower)?.let { m ->
            val n = m.groupValues[1].toIntOrNull() ?: 0
            if (n > 0) {
                val c = Calendar.getInstance().apply {
                    timeInMillis = System.currentTimeMillis() + n * 60_000L
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                return Parsed(c, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
            }
        }

        // "za N godzin"
        relHourRegex.find(lower)?.let { m ->
            val n = m.groupValues[1].toIntOrNull() ?: 0
            if (n > 0) {
                val c = Calendar.getInstance().apply {
                    timeInMillis = System.currentTimeMillis() + n * 3_600_000L
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                return Parsed(c, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
            }
        }

        var hour: Int? = null
        var minute: Int? = null

        timeRegex.find(text)?.let { m ->
            val h = m.groupValues[1].toIntOrNull()
            val mn = m.groupValues[2].toIntOrNull()
            if (h != null && mn != null && h in 0..23 && mn in 0..59) {
                hour = h
                minute = mn
            }
        }

        var datePart: Calendar? = null

        // Tokenize by whitespace and punctuation for date keyword matching.
        val tokens = lower
            .split(Regex("""[\s,.!?;:()\[\]{}/\\-]+"""))
            .filter { it.isNotEmpty() }

        // Day of week — pick the first matching token.
        for (token in tokens) {
            val dow = dayOfWeekMap[token] ?: continue
            val c = Calendar.getInstance()
            val currentDow = c.get(Calendar.DAY_OF_WEEK)
            var daysToAdd = (dow - currentDow + 7) % 7
            if (daysToAdd == 0) daysToAdd = 7 // "pon" on Monday = next Monday
            c.add(Calendar.DAY_OF_YEAR, daysToAdd)
            datePart = c
            break
        }

        // Relative day keywords (only if no weekday already picked).
        if (datePart == null) {
            when {
                "pojutrze" in tokens -> datePart = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, 2)
                }
                "jutro" in tokens -> datePart = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
                "dziś" in tokens || "dzis" in tokens || "dzisiaj" in tokens ->
                    datePart = Calendar.getInstance()
            }
        }

        return Parsed(datePart, hour, minute)
    }
}
