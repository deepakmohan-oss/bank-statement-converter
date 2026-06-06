package com.ortuspro.service

import com.ortuspro.model.Statement
import com.ortuspro.model.Transaction
import com.ortuspro.parser.BankDetector

/**
 * TransactionExtractor — orchestrates PDF text → Statement extraction.
 */
class TransactionExtractor {

    fun extract(text: String): Statement {
        val parser = BankDetector.detectParser(text)
            ?: return Statement(bank = "UNKNOWN", transactions = emptyList())
        return parser.parse(text)
    }

    fun extractTransactions(text: String): List<Transaction> = extract(text).transactions
}
