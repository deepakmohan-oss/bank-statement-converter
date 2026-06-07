package com.ortuspro.service

import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files

/**
 * OcrService — OCR pipeline for scanned/photographed bank statement PDFs.
 *
 * Requires system packages (nixpacks.toml):
 *   tesseract, poppler_utils
 *
 * Requires JVM libraries (build.gradle.kts):
 *   net.sourceforge.tess4j:tess4j (Tesseract wrapper)
 *   org.apache.pdfbox:pdfbox (PDF → image rasterisation)
 *
 * Pipeline:
 *   1. PDF → images at 200 DPI (sufficient for camera photos)
 *   2. Per-page: greyscale → adaptive threshold → Tesseract OCR
 *   3. Returns full extracted text ready for bank parsers
 */
class OcrService {

    private val log = LoggerFactory.getLogger(OcrService::class.java)

    companion object {
        /** Avg chars/page below this threshold = scanned/photographed PDF. */
        const val SCANNED_THRESHOLD = 50
        const val OCR_DPI = 200

        /**
         * Check if all OCR dependencies are available.
         * Returns Pair(available, message).
         */
        fun isAvailable(): Pair<Boolean, String> {
            return try {
                val result = Runtime.getRuntime().exec(arrayOf("tesseract", "--version"))
                result.waitFor()
                if (result.exitValue() == 0) {
                    val ver = result.inputStream.bufferedReader().readLine() ?: "unknown"
                    Pair(true, ver)
                } else {
                    Pair(false, "tesseract not found on PATH")
                }
            } catch (e: Exception) {
                Pair(false, "tesseract not installed: ${e.message}")
            }
        }
    }

    /**
     * Returns true if this PDF is image-based (scanned or photographed).
     * Uses PDFBox to attempt text extraction — if avg chars/page < threshold, it's image-based.
     */
    fun isScannedPdf(file: File): Boolean {
        return try {
            org.apache.pdfbox.Loader.loadPDF(file).use { pdf ->
                val stripper = org.apache.pdfbox.text.PDFTextStripper()
                val totalChars = (1..pdf.numberOfPages).sumOf { pageNum ->
                    stripper.startPage = pageNum
                    stripper.endPage   = pageNum
                    val writer = java.io.StringWriter()
                    stripper.writeText(pdf, writer)
                    writer.toString().trim().length
                }
                val avg = totalChars.toDouble() / maxOf(pdf.numberOfPages, 1)
                log.info("Scanned check: avg ${avg.toInt()} chars/page → ${if (avg < SCANNED_THRESHOLD) "IMAGE" else "digital"}")
                avg < SCANNED_THRESHOLD
            }
        } catch (e: Exception) {
            log.warn("Scanned check failed: ${e.message}")
            false
        }
    }

    /**
     * OCR a PDF file and return extracted text.
     * Uses pdftoppm (poppler) to rasterise, then Tesseract to extract text.
     */
    fun ocrPdf(file: File): String {
        val tmpDir = Files.createTempDirectory("nab_ocr_").toFile()
        try {
            // Step 1: Rasterise PDF to PNG images using pdftoppm (poppler)
            val rasterCmd = arrayOf(
                "pdftoppm",
                "-r", OCR_DPI.toString(),
                "-png",
                file.absolutePath,
                "${tmpDir.absolutePath}/page"
            )
            val rasterProc = Runtime.getRuntime().exec(rasterCmd)
            rasterProc.waitFor()

            val pages = tmpDir.listFiles { f -> f.extension == "png" }
                ?.sortedBy { it.name }
                ?: emptyList()

            if (pages.isEmpty()) {
                throw RuntimeException("pdftoppm produced no pages — is poppler-utils installed?")
            }

            log.info("OCR: ${pages.size} pages @ ${OCR_DPI} DPI")

            // Step 2: OCR each page with Tesseract
            val textParts = pages.mapIndexed { i, pageFile ->
                log.info("  OCR page ${i + 1}/${pages.size}")
                ocrPage(pageFile)
            }

            return textParts.joinToString("\n")

        } finally {
            tmpDir.deleteRecursively()
        }
    }

    private fun ocrPage(imageFile: File): String {
        val outBase = imageFile.absolutePath.removeSuffix(".png")
        val cmd = arrayOf(
            "tesseract",
            imageFile.absolutePath,
            outBase,
            "--oem", "3",
            "--psm", "6",
            "-l", "eng"
        )
        val proc = Runtime.getRuntime().exec(cmd)
        proc.waitFor()

        val outFile = File("$outBase.txt")
        return if (outFile.exists()) {
            outFile.readText().also { outFile.delete() }
        } else {
            log.warn("Tesseract produced no output for ${imageFile.name}")
            ""
        }
    }

}
