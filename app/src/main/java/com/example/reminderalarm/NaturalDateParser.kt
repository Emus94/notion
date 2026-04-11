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

    /** Polish word numbers, case-insensitive lookup. */
    private val wordNumbers = mapOf(
        "jeden" to 1, "jedna" to 1, "jedną" to 1, "jednej" to 1,
        "dwa" to 2, "dwie" to 2, "dwóch" to 2, "dwu" to 2,
        "trzy" to 3, "trzech" to 3,
        "cztery" to 4, "czterech" to 4,
        "pięć" to 5, "piec" to 5, "pięciu" to 5,
        "sześć" to 6, "szesc" to 6, "sześciu" to 6,
        "siedem" to 7, "siedmiu" to 7,
        "osiem" to 8, "ośmiu" to 8, "osmiu" to 8,
        "dziewięć" to 9, "dziewiec" to 9, "dziewięciu" to 9,
        "dziesięć" to 10, "dziesiec" to 10, "dziesięciu" to 10,
        "jedenaście" to 11, "dwanaście" to 12, "trzynaście" to 13,
        "czternaście" to 14, "piętnaście" to 15, "szesnaście" to 16,
        "siedemnaście" to 17, "osiemnaście" to 18, "dziewiętnaście" to 19,
        "dwadzieścia" to 20, "dwadziescia" to 20,
        "trzydzieści" to 30, "trzydziesci" to 30,
        "czterdzieści" to 40, "czterdziesci" to 40,
        "pięćdziesiąt" to 50, "piecdziesiat" to 50,
        "sześćdziesiąt" to 60
    )

    private val wordNumberAlt = wordNumbers.keys
        .sortedByDescending { it.length }
        .joinToString("|") { Regex.escape(it) }

    private val timeRegex = Regex("""(?<!\d)(\d{1,2})[:.](\d{2})(?!\d)""")

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

    // "za 5 minut" / "za pięć minut" — digits or word numbers
    private val relMinRegex = Regex(
        """(?<!\p{L})za\s+(\d+|$wordNumberAlt)\s*(?:min|minut|minutę|minuty|minuta|minutki)(?!\p{L})""",
        RegexOption.IGNORE_CASE
    )

    // "za 2 godziny" / "za dwie godziny" — digits or word numbers
    private val relHourRegex = Regex(
        """(?<!\p{L})za\s+(\d+|$wordNumberAlt)\s*(?:h|godz|godzin|godziny|godzinę|godzina)(?!\p{L})""",
        RegexOption.IGNORE_CASE
    )

    // "za godzinę" / "za godzine" — implicit 1 hour, no number
    private val relOneHourRegex = Regex(
        """(?<!\p{L})za\s+godzin[ęey](?!\p{L})""",
        RegexOption.IGNORE_CASE
    )

    // "pół godziny" / "za pół godziny" — 30 minutes
    private val relHalfHourRegex = Regex(
        """(?<!\p{L})(?:za\s+)?pół\s+godziny(?!\p{L})""",
        RegexOption.IGNORE_CASE
    )

    // "kwadrans" / "za kwadrans" — 15 minutes
    private val relKwadransRegex = Regex(
        """(?<!\p{L})(?:za\s+)?kwadrans(?!\p{L})""",
        RegexOption.IGNORE_CASE
    )

    // "za chwilę" — 5 minutes
    private val relChwilaRegex = Regex(
        """(?<!\p{L})za\s+chwil[ęe](?!\p{L})""",
        RegexOption.IGNORE_CASE
    )

    fun parse(text: String): Parsed {
        if (text.isBlank()) return Parsed()
        val ranges = mutableListOf<IntRange>()

        // Special-case phrases that fully define the moment. Return
        // immediately on a match so they don't have to fight with the
        // generic "za N x" regex.
        relHalfHourRegex.find(text)?.let { return relativeParsed(it.range, 30) }
        relKwadransRegex.find(text)?.let { return relativeParsed(it.range, 15) }
        relChwilaRegex.find(text)?.let { return relativeParsed(it.range, 5) }
        relOneHourRegex.find(text)?.let { return relativeParsed(it.range, 60) }

        // "za N minut" — digits or word numbers.
        relMinRegex.find(text)?.let { m ->
            val n = parseNumber(m.groupValues[1])
            if (n > 0) {
                return relativeParsed(m.range, n.toLong())
            }
        }

        // "za N godzin" — digits or word numbers.
        relHourRegex.find(text)?.let { m ->
            val n = parseNumber(m.groupValues[1])
            if (n > 0) {
                return relativeParsed(m.range, n.toLong() * 60L)
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

        val dowMatches = dowRegex.findAll(text).toList()
        if (dowMatches.isNotEmpty()) {
            val firstKey = dowMatches.first().value.lowercase()
            val dow = dayOfWeekMap[firstKey]
            if (dow != null) {
                val c = Calendar.getInstance()
                val currentDow = c.get(Calendar.DAY_OF_WEEK)
                var daysToAdd = (dow - currentDow + 7) % 7
                if (daysToAdd == 0) daysToAdd = 7
                c.add(Calendar.DAY_OF_YEAR, daysToAdd)
                datePart = c
            }
            dowMatches.forEach { ranges.add(it.range) }
        }

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
     * Builds a [Parsed] for a relative offset of [minutes] minutes from
     * now, tagging the supplied text range as the source for later
     * stripping.
     */
    private fun relativeParsed(range: IntRange, minutes: Long): Parsed {
        val c = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis() + minutes * 60_000L
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return Parsed(
            datePart = c,
            timeHour = c.get(Calendar.HOUR_OF_DAY),
            timeMinute = c.get(Calendar.MINUTE),
            matchedRanges = listOf(range)
        )
    }

    /** Parses either a literal digit string or a Polish word number. */
    private fun parseNumber(raw: String): Int {
        val trimmed = raw.trim().lowercase()
        return trimmed.toIntOrNull() ?: wordNumbers[trimmed] ?: 0
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
