package net.postchain.rell.toolbox.lsp.references

import assertk.assertThat
import assertk.assertions.containsAll
import assertk.assertions.hasSize
import net.postchain.rell.toolbox.core.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.lsp.editing.Document
import net.postchain.rell.toolbox.lsp.symbols.RellSymbolService
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File

class ReferenceServiceTest {

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
        assertThat(result).containsAll(
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
        assertThat(result).containsAll(
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
        assertThat(result).containsAll(
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

    private fun assertParameterReferences(result: List<Location>) {
        assertThat(result).hasSize(2)
        assertThat(result).containsAll(
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

    companion object {
        val classLoader = javaClass.getClassLoader()
        val workspaceFile = File(classLoader.getResource("rellReferences")!!.file)
        val indexer = WorkspaceIndexer(workspaceFile.toURI())

        @JvmStatic
        @BeforeAll
        fun setup() {
            indexer.initialFileIndexBuild()
        }
    }
}