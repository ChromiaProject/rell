/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.editing

import net.postchain.rell.base.compiler.parser.antlr.RellParser
import net.postchain.rell.toolbox.formatter.FormatterOptions
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.parser.RellCustomTokenChannels
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.misc.Interval
import org.eclipse.lsp4j.*
import java.net.URI

/**
 * The `refactor.rewrite` pair converting a function/query body between its block form
 * (`{ return x; }` / `{ x; }`) and its expression form (`= x;`), mirroring Kotlin's
 * "Convert to expression body" / "Convert to block body" intentions.
 *
 * The conversion must never change the definition's (possibly inferred) return type, which
 * makes unit the interesting case: `return <unit-expr>;` is a compile error (`stmt_return_unit`),
 * so a unit expression body becomes `{ expr; }`, and only a `{ call(); }` block whose call is
 * known to return unit may become `= call();` (for a non-unit call the block form discards the
 * value while the expression form would silently change the inferred signature). When the
 * semantic info needed for that judgement is missing — a broken file — no action is offered.
 */
internal object BodyConversionService {
    const val TO_EXPRESSION_TITLE = "Convert to expression body"
    const val TO_BLOCK_TITLE = "Convert to block body"

    private const val UNIT_TYPE = "unit"

    fun getCodeActions(
        fileUri: URI,
        range: Range,
        resource: Resource,
        formatterOptions: FormatterOptions,
    ): List<CodeAction> {
        val def = findEnclosingDefinition(resource.parseTree, range) ?: return listOf()
        val action = when (def) {
            is RellParser.FunctionDefContext -> def.functionBody()?.let { body ->
                val block = body.blockStmt()
                val expr = body.expression()
                when {
                    block != null -> toExpressionBody(fileUri, resource, block)
                    // The `;`-only alternative is an abstract body: nothing to convert.
                    expr != null ->
                        toBlockBody(fileUri, def, body, expr, functionReturnsUnit(def, resource), formatterOptions)

                    else -> null
                }
            }

            is RellParser.QueryDefContext -> def.queryBody()?.let { body ->
                val block = body.blockStmt()
                val expr = body.expression()
                when {
                    block != null -> toExpressionBody(fileUri, resource, block)
                    // A query must return a value, so its block form always uses `return`.
                    expr != null -> toBlockBody(fileUri, def, body, expr, returnsUnit = false, formatterOptions)
                    else -> null
                }
            }

            else -> null
        }
        return listOfNotNull(action)
    }

    /**
     * Operations only allow block bodies, so only functions and queries are conversion targets;
     * definitions never nest in Rell, so the first match found while descending is the only one.
     */
    private fun findEnclosingDefinition(ctx: ParserRuleContext, range: Range): ParserRuleContext? {
        if (!contains(ctx, range)) return null
        for (i in 0 until ctx.childCount) {
            val child = ctx.getChild(i) as? ParserRuleContext ?: continue
            findEnclosingDefinition(child, range)?.let { return it }
        }
        return ctx.takeIf { it is RellParser.FunctionDefContext || it is RellParser.QueryDefContext }
    }

    private fun toExpressionBody(fileUri: URI, resource: Resource, block: RellParser.BlockStmtContext): CodeAction? {
        val statement = block.statement().singleOrNull() ?: return null
        val expression: ParserRuleContext = when (statement) {
            is RellParser.ReturnStmtAltContext -> statement.expression() ?: return null

            is RellParser.ExprStmtAltContext -> {
                // A trailing expression() means an assignment (`x = y;`), which is not convertible.
                val call = statement.baseExpr()?.takeIf { statement.expression() == null } ?: return null
                call.takeIf { isUnitCall(it, resource) } ?: return null
            }

            else -> return null
        }
        // Comments between the braces but outside the expression have no place in the converted
        // body; refuse the conversion instead of silently dropping them.
        if (hasCommentsOutside(resource, block, expression)) return null
        if (isBareJump(expression)) return null

        val newText = "= ${sourceText(expression)};"
        return rewriteAction(TO_EXPRESSION_TITLE, fileUri, tokenRange(block.start, block.stop), newText)
    }

    private fun toBlockBody(
        fileUri: URI,
        def: ParserRuleContext,
        body: ParserRuleContext,
        expression: RellParser.ExpressionContext,
        returnsUnit: Boolean?,
        formatterOptions: FormatterOptions,
    ): CodeAction? {
        if (returnsUnit == null || isBareJump(expression)) return null
        val newLine = formatterOptions.newLineString
        val baseIndent = lineIndent(def)
        val step = if (formatterOptions.insertSpaces) " ".repeat(formatterOptions.tabSize) else "\t"
        val statement = if (returnsUnit) "${sourceText(expression)};" else "return ${sourceText(expression)};"
        val newText = "{$newLine$baseIndent$step$statement$newLine$baseIndent}"
        return rewriteAction(TO_BLOCK_TITLE, fileUri, tokenRange(body.start, body.stop), newText)
    }

    /**
     * Whether the function's declared or inferred return type is unit; null when the file's
     * semantic info cannot answer, in which case no conversion is offered.
     */
    private fun functionReturnsUnit(def: RellParser.FunctionDefContext, resource: Resource): Boolean? {
        val type = def.type()
        if (type != null) return type.text == UNIT_TYPE
        val nameToken = def.qualifiedName()?.stop ?: return null
        return symbolResultType(resource, nameToken)?.let { it == UNIT_TYPE }
    }

    /**
     * Whether the expression is a call that is known to return unit. Only a plain or member call
     * with a resolvable callee qualifies: anything else either cannot be unit (literals,
     * operators, `create`) or cannot be judged, and both mean "do not convert".
     */
    private fun isUnitCall(expr: RellParser.BaseExprContext, resource: Resource): Boolean {
        if (expr.childCount < 2 || expr.getChild(expr.childCount - 1) !is RellParser.CallArgsContext) return false

        val calleeToken = when (val callee = expr.getChild(expr.childCount - 2)) {
            is RellParser.NameExprContext -> callee.qualifiedName()?.stop
            is RellParser.BaseExprTailMemberContext -> callee.RULE_ID()?.symbol
            else -> null
        } ?: return false

        return symbolResultType(resource, calleeToken) == UNIT_TYPE
    }

    /**
     * Whether the expression is nothing but a jump expression (`return x`, `break`, `continue`).
     * A jump embedded in a larger expression (`x ?: return 0`) converts fine, but a body that
     * *is* a jump would come out as the absurd `{ return return 1; }` (and back), so neither
     * direction is offered. Jumps have the bottom type, so no return type can depend on them.
     */
    private fun isBareJump(expression: ParserRuleContext): Boolean {
        var ctx: ParserRuleContext = expression
        while (ctx.childCount == 1) {
            if (ctx is RellParser.JumpExprContext) return true
            ctx = ctx.getChild(0) as? ParserRuleContext ?: return false
        }
        return ctx is RellParser.JumpExprContext
    }

    private fun symbolResultType(resource: Resource, token: Token): String? {
        val info = resource.locationInfo[Interval(token.startIndex, token.stopIndex)]?.ideSymbolInfo
        return info?.doc?.declaration?.completion?.result
    }

    private fun hasCommentsOutside(resource: Resource, block: ParserRuleContext, expression: ParserRuleContext): Boolean {
        for (i in block.start.tokenIndex + 1 until block.stop.tokenIndex) {
            if (i in expression.start.tokenIndex..expression.stop.tokenIndex) continue
            if (resource.tokenStream.get(i).channel == RellCustomTokenChannels.COMMENTS.channel) return true
        }
        return false
    }

    private fun rewriteAction(title: String, fileUri: URI, range: Range, newText: String): CodeAction {
        val action = CodeAction(title)
        action.kind = CodeActionKind.RefactorRewrite
        action.isPreferred = false
        action.edit = WorkspaceEdit(mapOf(fileUri.toString() to listOf(TextEdit(range, newText))))
        return action
    }

    private fun contains(ctx: ParserRuleContext, range: Range): Boolean {
        val start = ctx.start ?: return false
        val stop = ctx.stop ?: return false
        val ctxStart = Position(start.line - 1, start.charPositionInLine)
        val ctxEnd = Position(stop.line - 1, stop.charPositionInLine + (stop.text?.length ?: 0))
        return compare(ctxStart, range.start) <= 0 && compare(range.end, ctxEnd) <= 0
    }

    private fun compare(a: Position, b: Position): Int =
        if (a.line != b.line) a.line - b.line else a.character - b.character

    private fun sourceText(ctx: ParserRuleContext): String =
        ctx.start.inputStream.getText(Interval(ctx.start.startIndex, ctx.stop.stopIndex))

    private fun tokenRange(start: Token, stop: Token): Range = Range(
        Position(start.line - 1, start.charPositionInLine),
        Position(stop.line - 1, stop.charPositionInLine + (stop.text?.length ?: 0)),
    )

    /** The whitespace prefix of the definition's first line, preserving tabs. */
    private fun lineIndent(def: ParserRuleContext): String {
        val start = def.start
        val lineStartIndex = start.startIndex - start.charPositionInLine
        if (start.startIndex <= lineStartIndex) return ""
        val prefix = start.inputStream.getText(Interval(lineStartIndex, start.startIndex - 1))
        return prefix.takeWhile { it == ' ' || it == '\t' }
    }
}
