/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.rules

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import net.postchain.rell.toolbox.testing.testLinterOptions
import org.junit.jupiter.api.Test

class RedundantBooleanComparisonRuleTest : AbstractRuleTest() {
    private val fileName = "redundant_boolean_comparison.rell"

    @Test
    fun `should be disabled when enabled is false`() {
        assertThat(lint(fileName, testLinterOptions(enabled = false) { ruleRedundantBooleanComparison = true })).isEmpty()
    }

    @Test
    fun `should be disabled when rule is null`() {
        assertThat(lint(fileName, testLinterOptions { ruleRedundantBooleanComparison = null })).isEmpty()
    }

    @Test
    fun `should highlight the whole comparison expression`() {
        val result = lint(fileName, testLinterOptions { ruleRedundantBooleanComparison = true })
        assertThat(result).hasSize(4)
        // `active == false` on line 2.
        assertThat(result[0]).highlightsRange(2, 13, 2, 28)
    }

    @Test
    fun `should flag boolean-literal comparisons as diagnostics and exempt where-clauses`() {
        val result = lint(fileName, testLinterOptions { ruleRedundantBooleanComparison = true })
        assertThat(result).hasSize(4)

        for (issue in result) {
            assertThat(issue.ruleId).isEqualTo(RedundantBooleanComparisonRule.RULE_ID)
        }

        // Diagnostic-only: no auto-fix, because the rewrite is unsafe on nullable-boolean operands.
        for (issue in result) {
            assertThat(issue.fix()).isNull()
        }

        assertThat(result[0].message).isEqualTo("Redundant comparison with 'false'")
        assertThat(result[1].message).isEqualTo("Redundant comparison with 'true'")
    }
}
