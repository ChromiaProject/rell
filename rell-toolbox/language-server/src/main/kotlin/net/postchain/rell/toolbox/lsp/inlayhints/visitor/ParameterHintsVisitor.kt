/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.inlayhints.visitor

import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.lsp.inlayhints.RellInlayHintsProvider.Companion.createParameterInlayHint
import net.postchain.rell.toolbox.lsp.inlayhints.RellInlayHintsProvider.Companion.isInRange
import net.postchain.rell.toolbox.parser.RellBaseVisitor
import net.postchain.rell.toolbox.parser.RellParser
import org.antlr.v4.runtime.misc.Interval
import org.eclipse.lsp4j.InlayHint
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range

class ParameterHintsVisitor(
    private val resource: Resource,
    private val range: Range,
    private val hints: MutableList<InlayHint>
) : RellBaseVisitor<Unit>() {

    override fun visitRuleX_BaseExprTailCall(ctx: RellParser.RuleX_BaseExprTailCallContext) {
        processFunctionCall(ctx)
    }

    private fun processFunctionCall(callExpr: RellParser.RuleX_BaseExprTailCallContext) {
        val position = Position(callExpr.start.line - 1, callExpr.start.charPositionInLine)
        if (!isInRange(position, range)) return

        val callArgs = callExpr.ruleX_CallArgs()
        val callArgsRule = callArgs?.ruleX_CommaSeparated_28()?.ruleX_CommaSeparated_27()

        val paramIndices = resolveFunctionParams(callExpr)
        val callArgsList = getFunAnonymousArguments(callArgsRule)

        callArgsList?.forEach { argument ->
            val parameterName = paramIndices[argument.index]

            if (parameterName != null && argument.name != parameterName) {
                hints.add(createParameterInlayHint(argument.position, parameterName))
            }
        }
    }

    private fun getFunAnonymousArguments(
        functionParamRule: RellParser.RuleX_CommaSeparated_27Context?
    ) = functionParamRule
        ?.children
        ?.filterIsInstance<RellParser.RuleX_CallArgContext>()
        ?.mapIndexedNotNull { index, callArgContext ->
            when {
                callArgContext.ruleX_Name() != null -> null
                else -> CallArgument(
                    index,
                    Position(
                        callArgContext.ruleX_CallArgValue().start.line - 1,
                        callArgContext.ruleX_CallArgValue().start.charPositionInLine
                    ),
                    callArgContext.text
                )
            }
        }

    private fun resolveFunctionParams(
        callExpr: RellParser.RuleX_BaseExprTailCallContext
    ): Map<Int, String> {
        val baseExpr = (callExpr.parent?.parent as? RellParser.RuleX_BaseExprContext)
        val offset = baseExpr?.start?.startIndex ?: return mapOf()
        val ideSymbolInfo = resource.locationInfo[Interval(offset, offset)]?.ideSymbolInfo

        val paramIndices = ideSymbolInfo?.doc?.declaration?.completion?.params
            ?.mapIndexed { index, param -> index to param.name }
            ?.toMap() ?: mapOf()

        return paramIndices
    }

    data class CallArgument(val index: Int, val position: Position, val name: String)
}
