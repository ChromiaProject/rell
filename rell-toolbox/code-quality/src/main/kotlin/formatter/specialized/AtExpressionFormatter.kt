/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.toolbox.formatter.BracePairTypes
import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.*
import net.postchain.rell.toolbox.parser.RellParser.*

class AtExprModFormatter0(val tokenAnalyzer: TokenAnalyzer) : NodeFormatter<RuleX_AtExprModifiers_0Context> {
    override fun format(node: RuleX_AtExprModifiers_0Context, doc: FormattableDocument) {
        val offsetExpr = node.ruleX_AtExprOffset()
        val limit = tokenAnalyzer.tokenFor(node, "limit")
        val offset = tokenAnalyzer.tokenFor(offsetExpr, "offset")
        if (limit != null) {
            doc.surround(limit) { it.oneSpace() }
        }
        if (offset != null) {
            doc.surround(offset) { it.oneSpace() }
        }
        doc.format(node.ruleX_ExpressionRef())
        doc.format(node.ruleX_AtExprOffset())
    }
}

class AtExprModFormatter1(val tokenAnalyzer: TokenAnalyzer) : NodeFormatter<RuleX_AtExprModifiers_1Context> {
    override fun format(node: RuleX_AtExprModifiers_1Context, doc: FormattableDocument) {
        val limitExpr = node.ruleX_AtExprLimit()
        val offset = tokenAnalyzer.tokenFor(node, "offset")
        val limit = tokenAnalyzer.tokenFor(limitExpr, "limit")
        if (limit != null) doc.surround(limit) { it.oneSpace() }
        if (offset != null) doc.surround(offset) { it.oneSpace() }
        doc.format(node.ruleX_ExpressionRef())
        doc.format(node.ruleX_AtExprLimit())
    }
}

class AtExprFromFormatter(
    val lineAnalyzer: LineAnalyzer,
    val braceFormatter: BraceFormatter,
    val whitespaceFormatter: WhitespaceFormatter,
    val argumentFormatter: ArgumentFormatter,
) : NodeFormatter<RuleX_AtExprFromContext> {
    override fun format(node: RuleX_AtExprFromContext, doc: FormattableDocument) {
        val (atExprFromItem, trailingComma) = node.getAtExprFromItemWithTrailingComma()
        val lineSeparate = lineAnalyzer.lineSeparateArguments(node, BracePairTypes.PARENTHESES)
        if (!lineSeparate || atExprFromItem.isNullOrEmpty()) {
            braceFormatter.formatBracePairWithoutSpace(node, doc, BracePairTypes.PARENTHESES)
        }
        whitespaceFormatter.formatTrailingComma(trailingComma, doc, lineSeparate)
        argumentFormatter.formatArguments(atExprFromItem, doc, formatAsMultiLine = lineSeparate)
        atExprFromItem?.forEach { doc.format(it) }
    }
}

class AtExprFromItemFormatter(val tokenAnalyzer: TokenAnalyzer) : NodeFormatter<RuleX_AtExprFromItemContext> {
    override fun format(node: RuleX_AtExprFromItemContext, doc: FormattableDocument) {
        doc.append(tokenAnalyzer.tokenFor(node, ":")) { it.oneSpace() }
        node.ruleX_Annotation().forEach { xAnnotation ->
            doc.append(xAnnotation) { it.oneSpace() }
        }
        doc.interiorIndentRangeIncludeLast(node, node.ruleX_ExpressionRef())
        doc.format(node.ruleX_ExpressionRef())
    }
}

class AtExprAtFormatter : NodeFormatter<RuleX_AtExprAtContext> {
    override fun format(node: RuleX_AtExprAtContext, doc: FormattableDocument) {
        doc.surround(node) {
            it.oneSpace()
            it.highPriority()
        }
    }
}

class AtExprWhereFormatter(
    val lineAnalyzer: LineAnalyzer,
    val braceFormatter: BraceFormatter,
    val whitespaceFormatter: WhitespaceFormatter,
    val argumentFormatter: ArgumentFormatter,
) : NodeFormatter<RuleX_AtExprWhereContext> {
    override fun format(node: RuleX_AtExprWhereContext, doc: FormattableDocument) {
        val (expressionRef, trailingComma) = node.getExpressionRefWithTrailingComma()
        val formatAsMultiLine = lineAnalyzer.lineSeparateArguments(node, BracePairTypes.CURLY)

        if (!formatAsMultiLine || expressionRef.isNullOrEmpty()) {
            braceFormatter.formatBracePairWithSpace(node, doc, BracePairTypes.CURLY)
        }
        whitespaceFormatter.formatTrailingComma(trailingComma, doc, formatAsMultiLine)
        argumentFormatter.formatArguments(expressionRef, doc, formatAsMultiLine = formatAsMultiLine)
    }
}

class AtExprWhatCmplxFormatter(
    val lineAnalyzer: LineAnalyzer,
    val braceFormatter: BraceFormatter,
    val whitespaceFormatter: WhitespaceFormatter,
    val argumentFormatter: ArgumentFormatter,
    val expressionFormatter: ExpressionFormatter,
) : NodeFormatter<RuleX_AtExprWhatComplexContext> {
    override fun format(node: RuleX_AtExprWhatComplexContext, doc: FormattableDocument) {
        doc.prepend(node) { it.oneSpace() }

        val formatAsMultiLine = lineAnalyzer.lineSeparateArguments(node, BracePairTypes.PARENTHESES)
        val (items, trailingComma) = node.getAtExprWhatComplexItemWithTrailingComma()

        if (!formatAsMultiLine) {
            braceFormatter.formatBracePairWithSpace(node, doc, BracePairTypes.PARENTHESES)
        }
        whitespaceFormatter.formatTrailingComma(trailingComma, doc, formatAsMultiLine)
        argumentFormatter.formatArguments(items, doc, formatAsMultiLine = formatAsMultiLine)

        items?.forEach { item ->
            val baseExpr = item.getBaseExpr()
            if (baseExpr != null && baseExpr.ruleX_BaseExprTail().isNotEmpty()) {
                val tailCall = baseExpr.ruleX_BaseExprTail().first().ruleX_BaseExprTailCall()
                if (tailCall != null) {
                    doc.prepend(tailCall) { it.oneSpace() }
                    doc.interiorIndentRangeIncludeLast(baseExpr, tailCall)
                    expressionFormatter.formatExprTailCall(
                        baseExpr.ruleX_BaseExprTail().first(),
                        null,
                        doc
                    )
                }
            }
            doc.format(item)
        }
    }
}

class AtExprWhatCmplxItemFormatter : NodeFormatter<RuleX_AtExprWhatComplexItemContext> {
    override fun format(node: RuleX_AtExprWhatComplexItemContext, doc: FormattableDocument) {
        val itemAnnotations = node.ruleX_AtExprWhatModifiers().ruleX_Annotation()
        itemAnnotations.forEach { xAnnotation ->
            doc.append(xAnnotation) { it.oneSpace() }
            doc.format(xAnnotation.ruleX_AnnotationArgs())
        }
    }
}
