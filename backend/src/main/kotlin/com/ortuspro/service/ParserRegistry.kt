package com.ortuspro.service

import com.ortuspro.model.Statement
import com.ortuspro.parser.BankDetector
import com.ortuspro.parser.StatementParser

/**
 * ParserRegistry — resolves and runs the correct parser for a given text.
 */
object ParserRegistry {

    fun parse(text: String): Statement {
        val parser = BankDetector.detectParser(text)
            ?: return Statement(bank = "UNKNOWN")
        return parser.parse(text)
    }

    fun parserFor(bank: String): StatementParser? =
        BankDetector.detectParser(bank)

    fun bankName(text: String): String = BankDetector.detect(text)
}
