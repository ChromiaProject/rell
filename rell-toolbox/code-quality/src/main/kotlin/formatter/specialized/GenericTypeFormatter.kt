/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.base.compiler.parser.antlr.RellParser.*
import net.postchain.rell.toolbox.formatter.BracePairTypes
import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.*
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.TerminalNode

/**
 * `# genericTypeExpr` of `baseExprHead`:
 *   qualifiedName '<' type (',' type)* ','? '>' (callArgs | '.' RULE_ID)
 */
class GenTypeExprFormatter(
    private val braceFormatter: BraceFormatter,
    private val expressionFormatter: ExpressionFormatter,
) : NodeFormatter<GenericTypeExprContext> {
    override fun format(node: GenericTypeExprContext, doc: FormattableDocument) {
        // No space between qualifiedName and '<'.
        doc.append(node.qualifiedName()) { it.noSpace() }
        formatAngleTypeArgs(node, node.type(), doc, braceFormatter)

        val callArgs = node.callArgs()
        if (callArgs != null) {
            doc.prepend(callArgs) { it.noSpace() }
            val (callArg, _) = callArgs.getCallArgsItems()
            if (callArg.isNotEmpty()) {
                expressionFormatter.formatExprTailSingleline(BaseExprTail.Call(callArgs), doc)
            } else {
                braceFormatter.formatBracePairWithoutSpace(callArgs, doc, BracePairTypes.PARENTHESES)
            }
            doc.format(callArgs)
        }
        // The '.' RULE_ID alt: leave default formatting.
    }
}

/**
 * `# genericOrNameType` of `primaryType`:
 *   qualifiedName ('<' type (',' type)* ','? '>')?
 */
class GenericTypeFormatter(
    private val braceFormatter: BraceFormatter,
    private val argumentFormatter: ArgumentFormatter,
    private val whitespaceFormatter: WhitespaceFormatter,
    private val lineAnalyzer: LineAnalyzer,
) : NodeFormatter<GenericOrNameTypeContext> {
    override fun format(node: GenericOrNameTypeContext, doc: FormattableDocument) {
        if (node.type().isNotEmpty()) {
            doc.append(node.qualifiedName()) { it.noSpace() }
            formatAngleTypeArgs(node, node.type(), doc, braceFormatter)
        }
    }
}

/**
 * Common helper: format a `'<' type (',' type)* ','? '>'` run inside [parent] given the
 * concrete list of [TypeContext] type-arguments. Angle brackets get no surrounding space;
 * commas take no space before, single space after; type arguments themselves render flat
 * (no leading whitespace forced).
 */
private fun formatAngleTypeArgs(
    parent: ParserRuleContext,
    typeArgs: List<TypeContext>,
    doc: FormattableDocument,
    braceFormatter: BraceFormatter,
) {
    if (typeArgs.isEmpty()) return
    braceFormatter.formatBracePairWithoutSpace(parent, doc, BracePairTypes.ANGLE)

    // First type arg: no space before. Subsequent: one space before. None has trailing space.
    typeArgs.forEachIndexed { i, t ->
        if (i == 0) {
            doc.prepend(t) { it.noSpace() }
        } else {
            doc.prepend(t) { it.oneSpace() }
        }
        doc.append(t) { it.noSpace() }
    }
    // Commas: no space before, single space after; trailing comma (if any) has no space after.
    val trailing = parent.findTrailingComma(">")
    parent.children?.forEach { c ->
        if (c is TerminalNode && c.symbol.text == ",") {
            doc.prepend(c) { it.noSpace() }
            if (c === trailing) {
                doc.append(c) { it.noSpace() }
            } else {
                doc.append(c) { it.oneSpace() }
            }
        }
    }
}
