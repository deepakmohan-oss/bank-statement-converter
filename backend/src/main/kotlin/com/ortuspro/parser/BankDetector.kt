package com.ortuspro.parser
object BankDetector {
 fun detect(text:String):String = when {
  text.contains("COMMONWEALTH",true)->"COMMONWEALTH"
  text.contains("WESTPAC",true)->"WESTPAC"
  text.contains("ANZ",true)->"ANZ"
  text.contains("NATIONAL AUSTRALIA BANK",true)->"NAB"
  text.contains("MACQUARIE",true)->"MACQUARIE"
  text.contains("ING",true)->"ING"
  text.contains("BANKWEST",true)->"BANKWEST"
  text.contains("BENDIGO",true)->"BENDIGO"
  text.contains("ST.GEORGE",true)->"STGEORGE"
  else -> "UNKNOWN"
 }
}
