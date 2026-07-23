/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.rules

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import net.postchain.rell.toolbox.linter.LinterOptions
import org.junit.jupiter.api.Test

class PreferEmptyRuleTest : AbstractRuleTest() {
    private val fileName = "prefer_empty.rell"

    @Test
    fun `should be disabled when enabled is false`() {
        assertThat(lint(fileName, LinterOptions(enabled = false, rulePreferEmpty = true))).isEmpty()
    }

    @Test
    fun `should be disabled when rule is null`() {
        assertThat(lint(fileName, LinterOptions(enabled = true, rulePreferEmpty = null))).isEmpty()
    }

    @Test
    fun `should flag size comparisons and skip non-emptiness checks`() {
        val result = lint(fileName, LinterOptions(enabled = true, rulePreferEmpty = true))
        assertThat(result).hasSize(6)

        for (issue in result) {
            assertThat(issue.ruleId).isEqualTo(PreferEmptyRule.RULE_ID)
        }

        assertThat(result[0].fix()!!.newText).isEqualTo("xs.empty()")
        assertThat(result[1].fix()!!.newText).isEqualTo("not xs.empty()")
        assertThat(result[2].fix()!!.newText).isEqualTo("not xs.empty()")
        assertThat(result[3].fix()!!.newText).isEqualTo("xs.empty()")
        assertThat(result[4].fix()!!.newText).isEqualTo("xs.empty()")
        assertThat(result[5].fix()!!.newText).isEqualTo("not xs.empty()")
    }
}
