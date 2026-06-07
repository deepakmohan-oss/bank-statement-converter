package com.ortuspro.parsers

import com.ortuspro.model.Statement
import com.ortuspro.model.Transaction
import com.ortuspro.parser.StatementParser
import com.ortuspro.util.DateParser
import java.time.LocalDate

/**
 * NAB parser — handles two NAB formats:
 *
 * Format A: NAB Cash Manager Statement (Loiton Super Fund style)
 *   - Date: "3 Aug 2024" (abbr_year with full year)
 *   - Dot-leader separators: "Rental Transfer  KEN66 ........  1,641.50"
 *   - Monthly Transaction Summary blocks to skip
 *   - "Brought forward" / "Carried forward" lines to skip
 *
 * Format B: NAB Transaction Listing (Helping Wings style)
 *   - Date: "13 Feb 26" (dd Mon YY — 2-digit year)
 *   - Columns: Date | Particulars | Debits | Credits | Balance
 *   - Balance has "CR" suffix
 *   - "$0.00 $0.00 CR" informational lines (interest rate notices)
 *   - Amounts preceded by "$" with comma separators
 *   - "PLEASE NOTE FROM TODAY..." informational lines interspersed with dates
 *   - Opening balance may be "$0.00 CR"
 */
class NabParser : StatementParser {
    override val bankName = "NAB"
    override val dateFormat = null
    override val yearTracked = true

    override val detectPatterns = listOf(
        Regex("""National Australia Bank""", RegexOption.IGNORE_CASE),
        Regex("""nab\.com\.au|NAB Classic Banking|NAB iDirect|NAB Cash Manager|Transaction Listing""", RegexOption.IGNORE_CASE)
    )

    override val extraSkip = Regex(
        listOf(
            """^national australia bank limited$""",
            """^nab\s*$""",
            """nab\.com\.au""",
            """provisional list is not a statement""",
            """may include transactions which may appear""",
            """inclusion of a debit does not always""",
            """^important\s*$""",
            """with the exception of cheque""",
            """all transactions shown""",
            """individually authorised""",
            """date\s+particulars\s+debits""",
            """monthly transaction summary""",
            """internet bpay\s+\d""",
            """total transaction fees""",
            """less transaction rebate""",
            """transaction fees less rebate""",
            """account service fee""",
            """fee charged""",
            """the following information concerning""",
            """provided to assist in preparing""",
            """credit interest paid""",
            """resident withholding tax""",
            """if you have any queries.*call""",
            """number on the top of this statement""",
            """^\.{3,}""",
            """abn \d{2} \d{3} \d{3} \d{3}""",
            """may include transactions which may appear""",
            """inclusion of a debit does not always""",
            """^transactions,\s+all""",
            """\$ \$ is \d+%""",
            """^brought forward\s*$""",
            """^carried forward\s*$""",
            """statement starts""",
            """statement ends""",
            """^opening balance\s*$""",
            """^closing balance\s*$""",
            """please note from today your dr interest""",
            """page \d+ of \d+""",
            """date created:""",
            """account balance summary""",
            """transaction listing starts""",
            """transaction listing ends""",
            """account type\s+transaction""",
            """bsb number\s+\d""",
            """account number\s+\d"""
        ).joinToString("|"),
        RegexOption.IGNORE_CASE
    )

    override val extraStop = Regex(
        """^carrying forward|total debits & credits|^closing balance\s*$""",
        RegexOption.IGNORE_CASE
    )

    override fun parse(text: String): Statement {
        val meta = extractMeta(text)

        // Detect Format B by "Transaction Listing" marker or "CR" balance suffix with "$" amounts
        val isTxnListing = text.contains("Transaction Listing", ignoreCase = true)
            || text.contains("FLEXIPAY", ignoreCase = true)

        return if (isTxnListing) parseTxnListing(text, meta)
               else parseCashManager(text, meta)
    }

    // ── Format A: NAB Cash Manager ────────────────────────────────────────────
    private fun parseCashManager(text: String, meta: Map<String, String>): Statement {
        val lines   = text.lines()
        val txns    = mutableListOf<Transaction>()
        val amtRe   = Regex("""-?[\d,]+\.\d{2}""")
        val dotRe   = Regex("""\.{4,}""")                 // strip dot leaders
        val crDrRe  = Regex("""\s*(cr|dr)\b""", RegexOption.IGNORE_CASE)

        // "3 Aug 2024" or "3 Aug" (year injected if missing)
        val dateRe = Regex("""^(\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)(?:\s+\d{2,4})?)\s+(.*)$""", RegexOption.IGNORE_CASE)
        var currentYear = LocalDate.now().year
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
            txns.add(Transaction(DateParser.formatForExport(currentDate!!), currentDesc.take(300), debit, credit, balance))
            prevBal = balance; currentDate = null; currentDesc = ""; currentAmts = mutableListOf()
        }

        for (raw in lines) {
            val s = dotRe.replace(raw.trim(), " ").trim().let { crDrRe.replace(it, "") }.trim()
            if (s.isEmpty() || extraSkip?.containsMatchIn(s) == true) continue
            if (extraStop?.containsMatchIn(s) == true) { commit(); break }

            // Year tracking: standalone 4-digit year line
            val yearMatch = Regex("""^\s*((?:19|20)\d{2})\s*$""").find(s)
            if (yearMatch != null) { currentYear = yearMatch.groupValues[1].toInt(); continue }

            val m = dateRe.find(s)
            if (m != null) {
                commit()
                val dateStr = m.groupValues[1].trim()
                val parsed  = DateParser.parse(dateStr, currentYear)
                    ?: DateParser.parse("$dateStr $currentYear", currentYear)
                if (parsed == null || parsed.year !in 1990..2099) continue
                currentDate = parsed
                val rest    = m.groupValues[2]
                val amounts = amtRe.findAll(rest).map { it.value.replace(",", "").toDouble() }.toList()
                currentDesc = amtRe.replace(rest, "").trim()
                currentAmts = amounts.toMutableList()
            } else if (currentDate != null) {
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

    // ── Format B: NAB Transaction Listing ────────────────────────────────────
    // Date: "13 Feb 26" (2-digit year), amounts have "$" prefix, balance has "CR" suffix
    private fun parseTxnListing(text: String, meta: Map<String, String>): Statement {
        val lines  = text.lines()
        val txns   = mutableListOf<Transaction>()
        val amtRe  = Regex("""\$[\d,]+\.\d{2}(?:\s*CR)?""", RegexOption.IGNORE_CASE)
        val amtNum = Regex("""[\$,]|(?:\s*CR\s*$)""", RegexOption.IGNORE_CASE)

        // "13 Feb 26" — 2-digit year
        val dateRe = Regex("""^(\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\s+\d{2}(?:\d{2})?)\s+(.*)$""", RegexOption.IGNORE_CASE)

        var currentDate: LocalDate? = null
        var currentDesc = ""
        var currentAmts = mutableListOf<Double>()
        var prevBal: Double? = null

        fun commit() {
            if (currentDate == null) return
            val amounts = currentAmts.toList()
            if (amounts.isEmpty()) { currentDate = null; return }
            // For transaction listing: Date | Particulars | Debits | Credits | Balance
            // 3 amounts = debit/credit/balance; 2 = one of debit/credit + balance; 1 = balance only
            val balance = amounts.last()
            val debit: Double?; val credit: Double?
            when (amounts.size) {
                1 -> { debit = null; credit = null }
                2 -> { val a = amounts[0]
                       if (prevBal != null && balance < prevBal!! - 0.001) { debit = a; credit = null }
                       else { debit = null; credit = a } }
                else -> { debit = amounts[0].takeIf { it != 0.0 }; credit = amounts[1].takeIf { it != 0.0 } }
            }
            // Skip $0.00 informational lines
            if (balance == 0.0 && debit == null && credit == null && amounts.size <= 2) {
                currentDate = null; return
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
                val rest    = m.groupValues[2]
                val amounts = amtRe.findAll(rest).map { amtNum.replace(it.value, "").trim().toDoubleOrNull() ?: 0.0 }.toList()
                currentDesc = amtRe.replace(rest, "").trim()
                currentAmts = amounts.toMutableList()
            } else if (currentDate != null) {
                val amounts = amtRe.findAll(s).map { amtNum.replace(it.value, "").trim().toDoubleOrNull() ?: 0.0 }.toList()
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
        Regex("""Account number\s+([\d\-\s]+)""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.trim()?.let { meta["accountNumber"] = it }
        Regex("""BSB number\s+([\d\-]+)""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.trim()?.let { meta["bsb"] = it }
        Regex("""Opening balance\s*\$?([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.replace(",", "")?.let { meta["openingBalance"] = it }
        Regex("""Closing balance\s*\$?([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.replace(",", "")?.let { meta["closingBalance"] = it }
        Regex("""Total Credits\s*\$?([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.replace(",", "")?.let { meta["totalCredits"] = it }
        Regex("""Statement starts\s+(.+)""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.trim()?.let { meta["from"] = it }
        Regex("""Statement ends\s+(.+)""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.trim()?.let { meta["to"] = it }
        Regex("""Transaction Listing starts\s+(.+)""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.trim()?.let { meta["from"] = it }
        Regex("""Transaction Listing ends\s+(.+)""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.trim()?.let { meta["to"] = it }
        return meta
    }
}
