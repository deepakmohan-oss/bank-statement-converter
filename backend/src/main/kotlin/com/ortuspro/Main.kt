package com.ortuspro

import com.ortuspro.api.exportRoutes
import com.ortuspro.api.mergeRoutes
import com.ortuspro.api.uploadRoutes
import com.ortuspro.service.OcrService
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import java.io.File

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080

    embeddedServer(Netty, host = "0.0.0.0", port = port) {

        install(ContentNegotiation) {
            json(Json {
                prettyPrint       = false
                isLenient         = true
                ignoreUnknownKeys = true
            })
        }

        install(CORS) {
            anyHost()
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Authorization)
        }

        install(StatusPages) {
            exception<Throwable> { call, cause ->
                call.respondText(
                    """{"success":false,"errors":["Server error: ${cause.message}"]}""",
                    ContentType.Application.Json,
                    HttpStatusCode.InternalServerError
                )
            }
        }

        routing {

            get("/health") {
                val (ocrOk, ocrMsg) = OcrService.isAvailable()
                call.respond(mapOf(
                    "status"          to "healthy",
                    "version"         to "2.1.0",
                    "ocr_available"   to ocrOk,
                    "ocr_version"     to ocrMsg,
                    "supported_banks" to listOf(
                        "COMMONWEALTH", "NAB", "WESTPAC", "ANZ",
                        "MACQUARIE", "BOQ", "AUSWIDE",
                        "BANKWEST", "BENDIGO", "ING", "ST_GEORGE"
                    )
                ))
            }

            uploadRoutes()
            exportRoutes()
            mergeRoutes()

            // Serve React build (Vite output into resources/static)
            static("/") {
                resources("static")
                defaultResource("index.html", "static")
            }
        }

    }.start(wait = true)
}
