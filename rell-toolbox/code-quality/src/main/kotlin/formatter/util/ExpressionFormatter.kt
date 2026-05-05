/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.util

import net.postchain.rell.base.compiler.parser.antlr.RellParser.*
import net.postchain.rell.toolbox.formatter.BracePairTypes
import net.postchain.rell.toolbox.formatter.FormattableDocument
import org.antlr.v4.runtime.ParserRuleContext

/**
 * Logical "tail" entries of a [BaseExprContext]. The grammar has the head followed by
 * zero or more of:
 *   - a single [BaseExprTailNoCallNoAtContext]
 *   - a single [CallArgsContext]
 *   - a 4-rule run: [AtExprAtContext] [AtExprWhereContext] [AtExprWhatContext]? [AtExprModifiersContext]?
 *
 * Each [BaseExprTail] groups one of those.
 */
internal sealed class BaseExprTail {
    abstract val first: ParserRuleContext
    abstract val last: ParserRuleContext

    class NoCallNoAt(val ctx: BaseExprTailNoCallNoAtContext) : BaseExprTail() {
        override val first: ParserRuleContext get() = ctx
        override val last: ParserRuleContext get() = ctx
    }

    class Call(val ctx: CallArgsContext) : BaseExprTail() {
        override val first: ParserRuleContext get() = ctx
        override val last: ParserRuleContext get() = ctx
    }

    class AtExpr(
        val at: AtExprAtContext,
        val where: AtExprWhereContext,
        val what: AtExprWhatContext?,
        val modifiers: AtExprModifiersContext?,
    ) : BaseExprTail() {
        override val first: ParserRuleContext get() = at
        override val last: ParserRuleContext get() = modifiers ?: what ?: where
    }
}

/**
 * Anchor for a labeled list item: either a leading `RULE_ID` terminal or the item's
 * expression context.
 */
internal sealed class LabeledAnchor {
    class Term(val node: org.antlr.v4.runtime.tree.TerminalNode) : LabeledAnchor()
    class Rule(val ctx: ParserRuleContext) : LabeledAnchor()

    fun applyPrepend(
        doc: net.postchain.rell.toolbox.formatter.FormattableDocument,
        mod: (net.postchain.rell.toolbox.formatter.Changes) -> Unit,
    ) {
        when (this) {
            is Term -> doc.prepend(node, mod)
            is Rule -> doc.prepend(ctx, mod)
        }
    }
}

internal fun BaseExprContext.tails(): List<BaseExprTail> {
    val result = mutableListOf<BaseExprTail>()
    var i = 1 // children[0] is BaseExprHeadContext
    val n = childCount
    while (i < n) {
        when (val c = getChild(i)) {
            is BaseExprTailNoCallNoAtContext -> {
                result.add(BaseExprTail.NoCallNoAt(c))
                i++
            }
            is CallArgsContext -> {
                result.add(BaseExprTail.Call(c))
                i++
            }
            is AtExprAtContext -> {
                val at = c
                val where = getChild(i + 1) as AtExprWhereContext
                var j = i + 2
                var what: AtExprWhatContext? = null
                if (j < n && getChild(j) is AtExprWhatContext) {
                    what = getChild(j) as AtExprWhatContext
                    j++
                }
                var mods: AtExprModifiersContext? = null
                if (j < n && getChild(j) is AtExprModifiersContext) {
                    mods = getChild(j) as AtExprModifiersContext
                    j++
                }
                result.add(BaseExprTail.AtExpr(at, where, what, mods))
                i = j
            }
            else -> i++
        }
    }
    return result
}

class ExpressionFormatter(
    private val tokenAnalyzer: TokenAnalyzer,
    private val lineAnalyzer: LineAnalyzer,
    private val braceFormatter: BraceFormatter,
    private val whitespaceFormatter: WhitespaceFormatter,
    private val argumentFormatter: ArgumentFormatter
) {

    internal fun formatExprTailMultiline(
        currentTail: BaseExprTail,
        previousNode: ParserRuleContext?,
        doc: FormattableDocument
    ) {
        when (currentTail) {
            is BaseExprTail.NoCallNoAt -> {
                if (currentTail.ctx is BaseExprTailMemberContext) {
                    val ruleId = currentTail.ctx.RULE_ID()
                    doc.prepend(ruleId) { p -> p.noSpace() }
                    doc.append(currentTail.ctx) { p -> p.noSpace() }
                    doc.prepend(currentTail.ctx) { p -> p.newLine() }
                } else {
                    doc.format(currentTail.ctx)
                }
            }
            is BaseExprTail.Call -> {
                formatExprTailCall(currentTail, previousNode, doc, false)
            }
            is BaseExprTail.AtExpr -> {
                doc.format(currentTail.at)
                doc.format(currentTail.where)
                doc.format(currentTail.what)
                doc.format(currentTail.modifiers)
            }
        }
    }

    internal fun formatExprTailCall(
        callTail: BaseExprTail.Call,
        previousNode: ParserRuleContext?,
        doc: FormattableDocument,
        indent: Boolean = true
    ) {
        if (previousNode is CallArgsContext || previousNode is BaseExprTailNoCallNoAtContext) {
            doc.interiorIndent(callTail.ctx)
        }
        val callArgs = callTail.ctx
        braceFormatter.formatBracePairWithoutSpace(callArgs, doc, BracePairTypes.PARENTHESES)
        val (args, trailingComma) = callArgs.getCallArgsItems()
        val lineSeparate = formatCallArgsAsMultiLine(callArgs)
        whitespaceFormatter.formatTrailingComma(trailingComma, doc, lineSeparate)
        formatLabeledParenList(callArgs, args, doc, multiLine = lineSeparate, indent = indent)
        // Surround item-internal '=' too (already covered by formatLabeledParenList).
        doc.format(callArgs)
    }

    internal fun formatExprTailSingleline(tail: BaseExprTail, doc: FormattableDocument) {
        when (tail) {
            is BaseExprTail.NoCallNoAt -> {
                doc.prepend(tail.ctx) { p -> p.noSpace() }
                doc.append(tail.ctx) { p -> p.noSpace() }
                doc.format(tail.ctx)
            }
            is BaseExprTail.Call -> {
                doc.prepend(tail.ctx) { p -> p.noSpace() }
                val callArgs = tail.ctx
                braceFormatter.formatBracePairWithoutSpace(callArgs, doc, BracePairTypes.PARENTHESES)
                val (args, trailingComma) = callArgs.getCallArgsItems()
                val lineSeparate = lineAnalyzer.formatAsMultiLine(args)
                whitespaceFormatter.formatTrailingComma(trailingComma, doc, lineSeparate)
                formatLabeledParenList(callArgs, args, doc, multiLine = false)
                doc.format(callArgs)
            }
            is BaseExprTail.AtExpr -> {
                doc.format(tail.at)
                doc.format(tail.where)
                doc.format(tail.what)
                doc.format(tail.modifiers)
            }
        }
    }

    /**
     * Format a labeled paren-list like `(name = expr, name = expr, ...)`. Each item's anchor
     * is the leading `RULE_ID` (when present) or the expression (when not). Layouts:
     *
     *  - [multiLine] = true: each anchor preceded by a newline + indent; last expression
     *    appended with a newline (closes the list).
     *  - [multiLine] = false: each anchor preceded by one space; last expression appended
     *    with no space (closes the list).
     *
     * The opening/closing parens and trailing comma are handled separately by the caller.
     */
    fun formatLabeledParenList(
        parent: ParserRuleContext,
        items: List<ParserRuleContext>,
        doc: FormattableDocument,
        multiLine: Boolean,
        indent: Boolean = true,
    ) {
        if (items.isEmpty()) return
        val anchors = collectLabeledItemAnchors(parent, items)
        anchors.forEachIndexed { idx, anchor ->
            if (multiLine) {
                anchor.applyPrepend(doc) {
                    it.newLine()
                    if (indent) it.indent()
                }
                if (idx == items.lastIndex) {
                    doc.append(items.last()) { it.newLine() }
                }
            } else {
                anchor.applyPrepend(doc) { it.oneSpace() }
                if (idx == items.lastIndex) {
                    doc.append(items.last()) { it.noSpace() }
                }
            }
        }
        formatLabelEquals(parent, doc)
    }

    /**
     * For each item (an expression context), find its anchor: the closest preceding RULE_ID
     * terminal that is itself preceded by an item-boundary (`(`, `,`) — or fall back to the
     * expression itself if no label exists.
     */
    private fun collectLabeledItemAnchors(
        parent: ParserRuleContext,
        items: List<ParserRuleContext>,
    ): List<LabeledAnchor> {
        val anchors = mutableListOf<LabeledAnchor>()
        for (item in items) {
            val itemStartIdx = item.start.tokenIndex
            // Walk children of parent looking for the closest preceding RULE_ID with an
            // item-boundary terminal immediately before it.
            var foundLabel: org.antlr.v4.runtime.tree.TerminalNode? = null
            for (i in 0 until parent.childCount) {
                val c = parent.getChild(i)
                if (c is org.antlr.v4.runtime.tree.TerminalNode &&
                    c.symbol.type == net.postchain.rell.base.compiler.parser.antlr.RellParser.RULE_ID &&
                    c.symbol.tokenIndex < itemStartIdx
                ) {
                    // Check that the token before this RULE_ID is `(` or `,`.
                    val tokenStream = c.symbol.tokenSource as? org.antlr.v4.runtime.Lexer
                    @Suppress("UNUSED_VARIABLE") val _unused = tokenStream
                    if (i == 0) continue
                    val prev = parent.getChild(i - 1)
                    if (prev is org.antlr.v4.runtime.tree.TerminalNode &&
                        (prev.symbol.text == "(" || prev.symbol.text == ",")
                    ) {
                        foundLabel = c
                        // Don't break — we want the latest one before itemStartIdx.
                    }
                }
            }
            anchors.add(
                if (foundLabel != null) LabeledAnchor.Term(foundLabel)
                else LabeledAnchor.Rule(item)
            )
        }
        return anchors
    }

    /**
     * Surround every top-level `=` terminal of [parent] with a single space and ensure no
     * line-break around it. Used by callers that hold paren-lists with optional `RULE_ID '='`
     * labels (call args, create-expr args, update items, tuple heads).
     */
    fun formatLabelEquals(parent: ParserRuleContext, doc: FormattableDocument) {
        for (i in 0 until parent.childCount) {
            val c = parent.getChild(i)
            if (c is org.antlr.v4.runtime.tree.TerminalNode && c.symbol.text == "=") {
                doc.prepend(c) {
                    it.oneSpace()
                    it.setNewLines(0)
                    it.highPriority()
                }
                doc.append(c) {
                    it.oneSpace()
                    it.setNewLines(0)
                    it.highPriority()
                }
            }
        }
    }

    /**
     * Format a binary expression as multi-line. Walks children of the [ExpressionContext]
     * directly: prefix-operator/operand sequences are separated by inline binary-operator
     * terminals. The grammar emits `('+'|'-'|'not'|'++'|'--')*` prefix tokens, then a base
     * operand (`ifExpr`/`whenExpr`/`baseExpr`), then `(binOp prefix* operand)*`.
     */
    fun formatMultiLineStmts(expr: ExpressionContext, doc: FormattableDocument) {
        // Identify the operands and the binary-operator tokens between them.
        val (operands, binOps) = splitExpression(expr)
        if (operands.isEmpty() || binOps.isEmpty()) {
            // Nothing to multiline-format: single operand expression.
            return
        }

        // First operand gets indent + new-line in front
        val first = operands.first()
        doc.prepend(first) { it.newLine() }
        doc.prepend(first) { it.indent() }
        doc.interiorIndent(expr)

        binOps.forEachIndexed { index, opToken ->
            // Always one space around the operator.
            doc.prepend(opToken) { it.oneSpace() }
            doc.append(opToken) { it.oneSpace() }

            val opText = opToken.symbol.text
            if (opText == "and" || opText == "or") {
                // Logical operators: line-break + indent before the operator.
                doc.prepend(opToken) {
                    it.newLine()
                    it.indent()
                }
            }

            // The operand AFTER this operator
            val rhs = operands.getOrNull(index + 1)
            if (rhs != null && index == binOps.lastIndex) {
                doc.append(rhs) { it.newLine() }
            }
        }
    }

    /**
     * Split [ExpressionContext] children into operands (rule contexts: ifExpr/whenExpr/baseExpr)
     * and the binary-operator terminal tokens between them. Prefix tokens (`+`, `-`, `not`,
     * `++`, `--`) before each operand are skipped.
     */
    private fun splitExpression(
        expr: ExpressionContext
    ): Pair<List<ParserRuleContext>, List<org.antlr.v4.runtime.tree.TerminalNode>> {
        val operands = mutableListOf<ParserRuleContext>()
        val binOps = mutableListOf<org.antlr.v4.runtime.tree.TerminalNode>()
        var i = 0
        val n = expr.childCount
        var afterOperand = false
        while (i < n) {
            val c = expr.getChild(i)
            when {
                c is ParserRuleContext -> {
                    operands.add(c)
                    afterOperand = true
                }
                c is org.antlr.v4.runtime.tree.TerminalNode && afterOperand -> {
                    // After an operand we may see a binary operator; the very first terminal
                    // after an operand begins the operator. Some ops are two tokens (`not in`).
                    binOps.add(c)
                    afterOperand = false
                    // skip a possible "in" if previous was "not"
                    if (c.symbol.text == "not" && i + 1 < n) {
                        val nxt = expr.getChild(i + 1)
                        if (nxt is org.antlr.v4.runtime.tree.TerminalNode && nxt.symbol.text == "in") {
                            i++
                        }
                    }
                }
                // prefix operator tokens before an operand: ignored.
            }
            i++
        }
        return Pair(operands, binOps)
    }

    /**
     * True when an [ExpressionContext] has more than one operand (i.e. it has binary operators).
     */
    fun hasBinaryOperands(expr: ExpressionContext): Boolean {
        return splitExpression(expr).first.size > 1
    }

    fun expressionOperands(expr: ExpressionContext): List<ParserRuleContext> {
        return splitExpression(expr).first
    }

    // Handle internal block indents for expression tail
    internal fun indentExpressionTail(
        exprHead: BaseExprHeadContext,
        tails: List<BaseExprTail>,
        doc: FormattableDocument
    ) {
        if (tails.isEmpty()) return
        val lastNode: ParserRuleContext = tails.last().last
        val shouldLineSeparateTail = lineAnalyzer.lineSeparateExpr(exprHead, lastNode)
        if (shouldLineSeparateTail && tails.first() !is BaseExprTail.AtExpr) {
            if (tailOnlyConsistOfOneTailCall(tails)) {
                doc.interiorIndentRange(exprHead, lastNode)
            } else if (tailHasMemberAndEndsWithTailCall(tails)) {
                if (exprHead.stop.line != tails.last().first.start.line ||
                    lineAnalyzer.exceedsMaxLineWidth(lastNode)
                ) {
                    doc.interiorIndentRangeIncludeLast(exprHead, lastNode)
                }
            } else if (tailEndsWithAtExpression(tails)) {
                indentTailAtExpression(tails.last() as BaseExprTail.AtExpr, doc)
                if (shouldIndentBeforeAt(tails, exprHead) ||
                    lineAnalyzer.exceedsMaxLineWidth(lastNode)
                ) {
                    val before = tails[tails.lastIndex - 1].first
                    doc.prepend(before) { it.indent() }
                }
            } else {
                doc.interiorIndentRangeIncludeLast(exprHead, lastNode)
            }
        }
    }

    private fun tailOnlyConsistOfOneTailCall(tails: List<BaseExprTail>): Boolean {
        return tails.size == 1 && tails.first() is BaseExprTail.Call
    }

    private fun tailHasMemberAndEndsWithTailCall(tails: List<BaseExprTail>): Boolean {
        return tails.size == 2 && tails.last() is BaseExprTail.Call
    }

    private fun tailEndsWithAtExpression(tails: List<BaseExprTail>): Boolean {
        return tails.last() is BaseExprTail.AtExpr
    }

    private fun shouldIndentBeforeAt(
        tails: List<BaseExprTail>,
        exprHead: BaseExprHeadContext
    ): Boolean {
        return when (tails.size) {
            1 -> false
            2 -> exprHead.stop.line != tails.first().first.start.line
            else -> {
                val lastIndex = tails.lastIndex
                tails[lastIndex - 2].last.stop.line != tails[lastIndex - 1].first.start.line
            }
        }
    }

    private fun indentTailAtExpression(tailAt: BaseExprTail.AtExpr, doc: FormattableDocument) {
        val whereExpr = tailAt.where
        val (expressionRef, trailingComma) = whereExpr.getWhereItems()
        whitespaceFormatter.formatTrailingComma(trailingComma, doc)
        if (lineAnalyzer.formatAsMultiLine(expressionRef)) {
            doc.interiorIndentRangeIncludeLast(whereExpr, whereExpr)
        }

        val whatExpr = tailAt.what
        if (whatExpr != null) {
            if (whatExpr.start.line != whatExpr.stop.line || lineAnalyzer.exceedsMaxLineWidth(whatExpr)) {
                doc.interiorIndentRangeIncludeLast(whatExpr, whatExpr)
            }
        }
    }

    fun formatOpeningClosingLines(
        definitions: List<AnnotatedDefContext>,
        doc: FormattableDocument
    ) {
        if (definitions.isNotEmpty()) {
            val firstDef = definitions[0]

            val fileStartsWithComment = tokenAnalyzer.previousHiddenRegionList(firstDef.start).any {
                it.type == net.postchain.rell.base.compiler.parser.antlr.RellLexer.RULE_ML_COMMENT ||
                    it.type == net.postchain.rell.base.compiler.parser.antlr.RellLexer.RULE_SL_COMMENT
            }
            val newLines = if (fileStartsWithComment) 1 else 0

            doc.prepend(firstDef) {
                it.setNewLines(newLines)
                it.noSpace()
                it.highPriority()
            }

            val lastDef = definitions[definitions.size - 1]
            doc.append(lastDef) {
                it.setNewLines(1)
                it.noSpace()
                it.highPriority()
            }
        }
    }

    fun prependNodeList(
        firstNode: ParserRuleContext,
        nodeList: List<ParserRuleContext>?
    ): List<ParserRuleContext> {
        val expressions = mutableListOf<ParserRuleContext>()
        expressions.add(firstNode)
        if (nodeList != null) {
            expressions.addAll(nodeList)
        }
        return expressions
    }

    private fun formatCallArgsAsMultiLine(callArgs: CallArgsContext): Boolean {
        val (args, _) = callArgs.getCallArgsItems()
        return lineAnalyzer.formatAsMultiLine(args) || callArgs.start.line != callArgs.stop.line
    }
}
