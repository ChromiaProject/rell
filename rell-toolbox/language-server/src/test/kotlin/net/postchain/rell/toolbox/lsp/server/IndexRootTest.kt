/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.server

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.startsWith
import net.postchain.rell.toolbox.chromia.ChromiaModelProvider
import net.postchain.rell.toolbox.testing.testData
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class IndexRootTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `findIndexRoots finds all chromia config files in workspace`() {
        val pathProjectOne = tempDir.resolve("project1")
        val pathProjectTwo = tempDir.resolve("project2")

        val testDataBuilderOne = testData(pathProjectOne)
        val testDataBuilderTwo = testData(pathProjectTwo)

        val indexRoots = IndexRoot.findIndexRoots(tempDir.toURI())

        assertThat(indexRoots).hasSize(2)
        assertThat(indexRoots.map { it.chromiaConfigPath.fileName.toString() })
            .contains(ChromiaModelProvider.DEFAULT_CHROMIA_MODEL_FILENAME)

        val sourcePaths = indexRoots.map { it.sourceRootPath.toString() }
        assertThat(sourcePaths).containsExactlyInAnyOrder(
            testDataBuilderOne.sourceFolder.path,
            testDataBuilderTwo.sourceFolder.path
        )
    }

    @Test
    fun `fromChromiaConfig creates IndexRoot with correct source path from config`() {
        val customRelativeSrcPath = "custom/src"
        val testDataBuilder = testData(tempDir) {
            config {
                compile(
                    """
                    compile:
                        source: $customRelativeSrcPath
                    """.trimIndent()
                )
            }

            addWorkspaceFile("custom/src/main.rell", "module;")
        }
        val chromiaConfigPath = testDataBuilder.chromiaConfigFile.toPath()

        val indexRoot = IndexRoot.fromChromiaConfig(chromiaConfigPath)

        val expectedPath = testDataBuilder.workspaceFolder.resolve("custom/src").toString()
        assertThat(indexRoot.sourceRootPath.toString()).isEqualTo(expectedPath)
        assertThat(indexRoot.chromiaConfigPath).isEqualTo(chromiaConfigPath)
    }

    @Test
    fun `fromChromiaConfig falls back to default source directory when not specified in config`() {
        val testDataBuilder = testData(tempDir)
        val chromiaConfigPath = testDataBuilder.chromiaConfigFile.toPath()
        val indexRoot = IndexRoot.fromChromiaConfig(chromiaConfigPath)

        val expectedPath = testDataBuilder.sourceFolder.toPath()
        assertThat(indexRoot.sourceRootPath).isEqualTo(expectedPath)
    }

    @Test
    fun `URIs are correctly parsed and normalized`() {
        val testDataBuilder = testData(tempDir)

        val chromiaConfigPath = testDataBuilder.chromiaConfigFile.toPath()
        val indexRoot = IndexRoot.fromChromiaConfig(chromiaConfigPath)

        assertThat(indexRoot.sourceRootUri.toString()).startsWith("file:/")
        assertThat(indexRoot.chromiaConfigDirUri.toString()).startsWith("file:/")
    }

    @Test
    fun `findIndexRoots returns empty list when no chromia config files found`() {
        val indexRoots = IndexRoot.findIndexRoots(tempDir.toURI())
        assertThat(indexRoots).isEmpty()
    }

    @Test
    fun `findIndexRoots handles nested project structures`() {
        val pathProjectOuter = tempDir.resolve("outer")
        val pathProjectInner = tempDir.resolve("outer/inner")

        val testDataBuilderOuter = testData(pathProjectOuter)
        val testDataBuilderInner = testData(pathProjectInner)

        val indexRoots = IndexRoot.findIndexRoots(tempDir.toURI())

        assertThat(indexRoots).hasSize(2)
        val sourcePaths = indexRoots.map { it.sourceRootPath }
        assertThat(sourcePaths).containsExactlyInAnyOrder(
            testDataBuilderOuter.sourceFolder.toPath(),
            testDataBuilderInner.sourceFolder.toPath(),
        )
    }
}
