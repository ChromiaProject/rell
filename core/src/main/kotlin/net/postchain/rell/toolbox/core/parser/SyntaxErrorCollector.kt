package net.postchain.rell.toolbox.core.parser

import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer

class SyntaxError(val message: String?, val line: Int, val charPositionInLine: Int, val sourceName: String?)

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