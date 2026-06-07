package com.ortuspro.parsers

import com.ortuspro.model.Statement
import com.ortuspro.model.Transaction
import com.ortuspro.parser.StatementParser
import com.ortuspro.util.DateParser
import java.time.LocalDate

/**
 * Commonwealth Bank (CBA) parser — handles two formats:
 *
 * Format A: Classic CBA statement (CBA-50227 / Riverway Medical style)
 *   - Date: "01 Apr" (dd Mon no year) — year from statement period header
 *   - Columns: Date | Transaction | Debit | Credit | Balance
 *   - Balance has "CR" suffix: "$1,072,650.11 CR"
 *   - "OPENING BALANCE" and "CLOSING BALANCE" rows to skip
 *   - Date appears at start of line followed by description on same line
 *
 * Format B: CBA Transaction Summary (CBA-AJNA style)
 *   - Date: "08 Oct 2025" (abbr_year — dd MMM yyyy)
 *   - SINGLE Amount column (negative = debit, positive = credit)
 *   - Balance column: "$39,874.41"
 *   - Continuation lines: "Card xx8499", "Value Date: DD/MM/YYYY" — strip
 *   - "Transaction Summary v1.0.5" in header identifies this format
 */
class CommonwealthParser : StatementParser {
    override val bankName = "COMMONWEALTH"
    override val dateFormat = null
    override val yearTracked = false

    override val detectPatterns = listOf(
        Regex("""Commonwealth Bank""", RegexOption.IGNORE_CASE),
        Regex("""netbank\.com\.au|CommBank|CBA|commbank\.com\.au""", RegexOption.IGNORE_CASE)
    )

    override val extraSkip = Regex(
        listOf(
            """^commonwealth bank of australia""",
            """commbank\.com\.au""",
            """netbank\.com\.au""",
            """^(opening|closing) balance""",
            """^date\s+(transaction|description)""",
            """transaction summary v\d""",
            """created \d{2}/\d{2}/\d{2}""",
            """while this letter is accurate""",
            """we're not responsible""",
            """any pending transactions""",
            """proceeds of cheques""",
            """if you have questions""",
            """the commbank team""",
            """kind regards""",
            """account number\s+\d""",
            """^\d{9,}$""",
            """created \d{2}/\d{2}/\d{2}""",
            """\d+\.\d+[A-Z]+\.\d+[A-Z]+""",
            """sydney/melbourne time""",
            """account fee\s*\$\s*\$""",
            """paper statement fee""",
            """page \d+ of \d+""",
            """^tax invoice""",
            """gst.*taxable""",
            """total amount of taxable""",
            """total gst paid""",
            """transaction summary during""",
            """transaction type.*unit price""",
            """^account fee\b""",
            """^paper statement fee""",
            """^important information""",
            """we try to get things right""",
            """write to:.*cba""",
            """tell us online.*commbank""",
            """you can fix most problems""",
            """enquiries \d{2}""",
            """statement\s+period\s+\d""",
            """cheque acct bearing""",
            """the date of transactions""",
            """appears on the commbank"""
        ).joinToString("|"),
        RegexOption.IGNORE_CASE
    )

    override val extraStop = Regex(
        """^closing balance\s*\$|your credit interest rate""",
        RegexOption.IGNORE_CASE
    )

    override fun parse(text: String): Statement {
        val meta = extractMeta(text)
        // Detect format B by "Transaction Summary" marker or "$-" style amounts
        val isTxnSummary = text.contains("Transaction Summary v", ignoreCase = true)
            || Regex("""-\$[\d,]+\.\d{2}""").containsMatchIn(text)

        return if (isTxnSummary) parseTxnSummary(text, meta)
               else parseClassic(text, meta)
    }

    // ── Format A: Classic CBA ─────────────────────────────────────────────────
    private fun parseClassic(text: String, meta: Map<String, String>): Statement {
        val year = meta["year"]?.toIntOrNull() ?: LocalDate.now().year
        val lines = text.lines()
        val txns  = mutableListOf<Transaction>()

        // "01 Apr" or "01 Apr 2025"
        val dateRe = Regex("""^(\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)(?:\s+\d{2,4})?)\s+(.*)$""", RegexOption.IGNORE_CASE)
        val amtRe  = Regex("""-?[\d,]+\.\d{2}""")
        val crRe   = Regex("""\s*CR\s*$""", RegexOption.IGNORE_CASE)

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
                1    -> { debit = null; credit = null }
                2    -> {
                    val a = amounts[0]
                    if (prevBal != null && balance < prevBal!! - 0.001) { debit = a; credit = null }
                    else { debit = null; credit = a }
                }
                else -> { debit = amounts[0].takeIf { it != 0.0 }; credit = amounts[1].takeIf { it != 0.0 } }
            }
            txns.add(Transaction(DateParser.formatForExport(currentDate!!), currentDesc.take(300), debit, credit, balance))
            prevBal = balance; currentDate = null; currentDesc = ""; currentAmts = mutableListOf()
        }

        for (raw in lines) {
            val s = raw.trim().let { crRe.replace(it, "") }.trim()
            if (s.isEmpty() || extraSkip?.containsMatchIn(s) == true) continue
            if (extraStop?.containsMatchIn(s) == true) { commit(); break }

            val m = dateRe.find(s)
            if (m != null) {
                commit()
                val parsed = DateParser.parse(m.groupValues[1], year)
                    ?: DateParser.parse("${m.groupValues[1]} $year", year)
                if (parsed == null || parsed.year !in 1990..2099) continue
                currentDate = parsed
                val rest    = m.groupValues[2]
                val amounts = amtRe.findAll(rest).map { it.value.replace(",", "").toDouble() }.toList()
                currentDesc = amtRe.replace(rest, "").replace(crRe, "").trim()
                currentAmts = amounts.toMutableList()
            } else if (currentDate != null) {
                if (extraStop?.containsMatchIn(s) == true) { commit(); break }
                val amounts = amtRe.findAll(s).map { it.value.replace(",", "").toDouble() }.toList()
                currentAmts.addAll(amounts)
                val descPart = amtRe.replace(s, "").replace(crRe, "").trim()
                if (descPart.isNotBlank()) currentDesc = "$currentDesc $descPart".trim()
            }
        }
        commit()

        return Statement(bankName, meta["accountNumber"] ?: "", meta["accountName"] ?: "",
            meta["openingBalance"]?.toDoubleOrNull(), meta["closingBalance"]?.toDoubleOrNull(),
            meta["from"] ?: "", meta["to"] ?: "", txns.filter { it.balance != null })
    }

    // ── Format B: Transaction Summary (single Amount column) ──────────────────
    private fun parseTxnSummary(text: String, meta: Map<String, String>): Statement {
        val lines = text.lines()
        val txns  = mutableListOf<Transaction>()

        // "08 Oct 2025" with Amount and Balance on same line
        val dateRe = Regex("""^(\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\s+\d{2,4})\s+(.+?)\s+(-?\$[\d,]+\.\d{2})\s+(\$[\d,]+\.\d{2})$""", RegexOption.IGNORE_CASE)
        // Also handle lines where date+desc is on line 1, amounts on same line
        val dateSimple = Regex("""^(\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\s+\d{2,4})\s+(.*)$""", RegexOption.IGNORE_CASE)
        val amtRe  = Regex("""-?\$[\d,]+\.\d{2}""")
        val amtClean = Regex("""[\$,]""")
        val skipLine = Regex("""^(Card xx\d+|Value Date:|Created \d)""", RegexOption.IGNORE_CASE)

        var currentDate: LocalDate? = null
        var currentDesc = ""
        var currentAmt: Double? = null
        var currentBal: Double? = null

        fun commit() {
            if (currentDate == null || currentBal == null) return
            val amt = currentAmt ?: 0.0
            val debit  = if (amt < 0) -amt else null
            val credit = if (amt > 0)  amt  else null
            txns.add(Transaction(DateParser.formatForExport(currentDate!!),
                currentDesc.take(300), debit, credit, currentBal))
            currentDate = null; currentDesc = ""; currentAmt = null; currentBal = null
        }

        for (raw in lines) {
            val s = raw.trim()
            if (s.isEmpty() || extraSkip?.containsMatchIn(s) == true) continue
            if (skipLine.containsMatchIn(s)) continue

            val m = dateSimple.find(s)
            if (m != null) {
                // Check if this line has amounts
                val amounts = amtRe.findAll(s).map { amtClean.replace(it.value, "").toDouble() }.toList()
                val parsed = DateParser.parse(m.groupValues[1])
                if (parsed == null || parsed.year !in 2000..2099) continue

                commit()
                currentDate = parsed
                val rest = m.groupValues[2].trim()

                when {
                    amounts.size >= 2 -> {
                        // Amount and balance on same line
                        currentAmt = amounts[amounts.size - 2]
                        currentBal = amounts.last()
                        currentDesc = amtRe.replace(rest, "").trim()
                    }
                    amounts.size == 1 -> {
                        // Only one amount — likely balance
                        currentBal = amounts[0]
                        currentDesc = amtRe.replace(rest, "").trim()
                    }
                    else -> {
                        currentDesc = rest
                    }
                }
            } else if (currentDate != null) {
                val amounts = amtRe.findAll(s).map { amtClean.replace(it.value, "").toDouble() }.toList()
                when {
                    amounts.size >= 2 -> {
                        currentAmt = amounts[amounts.size - 2]
                        currentBal = amounts.last()
                        val descPart = amtRe.replace(s, "").trim()
                        if (descPart.isNotBlank()) currentDesc = "$currentDesc $descPart".trim()
                    }
                    amounts.size == 1 -> {
                        if (currentBal == null) currentBal = amounts[0]
                        else currentAmt = amounts[0]
                    }
                    else -> {
                        if (!skipLine.containsMatchIn(s))
                            currentDesc = "$currentDesc $s".trim()
                    }
                }
            }
        }
        commit()

        return Statement(bankName, meta["accountNumber"] ?: "", meta["accountName"] ?: "",
            meta["openingBalance"]?.toDoubleOrNull(), meta["closingBalance"]?.toDoubleOrNull(),
            meta["from"] ?: "", meta["to"] ?: "", txns.filter { it.balance != null })
    }

    override fun extractMeta(text: String): Map<String, String> {
        val meta = mutableMapOf<String, String>()
        // Year from "Statement Period 1 Apr 2025 - 30 Jun 2025"
        Regex("""Statement\s+Period\s+\d+\s+\w+\s+(\d{4})""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.let { meta["year"] = it }
        // Year from "from 01/10/25-31/12/25"
        Regex("""from\s+\d{1,2}/\d{1,2}/(\d{2,4})""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.let { y ->
                val yr = if (y.length == 2) "20$y" else y
                meta["year"] = yr
            }
        Regex("""Account Number\s+([\d\s]+)""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.trim()?.let { meta["accountNumber"] = it }
        Regex("""Account number\s+([\d\s]+)""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.trim()?.let { if (meta["accountNumber"].isNullOrBlank()) meta["accountNumber"] = it }
        Regex("""Closing Balance\s*\$?([\d,]+\.?\d*)\s*CR?""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.replace(",", "")?.let { meta["closingBalance"] = it }
        Regex("""Opening Balance\s*\$?([\d,]+\.?\d*)\s*CR?""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.replace(",", "")?.let { meta["openingBalance"] = it }
        return meta
    }
}
