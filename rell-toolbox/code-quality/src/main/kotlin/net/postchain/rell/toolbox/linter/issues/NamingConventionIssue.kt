package net.postchain.rell.toolbox.linter.issues

import net.postchain.rell.toolbox.linter.LinterFix
import net.postchain.rell.toolbox.linter.LinterIssue
import org.antlr.v4.runtime.ParserRuleContext

class NamingConventionIssue(ctx: ParserRuleContext, ruleId: String, message: String) :
    LinterIssue(ctx, ruleId, message) {
    override fun fix(): LinterFix? {
        return null
        // TODO: automatic renaming of identifiers could lead to unintended results, discuss it and decide accordingly
        // val name = ctx.start.text
        // val fixedName = toSnakeCase(name)
        // return LinterFix(ctx.start.line - 1, ctx.start.charPositionInLine, name.length, fixedName)
    }

//    private fun toSnakeCase(input: String): String {
//        val builder = StringBuilder()
//        var prevCharWasUpperCase = false
//
//        for (char in input) {
//            if (char.isUpperCase() && !prevCharWasUpperCase && builder.isNotEmpty()) {
//                builder.append('_')
//            }
//            builder.append(char.lowercaseChar())
//            prevCharWasUpperCase = char.isUpperCase()
//        }
//
//        return builder.toString()
//    }
}
