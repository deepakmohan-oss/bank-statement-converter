
package com.ortuspro

import com.ortuspro.api.uploadRoutes
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080

    embeddedServer(Netty, host = "0.0.0.0", port = port) {

        install(ContentNegotiation) { json() }

        routing {

            get("/") {
                call.respond(mapOf(
                    "service" to "OrtusPro AU Bank Converter",
                    "status" to "running"
                ))
            }

            get("/health") {
                call.respond(mapOf("status" to "healthy"))
            }

            uploadRoutes()
        }

    }.start(wait = true)
}
