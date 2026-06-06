package com.ortuspro.model

import kotlinx.serialization.Serializable

@Serializable
data class Statement(
    val bank: String,
    val accountNumber: String = "",
    val accountName: String = "",
    val openingBalance: Double? = null,
    val closingBalance: Double? = null,
    val statementFrom: String = "",
    val statementTo: String = "",
    val transactions: List<Transaction> = emptyList()
)
