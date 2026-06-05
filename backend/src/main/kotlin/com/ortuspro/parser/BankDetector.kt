
package com.ortuspro.parser

object BankDetector {
    fun detect(text:String): String = when {
        text.contains("COMMONWEALTH", true) -> "COMMONWEALTH"
        text.contains("WESTPAC", true) -> "WESTPAC"
        text.contains("ANZ", true) -> "ANZ"
        text.contains("NAB", true) || text.contains("NATIONAL AUSTRALIA BANK", true) -> "NAB"
        text.contains("MACQUARIE", true) -> "MACQUARIE"
        else -> "UNKNOWN"
    }
}
