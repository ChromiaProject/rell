/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.doc.compose

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.writeText

class ModuleDocsTest {

    @Test
    fun `parses dapp module package fragments`(@TempDir tempDir: Path) {
        val file = (tempDir / "docs.md").apply {
            writeText(
                """
                # Dapp My Dapp
                Top-level prose for the dapp.

                # Module main
                Main module description.

                # Package lib.lib1.nested
                Nested namespace.

                # Package [root]
                Root package description.
                """.trimIndent()
            )
        }
        val docs = ModuleDocs.load(listOf(file))
        assertThat(docs.moduleDoc("My Dapp")).contains("Top-level prose")
        assertThat(docs.moduleDoc("main")).contains("Main module description")
        assertThat(docs.packageDoc("lib.lib1.nested")).isEqualTo("Nested namespace.")
        val rootDoc = docs.packageDoc("") ?: ""
        assertThat(rootDoc).contains("Root package description")
    }

    @Test
    fun `merges multiple fragments for the same target`(@TempDir tempDir: Path) {
        val first = (tempDir / "a.md").apply {
            writeText("# Module main\nFirst chunk.")
        }

        val second = (tempDir / "b.md").apply {
            writeText("# Module main\nSecond chunk.")
        }

        val docs = ModuleDocs.load(listOf(first, second))
        val combined = docs.moduleDoc("main")
        assertThat(combined).contains("First chunk")
        assertThat(combined).contains("Second chunk")
    }

    @Test
    fun `missing files yield no docs`() {
        val docs = ModuleDocs.load(listOf(Path("/nonexistent/path.md")))
        assertThat(docs.moduleDoc("any")).isEmpty()
    }
}
