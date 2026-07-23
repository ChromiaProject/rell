/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.rules

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import net.postchain.rell.toolbox.linter.LinterOptions
import org.junit.jupiter.api.Test

class RedundantBooleanComparisonRuleTest : AbstractRuleTest() {
    private val fileName = "redundant_boolean_comparison.rell"

    @Test
    fun `should be disabled when enabled is false`() {
        assertThat(lint(fileName, LinterOptions(enabled = false, ruleRedundantBooleanComparison = true))).isEmpty()
    }

    @Test
    fun `should be disabled when rule is null`() {
        assertThat(lint(fileName, LinterOptions(enabled = true, ruleRedundantBooleanComparison = null))).isEmpty()
    }

    @Test
    fun `should flag boolean-literal comparisons as diagnostics and exempt where-clauses`() {
        val result = lint(fileName, LinterOptions(enabled = true, ruleRedundantBooleanComparison = true))
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
