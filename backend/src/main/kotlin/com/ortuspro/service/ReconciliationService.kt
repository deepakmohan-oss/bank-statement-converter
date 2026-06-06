package com.ortuspro.service

import com.ortuspro.model.Statement
import com.ortuspro.model.Transaction

/**
 * ReconciliationService — validates parsed transactions against statement balances.
 * Ported from the Python app's reconciliation logic.
 *
 * Checks:
 *   1. Running balance: each transaction's balance = prevBalance ± amount (within tolerance)
 *   2. Opening→Closing: openingBalance + totalCredits - totalDebits ≈ closingBalance
 *   3. Transaction count sanity
 */
class ReconciliationService {

    data class ReconciliationResult(
        val balanced: Boolean,
        val openingBalance: Double?,
        val closingBalance: Double?,
        val computedClosing: Double?,
        val totalDebits: Double,
        val totalCredits: Double,
        val transactionCount: Int,
        val discrepancy: Double?,
        val warnings: List<String>
    )

    companion object {
        const val TOLERANCE = 0.02  // $0.02 tolerance for floating point
    }

    fun reconcile(statement: Statement): ReconciliationResult {
        val txns     = statement.transactions
        val warnings = mutableListOf<String>()

        val totalDebits  = txns.sumOf { it.debit  ?: 0.0 }
        val totalCredits = txns.sumOf { it.credit ?: 0.0 }

        if (txns.isEmpty()) {
            return ReconciliationResult(
                balanced         = false,
                openingBalance   = statement.openingBalance,
                closingBalance   = statement.closingBalance,
                computedClosing  = null,
                totalDebits      = 0.0,
                totalCredits     = 0.0,
                transactionCount = 0,
                discrepancy      = null,
                warnings         = listOf("No transactions to reconcile")
            )
        }

        val firstBal = txns.first().balance ?: 0.0
        val lastBal  = txns.last().balance  ?: 0.0

        // Check running balance continuity
        var prevBal = firstBal
        var runningBalanceOk = true
        for ((i, tx) in txns.withIndex()) {
            if (i == 0) continue
            val expectedBal = prevBal - (tx.debit ?: 0.0) + (tx.credit ?: 0.0)
            val actualBal   = tx.balance ?: 0.0
            if (Math.abs(expectedBal - actualBal) > TOLERANCE) {
                warnings.add("Balance discontinuity at ${tx.date}: expected ${f(expectedBal)} got ${f(actualBal)}")
                runningBalanceOk = false
            }
            prevBal = actualBal
        }

        // Check opening→closing
        val computedClosing = if (statement.openingBalance != null) {
            statement.openingBalance + totalCredits - totalDebits
        } else null

        val discrepancy = if (computedClosing != null && statement.closingBalance != null) {
            Math.abs(computedClosing - statement.closingBalance)
        } else null

        val closingMatch = discrepancy == null || discrepancy <= TOLERANCE
        // Also check last parsed balance vs stated closing
        val lastBalMatch = statement.closingBalance == null ||
            Math.abs(lastBal - statement.closingBalance) <= TOLERANCE

        if (!lastBalMatch && statement.closingBalance != null) {
            warnings.add("Last transaction balance ${f(lastBal)} ≠ stated closing ${f(statement.closingBalance)}")
        }
        if (!closingMatch) {
            warnings.add("Computed closing ${f(computedClosing!!)} ≠ stated closing ${f(statement.closingBalance!!)}")
        }

        val balanced = runningBalanceOk && closingMatch && lastBalMatch

        return ReconciliationResult(
            balanced         = balanced,
            openingBalance   = statement.openingBalance,
            closingBalance   = statement.closingBalance,
            computedClosing  = computedClosing,
            totalDebits      = totalDebits,
            totalCredits     = totalCredits,
            transactionCount = txns.size,
            discrepancy      = discrepancy,
            warnings         = warnings
        )
    }

    private fun f(v: Double) = "$${"%.2f".format(v)}"
}
