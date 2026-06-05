package com.ortuspro.parsers

import com.ortuspro.model.Statement
import com.ortuspro.model.Transaction
import com.ortuspro.parser.StatementParser

class CommonwealthParser : StatementParser {
    override fun parse(text: String): Statement {
        return Statement(
            bankName = "COMMONWEALTH",
            accountNumber = "",
            transactions = listOf(
                Transaction("", "Parser scaffold", null, null, null)
            )
        )
    }
}
