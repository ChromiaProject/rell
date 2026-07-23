/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.rules

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import net.postchain.rell.toolbox.linter.LinterFix
import net.postchain.rell.toolbox.testing.testLinterOptions
import org.junit.jupiter.api.Test

class ConstantDetectionRuleTest : AbstractRuleTest() {
    @Test
    fun `should be disabled when enabled is false`() {
        assertThat(lint("constant.rell", testLinterOptions(enabled = false) { ruleConstantDetection = true })).isEmpty()
    }

    @Test
    fun `should be disabled when rule is false`() {
        assertThat(lint("constant.rell", testLinterOptions { ruleConstantDetection = false })).isEmpty()
    }

    @Test
    fun `should be disabled when rule is null`() {
        assertThat(lint("constant.rell", testLinterOptions { ruleConstantDetection = null })).isEmpty()
    }

    @Test
    fun `should find vars which are never modified`() {
        val result = lint("constant.rell", testLinterOptions { ruleConstantDetection = true })
        assertThat(result).hasSize(8)
        // A plain declarator is rewritten from `var` to `val`.
        assertThat(result[0]).matches(
            2,
            9,
            ConstantDetectionRule.RULE_ID,
            "Variable 'x' is never modified, so it can be declared using 'val'",
            LinterFix(1, 4, 3, "val", 1, 7)
        )
        assertThat(result[1]).matches(
            3,
            9,
            ConstantDetectionRule.RULE_ID,
            "Variable 'y' is never modified, so it can be declared using 'val'",
            LinterFix(2, 4, 3, "val", 2, 7)
        )
        assertThat(result[2]).matches(
            10,
            9,
            ConstantDetectionRule.RULE_ID,
            "Variable 'last_one' is never modified, so it can be declared using 'val'",
            LinterFix(9, 4, 3, "val", 9, 7)
        )
        // Tuple declarators share one `var` keyword, so they are reported without a fix.
        assertThat(result[3]).matches(12, 10, ConstantDetectionRule.RULE_ID, "Variable 'a' is never modified")
        assertThat(result[4]).matches(12, 13, ConstantDetectionRule.RULE_ID, "Variable 'b' is never modified")
        assertThat(result[5]).matches(14, 10, ConstantDetectionRule.RULE_ID, "Variable 'f' is never modified")
        assertThat(result[6]).matches(14, 14, ConstantDetectionRule.RULE_ID, "Variable 'q' is never modified")
        assertThat(result[7]).matches(14, 17, ConstantDetectionRule.RULE_ID, "Variable 'n' is never modified")
    }
}
