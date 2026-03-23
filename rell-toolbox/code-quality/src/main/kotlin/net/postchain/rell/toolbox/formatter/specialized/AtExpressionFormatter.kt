/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.toolbox.formatter.BracePairTypes
import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.ArgumentFormatter
import net.postchain.rell.toolbox.formatter.util.BraceFormatter
import net.postchain.rell.toolbox.formatter.util.ExpressionFormatter
import net.postchain.rell.toolbox.formatter.util.LineAnalyzer
import net.postchain.rell.toolbox.formatter.util.TokenAnalyzer
import net.postchain.rell.toolbox.formatter.util.WhitespaceFormatter
import net.postchain.rell.toolbox.formatter.util.getAtExprFromItemWithTrailingComma
import net.postchain.rell.toolbox.formatter.util.getAtExprWhatComplexItemWithTrailingComma
import net.postchain.rell.toolbox.formatter.util.getBaseExpr
import net.postchain.rell.toolbox.formatter.util.getExpressionRefWithTrailingComma
import net.postchain.rell.toolbox.parser.RellParser.*

class AtExprModFormatter0(val tokenAnalyzer: TokenAnalyzer) : NodeFormatter<RuleX_AtExprModifiers_0Context> {
    override fun format(xAtExprMod: RuleX_AtExprModifiers_0Context, doc: FormattableDocument) {
        val offsetExpr = xAtExprMod.ruleX_AtExprOffset()
        val limit = tokenAnalyzer.tokenFor(xAtExprMod, "limit")
        val offset = tokenAnalyzer.tokenFor(offsetExpr, "offset")
        if (limit != null) {
            doc.surround(limit) { it.oneSpace() }
        }
        if (offset != null) {
            doc.surround(offset) { it.oneSpace() }
        }
        doc.format(xAtExprMod.ruleX_ExpressionRef())
        doc.format(xAtExprMod.ruleX_AtExprOffset())
    }
}

class AtExprModFormatter1(val tokenAnalyzer: TokenAnalyzer) : NodeFormatter<RuleX_AtExprModifiers_1Context> {
    override fun format(xAtExprMod: RuleX_AtExprModifiers_1Context, doc: FormattableDocument) {
        val limitExpr = xAtExprMod.ruleX_AtExprLimit()
        val offset = tokenAnalyzer.tokenFor(xAtExprMod, "offset")
        val limit = tokenAnalyzer.tokenFor(limitExpr, "limit")
        if (limit != null) doc.surround(limit) { it.oneSpace() }
        if (offset != null) doc.surround(offset) { it.oneSpace() }
        doc.format(xAtExprMod.ruleX_ExpressionRef())
        doc.format(xAtExprMod.ruleX_AtExprLimit())
    }
}

class AtExprFromFormatter(
    val lineAnalyzer: LineAnalyzer,
    val braceFormatter: BraceFormatter,
    val whitespaceFormatter: WhitespaceFormatter,
    val argumentFormatter: ArgumentFormatter,
) : NodeFormatter<RuleX_AtExprFromContext> {
    override fun format(xAtExprFrom: RuleX_AtExprFromContext, doc: FormattableDocument) {
        val (atExprFromItem, trailingComma) = xAtExprFrom.getAtExprFromItemWithTrailingComma()
        val lineSeparate = lineAnalyzer.lineSeparateArguments(xAtExprFrom, BracePairTypes.PARENTHESES)
        if (!lineSeparate || atExprFromItem.isNullOrEmpty()) {
            braceFormatter.formatBracePairWithoutSpace(xAtExprFrom, doc, BracePairTypes.PARENTHESES)
        }
        whitespaceFormatter.formatTrailingComma(trailingComma, doc, lineSeparate)
        argumentFormatter.formatArguments(atExprFromItem, doc, formatAsMultiLine = lineSeparate)
        atExprFromItem?.forEach { doc.format(it) }
    }
}

class AtExprFromItemFormatter(val tokenAnalyzer: TokenAnalyzer) : NodeFormatter<RuleX_AtExprFromItemContext> {
    override fun format(xAtExprFromItem: RuleX_AtExprFromItemContext, doc: FormattableDocument) {
        doc.append(tokenAnalyzer.tokenFor(xAtExprFromItem, ":")) { it.oneSpace() }
        xAtExprFromItem.ruleX_Annotation().forEach { xAnnotation ->
            doc.append(xAnnotation) { it.oneSpace() }
        }
        doc.interiorIndentRangeIncludeLast(xAtExprFromItem, xAtExprFromItem.ruleX_ExpressionRef())
        doc.format(xAtExprFromItem.ruleX_ExpressionRef())
    }
}

class AtExprAtFormatter : NodeFormatter<RuleX_AtExprAtContext> {
    override fun format(xAtExprAt: RuleX_AtExprAtContext, doc: FormattableDocument) {
        doc.surround(xAtExprAt) {
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
    override fun format(xAtExprWhere: RuleX_AtExprWhereContext, doc: FormattableDocument) {
        val (expressionRef, trailingComma) = xAtExprWhere.getExpressionRefWithTrailingComma()
        val formatAsMultiLine = lineAnalyzer.lineSeparateArguments(xAtExprWhere, BracePairTypes.CURLY)

        if (!formatAsMultiLine || expressionRef.isNullOrEmpty()) {
            braceFormatter.formatBracePairWithSpace(xAtExprWhere, doc, BracePairTypes.CURLY)
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
    override fun format(xAtExprWhatCmplx: RuleX_AtExprWhatComplexContext, doc: FormattableDocument) {
        doc.prepend(xAtExprWhatCmplx) { it.oneSpace() }

        val formatAsMultiLine = lineAnalyzer.lineSeparateArguments(xAtExprWhatCmplx, BracePairTypes.PARENTHESES)
        val (items, trailingComma) = xAtExprWhatCmplx.getAtExprWhatComplexItemWithTrailingComma()

        if (!formatAsMultiLine) {
            braceFormatter.formatBracePairWithSpace(xAtExprWhatCmplx, doc, BracePairTypes.PARENTHESES)
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
    override fun format(xAtExprWhatCmplxItem: RuleX_AtExprWhatComplexItemContext, doc: FormattableDocument) {
        val itemAnnotations = xAtExprWhatCmplxItem.ruleX_AtExprWhatModifiers().ruleX_Annotation()
        itemAnnotations.forEach { xAnnotation ->
            doc.append(xAnnotation) { it.oneSpace() }
            doc.format(xAnnotation.ruleX_AnnotationArgs())
        }
    }
}
