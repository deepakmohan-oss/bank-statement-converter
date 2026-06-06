package com.ortuspro.parsers

import com.ortuspro.model.Statement
import com.ortuspro.model.Transaction
import com.ortuspro.parser.StatementParser
import com.ortuspro.util.DateParser
import java.time.LocalDate

/**
 * Macquarie Bank CMA parser.
 *
 * Format: "DD.MM.YY  TxnType  Description  debits  credits  balance"
 *   - Date: 28.06.24 (dd.MM.yy dot-separated)
 *   - Transaction type BEFORE description: "Deposit", "Funds transfer", "Interest", "BPAY"
 *   - pdfbox collapses type+desc+amounts onto one line
 *   - Known transaction types to strip from description prefix
 *   - CLOSING BALANCE line: "CLOSING BALANCE AS AT DD MMM YY  totalDebit  totalCredit  balance"
 *   - "OPENING BALANCE" and "CLOSING BALANCE" lines to skip
 */
class MacquarieParser : StatementParser {
    override val bankName = "MACQUARIE"
    override val dateFormat = "dot"
    override val yearTracked = false

    override val detectPatterns = listOf(
        Regex("""Macquarie Bank|Macquarie Cash Management""", RegexOption.IGNORE_CASE),
        Regex("""macquarie\.com\.au|MACQUARIE BANK LIMITED""", RegexOption.IGNORE_CASE)
    )

    // Known transaction type prefixes that appear between date and description
    private val TXN_TYPE_RE = Regex(
        """^(Deposit|Funds transfer|Interest|BPAY|Withdrawal|Transfer|Salary|Direct Credit|Direct Debit)\s+""",
        RegexOption.IGNORE_CASE
    )

    override val extraSkip = Regex(
        listOf(
            """^macquarie bank""",
            """macquarie\.com\.au""",
            """macquarie group""",
            """^page \d+ of \d+""",
            """statement no\.""",
            """^from \d""",
            """^account balance""",
            """^as at \d""",
            """^account name""",
            """^account no\.""",
            """^bsb \d""",
            """transaction\s+description\s+debits""",
            """we offer several options""",
            """about your account""",
            """protect your account""",
            """access to and sharing""",
            """visit our help centre""",
            """how you can keep""",
            """download macquarie authenticator""",
            """continued on next page""",
            """closing balance as at""",   // summary line — skip (has totals not a real txn)
            """opening balance""",
            """annual interest summary""",
            """interest paid\s+\d""",
            """total income paid""",
            """stepped interest rates"""
        ).joinToString("|"),
        RegexOption.IGNORE_CASE
    )

    override val extraStop = Regex(
        """we offer several options""",
        RegexOption.IGNORE_CASE
    )

    override fun parse(text: String): Statement {
        val meta  = extractMeta(text)
        val lines = text.lines()
        val txns  = mutableListOf<Transaction>()

        val dateRe  = Regex("""^(\d{1,2}\.\d{2}\.\d{2})\s+(.*)$""")
        val amtRe   = Regex("""-?[\d,]+\.\d{2}""")

        var currentDate: LocalDate? = null
        var currentDesc = ""
        var currentAmts = mutableListOf<Double>()
        var prevBal: Double? = null

        fun commit() {
            if (currentDate == null) return
            val amounts = currentAmts.toList()
            if (amounts.isEmpty()) { currentDate = null; return }

            val balance = amounts.last()
            val debit: Double?
            val credit: Double?
            when (amounts.size) {
                1 -> { debit = null; credit = null }
                2 -> {
                    val amt = amounts[0]
                    debit  = if (prevBal != null && balance < prevBal!! - 0.001) amt else null
                    credit = if (debit == null) amt else null
                }
                else -> {
                    debit  = amounts[0].takeIf { it != 0.0 }
                    credit = amounts[1].takeIf { it != 0.0 }
                }
            }

            txns.add(Transaction(
                date        = DateParser.formatForExport(currentDate!!),
                description = currentDesc.take(300),
                debit       = debit,
                credit      = credit,
                balance     = balance
            ))
            prevBal = balance
            currentDate = null; currentDesc = ""; currentAmts = mutableListOf()
        }

        for (raw in lines) {
            val s = raw.trim()
            if (s.isEmpty() || extraSkip?.containsMatchIn(s) == true) continue
            if (extraStop?.containsMatchIn(s) == true) { commit(); break }

            val m = dateRe.find(s)
            if (m != null) {
                commit()
                val parsed = DateParser.parse(m.groupValues[1])
                if (parsed == null || parsed.year !in 1990..2099) continue
                currentDate = parsed

                // Strip transaction type prefix from rest
                var rest = m.groupValues[2].trim()
                rest = TXN_TYPE_RE.replace(rest, "")

                val amounts = amtRe.findAll(rest).map { it.value.replace(",", "").toDouble() }.toList()
                currentDesc = amtRe.replace(rest, "")
                    .replace(Regex("""\*"""), "")
                    .replace(Regex("""\s+"""), " ").trim()
                currentAmts = amounts.toMutableList()
            } else if (currentDate != null) {
                if (extraStop?.containsMatchIn(s) == true) { commit(); break }
                val amounts = amtRe.findAll(s).map { it.value.replace(",", "").toDouble() }.toList()
                currentAmts.addAll(amounts)
                val descPart = amtRe.replace(s, "")
                    .replace(Regex("""\*"""), "")
                    .replace(Regex("""\s+"""), " ").trim()
                if (descPart.isNotBlank()) currentDesc = "$currentDesc $descPart".trim()
            }
        }
        commit()

        return Statement(
            bank             = bankName,
            accountNumber    = meta["accountNumber"] ?: "",
            accountName      = meta["accountName"] ?: "",
            openingBalance   = meta["openingBalance"]?.toDoubleOrNull(),
            closingBalance   = meta["closingBalance"]?.toDoubleOrNull(),
            statementFrom    = meta["from"] ?: "",
            statementTo      = meta["to"] ?: "",
            transactions     = txns.filter { it.balance != null }
        )
    }

    override fun extractMeta(text: String): Map<String, String> {
        val meta = mutableMapOf<String, String>()
        Regex("""account no\.\s*([\d]+)""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.trim()?.let { meta["accountNumber"] = it }
        Regex("""account name\s+(.+)""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.trim()?.let { meta["accountName"] = it }
        Regex("""from\s+(\d{1,2}[\.\s]\w+[\.\s]\d{2,4})\s+to\s+(\d{1,2}[\.\s]\w+[\.\s]\d{2,4})""", RegexOption.IGNORE_CASE).find(text)
            ?.let { meta["from"] = it.groupValues[1]; meta["to"] = it.groupValues[2] }
        // Closing balance from header line "account balance $2,328.89"
        Regex("""account balance\s*\$?([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.replace(",", "")?.let { meta["closingBalance"] = it }
        // From CLOSING BALANCE line: last 3 numbers are totalDebit totalCredit balance
        Regex("""CLOSING BALANCE AS AT[^\n]+\s+([\d,]+\.\d{2})\s+([\d,]+\.\d{2})\s+([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(3)?.replace(",", "")?.let { meta["closingBalance"] = it }
        return meta
    }
}
