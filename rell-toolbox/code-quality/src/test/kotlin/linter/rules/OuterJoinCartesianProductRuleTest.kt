/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.rules

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import net.postchain.rell.toolbox.linter.LinterOptions
import org.junit.jupiter.api.Test

class OuterJoinCartesianProductRuleTest : AbstractRuleTest() {
    @Test
    fun `should be disabled when enabled is false`() {
        assertThat(
            lint(
                "outer_join_cartesian_product.rell",
                LinterOptions(enabled = false, ruleOuterJoinCartesianProduct = true)
            )
        ).isEmpty()
    }

    @Test
    fun `should be disabled when rule is false`() {
        assertThat(
            lint(
                "outer_join_cartesian_product.rell",
                LinterOptions(enabled = true, ruleOuterJoinCartesianProduct = false)
            )
        ).isEmpty()
    }

    @Test
    fun `should be disabled when rule is null`() {
        assertThat(
            lint(
                "outer_join_cartesian_product.rell",
                LinterOptions(enabled = true, ruleOuterJoinCartesianProduct = null)
            )
        ).isEmpty()
    }

    @Test
    fun `should find outer joins without condition or with empty one`() {
        val result =
            lint(
                "outer_join_cartesian_product.rell",
                LinterOptions(enabled = true, ruleOuterJoinCartesianProduct = true)
            )
        assertThat(result).hasSize(2)
        assertThat(
            result[0]
        ).matches(
            20,
            5,
            OuterJoinCartesianProductRule.RULE_ID,
            "Missing 'outer join' condition. resulting in cartesian product"
        )
        assertThat(
            result[1]
        ).matches(
            25,
            5,
            OuterJoinCartesianProductRule.RULE_ID,
            "Missing 'outer join' condition. resulting in cartesian product"
        )
    }
}
