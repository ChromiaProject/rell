/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.parser

import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer

class SyntaxErrorCollector : BaseErrorListener() {
    val errors = mutableListOf<SyntaxError>()

    override fun syntaxError(
        recognizer: Recognizer<*, *>?,
        offendingSymbol: Any?,
        line: Int,
        charPositionInLine: Int,
        message: String?,
        e: RecognitionException?
    ) {
        val sourceName = recognizer?.inputStream?.sourceName
        errors.add(SyntaxError(message, line, charPositionInLine, sourceName))
    }
}
