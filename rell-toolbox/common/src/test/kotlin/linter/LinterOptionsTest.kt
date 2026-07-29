/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LinterOptionsTest {

    @Test
    fun `a key removed from the config falls back to its default on reload`(@TempDir tempDir: File) {
        val configFile = File(tempDir, LinterOptions.CONFIG_FILE_NAME)
        val options = LinterOptions()

        configFile.writeText("[*.rell]\nrule_prefer_empty=false\nrule_quote_format=double\n")
        options.updateOptionsFromFile(configFile)
        assertThat(options.rulePreferEmpty).isEqualTo(false)
        assertThat(options.ruleQuoteFormat).isEqualTo(Quote.DOUBLE)

        configFile.writeText("[*.rell]\nrule_quote_format=double\n")
        options.updateOptionsFromFile(configFile)
        assertThat(options.rulePreferEmpty).isEqualTo(true)
        assertThat(options.ruleQuoteFormat).isEqualTo(Quote.DOUBLE)
    }
}
