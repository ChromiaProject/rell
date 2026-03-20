package net.postchain.rell.toolbox.formatter.util

import net.postchain.rell.toolbox.formatter.BracePairTypes
import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.parser.RellParser.RuleX_AnnotatedDefContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_BaseExprHeadContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_BaseExprTailAtContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_BaseExprTailCallContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_BaseExprTailContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_BaseExprTailMemberContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_BinaryExprOperandContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_CallArgsContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_UnaryExprContext
import org.antlr.v4.runtime.ParserRuleContext

class ExpressionFormatter(
    private val tokenAnalyzer: TokenAnalyzer,
    private val lineAnalyzer: LineAnalyzer,
    private val braceFormatter: BraceFormatter,
    private val whitespaceFormatter: WhitespaceFormatter,
    private val argumentFormatter: ArgumentFormatter
) {

    fun formatExprTailMultiline(
        currentExpr: ParserRuleContext?,
        previousExpr: ParserRuleContext?,
        doc: FormattableDocument
    ) {
        when (currentExpr) {
            is RuleX_BaseExprTailMemberContext -> {
                doc.prepend(currentExpr.ruleX_Name()) { p -> p.noSpace() }
                doc.append(currentExpr) { p -> p.noSpace() }
                doc.prepend(currentExpr) { p -> p.newLine() }
            }

            is RuleX_BaseExprTailContext -> {
                if (currentExpr.ruleX_BaseExprTailCall() != null) {
                    formatExprTailCall(currentExpr, previousExpr, doc, false)
                } else {
                    if (currentExpr.ruleX_BaseExprTailMember() != null) {
                        formatExprTailMultiline(currentExpr.ruleX_BaseExprTailMember(), currentExpr, doc)
                    } else {
                        doc.format(currentExpr)
                    }
                }
            }

            else -> {
                doc.format(currentExpr)
            }
        }
    }

    fun formatExprTailCall(
        currentExpr: RuleX_BaseExprTailContext,
        previousExpr: ParserRuleContext?,
        doc: FormattableDocument,
        indent: Boolean = true
    ) {
        if (previousExpr is RuleX_BaseExprTailContext) {
            doc.interiorIndent(currentExpr)
        }
        val callArgs = currentExpr.ruleX_BaseExprTailCall().ruleX_CallArgs()
        braceFormatter.formatBracePairWithoutSpace(callArgs, doc, BracePairTypes.PARENTHESES)
        val (callArg, trailingComma) = callArgs.getCallArgWithTrailingComma()
        val lineSeparate = formatCallArgsAsMultiLine(callArgs)
        whitespaceFormatter.formatTrailingComma(trailingComma, doc, lineSeparate)
        argumentFormatter.formatArguments(callArg, doc, formatAsMultiLine = lineSeparate, indent = indent)
        doc.format(callArgs)
    }

    fun formatExprTailSingleline(xBaseExprTail: ParserRuleContext, doc: FormattableDocument) {
        if (xBaseExprTail is RuleX_BaseExprTailMemberContext) {
            doc.prepend(xBaseExprTail) { p -> p.noSpace() }
            doc.append(xBaseExprTail) { p -> p.noSpace() }
        } else if (xBaseExprTail is RuleX_BaseExprTailCallContext) {
            doc.append(xBaseExprTail) { p -> p.noSpace() }
            val callArgs = xBaseExprTail.ruleX_CallArgs()
            braceFormatter.formatBracePairWithoutSpace(callArgs, doc, BracePairTypes.PARENTHESES)
            val (callArg, trailingComma) = callArgs.getCallArgWithTrailingComma()
            val lineSeparate = lineAnalyzer.formatAsMultiLine(callArg)
            whitespaceFormatter.formatTrailingComma(trailingComma, doc, lineSeparate)
            argumentFormatter.formatArguments(callArg, doc)
            doc.format(xBaseExprTail.ruleX_CallArgs())
        } else if (xBaseExprTail is RuleX_BaseExprTailContext) {
            if (xBaseExprTail.ruleX_BaseExprTailCall() != null) {
                formatExprTailSingleline(xBaseExprTail.ruleX_BaseExprTailCall(), doc)
            } else if (xBaseExprTail.ruleX_BaseExprTailMember() != null) {
                formatExprTailSingleline(xBaseExprTail.ruleX_BaseExprTailMember(), doc)
            } else {
                doc.format(xBaseExprTail)
            }
        } else {
            doc.format(xBaseExprTail)
        }
    }

    fun formatMultiLineStmts(
        xUnaryExpr: RuleX_UnaryExprContext,
        stmts: List<RuleX_BinaryExprOperandContext>,
        doc: FormattableDocument
    ) {
        doc.prepend(xUnaryExpr) { p -> p.newLine() }
        doc.prepend(xUnaryExpr) { p -> p.indent() }
        doc.interiorIndent(xUnaryExpr)
        stmts.forEachIndexed { index, expr ->
            doc.surround(expr.ruleX_BinaryOperator()) { p -> p.oneSpace() }
            val isLogicalOperator = expr.ruleX_BinaryOperator().ruleX_BinaryOperator_14() != null ||
                expr.ruleX_BinaryOperator().ruleX_BinaryOperator_13() != null
            if (isLogicalOperator) {
                doc.surround(expr.ruleX_BinaryOperator()) { p ->
                    p.newLine()
                    p.indent()
                }
            }
            if (index == stmts.lastIndex) doc.append(expr.ruleX_UnaryExpr()) { p -> p.newLine() }
        }
    }

    // Handle internal block indents for expression tail
    fun indentExpressionTail(
        exprHead: RuleX_BaseExprHeadContext,
        exprTailList: List<RuleX_BaseExprTailContext>,
        doc: FormattableDocument
    ) {
        val shouldLineSeparateTail = lineAnalyzer.lineSeparateExpr(exprHead, exprTailList.last())
        if (shouldLineSeparateTail && exprTailList.first().ruleX_BaseExprTailAt() == null) {
            if (tailOnlyConsistOfOneTailCall(exprTailList)) {
                doc.interiorIndentRange(exprHead, exprTailList.last())
            } else if (tailHasMemberAndEndsWithTailCall(exprTailList)) {
                if (exprHead.stop.line != exprTailList.last().start.line ||
                    lineAnalyzer.exceedsMaxLineWidth(exprTailList.last())
                ) {
                    doc.interiorIndentRangeIncludeLast(exprHead, exprTailList.last())
                }
            } else if (tailEndsWithAtExpression(exprTailList)) {
                indentTailAtExpression(exprTailList.last().ruleX_BaseExprTailAt(), doc)
                if (shouldIndentBeforeAt(exprTailList, exprHead) ||
                    lineAnalyzer.exceedsMaxLineWidth(exprTailList.last())
                ) {
                    doc.prepend(exprTailList[exprTailList.lastIndex - 1]) { it.indent() }
                }
            } else {
                doc.interiorIndentRangeIncludeLast(exprHead, exprTailList.last())
            }
        }
    }

    private fun tailOnlyConsistOfOneTailCall(exprTailList: List<RuleX_BaseExprTailContext>): Boolean {
        return exprTailList.size == 1 && exprTailList.first().ruleX_BaseExprTailCall() != null
    }

    private fun tailHasMemberAndEndsWithTailCall(exprTailList: List<RuleX_BaseExprTailContext>): Boolean {
        return exprTailList.size == 2 && exprTailList.last().ruleX_BaseExprTailCall() != null
    }

    private fun tailEndsWithAtExpression(exprTailList: List<RuleX_BaseExprTailContext>): Boolean {
        return exprTailList.last().ruleX_BaseExprTailAt() != null
    }

    private fun shouldIndentBeforeAt(
        exprTailList: List<RuleX_BaseExprTailContext>,
        exprHead: RuleX_BaseExprHeadContext
    ): Boolean {
        return when (exprTailList.size) {
            1 -> false
            2 -> exprHead.stop.line != exprTailList.first().start.line
            else -> {
                val lastIndex = exprTailList.lastIndex
                exprTailList[lastIndex - 2].stop.line != exprTailList[lastIndex - 1].start.line
            }
        }
    }

    private fun indentTailAtExpression(tailAt: RuleX_BaseExprTailAtContext, doc: FormattableDocument) {
        val whereExpr = tailAt.ruleX_AtExprWhere()
        val (expressionRef, trailingComma) = whereExpr.getExpressionRefWithTrailingComma()
        whitespaceFormatter.formatTrailingComma(trailingComma, doc)
        if (whereExpr != null && lineAnalyzer.formatAsMultiLine(expressionRef)) {
            doc.interiorIndentRangeIncludeLast(whereExpr, whereExpr)
        }

        val whatExpr = tailAt.ruleX_AtExprWhat()
        if (whatExpr != null) {
            if (whatExpr.start.line != whatExpr.stop.line || lineAnalyzer.exceedsMaxLineWidth(whatExpr)) {
                doc.interiorIndentRangeIncludeLast(whatExpr, whatExpr)
            }
        }
    }

    fun formatOpeningClosingLines(
        definitions: List<RuleX_AnnotatedDefContext>,
        doc: FormattableDocument
    ) {
        if (definitions.isNotEmpty()) {
            val firstDef = definitions[0]

            val fileStartsWithComment = tokenAnalyzer.previousHiddenRegionList(firstDef.start).any {
                it.type == net.postchain.rell.toolbox.parser.RellLexer.RULE_ML_COMMENT ||
                    it.type == net.postchain.rell.toolbox.parser.RellLexer.RULE_SL_COMMENT
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

    private fun formatCallArgsAsMultiLine(callArgs: RuleX_CallArgsContext): Boolean {
        val (callArg, _) = callArgs.getCallArgWithTrailingComma()
        return lineAnalyzer.formatAsMultiLine(callArg) || callArgs.start.line != callArgs.stop.line
    }
}
