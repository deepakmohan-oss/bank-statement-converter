package com.ortuspro.api

import com.ortuspro.model.UploadResponse
import com.ortuspro.parser.BankDetector
import com.ortuspro.parser.PdfExtractor
import com.ortuspro.service.OcrService
import com.ortuspro.service.ReconciliationService
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

fun Route.uploadRoutes() {

    val ocr           = OcrService()
    val reconciler    = ReconciliationService()

    post("/api/upload") {
        val errors   = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var tempFile: File? = null

        try {
            val multipart = call.receiveMultipart()
            multipart.forEachPart { part ->
                if (part is PartData.FileItem) {
                    tempFile = File.createTempFile("statement_", ".pdf").also {
                        it.writeBytes(part.streamProvider().readBytes())
                    }
                }
                part.dispose()
            }

            val file = tempFile ?: run {
                call.respond(UploadResponse(success = false, errors = listOf("No file received")))
                return@post
            }

            // ── Extract text ───────────────────────────────────────────────
            var text = ""
            var ocrUsed = false

            try {
                text = PdfExtractor.extract(file)
            } catch (e: Exception) {
                call.respond(UploadResponse(success = false,
                    errors = listOf("Could not read PDF: ${e.message}")))
                file.delete(); return@post
            }

            // If text is too sparse, try OCR
            val avgCharsPerPage = if (text.isNotBlank()) {
                text.length / maxOf(text.count { it == '\n' } / 30, 1) // rough page estimate
            } else 0

            if (text.isBlank() || ocr.isScannedPdf(file)) {
                val (ocrAvailable, ocrMsg) = OcrService.isAvailable()
                if (!ocrAvailable) {
                    call.respond(UploadResponse(
                        success  = false,
                        errors   = listOf(
                            "This PDF appears to be a photographed or scanned copy — it contains images " +
                            "of pages, not machine-readable text. OCR is not available on this server. " +
                            "Please download a digital PDF directly from your bank's internet banking portal " +
                            "(e.g. NAB Internet Banking → Accounts → Statements → Download as PDF)."
                        )
                    ))
                    file.delete(); return@post
                }
                try {
                    text = ocr.ocrPdf(file)
                    ocrUsed = true
                    warnings.add(
                        "This PDF appears to be a photographed or scanned copy. OCR was applied — " +
                        "accuracy is good for flat scans but may be reduced for camera photos. " +
                        "Download a digital PDF from your bank's internet banking portal for best results."
                    )
                } catch (e: Exception) {
                    call.respond(UploadResponse(
                        success = false,
                        errors  = listOf(
                            "OCR failed: ${e.message}. This PDF is image-based (scanned or photographed). " +
                            "Please download a digital PDF from your bank's internet banking portal."
                        )
                    ))
                    file.delete(); return@post
                }
            }

            if (text.isBlank()) {
                call.respond(UploadResponse(success = false,
                    errors = listOf("No text could be extracted from this PDF.")))
                file.delete(); return@post
            }

            // ── Detect bank ────────────────────────────────────────────────
            val parser = BankDetector.detectParser(text)
            if (parser == null) {
                warnings.add("Bank not recognised — no supported bank detected in this PDF.")
            }

            // ── Parse ──────────────────────────────────────────────────────
            val statement = parser?.parse(text)
                ?: com.ortuspro.model.Statement(bank = "UNKNOWN")

            if (statement.transactions.isEmpty()) {
                warnings.add(
                    "No transactions were extracted. The statement format may not yet be fully supported. " +
                    "Detected bank: ${statement.bank}"
                )
            }

            // ── Reconcile ──────────────────────────────────────────────────
            val recon = reconciler.reconcile(statement)
            if (!recon.balanced && recon.warnings.isNotEmpty()) {
                warnings.addAll(recon.warnings.map { "Reconciliation: $it" })
            }

            call.respond(UploadResponse(
                success          = true,
                bank             = statement.bank,
                accountNumber    = statement.accountNumber,
                transactionCount = statement.transactions.size,
                statement        = statement,
                warnings         = warnings,
                errors           = errors
            ))

        } catch (e: Exception) {
            call.respond(UploadResponse(success = false,
                errors = listOf("Unexpected error: ${e.message}")))
        } finally {
            tempFile?.delete()
        }
    }
}
