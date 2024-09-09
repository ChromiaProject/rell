package net.postchain.rell.toolbox.indexer

import net.postchain.rell.toolbox.linter.LinterFix
import net.postchain.rell.toolbox.linter.LinterIssue
import org.antlr.v4.runtime.ParserRuleContext

class TestLinterIssue(ctx: ParserRuleContext, ruleId: String, message: String) : LinterIssue(ctx, ruleId, message) {
    override fun fix(): LinterFix? {
        return null
    }
}
