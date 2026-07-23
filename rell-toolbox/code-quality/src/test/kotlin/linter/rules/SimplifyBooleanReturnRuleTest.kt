/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.rules

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import net.postchain.rell.toolbox.testing.testLinterOptions
import org.junit.jupiter.api.Test

class SimplifyBooleanReturnRuleTest : AbstractRuleTest() {
    private val fileName = "simplify_boolean_return.rell"

    @Test
    fun `should be disabled when enabled is false`() {
        assertThat(lint(fileName, testLinterOptions(enabled = false) { ruleSimplifyBooleanReturn = true })).isEmpty()
    }

    @Test
    fun `should be disabled when rule is null`() {
        assertThat(lint(fileName, testLinterOptions { ruleSimplifyBooleanReturn = null })).isEmpty()
    }

    @Test
    fun `should simplify boolean-literal if statements and expressions`() {
        val result = lint(fileName, testLinterOptions { ruleSimplifyBooleanReturn = true })
        assertThat(result).hasSize(5)
        for (issue in result) {
            assertThat(issue.ruleId).isEqualTo(SimplifyBooleanReturnRule.RULE_ID)
        }

        assertThat(result[0].fix()!!.newText).isEqualTo("return x > 0;")
        assertThat(result[1].fix()!!.newText).isEqualTo("return not (x > 0);")
        assertThat(result[2].fix()!!.newText).isEqualTo("return x > 0;")
        assertThat(result[3].fix()!!.newText).isEqualTo("return x > 0;")
        assertThat(result[4].fix()!!.newText).isEqualTo("return not (x > 0);")
    }
}
