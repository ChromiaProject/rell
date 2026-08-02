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

class PreferAtProjectionRuleTest : AbstractRuleTest() {
    private val fileName = "prefer_at_projection.rell"

    @Test
    fun `should be disabled when enabled is false`() {
        assertThat(lint(fileName, testLinterOptions(enabled = false) { rulePreferAtProjection = true })).isEmpty()
    }

    @Test
    fun `should be disabled when rule is null`() {
        assertThat(lint(fileName, testLinterOptions { rulePreferAtProjection = null })).isEmpty()
    }

    @Test
    fun `should flag unprojected entity loops and stay silent on whole-entity uses`() {
        val result = lint(fileName, testLinterOptions { rulePreferAtProjection = true })
        assertThat(result).hasSize(6)

        for (issue in result) {
            assertThat(issue.ruleId).isEqualTo(PreferAtProjectionRule.RULE_ID)
            assertThat(issue.fix()).isNull()
        }

        // bad_direct: plain @* loop.
        assertThat(result[0].message).isEqualTo(message("*", ".value"))
        // bad_plus: @+ cardinality.
        assertThat(result[1].message).isEqualTo(message("+", ".value"))
        // bad_self_projection: `(person)` what-clause is still an unprojected entity list.
        assertThat(result[2].message).isEqualTo(message("*", ".value"))
        // bad_aliased_from: `(q: person) @* {...}`.
        assertThat(result[3].message).isEqualTo(message("*", ".value"))
        // bad_via_val: at-result bound to a single-use val.
        assertThat(result[4].message).isEqualTo(message("*", ".value"))
        // bad_multi_attr: plain attribute and a reference path.
        assertThat(result[5].message).isEqualTo(message("*", ".name, .company.cname"))
    }

    // Every flagged loop in the resource iterates `p` over `person`; only cardinality and
    // projection vary.
    private fun message(cardinality: String, what: String): String =
        "Each 'p.<attr>' in the loop body runs a separate SQL query; " +
            "project the attributes in the at-expression instead: 'person @$cardinality {...} ($what)'"
}
