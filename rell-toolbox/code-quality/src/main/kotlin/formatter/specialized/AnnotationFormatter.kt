/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.base.compiler.parser.antlr.RellManualParser.AnnotationArgsContext
import net.postchain.rell.base.compiler.parser.antlr.RellManualParser.AnnotationContext
import net.postchain.rell.toolbox.formatter.BracePairTypes
import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.*

class AnnotationFormatter(
    @Suppress("unused") private val whitespaceFormatter: WhitespaceFormatter,
) : NodeFormatter<AnnotationContext> {
    override fun format(node: AnnotationContext, doc: FormattableDocument) {
        doc.append(node) {
            it.setNewLines(1)
            it.highPriority()
        }
        // The annotation has children: '@', RULE_ID, optional annotationArgs.
        // No space between '@' and RULE_ID, no space between RULE_ID and '('.
        doc.append(node.RULE_ID()) { it.noSpace() }
        doc.format(node.annotationArgs())
    }
}

class AnnotArgsFormatter(
    val braceFormatter: BraceFormatter,
    val argumentFormatter: ArgumentFormatter,
    val whitespaceFormatter: WhitespaceFormatter,
    val lineAnalyzer: LineAnalyzer
) : NodeFormatter<AnnotationArgsContext> {
    override fun format(node: AnnotationArgsContext, doc: FormattableDocument) {
        braceFormatter.formatBracePairWithoutSpace(node, doc, BracePairTypes.PARENTHESES)
        val (annotationArg, trailingComma) = node.getAnnotationArgs()
        val lineSeparate = lineAnalyzer.formatAsMultiLine(annotationArg)
        whitespaceFormatter.formatTrailingComma(trailingComma, doc, lineSeparate)
        if (lineSeparate) {
            doc.interiorIndentRangeIncludeLast(node, node)
        }
        argumentFormatter.formatArguments(annotationArg, doc, formatAsMultiLine = lineSeparate)
    }
}
