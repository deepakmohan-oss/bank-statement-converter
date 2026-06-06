package com.ortuspro.parsers

import com.ortuspro.model.Statement
import com.ortuspro.model.Transaction
import com.ortuspro.parser.StatementParser
import com.ortuspro.util.DateParser
import java.time.LocalDate

/**
 * Auswide Bank parser.
 *
 * Format:
 *   - Date: "02 JUL 24" (dd MON YY — 2-digit year, ALL CAPS month)
 *   - Has "Effective Date" second date column — use posting date (first one)
 *   - Columns: Date | Effective Date | Transaction Details | Cheq No | Debit | Credit | Balance
 *   - No "$" prefix on amounts — plain numbers with comma separators
 *   - "Opening Balance" and "Closing Balance" rows to skip
 *   - Continuation lines for transaction reference details
 */
class AuswideParser : StatementParser {
    override val bankName = "AUSWIDE"
    override val dateFormat = null
    override val yearTracked = false

    override val detectPatterns = listOf(
        Regex("""Auswide Bank|AUSWIDE BANK""", RegexOption.IGNORE_CASE),
        Regex("""auswidebank\.com\.au""", RegexOption.IGNORE_CASE)
    )

    override val extraSkip = Regex(
        listOf(
            """^auswide bank""",
            """auswidebank\.com\.au""",
            """^statement summary""",
            """opening balance\s+total debits""",  // header row
            """^date\s+effective date""",            // column header
            """^opening balance\s*$""",
            """^closing balance\s*$""",
            """australian government deposit guarantee""",
            """protected account under banking act""",
            """financial claims scheme""",
            """personal cheque books""",
            """auswide bank advises""",
            """fees & charges changes""",
            """new fees introduced""",
            """cheque withdrawal using""",
            """online banking\s*$""",
            """conduct your banking""",
            """eStatements\s*$""",
            """help save paper""",
            """pass code security""",
            """protect your account""",
            """please check all transactions""",
            """should you require further""",
            """your statement continues""",
            """statement no\. \d""",
            """page no\. \d""",
            """^\*\d+Q"""
        ).joinToString("|"),
        RegexOption.IGNORE_CASE
    )

    override val extraStop = null

    override fun parse(text: String): Statement {
        val meta  = extractMeta(text)
        val lines = text.lines()
        val txns  = mutableListOf<Transaction>()

        val amtRe = Regex("""-?[\d,]+\.\d{2}""")
        // Auswide date: "02 JUL 24" — caps month, 2-digit year
        val dateRe = Regex("""^(\d{1,2}\s+(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)\s+\d{2,4})\s+(.*)$""", RegexOption.IGNORE_CASE)

        var currentDate: LocalDate? = null
        var currentDesc = ""
        var currentAmts = mutableListOf<Double>()
        var prevBal: Double? = null

        fun commit() {
            if (currentDate == null) return
            val amounts = currentAmts.toList()
            if (amounts.isEmpty()) { currentDate = null; return }
            val balance = amounts.last()
            val debit: Double?; val credit: Double?
            when (amounts.size) {
                1 -> { debit = null; credit = null }
                2 -> { val a = amounts[0]
                       if (prevBal != null && balance < prevBal!! - 0.001) { debit = a; credit = null }
                       else { debit = null; credit = a } }
                else -> { debit = amounts[0].takeIf { it != 0.0 }; credit = amounts[1].takeIf { it != 0.0 } }
            }
            if (currentDesc.contains("Opening Balance", ignoreCase = true) ||
                currentDesc.contains("Closing Balance", ignoreCase = true)) {
                currentDate = null; return
            }
            txns.add(Transaction(DateParser.formatForExport(currentDate!!), currentDesc.take(300), debit, credit, balance))
            prevBal = balance; currentDate = null; currentDesc = ""; currentAmts = mutableListOf()
        }

        for (raw in lines) {
            val s = raw.trim()
            if (s.isEmpty() || extraSkip?.containsMatchIn(s) == true) continue

            val m = dateRe.find(s)
            if (m != null) {
                commit()
                val parsed = DateParser.parse(m.groupValues[1])
                if (parsed == null || parsed.year !in 1990..2099) continue
                currentDate = parsed

                var rest = m.groupValues[2].trim()
                // If rest starts with another date (Effective Date), strip it
                val effDate = dateRe.find(rest)
                if (effDate != null && effDate.range.first == 0) rest = effDate.groupValues[2].trim()

                val amounts = amtRe.findAll(rest).map { it.value.replace(",", "").toDouble() }.toList()
                currentDesc = amtRe.replace(rest, "").trim()
                currentAmts = amounts.toMutableList()
            } else if (currentDate != null) {
                // Continuation — could be reference detail, skip if it starts with "From:"
                if (s.startsWith("From:", ignoreCase = true)) continue
                val amounts = amtRe.findAll(s).map { it.value.replace(",", "").toDouble() }.toList()
                currentAmts.addAll(amounts)
                val dp = amtRe.replace(s, "").trim()
                if (dp.isNotBlank()) currentDesc = "$currentDesc $dp".trim()
            }
        }
        commit()

        return Statement(bankName, meta["accountNumber"] ?: "", meta["accountName"] ?: "",
            meta["openingBalance"]?.toDoubleOrNull(), meta["closingBalance"]?.toDoubleOrNull(),
            meta["from"] ?: "", meta["to"] ?: "", txns.filter { it.balance != null })
    }

    override fun extractMeta(text: String): Map<String, String> {
        val meta = mutableMapOf<String, String>()
        Regex("""Account Number\s+([\w\d]+)""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.trim()?.let { meta["accountNumber"] = it }
        Regex("""Account Name\s+(.+)""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.trim()?.let { meta["accountName"] = it }
        Regex("""Statement Period\s+(\d+\s+\w+\s+\d+)\s+to\s+(\d+\s+\w+\s+\d+)""", RegexOption.IGNORE_CASE).find(text)
            ?.let { meta["from"] = it.groupValues[1]; meta["to"] = it.groupValues[2] }
        // "43,751.03 - 0.00 + 2,946.91 = 46,697.94"
        Regex("""([\d,]+\.\d{2})\s+-\s+[\d,]+\.\d{2}\s+\+\s+[\d,]+\.\d{2}\s+=\s+([\d,]+\.\d{2})""").find(text)
            ?.let {
                meta["openingBalance"] = it.groupValues[1].replace(",", "")
                meta["closingBalance"] = it.groupValues[2].replace(",", "")
            }
        return meta
    }
}
