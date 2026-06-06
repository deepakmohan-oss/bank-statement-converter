package com.ortuspro.parser

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import java.io.File
import java.io.StringWriter
import java.util.logging.Level
import java.util.logging.Logger

/**
 * PdfExtractor — tuned PDFBox text extraction for Australian bank statements.
 *
 * Equivalent to pdfplumber tuning in the Python app:
 *   - Sort by position (left-to-right, top-to-bottom) for correct column ordering
 *   - Word separator preserved (space between tokens on same line)
 *   - Line separator = newline
 *   - Drop zero-width / invisible characters
 *   - Suppress PDFBox font warnings (same issue as pdfplumber FontBBox errors)
 *
 * Additional AU bank-specific handling:
 *   - "blank" placeholder removal (ANZ)
 *   - Dot-leader normalisation (NAB: "......  1,641.50" → "  1,641.50")
 *   - "CR"/"DR" suffix preservation (BOQ, CBA)
 *   - Negative amount normalisation ("-$10.00" → "-10.00")
 */
object PdfExtractor {

    init {
        // Suppress noisy PDFBox font warnings — same as Python's suppress_warnings pattern
        listOf(
            "org.apache.pdfbox",
            "org.apache.fontbox",
            "org.apache.pdfbox.pdmodel.font"
        ).forEach { Logger.getLogger(it).level = Level.SEVERE }
    }

    fun extract(file: File): String {
        Loader.loadPDF(file).use { pdf ->
            val stripper = BankStatementStripper()
            stripper.sortByPosition = true
            stripper.wordSeparator  = " "
            stripper.lineSeparator  = "\n"
            val writer = StringWriter()
            stripper.writeText(pdf, writer)
            return postProcess(writer.toString())
        }
    }

    /**
     * Post-processing pipeline — applied after raw PDFBox extraction.
     * Order matters: dot leaders before amount normalisation.
     */
    private fun postProcess(raw: String): String {
        return raw
            .let { normaliseDotLeaders(it) }       // NAB "........ 1,641.50" → " 1,641.50"
            .let { normaliseNegativeAmounts(it) }   // "$-10.00" or "- $10.00" → "-10.00"
            .let { removeAnzBlanks(it) }            // "blank" placeholder (ANZ)
            .let { normaliseWhitespace(it) }        // collapse multiple spaces, normalise CRLF
            .let { removeGarbage(it) }              // barcodes, base64 noise, CID codes
            .trimEnd()
    }

    /** Strip dot-leader separators used in NAB statements. */
    private fun normaliseDotLeaders(s: String): String =
        s.replace(Regex("""\.{4,}"""), "  ")

    /** Normalise "$-10.00", "- $10.00", "-$10.00" → standard "-10.00" form. */
    private fun normaliseNegativeAmounts(s: String): String =
        s.replace(Regex("""-\s*\$\s*([\d,]+\.\d{2})"""), "-$1")
         .replace(Regex("""\+\s*\$\s*([\d,]+\.\d{2})"""), "$1")

    /** Remove ANZ "blank" placeholder tokens that appear for empty columns. */
    private fun removeAnzBlanks(s: String): String =
        s.replace(Regex("""(?m)^\s*blank\s*$""", RegexOption.IGNORE_CASE), "")

    private fun normaliseWhitespace(s: String): String =
        s.replace("\r\n", "\n").replace("\r", "\n")
         .replace(Regex("""[ \t]{2,}"""), "  ")   // collapse but keep double-space as column separator hint
         .replace(Regex("""\n{3,}"""), "\n\n")

    /** Remove barcode data, base64 noise, CID character codes from PDFBox artifacts. */
    private fun removeGarbage(s: String): String =
        s.replace(Regex("""\(cid:\d+\)"""), "")             // CID font artifacts
         .replace(Regex("""[^\x20-\x7E\n\r\t]"""), "")     // non-ASCII printable
         .replace(Regex("""(?m)^\s*[0-9A-F]{20,}\s*$"""), "") // long hex strings (barcodes)
}

/**
 * BankStatementStripper — custom PDFTextStripper subclass.
 * Filters out invisible/zero-width characters that create noise in bank statements.
 */
private class BankStatementStripper : PDFTextStripper() {
    override fun writeString(text: String, textPositions: List<TextPosition>) {
        // Filter zero-width and whitespace-only text positions
        val filtered = textPositions.filter { tp ->
            tp.unicode.isNotBlank() && tp.widthDirAdj > 0
        }
        if (filtered.isNotEmpty()) {
            super.writeString(text, filtered)
        }
    }
}
