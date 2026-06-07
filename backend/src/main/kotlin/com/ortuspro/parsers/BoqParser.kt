package com.ortuspro.parsers

import com.ortuspro.model.Statement
import com.ortuspro.model.Transaction
import com.ortuspro.parser.StatementParser
import com.ortuspro.util.DateParser
import java.time.LocalDate

class BoqParser : StatementParser {
    override val bankName = "BOQ"
    override val dateFormat = null
    override val yearTracked = true

    override val detectPatterns = listOf(
        Regex("""Bank of Queensland""", RegexOption.IGNORE_CASE),
        Regex("""boq\.com\.au""", RegexOption.IGNORE_CASE)
    )

    override val extraSkip = Regex(
        listOf(
            """^bank of queensland""", """^boq centre""", """boq\.com\.au""",
            """^afsl no\.""", """^gpo box""", """^telephone \d""", """^facsimile""",
            """superannuation savings account""", """posting\s+transaction details""",
            """^total debits & credits""", """credit interest rates""",
            """effective date:.*amount""", """\$\d.*%.*$""",
            """security alert for pin""", """protect your card\.""",
            """when using atms""", """others may know your pin""",
            """^g460""", """^g\d{3}$""", """your statement continues""",
            """page \d+ of \d+""", """^statement\s*$""",
            """^level \d+,""", """^newstead""",
            """when using atm""", """don.t record your pin""",
            """personal identification number""",
            """from \d{2}-\w{3}-\d{4} to""",
            """between your last statement""", """charges booklets""",
            """you can also obtain""", """statement integrity""",
            """total debits\s*&\s*credits""",
            """^account details$""", """^statement$"""
        ).joinToString("|"),
        RegexOption.IGNORE_CASE
    )

    override val extraStop = Regex(
        listOf(
            """for the period""", """total interest credited""",
            """use online.*mobile.*tablet""",
            """please note the following financial""",
            """we offer several options""",
            """^statement summary$""", """^account details$"""
        ).joinToString("|"),
        RegexOption.IGNORE_CASE
    )

    override fun parse(text: String): Statement {
        val meta  = extractMeta(text)
        val lines = text.lines()
        val txns  = mutableListOf<Transaction>()

        val amtRe  = Regex("""-?[\d,]+\.\d{2}""")
        val crDrRe = Regex("""\s*(cr|dr)\s*$""", RegexOption.IGNORE_CASE)
        val dateRe = Regex("""^(\d{1,2}-(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec))\s+(.*)$""", RegexOption.IGNORE_CASE)
        val yearRe = Regex("""^\s*((?:19|20)\d{2})\s*$""")

        var currentYear = meta["year"]?.toIntOrNull() ?: LocalDate.now().year
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
            if (extraStop?.containsMatchIn(s) == true) { commit(); continue }

            val ym = yearRe.find(s)
            if (ym != null) { currentYear = ym.groupValues[1].toInt(); continue }

            if (s.startsWith("Opening Balance", ignoreCase = true) ||
                s.startsWith("Closing Balance", ignoreCase = true)) continue

            val m = dateRe.find(s)
            if (m != null) {
                commit()
                val parsed = DateParser.parse("${m.groupValues[1]}-$currentYear")
                if (parsed == null || parsed.year !in 1990..2099) continue
                currentDate = parsed
                val rest    = m.groupValues[2].trim()
                val amounts = amtRe.findAll(rest).map { it.value.replace(",", "").toDoubleOrNull() ?: 0.0 }.toList()
                currentDesc = crDrRe.replace(amtRe.replace(rest, ""), "").trim()
                currentAmts = amounts.toMutableList()
            } else if (currentDate != null) {
                val amounts = amtRe.findAll(s).map { it.value.replace(",", "").toDoubleOrNull() ?: 0.0 }.toList()
                currentAmts.addAll(amounts)
                val dp = crDrRe.replace(amtRe.replace(s, ""), "").trim()
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
        // Extract year from period header for year tracking
        Regex("""(\d{2})-\w{3}-(\d{4})""").find(text)
            ?.groupValues?.get(2)?.let { meta["year"] = it }
        Regex("""Account Number:\s*(\d+)""", RegexOption.IGNORE_CASE).find(text)
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
