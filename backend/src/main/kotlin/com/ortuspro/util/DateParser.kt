package com.ortuspro.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * DateParser — handles all Australian bank statement date formats.
 * Ported from the Python parser's multi-format auto-detection logic.
 */
object DateParser {

    private val SLASH_FMTS  = listOf("d/M/yy", "d/M/yyyy")
    private val DOT_FMTS    = listOf("d.M.yy", "d.M.yyyy")
    private val ABBR_FMTS   = listOf("d MMM yyyy", "d MMMM yyyy", "d MMM yy", "d MMMM yy")
    private val HYPHEN_FMTS = listOf("d-MMM-yy", "d-MMMM-yy", "d-MMM-yyyy", "d-MMMM-yyyy")

    fun parse(raw: String, yearHint: Int = LocalDate.now().year): LocalDate? {
        val s = raw.trim()
        // Try formats in order: slash, dot, abbr with year, abbr without year (inject hint), hyphen
        return tryFormats(s, SLASH_FMTS)
            ?: tryFormats(s, DOT_FMTS)
            ?: tryFormats(s, ABBR_FMTS)
            ?: tryFormats("$s $yearHint", ABBR_FMTS)   // inject year for abbr-no-year
            ?: tryFormats(s, HYPHEN_FMTS)
            ?: tryFormats("$s-$yearHint", HYPHEN_FMTS) // inject year for hyphen-no-year
    }

    private fun tryFormats(s: String, fmts: List<String>): LocalDate? {
        for (fmt in fmts) {
            try {
                return LocalDate.parse(s, DateTimeFormatter.ofPattern(fmt, java.util.Locale.ENGLISH))
            } catch (_: DateTimeParseException) {}
        }
        return null
    }

    /**
     * Auto-detect which date format a list of lines uses.
     * Returns one of: "slash", "dot", "abbr", "abbr_year", "hyphen", "unknown"
     */
    fun detectFormat(lines: List<String>): String {
        val counts = mutableMapOf("slash" to 0, "dot" to 0, "abbr" to 0, "abbr_year" to 0, "hyphen" to 0)
        for (line in lines) {
            val s = line.trim()
            when {
                RegexLibrary.DATE_SLASH.containsMatchIn(s)     -> counts["slash"] = counts["slash"]!! + 1
                RegexLibrary.DATE_DOT.containsMatchIn(s)       -> counts["dot"] = counts["dot"]!! + 1
                RegexLibrary.DATE_ABBR_YEAR.containsMatchIn(s) -> counts["abbr_year"] = counts["abbr_year"]!! + 1
                RegexLibrary.DATE_ABBR.containsMatchIn(s)      -> counts["abbr"] = counts["abbr"]!! + 1
                RegexLibrary.DATE_HYPHEN.containsMatchIn(s)    -> counts["hyphen"] = counts["hyphen"]!! + 1
            }
        }
        return counts.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key ?: "unknown"
    }

    fun formatForExport(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
}
