/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.parser

import net.postchain.rell.base.compiler.parser.antlr.RellLexer
import net.postchain.rell.base.compiler.parser.antlr.RellParser
import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.misc.IntervalSet

/**
 * Formats ANTLR syntax errors the way IDE users expect them ("Name expected"): raw grammar token
 * names (`RULE_ID`) and internal phrasing ("mismatched input", "no viable alternative") never
 * surface. Shared by the compiler's C_Parser and the language-server parser so both report the
 * same message for the same mistake. Recovery behavior is inherited from [DefaultErrorStrategy]
 * unchanged — only the report methods that compose user-visible messages are overridden.
 */
class RellParserErrorStrategy : DefaultErrorStrategy() {
    override fun reportInputMismatch(recognizer: Parser, e: InputMismatchException) {
        recognizer.notifyErrorListeners(e.offendingToken, expectedGot(e.expectedTokens, e.offendingToken), e)
    }

    override fun reportNoViableAlternative(recognizer: Parser, e: NoViableAltException) {
        val token = e.offendingToken ?: e.startToken
        recognizer.notifyErrorListeners(token, unexpected(token), e)
    }

    override fun reportUnwantedToken(recognizer: Parser) {
        if (inErrorRecoveryMode(recognizer)) return
        beginErrorCondition(recognizer)
        val token = recognizer.currentToken
        recognizer.notifyErrorListeners(token, expectedGot(getExpectedTokens(recognizer), token), null)
    }

    override fun reportMissingToken(recognizer: Parser) {
        if (inErrorRecoveryMode(recognizer)) return
        beginErrorCondition(recognizer)
        val token = recognizer.currentToken
        val expected = displayTokenSet(getExpectedTokens(recognizer))
        val msg = if (expected == null) unexpected(token) else capitalize("$expected expected")
        recognizer.notifyErrorListeners(token, msg, null)
    }

    private fun expectedGot(expected: IntervalSet?, token: Token?): String {
        val display = displayTokenSet(expected) ?: return unexpected(token)
        return capitalize("$display expected, got ${displayToken(token)}")
    }

    private fun unexpected(token: Token?): String = when {
        token == null -> "Unexpected input"
        token.type == Token.EOF -> "Unexpected end of file"
        else -> "Unexpected token ${displayToken(token)}"
    }

    /**
     * Human-readable "A, B or C" rendering of an expected-token set, or null when there is nothing
     * to show — the set is empty, or so wide that listing alternatives would be noise (callers then
     * degrade to a plain "unexpected token" message).
     */
    private fun displayTokenSet(expected: IntervalSet?): String? {
        expected ?: return null
        val names = expected.toList().map { displayTokenType(it) }.distinct()
        return when {
            names.isEmpty() || names.size > MAX_EXPECTED_TOKENS -> null
            names.size == 1 -> names[0]
            else -> names.dropLast(1).joinToString(", ") + " or " + names.last()
        }
    }

    private fun displayToken(token: Token?): String = when {
        token == null -> "input"
        token.type == Token.EOF -> "end of file"
        else -> "'${escapeTokenText(token.text ?: "")}'"
    }

    private fun displayTokenType(type: Int): String = when (type) {
        Token.EOF -> "end of file"
        RellLexer.RULE_ID -> "name"
        RellLexer.RULE_NUMBER, RellLexer.RULE_BIG_INTEGER, RellLexer.RULE_DECIMAL -> "number"
        RellLexer.RULE_STRING -> "string"
        RellLexer.RULE_BYTES -> "byte array"
        else -> RellParser.VOCABULARY.getLiteralName(type)
            ?: "'${RellParser.VOCABULARY.getDisplayName(type)}'"
    }

    private fun escapeTokenText(text: String): String {
        val escaped = text.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
        return if (escaped.length <= MAX_TOKEN_TEXT) escaped else escaped.take(MAX_TOKEN_TEXT) + "..."
    }

    private fun capitalize(msg: String): String = msg.replaceFirstChar { it.uppercaseChar() }

    private companion object {
        private const val MAX_EXPECTED_TOKENS = 8
        private const val MAX_TOKEN_TEXT = 20
    }
}
