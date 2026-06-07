package com.ortuspro.parsers

import com.ortuspro.model.Statement
import com.ortuspro.model.Transaction
import com.ortuspro.parser.StatementParser
import com.ortuspro.util.DateParser
import java.time.LocalDate

class AnzParser : StatementParser {
    override val bankName = "ANZ"
    override val dateFormat = null
    override val yearTracked = false

    override val detectPatterns = listOf(
        Regex("""Australia and New Zealand Banking Group|ANZ BUSINESS ESSENTIALS""", RegexOption.IGNORE_CASE),
        Regex("""ANZ Bank|anz\.com|ANZ Access|ANZ Business""", RegexOption.IGNORE_CASE)
    )

    override val extraSkip = Regex(
        listOf(
            """^australia and new zealand banking group""",
            """anz\.com""", """anz internet banking""",
            """lost.stolen cards""", """rtbsp|xprcap""",
            """totals at end of page""", """totals at end of period""",
            """this statement includes""", """interest earned on deposits""",
            """transaction details.*withdrawals""", """withdrawals.*deposits.*balance""",
            """please retain this statement""",
            """anz business essentials statement""",
            """^account number \d""",
            """^important information""", """all entries generated are subject""",
            """further information""", """if you believe there is an error""",
            """^important\s*$""", """^information\s*$""",
            """^anz\s*$""", """^blank\s*$""",
            """date\s+particulars\s+debit""",
            """date\s+transaction details\s+withdrawal""",
            """transaction date.*description""",
            """^opening balance\s*$""", """^brought forward""",
            """need to get in touch""", """account at a glance""",
            """page \d+ of \d+"""
        ).joinToString("|"),
        RegexOption.IGNORE_CASE
    )

    override val extraStop = Regex("""totals at end of period""", RegexOption.IGNORE_CASE)

    override fun parse(text: String): Statement {
        val meta = extractMeta(text)
        val year = meta["year"]?.toIntOrNull() ?: LocalDate.now().year
        val lines = text.lines()
        val txns  = mutableListOf<Transaction>()

        val dateRe  = Regex("""^(\d{1,2}\s+(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)(?:\s+\d{2,4})?)\s+(.*)$""", RegexOption.IGNORE_CASE)
        val amtRe   = Regex("""-?[\d,]+\.\d{2}""")
        val blankRe = Regex("""^\s*blank\s*$""", RegexOption.IGNORE_CASE)
        val effRe   = Regex("""^EFFECTIVE DATE\b""", RegexOption.IGNORE_CASE)

        var currentDate: LocalDate? = null; var currentDesc = ""
        var currentAmts = mutableListOf<Double>(); var prevBalance: Double? = null

        fun commit() {
            if (currentDate == null) return
            val amounts = currentAmts.toList()
            if (amounts.isEmpty()) { currentDate = null; return }
            val balance = amounts.last()
            val debit: Double?; val credit: Double?
            when (amounts.size) {
                1    -> { debit = null; credit = null }
                2    -> {
                    val amt = amounts[0]
                    if (prevBalance != null && balance < prevBalance!! - 0.001) { debit = amt; credit = null }
                    else { debit = null; credit = amt }
                }
                else -> { debit = amounts[0].takeIf { it != 0.0 }; credit = amounts[1].takeIf { it != 0.0 } }
            }
            txns.add(Transaction(DateParser.formatForExport(currentDate!!),
                currentDesc.replace(Regex("""\s+"""), " ").trim().take(300),
                debit, credit, balance))
            prevBalance = balance; currentDate = null; currentDesc = ""; currentAmts = mutableListOf()
        }

        for (raw in lines) {
            val s = raw.trim()
            if (s.isEmpty() || blankRe.matches(s) || extraSkip?.containsMatchIn(s) == true) continue
            if (extraStop?.containsMatchIn(s) == true) { commit(); break }
            if (effRe.containsMatchIn(s)) continue

            val m = dateRe.find(s)
            if (m != null) {
                commit()
                val dateStr = m.groupValues[1].trim()
                val parsed  = DateParser.parse(dateStr, year) ?: DateParser.parse("$dateStr $year", year)
                if (parsed == null || parsed.year !in 1990..2099) continue
                currentDate = parsed
                val rest    = m.groupValues[2].trim()
                val amounts = amtRe.findAll(rest).map { it.value.replace(",", "").toDouble() }.toList()
                currentDesc = amtRe.replace(rest, "").replace(Regex("""\s*(cr|dr)\b""", RegexOption.IGNORE_CASE), "").trim()
                currentAmts = amounts.toMutableList()
            } else if (currentDate != null) {
                if (extraStop?.containsMatchIn(s) == true) { commit(); break }
                if (effRe.containsMatchIn(s)) continue
                val amounts = amtRe.findAll(s).map { it.value.replace(",", "").toDouble() }.toList()
                currentAmts.addAll(amounts)
                val dp = amtRe.replace(s, "").replace(Regex("""\s*(cr|dr)\b""", RegexOption.IGNORE_CASE), "").trim()
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
