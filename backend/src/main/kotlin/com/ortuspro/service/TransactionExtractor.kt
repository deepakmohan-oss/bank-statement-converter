
package com.ortuspro.service

import com.ortuspro.model.Transaction

class TransactionExtractor {
    fun extract(text:String): List<Transaction> {
        return text.lines()
            .filter { it.isNotBlank() }
            .take(100)
            .map {
                Transaction("", it.trim(), null, null, null)
            }
    }
}
