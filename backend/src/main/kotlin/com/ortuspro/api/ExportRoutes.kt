
package com.ortuspro.api

import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.exportRoutes() {

    get("/api/export/status") {
        call.respond(
            mapOf(
                "csv" to true,
                "xlsx" to false
            )
        )
    }
}
