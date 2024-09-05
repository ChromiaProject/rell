package net.postchain.rell.toolbox.linter.rules

import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.toolbox.core.Position
import net.postchain.rell.toolbox.core.Range
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.parser.RellBaseVisitor
import net.postchain.rell.toolbox.linter.LinterContext
import net.postchain.rell.toolbox.linter.LinterIssue
import net.postchain.rell.toolbox.linter.LinterOptions
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token


abstract class LinterRule(
    val config: LinterOptions,
    val resource: Resource,
    protected val linterContext: LinterContext
) : RellBaseVisitor<Unit>() {

    abstract val ruleId: String

    fun report(issue: LinterIssue) {
        linterContext.addIssue(issue)
    }

    fun hasIgnoreCommentOnTop(token: Token): Boolean {
        return previousCommentRegion(token)?.text?.contains("rell-lint-disable-next-line") ?: false
    }

    fun isDisabled(feature: Boolean?): Boolean {
        return !config.enabled || (feature == null || !feature)
    }

    fun getRange(ctx: ParserRuleContext): Range {
        return Range(
            Position(ctx.start.line, ctx.start.charPositionInLine),
            Position(ctx.stop.line, ctx.stop.charPositionInLine)
        )
    }

    private fun previousCommentRegion(token: Token): Token? {
        val commonTokenStream = resource.tokenStream
        return commonTokenStream.getCommentLineAboveToken(token)
    }

    protected fun hasSemanticErrors(): Boolean {
        return resource.semanticErrors.firstOrNull {
            resource.fileUri.path.endsWith(
                "/" + it.pos.path().str()
            ) && it.type == C_MessageType.ERROR
        } != null
    }
}