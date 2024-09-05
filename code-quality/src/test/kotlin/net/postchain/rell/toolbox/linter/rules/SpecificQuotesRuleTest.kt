package net.postchain.rell.toolbox.linter.rules

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import net.postchain.rell.toolbox.linter.LinterFix
import net.postchain.rell.toolbox.linter.LinterOptions
import net.postchain.rell.toolbox.linter.Quote
import org.junit.jupiter.api.Test

class SpecificQuotesRuleTest : AbstractRuleTest() {
    @Test
    fun `should be disabled when enabled is false`() {
        assertThat(lint("quotes.rell", LinterOptions(enabled = false, ruleQuoteFormat = Quote.DOUBLE))).isEmpty()
    }

    @Test
    fun `should be disabled when rule is null`() {
        assertThat(lint("quotes.rell", LinterOptions(enabled = true, ruleQuoteFormat = null))).isEmpty()
    }

    @Test
    fun `should find strings which are not double quote`() {
        val result = lint("quotes.rell", LinterOptions(enabled = true, ruleQuoteFormat = Quote.DOUBLE))
        assertThat(result).hasSize(3)
        assertThat(result[0]).matches(
            2, 13, SpecificQuotesRule.RULE_ID, "Use double quotes for 'hello'",
            LinterFix(1, 12, 7, "\"hello\"")
        )
        assertThat(result[1]).matches(
            3, 13, SpecificQuotesRule.RULE_ID, "Use double quotes for 'with \"quote\" inside'",
            LinterFix(2, 12, 21, "\"with \\\"quote\\\" inside\"")
        )
        assertThat(result[2]).matches(
            4, 13, SpecificQuotesRule.RULE_ID, "Use double quotes for 'with escaped \\\"quote\\\" inside'",
            LinterFix(3, 12, 31, "\"with escaped \\\"quote\\\" inside\"")
        )
    }

    @Test
    fun `should find strings which are not single quote`() {
        val result = lint("quotes.rell", LinterOptions(enabled = true, ruleQuoteFormat = Quote.SINGLE))
        assertThat(result).hasSize(3)
        assertThat(result[0]).matches(
            8, 13, SpecificQuotesRule.RULE_ID, "Use single quotes for \"world\"",
            LinterFix(7, 12, 7, "'world'")
        )
        assertThat(result[1]).matches(
            9, 13, SpecificQuotesRule.RULE_ID, "Use single quotes for \"with 'quote' inside\"",
            LinterFix(8, 12, 21, "'with \\'quote\\' inside'")
        )
        assertThat(result[2]).matches(
            10, 13, SpecificQuotesRule.RULE_ID, "Use single quotes for \"with escaped \\'quote\\' inside\"",
            LinterFix(9, 12, 31, "'with escaped \\'quote\\' inside'")
        )
    }
}
