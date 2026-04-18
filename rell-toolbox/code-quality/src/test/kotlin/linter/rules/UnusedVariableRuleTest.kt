/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.rules

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import net.postchain.rell.toolbox.linter.LinterOptions
import org.junit.jupiter.api.Test

class UnusedVariableRuleTest : AbstractRuleTest() {
    @Test
    fun `should be disabled when enabled is false`() {
        assertThat(lint("unused_var.rell", LinterOptions(enabled = false, ruleUnusedVariable = true))).isEmpty()
    }

    @Test
    fun `should be disabled when rule is false`() {
        assertThat(lint("unused_var.rell", LinterOptions(enabled = true, ruleUnusedVariable = false))).isEmpty()
    }

    @Test
    fun `should be disabled when rule is null`() {
        assertThat(lint("unused_var.rell", LinterOptions(enabled = true, ruleUnusedVariable = null))).isEmpty()
    }

    @Test
    fun `should find vars which are never used`() {
        val result = lint("unused_var.rell", LinterOptions(enabled = true, ruleUnusedVariable = true))
        assertThat(result).hasSize(7)
        assertThat(result[0]).matches(3, 9, UnusedVariableRule.RULE_ID, "Variable 'y' is never used")
        assertThat(result[1]).matches(4, 9, UnusedVariableRule.RULE_ID, "Variable 'z' is never used")
        assertThat(result[2]).matches(9, 10, UnusedVariableRule.RULE_ID, "Variable 'a' is never used")
        assertThat(result[3]).matches(9, 13, UnusedVariableRule.RULE_ID, "Variable '_b' is never used")
        assertThat(result[4]).matches(11, 10, UnusedVariableRule.RULE_ID, "Variable 'f' is never used")
        assertThat(result[5]).matches(11, 14, UnusedVariableRule.RULE_ID, "Variable 'q' is never used")
        assertThat(result[6]).matches(11, 17, UnusedVariableRule.RULE_ID, "Variable 'n' is never used")
    }
}
