/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.references

import assertk.assertThat
import assertk.assertions.containsAtLeast
import assertk.assertions.hasSize
import net.postchain.rell.toolbox.formatter.FormatterOptions
import net.postchain.rell.toolbox.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.linter.FormattingStyleLinter
import net.postchain.rell.toolbox.linter.LinterOptions
import net.postchain.rell.toolbox.linter.RellLinter
import net.postchain.rell.toolbox.lsp.editing.Document
import net.postchain.rell.toolbox.lsp.symbols.RellSymbolService
import net.postchain.rell.toolbox.testing.TestDataBuilder
import net.postchain.rell.toolbox.testing.testData
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@Suppress("JAVA_CLASS_ON_COMPANION")
class ReferenceServiceTest {
    private val rellLinter = RellLinter()
    private val formattingStyleLinter = FormattingStyleLinter()
    private val formatterOptions = FormatterOptions()
    private val linterOptions = LinterOptions()

    @TempDir
    private lateinit var tempDir: File
    private lateinit var workspaceFile: File
    private lateinit var indexer: WorkspaceIndexer

    @BeforeEach
    fun setup() {
        workspaceFile = setupReferenceTestProject(tempDir).workspaceFolder
        indexer = WorkspaceIndexer(
            workspaceFile.toURI(),
            rellLinter,
            linterOptions,
            formattingStyleLinter,
            formatterOptions
        )
        indexer.initialFileIndexBuild()
    }

    @Test
    fun `Global references correctly found when triggered from definition`() {
        val referenceService = RellReferenceService(RellSymbolService())
        val mainFileUri = workspaceFile.resolve("src/main.rell").toURI()
        val document = Document(mainFileUri, 1, File(mainFileUri).readText())
        val position = Position(2, 16)

        val result = referenceService.getReferenceLocations(mainFileUri, document, indexer, position)

        assertGlobalReferences(result)
    }

    @Test
    fun `Global references correctly found when triggered from usage`() {
        val referenceService = RellReferenceService(RellSymbolService())
        val mainFileUri = workspaceFile.resolve("src/main.rell").toURI()
        val document = Document(mainFileUri, 1, File(mainFileUri).readText())
        val position = Position(12, 25)

        val result = referenceService.getReferenceLocations(mainFileUri, document, indexer, position)

        assertGlobalReferences(result)
    }

    private fun assertGlobalReferences(result: List<Location>) {
        assertThat(result).hasSize(3)
        assertThat(result).containsAtLeast(
            Location(
                workspaceFile.resolve("src/submodule/another_importing.rell").toURI().toString(),
                Range(Position(2, 17), Position(2, 32))
            ),
            Location(
                workspaceFile.resolve("src/importing.rell").toURI().toString(),
                Range(Position(3, 17), Position(3, 32))
            ),
            Location(
                workspaceFile.resolve("src/main.rell").toURI().toString(),
                Range(Position(12, 17), Position(12, 32))
            )
        )
    }

    @Test
    fun `Local variable references correctly found when triggered from definition`() {
        val referenceService = RellReferenceService(RellSymbolService())
        val mainFileUri = workspaceFile.resolve("src/main.rell").toURI()
        val document = Document(mainFileUri, 1, File(mainFileUri).readText())
        val position = Position(3, 13)

        val result = referenceService.getReferenceLocations(mainFileUri, document, indexer, position)

        assertLocalReferences(result)
    }

    @Test
    fun `Local variable references correctly found when triggered from usage`() {
        val referenceService = RellReferenceService(RellSymbolService())
        val mainFileUri = workspaceFile.resolve("src/main.rell").toURI()
        val document = Document(mainFileUri, 1, File(mainFileUri).readText())
        val position = Position(6, 22)

        val result = referenceService.getReferenceLocations(mainFileUri, document, indexer, position)

        assertLocalReferences(result)
    }

    private fun assertLocalReferences(result: List<Location>) {
        assertThat(result).hasSize(1)
        assertThat(result).containsAtLeast(
            Location(
                workspaceFile.resolve("src/main.rell").toURI().toString(),
                Range(Position(6, 17), Position(6, 23))
            )
        )
    }

    @Test
    fun `Module references correctly found when triggered from definition`() {
        val referenceService = RellReferenceService(RellSymbolService())
        val mainFileUri = workspaceFile.resolve("src/importing.rell").toURI()
        val document = Document(mainFileUri, 1, File(mainFileUri).readText())
        val position = Position(0, 10)

        val result = referenceService.getReferenceLocations(mainFileUri, document, indexer, position)

        assertThat(result).hasSize(2)
        assertThat(result).containsAtLeast(
            Location(
                workspaceFile.resolve("src/submodule/module.rell").toURI().toString(),
                Range(Position(0, 1), Position(0, 1))
            ),
            Location(
                workspaceFile.resolve("src/importing.rell").toURI().toString(),
                Range(Position(0, 1), Position(0, 1))
            )
        )
    }

    @Test
    fun `Parameter references correctly found from definition`() {
        val referenceService = RellReferenceService(RellSymbolService())
        val mainFileUri = workspaceFile.resolve("src/main.rell").toURI()
        val document = Document(mainFileUri, 1, File(mainFileUri).readText())
        val position = Position(2, 28)

        val result = referenceService.getReferenceLocations(mainFileUri, document, indexer, position)

        assertParameterReferences(result)
    }

    @Test
    fun `Parameter references correctly found from usage`() {
        val referenceService = RellReferenceService(RellSymbolService())
        val mainFileUri = workspaceFile.resolve("src/main.rell").toURI()
        val document = Document(mainFileUri, 1, File(mainFileUri).readText())
        val position = Position(6, 29)

        val result = referenceService.getReferenceLocations(mainFileUri, document, indexer, position)

        assertParameterReferences(result)
    }

    @Test
    fun `References correctly found when cursor is just to the left of token`() {
        val referenceService = RellReferenceService(RellSymbolService())
        val mainFileUri = workspaceFile.resolve("src/main.rell").toURI()
        val document = Document(mainFileUri, 1, File(mainFileUri).readText())
        val position = Position(6, 26)

        val result = referenceService.getReferenceLocations(mainFileUri, document, indexer, position)

        assertParameterReferences(result)
    }

    @Test
    fun `References correctly found when cursor is just to the right of token`() {
        val referenceService = RellReferenceService(RellSymbolService())
        val mainFileUri = workspaceFile.resolve("src/main.rell").toURI()
        val document = Document(mainFileUri, 1, File(mainFileUri).readText())
        val position = Position(6, 32)

        val result = referenceService.getReferenceLocations(mainFileUri, document, indexer, position)

        assertParameterReferences(result)
    }

    private fun assertParameterReferences(result: List<Location>) {
        assertThat(result).hasSize(2)
        assertThat(result).containsAtLeast(
            Location(
                workspaceFile.resolve("src/main.rell").toURI().toString(),
                Range(Position(3, 17), Position(3, 23))
            ),
            Location(
                workspaceFile.resolve("src/main.rell").toURI().toString(),
                Range(Position(6, 26), Position(6, 32))
            )
        )
    }
}

fun setupReferenceTestProject(dir: File): TestDataBuilder {
    return testData(dir) {
        addFile(
            "importing.rell",
            """
            import .main.*;

            function referencing_from_another_file() {
                val result = better_addition(44, 644);
            }
            """.trimIndent()
        )
        addMainFile(
            """
            module;

            function better_addition(paramA: integer, paramB: integer): integer {
                val localA = paramA;
                val localB = paramB;

                val localC = localA + paramA;

                return localC;
            }

            function local_reference() {
                val result = better_addition(4, 6);
            }
            """.trimIndent()
        )
        addFile(
            "submodule/module.rell",
            """
            module;

            import ^.main.*;
            """.trimIndent()
        )
        addFile(
            "submodule/another_importing.rell",
            """

            function another_reference_from_submodule() {
                val result = better_addition(24, 343);
            }
            """.trimIndent()
        )
    }
}
