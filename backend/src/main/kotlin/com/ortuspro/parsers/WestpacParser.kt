package com.ortuspro.parsers

import com.ortuspro.model.Statement
import com.ortuspro.model.Transaction
import com.ortuspro.parser.StatementParser
import com.ortuspro.util.DateParser
import java.time.LocalDate

/**
 * Westpac parser — handles 3 distinct Westpac statement formats:
 *
 * Format A: "Westpac DIY Super Working Account" / classic statement
 *   - Date: DD/MM/YY slash, 3 columns Debit | Credit | Balance
 *
 * Format B: "Statement of recent transactions" (Business One Plus)
 *   - Date: DD MMM YYYY (abbr_year), NO running balance column
 *   - Columns: Date | Description | Withdrawal | Deposit
 *   - Balance reconstructed from "Current balance" header (subtract forward from end)
 *   - Transactions in REVERSE chronological order
 *
 * Format C: "Transactions report" (Business One)
 *   - Date: DD MMM YYYY, columns: Date | Description | Withdrawal | Deposit | Balance
 *   - Amounts have leading "-" for withdrawals, "+" for deposits
 *   - Description split across 2–3 lines
 */
class WestpacParser : StatementParser {
    override val bankName = "WESTPAC"
    override val dateFormat = null
    override val yearTracked = false

    override val detectPatterns = listOf(
        Regex("""Westpac Banking|Westpac Business""", RegexOption.IGNORE_CASE),
        Regex("""westpac\.com\.au|Westpac Group""", RegexOption.IGNORE_CASE)
    )

    override val extraSkip = Regex(
        listOf(
            """^westpac banking corporation""",
            """westpac\.com\.au""",
            """^date\s+narration""",
            """^date\s+transaction description""",
            """statement opening balance""",
            """closing balance""",
            """convenience at your fingertips""",
            """use online.*mobile.*tablet""",
            """copyright.*westpac""",
            """date created:""",
            """this report covers""",
            """this report shows only""",
            """current balance:""",
            """start balance""",
            """end balance""",
            """total withdrawals""",
            """total deposits""",
            """things you should know""",
            """account opened:""",
            """account/card number""",
            """^interest rates""",
            """effective date.*over""",
            """annual information""",
            """for the period \d""",
            """total interest credited""",
            """^page \d+ of \d+$"""
        ).joinToString("|"),
        RegexOption.IGNORE_CASE
    )

    override val extraStop = Regex(
        """^closing balance|^statement closing""",
        RegexOption.IGNORE_CASE
    )

    override fun parse(text: String): Statement {
        val meta = extractMeta(text)

        // Detect which sub-format
        return when {
            text.contains("Statement of recent transactions", ignoreCase = true) ->
                parseNoBalanceFormat(text, meta)
            text.contains("Transactions report", ignoreCase = true) ||
            text.contains("Start Balance", ignoreCase = true) ->
                parseTransactionsReport(text, meta)
            else ->
                parseClassicFormat(text, meta)
        }
    }

    // ── Format A: Classic DD/MM/YY with Debit | Credit | Balance ──────────────
    private fun parseClassicFormat(text: String, meta: Map<String, String>): Statement {
        val txns = mutableListOf<Transaction>()
        val lines = text.lines()
        val dateRe = Regex("""^(\d{1,2}/\d{1,2}/\d{2,4})\s+(.+)$""")
        val amtRe  = Regex("""-?[\d,]+\.\d{2}""")
        var currentDate: LocalDate? = null
        var currentDesc = ""
        var currentLine = ""

        fun commit() {
            if (currentDate == null || currentLine.isBlank()) return
            val amounts = amtRe.findAll(currentLine).map { it.value.replace(",", "").toDouble() }.toList()
            if (amounts.isEmpty()) return
            val balance = amounts.last()
            val debit   = if (amounts.size >= 2 && amounts[0] != 0.0) amounts[0] else null
            val credit  = if (amounts.size >= 3 && amounts[1] != 0.0) amounts[1] else
                          if (amounts.size == 2 && amounts[0] == 0.0) null else null
            txns.add(Transaction(
                date        = DateParser.formatForExport(currentDate!!),
                description = cleanWestpacDesc(currentDesc),
                debit       = debit,
                credit      = credit,
                balance     = balance
            ))
        }

        for (line in lines) {
            val s = line.trim()
            if (s.isEmpty() || extraSkip?.containsMatchIn(s) == true) continue
            val m = dateRe.find(s)
            if (m != null) {
                commit()
                currentDate = DateParser.parse(m.groupValues[1])
                currentDesc = amtRe.replace(m.groupValues[2], "").trim()
                currentLine = m.groupValues[2]
            } else if (currentDate != null) {
                if (amtRe.containsMatchIn(s)) currentLine += " $s"
                else currentDesc += " $s"
            }
        }
        commit()

        return Statement(
            bank             = bankName,
            accountNumber    = meta["accountNumber"] ?: "",
            openingBalance   = meta["openingBalance"]?.toDoubleOrNull(),
            closingBalance   = meta["closingBalance"]?.toDoubleOrNull(),
            statementFrom    = meta["from"] ?: "",
            statementTo      = meta["to"] ?: "",
            transactions     = txns
        )
    }

    // ── Format B: "Statement of recent transactions" — no running balance ──────
    // Transactions in REVERSE order. Balance reconstructed from current balance.
    private fun parseNoBalanceFormat(text: String, meta: Map<String, String>): Statement {
        val lines = text.lines()
        val dateRe = Regex("""^(\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\s+\d{4})\s+(.*)$""", RegexOption.IGNORE_CASE)
        val amtRe  = Regex("""-?\$[\d,]+\.\d{2}""")
        val amtClean = Regex("""[\$,]""")

        data class RawTx(val date: LocalDate, val desc: String, val withdrawal: Double?, val deposit: Double?)

        val rawTxns = mutableListOf<RawTx>()
        var currentDate: LocalDate? = null
        var currentDesc = ""
        var currentWd: Double? = null
        var currentDep: Double? = null

        fun commitRaw() {
            if (currentDate == null) return
            rawTxns.add(RawTx(currentDate!!, cleanWestpacDesc(currentDesc), currentWd, currentDep))
            currentDate = null; currentDesc = ""; currentWd = null; currentDep = null
        }

        for (line in lines) {
            val s = line.trim()
            if (s.isEmpty() || extraSkip?.containsMatchIn(s) == true) continue
            val m = dateRe.find(s)
            if (m != null) {
                commitRaw()
                val parsed = DateParser.parse(m.groupValues[1])
                if (parsed == null || parsed.year !in 2000..2099) continue
                currentDate = parsed
                val rest = m.groupValues[2]
                val amounts = amtRe.findAll(rest).map { amtClean.replace(it.value, "").toDouble() }.toList()
                currentDesc = amtRe.replace(rest, "").trim()
                // Assign: negative = withdrawal, positive = deposit
                for (a in amounts) {
                    if (a < 0) currentWd = -a else currentDep = a
                }
            } else if (currentDate != null) {
                val amounts = amtRe.findAll(s).map { amtClean.replace(it.value, "").toDouble() }.toList()
                if (amounts.isNotEmpty()) {
                    for (a in amounts) { if (a < 0) currentWd = -a else currentDep = a }
                } else {
                    currentDesc += " $s"
                }
            }
        }
        commitRaw()

        // Transactions came out in reverse — sort ascending by date
        rawTxns.sortBy { it.date }

        // Reconstruct running balance: currentBalance is END balance
        val endBalance = meta["closingBalance"]?.toDoubleOrNull()
            ?: rawTxns.size.let { 0.0 } // fallback

        // Walk backwards to compute opening balance
        var runBal = endBalance
        val reversed = rawTxns.reversed()
        val balances = mutableListOf<Double>()
        for (tx in reversed) {
            balances.add(0, runBal)
            runBal -= (tx.deposit ?: 0.0)
            runBal += (tx.withdrawal ?: 0.0)
        }

        val txns = rawTxns.zip(balances).map { (tx, bal) ->
            Transaction(
                date        = DateParser.formatForExport(tx.date),
                description = tx.desc,
                debit       = tx.withdrawal,
                credit      = tx.deposit,
                balance     = bal
            )
        }

        return Statement(
            bank             = bankName,
            accountNumber    = meta["accountNumber"] ?: "",
            closingBalance   = endBalance,
            statementFrom    = meta["from"] ?: "",
            statementTo      = meta["to"] ?: "",
            transactions     = txns
        )
    }

    // ── Format C: "Transactions report" — has balance column, signed amounts ───
    private fun parseTransactionsReport(text: String, meta: Map<String, String>): Statement {
        val lines = text.lines()
        val dateRe = Regex("""^(\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\s+\d{4})\s+(.*)$""", RegexOption.IGNORE_CASE)
        val amtRe  = Regex("""-?\$[\d,]+\.\d{2}|\+\$[\d,]+\.\d{2}""")
        val amtClean = Regex("""[\$,+]""")

        val txns = mutableListOf<Transaction>()
        var currentDate: LocalDate? = null
        var currentDesc = ""
        var currentWd: Double? = null
        var currentDep: Double? = null
        var currentBal: Double? = null

        fun assignAmounts(amounts: List<Double>) {
            // Last amount is always balance (positive), first is wd (negative) or dep (positive)
            if (amounts.isEmpty()) return
            currentBal = Math.abs(amounts.last())
            if (amounts.size >= 2) {
                val first = amounts[0]
                if (first < 0) currentWd = -first else currentDep = first
            }

        }
        fun commit() {
            if (currentDate == null || currentBal == null) return
            txns.add(Transaction(
                date        = DateParser.formatForExport(currentDate!!),
                description = cleanWestpacDesc(currentDesc),
                debit       = currentWd,
                credit      = currentDep,
                balance     = currentBal!!
            ))
        }

        for (line in lines) {
            val s = line.trim()
            if (s.isEmpty() || extraSkip?.containsMatchIn(s) == true) continue
            val m = dateRe.find(s)
            if (m != null) {
                commit()
                val parsed = DateParser.parse(m.groupValues[1])
                if (parsed == null || parsed.year !in 2000..2099) { currentDate = null; continue }
                currentDate = parsed
                currentWd = null; currentDep = null; currentBal = null
                val rest = m.groupValues[2]
                val amounts = amtRe.findAll(rest).map { amtClean.replace(it.value, "").toDouble() }.toList()
                currentDesc = amtRe.replace(rest, "").trim()
                assignAmounts(amounts)
            } else if (currentDate != null) {
                val amounts = amtRe.findAll(s).map { amtClean.replace(it.value, "").toDouble() }.toList()
                if (amounts.isNotEmpty()) assignAmounts(amounts)
                else if (!s.contains(Regex("""card no\.|~\d{6}""", RegexOption.IGNORE_CASE)))
                    currentDesc += " $s"
            }
        }
        commit()

        }

        return Statement(
            bank             = bankName,
            accountNumber    = meta["accountNumber"] ?: "",
            openingBalance   = meta["openingBalance"]?.toDoubleOrNull(),
            closingBalance   = meta["closingBalance"]?.toDoubleOrNull(),
            statementFrom    = meta["from"] ?: "",
            statementTo      = meta["to"] ?: "",
            transactions     = txns
        )
    }

    private fun cleanWestpacDesc(desc: String): String {
        return desc
            .replace(Regex("""Card No\.\s*~\d+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""~\d{4,}"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(300)
    }

    override fun extractMeta(text: String): Map<String, String> {
        val meta = mutableMapOf<String, String>()
        Regex("""Account/Card number\s*\n?\s*([\d\-\s]+)""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.trim()?.let { meta["accountNumber"] = it }
        Regex("""(\d{3}-\d{3}\s+\d{6,})""").find(text)
            ?.groupValues?.get(1)?.trim()?.let { meta["accountNumber"] = it }
        Regex("""Account Number\s+([\d\s]+)""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.trim()?.let { if (meta["accountNumber"].isNullOrBlank()) meta["accountNumber"] = it }
        Regex("""Opening Balance.*?\+([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.replace(",", "")?.let { meta["openingBalance"] = it }
        Regex("""Closing Balance.*?\+([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.replace(",", "")?.let { meta["closingBalance"] = it }
        // "Statement of recent transactions" format — current balance at top
        Regex("""Current balance:\s*\$?([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.replace(",", "")?.let { meta["closingBalance"] = it }
        Regex("""End Balance.*?\+([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.replace(",", "")?.let { meta["closingBalance"] = it }
        Regex("""from\s+(\d{1,2}[/-]\w+[/-]?\d*)\s+to\s+(\w.+)""", RegexOption.IGNORE_CASE).find(text)
            ?.let { meta["from"] = it.groupValues[1]; meta["to"] = it.groupValues[2].trim() }
        return meta
    }
}
