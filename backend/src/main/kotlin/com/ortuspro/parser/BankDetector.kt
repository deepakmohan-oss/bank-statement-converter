package com.ortuspro.parser

import com.ortuspro.parsers.*

/**
 * BankDetector — confidence-score based detection across all registered parsers.
 * Returns the highest-confidence parser, or null if nothing scores above 0.
 */
object BankDetector {

    private val parsers = listOf(
        CommonwealthParser(),
        NabParser(),
        WestpacParser(),
        AnzParser(),
        MacquarieParser(),
        BoqParser(),
        AuswideParser(),
        BankwestParser(),
        BendigoParser(),
        IngParser(),
        StGeorgeParser()
    )

    fun detectParser(text: String): StatementParser? =
        parsers
            .map { it to it.detect(text) }
            .filter { (_, conf) -> conf > 0.0 }
            .maxByOrNull { (_, conf) -> conf }
            ?.first

    fun detect(text: String): String = detectParser(text)?.bankName ?: "UNKNOWN"
}
