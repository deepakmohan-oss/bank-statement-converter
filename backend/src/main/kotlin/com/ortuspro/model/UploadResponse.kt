package com.ortuspro.model

import kotlinx.serialization.Serializable

@Serializable
data class UploadResponse(
    val success: Boolean,
    val bank: String = "",
    val accountNumber: String = "",
    val transactionCount: Int = 0,
    val statement: Statement? = null,
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList()
)
