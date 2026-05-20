/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.chromia.model

import assertk.assertThat
import assertk.assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.io.path.absolutePathString

class ChromiaModelTest {

    @Test
    fun `parseModel returns empty model for empty file`(@TempDir dir: File) {
        val yml = File(dir, "chromia.yml").also { it.writeText("") }
        val model = parseModel(yml.toPath())
        assertThat(model.libs).isEmpty()
        assertThat(model.compile.rellVersion).isNull()
        assertThat(model.compile.source).isNull()
    }

    @Test
    fun `parseModel reads rellVersion when present`(@TempDir dir: File) {
        val yml = File(dir, "chromia.yml").also {
            it.writeText("compile:\n  rellVersion: 0.13.42\n")
        }
        val model = parseModel(yml.toPath())
        assertThat(model.compile.rellVersion).isEqualTo("0.13.42")
    }

    @Test
    fun `parseModel treats blank rellVersion as missing`(@TempDir dir: File) {
        val yml = File(dir, "chromia.yml").also { it.writeText("compile:\n  rellVersion:\n") }
        assertThat(parseModel(yml.toPath()).compile.rellVersion).isNull()
    }

    @Test
    fun `parseModel resolves compile source against chromia yml directory`(@TempDir dir: File) {
        val yml = File(dir, "chromia.yml").also {
            it.writeText("compile:\n  source: custom/src\n")
        }
        val source = parseModel(yml.toPath()).compile.source
        assertThat(source).isNotNull()
        assertThat(source!!.absolutePathString()).isEqualTo(File(dir, "custom/src").absolutePath)
    }

    @Test
    fun `parseModel collects all lib keys`(@TempDir dir: File) {
        val yml = File(dir, "chromia.yml").also {
            it.writeText(
                """
                libs:
                  alpha:
                    registry: r1
                    path: p1
                  beta:
                    registry: r2
                    insecure: true
                  gamma:
                """.trimIndent()
            )
        }
        val model = parseModel(yml.toPath())
        assertThat(model.libs.keys.toList()).containsExactly("alpha", "beta", "gamma")
        assertThat(model.libs["alpha"]!!.registry).isEqualTo("r1")
        assertThat(model.libs["alpha"]!!.path).isEqualTo("p1")
        assertThat(model.libs["beta"]!!.insecure).isEqualTo(true)
        assertThat(model.libs["gamma"]).isEqualTo(RellLibraryModel())
    }

    @Test
    fun `parseModel throws on malformed yaml`(@TempDir dir: File) {
        val yml = File(dir, "chromia.yml").also {
            it.writeText("libs:\n  registry: a.registry.abc\n    path: x\n")
        }
        assertThrows<Exception> { parseModel(yml.toPath()) }
    }

    @Test
    fun `RellLibraryModel format emits registry, tagOrBranch, version, path, and insecure`() {
        val lib = RellLibraryModel(
            registry = "a.registry.abc",
            tagOrBranch = "main",
            path = "lib/path",
            insecure = true,
            version = "1.2.3",
        )
        val rendered = lib.format("external")
        assertThat(rendered).contains("\n  external:")
        assertThat(rendered).contains("\n    registry: a.registry.abc")
        assertThat(rendered).contains("\n    tagOrBranch: main")
        assertThat(rendered).contains("\n    version: 1.2.3")
        assertThat(rendered).contains("\n    path: lib/path")
        assertThat(rendered).contains("\n    insecure: true")
    }

    @Test
    fun `RellLibraryModel format omits absent fields`() {
        val rendered = RellLibraryModel(registry = "r").format("lib")
        assertThat(rendered).contains("\n    registry: r")
        assertThat(rendered).doesNotContain("path")
        assertThat(rendered).doesNotContain("tagOrBranch")
        assertThat(rendered).doesNotContain("insecure")
        assertThat(rendered).doesNotContain("version")
    }
}
