/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.inlayhints.visitor

import net.postchain.rell.base.compiler.parser.antlr.RellManualBaseVisitor
import net.postchain.rell.base.compiler.parser.antlr.RellManualParser
import net.postchain.rell.base.utils.ide.IdeSymbolInfo
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.lsp.inlayhints.RellInlayHintsProvider.Companion.createTypeInlayHint
import net.postchain.rell.toolbox.lsp.inlayhints.RellInlayHintsProvider.Companion.isInRange
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.misc.Interval
import org.eclipse.lsp4j.InlayHint
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range

/**
 * Inlay-hint visitor for inferred function/query return types.
 * Migrated from legacy `Rell.g4` to canonical `RellManual.g4`.
 */
class ReturnTypeHintsVisitor(
    private val resource: Resource,
    private val range: Range,
    private val hints: MutableList<InlayHint>
) : RellManualBaseVisitor<Unit>() {

    override fun visitFunctionDef(ctx: RellManualParser.FunctionDefContext) {
        val name = ctx.qualifiedName()
        processDefinition(ctx, ctx.type(), name, ctx.formalParameters())
        super.visitFunctionDef(ctx)
    }

    override fun visitQueryDef(ctx: RellManualParser.QueryDefContext) {
        // The query name is a bare RULE_ID terminal in the new grammar.
        val nameTerminal = ctx.RULE_ID()
        val nameCtx = nameTerminal?.let { TerminalAsContext(it.symbol.startIndex, it.symbol.stopIndex, it.symbol.line, it.symbol.charPositionInLine) }
        processDefinition(ctx, ctx.type(), nameCtx, ctx.formalParameters())
        super.visitQueryDef(ctx)
    }

    private fun processDefinition(
        context: ParserRuleContext,
        type: RellManualParser.TypeContext?,
        name: ParserRuleContext?,
        params: RellManualParser.FormalParametersContext?
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
        params: RellManualParser.FormalParametersContext?
    ): Position = when {
        params != null -> Position(params.stop.line - 1, params.stop.charPositionInLine + 1)
        name != null -> Position(name.stop.line - 1, name.stop.charPositionInLine + 1)
        else -> Position(context.stop.line - 1, context.stop.charPositionInLine)
    }
}

/**
 * Lightweight ParserRuleContext wrapper used to expose a single token's position
 * to `processDefinition`. The legacy `RuleX_NameContext` provided this surface
 * implicitly; the new grammar exposes the query name as a bare RULE_ID terminal.
 */
private class TerminalAsContext(
    startIndex: Int,
    stopIndex: Int,
    line: Int,
    charPos: Int
) : ParserRuleContext() {
    init {
        start = SyntheticToken(startIndex, line, charPos)
        stop = SyntheticToken(stopIndex, line, charPos)
    }
}

private class SyntheticToken(
    private val startIdx: Int,
    private val lineNum: Int,
    private val charPos: Int
) : org.antlr.v4.runtime.Token {
    override fun getText() = ""
    override fun getType() = 0
    override fun getLine() = lineNum
    override fun getCharPositionInLine() = charPos
    override fun getChannel() = 0
    override fun getTokenIndex() = -1
    override fun getStartIndex() = startIdx
    override fun getStopIndex() = startIdx
    override fun getTokenSource() = null
    override fun getInputStream() = null
}
