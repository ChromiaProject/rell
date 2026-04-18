/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.toolbox.formatter.BracePairTypes
import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.*
import net.postchain.rell.toolbox.parser.RellParser.RuleX_UpdateStmtContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_UpdateTargetAtContext

class UpdateStmtFormatter(
    private val braceFormatter: BraceFormatter,
    private val lineAnalyzer: LineAnalyzer,
    private val whitespaceFormatter: WhitespaceFormatter,
    private val argumentFormatter: ArgumentFormatter,
) : NodeFormatter<RuleX_UpdateStmtContext> {
    override fun format(node: RuleX_UpdateStmtContext, doc: FormattableDocument) {
        doc.append(node.ruleX_tkUPDATE()) { it.oneSpace() }
        doc.append(node.ruleX_UpdateTarget()) { it.oneSpace() }
        doc.format(node.ruleX_UpdateTarget())
        braceFormatter.formatBracePairWithSpace(node, doc, BracePairTypes.PARENTHESES)
        val (whatExpr, trailingComma) = node.getUpdateWhatExprWithTrailingComma()
        val lineSeparate = lineAnalyzer.lineSeparateArguments(node, BracePairTypes.PARENTHESES)
        whitespaceFormatter.formatTrailingComma(trailingComma, doc, lineSeparate)
        argumentFormatter.formatArguments(whatExpr, doc, formatAsMultiLine = lineSeparate)
        whitespaceFormatter.formatSemicolon(node, doc)
    }
}

class UpdateTargetAtFormatter(
    private val braceFormatter: BraceFormatter,
    private val lineAnalyzer: LineAnalyzer,
    private val whitespaceFormatter: WhitespaceFormatter,
    private val argumentFormatter: ArgumentFormatter,
) : NodeFormatter<RuleX_UpdateTargetAtContext> {
    override fun format(node: RuleX_UpdateTargetAtContext, doc: FormattableDocument) {
        // doc.append(xUpdateTargetAt.ruleX_AtExprFrom()) { it.oneSpace() }
        doc.append(node.ruleX_UpdateFrom()) { it.oneSpace() }
        doc.append(node.ruleX_AtExprAt()) { it.oneSpace() }

        // TODO: Format atExprWhere should work, duplication of code
        val atExprWhere = node.ruleX_AtExprWhere()
        braceFormatter.formatBracePairWithSpace(atExprWhere, doc, BracePairTypes.CURLY)
        val (expressionRef, trailingComma) = atExprWhere.getExpressionRefWithTrailingComma()
        val lineSeparate = lineAnalyzer.lineSeparateArguments(atExprWhere, BracePairTypes.CURLY)
        whitespaceFormatter.formatTrailingComma(trailingComma, doc, lineSeparate)
        argumentFormatter.formatArguments(expressionRef, doc, formatAsMultiLine = lineSeparate)
        expressionRef?.forEach { doc.format(it) }
    }
}
