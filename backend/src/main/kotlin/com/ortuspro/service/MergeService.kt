package com.ortuspro.service

import com.ortuspro.model.Statement
import com.ortuspro.model.Transaction

/**
 * MergeService — merges multiple statements (e.g. across months) into one.
 * Sorts by date and removes cross-statement duplicates.
 */
class MergeService {

    private val dupeDetector = DuplicateDetector()

    fun merge(statements: List<Statement>, removeDuplicates: Boolean = true): Statement {
        if (statements.isEmpty()) return Statement(bank = "UNKNOWN")

        val allTxns = statements.flatMap { it.transactions }
            .sortedWith(compareBy({ it.date }, { it.description }))

        val finalTxns = if (removeDuplicates) {
            dupeDetector.detectAcrossStatements(allTxns).unique
        } else {
            allTxns
        }

        return Statement(
            bank             = statements.first().bank,
            accountNumber    = statements.first().accountNumber,
            accountName      = statements.first().accountName,
            openingBalance   = statements.minOfOrNull { it.openingBalance ?: Double.MAX_VALUE }
                                  .takeIf { it != Double.MAX_VALUE },
            closingBalance   = statements.maxOfOrNull { it.closingBalance ?: Double.MIN_VALUE }
                                  .takeIf { it != Double.MIN_VALUE },
            statementFrom    = statements.minOfOrNull { it.statementFrom } ?: "",
            statementTo      = statements.maxOfOrNull { it.statementTo } ?: "",
            transactions     = finalTxns
        )
    }
}
