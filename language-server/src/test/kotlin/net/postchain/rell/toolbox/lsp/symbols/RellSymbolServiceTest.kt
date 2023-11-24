package net.postchain.rell.toolbox.lsp.symbols

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import net.postchain.rell.toolbox.core.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.lsp.editing.Document
import org.eclipse.lsp4j.Position
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class RellSymbolServiceTest {
    val rellSymbolService = RellSymbolService()

    @Test
    fun `test for getLengthOfSymbol`() {
        val definitionId = "function[foo]"
        val res = rellSymbolService.getLengthOfSymbol(definitionId)
        assertThat(res).isEqualTo(3)
    }

    @Test
    fun `Returns empty list when resource does not exist`(@TempDir dir: File) {
        val rellFile = File(dir, "rell_file.rell").apply {
            writeText(
                """
                module;
                function main() {
                    return "main";
                }
            """.trimIndent()
            )
        }

        val indexer = WorkspaceIndexer(dir.toURI())
        indexer.initialFileIndexBuild()
        val unIndexedFile = File(dir, "unindexed_file.rell").apply { writeText("""""") }
        val document = Document(unIndexedFile.toURI(), 1, rellFile.readText())
        val position = Position(1, 1)
        val res = rellSymbolService.getSymbolLocations(document, indexer, position)
        assertThat(res).isEmpty()
    }

    @Test
    fun `Returns empty list when symbol does not exist`(@TempDir dir: File) {
        val rellFile = File(dir, "rell_file.rell").apply {
            writeText(
                """
                module;
                function main() {
                    return "main";
                }
            """.trimIndent()
            )
        }
        val document = Document(rellFile.toURI(), 1, rellFile.readText())
        val indexer = WorkspaceIndexer(dir.toURI())
        indexer.initialFileIndexBuild()
        val position = Position(1, 1)
        val res = rellSymbolService.getSymbolLocations(document, indexer, position)
        assertThat(res).isEmpty()
    }
}
