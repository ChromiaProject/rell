/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.editing

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LinterConfigEditorTest {

    @TempDir
    private lateinit var tempDir: File

    private val configFile: File
        get() = File(tempDir, ".rell_lint")

    @Test
    fun `should create the config with a section when missing`() {
        LinterConfigEditor.disableRule(configFile, "rule_prefer_empty")

        assertThat(configFile.readText()).isEqualTo("[*.rell]\nrule_prefer_empty=false\n")
    }

    @Test
    fun `should append to an existing config without duplicating the section header`() {
        configFile.writeText("[*.rell]\nrule_quote_format=double\n")

        LinterConfigEditor.disableRule(configFile, "rule_prefer_empty")

        assertThat(configFile.readText())
            .isEqualTo("[*.rell]\nrule_quote_format=double\nrule_prefer_empty=false\n")
    }

    @Test
    fun `should rewrite an existing assignment instead of appending a duplicate`() {
        configFile.writeText("[*.rell]\nrule_prefer_empty=true\nrule_quote_format=double\n")

        LinterConfigEditor.disableRule(configFile, "rule_prefer_empty")

        assertThat(configFile.readText())
            .isEqualTo("[*.rell]\nrule_prefer_empty=false\nrule_quote_format=double\n")
    }

    @Test
    fun `should be idempotent when applied repeatedly`() {
        LinterConfigEditor.disableRule(configFile, "rule_prefer_empty")
        LinterConfigEditor.disableRule(configFile, "rule_prefer_empty")
        LinterConfigEditor.disableRule(configFile, "rule_prefer_empty")

        assertThat(configFile.readText()).isEqualTo("[*.rell]\nrule_prefer_empty=false\n")
    }

    @Test
    fun `should add a section header to a section-less config`() {
        configFile.writeText("root=true\n")

        LinterConfigEditor.disableRule(configFile, "rule_prefer_empty")

        assertThat(configFile.readText()).isEqualTo("root=true\n[*.rell]\nrule_prefer_empty=false\n")
    }

    @Test
    fun `should terminate an unterminated last line before appending`() {
        configFile.writeText("[*.rell]\nrule_quote_format=double")

        LinterConfigEditor.disableRule(configFile, "rule_prefer_empty")

        assertThat(configFile.readText())
            .isEqualTo("[*.rell]\nrule_quote_format=double\nrule_prefer_empty=false\n")
    }
}
