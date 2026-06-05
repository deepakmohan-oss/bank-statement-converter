package com.ortuspro.model

data class Transaction(
    val date:String,
    val description:String,
    val debit:Double?,
    val credit:Double?,
    val balance:Double?
)
