package com.ortuspro.parsers

import com.ortuspro.parser.StatementParser

/**
 * St.George Bank parser (Westpac subsidiary — shares same statement format).
 * Date format: DD/MM/YYYY (slash)
 */
class StGeorgeParser : StatementParser {
    override val bankName = "ST_GEORGE"
    override val dateFormat = "slash"
    override val yearTracked = false

    override val detectPatterns = listOf(
        Regex("""St\.?George Bank|St George""", RegexOption.IGNORE_CASE),
        Regex("""stgeorge\.com\.au""", RegexOption.IGNORE_CASE)
    )

    override val extraSkip = Regex(
        listOf(
            """^st\.?george bank""",
            """stgeorge\.com\.au""",
            """date\s+narration\s+debit"""
        ).joinToString("|"),
        RegexOption.IGNORE_CASE
    )

    override val extraStop = Regex("""closing balance""", RegexOption.IGNORE_CASE)

    override fun extractMeta(text: String): Map<String, String> {
        val meta = mutableMapOf<String, String>()
        Regex("""Account Number[:\s]+([\d\s\-]+)""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.trim()?.let { meta["accountNumber"] = it }
        return meta
    }
}
