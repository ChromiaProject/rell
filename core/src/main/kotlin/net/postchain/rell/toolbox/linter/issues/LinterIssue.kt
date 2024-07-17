package net.postchain.rell.toolbox.linter.issues

import net.postchain.rell.toolbox.linter.LinterFix
import org.antlr.v4.runtime.ParserRuleContext

abstract class LinterIssue(val ctx: ParserRuleContext, val ruleId: String, val message: String) {
    abstract fun fix(): LinterFix?
}


