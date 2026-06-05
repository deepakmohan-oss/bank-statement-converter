package com.ortuspro.model

data class Statement(
    val bankName:String,
    val accountNumber:String,
    val transactions:List<Transaction>
)
