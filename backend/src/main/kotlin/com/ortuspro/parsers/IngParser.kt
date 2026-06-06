package com.ortuspro.parsers

import com.ortuspro.parser.StatementParser

class IngParser : StatementParser {
    override val bankName = "ING"
    override val dateFormat = "slash"
    override val yearTracked = false

    override val detectPatterns = listOf(
        Regex("""ING Bank|ING Direct""", RegexOption.IGNORE_CASE),
        Regex("""ing\.com\.au""", RegexOption.IGNORE_CASE)
    )

    override val extraSkip = Regex(
        listOf(
            """^ing bank""",
            """^ing direct""",
            """ing\.com\.au""",
            """date\s+description\s+debit"""
        ).joinToString("|"),
        RegexOption.IGNORE_CASE
    )

    override val extraStop = Regex("""closing balance""", RegexOption.IGNORE_CASE)

    override fun extractMeta(text: String): Map<String, String> {
        val meta = mutableMapOf<String, String>()
        Regex("""Account Number[:\s]+([\d\s]+)""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.trim()?.let { meta["accountNumber"] = it }
        return meta
    }
}
