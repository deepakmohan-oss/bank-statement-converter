package com.ortuspro.api

import com.ortuspro.model.Statement
import com.ortuspro.service.ExportService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun Route.exportRoutes() {

    val exportService = ExportService()

    post("/api/export/csv") {
        val statement = call.receive<Statement>()
        val csv = exportService.exportCsvToString(statement.transactions)
        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(
                ContentDisposition.Parameters.FileName,
                "transactions_${statement.bank}.csv"
            ).toString()
        )
        call.respondText(csv, ContentType.Text.CSV)
    }

    post("/api/export/xlsx") {
        val statement = call.receive<Statement>()
        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(
                ContentDisposition.Parameters.FileName,
                "transactions_${statement.bank}.xlsx"
            ).toString()
        )
        call.respondOutputStream(ContentType.parse("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) {
            exportService.exportXlsx(statement, this)
        }
    }
}
