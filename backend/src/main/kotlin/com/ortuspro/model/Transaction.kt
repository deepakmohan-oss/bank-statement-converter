package com.ortuspro.model

import kotlinx.serialization.Serializable

@Serializable
data class Transaction(
    val date: String,           // dd/MM/yyyy
    val description: String,
    val debit: Double?,
    val credit: Double?,
    val balance: Double?,
    val rawLine: String = ""    // original line for debugging
)
