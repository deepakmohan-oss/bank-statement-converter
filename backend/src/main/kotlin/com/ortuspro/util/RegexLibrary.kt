package com.ortuspro.util

/**
 * RegexLibrary — central source of truth for all date, amount, and skip/stop patterns.
 * Ported from the Python bank parser engine.
 */
object RegexLibrary {

    // ── Amount ────────────────────────────────────────────────────────────────
    val AMOUNT = Regex("""-?[\d,]+\.\d{2}""")

    // ── Date formats ──────────────────────────────────────────────────────────
    val DATE_SLASH     = Regex("""^(\d{1,2}/\d{1,2}/\d{2,4})\s+(.*)$""",     RegexOption.IGNORE_CASE)
    val DATE_DOT       = Regex("""^(\d{1,2}\.\d{1,2}\.\d{2,4})\s+(.*)$""",   RegexOption.IGNORE_CASE)
    val DATE_ABBR      = Regex("""^(\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec))\s+(.*)$""",            RegexOption.IGNORE_CASE)
    val DATE_ABBR_YEAR = Regex("""^(\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\s+\d{2,4})\s+(.*)$""", RegexOption.IGNORE_CASE)
    val DATE_HYPHEN    = Regex("""^(\d{1,2}-(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)(?:-\d{2,4})?)\b(.*)$""", RegexOption.IGNORE_CASE)
    val YEAR_ONLY      = Regex("""^\s*((?:19|20)\d{2})\b""")

    // ── Universal boilerplate to skip across all Australian banks ─────────────
    val BASE_SKIP = Regex(
        listOf(
            """page \d+ of \d+""", """please check""", """terms and conditions""",
            """financial complaints""", """australian government deposit""",
            """cheque deposit""", """eStatements""", """access to and sharing""",
            """help centre""", """interest rates""", """effective date""",
            """credit interest rate""", """closing balance as at""",
            """total debits.*credits""", """total income paid""",
            """annual interest summary""", """authority descriptions""",
            """general withdrawal authority""", """view authority""",
            """continued on next page""",
            """abn \d{2} \d{3} \d{3} \d{3}""",
            """you should check all entries""",
            """protect your""", """security alert""",
            """online banking""", """authenticator""",
            """important information.*we try""",
            """may not include all transactions""",
            """protected account under banking""",
            """financial claims scheme""",
            """ensure noone is watching""", """suspect unauthorised use""",
            """card is lost.*stolen""", """others may know your pin""",
            """call.*immediately on \d""",
            """month will be paid""", """balances greater than""",
            """interest is calculated""", """subject to approval""",
            """retirement fund$""", """superannuation fund$""",
            """between your last statement""", """charges booklets""",
            """you can also obtain""", """statement integrity""",
            """date\s+transaction\s+description""",
            """^\s*$"""
        ).joinToString("|"),
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    )

    val BASE_STOP = Regex(
        listOf(
            """for the period""", """total interest credited""",
            """use online.*mobile.*tablet""",
            """please note the following financial""",
            """we offer several options"""
        ).joinToString("|"),
        RegexOption.IGNORE_CASE
    )

    // Truncate description when these strings appear
    val DESC_CUTOFFS = listOf(
        "Statement No.", "Page No.", "Australian Government",
        "Please check", "Further information", "If you have a complaint",
        "AFCA", "www.", "http"
    )
}
