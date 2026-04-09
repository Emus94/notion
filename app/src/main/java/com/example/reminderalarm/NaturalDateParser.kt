package com.example.reminderalarm

import java.util.Calendar

/**
 * Extracts a date and/or time from free-form Polish text in the reminder
 * label, similar to Todoist's quick-add. Intentionally forgiving — no
 * match just returns an empty [Parsed]. Also reports the character
 * ranges that matched so the caller can strip them from the final label.
 */
object NaturalDateParser {

    data class Parsed(
        val datePart: Calendar? = null,
        val timeHour: Int? = null,
        val timeMinute: Int? = null,
        val matchedRanges: List<IntRange> = emptyList()
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

    // Longest alternatives first so the alternation prefers full words
    // ("poniedziałek") over their abbreviations ("pon").
    private val dowAlternation = dayOfWeekMap.keys
        .sortedByDescending { it.length }
        .joinToString("|") { Regex.escape(it) }
    private val dowRegex = Regex(
        """(?<!\p{L})(?:$dowAlternation)(?!\p{L})""",
        RegexOption.IGNORE_CASE
    )

    private val relDayRegex = Regex(
        """(?<!\p{L})(?:pojutrze|jutro|dzisiaj|dziś|dzis)(?!\p{L})""",
        RegexOption.IGNORE_CASE
    )

    private val relMinRegex = Regex(
        """(?<!\p{L})za\s+(\d+)\s*(?:min|minut|minutę|minuty|minuta)(?!\p{L})""",
        RegexOption.IGNORE_CASE
    )

    private val relHourRegex = Regex(
        """(?<!\p{L})za\s+(\d+)\s*(?:h|godz|godzin|godziny|godzinę|godzina)(?!\p{L})""",
        RegexOption.IGNORE_CASE
    )

    fun parse(text: String): Parsed {
        if (text.isBlank()) return Parsed()
        val ranges = mutableListOf<IntRange>()

        // "za N minut" fully defines the moment — return straight away.
        relMinRegex.find(text)?.let { m ->
            val n = m.groupValues[1].toIntOrNull() ?: 0
            if (n > 0) {
                val c = Calendar.getInstance().apply {
                    timeInMillis = System.currentTimeMillis() + n * 60_000L
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                ranges.add(m.range)
                return Parsed(c, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), ranges)
            }
        }

        // "za N godzin"
        relHourRegex.find(text)?.let { m ->
            val n = m.groupValues[1].toIntOrNull() ?: 0
            if (n > 0) {
                val c = Calendar.getInstance().apply {
                    timeInMillis = System.currentTimeMillis() + n * 3_600_000L
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                ranges.add(m.range)
                return Parsed(c, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), ranges)
            }
        }

        var hour: Int? = null
        var minute: Int? = null
        var datePart: Calendar? = null

        // Absolute time HH:MM
        timeRegex.find(text)?.let { m ->
            val h = m.groupValues[1].toIntOrNull()
            val mn = m.groupValues[2].toIntOrNull()
            if (h != null && mn != null && h in 0..23 && mn in 0..59) {
                hour = h
                minute = mn
                ranges.add(m.range)
            }
        }

        // Day of week — first match sets the date, but every occurrence
        // is reported so stripping can remove stray tokens.
        val dowMatches = dowRegex.findAll(text).toList()
        if (dowMatches.isNotEmpty()) {
            val firstKey = dowMatches.first().value.lowercase()
            val dow = dayOfWeekMap[firstKey]
            if (dow != null) {
                val c = Calendar.getInstance()
                val currentDow = c.get(Calendar.DAY_OF_WEEK)
                var daysToAdd = (dow - currentDow + 7) % 7
                if (daysToAdd == 0) daysToAdd = 7 // "pon" on Monday = next Monday
                c.add(Calendar.DAY_OF_YEAR, daysToAdd)
                datePart = c
            }
            dowMatches.forEach { ranges.add(it.range) }
        }

        // Relative day ("jutro", "pojutrze", "dziś")
        val relDayMatches = relDayRegex.findAll(text).toList()
        if (relDayMatches.isNotEmpty()) {
            if (datePart == null) {
                val firstKey = relDayMatches.first().value.lowercase()
                datePart = when (firstKey) {
                    "pojutrze" -> Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 2) }
                    "jutro" -> Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
                    "dziś", "dzis", "dzisiaj" -> Calendar.getInstance()
                    else -> null
                }
            }
            relDayMatches.forEach { ranges.add(it.range) }
        }

        return Parsed(datePart, hour, minute, ranges)
    }

    /**
     * Removes the supplied character ranges from [text] and collapses
     * any resulting stretches of whitespace. Used to clean the reminder
     * label after the parser has consumed the date/time tokens.
     */
    fun stripRanges(text: String, ranges: List<IntRange>): String {
        if (ranges.isEmpty()) return text
        val sorted = ranges.sortedByDescending { it.first }
        var result = text
        for (range in sorted) {
            if (range.first < 0 || range.last >= result.length) continue
            result = result.substring(0, range.first) + result.substring(range.last + 1)
        }
        return result.replace(Regex("\\s+"), " ").trim()
    }
}
