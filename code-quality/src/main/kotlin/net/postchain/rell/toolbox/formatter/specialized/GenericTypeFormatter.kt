package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.toolbox.formatter.BracePairTypes
import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.ArgumentFormatter
import net.postchain.rell.toolbox.formatter.util.BraceFormatter
import net.postchain.rell.toolbox.formatter.util.ExpressionFormatter
import net.postchain.rell.toolbox.formatter.util.LineAnalyzer
import net.postchain.rell.toolbox.formatter.util.WhitespaceFormatter
import net.postchain.rell.toolbox.formatter.util.getCallArgWithTrailingComma
import net.postchain.rell.toolbox.formatter.util.getGenericTypeContext
import net.postchain.rell.toolbox.formatter.util.getTypeRefWithTrailingComma
import net.postchain.rell.toolbox.parser.RellParser.*

class GenTypeExprFormatter(
    private val braceFormatter: BraceFormatter,
    private val expressionFormatter: ExpressionFormatter,
) : NodeFormatter<RuleX_GenericTypeExprContext> {
    override fun format(xGenTypeExpr: RuleX_GenericTypeExprContext, doc: FormattableDocument) {
        doc.format(xGenTypeExpr.ruleX_GenericType())
        if (xGenTypeExpr.ruleX_BaseExprTailMember() != null) {
            doc.format(xGenTypeExpr.ruleX_BaseExprTailMember())
        } else if (xGenTypeExpr.ruleX_BaseExprTailCall() != null) {
            val tailCall = xGenTypeExpr.ruleX_BaseExprTailCall()
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
    override fun format(xGenericType: RuleX_GenericTypeContext, doc: FormattableDocument) {
        doc.append(xGenericType.ruleX_QualifiedName()) { it.noSpace() }
        braceFormatter.formatBracePairWithoutSpace(xGenericType.getGenericTypeContext(), doc, BracePairTypes.ANGLE)
        val (typeRef, trailingComma) = xGenericType.getTypeRefWithTrailingComma()
        val lineSeparate = lineAnalyzer.formatAsMultiLine(typeRef)
        argumentFormatter.formatArguments(typeRef, doc, formatAsMultiLine = lineSeparate)
        whitespaceFormatter.formatTrailingComma(trailingComma, doc, lineSeparate)
        typeRef?.forEach { xTypeRef ->
            doc.prepend(xTypeRef) { it.oneSpace() }
            doc.append(xTypeRef) { it.noSpace() }
        }
    }
}