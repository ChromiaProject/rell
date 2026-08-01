/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.rules

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import net.postchain.rell.toolbox.linter.LinterIssue
import net.postchain.rell.toolbox.testing.testLinterOptions
import org.junit.jupiter.api.Test

class PreferNullCheckOperatorRuleTest : AbstractRuleTest() {
    private val fileName = "prefer_null_check_operator.rell"
    private val message = "Replace '!= null' with the null-check operator '??'"

    @Test
    fun `should be disabled when enabled is false`() {
        assertThat(lint(fileName, testLinterOptions(enabled = false) { rulePreferNullCheckOperator = true })).isEmpty()
    }

    @Test
    fun `should be disabled when rule is null`() {
        assertThat(lint(fileName, testLinterOptions { rulePreferNullCheckOperator = null })).isEmpty()
    }

    @Test
    fun `should suggest the null-check operator for verbose comparisons`() {
        val result = lint(fileName, testLinterOptions { rulePreferNullCheckOperator = true })

        assertThat(result.map { it.fix()!!.newText }).containsExactly(
            "x??",
            "x??",
            "x??",
            "a.balance??",
            "find()??",
            "x??",
            "a??",
            "x??",
        )
        assertThat(result.map { it.ruleId }.distinct()).containsExactly(PreferNullCheckOperatorRule.RULE_ID)
        assertThat(result.map { it.message }.distinct()).containsExactly(message)
        assertThat(result.map { it.line() }).containsExactly(6, 9, 12, 18, 21, 42, 45, 49)
        // The whole comparison is replaced, `null != x` included.
        assertThat(result[1]).highlightsRange(9, 12, 9, 21)
    }

    @Test
    fun `should yield to the elvis and safe-access rewrites of the whole if`() {
        val result = lint(fileName, testLinterOptions {
            rulePreferNullCheckOperator = true
            ruleSimplifyNullableIf = true
        }).filter { it.ruleId == PreferNullCheckOperatorRule.RULE_ID }

        // Lines 42 and 45 are gone: there the whole 'if' becomes '?:' / '?.'.
        assertThat(result.map { it.line() }).containsExactly(6, 9, 12, 18, 21, 49)
    }

    private fun LinterIssue.line() = ctx.start.line
}
