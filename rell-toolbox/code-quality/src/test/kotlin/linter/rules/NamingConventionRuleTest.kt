/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.rules

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import net.postchain.rell.toolbox.testing.testLinterOptions
import org.junit.jupiter.api.Test

class NamingConventionRuleTest : AbstractRuleTest() {
    @Test
    fun `should be disabled when enabled is false`() {
        assertThat(lint("constant.rell", testLinterOptions(enabled = false) { ruleNamingConvention = true })).isEmpty()
    }

    @Test
    fun `should be disabled when rule is false`() {
        assertThat(lint("constant.rell", testLinterOptions { ruleNamingConvention = false })).isEmpty()
    }

    @Test
    fun `should be disabled when rule is null`() {
        assertThat(lint("constant.rell", testLinterOptions { ruleNamingConvention = null })).isEmpty()
    }

    @Test
    fun `should find identifiers which are not snake_case`() {
        val result = lint("naming.rell", testLinterOptions { ruleNamingConvention = true })
        assertThat(result).hasSize(2)
        assertThat(result[0]).matches(1, 10, NamingConventionRule.RULE_ID, "'FooBar' should be in snake case")
        assertThat(result[1]).matches(5, 9, NamingConventionRule.RULE_ID, "'Bar' should be in snake case")
    }
}
