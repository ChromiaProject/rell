/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.base.compiler.parser.antlr.RellParser.*
import net.postchain.rell.toolbox.formatter.BracePairTypes
import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.*


class EntityDefFormatter : NodeFormatter<EntityDefContext> {
    override fun format(node: EntityDefContext, doc: FormattableDocument) {
        doc.surround(node) { it.setNewLines(2) }
        doc.interiorIndent(node.entityBody())
        doc.surround(node.RULE_ID()) { it.oneSpace() }
        node.entityAnnotations()?.let { doc.format(it) }
        node.entityBody()?.let { doc.format(it) }
    }
}

class EntityBodyFormatter(
    private val tokenAnalyzer: TokenAnalyzer,
) : NodeFormatter<EntityBodyContext> {
    override fun format(node: EntityBodyContext, doc: FormattableDocument) {
        // entityBody: ';' | '{' relClause* '}'
        val anyClause = node.relClause()
        anyClause.forEachIndexed { index, xRelAnyClause ->
            doc.prepend(xRelAnyClause) { it.newLine() }
            if (index == anyClause.lastIndex) {
                doc.append(xRelAnyClause) { it.newLine() }
            }
            doc.format(xRelAnyClause)
        }
        val closingCurly = tokenAnalyzer.tokenFor(node, "}")
        doc.prepend(closingCurly) { it.newLine() }
    }
}

class EntityAnnotationsFormatter(
    private val braceFormatter: BraceFormatter,
    private val argumentFormatter: ArgumentFormatter,
    private val whitespaceFormatter: WhitespaceFormatter,
    private val lineAnalyzer: LineAnalyzer
) : NodeFormatter<EntityAnnotationsContext> {
    override fun format(node: EntityAnnotationsContext, doc: FormattableDocument) {
        doc.surround(node) { it.oneSpace() }
        val (xNames, trailingComma) = node.getAnnotationNames()
        val lineSeparate = node.start.line != node.stop.line
        whitespaceFormatter.formatTrailingComma(trailingComma, doc, lineSeparate)
        // Treat each RULE_ID as an argument by setting whitespace around it manually.
        if (xNames.isNotEmpty()) {
            xNames.forEachIndexed { index, id ->
                if (lineSeparate) {
                    doc.prepend(id) {
                        it.newLine()
                        it.indent()
                    }
                    if (index == xNames.lastIndex) {
                        doc.append(id) { it.newLine() }
                    } else {
                        doc.append(id) { it.noSpace() }
                    }
                } else {
                    doc.prepend(id) { it.oneSpace() }
                    doc.append(id) { it.noSpace() }
                }
            }
        }
        braceFormatter.formatBracePairWithoutSpace(node, doc, BracePairTypes.PARENTHESES)
    }
}

class KeyIndexFormatter(
    private val whitespaceFormatter: WhitespaceFormatter
) : NodeFormatter<KeyIndexClauseContext> {
    override fun format(node: KeyIndexClauseContext, doc: FormattableDocument) {
        doc.append(node) { it.noSpace() }
        doc.prepend(node) { it.newLine() }
        whitespaceFormatter.formatSemicolon(node, doc)

        val (attributeDefs, trailingComma) = node.getAttrItems()
        whitespaceFormatter.formatTrailingComma(trailingComma, doc)
        attributeDefs.forEach { attributeDef ->
            doc.prepend(attributeDef) { it.oneSpace() }
            attributeDef.modifiers().modifier().forEach {
                doc.append(it) { it.oneSpace() }
            }
            doc.format(attributeDef.attrHeader())
            attributeDef.expression()?.let { doc.prepend(it) { c -> c.oneSpace() } }
            doc.append(attributeDef) { it.noSpace() }
        }
    }
}

class BaseAttributeDefFormatter(
    private val tokenAnalyzer: TokenAnalyzer,
) : NodeFormatter<BaseAttributeDefinitionContext> {
    override fun format(node: BaseAttributeDefinitionContext, doc: FormattableDocument) {
        doc.append(node) { it.noSpace() }
        doc.prepend(node) { it.newLine() }
        node.modifiers().modifier().forEach {
            doc.append(it) { it.oneSpace() }
        }
        doc.format(node.attrHeader())
        val equalSign = tokenAnalyzer.tokenFor(node, "=")
        if (equalSign != null) doc.surround(equalSign) { it.oneSpace() }
        node.expression()?.let { doc.prepend(it) { c -> c.oneSpace() } }
    }
}

class NameTypeAttrHeadFormatter : NodeFormatter<NameTypeAttrHeaderContext> {
    override fun format(node: NameTypeAttrHeaderContext, doc: FormattableDocument) {
        doc.append(node.RULE_ID()) { it.noSpace() }
        doc.prepend(node.type()) { it.oneSpace() }
    }
}
