package com.ortuspro.parser

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File

object PdfExtractor {
    fun extract(file: File): String {
        Loader.loadPDF(file).use { pdf ->
            return PDFTextStripper().getText(pdf)
        }
    }
}
