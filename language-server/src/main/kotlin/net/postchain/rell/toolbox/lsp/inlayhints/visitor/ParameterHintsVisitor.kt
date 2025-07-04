package net.postchain.rell.toolbox.lsp.inlayhints.visitor

import net.postchain.rell.base.utils.ide.IdeSymbolInfo
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

        val functionInfo = resolveIdeSymbolFrom(callExpr)
        val callArgsList = getFunAnonymousParams(callArgsRule)

        callArgsList?.forEach { (paramIndex, position) ->
            val parameterName = functionInfo?.doc
                ?.declaration
                ?.completion
                ?.params
                ?.getOrNull(paramIndex)
                ?.name

            if (parameterName != null) {
                hints.add(createParameterInlayHint(position, parameterName))
            }
        }
    }

    private fun getFunAnonymousParams(
        functionParamRule: RellParser.RuleX_CommaSeparated_27Context?
    ): MutableList<Pair<Int, Position>>? {
        functionParamRule ?: return null
        val anonymousParams = mutableListOf<Pair<Int, Position>>()
        functionParamRule.children.filter {
            it is RellParser.RuleX_CallArgContext
        }.forEachIndexed { i, child ->
            if ((child as RellParser.RuleX_CallArgContext).ruleX_Name() == null) {
                anonymousParams.add(
                    i to Position(
                        child.ruleX_CallArgValue().start.line - 1,
                        child.ruleX_CallArgValue().start.charPositionInLine
                    )
                )
            }
        }
        return anonymousParams
    }

    private fun resolveIdeSymbolFrom(
        callExpr: RellParser.RuleX_BaseExprTailCallContext
    ): IdeSymbolInfo? {
        val baseExpr = (callExpr.parent?.parent as? RellParser.RuleX_BaseExprContext)
        val offset = baseExpr?.start?.startIndex ?: return null
        return resource.locationInfo[Interval(offset, offset)]?.ideSymbolInfo
    }
}
