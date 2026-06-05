
package com.ortuspro.api

import com.ortuspro.parser.BankDetector
import com.ortuspro.parser.PdfExtractor
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

fun Route.uploadRoutes() {

    post("/api/upload") {

        val multipart = call.receiveMultipart()

        var detectedBank = "UNKNOWN"
        var extractedText = ""

        multipart.forEachPart { part ->
            if (part is PartData.FileItem) {

                val tempFile = File.createTempFile("statement", ".pdf")
                tempFile.writeBytes(part.streamProvider().readBytes())

                extractedText = PdfExtractor.extract(tempFile)
                detectedBank = BankDetector.detect(extractedText)

                tempFile.delete()
            }
            part.dispose()
        }

        call.respond(
            mapOf(
                "success" to true,
                "bank" to detectedBank,
                "previewLength" to extractedText.length
            )
        )
    }
}
