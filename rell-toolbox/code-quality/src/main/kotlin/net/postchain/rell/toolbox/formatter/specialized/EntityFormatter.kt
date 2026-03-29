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
import net.postchain.rell.toolbox.formatter.util.TokenAnalyzer
import net.postchain.rell.toolbox.formatter.util.WhitespaceFormatter
import net.postchain.rell.toolbox.formatter.util.getAttributeDefsWithTrailingComma
import net.postchain.rell.toolbox.formatter.util.getEntityAnnotationContext
import net.postchain.rell.toolbox.formatter.util.getXNamesWithTrailingComma
import net.postchain.rell.toolbox.parser.RellParser.*


class EntityDefFormatter : NodeFormatter<RuleX_EntityDefContext> {
    override fun format(node: RuleX_EntityDefContext, doc: FormattableDocument) {
        doc.surround(node) { it.setNewLines(2) }
        doc.interiorIndent(node.ruleX_EntityBody())
        doc.surround(node.ruleX_Name()) { it.oneSpace() }
        doc.format(node.ruleX_EntityAnnotations())
        doc.format(node.ruleX_EntityBody())
    }
}

class EntityBodyFormatter(
    private val tokenAnalyzer: TokenAnalyzer,
) : NodeFormatter<RuleX_EntityBodyFullContext> {
    override fun format(node: RuleX_EntityBodyFullContext, doc: FormattableDocument) {
        val anyClause = node.ruleX_RelClause()
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
) : NodeFormatter<RuleX_EntityAnnotationsContext> {
    override fun format(node: RuleX_EntityAnnotationsContext, doc: FormattableDocument) {
        doc.surround(node) { it.oneSpace() }
        val (xNames, trailingComma) = node.getXNamesWithTrailingComma()
        val lineSeparate = lineAnalyzer.formatAsMultiLine(xNames)
        whitespaceFormatter.formatTrailingComma(trailingComma, doc, lineSeparate)
        argumentFormatter.formatArguments(xNames, doc, formatAsMultiLine = lineSeparate)
        braceFormatter.formatBracePairWithoutSpace(node.getEntityAnnotationContext(), doc, BracePairTypes.PARENTHESES)
    }
}

class KeyIndexFormatter(
    private val whitespaceFormatter: WhitespaceFormatter
) : NodeFormatter<RuleX_KeyIndexClauseContext> {
    override fun format(node: RuleX_KeyIndexClauseContext, doc: FormattableDocument) {
        doc.append(node) { it.noSpace() }
        doc.prepend(node) { it.newLine() }
        whitespaceFormatter.formatSemicolon(node, doc)

        val (attributeDefs, trailingComma) = node.getAttributeDefsWithTrailingComma()
        whitespaceFormatter.formatTrailingComma(trailingComma, doc)
        attributeDefs?.forEach { attributeDef ->
            doc.prepend(attributeDef) { it.oneSpace() }
            attributeDef.ruleX_Modifiers().ruleX_Modifier().forEach {
                doc.append(it) { it.oneSpace() }
            }
            doc.format(attributeDef.ruleX_AttrHeader())
            doc.prepend(attributeDef.ruleX_ExpressionRef()) { it.oneSpace() }
            doc.append(attributeDef) { it.noSpace() }
        }
    }
}

class BaseAttributeDefFormatter(
    private val tokenAnalyzer: TokenAnalyzer,
) : NodeFormatter<RuleX_BaseAttributeDefinitionContext> {
    override fun format(node: RuleX_BaseAttributeDefinitionContext, doc: FormattableDocument) {
        doc.append(node) { it.noSpace() }
        doc.prepend(node) { it.newLine() }
        node.ruleX_Modifiers().ruleX_Modifier().forEach {
            doc.append(it) { it.oneSpace() }
        }
        doc.format(node.ruleX_AttrHeader())
        val equalSign = tokenAnalyzer.tokenFor(node, "=")
        doc.surround(equalSign) { it.oneSpace() }
        doc.prepend(node.ruleX_ExpressionRef()) { it.oneSpace() }
    }
}

class NameTypeAttrHeadFormatter : NodeFormatter<RuleX_NameTypeAttrHeaderContext> {
    override fun format(node: RuleX_NameTypeAttrHeaderContext, doc: FormattableDocument) {
        doc.append(node.ruleX_NameNode()) { it.noSpace() }
        doc.prepend(node.ruleX_Type()) { it.oneSpace() }
    }
}
