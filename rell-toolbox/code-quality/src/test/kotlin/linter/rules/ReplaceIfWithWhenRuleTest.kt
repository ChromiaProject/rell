/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.rules

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import net.postchain.rell.toolbox.linter.LinterFix
import net.postchain.rell.toolbox.testing.testLinterOptions
import org.junit.jupiter.api.Test

class ReplaceIfWithWhenRuleTest : AbstractRuleTest() {
    @Test
    fun `should be disabled when enabled is false`() {
        assertThat(
            lint("replace_if_with_when.rell", testLinterOptions(enabled = false) { ruleReplaceIfWithWhen = true })
        ).isEmpty()
    }

    @Test
    fun `should be disabled when rule is null`() {
        assertThat(
            lint("replace_if_with_when.rell", testLinterOptions { ruleReplaceIfWithWhen = null })
        ).isEmpty()
    }

    @Test
    fun `should report if-else-if chains with a when replacement`() {
        val result = lint("replace_if_with_when.rell", testLinterOptions { ruleReplaceIfWithWhen = true })
        assertThat(result).hasSize(4)

        val multiLineWhen = "when {\n" +
            "        input == 'a' -> {\n" +
            "            return 1;\n" +
            "        }\n" +
            "        input == 'b' -> {\n" +
            "            return 2;\n" +
            "        }\n" +
            "        else -> {\n" +
            "            return 3;\n" +
            "        }\n" +
            "    }"
        assertThat(result[0]).matches(
            2,
            5,
            ReplaceIfWithWhenRule.RULE_ID,
            "Replace 'if' with 'when'",
            LinterFix(1, 4, 123, multiLineWhen, 7, 5)
        )

        val singleLineWhen = "when {\n" +
            "        x == 1 -> print('one');\n" +
            "        x == 2 -> print('two');\n" +
            "    }"
        assertThat(result[1]).matches(
            12,
            5,
            ReplaceIfWithWhenRule.RULE_ID,
            "Replace 'if' with 'when'",
            LinterFix(11, 4, 56, singleLineWhen, 11, 60)
        )

        val exprWhen = "when {\n" +
            "                a == 1 -> 1;\n" +
            "                a == 2 -> 0;\n" +
            "                else -> 3;\n" +
            "            }"
        assertThat(result[2]).matches(
            25,
            13,
            ReplaceIfWithWhenRule.RULE_ID,
            "Replace 'if' with 'when'",
            LinterFix(24, 12, 39, exprWhen, 24, 51)
        )

        val blockArmWhen = "when {\n" +
            "               a == 1 -> { 1 }\n" +
            "               a == 2 -> 2;\n" +
            "               else -> 3;\n" +
            "           }"
        assertThat(result[3]).matches(
            30,
            12,
            ReplaceIfWithWhenRule.RULE_ID,
            "Replace 'if' with 'when'",
            LinterFix(29, 11, 43, blockArmWhen, 29, 54)
        )
    }
}
