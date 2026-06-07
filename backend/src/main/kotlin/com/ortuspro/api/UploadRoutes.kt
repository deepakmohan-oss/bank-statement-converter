package com.ortuspro.api

import com.ortuspro.model.UploadResponse
import com.ortuspro.parser.BankDetector
import com.ortuspro.parser.PdfExtractor
import com.ortuspro.service.OcrService
import com.ortuspro.service.RateLimiter
import com.ortuspro.service.ReconciliationService
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

private const val MAX_FILE_BYTES   = 20 * 1024 * 1024L  // 20 MB
private const val MAX_REQUESTS_PER_MIN = 10              // per IP

fun Route.uploadRoutes() {

    val ocr        = OcrService()
    val reconciler = ReconciliationService()
    val limiter    = RateLimiter(MAX_REQUESTS_PER_MIN)

    post("/api/upload") {
        // ── Rate limiting ──────────────────────────────────────────────────
        val ip = call.request.local.remoteAddress
        if (!limiter.allow(ip)) {
            call.respond(
                HttpStatusCode.TooManyRequests,
                UploadResponse(success = false,
                    errors = listOf("Too many requests — please wait a minute before retrying."))
            )
            return@post
        }

        val errors   = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var tempFile: File? = null

        try {
            // ── Receive multipart with size guard ──────────────────────────
            val multipart = call.receiveMultipart()
            var bytesReceived = 0L

            multipart.forEachPart { part ->
                if (part is PartData.FileItem) {
                    val bytes = part.streamProvider().readBytes()
                    bytesReceived = bytes.size.toLong()

                    if (bytesReceived > MAX_FILE_BYTES) {
                        part.dispose()
                        return@forEachPart
                    }

                    if (!part.originalFileName.orEmpty().endsWith(".pdf", ignoreCase = true)) {
                        part.dispose()
                        errors.add("Only PDF files are supported.")
                        return@forEachPart
                    }

                    tempFile = File.createTempFile("statement_", ".pdf").also {
                        it.writeBytes(bytes)
                    }
                }
                part.dispose()
            }

            if (bytesReceived > MAX_FILE_BYTES) {
                call.respond(UploadResponse(success = false,
                    errors = listOf("File too large. Maximum size is 20 MB.")))
                tempFile?.delete()
                return@post
            }

            if (errors.isNotEmpty()) {
                call.respond(UploadResponse(success = false, errors = errors))
                tempFile?.delete()
                return@post
            }

            val file = tempFile ?: run {
                call.respond(UploadResponse(success = false,
                    errors = listOf("No file received.")))
                return@post
            }

            // ── Extract text ───────────────────────────────────────────────
            var text    = ""
            var ocrUsed = false

            try {
                text = PdfExtractor.extract(file)
            } catch (e: Exception) {
                call.respond(UploadResponse(success = false,
                    errors = listOf("Could not read PDF: ${e.message}")))
                file.delete(); return@post
            }

            // ── Scanned PDF detection → OCR fallback ───────────────────────
            if (text.isBlank() || ocr.isScannedPdf(file)) {
                val (ocrAvailable, _) = OcrService.isAvailable()
                if (!ocrAvailable) {
                    call.respond(UploadResponse(success = false, errors = listOf(
                        "This PDF appears to be a photographed or scanned copy. " +
                        "OCR is not available on this server. " +
                        "Please download a digital PDF from your bank's internet banking portal."
                    )))
                    file.delete(); return@post
                }
                try {
                    text    = ocr.ocrPdf(file)
                    ocrUsed = true
                    warnings.add(
                        "This PDF is a scanned or photographed copy — OCR was applied. " +
                        "For best accuracy, download a digital PDF from your bank portal."
                    )
                } catch (e: Exception) {
                    call.respond(UploadResponse(success = false, errors = listOf(
                        "OCR failed: ${e.message}. " +
                        "Please download a digital PDF from your bank's internet banking portal."
                    )))
                    file.delete(); return@post
                }
            }

            if (text.isBlank()) {
                call.respond(UploadResponse(success = false,
                    errors = listOf("No text could be extracted from this PDF.")))
                file.delete(); return@post
            }

            // ── Detect & parse ─────────────────────────────────────────────
            val parser = BankDetector.detectParser(text)
            if (parser == null) {
                warnings.add("Bank not recognised. Supported: Commonwealth, NAB, Westpac, ANZ, " +
                             "Macquarie, BOQ, Auswide, Bankwest, Bendigo, ING, St George.")
            }

            val statement = parser?.parse(text)
                ?: com.ortuspro.model.Statement(bank = "UNKNOWN")

            if (statement.transactions.isEmpty()) {
                warnings.add("No transactions were extracted. The statement format may not be fully supported yet.")
            }

            // ── Reconcile ──────────────────────────────────────────────────
            val recon = reconciler.reconcile(statement)
            if (!recon.balanced && recon.warnings.isNotEmpty()) {
                warnings.addAll(recon.warnings.map { "Reconciliation: $it" })
            }

            file.delete()

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
            tempFile?.delete()
            call.respond(UploadResponse(success = false,
                errors = listOf("Unexpected error: ${e.message}")))
        }
    }
}
