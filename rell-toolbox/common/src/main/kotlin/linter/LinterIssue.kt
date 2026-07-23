/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter

import net.postchain.rell.toolbox.indexer.RellIssueSeverity
import org.antlr.v4.runtime.ParserRuleContext

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
}
