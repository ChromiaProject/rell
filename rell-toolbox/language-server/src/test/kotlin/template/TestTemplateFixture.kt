/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.template

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

// Minimal on-disk template tree mirroring the structure of the chromia-cli-tools template
// resources. Used by tests to stand in for [RemoteTemplateRepository] so the test suite stays
// offline. Only the subset of templates exercised by tests is materialised: the PLAIN template
// and the .devcontainer fragment.
object TestTemplateFixture {

    fun materialize(): Path {
        val root = Files.createTempDirectory("rell-test-templates-")
        writeFile(root.resolve(".gitignore"), "# placeholder\n")
        writeFile(root.resolve(".rell_format"), "")
        writeFile(root.resolve(".rell_lint"), "")

        val plain = root.resolve("plain").createDirectories()
        writeFile(
            plain.resolve("chromia.yml"),
            """
            compile:
              rellVersion: RELL_VERSION
            database:
              schema: RELL_SCHEMA
            blockchains:
              PROJECT_NAME:
                module: main
            """.trimIndent()
        )
        writeFile(plain.resolve("src/main.rell"), "module;\n")
        writeFile(plain.resolve("src/test/plain_test.rell"), "@test module;\n")

        val devContainer = root.resolve(".devcontainer").createDirectories()
        writeFile(devContainer.resolve("devcontainer.json"), "{}\n")
        writeFile(devContainer.resolve("Dockerfile"), "FROM scratch\n")

        return root
    }

    private fun writeFile(target: Path, content: String) {
        target.parent?.createDirectories()
        target.writeText(content)
    }
}
