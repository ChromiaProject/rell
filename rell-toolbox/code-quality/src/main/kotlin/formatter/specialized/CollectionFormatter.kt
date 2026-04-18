/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.toolbox.formatter.BracePairTypes
import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.*
import net.postchain.rell.toolbox.parser.RellParser.*

class MapExprFormatter(
    val braceFormatter: BraceFormatter,
    val lineAnalyzer: LineAnalyzer,
    val whitespaceFormatter: WhitespaceFormatter,
) : NodeFormatter<RuleX_NonEmptyMapLiteralExprContext> {
    override fun format(node: RuleX_NonEmptyMapLiteralExprContext, doc: FormattableDocument) {
        val mapExprContext = node.getMapExprContext()

        braceFormatter.formatBracePairWithoutSpace(mapExprContext, doc, BracePairTypes.BRACKETS)
        val lineSeparate = lineAnalyzer.lineSeparateArguments(node, BracePairTypes.BRACKETS)
        val (mapExprEntries, trailingComma) = node.getMapExprEntryWithTrailingComma()
        whitespaceFormatter.formatTrailingComma(trailingComma, doc, lineSeparate)

        mapExprEntries?.forEachIndexed { index, mapEntry ->
            doc.prepend(mapEntry) { it.oneSpace() }
            doc.append(mapEntry) { it.noSpace() }
            doc.append(mapEntry.ruleX_ExpressionRef(0)) { it.noSpace() }
            doc.prepend(mapEntry.ruleX_ExpressionRef(1)) { it.oneSpace() }

            if (lineSeparate) {
                doc.prepend(mapEntry) {
                    it.newLine()
                    it.indent()
                }
                if (index == mapExprEntries.lastIndex) {
                    doc.append(mapEntry) {
                        it.newLine()
                    }
                }
            }
            doc.format(mapEntry)
        }
    }
}

class TupleEqExprFormatter : NodeFormatter<RuleX_TupleExprFieldContext> {
    override fun format(node: RuleX_TupleExprFieldContext, doc: FormattableDocument) {
        doc.surround(node.ruleX_NameNode()) { it.oneSpace() }
        doc.surround(node.ruleX_tkASSIGN()) { it.oneSpace() }
        doc.format(node.ruleX_ExpressionRef())
    }
}

class ListExprFormatter(
    val braceFormatter: BraceFormatter,
    val lineAnalyzer: LineAnalyzer,
    val whitespaceFormatter: WhitespaceFormatter,
    val argumentFormatter: ArgumentFormatter,
) : NodeFormatter<RuleX_ListLiteralExprContext> {
    override fun format(node: RuleX_ListLiteralExprContext, doc: FormattableDocument) {
        val xLisExprContext = node.getListLiteralExprContext()
        braceFormatter.formatBracePairWithoutSpace(xLisExprContext, doc, BracePairTypes.BRACKETS)
        val (expressionRef, trailingComma) = node.getExpressionRefWithTrailingComma()
        val lineSeparate = lineAnalyzer.formatAsMultiLine(expressionRef)
        whitespaceFormatter.formatTrailingComma(trailingComma, doc, lineSeparate)
        argumentFormatter.formatArguments(expressionRef, doc)
        expressionRef?.forEach { xExprRef -> doc.format(xExprRef) }
    }
}

class MirrorStructFormatter(val braceFormatter: BraceFormatter) : NodeFormatter<RuleX_MirrorStructType0Context> {
    override fun format(node: RuleX_MirrorStructType0Context, doc: FormattableDocument) {
        braceFormatter.formatBracePairWithoutSpace(node, doc, BracePairTypes.ANGLE)
        doc.append(node) { it.noSpace() }
        doc.append(node.ruleX_tkSTRUCT()) { it.noSpace() }
        doc.append(node.ruleX_tkMUTABLE()) { it.oneSpace() }
    }
}

class TupleExprContextFormatter(
    val braceFormatter: BraceFormatter,
    val lineAnalyzer: LineAnalyzer,
    val whitespaceFormatter: WhitespaceFormatter,
    val argumentFormatter: ArgumentFormatter,
) : NodeFormatter<RuleX_TupleExprContext> {
    override fun format(node: RuleX_TupleExprContext, doc: FormattableDocument) {
        val xTupleExpr = node.ruleX_CommaSeparated_14()
        val opening = xTupleExpr.ruleX_tkLPAR()
        val closing = xTupleExpr.ruleX_tkRPAR()
        val lineSeparateExpression = lineAnalyzer.lineSeparateExpr(opening?.start, closing?.start)
        braceFormatter.formatBracePairWithoutSpace(xTupleExpr, doc, BracePairTypes.PARENTHESES)

        val commaSeparateExpr13 = xTupleExpr.ruleX_CommaSeparated_13()
        val (tupleExprField, trailingComma) = node.getXNamesWithTrailingComma()
        whitespaceFormatter.formatTrailingComma(trailingComma, doc, lineSeparateExpression)

        if (lineSeparateExpression) {
            doc.interiorIndent(xTupleExpr)
            doc.prepend(commaSeparateExpr13) { it.newLine() }
            doc.append(commaSeparateExpr13) { it.noSpace() }
            doc.format(commaSeparateExpr13)

            if (tupleExprField != null) {
                argumentFormatter.formatArguments(
                    tupleExprField,
                    doc,
                    indent = false
                )
            }
            doc.prepend(closing) { it.newLine() }
        } else {
            argumentFormatter.formatArguments(tupleExprField, doc)
        }
    }
}
