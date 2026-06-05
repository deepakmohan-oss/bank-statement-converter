
package com.ortuspro.service

import com.ortuspro.model.Transaction
import java.io.File

class ExportService {
    fun exportCsv(txns: List<Transaction>, file: File) {
        file.printWriter().use { out ->
            out.println("Date,Description,Debit,Credit,Balance")
            txns.forEach {
                out.println("${it.date},${it.description},${it.debit ?: ""},${it.credit ?: ""},${it.balance ?: ""}")
            }
        }
    }
}
