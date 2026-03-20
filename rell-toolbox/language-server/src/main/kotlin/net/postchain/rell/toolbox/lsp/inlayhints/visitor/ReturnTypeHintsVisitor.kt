package net.postchain.rell.toolbox.lsp.inlayhints.visitor

import net.postchain.rell.base.utils.ide.IdeSymbolInfo
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.lsp.inlayhints.RellInlayHintsProvider.Companion.createTypeInlayHint
import net.postchain.rell.toolbox.lsp.inlayhints.RellInlayHintsProvider.Companion.isInRange
import net.postchain.rell.toolbox.parser.RellBaseVisitor
import net.postchain.rell.toolbox.parser.RellParser
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.misc.Interval
import org.eclipse.lsp4j.InlayHint
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range

class ReturnTypeHintsVisitor(
    private val resource: Resource,
    private val range: Range,
    private val hints: MutableList<InlayHint>
) : RellBaseVisitor<Unit>() {

    override fun visitRuleX_FunctionDef(ctx: RellParser.RuleX_FunctionDefContext) {
        processDefinition(
            ctx,
            ctx.ruleX_Type(),
            ctx.ruleX_QualifiedName(),
            ctx.ruleX_FormalParameters()
        )
    }

    override fun visitRuleX_QueryDef(ctx: RellParser.RuleX_QueryDefContext) {
        processDefinition(
            ctx,
            ctx.ruleX_Type(),
            ctx.ruleX_Name(),
            ctx.ruleX_FormalParameters()
        )
    }

    private fun processDefinition(
        context: ParserRuleContext,
        type: RellParser.RuleX_TypeContext?,
        name: ParserRuleContext?,
        params: RellParser.RuleX_FormalParametersContext?
    ) {
        if (type != null) return

        val position = Position(context.start.line - 1, context.start.charPositionInLine)
        if (!isInRange(position, range)) return

        val offset = name?.start?.startIndex ?: return
        val inferredReturnType = getSymbolInfoFrom(offset)?.let {
            extractReturnTypeFromSymbolInfo(it)
        } ?: return

        val hintPosition = getPositionAfterParameters(context, name, params)
        hints.add(createTypeInlayHint(hintPosition, inferredReturnType))
    }

    private fun getSymbolInfoFrom(offset: Int): IdeSymbolInfo? =
        resource.locationInfo[Interval(offset, offset)]?.ideSymbolInfo

    private fun extractReturnTypeFromSymbolInfo(symbolInfo: IdeSymbolInfo): String? =
        symbolInfo.doc?.declaration?.completion?.result

    private fun getPositionAfterParameters(
        context: ParserRuleContext,
        name: ParserRuleContext?,
        params: RellParser.RuleX_FormalParametersContext?
    ): Position = when {
        params != null -> Position(params.stop.line - 1, params.stop.charPositionInLine + 1)
        name != null -> Position(name.stop.line - 1, name.stop.charPositionInLine + 1)
        else -> Position(context.stop.line - 1, context.stop.charPositionInLine)
    }
}
