package com.ortuspro.service

import com.ortuspro.model.Transaction

/**
 * DuplicateDetector — finds duplicate transactions within or across statements.
 * Uses exact key matching + amount tolerance for near-duplicates.
 */
class DuplicateDetector {

    data class DuplicateResult(
        val unique: List<Transaction>,
        val duplicates: List<Transaction>
    )

    /** Exact duplicate: same date + description + debit + credit. */
    fun detect(transactions: List<Transaction>): DuplicateResult {
        val seen = mutableSetOf<String>()
        val unique = mutableListOf<Transaction>()
        val dupes  = mutableListOf<Transaction>()

        for (tx in transactions) {
            val key = "${tx.date}|${tx.description.trim().uppercase()}|${tx.debit}|${tx.credit}"
            if (seen.add(key)) unique.add(tx) else dupes.add(tx)
        }
        return DuplicateResult(unique, dupes)
    }

    /**
     * Cross-statement duplicate detection for merged statements.
     * Flags any transaction appearing in more than one statement with same date+amount.
     */
    fun detectAcrossStatements(allTransactions: List<Transaction>): DuplicateResult {
        val keyCount = mutableMapOf<String, Int>()
        for (tx in allTransactions) {
            val key = "${tx.date}|${(tx.debit ?: tx.credit)}"
            keyCount[key] = (keyCount[key] ?: 0) + 1
        }
        val unique = mutableListOf<Transaction>()
        val dupes  = mutableListOf<Transaction>()
        val seen   = mutableSetOf<String>()

        for (tx in allTransactions) {
            val key = "${tx.date}|${(tx.debit ?: tx.credit)}"
            if ((keyCount[key] ?: 0) > 1) {
                if (!seen.add(key)) dupes.add(tx) else unique.add(tx)
            } else {
                unique.add(tx)
            }
        }
        return DuplicateResult(unique, dupes)
    }
}
