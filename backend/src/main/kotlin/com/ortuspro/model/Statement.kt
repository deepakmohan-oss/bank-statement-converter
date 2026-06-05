
package com.ortuspro.model

data class Statement(
    val bank:String,
    val accountNumber:String="",
    val transactions:List<Transaction> = emptyList()
)
