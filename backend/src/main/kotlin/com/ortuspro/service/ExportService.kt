package com.ortuspro.service

import com.ortuspro.model.Statement
import com.ortuspro.model.Transaction
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.OutputStream

/**
 * ExportService — exports transactions to CSV or XLSX.
 * XLSX format matches the Python app: Date / Description / Debit / Credit / Balance
 * with header styling and number formatting.
 */
class ExportService {

    // ── CSV ───────────────────────────────────────────────────────────────────

    fun exportCsv(transactions: List<Transaction>, file: File) {
        file.printWriter().use { out ->
            out.println("Date,Description,Debit,Credit,Balance")
            for (tx in transactions) {
                out.println(
                    "${tx.date}," +
                    "\"${tx.description.replace("\"", "\"\"")}\"," +
                    "${tx.debit ?: ""}," +
                    "${tx.credit ?: ""}," +
                    "${tx.balance ?: ""}"
                )
            }
        }
    }

    fun exportCsvToString(transactions: List<Transaction>): String {
        val sb = StringBuilder()
        sb.appendLine("Date,Description,Debit,Credit,Balance")
        for (tx in transactions) {
            sb.appendLine(
                "${tx.date}," +
                "\"${tx.description.replace("\"", "\"\"")}\"," +
                "${tx.debit ?: ""}," +
                "${tx.credit ?: ""}," +
                "${tx.balance ?: ""}"
            )
        }
        return sb.toString()
    }

    // ── XLSX ──────────────────────────────────────────────────────────────────

    fun exportXlsx(statement: Statement, outputStream: OutputStream) {
        XSSFWorkbook().use { wb ->
            val sheet = wb.createSheet("Transactions")

            // Styles
            val headerStyle = wb.createCellStyle().apply {
                fillForegroundColor = IndexedColors.DARK_BLUE.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
                val font = wb.createFont().apply {
                    bold = true
                    color = IndexedColors.WHITE.index
                }
                setFont(font)
            }
            val numberStyle = wb.createCellStyle().apply {
                dataFormat = wb.createDataFormat().getFormat("#,##0.00")
            }
            val dateStyle = wb.createCellStyle().apply {
                dataFormat = wb.createDataFormat().getFormat("dd/mm/yyyy")
            }

            // Header
            val headers = listOf("Date", "Description", "Debit", "Credit", "Balance")
            sheet.createRow(0).also { row ->
                headers.forEachIndexed { i, h ->
                    row.createCell(i).apply {
                        setCellValue(h)
                        cellStyle = headerStyle
                    }
                }
            }

            // Data rows
            for ((idx, tx) in statement.transactions.withIndex()) {
                val row = sheet.createRow(idx + 1)
                row.createCell(0).apply { setCellValue(tx.date); cellStyle = dateStyle }
                row.createCell(1).setCellValue(tx.description)
                row.createCell(2).apply { tx.debit?.let { setCellValue(it); cellStyle = numberStyle } }
                row.createCell(3).apply { tx.credit?.let { setCellValue(it); cellStyle = numberStyle } }
                row.createCell(4).apply { tx.balance?.let { setCellValue(it); cellStyle = numberStyle } }
            }

            // Summary sheet
            val summary = wb.createSheet("Summary")
            val summaryData = listOf(
                listOf("Bank", statement.bank),
                listOf("Account", statement.accountNumber),
                listOf("Account Name", statement.accountName),
                listOf("From", statement.statementFrom),
                listOf("To", statement.statementTo),
                listOf("Opening Balance", statement.openingBalance?.toString() ?: ""),
                listOf("Closing Balance", statement.closingBalance?.toString() ?: ""),
                listOf("Total Transactions", statement.transactions.size.toString()),
                listOf("Total Debits", statement.transactions.sumOf { it.debit ?: 0.0 }.toString()),
                listOf("Total Credits", statement.transactions.sumOf { it.credit ?: 0.0 }.toString())
            )
            summaryData.forEachIndexed { i, (k, v) ->
                summary.createRow(i).also { row ->
                    row.createCell(0).setCellValue(k)
                    row.createCell(1).setCellValue(v)
                }
            }

            // Auto-size columns
            for (i in 0..4) sheet.autoSizeColumn(i)

            wb.write(outputStream)
        }
    }

    fun exportXlsx(statement: Statement, file: File) {
        file.outputStream().use { exportXlsx(statement, it) }
    }
}
