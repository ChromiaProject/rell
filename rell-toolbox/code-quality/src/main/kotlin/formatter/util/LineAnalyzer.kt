/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.util

import net.postchain.rell.toolbox.formatter.BracePairTypes
import net.postchain.rell.toolbox.formatter.FormatterOptions
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token

internal class LineAnalyzer(
    private val source: String,
    private val formatterOptions: FormatterOptions,
    private val braceFormatter: BraceFormatter
) {

    fun lineSeparateExpr(currentExpr: ParserRuleContext, previousExp: ParserRuleContext): Boolean {
        val previousExprLineNr = previousExp.stop.line
        val currentExprLineNr = currentExpr.stop.line
        val isLineSeparated = previousExprLineNr != currentExprLineNr

        return getLineLength(currentExpr) > formatterOptions.maxLineWidth || isLineSeparated
    }

    fun lineSeparateExpr(currentExpr: Token?, previousExp: Token?): Boolean {
        if (currentExpr == null && previousExp == null) {
            return false
        }
        val previousExprLineNr = previousExp!!.line
        val currentExprLineNr = currentExpr!!.line
        val isLineSeperated = previousExprLineNr != currentExprLineNr
        return getLineLength(currentExpr) > formatterOptions.maxLineWidth || isLineSeperated
    }

    fun lineSeparateArguments(methodDef: ParserRuleContext, pair: BracePairTypes): Boolean {
        val (bracketOpening, bracketClosing) = braceFormatter.bracePairFor(methodDef, pair)
        var isLineSeperated = false
        if (bracketOpening != null && bracketClosing != null) {
            val openingLine = bracketOpening.symbol.line
            val closingLine = bracketClosing.symbol.line
            isLineSeperated = openingLine != closingLine
        }
        return getLineLength(methodDef) > formatterOptions.maxLineWidth || isLineSeperated
    }

    fun exceedsMaxLineWidth(node: ParserRuleContext): Boolean {
        return getLineLength(node) > formatterOptions.maxLineWidth
    }

    private fun getLineLength(node: ParserRuleContext): Int {
        val lineStartOffset = node.start.startIndex - node.start.charPositionInLine
        val lineEndOffset = source.indexOf('\n', node.start.startIndex)
        return if (lineEndOffset != -1) lineEndOffset - lineStartOffset else node.text.length
    }

    private fun getLineLength(node: Token): Int {
        val lineStartOffset = node.startIndex - node.charPositionInLine
        val lineEndOffset = source.indexOf('\n', node.startIndex)
        return if (lineEndOffset != -1) lineEndOffset - lineStartOffset else node.text.length
    }

    /**
     * Width the chain segment [from]..[to] would occupy once moved to its own continuation
     * line: the current line's indentation plus one extra level, plus the segment text. Lets
     * a call keep its arguments inline when breaking the chain before [from] already makes
     * the line fit.
     */
    fun fitsWhenMovedToContinuationLine(from: ParserRuleContext, to: ParserRuleContext): Boolean {
        val start = from.start
        val lineStartOffset = start.startIndex - start.charPositionInLine
        var indent = 0
        var i = lineStartOffset
        while (i < source.length && (source[i] == ' ' || source[i] == '\t')) {
            indent += if (source[i] == '\t') formatterOptions.tabSize else 1
            i++
        }
        val segmentWidth = to.stop.stopIndex + 1 - start.startIndex
        return indent + formatterOptions.tabSize + segmentWidth <= formatterOptions.maxLineWidth
    }

    fun formatAsMultiLine(args: List<ParserRuleContext>?): Boolean {
        var lineLength = 0
        if (args == null) {
            return false
        }
        if (args.isNotEmpty()) {
            lineLength = getLineLength(args[0])
        }
        return formatAsMultiLine(args, lineLength)
    }

    fun formatAsMultiLine(args: List<ParserRuleContext>, length: Int): Boolean {
        var isMultiLine = false
        val nrOfArgs: Int = args.size
        if (nrOfArgs > 0) {
            val defStartLine = args[0].start.line
            val defEndLine = args[nrOfArgs - 1].stop.line
            isMultiLine = defStartLine != defEndLine
        }
        return length > formatterOptions.maxLineWidth || isMultiLine
    }
}
