/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.rules

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import net.postchain.rell.toolbox.testing.testLinterOptions
import org.junit.jupiter.api.Test

class ImportFromNonModuleRuleTest : AbstractRuleTest() {
    @Test
    fun `should be disabled when enabled is false`() {
        assertThat(lint("non_module.rell", testLinterOptions(enabled = false) { ruleImportFromNonModule = true })).isEmpty()
    }

    @Test
    fun `should be disabled when rule is false`() {
        assertThat(lint("non_module.rell", testLinterOptions { ruleImportFromNonModule = false })).isEmpty()
    }

    @Test
    fun `should be disabled when rule is null`() {
        assertThat(lint("non_module.rell", testLinterOptions { ruleImportFromNonModule = null })).isEmpty()
    }

    @Test
    fun `should find imports in non-modules`() {
        val result = lint(
            "non_module.rell",
            testLinterOptions { ruleImportFromNonModule = true },
            listOf(
                "bogus.rell",
                "ok.rell"
            )
        )
        assertThat(result).hasSize(1)
        assertThat(result[0]).matches(
            1,
            1,
            ImportFromNonModuleRule.RULE_ID,
            "Move import to 'module.rell'"
        )
    }

    @Test
    fun `should not find imports in modules`() {
        val result = lint("module.rell", testLinterOptions { ruleImportFromNonModule = true })
        assertThat(result).isEmpty()
    }
}
