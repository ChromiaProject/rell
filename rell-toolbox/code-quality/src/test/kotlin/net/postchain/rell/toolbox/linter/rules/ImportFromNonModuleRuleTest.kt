package net.postchain.rell.toolbox.linter.rules

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import net.postchain.rell.toolbox.linter.LinterOptions
import org.junit.jupiter.api.Test

class ImportFromNonModuleRuleTest : AbstractRuleTest() {
    @Test
    fun `should be disabled when enabled is false`() {
        assertThat(lint("non_module.rell", LinterOptions(enabled = false, ruleImportFromNonModule = true))).isEmpty()
    }

    @Test
    fun `should be disabled when rule is false`() {
        assertThat(lint("non_module.rell", LinterOptions(enabled = true, ruleImportFromNonModule = false))).isEmpty()
    }

    @Test
    fun `should be disabled when rule is null`() {
        assertThat(lint("non_module.rell", LinterOptions(enabled = true, ruleImportFromNonModule = null))).isEmpty()
    }

    @Test
    fun `should find imports in non-modules`() {
        val result = lint(
            "non_module.rell",
            LinterOptions(enabled = true, ruleImportFromNonModule = true),
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
        val result = lint("module.rell", LinterOptions(enabled = true, ruleImportFromNonModule = true))
        assertThat(result).isEmpty()
    }
}
