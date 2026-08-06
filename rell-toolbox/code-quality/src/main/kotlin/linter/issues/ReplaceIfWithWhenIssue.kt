/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.issues

import net.postchain.rell.base.compiler.parser.antlr.RellParser
import net.postchain.rell.toolbox.linter.LinterFix
import net.postchain.rell.toolbox.linter.LinterIssue
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.misc.Interval

internal sealed class ReplaceIfWithWhenIssue(
    ctx: ParserRuleContext,
    ruleId: String,
    message: String
) : LinterIssue(ctx, ruleId, message) {

    // The chain spans many lines; underline only the leading `if` keyword.
    final override val highlightStop: Token get() = ctx.start

    final override fun fix(): LinterFix {
        val start = ctx.start
        val stop = ctx.stop
        return LinterFix(
            title = "Replace 'if' with 'when'",
            line = start.line - 1,
            charPositionInLine = start.charPositionInLine,
            length = stop.stopIndex - start.startIndex + 1,
            newText = buildWhenText(),
            endLine = stop.line - 1,
            endCharPositionInLine = stop.charPositionInLine + stop.text.length,
        )
    }

    protected abstract fun buildWhenText(): String

    protected val indent: String
        get() = " ".repeat(ctx.start.charPositionInLine)

    protected fun sourceText(ctx: ParserRuleContext): String {
        return ctx.start.inputStream.getText(Interval.of(ctx.start.startIndex, ctx.stop.stopIndex))
    }

    // Arms sit one level deeper than the original `if`, so continuation lines
    // of multi-line conditions and blocks are shifted by one indent unit.
    protected fun reindent(text: String): String {
        return text.lines().mapIndexed { index, line ->
            if (index == 0 || line.isBlank()) line else INDENT_UNIT + line
        }.joinToString("\n")
    }
}

internal class ReplaceIfStmtWithWhenIssue(
    private val ifCtx: RellParser.IfStmtAltContext,
    ruleId: String,
    message: String
) : ReplaceIfWithWhenIssue(ifCtx, ruleId, message) {

    override fun buildWhenText(): String {
        val armIndent = indent + INDENT_UNIT
        val sb = StringBuilder("when {\n")
        var current = ifCtx
        while (true) {
            sb.append(armIndent)
                .append(reindent(sourceText(current.expression())))
                .append(" -> ")
                .append(armText(current.statement(0)))
                .append("\n")
            val elseBranch = if (current.statement().size > 1) current.statement(1) else null
            when (elseBranch) {
                null -> break
                is RellParser.IfStmtAltContext -> current = elseBranch
                else -> {
                    sb.append(armIndent)
                        .append("else -> ")
                        .append(armText(elseBranch))
                        .append("\n")
                    break
                }
            }
        }
        return sb.append(indent).append("}").toString()
    }

    // A block wrapping a single statement adds nothing in a `when` arm, so unwrap it. A variable
    // declaration keeps its block: without it the name would leak into the enclosing scope.
    private fun armText(stmt: RellParser.StatementContext): String =
        when (val single = (stmt as? RellParser.BlockStmtAltContext)?.blockStmt()?.statement()?.singleOrNull()) {
            null, is RellParser.VarStmtAltContext, is RellParser.EmptyStmtContext -> reindent(sourceText(stmt))
            // The unwrapped statement already sits at the arm's indentation, so it is not reindented.
            else -> sourceText(single)
        }
}

internal class ReplaceIfExprWithWhenIssue(
    private val ifCtx: RellParser.IfExprContext,
    ruleId: String,
    message: String
) : ReplaceIfWithWhenIssue(ifCtx, ruleId, message) {

    override fun buildWhenText(): String {
        val armIndent = indent + INDENT_UNIT
        val sb = StringBuilder("when {\n")
        var current = ifCtx
        while (true) {
            sb.append(armIndent)
                .append(reindent(sourceText(current.expression())))
                .append(" -> ")
                .append(armText(current.exprOrValueBlock(0)))
                .append("\n")
            val next = current.elseIfChainNext()
            if (next == null) {
                sb.append(armIndent)
                    .append("else -> ")
                    .append(armText(current.exprOrValueBlock(1)))
                    .append("\n")
                break
            }
            current = next
        }
        return sb.append(indent).append("}").toString()
    }

    // A `when` expression arm is ';'-terminated unless it is a value block. A value block holding
    // nothing but its result expression is unwrapped: the block buys nothing in an arm.
    private fun armText(branch: RellParser.ExprOrValueBlockContext): String {
        val block = branch.valueBlock() ?: return reindent(sourceText(branch)) + ";"
        val result = block.expression()
        // The unwrapped expression already sits at the arm's indentation, so it is not reindented.
        return if (result != null && block.statement().isEmpty()) sourceText(result) + ";" else reindent(sourceText(branch))
    }
}

/**
 * The `else` arm of an if-expression when it is a bare nested if-expression, i.e. the next
 * link of an if/else-if chain; null when the chain ends at this `if`.
 */
internal fun RellParser.IfExprContext.elseIfChainNext(): RellParser.IfExprContext? {
    val binary = exprOrValueBlock(1).expression()?.binaryExpr() ?: return null
    return if (binary.childCount == 1) binary.getChild(0) as? RellParser.IfExprContext else null
}

private const val INDENT_UNIT = "    "
