/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter

import net.postchain.rell.toolbox.indexer.RellIssueSeverity
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token

abstract class LinterIssue(
    val ctx: ParserRuleContext,
    val ruleId: String,
    val message: String,
    /**
     * How loudly the issue surfaces. Style inspections stay at [RellIssueSeverity.WEAK_WARNING], so
     * they are visible without being intrusive and need no opt-in; rules that flag a likely defect
     * raise it to [RellIssueSeverity.WARNING].
     */
    val severity: RellIssueSeverity = RellIssueSeverity.WEAK_WARNING,
) {
    abstract fun fix(): LinterFix?

    /**
     * Token span the editor underlines. Defaults to the whole [ctx]; issues anchored on a
     * multi-line construct narrow it to the offending keyword (IDEA convention: 'if' for
     * replace-with-when, 'var' for constant detection).
     */
    open val highlightStart: Token get() = ctx.start
    // `stop` is null when the parser recovered from an error inside the context.
    open val highlightStop: Token get() = ctx.stop ?: ctx.start
}
