
package com.ortuspro.api

import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.mergeRoutes() {
    post("/api/merge") {
        call.respond(mapOf("success" to true, "message" to "Merge endpoint scaffold"))
    }
}
