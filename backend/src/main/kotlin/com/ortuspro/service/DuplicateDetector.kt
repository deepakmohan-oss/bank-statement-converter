package com.ortuspro.service

import com.ortuspro.model.Transaction

class DuplicateDetector {
    fun findDuplicates(txns: List<Transaction>): List<Transaction> {
        val seen = mutableSetOf<String>()
        return txns.filter {
            !seen.add("${it.date}|${it.description}|${it.debit}|${it.credit}")
        }
    }
}
