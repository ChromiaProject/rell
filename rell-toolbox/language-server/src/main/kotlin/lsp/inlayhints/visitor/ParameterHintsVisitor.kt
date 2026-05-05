/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.inlayhints.visitor

import net.postchain.rell.base.compiler.parser.antlr.RellBaseVisitor
import net.postchain.rell.base.compiler.parser.antlr.RellLexer
import net.postchain.rell.base.compiler.parser.antlr.RellParser
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.lsp.inlayhints.RellInlayHintsProvider.Companion.createParameterInlayHint
import net.postchain.rell.toolbox.lsp.inlayhints.RellInlayHintsProvider.Companion.isInRange
import org.antlr.v4.runtime.tree.TerminalNode
import org.antlr.v4.runtime.misc.Interval
import org.eclipse.lsp4j.InlayHint
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range

/**
 * Inlay-hint visitor for parameter names at function-call sites.
 * Migrated from legacy `Rell.g4` to canonical `Rell.g4`.
 *
 * In the new grammar there is no `RuleX_BaseExprTailCallContext`. Calls are
 * inline children of `baseExpr`: a `callArgs` child following a `baseExprHead`
 * (or following further `baseExprTailNoCallNoAt` / `callArgs` chains for
 * curried calls). We walk every `baseExpr` and inspect its `callArgs` children.
 *
 * The `callArgs` body is `'(' (((RULE_ID '=')? expression) (',' ...)* ','?)? ')'`,
 * so we walk `callArgs.children` directly to identify each argument and whether
 * it is anonymous (no `RULE_ID '='` prefix) or named.
 */
class ParameterHintsVisitor(
    private val resource: Resource,
    private val range: Range,
    private val hints: MutableList<InlayHint>
) : RellBaseVisitor<Unit>() {

    override fun visitBaseExpr(ctx: RellParser.BaseExprContext) {
        val head = ctx.baseExprHead()
        // Find each callArgs child, with the position of the head/preceding tail
        // it is invoking. For simplicity, only emit hints for the first call (the
        // most common form `name(args)` and `expr.member(args)`); curried calls
        // would require resolving each intermediate symbol.
        val callArgsList = ctx.callArgs()
        if (callArgsList.isNotEmpty() && head != null) {
            val firstCall = callArgsList[0]
            processCall(ctx, firstCall)
        }
        super.visitBaseExpr(ctx)
    }

    private fun processCall(
        baseExpr: RellParser.BaseExprContext,
        callArgs: RellParser.CallArgsContext
    ) {
        val position = Position(callArgs.start.line - 1, callArgs.start.charPositionInLine)
        if (!isInRange(position, range)) return

        val paramIndices = resolveFunctionParams(baseExpr)
        if (paramIndices.isEmpty()) return

        val anonArgs = collectAnonymousArguments(callArgs)
        for (arg in anonArgs) {
            val parameterName = paramIndices[arg.index] ?: continue
            if (arg.text != parameterName) {
                hints.add(createParameterInlayHint(arg.position, parameterName))
            }
        }
    }

    private fun collectAnonymousArguments(callArgs: RellParser.CallArgsContext): List<CallArgument> {
        val out = mutableListOf<CallArgument>()
        var index = 0
        var i = 0
        val children = callArgs.children ?: return out
        // Skip the leading '('.
        while (i < children.size) {
            val child = children[i]
            if (child is TerminalNode && child.symbol.text == "(") {
                i++
                break
            }
            i++
        }
        while (i < children.size) {
            // An argument item is one of:
            //   RULE_ID '=' expression       (named)
            //   expression                   (anonymous)
            //   '*'                          (special placeholder)
            val first = children[i]
            if (first is TerminalNode) {
                val sym = first.symbol
                if (sym.text == ")") break
                if (sym.text == ",") { i++; continue }
                if (sym.type == RellLexer.RULE_ID
                    && i + 1 < children.size
                    && (children[i + 1] as? TerminalNode)?.symbol?.text == "="
                ) {
                    // Named argument: skip RULE_ID, '=', and the following expression.
                    i += 2
                    if (i < children.size && children[i] is RellParser.ExpressionContext) i++
                    index++
                    continue
                }
                if (sym.text == "*") {
                    // Anonymous wildcard arg.
                    val pos = Position(sym.line - 1, sym.charPositionInLine)
                    out.add(CallArgument(index, pos, "*"))
                    i++
                    index++
                    continue
                }
                i++
                continue
            }
            if (first is RellParser.ExpressionContext) {
                val pos = Position(first.start.line - 1, first.start.charPositionInLine)
                out.add(CallArgument(index, pos, first.text))
                i++
                index++
                continue
            }
            i++
        }
        return out
    }

    private fun resolveFunctionParams(
        baseExpr: RellParser.BaseExprContext
    ): Map<Int, String> {
        val offset = baseExpr.start?.startIndex ?: return mapOf()
        val ideSymbolInfo = resource.locationInfo[Interval(offset, offset)]?.ideSymbolInfo
        return ideSymbolInfo?.doc?.declaration?.completion?.params
            ?.mapIndexed { idx, param -> idx to param.name }
            ?.toMap() ?: mapOf()
    }

    data class CallArgument(val index: Int, val position: Position, val text: String)
}
