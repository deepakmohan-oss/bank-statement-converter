package com.ortuspro.parsers

import com.ortuspro.model.Statement
import com.ortuspro.model.Transaction
import com.ortuspro.parser.StatementParser
import com.ortuspro.util.DateParser
import java.time.LocalDate

/**
 * Macquarie Bank parser — two formats:
 *
 * Format A: Classic CMA Statement
 *   - Date: DD.MM.YY  (e.g. 28.06.24)
 *   - Transaction type prefix before description
 *
 * Format B: Transaction Listing Report
 *   - Month-year group headers: "Aug 2024"
 *   - Transaction date rows:   "Aug 19  DESCRIPTION  amounts  balance CR"
 *   - Balance inferred from delta vs previous balance
 */
class MacquarieParser : StatementParser {
    override val bankName = "MACQUARIE"
    override val dateFormat = "dot"
    override val yearTracked = false

    override val detectPatterns = listOf(
        Regex("""Macquarie Bank Limited|MACQUARIE BANK LIMITED|Macquarie Cash Management""", RegexOption.IGNORE_CASE),
        Regex("""macquarie\.com\.au|Cash Management Accelerator|Transaction Listing Report""", RegexOption.IGNORE_CASE)
    )

    private val TXN_TYPE_RE = Regex(
        """^(Deposit|Funds transfer|Interest|BPAY|Withdrawal|Transfer|Salary|Direct Credit|Direct Debit)\s+""",
        RegexOption.IGNORE_CASE
    )

    private val MONTH_MAP = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5,  "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
    )

    private val MONTH_YEAR_RE = Regex(
        """^(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\s+(20\d{2})\s*$""",
        RegexOption.IGNORE_CASE
    )

    private val TXN_DATE_RE = Regex(
        """^(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\s+(\d{1,2})\s+(.*)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val FORMAT_B_SKIP = Regex(
        listOf(
            """^opening balance""", """^closing balance""",
            """^date\s+description""", """^your transactions""",
            """please check each entry""", """check we have your""",
            """^end of transaction listing""",
            """this information is provided by macquarie""",
            """periodic statement to conduct""",
            """may not include all transactions""",
            """page \d+ of \d+""",
            """^from \d{1,2}\s+\w+\s+\d{4}""",
            """^overview of this transaction""",
            """opening balance\s*-\s*total debits""",
            """^\$[\d,]+\.\d{2}\s+\$[\d,]+\.\d{2}""",
            """^clearance\.\s*$""", """^away\.\s*$""",
            """we recommend you use your""", """awaiting$"""
        ).joinToString("|"),
        RegexOption.IGNORE_CASE
    )

    override val extraSkip = Regex(
        listOf(
            """^macquarie bank""", """macquarie\.com\.au""", """macquarie group""",
            """^page \d+ of \d+""", """statement no\.""", """^from \d""",
            """^account balance""", """^as at \d""",
            """^account name""", """^account no\.""", """^bsb \d""",
            """transaction\s+description\s+debits""",
            """we offer several options""", """about your account""",
            """protect your account""", """access to and sharing""",
            """visit our help centre""", """how you can keep""",
            """download macquarie authenticator""", """continued on next page""",
            """closing balance as at""", """opening balance""",
            """annual interest summary""", """interest paid\s+\d""",
            """total income paid""", """stepped interest rates""",
            """your security""", """it connects seamlessly""",
            """deny login""", """the apple logo""", """google logo"""
        ).joinToString("|"),
        RegexOption.IGNORE_CASE
    )

    override val extraStop = Regex("""we offer several options""", RegexOption.IGNORE_CASE)

    override fun parse(text: String): Statement {
        return if (text.contains("Transaction Listing Report", ignoreCase = true))
            parseFormatB(text)
        else
            parseFormatA(text)
    }

    // ── Format A: Classic CMA dot-date ────────────────────────────────────────
    private fun parseFormatA(text: String): Statement {
        val meta  = extractMeta(text)
        val lines = text.lines()
        val txns  = mutableListOf<Transaction>()
        val dateRe = Regex("""^(\d{1,2}\.\d{2}\.\d{2})\s+(.*)$""")
        val amtRe  = Regex("""-?[\d,]+\.\d{2}""")
        var currentDate: LocalDate? = null; var currentDesc = ""
        var currentAmts = mutableListOf<Double>(); var prevBal: Double? = null

        fun commit() {
            if (currentDate == null) return
            val amounts = currentAmts.toList()
            if (amounts.isEmpty()) { currentDate = null; return }
            val balance = amounts.last()
            val debit: Double?; val credit: Double?
            when (amounts.size) {
                1    -> { debit = null; credit = null }
                2    -> { val a = amounts[0]
                          debit = if (prevBal != null && balance < prevBal!! - 0.001) a else null
                          credit = if (debit == null) a else null }
                else -> { debit = amounts[0].takeIf { it != 0.0 }; credit = amounts[1].takeIf { it != 0.0 } }
            }
            txns.add(Transaction(DateParser.formatForExport(currentDate!!), currentDesc.take(300), debit, credit, balance))
            prevBal = balance; currentDate = null; currentDesc = ""; currentAmts = mutableListOf()
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
                var rest = m.groupValues[2].trim()
                rest = TXN_TYPE_RE.replace(rest, "")
                val amounts = amtRe.findAll(rest).map { it.value.replace(",", "").toDouble() }.toList()
                currentDesc = amtRe.replace(rest, "").replace(Regex("""\*"""), "").replace(Regex("""\s+"""), " ").trim()
                currentAmts = amounts.toMutableList()
            } else if (currentDate != null) {
                if (extraStop?.containsMatchIn(s) == true) { commit(); break }
                val amounts = amtRe.findAll(s).map { it.value.replace(",", "").toDouble() }.toList()
                currentAmts.addAll(amounts)
                val dp = amtRe.replace(s, "").replace(Regex("""\*"""), "").replace(Regex("""\s+"""), " ").trim()
                if (dp.isNotBlank()) currentDesc = "$currentDesc $dp".trim()
            }
        }
        commit()

        return Statement(bankName, meta["accountNumber"] ?: "", meta["accountName"] ?: "",
            meta["openingBalance"]?.toDoubleOrNull(), meta["closingBalance"]?.toDoubleOrNull(),
            meta["from"] ?: "", meta["to"] ?: "", txns.filter { it.balance != null })
    }

    // ── Format B: Transaction Listing Report ──────────────────────────────────
    private fun parseFormatB(text: String): Statement {
        val meta   = extractMeta(text)
        val lines  = text.lines()
        val txns   = mutableListOf<Transaction>()
        val amtRe  = Regex("""-?[\d,]+\.\d{2}""")
        val crRe   = Regex("""\s+CR\s*$""", RegexOption.IGNORE_CASE)

        var currentYear: Int? = null; var currentMonth: Int? = null
        var currentDate: LocalDate? = null; var currentDesc = ""
        var currentAmts = mutableListOf<Double>(); var prevBalance = 0.0

        fun commit() {
            if (currentDate == null || currentAmts.isEmpty()) return
            val balance = currentAmts.last()
            val delta   = balance - prevBalance
            val debit   = if (delta < -0.005) Math.abs(delta) else null
            val credit  = if (delta >  0.005) delta else null
            txns.add(Transaction(DateParser.formatForExport(currentDate!!), currentDesc.take(300), debit, credit, balance))
            prevBalance = balance; currentDate = null; currentDesc = ""; currentAmts = mutableListOf()
        }

        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue

            // Month-year group header
            val myM = MONTH_YEAR_RE.find(line)
            if (myM != null) {
                currentMonth = MONTH_MAP[myM.groupValues[1].lowercase()]
                currentYear  = myM.groupValues[2].toInt()
                continue
            }

            if (extraSkip?.containsMatchIn(line) == true || FORMAT_B_SKIP.containsMatchIn(line)) continue

            // Transaction date row: "Aug 19  DESCRIPTION  amounts  balance CR"
            val txM = TXN_DATE_RE.find(line)
            if (txM != null) {
                commit()
                val month = MONTH_MAP[txM.groupValues[1].lowercase()] ?: currentMonth ?: 1
                val day   = txM.groupValues[2].toInt()
                val year  = currentYear ?: LocalDate.now().year
                currentDate = try { LocalDate.of(year, month, day) } catch (_: Exception) { null } ?: continue
                val rest  = crRe.replace(txM.groupValues[3], "").replace(Regex("""\s+-\s+"""), " ")
                val amts  = amtRe.findAll(rest).map { it.value.replace(",", "").toDouble() }.toList()
                currentDesc = amtRe.replace(rest, "").trim()
                currentAmts = amts.toMutableList()
            } else if (currentDate != null) {
                val lineClean = crRe.replace(line, "")
                val amts = amtRe.findAll(lineClean).map { it.value.replace(",", "").toDouble() }.toList()
                currentAmts.addAll(amts)
                val dp = amtRe.replace(lineClean, "").trim()
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
        Regex("""account no\.\s*([\d]+)""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.trim()?.let { meta["accountNumber"] = it }
        Regex("""account name\s+(.+)""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.trim()?.let { meta["accountName"] = it }
        Regex("""from\s+(\d{1,2}[.\s]\w+[.\s]\d{2,4})\s+to\s+(\d{1,2}[.\s]\w+[.\s]\d{2,4})""", RegexOption.IGNORE_CASE).find(text)
            ?.let { meta["from"] = it.groupValues[1]; meta["to"] = it.groupValues[2] }
        Regex("""From\s+(\d{1,2}\s+\w+\s+\d{4})\s+to\s+(\d{1,2}\s+\w+\s+\d{4})""", RegexOption.IGNORE_CASE).find(text)
            ?.let { meta["from"] = it.groupValues[1]; meta["to"] = it.groupValues[2] }
        Regex("""account balance\s*\$?([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.replace(",", "")?.let { meta["closingBalance"] = it }
        Regex("""CLOSING BALANCE AS AT[^\n]+\s+([\d,]+\.\d{2})\s+([\d,]+\.\d{2})\s+([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(3)?.replace(",", "")?.let { meta["closingBalance"] = it }
        // Format B: "Closing balance NNN.NN CR"
        Regex("""[Cc]losing [Bb]alance\s*([\d,]+\.\d{2})\s*CR""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.replace(",", "")?.let { meta["closingBalance"] = it }
        Regex("""[Oo]pening [Bb]alance\s*([\d,]+\.\d{2})\s*CR""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.replace(",", "")?.let { meta["openingBalance"] = it }
        return meta
    }
}
