package com.ortuspro.parsers

import com.ortuspro.parser.StatementParser

class BendigoParser : StatementParser {
    override val bankName = "BENDIGO"
    override val dateFormat = "slash"
    override val yearTracked = false

    override val detectPatterns = listOf(
        Regex("""Bendigo Bank|Bendigo and Adelaide""", RegexOption.IGNORE_CASE),
        Regex("""bendigobank\.com\.au""", RegexOption.IGNORE_CASE)
    )

    override val extraSkip = Regex(
        listOf(
            """^bendigo bank""",
            """^bendigo and adelaide bank""",
            """bendigobank\.com\.au""",
            """date\s+description\s+debit"""
        ).joinToString("|"),
        RegexOption.IGNORE_CASE
    )

    override val extraStop = Regex("""closing balance""", RegexOption.IGNORE_CASE)

    override fun extractMeta(text: String): Map<String, String> {
        val meta = mutableMapOf<String, String>()
        Regex("""Account[:\s]+([\d\s\-]+)""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.trim()?.let { meta["accountNumber"] = it }
        return meta
    }
}
