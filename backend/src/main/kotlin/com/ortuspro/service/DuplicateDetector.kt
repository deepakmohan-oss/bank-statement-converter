
package com.ortuspro.service

import com.ortuspro.model.Transaction

class DuplicateDetector {
    fun detect(transactions: List<Transaction>): List<Transaction> {
        val seen = mutableSetOf<String>()
        return transactions.filter {
            !seen.add("${it.date}|${it.description}|${it.debit}|${it.credit}")
        }
    }
}
