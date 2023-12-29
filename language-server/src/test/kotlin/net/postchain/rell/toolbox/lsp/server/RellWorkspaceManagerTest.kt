package net.postchain.rell.toolbox.lsp.server

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsAll
import assertk.assertions.containsOnly
import assertk.assertions.doesNotContain
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNull
import assertk.assertions.isTrue
import net.postchain.rell.toolbox.core.indexer.RellIssue
import net.postchain.rell.toolbox.lsp.caching.RellIndexCachingService
import net.postchain.rell.toolbox.lsp.references.RellReferenceService
import net.postchain.rell.toolbox.lsp.symbols.RellSymbolService
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.WorkspaceFolder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URI
import kotlin.io.path.createDirectories
import kotlin.io.path.createDirectory

class RellWorkspaceManagerTest {

    private lateinit var workspaceManager: RellWorkspaceManager
    private var diagnostics = mutableMapOf<URI, List<RellIssue>>()

    @BeforeEach
    fun setup() {
        val symbolService = RellSymbolService()
        val referenceService = RellReferenceService(symbolService)
        val indexCachingService = RellIndexCachingService()
        workspaceManager = RellWorkspaceManager(symbolService, referenceService, indexCachingService)
        diagnostics.clear()
    }

    private fun populateDiagnostics(uri: URI, issues: List<RellIssue>) {
        diagnostics.put(uri, issues)
    }

    @Test
    fun `Initialization correctly index relevant files`(@TempDir tempDir: File) {
        val srcDir = File(tempDir, "src").toPath().createDirectory().toFile()
        val rellFile = File(srcDir, "rell_file.rell").apply {
            writeText(
                """
                module;
                function main() {
                    return "main";
                }
            """.trimIndent()
            )
        }
        File(tempDir, "excluded.rell").apply {
            writeText(
                """
                module;
                function excluded() {
                    return "excluded";
                }
            """.trimIndent()
            )
        }
        File(srcDir, "not_a_rell_file.json").apply {
            writeText("{module}")
        }

        val workspaceFolders = listOf(WorkspaceFolder(tempDir.toURI().toString()))
        workspaceManager.initialize(workspaceFolders, ::populateDiagnostics)

        val indexers = workspaceManager.indexers
        val srcDirUri = srcDir.toURI()

        assertThat(indexers).hasSize(1)
        assertThat(indexers.keys.first()).isEqualTo(srcDirUri)
        assertThat(indexers[srcDirUri]!!.fileUriResourceMap).hasSize(1)
        assertThat(indexers[srcDirUri]!!.fileUriResourceMap.keys).containsOnly(rellFile.toURI())
    }

    @Test
    fun `Initialization correctly index for single file`(@TempDir tempDir: File) {
        val srcDir = File(tempDir, "src").toPath().createDirectory().toFile()
        val mainFile = File(srcDir, "main.rell").apply {
            writeText(
                """
                module;
                import import_file2.*;
                function main() {
                    return "main";
                }
            """.trimIndent()
            )
        }
        File(srcDir, "import_file.rell").apply {
            writeText(
                """
                module;
                function foo() {
                    return "foo";
                }
            """.trimIndent()
            )
        }

        val workspaceFolders = listOf(WorkspaceFolder(mainFile.toURI().toString()))
        workspaceManager.initialize(workspaceFolders, ::populateDiagnostics)

        val indexers = workspaceManager.indexers
        val srcDirUri = srcDir.toURI()

        assertThat(indexers).hasSize(1)
        assertThat(indexers.keys.first()).isEqualTo(srcDirUri)
        assertThat(indexers[srcDirUri]!!.fileUriResourceMap).hasSize(2)
    }

    @Test
    fun `Initialization correctly index for single file src out of depth search`(@TempDir tempDir: File) {
        val srcDir = File(tempDir, "src").toPath().createDirectory().toFile()
        val childDirs =File(srcDir, "one/two/three/four/five").toPath().createDirectories().toFile()
        val mainFile = File(childDirs, "main.rell").apply {
            writeText(
                """
                module;
                import import_file2.*;
                function main() {
                    return "main";
                }
            """.trimIndent()
            )
        }
        File(childDirs, "import_file.rell").apply {
            writeText(
                """
                module;
                function foo() {
                    return "foo";
                }
            """.trimIndent()
            )
        }

        val workspaceFolders = listOf(WorkspaceFolder(mainFile.toURI().toString()))
        workspaceManager.initialize(workspaceFolders, ::populateDiagnostics)

        val indexers = workspaceManager.indexers

        assertThat(indexers).hasSize(1)
        assertThat(indexers.keys.first()).isEqualTo(mainFile.toURI())
        assertThat(indexers[mainFile.toURI()]!!.fileUriResourceMap).hasSize(1)
    }

    @Test
    fun `Opening file triggers resource update`(@TempDir tempDir: File) {
        val srcDir = File(tempDir, "src").toPath().createDirectory().toFile()
        val rellFile = File(srcDir, "rell_file.rell").apply {
            writeText(
                """
                module;
                function main() {
                    return "main";
                }
            """.trimIndent()
            )
        }
        val workspaceFolders = listOf(WorkspaceFolder(tempDir.toURI().toString()))
        workspaceManager.initialize(workspaceFolders, ::populateDiagnostics)

        val updatedContents = "gibberish"
        rellFile.writeText(updatedContents)
        workspaceManager.didOpen(rellFile.toURI(), 1, updatedContents)

        val rellFileResource = workspaceManager.indexers[srcDir.toURI()]!!.fileUriResourceMap[rellFile.toURI()]!!
        assertThat(rellFileResource.parseTree.text).contains(updatedContents)
        assertThat(workspaceManager.openDocuments.keys).containsOnly(rellFile.toURI())
    }

    @Test
    fun `Opening Git file URI is skipped by indexer`(@TempDir tempDir: File) {
        val srcDir = File(tempDir, "src").toPath().createDirectory().toFile()
        val rellFileUri = File(srcDir, "rell_file.rell").apply {
            writeText(
                """
                module;
                function main() {
                    return "main";
                }
            """.trimIndent()
            )
        }.toURI()

        val workspaceFolders = listOf(WorkspaceFolder(tempDir.toURI().toString()))
        workspaceManager.initialize(workspaceFolders, ::populateDiagnostics)

        val gitFileUri = URI(rellFileUri.toString().replace("file:/", "git:/"))
        val gitFileContent = "gitcontent"
        workspaceManager.didOpen(gitFileUri, 1, gitFileContent)

        val indexer = workspaceManager.indexers[srcDir.toURI()]!!
        val gitFileResource = indexer.fileUriResourceMap[gitFileUri]
        assertThat(gitFileResource).isNull()
        assertThat(indexer.fileUriResourceMap.keys).contains(rellFileUri)
        assertThat(workspaceManager.openDocuments.keys).containsOnly(gitFileUri)
    }

    @Test
    fun `Correct indexer returned for new file`(@TempDir tempDir: File) {
        val srcDir = File(tempDir, "src").toPath().createDirectory().toFile()
        var srcDirUri = srcDir.toURI()
        File(srcDir, "rell_file.rell").apply {
            writeText(
                """
                module;
                function main() {
                    return "main";
                }
            """.trimIndent()
            )
        }
        val workspaceFolders = listOf(WorkspaceFolder(tempDir.toURI().toString()))
        workspaceManager.initialize(workspaceFolders, ::populateDiagnostics)

        assertThat(workspaceManager.indexers.keys).containsOnly(srcDirUri)
        val expectedIndexer = workspaceManager.indexers[srcDirUri]!!
        val newRellFileUri = File(srcDir, "new_rell_file.rell").apply {
            writeText(
                """
                module;
                function main() {
                    return "new file";
                }
            """.trimIndent()
            )
        }.toURI()
        val indexer = workspaceManager.getIndexerFor(newRellFileUri)
        assertThat(workspaceManager.indexers.keys).containsOnly(srcDirUri)
        assertThat(indexer === expectedIndexer).isTrue()
    }

    @Test
    fun `Correct indexer returned for existing file`(@TempDir tempDir: File) {
        val srcDir = File(tempDir, "src").toPath().createDirectory().toFile()
        val rellFileUri = File(srcDir, "rell_file.rell").apply {
            writeText(
                """
                module;
                function main() {
                    return "main";
                }
            """.trimIndent()
            )
        }.toURI()
        val workspaceFolders = listOf(WorkspaceFolder(tempDir.toURI().toString()))
        workspaceManager.initialize(workspaceFolders, ::populateDiagnostics)

        val indexer = workspaceManager.getIndexerFor(rellFileUri)

        assertThat(indexer.fileUriResourceMap.keys).containsOnly(rellFileUri)
    }

    @Test
    fun `Correct indexer for single file opening (without workspace)`(@TempDir tempDir: File) {
        val srcDir = File(tempDir, "src").toPath().createDirectory().toFile()
        val singleRellFileUri = File(srcDir, "rell_file.rell").apply {
            writeText(
                """
                module;
                function main() {
                    return "main";
                }
            """.trimIndent()
            )
        }.toURI()
        workspaceManager.initialize(listOf(), ::populateDiagnostics)

        val indexer = workspaceManager.getIndexerFor(singleRellFileUri)

        assertThat(indexer.fileUriResourceMap.keys).containsOnly(singleRellFileUri)
    }

    @Test
    fun `Closing file works`(@TempDir tempDir: File) {
        val srcDir = File(tempDir, "src").toPath().createDirectory().toFile()
        val rellFileUri = File(srcDir, "rell_file.rell").apply {
            writeText(
                """
                module;
                function main() {
                    return "main";
                }
            """.trimIndent()
            )
        }.toURI()
        val workspaceFolders = listOf(WorkspaceFolder(tempDir.toURI().toString()))
        workspaceManager.initialize(workspaceFolders, ::populateDiagnostics)

        workspaceManager.didOpen(rellFileUri, 1, "gibberish")
        assertThat(workspaceManager.openDocuments.keys).containsOnly(rellFileUri)
        workspaceManager.didClose(rellFileUri)
        assertThat(workspaceManager.openDocuments.keys).isEmpty()
    }

    @Test
    fun `didChangeTextDocumentContent correctly updates in-memory representation of file`(@TempDir tempDir: File) {
        val srcDir = File(tempDir, "src").toPath().createDirectory().toFile()
        val rellFileContent = """
                module;
                function main() {
                    return "main";
                }
            """.trimIndent()
        val rellFile = File(srcDir, "rell_file.rell").apply {
            writeText(rellFileContent)
        }
        val workspaceFolders = listOf(WorkspaceFolder(tempDir.toURI().toString()))
        workspaceManager.initialize(workspaceFolders, ::populateDiagnostics)
        workspaceManager.didOpen(rellFile.toURI(), 1, rellFileContent)

        val updateEvents = listOf(
            TextDocumentContentChangeEvent().apply {
                range = Range(Position(0, 0), Position(1, 0))
                text = "gibberish "
            }
        )
        workspaceManager.didChangeTextDocumentContent(rellFile.toURI(), 2, updateEvents)

        val rellFileUri = rellFile.toURI()
        val expectedRellFileContent = """
                gibberish function main() {
                    return "main";
                }
            """.trimIndent()

        assertThat(workspaceManager.openDocuments.keys).containsOnly(rellFileUri)
        assertThat(workspaceManager.openDocuments[rellFileUri]!!.contents).isEqualTo(expectedRellFileContent)
    }

    @Test
    fun `Adding, renaming or deleting file correctly updates index`(@TempDir tempDir: File) {
        val srcDir = File(tempDir, "src").toPath().createDirectory().toFile()
        val renameFile = File(srcDir, "rell_file.rell").apply {
            writeText(
                """
                module;
                function main() {
                    return "rename file";
                }
            """.trimIndent()
            )
        }
        val beforeRenameFileUri = renameFile.toURI()
        val deleteFileUri = File(srcDir, "delete_file.rell").apply {
            writeText(
                """
                module;
                function main() {
                    return "delete file";
                }
            """.trimIndent()
            )
        }.toURI()
        val workspaceFolders = listOf(WorkspaceFolder(tempDir.toURI().toString()))
        workspaceManager.initialize(workspaceFolders, ::populateDiagnostics)

        val indexer = workspaceManager.indexers[srcDir.toURI()]!!
        assertThat(indexer.fileUriResourceMap).hasSize(2)
        assertThat(indexer.fileUriResourceMap.keys).containsOnly(beforeRenameFileUri, deleteFileUri)

        val newFileUri = File(srcDir, "new_file.rell").apply {
            writeText(
                """
                module;
                function main() {
                    return "new file";
                }
            """.trimIndent()
            )
        }.toURI()

        val newNameFile = File(srcDir, "much_cooler_name.rell")
        renameFile.renameTo(newNameFile)

        val renamedFileUri = newNameFile.toURI()
        workspaceManager.didChangeFiles(listOf(newFileUri, renamedFileUri), listOf(deleteFileUri, beforeRenameFileUri))

        assertThat(indexer.fileUriResourceMap.keys).containsAll(newFileUri, renamedFileUri)
        assertThat(indexer.fileUriResourceMap.keys).doesNotContain(deleteFileUri)
        assertThat(indexer.fileUriResourceMap.keys).doesNotContain(beforeRenameFileUri)
    }

    @Test
    fun `Saving file updates current and all dependent files in index`(@TempDir tempDir: File) {
        val srcDir = File(tempDir, "src").toPath().createDirectory().toFile()
        val rellFileContent = """
                module;
                function some_function() {
                    return "main";
                }
            """.trimIndent()
        val rellFile = File(srcDir, "rell_file.rell").apply {
            writeText(rellFileContent)
        }
        val rellFileUri = rellFile.toURI()
        val importerFileUri = File(srcDir, "importer.rell").apply {
            writeText(
                """
                module;
                import rell_file.*;
                function main() {
                    return some_function();
                }
            """.trimIndent()
            )
        }.toURI()
        val workspaceFolders = listOf(WorkspaceFolder(tempDir.toURI().toString()))
        workspaceManager.initialize(workspaceFolders, ::populateDiagnostics)
        workspaceManager.didOpen(rellFileUri, 1, rellFileContent)

        val indexer = workspaceManager.indexers[srcDir.toURI()]!!
        assertThat(indexer.fileUriResourceMap[importerFileUri]!!.semanticErrors).isEmpty()

        val newContent = "gibberish"
        rellFile.writeText(newContent)
        workspaceManager.didSave(rellFile.toURI())

        assertThat(indexer.fileUriResourceMap[importerFileUri]!!.semanticErrors).isNotEmpty()
        assertThat(indexer.fileUriResourceMap[rellFileUri]!!.parseTree.text).contains(newContent)
    }

    @Test
    fun `Go to definition for imported object`(@TempDir tempDir: File) {
        val srcDir = File(tempDir, "src").toPath().createDirectory().toFile()
        val rellFile = File(srcDir, "rell_file.rell").apply {
            writeText(
                """
                    module;
                    import imported.*;
                    function main() {
                        return some_function();
                    }
            """.trimIndent()
            )
        }

        val importedFileUri = File(srcDir, "imported.rell").apply {
            writeText(
                """
                    module;
                    function some_function() {
                        return "main";
                    }
            """.trimIndent()
            )
        }.toURI()

        val workspaceFolders = listOf(WorkspaceFolder(tempDir.toURI().toString()))
        workspaceManager.initialize(workspaceFolders, ::populateDiagnostics)
        workspaceManager.didOpen(rellFile.toURI(), 1, rellFile.readText())
        val candidate = workspaceManager.getDefinitionLocations(rellFile.toURI(), Position(3, 15))
        assertThat(candidate.left!![0].uri).isEqualTo(importedFileUri.toString())
        assertThat(candidate.left!![0].range.start).isEqualTo(Position(1, 9))
        assertThat(candidate.left!![0].range.end).isEqualTo(Position(1, 22))
    }

    @Test
    fun `Go to definition for local link`(@TempDir tempDir: File) {
        val srcDir = File(tempDir, "src").toPath().createDirectory().toFile()
        val localLinkFile = File(srcDir, "local_link.rell").apply {
            writeText(
                """
                module;
                function foo() {
                    val a_long_val_name = 2;
                    val b = a_long_val_name;
                }
            """.trimIndent()
            )
        }


        val workspaceFolders = listOf(WorkspaceFolder(tempDir.toURI().toString()))
        workspaceManager.initialize(workspaceFolders, ::populateDiagnostics)
        workspaceManager.didOpen(localLinkFile.toURI(), 1, localLinkFile.readText())
        val candidate = workspaceManager.getDefinitionLocations(
            localLinkFile.toURI(),
            Position(3, 20)
        ) //TODO: Believe we are off with one line here

        assertThat(candidate.left!![0].uri.contains(localLinkFile.toString())).isTrue()
        assertThat(candidate.left!![0].range.start).isEqualTo(Position(2, 8))
        assertThat(candidate.left!![0].range.end).isEqualTo(Position(2, 23))
    }

    @Test
    fun `Go to definition for module file`(@TempDir tempDir: File) {
        val srcDir = File(tempDir, "src").toPath().createDirectory().toFile()
        val rellFileContent = """
                module;
                import importer.*;
                function main() {
                    return some_function();
                }
            """.trimIndent()
        val importerFileUri = File(srcDir, "rell_file.rell").apply {
            writeText(rellFileContent)
        }.toURI()

        val importerFile = File(srcDir, "importer.rell").apply {
            writeText(
                """
            module;
            function some_function() {
                return "main";
            }
            """.trimIndent()
            )
        }.toURI()
        val workspaceFolders = listOf(WorkspaceFolder(tempDir.toURI().toString()))
        workspaceManager.initialize(workspaceFolders, ::populateDiagnostics)
        workspaceManager.didOpen(importerFileUri, 1, rellFileContent)

        val candidate = workspaceManager.getDefinitionLocations(importerFileUri, Position(1, 11))
        assertThat(candidate.left!![0].uri).isEqualTo(importerFile.toString())
        assertThat(candidate.left!![0].range.start).isEqualTo(Position(0, 1))
    }

    @Test
    fun `Find all references`() {
        val classLoader = javaClass.getClassLoader()
        val workspaceFile = File(classLoader.getResource("rellReferences")!!.file)
        val mainFile = File(workspaceFile, "src/main.rell")
        val workspaceFolders = listOf(WorkspaceFolder(workspaceFile.toURI().toString()))
        workspaceManager.initialize(workspaceFolders, ::populateDiagnostics)
        workspaceManager.didOpen(mainFile.toURI(), 1, mainFile.readText())
        val references = workspaceManager.getReferenceLocations(mainFile.toURI(), Position(2, 16))
        assertThat(references).hasSize(3)
    }
}
