
package com.ortuspro.service

object ParserRegistry {

    fun parserFor(bank: String): String {
        return when(bank.uppercase()) {
            "COMMONWEALTH" -> "CommonwealthParser"
            "ANZ" -> "AnzParser"
            "NAB" -> "NabParser"
            "WESTPAC" -> "WestpacParser"
            else -> "Unsupported"
        }
    }
}
