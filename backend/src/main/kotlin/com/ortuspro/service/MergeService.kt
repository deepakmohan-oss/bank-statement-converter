
package com.ortuspro.service

import com.ortuspro.model.Statement
import com.ortuspro.model.Transaction

class MergeService {
    fun merge(statements: List<Statement>): List<Transaction> =
        statements.flatMap { it.transactions }.sortedBy { it.date }
}
