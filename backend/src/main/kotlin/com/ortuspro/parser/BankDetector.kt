package com.ortuspro.parser

object BankDetector {
    fun detect(text:String):String = when {
        text.contains("COMMONWEALTH", true) -> "COMMONWEALTH"
        text.contains("WESTPAC", true) -> "WESTPAC"
        text.contains("NAB", true) -> "NAB"
        text.contains("ANZ", true) -> "ANZ"
        else -> "UNKNOWN"
    }
}
