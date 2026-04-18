/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.editorconfig

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class EditorConfigParserTest {

    @Test
    fun `parsing property with empty value isn't throwing`(@TempDir tempDir: File) {
        val editorConfigFile = File(tempDir, "some_editor_config").apply {
            writeText(
                """
                [*.rell]
                rule_unused_variable=
                """.trimIndent()
            )
        }

        val editorConfig = EditorConfigParser.parse(editorConfigFile)

        assertThat(editorConfig).isNotNull()
        assertThat(editorConfig?.sections?.size).isEqualTo(1)
        assertThat(editorConfig?.sections?.get(0)?.properties?.size).isEqualTo(1)
        editorConfig?.sections?.get(0)?.properties?.get("rule_unused_variable")?.let {
            assertThat(it.name).isEqualTo("rule_unused_variable")
            assertThat(it.sourceValue).isEqualTo("")
        }
    }
}
