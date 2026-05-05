/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.base.compiler.parser.antlr.RellParser.FunctionBodyContext
import net.postchain.rell.base.compiler.parser.antlr.RellParser.FunctionDefContext
import net.postchain.rell.base.compiler.parser.antlr.RellParser.QueryBodyContext
import net.postchain.rell.toolbox.formatter.BracePairTypes
import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.*

class FunctionDefFormatter(
    private val braceFormatter: BraceFormatter,
    private val argumentFormatter: ArgumentFormatter,
    private val whitespaceFormatter: WhitespaceFormatter,
    private val lineAnalyzer: LineAnalyzer,
) : NodeFormatter<FunctionDefContext> {
    override fun format(node: FunctionDefContext, doc: FormattableDocument) {
        doc.surround(node) { it.setNewLines(2) }
        node.qualifiedName()?.let {
            doc.prepend(it) { c -> c.oneSpace() }
            doc.append(it) { c -> c.noSpace() }
        }

        val params = node.formalParameters() ?: return

        braceFormatter.formatBracePairWithoutSpace(params, doc, BracePairTypes.PARENTHESES)
        whitespaceFormatter.formatType(node, doc)
        val lineSeparate = lineAnalyzer.lineSeparateArguments(node, BracePairTypes.PARENTHESES)
        val (formalParameters, trailingComma) = params.getFormalParametersItems()

        whitespaceFormatter.formatTrailingComma(trailingComma, doc, lineSeparate)
        argumentFormatter.formatArguments(
            formalParameters,
            doc,
            formatAsMultiLine = lineSeparate
        )
        argumentFormatter.formatParametersType(formalParameters, doc)
        doc.format(node.functionBody())
    }
}

/**
 * functionBody: ';' | '=' expression ';' | blockStmt
 */
class FunctionBodyFormatter(
    private val whitespaceFormatter: WhitespaceFormatter,
) : NodeFormatter<FunctionBodyContext> {
    override fun format(node: FunctionBodyContext, doc: FormattableDocument) {
        whitespaceFormatter.formatSemicolon(node, doc)
        // Surround '=' with a single space when the body is the short form.
        node.children?.forEach { c ->
            if (c is org.antlr.v4.runtime.tree.TerminalNode && c.symbol.text == "=") {
                doc.surround(c) { it.oneSpace() }
            }
        }
        node.expression()?.let { doc.format(it) }
        node.blockStmt()?.let { doc.format(it) }
    }
}

/**
 * queryBody: '=' expression ';' | blockStmt
 */
class QueryBodyFormatter(
    private val whitespaceFormatter: WhitespaceFormatter,
) : NodeFormatter<QueryBodyContext> {
    override fun format(node: QueryBodyContext, doc: FormattableDocument) {
        whitespaceFormatter.formatSemicolon(node, doc)
        node.children?.forEach { c ->
            if (c is org.antlr.v4.runtime.tree.TerminalNode && c.symbol.text == "=") {
                doc.surround(c) { it.oneSpace() }
            }
        }
        node.expression()?.let { doc.format(it) }
        node.blockStmt()?.let { doc.format(it) }
    }
}
