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

class AtCardinalityMisuseRuleTest : AbstractRuleTest() {
    private val fileName = "at_cardinality_misuse.rell"

    @Test
    fun `should be disabled when enabled is false`() {
        assertThat(lint(fileName, LinterOptions(enabled = false, ruleAtCardinalityMisuse = true))).isEmpty()
    }

    @Test
    fun `should be disabled when rule is null`() {
        assertThat(lint(fileName, LinterOptions(enabled = true, ruleAtCardinalityMisuse = null))).isEmpty()
    }

    @Test
    fun `should flag cardinality misuse with a fix only for the trailing-free not-null form`() {
        val result = lint(fileName, LinterOptions(enabled = true, ruleAtCardinalityMisuse = true))
        assertThat(result).hasSize(6)

        for (issue in result) {
            assertThat(issue.ruleId).isEqualTo(AtCardinalityMisuseRule.RULE_ID)
        }

        // (b) @? {...}!! -> @ {...}  (semantics-preserving, auto-fixed)
        assertThat(result[0].message).isEqualTo("Replace '@? {...}!!' with '@ {...}'")
        assertThat(result[0].fix()!!.newText).isEqualTo("user @ { .name == 'a' }")

        // (b) with a trailing method call: reported without a fix (the naive fix would be broken).
        assertThat(result[1].message).isEqualTo("Replace '@? {...}!!' with '@ {...}'")
        assertThat(result[1].fix()).isNull()

        // (c) indexing @* with [0]
        assertThat(result[2].message).isEqualTo("Indexing a '@*'/'@+' result with [0]; use '@' or '@?'")
        assertThat(result[2].fix()).isNull()

        // (d) size()==0 existence check on @*
        assertThat(result[3].fix()).isNull()
        // (d) .empty() existence check on @*
        assertThat(result[4].fix()).isNull()
        // (e) @+ emptiness check is always false
        assertThat(result[5].message).isEqualTo("'@+' always returns a non-empty list; the check is redundant")
        assertThat(result[5].fix()).isNull()
    }
}
