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

class PreferValRuleTest : AbstractRuleTest() {
    private val fileName = "prefer_val.rell"

    @Test
    fun `should be disabled when enabled is false`() {
        assertThat(lint(fileName, LinterOptions(enabled = false, rulePreferVal = true))).isEmpty()
    }

    @Test
    fun `should be disabled when rule is null`() {
        assertThat(lint(fileName, LinterOptions(enabled = true, rulePreferVal = null))).isEmpty()
    }

    @Test
    fun `should flag only vars that are never reassigned`() {
        val result = lint(fileName, LinterOptions(enabled = true, rulePreferVal = true))
        assertThat(result).hasSize(1)
        assertThat(result[0].ruleId).isEqualTo(PreferValRule.RULE_ID)
        assertThat(result[0].message).isEqualTo("Variable 'e' is never reassigned; use 'val'")
        assertThat(result[0].fix()!!.newText).isEqualTo("val")
        assertThat(result[0].ctx.start.line).isEqualTo(22)
    }
}
