
package com.ortuspro.service

import com.ortuspro.model.Transaction

class TransactionExtractor {

    fun extract(text:String): List<Transaction> {
        return text.lines()
            .filter { it.isNotBlank() }
            .take(200)
            .map {
                Transaction(
                    date = "",
                    description = it.trim(),
                    debit = null,
                    credit = null,
                    balance = null
                )
            }
    }
}
