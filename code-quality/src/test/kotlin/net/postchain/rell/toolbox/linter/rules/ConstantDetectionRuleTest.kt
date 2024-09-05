package net.postchain.rell.toolbox.linter.rules

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import net.postchain.rell.toolbox.linter.LinterOptions
import org.junit.jupiter.api.Test

class ConstantDetectionRuleTest : AbstractRuleTest() {
    @Test
    fun `should be disabled when enabled is false`() {
        assertThat(lint("constant.rell", LinterOptions(enabled = false, ruleConstantDetection = true))).isEmpty()
    }

    @Test
    fun `should be disabled when rule is false`() {
        assertThat(lint("constant.rell", LinterOptions(enabled = true, ruleConstantDetection = false))).isEmpty()
    }

    @Test
    fun `should be disabled when rule is null`() {
        assertThat(lint("constant.rell", LinterOptions(enabled = true, ruleConstantDetection = null))).isEmpty()
    }

    @Test
    fun `should find vars which are never modified`() {
        val result = lint("constant.rell", LinterOptions(enabled = true, ruleConstantDetection = true))
        assertThat(result).hasSize(8)
        assertThat(result[0]).matches(
            2,
            9,
            ConstantDetectionRule.RULE_ID,
            "Variable 'x' is never modified, so it can be declared using 'val'"
        )
        assertThat(result[1]).matches(
            3,
            9,
            ConstantDetectionRule.RULE_ID,
            "Variable 'y' is never modified, so it can be declared using 'val'"
        )
        assertThat(result[2]).matches(
            10,
            9,
            ConstantDetectionRule.RULE_ID,
            "Variable 'last_one' is never modified, so it can be declared using 'val'"
        )
        assertThat(result[3]).matches(12, 10, ConstantDetectionRule.RULE_ID, "Variable 'a' is never modified")
        assertThat(result[4]).matches(12, 13, ConstantDetectionRule.RULE_ID, "Variable 'b' is never modified")
        assertThat(result[5]).matches(14, 10, ConstantDetectionRule.RULE_ID, "Variable 'f' is never modified")
        assertThat(result[6]).matches(14, 14, ConstantDetectionRule.RULE_ID, "Variable 'q' is never modified")
        assertThat(result[7]).matches(14, 17, ConstantDetectionRule.RULE_ID, "Variable 'n' is never modified")
    }
}
