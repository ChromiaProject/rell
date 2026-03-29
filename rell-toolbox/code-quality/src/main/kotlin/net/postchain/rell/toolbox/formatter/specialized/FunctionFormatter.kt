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
import net.postchain.rell.toolbox.formatter.util.getFormalParameterWithTrailingComma
import net.postchain.rell.toolbox.parser.RellParser.RuleX_FunctionBodyShortContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_FunctionDefContext

class FunctionDefFormatter(
    private val braceFormatter: BraceFormatter,
    private val argumentFormatter: ArgumentFormatter,
    private val whitespaceFormatter: WhitespaceFormatter,
    private val lineAnalyzer: LineAnalyzer,
) : NodeFormatter<RuleX_FunctionDefContext> {
    override fun format(node: RuleX_FunctionDefContext, doc: FormattableDocument) {
        doc.surround(node) { it.setNewLines(2) }
        doc.prepend(node.ruleX_QualifiedName()) { it.oneSpace() }
        doc.append(node.ruleX_QualifiedName()) { it.noSpace() }

        node.ruleX_FormalParameters() ?: return

        braceFormatter.formatBracePairWithoutSpace(
            node.ruleX_FormalParameters(),
            doc,
            BracePairTypes.PARENTHESES
        )
        whitespaceFormatter.formatType(node, doc)
        val lineSeparate = lineAnalyzer.lineSeparateArguments(node, BracePairTypes.PARENTHESES)
        val (formalParameters, trailingComma) = node.ruleX_FormalParameters()
            .getFormalParameterWithTrailingComma()

        whitespaceFormatter.formatTrailingComma(trailingComma, doc, lineSeparate)
        argumentFormatter.formatArguments(
            formalParameters,
            doc,
            formatAsMultiLine = lineSeparate
        )
        argumentFormatter.formatParametersType(formalParameters, doc)
        doc.format(node.ruleX_FunctionBody())
    }
}

class FunctionBodyShortFormatter(
    private val whitespaceFormatter: WhitespaceFormatter,
) : NodeFormatter<RuleX_FunctionBodyShortContext> {
    override fun format(node: RuleX_FunctionBodyShortContext, doc: FormattableDocument) {
        whitespaceFormatter.formatSemicolon(node, doc)
        doc.format(node.ruleX_Expression())
    }
}
