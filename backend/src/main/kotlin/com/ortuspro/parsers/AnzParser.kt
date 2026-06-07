package com.ortuspro.parsers

import com.ortuspro.model.Statement
import com.ortuspro.model.Transaction
import com.ortuspro.parser.StatementParser
import com.ortuspro.util.DateParser
import java.time.LocalDate

/**
 * ANZ parser — handles ANZ Business Essentials statement format.
 *
 * Format:
 *   - Date: "24 NOV" or "24 NOV 2025" (dd MMM or dd MMM yyyy)
 *   - Header line: "21 NOVEMBER 2025 TO 22 DECEMBER 2025" → year extraction
 *   - "blank" placeholder for empty cells (pdfbox artifact)
 *   - Columns: Date | Transaction Details | Withdrawals ($) | Deposits ($) | Balance ($)
 *   - Multi-line descriptions (continuation lines without a date)
 *   - "OPENING BALANCE" line has only balance — skip it
 *   - "EFFECTIVE DATE DD MMM YYYY" continuation lines — strip
 *   - "TOTALS AT END OF PAGE" lines — stop parsing at these
 */
class AnzParser : StatementParser {
    override val bankName = "ANZ"
    override val dateFormat = null
    override val yearTracked = false

    override val detectPatterns = listOf(
        Regex("""Australia and New Zealand Banking""", RegexOption.IGNORE_CASE),
        Regex("""ANZ Bank|anz\.com|ANZ Access|ANZ Business|ANZ BUSINESS""", RegexOption.IGNORE_CASE)
    )

    override val extraSkip = Regex(
        listOf(
            """^australia and new zealand banking group""",
            """anz\.com""",
            """^important\s*$""",
            """^information\s*$""",
            """^anz\s*$""",
            """^blank\s*$""",
            """date\s+particulars\s+debit""",
            """date\s+transaction details\s+withdrawal""",
            """transaction date.*description""",
            """^opening balance\s*$""",
            """^brought forward""",
            """totals at end of page""",
            """totals at end of period""",
            """account number \d""",
            """page \d+ of \d+""",
            """rtbsp|xprcap""",
            """need to get in touch""",
            """account at a glance""",
            """anz internet banking""",
            """lost/stolen cards"""
        ).joinToString("|"),
        RegexOption.IGNORE_CASE
    )

    override val extraStop = Regex(
        """totals at end of period""",
        RegexOption.IGNORE_CASE
    )

    override fun parse(text: String): Statement {
        val meta = extractMeta(text)
        val year = meta["year"]?.toIntOrNull() ?: LocalDate.now().year

        val lines = text.lines()
        val txns  = mutableListOf<Transaction>()

        // ANZ date: "24 NOV" or "24 NOV 2025" (all caps months)
        val dateRe   = Regex("""^(\d{1,2}\s+(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)(?:\s+\d{2,4})?)\s+(.*)$""", RegexOption.IGNORE_CASE)
        val amtRe    = Regex("""-?[\d,]+\.\d{2}""")
        val blankRe  = Regex("""^\s*blank\s*$""", RegexOption.IGNORE_CASE)
        val effRe    = Regex("""^EFFECTIVE DATE\b""", RegexOption.IGNORE_CASE)

        var currentDate: LocalDate? = null
        var currentDesc = ""
        var currentAmts = mutableListOf<Double>()
        var prevBalance: Double? = null

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
                    if (prevBalance != null && balance < prevBalance!! - 0.001) {
                        debit = amt; credit = null
                    } else {
                        debit = null; credit = amt
                    }
                }
                else -> {
                    debit  = amounts[0].takeIf { it != 0.0 }
                    credit = amounts[1].takeIf { it != 0.0 }
                }
            }

            txns.add(Transaction(
                date        = DateParser.formatForExport(currentDate!!),
                description = cleanAnzDesc(currentDesc),
                debit       = debit,
                credit      = credit,
                balance     = balance
            ))
            prevBalance = balance
            currentDate = null; currentDesc = ""; currentAmts = mutableListOf()
        }

        for (raw in lines) {
            val s = raw.trim()
            if (s.isEmpty() || blankRe.matches(s)) continue
            if (extraSkip?.containsMatchIn(s) == true) continue
            if (extraStop?.containsMatchIn(s) == true) { commit(); break }
            if (effRe.containsMatchIn(s)) continue  // skip "EFFECTIVE DATE ..." continuation

            val m = dateRe.find(s)
            if (m != null) {
                commit()
                val dateStr = m.groupValues[1].trim()
                val rest    = m.groupValues[2].trim()

                // Try with year first, then inject year
                val parsed = DateParser.parse(dateStr, year)
                    ?: DateParser.parse("$dateStr $year", year)
                if (parsed == null || parsed.year !in 1990..2099) continue

                currentDate = parsed
                val (desc, amounts) = extractAmountsFromLine(rest, amtRe)
                currentDesc = desc
                currentAmts = amounts.toMutableList()
            } else if (currentDate != null) {
                if (extraStop?.containsMatchIn(s) == true) { commit(); break }
                val (descPart, amounts) = extractAmountsFromLine(s, amtRe)
                currentAmts.addAll(amounts)
                if (descPart.isNotBlank() && !effRe.containsMatchIn(s))
                    currentDesc = "$currentDesc $descPart".trim()
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
            transactions     = txns.filter { it.balance != null }
        )
    }

    private fun extractAmountsFromLine(s: String, amtRe: Regex): Pair<String, List<Double>> {
        val amounts = amtRe.findAll(s).map { it.value.replace(",", "").toDouble() }.toList()
        val desc    = amtRe.replace(s, "")
            .replace(Regex("""\s*(cr|dr)\b""", RegexOption.IGNORE_CASE), "")
            .trim()
        return desc to amounts
    }

    private fun cleanAnzDesc(desc: String): String =
        desc.replace(Regex("""\s+"""), " ").trim().take(300)

    override fun extractMeta(text: String): Map<String, String> {
        val meta = mutableMapOf<String, String>()
        // Year from "21 NOVEMBER 2025 TO 22 DECEMBER 2025"
        Regex("""\d{1,2}\s+\w+\s+(\d{4})\s+TO""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.let { meta["year"] = it }
        Regex("""Account Number\s*\n?([\d\-\s]+)""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.trim()?.let { meta["accountNumber"] = it }
        Regex("""Opening Balance:?\s*\$?([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.replace(",", "")?.let { meta["openingBalance"] = it }
        Regex("""Closing Balance:?\s*\$?([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.replace(",", "")?.let { meta["closingBalance"] = it }
        return meta
    }
}
