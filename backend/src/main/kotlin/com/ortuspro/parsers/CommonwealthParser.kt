package com.ortuspro.parsers

import com.ortuspro.model.Statement
import com.ortuspro.model.Transaction
import com.ortuspro.parser.StatementParser
import com.ortuspro.util.DateParser
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Commonwealth Bank (CBA) parser — handles two formats:
 *
 * FORMAT A: Classic CBA statement (e.g. Riverway Medical / CBA-50227)
 * ─────────────────────────────────────────────────────────────────────
 *   Identified by: "Your Statement" / "Statement NNN" header
 *   Date:    "01 Apr" (dd MMM, NO year) — year derived from statement period header
 *   Columns: Date | Transaction (description, may wrap to next line) | Debit | Credit | Balance
 *   Balance: plain number with "CR" suffix e.g. "$1,072,650.11 CR"
 *   Debit / Credit: plain numbers WITHOUT a sign  e.g. "16,131.84"  "46.90"
 *   OPENING BALANCE / CLOSING BALANCE rows must be skipped (no Debit/Credit amounts)
 *
 *   Key parsing rules:
 *   - A transaction row starts with a date token "dd Mon"  (e.g. "01 Apr")
 *   - Continuation lines (no leading date) belong to the current transaction description
 *   - Lines that are ONLY a number are continuation balance/amount lines — skip
 *   - The last TWO numeric tokens on a date-bearing row are: [credit|debit]  balance
 *     (one of credit or debit may be absent — the column that has a value determines which)
 *   - Because PDFBox flattens columns, we must infer debit vs credit from context:
 *       • If the balance DECREASED  →  debit
 *       • If the balance INCREASED  →  credit
 *       • If balance is unchanged   →  treat as credit (rare fee reversals)
 *
 * FORMAT B: CBA Transaction Summary (e.g. CBA-Ajna)
 * ──────────────────────────────────────────────────
 *   Identified by: "Transaction Summary v" in page text
 *   Date:    "08 Oct 2025" (dd MMM yyyy — full year on same line as description)
 *   Columns: Date | Transaction details | Amount | Balance
 *   Amount:  signed — negative = debit  e.g. "-$116.98",  positive = credit e.g. "$23,100.00"
 *   Balance: unsigned e.g. "$39,874.41"
 *   Continuation lines to STRIP (not part of description):
 *       "Card xxNNNN"
 *       "Value Date: DD/MM/YYYY"
 *       Lines that are only a date or amount
 *
 *   Key parsing rules:
 *   - A transaction row starts with "dd MMM yyyy" (3-char month, 4-digit year)
 *   - Description may span the rest of that line; continuation lines with "Card"/"Value Date" are stripped
 *   - Amount sign directly encodes debit (negative) vs credit (positive)
 */
class CommonwealthParser : StatementParser {

    override val bankName = "COMMONWEALTH"
    override val dateFormat = null   // handled internally per sub-format
    override val yearTracked = false

    override val detectPatterns = listOf(
        Regex("""Commonwealth\s*Bank""", RegexOption.IGNORE_CASE),
        Regex("""commbank\.com\.au|CommBank""", RegexOption.IGNORE_CASE),
        Regex("""netbank\.com\.au""", RegexOption.IGNORE_CASE)
    )

    // Lines that are never transactions — applied BEFORE sub-format dispatch
    override val extraSkip = Regex(
        listOf(
            """^commonwealth bank of australia""",
            """commbank\.com\.au""",
            """netbank\.com\.au""",
            """^(opening|closing)\s+balance""",
            """^date\s+(transaction|description)""",
            """transaction summary v\d""",
            """created \d{2}/\d{2}/\d{2}""",
            """while this letter is accurate""",
            """we.?re not responsible""",
            """any pending transactions""",
            """proceeds of cheques""",
            """if you have questions""",
            """the commbank team""",
            """kind regards""",
            """account number\s+\d""",
            """page \d+ of \d+""",
            """^tax invoice""",
            """^gst\b""",
            """statement \d+ \(page""",
            """^your statement$""",
            """cheque acct bearing interest""",
            """important information""",
            """^note:""",
            """^gst:""",
            """^the date of transactions""",
            """^enquiries""",
            """^\d{2} hours a day""",
            """^opening balance as at""",
            """^closing balance as at""",
            """^your credit interest""",
            """^transaction summary during""",
            """^transaction type\b""",
            """unit price\s+fee charged""",
            """account fee""",
            """paper statement fee""",
            """^\s*\d{4}\.\d{5}\.\d+\.""",   // barcode lines e.g. 7028.16059.1.14
        ).joinToString("|"),
        RegexOption.IGNORE_CASE
    )

    // ── Public entry point ────────────────────────────────────────────────────

    override fun parse(text: String): Statement {
        val isTxnSummary = Regex("""Transaction Summary v""", RegexOption.IGNORE_CASE).containsMatchIn(text)
        return if (isTxnSummary) parseTxnSummary(text) else parseClassic(text)
    }

    override fun extractMeta(text: String): Map<String, String> {
        val meta = mutableMapOf<String, String>()

        // Account number — try both "Account Number  063234 11237252" and "06 4817 10650227"
        Regex("""Account\s+Number\s+([\d\s]+)""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.trim()
            ?.replace(Regex("""\s+"""), " ")
            ?.let { meta["accountNumber"] = it }

        // Statement period — "1 Apr 2025 - 30 Jun 2025" or "01/10/25-31/12/25"
        Regex("""(\d{1,2}\s+\w+\s+\d{4})\s*[-–]\s*(\d{1,2}\s+\w+\s+\d{4})""")
            .find(text)?.let { mr ->
                meta["from"] = mr.groupValues[1]
                meta["to"]   = mr.groupValues[2]
            }
        if (!meta.containsKey("from")) {
            Regex("""(\d{2}/\d{2}/\d{2,4})\s*[-–]\s*(\d{2}/\d{2}/\d{2,4})""")
                .find(text)?.let { mr ->
                    meta["from"] = mr.groupValues[1]
                    meta["to"]   = mr.groupValues[2]
                }
        }

        // Opening / closing balance
        Regex("""OPENING BALANCE\s+\$?([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.replace(",", "")?.let { meta["openingBalance"] = it }
        Regex("""Closing Balance\s+\$?([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.replace(",", "")?.let { meta["closingBalance"] = it }
        // Classic footer: "$1,072,650.11 CR  $7,031,342.75  $6,375,895.77  $417,203.13 CR"
        Regex("""\$([\d,]+\.\d{2})\s+CR\s+\$([\d,]+\.\d{2})\s+\$([\d,]+\.\d{2})\s+\$([\d,]+\.\d{2})\s+CR""")
            .find(text)?.let { mr ->
                if (!meta.containsKey("openingBalance")) meta["openingBalance"] = mr.groupValues[1].replace(",", "")
                if (!meta.containsKey("closingBalance")) meta["closingBalance"] = mr.groupValues[4].replace(",", "")
            }

        return meta
    }

    // ═════════════════════════════════════════════════════════════════════════
    // FORMAT B — Transaction Summary  (single signed Amount column)
    // ═════════════════════════════════════════════════════════════════════════

    private val txnSummaryDateRe = Regex(
        """^(\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\s+\d{4})\s+(.+)$""",
        RegexOption.IGNORE_CASE
    )
    // Matches one or two $ amounts at end of line, possibly with minus sign
    private val trailingAmountsRe = Regex(
        """(-?\$[\d,]+\.\d{2})\s+(\$[\d,]+\.\d{2})\s*$"""
    )
    // Single trailing amount (balance only, description line with no amount)
    private val singleTrailingRe = Regex("""(-?\$[\d,]+\.\d{2})\s*$""")

    private val continuationStripRe = Regex(
        """^(Card\s+xx\d+|Value\s+Date\s*:\s*\d{2}/\d{2}/\d{4})\s*$""",
        RegexOption.IGNORE_CASE
    )

    private fun parseTxnSummary(text: String): Statement {
        val meta  = extractMeta(text)
        val txns  = mutableListOf<Transaction>()
        val lines = text.lines()

        var pendingDate: LocalDate? = null
        var pendingDesc = StringBuilder()
        var pendingAmt:  Double?    = null
        var pendingBal:  Double?    = null

        fun commitPending() {
            val d = pendingDate ?: return
            val bal = pendingBal ?: return
            val amt = pendingAmt ?: 0.0
            val debit  = if (amt < 0) kotlin.math.abs(amt) else null
            val credit = if (amt > 0) amt else null
            txns.add(
                Transaction(
                    date        = DateParser.formatForExport(d),
                    description = pendingDesc.toString().trim(),
                    debit       = debit,
                    credit      = credit,
                    balance     = bal
                )
            )
            pendingDate = null
            pendingDesc.clear()
            pendingAmt  = null
            pendingBal  = null
        }

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isBlank()) continue
            if (extraSkip.containsMatchIn(line.lowercase())) continue
            if (continuationStripRe.matches(line)) continue  // strip Card / Value Date lines

            val dateMatch = txnSummaryDateRe.matchEntire(line)
            if (dateMatch != null) {
                // New transaction — commit previous
                commitPending()

                val dateStr  = dateMatch.groupValues[1]
                val rest     = dateMatch.groupValues[2].trim()

                pendingDate = parseTxnSummaryDate(dateStr)

                // Try to pull off trailing "amount balance" from the rest of the line
                val amtMatch = trailingAmountsRe.find(rest)
                if (amtMatch != null) {
                    val descPart = rest.substring(0, amtMatch.range.first).trim()
                    pendingDesc.append(descPart)
                    pendingAmt = parseSignedAmount(amtMatch.groupValues[1])
                    pendingBal = parseUnsignedAmount(amtMatch.groupValues[2])
                } else {
                    // Amounts might be on continuation line(s) — collect description
                    pendingDesc.append(rest)
                }
            } else {
                // Continuation line — does it carry the amounts?
                if (pendingDate != null && pendingBal == null) {
                    val amtMatch = trailingAmountsRe.find(line)
                    if (amtMatch != null) {
                        val descPart = line.substring(0, amtMatch.range.first).trim()
                        if (descPart.isNotEmpty()) {
                            if (pendingDesc.isNotEmpty()) pendingDesc.append(" ")
                            pendingDesc.append(descPart)
                        }
                        pendingAmt = parseSignedAmount(amtMatch.groupValues[1])
                        pendingBal = parseUnsignedAmount(amtMatch.groupValues[2])
                    } else {
                        // Pure description continuation — append
                        if (pendingDate != null) {
                            if (pendingDesc.isNotEmpty()) pendingDesc.append(" ")
                            pendingDesc.append(line)
                        }
                    }
                }
                // If pendingBal is already set this is a stray continuation — ignore
            }
        }
        commitPending()

        return Statement(
            bank            = bankName,
            accountNumber   = meta["accountNumber"] ?: "",
            from            = meta["from"] ?: "",
            to              = meta["to"] ?: "",
            openingBalance  = meta["openingBalance"]?.toDoubleOrNull(),
            closingBalance  = meta["closingBalance"]?.toDoubleOrNull(),
            transactions    = txns
        )
    }

    // ═════════════════════════════════════════════════════════════════════════
    // FORMAT A — Classic CBA statement  (separate Debit | Credit columns)
    // ═════════════════════════════════════════════════════════════════════════
    //
    // PDFBox flattens the table into lines like:
    //   "01 Apr Credit Interest 7.63 $1,072,657.74 CR"
    //   "01 Apr Transfer to other Bank NetBank\n12HERVEY RIVERWAY 16,131.84 $ $1,056,525.90 CR"
    //
    // We CANNOT reliably tell which column (debit vs credit) a number came from just
    // by its position in the flattened text, so we use balance-delta inference:
    //   delta = thisBalance - prevBalance
    //   delta < 0  →  debit  of |delta|
    //   delta > 0  →  credit of  delta
    //
    // Special CBA quirk: some transfers print the amount TWICE (debit column and
    // balance-embedded) — the balance delta approach handles this correctly because
    // we anchor to the balance figure, not the raw amount token.
    //
    // "Direct Debit 654064 RIVERWAY MEDICAL Com / RW WE23MARCH  $39,924.05" is a
    // CREDIT (payroll bureau credit) — the label "Direct Debit" is the BSB payment
    // type code, NOT a debit direction indicator.  Balance delta resolves this correctly.

    // "01 Apr" — day + abbreviated month
    private val classicDateRe = Regex(
        """^(\d{1,2})\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\s+(.*)$""",
        RegexOption.IGNORE_CASE
    )

    // Balance at end of line: optional "$", digits, comma-groups, decimal, optional " CR"
    private val balanceRe = Regex(
        """\$?([\d,]+\.\d{2})\s+CR\s*$""",
        RegexOption.IGNORE_CASE
    )

    // Any plain number (with optional $ prefix and commas) — used to strip trailing
    // amount tokens from description
    private val numberTokenRe = Regex("""\$?[\d,]+\.\d{2}""")

    // Rows that look like ONLY a balance figure — skip entirely
    private val balanceOnlyRe = Regex("""^\$?[\d,]+\.\d{2}\s*(CR)?\s*$""", RegexOption.IGNORE_CASE)

    private fun parseClassic(text: String): Statement {
        val meta = extractMeta(text)

        // Derive the statement year(s) from the period header
        // "1 Apr 2025 - 30 Jun 2025"  →  map month->year so we can attach year to "01 Apr"
        val yearMap = buildYearMap(meta["from"], meta["to"])
        val defaultYear = yearMap.values.firstOrNull() ?: LocalDate.now().year

        val txns   = mutableListOf<Transaction>()
        val lines  = text.lines()

        var pendingDate: LocalDate?     = null
        var pendingDescParts            = mutableListOf<String>()
        var pendingBalance: Double?     = null
        var prevBalance: Double?        = meta["openingBalance"]?.toDoubleOrNull()

        fun commitPending() {
            val d   = pendingDate   ?: return
            val bal = pendingBalance ?: return

            val delta = if (prevBalance != null) bal - prevBalance!! else 0.0
            val roundedDelta = kotlin.math.round(delta * 100) / 100.0

            val debit  = if (roundedDelta < -0.005) kotlin.math.abs(roundedDelta) else null
            val credit = if (roundedDelta >  0.005) roundedDelta else null

            // Clean description: strip trailing amount tokens that PDFBox left in
            val rawDesc = pendingDescParts.joinToString(" ").trim()
            val cleanDesc = rawDesc
                .replace(Regex("""\s+\$?[\d,]+\.\d{2}(\s+CR)?\s*$""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\s{2,}"""), " ")
                .trim()

            if (cleanDesc.isNotBlank() || debit != null || credit != null) {
                txns.add(
                    Transaction(
                        date        = DateParser.formatForExport(d),
                        description = cleanDesc,
                        debit       = debit,
                        credit      = credit,
                        balance     = bal
                    )
                )
                prevBalance = bal
            }

            pendingDate = null
            pendingDescParts.clear()
            pendingBalance = null
        }

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isBlank()) continue
            if (extraSkip.containsMatchIn(line.lowercase())) continue
            if (balanceOnlyRe.matches(line)) continue

            // Does this line START a new transaction?
            val dateMatch = classicDateRe.matchEntire(line)
            if (dateMatch != null) {
                commitPending()

                val day   = dateMatch.groupValues[1].toInt()
                val month = parseMonth(dateMatch.groupValues[2])
                val year  = yearMap[month] ?: defaultYear
                pendingDate = LocalDate.of(year, month, day)

                val rest = dateMatch.groupValues[3].trim()

                // Extract balance from end of line if present
                val balMatch = balanceRe.find(rest)
                if (balMatch != null) {
                    pendingBalance = parseUnsignedAmount(balMatch.groupValues[1])
                    val descPart = rest.substring(0, balMatch.range.first).trim()
                    if (descPart.isNotEmpty()) pendingDescParts.add(descPart)
                } else {
                    if (rest.isNotEmpty()) pendingDescParts.add(rest)
                }
            } else {
                // Continuation line for the current transaction
                if (pendingDate != null) {
                    val balMatch = balanceRe.find(line)
                    if (balMatch != null && pendingBalance == null) {
                        // This continuation line carries the balance
                        pendingBalance = parseUnsignedAmount(balMatch.groupValues[1])
                        val descPart = line.substring(0, balMatch.range.first).trim()
                        if (descPart.isNotEmpty()) pendingDescParts.add(descPart)
                    } else if (pendingBalance == null) {
                        // Still no balance — append to description
                        pendingDescParts.add(line)
                    }
                    // If pendingBalance already set, additional lines are noise — skip
                }
            }
        }
        commitPending()

        return Statement(
            bank            = bankName,
            accountNumber   = meta["accountNumber"] ?: "",
            from            = meta["from"] ?: "",
            to              = meta["to"] ?: "",
            openingBalance  = meta["openingBalance"]?.toDoubleOrNull(),
            closingBalance  = meta["closingBalance"]?.toDoubleOrNull(),
            transactions    = txns
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private val txnSummaryDateFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    private fun parseTxnSummaryDate(s: String): LocalDate? = try {
        LocalDate.parse(s.trim(), txnSummaryDateFmt)
    } catch (e: Exception) { null }

    /** Parse a signed amount string like "-$116.98" or "$23,100.00" → Double */
    private fun parseSignedAmount(s: String): Double {
        val negative = s.startsWith("-")
        val clean    = s.replace(Regex("""[,\$\s]"""), "").trimStart('-')
        val value    = clean.toDoubleOrNull() ?: 0.0
        return if (negative) -value else value
    }

    /** Parse an unsigned amount string like "39,874.41" or "$39,874.41" → Double */
    private fun parseUnsignedAmount(s: String): Double =
        s.replace(Regex("""[,\$\sCR]""", RegexOption.IGNORE_CASE), "").toDoubleOrNull() ?: 0.0

    private fun parseMonth(abbr: String): Int = when (abbr.lowercase()) {
        "jan" -> 1; "feb" -> 2; "mar" -> 3; "apr" -> 4
        "may" -> 5; "jun" -> 6; "jul" -> 7; "aug" -> 8
        "sep" -> 9; "oct" -> 10; "nov" -> 11; "dec" -> 12
        else  -> 1
    }

    /**
     * Build a month-number → year map from the statement period.
     * e.g. "1 Apr 2025 - 30 Jun 2025" → {4→2025, 5→2025, 6→2025}
     * If the period spans a year boundary, we assign months accordingly.
     */
    private fun buildYearMap(from: String?, to: String?): Map<Int, Int> {
        val result = mutableMapOf<Int, Int>()
        if (from == null || to == null) return result

        val fromDate = parsePeriodDate(from) ?: return result
        val toDate   = parsePeriodDate(to)   ?: return result

        var current = fromDate.withDayOfMonth(1)
        val end     = toDate.withDayOfMonth(1)
        while (!current.isAfter(end)) {
            result[current.monthValue] = current.year
            current = current.plusMonths(1)
        }
        return result
    }

    private val periodDateFmts = listOf(
        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd/MM/yy",   Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH),
    )

    private fun parsePeriodDate(s: String): LocalDate? {
        for (fmt in periodDateFmts) {
            try { return LocalDate.parse(s.trim(), fmt) } catch (_: Exception) {}
        }
        return null
    }
}
