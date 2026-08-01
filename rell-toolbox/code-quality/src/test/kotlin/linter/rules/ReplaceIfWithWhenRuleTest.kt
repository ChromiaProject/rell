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
    fun `should highlight only the leading if keyword`() {
        val result = lint("replace_if_with_when.rell", testLinterOptions { ruleReplaceIfWithWhen = true })
        assertThat(result).hasSize(6)
        assertThat(result[0]).highlightsRange(2, 5, 2, 7)
        assertThat(result[1]).highlightsRange(12, 5, 12, 7)
    }

    @Test
    fun `should report if-else-if chains with a when replacement`() {
        val result = lint("replace_if_with_when.rell", testLinterOptions { ruleReplaceIfWithWhen = true })
        assertThat(result).hasSize(6)

        val multiLineWhen = "when {\n" +
            "        input == 'a' -> return 1;\n" +
            "        input == 'b' -> return 2;\n" +
            "        else -> return 3;\n" +
            "    }"
        assertThat(result[0]).matches(
            2,
            5,
            ReplaceIfWithWhenRule.RULE_ID,
            "Replace 'if' with 'when'",
            LinterFix("Replace 'if' with 'when'", 1, 4, 123, multiLineWhen, 7, 5)
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
            LinterFix("Replace 'if' with 'when'", 11, 4, 56, singleLineWhen, 11, 60)
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
            LinterFix("Replace 'if' with 'when'", 24, 12, 39, exprWhen, 24, 51)
        )

        val blockArmWhen = "when {\n" +
            "               a == 1 -> 1;\n" +
            "               a == 2 -> 2;\n" +
            "               else -> 3;\n" +
            "           }"
        assertThat(result[3]).matches(
            30,
            12,
            ReplaceIfWithWhenRule.RULE_ID,
            "Replace 'if' with 'when'",
            LinterFix("Replace 'if' with 'when'", 29, 11, 43, blockArmWhen, 29, 54)
        )
    }

    @Test
    fun `should keep block arms that a compact arm cannot express`() {
        val result = lint("replace_if_with_when.rell", testLinterOptions { ruleReplaceIfWithWhen = true })
        assertThat(result).hasSize(6)

        val stmtWhen = "when {\n" +
            "        x == 1 -> {\n" +
            "            val y = x + 1;\n" +
            "            print(y);\n" +
            "        }\n" +
            "        x == 2 -> {\n" +
            "            val z = x;\n" +
            "        }\n" +
            "        else -> print(3);\n" +
            "    }"
        assertThat(result[4]).matches(
            43,
            5,
            ReplaceIfWithWhenRule.RULE_ID,
            "Replace 'if' with 'when'",
            LinterFix("Replace 'if' with 'when'", 42, 4, 135, stmtWhen, 49, 5)
        )

        val exprWhen = "when {\n" +
            "               a == 1 -> { val b = a; b }\n" +
            "               a == 2 -> 2;\n" +
            "               else -> 3;\n" +
            "           }"
        assertThat(result[5]).matches(
            54,
            12,
            ReplaceIfWithWhenRule.RULE_ID,
            "Replace 'if' with 'when'",
            LinterFix("Replace 'if' with 'when'", 53, 11, 54, exprWhen, 53, 65)
        )
    }
}
