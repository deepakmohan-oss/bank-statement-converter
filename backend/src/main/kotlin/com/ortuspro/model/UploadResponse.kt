package com.ortuspro.model

data class UploadResponse(
    val success:Boolean,
    val bank:String,
    val transactionCount:Int
)
