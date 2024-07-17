package net.postchain.rell.toolbox.linter.issues

import net.postchain.rell.toolbox.linter.LinterFix
import net.postchain.rell.toolbox.linter.Quote
import org.antlr.v4.runtime.ParserRuleContext

class SpecificQuotesIssue(ctx: ParserRuleContext, ruleId: String, message: String, val quote: Quote) :
    LinterIssue(ctx, ruleId, message) {
    override fun fix(): LinterFix {
        val string = ctx.start.text
        val literal = quote.literal
        val content = escapeQuotes(string.substring(1, string.length - 1), quote)
        val fixedString = "$literal$content$literal"
        return LinterFix(ctx.start.line - 1, ctx.start.charPositionInLine, string.length, fixedString)
    }

    private fun escapeQuotes(input: String, quote: Quote): String {
        val sb = StringBuilder()
        var i = 0
        val literal = quote.literal.first()
        while (i < input.length) {
            if (input[i] == '\\' && i + 1 < input.length && input[i + 1] == literal) {
                // Skip the next character since it's already escaped
                sb.append("\\" + literal)
                i += 2
            } else if (input[i] == literal && (i == 0 || input[i - 1] != '\\')) {
                // Escape the quote if it's not already escaped
                sb.append("\\" + literal)
                i++
            } else {
                // Append the current character
                sb.append(input[i])
                i++
            }
        }

        return sb.toString()
    }
}