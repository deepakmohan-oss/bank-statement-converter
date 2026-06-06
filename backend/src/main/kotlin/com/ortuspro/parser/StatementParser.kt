package com.ortuspro.parser

import com.ortuspro.model.Statement
import com.ortuspro.model.Transaction
import com.ortuspro.util.DateParser
import com.ortuspro.util.RegexLibrary
import java.time.LocalDate

/**
 * StatementParser — base interface all bank parsers implement.
 *
 * Each bank parser provides:
 *   - detectPatterns: list of regexes; detect() returns fraction matched
 *   - extraSkip / extraStop: bank-specific boilerplate to ignore
 *   - parse(): fully custom parsing logic per-bank (overrides default)
 *   - extractMeta(): extract account/balance from header
 *
 * The default parse() in this interface is a fallback for simple formats.
 * All real bank parsers override it with their own logic.
 */
interface StatementParser {
    val bankName: String
    val detectPatterns: List<Regex>
    val extraSkip: Regex?
    val extraStop: Regex?
    val dateFormat: String?     // null = auto-detect in default parser
    val yearTracked: Boolean    // true if year appears on its own line

    /** Confidence score 0.0–1.0: fraction of detectPatterns matched. */
    fun detect(text: String): Double {
        if (detectPatterns.isEmpty()) return 0.0
        val hits = detectPatterns.count { it.containsMatchIn(text) }
        return hits.toDouble() / detectPatterns.size
    }

    /** Override in each bank parser. Default delegates to CoreLineParser. */
    fun parse(text: String): Statement {
        val lines = text.lines()
        val fmt   = dateFormat ?: DateParser.detectFormat(lines)
        val txns  = CoreLineParser.parse(lines, fmt, yearTracked, extraSkip, extraStop ?: RegexLibrary.BASE_STOP)
        val meta  = extractMeta(text)
        return Statement(
            bank           = bankName,
            accountNumber  = meta["accountNumber"] ?: "",
            accountName    = meta["accountName"] ?: "",
            openingBalance = meta["openingBalance"]?.toDoubleOrNull(),
            closingBalance = meta["closingBalance"]?.toDoubleOrNull(),
            statementFrom  = meta["from"] ?: "",
            statementTo    = meta["to"] ?: "",
            transactions   = txns
        )
    }

    fun extractMeta(text: String): Map<String, String> = emptyMap()
}

// ─────────────────────────────────────────────────────────────────────────────
// CoreLineParser — generic fallback line parser used by simple-format parsers
// ─────────────────────────────────────────────────────────────────────────────
object CoreLineParser {

    fun parse(
        lines: List<String>,
        fmt: String,
        yearTracked: Boolean,
        extraSkip: Regex?,
        stopRe: Regex
    ): List<Transaction> {

        val dateRe = formatRegex(fmt) ?: return emptyList()
        val transactions = mutableListOf<Transaction>()
        var currentDate: LocalDate? = null
        var currentDesc = ""
        var currentAmts = mutableListOf<Double>()
        var prevBalance: Double? = null
        var currentYear = LocalDate.now().year

        fun commit() {
            if (currentDate == null) return
            val (debit, credit, balance) = assignDebitCredit(currentAmts, prevBalance)
            if (balance != null) {
                transactions.add(Transaction(
                    date        = DateParser.formatForExport(currentDate!!),
                    description = cleanDesc(currentDesc),
                    debit       = debit,
                    credit      = credit,
                    balance     = balance
                ))
                prevBalance = balance
            }
            currentDate = null; currentDesc = ""; currentAmts = mutableListOf()
        }

        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue

            if (yearTracked) {
                val ym = Regex("""^\s*((?:19|20)\d{2})\b""").find(line)
                if (ym != null && line.length < 8) { currentYear = ym.groupValues[1].toInt(); continue }
            }

            if (RegexLibrary.BASE_SKIP.containsMatchIn(line)) continue
            if (extraSkip?.containsMatchIn(line) == true) continue
            if (stopRe.containsMatchIn(line)) { commit(); continue }

            val m = dateRe.find(line)
            if (m != null) {
                commit()
                val dateStr = m.groupValues[1].trim()
                val rest    = m.groupValues[2].trim()
                val parsed  = if (yearTracked)
                    DateParser.parse(dateStr, currentYear) ?: DateParser.parse("$dateStr $currentYear", currentYear)
                else
                    DateParser.parse(dateStr)

                if (parsed == null || parsed.year !in 1990..2099) continue
                currentDate = parsed
                val (desc, amts) = extractAmounts(rest)
                currentDesc = desc; currentAmts = amts.toMutableList()
            } else if (currentDate != null) {
                if (stopRe.containsMatchIn(line)) { commit(); continue }
                val (descPart, extraAmts) = extractAmounts(line)
                currentAmts.addAll(extraAmts)
                if (descPart.isNotBlank()) currentDesc = "$currentDesc $descPart".trim()
            }
        }
        commit()

        return transactions.filter { it.balance != null }.sortedBy { it.date }
    }

    fun assignDebitCredit(amounts: List<Double>, prevBalance: Double?): Triple<Double?, Double?, Double?> {
        if (amounts.isEmpty()) return Triple(null, null, null)
        val balance = amounts.last()
        return when (amounts.size) {
            1    -> Triple(null, null, balance)
            2    -> {
                val amt = amounts[0]
                when {
                    amt < 0                             -> Triple(-amt, null, balance)
                    prevBalance != null && balance < prevBalance - 0.001 -> Triple(amt, null, balance)
                    else                                -> Triple(null, amt, balance)
                }
            }
            else -> Triple(
                amounts[0].takeIf { it != 0.0 },
                amounts[1].takeIf { it != 0.0 },
                balance
            )
        }
    }

    fun extractAmounts(text: String): Pair<String, List<Double>> {
        val amounts = RegexLibrary.AMOUNT.findAll(text)
            .map { it.value.replace(",", "").toDouble() }.toList()
        val cleaned = RegexLibrary.AMOUNT.replace(text, "")
            .replace(Regex("""\s*(?:cr|dr)\b""", RegexOption.IGNORE_CASE), "")
            .trim()
        return cleaned to amounts
    }

    fun cleanDesc(desc: String): String {
        var s = desc
        for (cutoff in RegexLibrary.DESC_CUTOFFS) {
            val idx = s.indexOf(cutoff)
            if (idx > 0) s = s.substring(0, idx).trim()
        }
        return s.take(300).trim()
    }

    private fun formatRegex(fmt: String): Regex? = when (fmt) {
        "slash"     -> RegexLibrary.DATE_SLASH
        "dot"       -> RegexLibrary.DATE_DOT
        "abbr"      -> RegexLibrary.DATE_ABBR
        "abbr_year" -> RegexLibrary.DATE_ABBR_YEAR
        "hyphen"    -> RegexLibrary.DATE_HYPHEN
        else        -> null
    }
}
