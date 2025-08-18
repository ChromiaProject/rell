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
    override fun format(xEntityDef: RuleX_EntityDefContext, doc: FormattableDocument) {
        doc.surround(xEntityDef) { it.setNewLines(2) }
        doc.interiorIndent(xEntityDef.ruleX_EntityBody())
        doc.surround(xEntityDef.ruleX_Name()) { it.oneSpace() }
        doc.format(xEntityDef.ruleX_EntityAnnotations())
        doc.format(xEntityDef.ruleX_EntityBody())
    }
}

class EntityBodyFormatter(
    private val tokenAnalyzer: TokenAnalyzer,
) : NodeFormatter<RuleX_EntityBodyFullContext> {
    override fun format(xEntitybody: RuleX_EntityBodyFullContext, doc: FormattableDocument) {
        val anyClause = xEntitybody.ruleX_RelClause()
        anyClause.forEachIndexed { index, xRelAnyClause ->
            doc.prepend(xRelAnyClause) { it.newLine() }
            if (index == anyClause.lastIndex) {
                doc.append(xRelAnyClause) { it.newLine() }
            }
            doc.format(xRelAnyClause)
        }
        val closingCurly = tokenAnalyzer.tokenFor(xEntitybody, "}")
        doc.prepend(closingCurly) { it.newLine() }
    }
}

class EntityAnnotationsFormatter(
    private val braceFormatter: BraceFormatter,
    private val argumentFormatter: ArgumentFormatter,
    private val whitespaceFormatter: WhitespaceFormatter,
    private val lineAnalyzer: LineAnalyzer
) : NodeFormatter<RuleX_EntityAnnotationsContext> {
    override fun format(xEntityAnnotations: RuleX_EntityAnnotationsContext, doc: FormattableDocument) {
        doc.surround(xEntityAnnotations) { it.oneSpace() }
        val (xNames, trailingComma) = xEntityAnnotations.getXNamesWithTrailingComma()
        val lineSeparate = lineAnalyzer.formatAsMultiLine(xNames)
        whitespaceFormatter.formatTrailingComma(trailingComma, doc, lineSeparate)
        argumentFormatter.formatArguments(xNames, doc, formatAsMultiLine = lineSeparate)
        braceFormatter.formatBracePairWithoutSpace(xEntityAnnotations.getEntityAnnotationContext(), doc, BracePairTypes.PARENTHESES)
    }
}

class KeyIndexFormatter(
    private val whitespaceFormatter: WhitespaceFormatter
) : NodeFormatter<RuleX_KeyIndexClauseContext> {
    override fun format(xRellKeyIndex: RuleX_KeyIndexClauseContext, doc: FormattableDocument) {
        doc.append(xRellKeyIndex) { it.noSpace() }
        doc.prepend(xRellKeyIndex) { it.newLine() }
        whitespaceFormatter.formatSemicolon(xRellKeyIndex, doc)

        val (attributeDefs, trailingComma) = xRellKeyIndex.getAttributeDefsWithTrailingComma()
        whitespaceFormatter.formatTrailingComma(trailingComma, doc)
        attributeDefs?.forEach { attributeDef ->
            doc.prepend(attributeDef) { it.oneSpace() }
            doc.append(attributeDef.ruleX_tkMUTABLE()) { it.oneSpace() }
            doc.format(attributeDef.ruleX_AttrHeader())
            doc.prepend(attributeDef.ruleX_ExpressionRef()) { it.oneSpace() }
            doc.append(attributeDef) { it.noSpace() }
        }
    }
}

class BaseAttributeDefFormatter(
    private val tokenAnalyzer: TokenAnalyzer,
) : NodeFormatter<RuleX_BaseAttributeDefinitionContext> {
    override fun format(xBaseAttriDef: RuleX_BaseAttributeDefinitionContext, doc: FormattableDocument) {
        doc.append(xBaseAttriDef) { it.noSpace() }
        doc.prepend(xBaseAttriDef) { it.newLine() }
        doc.append(xBaseAttriDef.ruleX_tkMUTABLE()) { it.oneSpace() }
        doc.format(xBaseAttriDef.ruleX_AttrHeader())
        val equalSign = tokenAnalyzer.tokenFor(xBaseAttriDef, "=")
        doc.surround(equalSign) { it.oneSpace() }
        doc.prepend(xBaseAttriDef.ruleX_ExpressionRef()) { it.oneSpace() }
    }
}

class NameTypeAttrHeadFormatter : NodeFormatter<RuleX_NameTypeAttrHeaderContext> {
    override fun format(xNameTypeAttrHead: RuleX_NameTypeAttrHeaderContext, doc: FormattableDocument) {
        doc.append(xNameTypeAttrHead.ruleX_NameNode()) { it.noSpace() }
        doc.prepend(xNameTypeAttrHead.ruleX_Type()) { it.oneSpace() }
    }
}
