package com.ortuspro.parser

import com.ortuspro.model.Statement

interface StatementParser {
    fun parse(text:String): Statement
}
