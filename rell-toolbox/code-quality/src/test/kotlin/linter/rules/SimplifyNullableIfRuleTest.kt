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

class SimplifyNullableIfRuleTest : AbstractRuleTest() {
    private val fileName = "simplify_nullable_if.rell"

    @Test
    fun `should be disabled when enabled is false`() {
        assertThat(lint(fileName, testLinterOptions(enabled = false) { ruleSimplifyNullableIf = true })).isEmpty()
    }

    @Test
    fun `should be disabled when rule is null`() {
        assertThat(lint(fileName, testLinterOptions { ruleSimplifyNullableIf = null })).isEmpty()
    }

    @Test
    fun `should highlight only the if keyword`() {
        val result = lint(fileName, testLinterOptions { ruleSimplifyNullableIf = true })
        assertThat(result).hasSize(23)
        assertThat(result[0]).highlightsRange(6, 12, 6, 14)
    }

    @Test
    fun `should rewrite null guards to elvis and safe-access`() {
        val result = lint(fileName, testLinterOptions { ruleSimplifyNullableIf = true })
        assertThat(result).hasSize(23)
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

    @Test
    fun `should merge statement null guards into the declaration`() {
        val result = lint(fileName, testLinterOptions { ruleSimplifyNullableIf = true })

        assertThat(result[5].fix()!!.newText).isEqualTo("val x = find() ?: return null;")
        assertThat(result[6].fix()!!.newText).isEqualTo("val x = find() ?: return null;")
        assertThat(result[7].fix()!!.newText).isEqualTo("val x = find() ?: return 0;")
        assertThat(result[8].fix()!!.newText).isEqualTo("val v = item ?: continue;")
        // The fix would swallow the comment sitting between the declaration and the guard.
        assertThat(result[9].fix()).isNull()
        // A guard inside a value block, not a function body.
        assertThat(result[10].fix()!!.newText).isEqualTo("val v = o ?: return 0;")
    }

    @Test
    fun `should accept conditions spelled with the null-check operator`() {
        val result = lint(fileName, testLinterOptions { ruleSimplifyNullableIf = true })

        assertThat(result[11].fix()!!.newText).isEqualTo("x ?: y")
        assertThat(result[12].fix()!!.newText).isEqualTo("a?.balance")
        assertThat(result[13].fix()!!.newText).isEqualTo("val x = find() ?: return null;")
        assertThat(result[14].fix()!!.newText).isEqualTo("val x = find() ?: return 0;")
    }

    @Test
    fun `should merge positive guards into the following return`() {
        val result = lint(fileName, testLinterOptions { ruleSimplifyNullableIf = true })

        assertThat(result[15].fix()!!.newText).isEqualTo("return x ?: y;")
        assertThat(result[16].fix()!!.newText).isEqualTo("return a?.balance;")
        // The member may itself be nullable, so a non-null fallback gets no automatic fix.
        assertThat(result[17].fix()).isNull()
        // A null fallback makes the guard redundant.
        assertThat(result[18].fix()!!.newText).isEqualTo("return x;")
        // Compound fallback is parenthesized so `?:` grouping is preserved.
        assertThat(result[19].fix()!!.newText).isEqualTo("return flag ?: (a or b);")
        assertThat(result[20].fix()!!.newText).isEqualTo("return x ?: y;")
        assertThat(result[21].fix()!!.newText).isEqualTo("return x ?: y;")
        // The fix would swallow the comment sitting between the guard and the fallback.
        assertThat(result[22].fix()).isNull()
    }
}
