/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.toolbox.formatter.BracePairTypes
import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.*
import net.postchain.rell.toolbox.parser.RellParser.RuleX_GenericTypeContext
import net.postchain.rell.toolbox.parser.RellParser.RuleX_GenericTypeExprContext

class GenTypeExprFormatter(
    private val braceFormatter: BraceFormatter,
    private val expressionFormatter: ExpressionFormatter,
) : NodeFormatter<RuleX_GenericTypeExprContext> {
    override fun format(node: RuleX_GenericTypeExprContext, doc: FormattableDocument) {
        doc.format(node.ruleX_GenericType())
        if (node.ruleX_BaseExprTailMember() != null) {
            doc.format(node.ruleX_BaseExprTailMember())
        } else if (node.ruleX_BaseExprTailCall() != null) {
            val tailCall = node.ruleX_BaseExprTailCall()
            val (callArg, _) = tailCall.ruleX_CallArgs().getCallArgWithTrailingComma()
            doc.prepend(tailCall) { it.noSpace() }
            if (!callArg.isNullOrEmpty()) {
                expressionFormatter.formatExprTailSingleline(tailCall, doc)
            } else {
                braceFormatter.formatBracePairWithoutSpace(tailCall, doc, BracePairTypes.PARENTHESES)
            }
            doc.format(tailCall)
        }
    }
}

class GenericTypeFormatter(
    private val braceFormatter: BraceFormatter,
    private val argumentFormatter: ArgumentFormatter,
    private val whitespaceFormatter: WhitespaceFormatter,
    private val lineAnalyzer: LineAnalyzer,
) : NodeFormatter<RuleX_GenericTypeContext> {
    override fun format(node: RuleX_GenericTypeContext, doc: FormattableDocument) {
        doc.append(node.ruleX_QualifiedName()) { it.noSpace() }
        braceFormatter.formatBracePairWithoutSpace(node.getGenericTypeContext(), doc, BracePairTypes.ANGLE)
        val (typeRef, trailingComma) = node.getTypeRefWithTrailingComma()
        val lineSeparate = lineAnalyzer.formatAsMultiLine(typeRef)
        argumentFormatter.formatArguments(typeRef, doc, formatAsMultiLine = lineSeparate)
        whitespaceFormatter.formatTrailingComma(trailingComma, doc, lineSeparate)
        typeRef?.forEach { xTypeRef ->
            doc.prepend(xTypeRef) { it.oneSpace() }
            doc.append(xTypeRef) { it.noSpace() }
        }
    }
}