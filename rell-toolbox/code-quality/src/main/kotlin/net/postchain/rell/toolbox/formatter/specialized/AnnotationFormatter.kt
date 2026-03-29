/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.toolbox.formatter.BracePairTypes
import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.ArgumentFormatter
import net.postchain.rell.toolbox.formatter.util.BraceFormatter
import net.postchain.rell.toolbox.formatter.util.LineAnalyzer
import net.postchain.rell.toolbox.formatter.util.WhitespaceFormatter
import net.postchain.rell.toolbox.formatter.util.getAnnotationArgWithTrailingComma
import net.postchain.rell.toolbox.parser.RellParser.RuleX_AnnotationArgsContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_AnnotationContext

class AnnotationFormatter : NodeFormatter<RuleX_AnnotationContext> {
    override fun format(node: RuleX_AnnotationContext, doc: FormattableDocument) {
        doc.append(node) {
            it.setNewLines(1)
            it.highPriority()
        }
        doc.append(node.ruleX_Name()) { it.noSpace() }
        doc.format(node.ruleX_AnnotationArgs())
    }
}

class AnnotArgsFormatter(
    val braceFormatter: BraceFormatter,
    val argumentFormatter: ArgumentFormatter,
    val whitespaceFormatter: WhitespaceFormatter,
    val lineAnalyzer: LineAnalyzer
) : NodeFormatter<RuleX_AnnotationArgsContext> {
    override fun format(node: RuleX_AnnotationArgsContext, doc: FormattableDocument) {
        braceFormatter.formatBracePairWithoutSpace(node, doc, BracePairTypes.PARENTHESES)
        val (annotationArg, trailingComma) = node.getAnnotationArgWithTrailingComma()
        val lineSeparate = lineAnalyzer.formatAsMultiLine(annotationArg)
        whitespaceFormatter.formatTrailingComma(trailingComma, doc, lineSeparate)
        if (lineSeparate) {
            doc.interiorIndentRangeIncludeLast(node, node)
        }
        argumentFormatter.formatArguments(annotationArg, doc, formatAsMultiLine = lineSeparate)
    }
}
