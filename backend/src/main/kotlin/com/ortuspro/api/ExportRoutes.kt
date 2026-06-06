
package com.ortuspro.api

import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.exportRoutes() {

    get("/api/export/csv") {
        call.respond(mapOf("status" to "csv export ready"))
    }

    get("/api/export/xlsx") {
        call.respond(mapOf("status" to "xlsx export ready"))
    }
}
