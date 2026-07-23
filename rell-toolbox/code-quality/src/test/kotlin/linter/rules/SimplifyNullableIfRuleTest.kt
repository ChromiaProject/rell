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

class SimplifyNullableIfRuleTest : AbstractRuleTest() {
    private val fileName = "simplify_nullable_if.rell"

    @Test
    fun `should be disabled when enabled is false`() {
        assertThat(lint(fileName, LinterOptions(enabled = false, ruleSimplifyNullableIf = true))).isEmpty()
    }

    @Test
    fun `should be disabled when rule is null`() {
        assertThat(lint(fileName, LinterOptions(enabled = true, ruleSimplifyNullableIf = null))).isEmpty()
    }

    @Test
    fun `should rewrite null guards to elvis and safe-access`() {
        val result = lint(fileName, LinterOptions(enabled = true, ruleSimplifyNullableIf = true))
        assertThat(result).hasSize(5)
        for (issue in result) {
            assertThat(issue.ruleId).isEqualTo(SimplifyNullableIfRule.RULE_ID)
        }

        assertThat(result[0].fix()!!.newText).isEqualTo("x ?: y")
        assertThat(result[1].fix()!!.newText).isEqualTo("a?.balance")
        assertThat(result[2].fix()!!.newText).isEqualTo("x ?: y")
        assertThat(result[3].fix()!!.newText).isEqualTo("(x ?: 0)")
        // Compound else arm is parenthesized so `?:` grouping is preserved.
        assertThat(result[4].fix()!!.newText).isEqualTo("flag ?: (a or b)")
    }
}
