package com.ortuspro.api

import com.ortuspro.model.Statement
import com.ortuspro.service.MergeService
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.mergeRoutes() {

    val mergeService = MergeService()

    post("/api/merge") {
        val statements = call.receive<List<Statement>>()
        val merged = mergeService.merge(statements, removeDuplicates = true)
        call.respond(merged)
    }
}
