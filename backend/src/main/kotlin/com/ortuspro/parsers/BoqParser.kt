package com.ortuspro.parsers

import com.ortuspro.model.Statement
import com.ortuspro.model.Transaction
import com.ortuspro.parser.StatementParser
import com.ortuspro.util.DateParser
import java.time.LocalDate

/**
 * Bank of Queensland (BOQ) parser.
 *
 * Format:
 *   - Date: "04-Mar" (dd-Mon NO year) — year appears on its own line e.g. "2024"
 *   - Balance has "cr" or "dr" suffix — strip and negate if dr
 *   - Columns: Posting Date | Transaction Details | Debit | Credit | Balance
 *   - "Opening Balance" / "Closing Balance" rows — extract meta then skip
 *   - "Total Debits & Credits" row — skip
 *   - BOQ security alert text spans multiple lines — already in BASE_SKIP
 */
class BoqParser : StatementParser {
    override val bankName = "BOQ"
    override val dateFormat = null
    override val yearTracked = true  // year appears on its own line

    override val detectPatterns = listOf(
        Regex("""Bank of Queensland""", RegexOption.IGNORE_CASE),
        Regex("""boq\.com\.au""", RegexOption.IGNORE_CASE)
    )

    override val extraSkip = Regex(
        listOf(
            """^bank of queensland""",
            """^boq centre""",
            """boq\.com\.au""",
            """^afsl no\.""",
            """^gpo box""",
            """^telephone \d""",
            """^facsimile""",
            """superannuation savings account""",
            """posting\s+transaction details""",
            """^total debits & credits""",
            """credit interest rates""",
            """effective date:.*amount""",
            """\$\d.*%.*$""",           // interest rate table rows
            """security alert for pin""",
            """protect your card\.""",
            """when using atms""",
            """others may know your pin""",
            """^g460""",
            """your statement continues""",
            """page \d+ of \d+""",
            """^statement\s*$"""
        ).joinToString("|"),
        RegexOption.IGNORE_CASE
    )

    override val extraStop = null

    override fun parse(text: String): Statement {
        val meta  = extractMeta(text)
        val lines = text.lines()
        val txns  = mutableListOf<Transaction>()

        val amtRe   = Regex("""-?[\d,]+\.\d{2}""")
        val crDrRe  = Regex("""\s*(cr|dr)\s*$""", RegexOption.IGNORE_CASE)
        // BOQ date: "04-Mar" (hyphen separator, no year on line)
        val dateRe  = Regex("""^(\d{1,2}-(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec))\s+(.*)$""", RegexOption.IGNORE_CASE)
        val yearRe  = Regex("""^\s*((?:19|20)\d{2})\s*$""")

        var currentYear = LocalDate.now().year
        var currentDate: LocalDate? = null
        var currentDesc = ""
        var currentAmts = mutableListOf<Double>()
        var prevBal: Double? = null

        fun parseAmt(s: String): Double? {
            val clean = crDrRe.replace(s, "").replace(",", "").trim()
            val v = clean.toDoubleOrNull() ?: return null
            // "dr" means negative (debit) balance — negate
            return if (s.contains("dr", ignoreCase = true)) -v else v
        }

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
            val s = raw.trim()
            if (s.isEmpty() || extraSkip?.containsMatchIn(s) == true) continue

            // Year tracking: standalone 4-digit year
            val ym = yearRe.find(s)
            if (ym != null) { currentYear = ym.groupValues[1].toInt(); continue }

            // Skip opening/closing balance rows but extract balance
            if (s.startsWith("Opening Balance", ignoreCase = true) || s.startsWith("Closing Balance", ignoreCase = true)) {
                // Already in extraMeta
                continue
            }

            val m = dateRe.find(s)
            if (m != null) {
                commit()
                val dateStr = "${m.groupValues[1]}-$currentYear"
                val parsed  = DateParser.parse(dateStr)
                if (parsed == null || parsed.year !in 1990..2099) continue
                currentDate = parsed

                val rest = m.groupValues[2].trim()
                // Parse amounts — BOQ raw: "174.18 cr" or just a number
                val rawAmts = amtRe.findAll(rest).toList()
                val amounts = rawAmts.mapNotNull { ma ->
                    // Get context around match to check for cr/dr
                    val end   = ma.range.last + 1
                    val ctx   = if (end + 3 <= rest.length) rest.substring(end, end + 3) else ""
                    val raw2  = ma.value + ctx
                    ma.value.replace(",", "").toDoubleOrNull()
                }
                currentDesc = amtRe.replace(rest, "").replace(crDrRe, "").trim()
                currentAmts = amounts.toMutableList()
            } else if (currentDate != null) {
                val amounts = amtRe.findAll(s).map { it.value.replace(",", "").toDoubleOrNull() ?: 0.0 }.toList()
                currentAmts.addAll(amounts)
                val dp = amtRe.replace(s, "").replace(crDrRe, "").trim()
                if (dp.isNotBlank()) currentDesc = "$currentDesc $dp".trim()
            }
        }
        commit()

        return Statement(bankName, meta["accountNumber"] ?: "", "",
            meta["openingBalance"]?.toDoubleOrNull(), meta["closingBalance"]?.toDoubleOrNull(),
            meta["from"] ?: "", meta["to"] ?: "", txns.filter { it.balance != null })
    }

    override fun extractMeta(text: String): Map<String, String> {
        val meta = mutableMapOf<String, String>()
        Regex("""Account Number:\s*([\d]+)""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.trim()?.let { meta["accountNumber"] = it }
        Regex("""BSB:\s*([\d\-]+)""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.trim()?.let { meta["bsb"] = it }
        Regex("""From\s+([\d\-\w]+)\s+to\s+([\d\-\w]+)""", RegexOption.IGNORE_CASE).find(text)
            ?.let { meta["from"] = it.groupValues[1]; meta["to"] = it.groupValues[2] }
        Regex("""Opening Balance\s*\$?\s*([\d,]+\.\d{2})\s*cr""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.replace(",", "")?.let { meta["openingBalance"] = it }
        Regex("""Closing Balance\s*\$?\s*([\d,]+\.\d{2})\s*cr""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.replace(",", "")?.let { meta["closingBalance"] = it }
        return meta
    }
}
