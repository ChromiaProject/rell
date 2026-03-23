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
import net.postchain.rell.toolbox.formatter.util.getExpressionRefWithTrailingComma
import net.postchain.rell.toolbox.formatter.util.getUpdateWhatExprWithTrailingComma
import net.postchain.rell.toolbox.parser.RellParser.*

class UpdateStmtFormatter(
    private val braceFormatter: BraceFormatter,
    private val lineAnalyzer: LineAnalyzer,
    private val whitespaceFormatter: WhitespaceFormatter,
    private val argumentFormatter: ArgumentFormatter,
) : NodeFormatter<RuleX_UpdateStmtContext> {
    override fun format(xUpdateStmt: RuleX_UpdateStmtContext, doc: FormattableDocument) {
        doc.append(xUpdateStmt.ruleX_tkUPDATE()) { it.oneSpace() }
        doc.append(xUpdateStmt.ruleX_UpdateTarget()) { it.oneSpace() }
        doc.format(xUpdateStmt.ruleX_UpdateTarget())
        braceFormatter.formatBracePairWithSpace(xUpdateStmt, doc, BracePairTypes.PARENTHESES)
        val (whatExpr, trailingComma) = xUpdateStmt.getUpdateWhatExprWithTrailingComma()
        val lineSeparate = lineAnalyzer.lineSeparateArguments(xUpdateStmt, BracePairTypes.PARENTHESES)
        whitespaceFormatter.formatTrailingComma(trailingComma, doc, lineSeparate)
        argumentFormatter.formatArguments(whatExpr, doc, formatAsMultiLine = lineSeparate)
        whitespaceFormatter.formatSemicolon(xUpdateStmt, doc)
    }
}

class UpdateTargetAtFormatter(
    private val braceFormatter: BraceFormatter,
    private val lineAnalyzer: LineAnalyzer,
    private val whitespaceFormatter: WhitespaceFormatter,
    private val argumentFormatter: ArgumentFormatter,
) : NodeFormatter<RuleX_UpdateTargetAtContext> {
    override fun format(xUpdateTargetAt: RuleX_UpdateTargetAtContext, doc: FormattableDocument) {
        // doc.append(xUpdateTargetAt.ruleX_AtExprFrom()) { it.oneSpace() }
        doc.append(xUpdateTargetAt.ruleX_UpdateFrom()) { it.oneSpace() }
        doc.append(xUpdateTargetAt.ruleX_AtExprAt()) { it.oneSpace() }

        // TODO: Format atExprWhere should work, duplication of code
        val atExprWhere = xUpdateTargetAt.ruleX_AtExprWhere()
        braceFormatter.formatBracePairWithSpace(atExprWhere, doc, BracePairTypes.CURLY)
        val (expressionRef, trailingComma) = atExprWhere.getExpressionRefWithTrailingComma()
        val lineSeparate = lineAnalyzer.lineSeparateArguments(atExprWhere, BracePairTypes.CURLY)
        whitespaceFormatter.formatTrailingComma(trailingComma, doc, lineSeparate)
        argumentFormatter.formatArguments(expressionRef, doc, formatAsMultiLine = lineSeparate)
        expressionRef?.forEach { doc.format(it) }
    }
}
