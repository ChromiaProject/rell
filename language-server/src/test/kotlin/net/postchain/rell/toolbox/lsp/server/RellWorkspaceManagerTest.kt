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
import net.postchain.rell.toolbox.lsp.server.utils.WorkspaceManagerTestBase
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.WorkspaceFolder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URI
import kotlin.io.path.createDirectories
import kotlin.io.path.createDirectory

class RellWorkspaceManagerTest: WorkspaceManagerTestBase() {

    @Test
    fun `Initialization correctly index relevant files`() {
        val rellFile = File(sourceDir, "rell_file.rell").apply {
            writeText(
                """
                module;
                function main() {
                    return "main";
                }
            """.trimIndent()
            )
        }
        File(workspace, "excluded.rell").apply {
            writeText(
                """
                module;
                function excluded() {
                    return "excluded";
                }
            """.trimIndent()
            )
        }
        File(sourceDir, "not_a_rell_file.json").apply {
            writeText("{module}")
        }

        initializeWorkspace()
        val indexers = workspaceManager.indexers
        val sourceDirUri = sourceDir.toURI()

        assertThat(indexers).hasSize(1)
        assertThat(indexers.keys.first()).isEqualTo(sourceDirUri)
        assertThat(indexers[sourceDirUri]!!.fileUriResourceMap).hasSize(1)
        assertThat(indexers[sourceDirUri]!!.fileUriResourceMap.keys).containsOnly(rellFile.toURI())
    }

    @Test
    fun `Initialization correctly index for single file`() {
        File(sourceDir, "main.rell").apply {
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
        File(sourceDir, "import_file.rell").apply {
            writeText(
                """
                module;
                function foo() {
                    return "foo";
                }
            """.trimIndent()
            )
        }

        initializeWorkspace()

        val indexers = workspaceManager.indexers
        val sourceDirUri = sourceDir.toURI()

        assertThat(indexers).hasSize(1)
        assertThat(indexers.keys.first()).isEqualTo(sourceDirUri)
        assertThat(indexers[sourceDirUri]!!.fileUriResourceMap).hasSize(2)
    }

    @Test
    fun `Initialization correctly index for single file src out of depth search`() {
        val childDirs =File(sourceDir, "one/two/three/four/five").toPath().createDirectories().toFile()
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

        initializeWorkspace(mainFile)

        val indexers = workspaceManager.indexers

        assertThat(indexers).hasSize(1)
        assertThat(indexers.keys.first()).isEqualTo(mainFile.toURI())
        assertThat(indexers[mainFile.toURI()]!!.fileUriResourceMap).hasSize(1)
    }

    @Test
    fun `Opening file triggers resource update`() {
        val rellFile = File(sourceDir, "rell_file.rell").apply {
            writeText(
                """
                module;
                function main() {
                    return "main";
                }
            """.trimIndent()
            )
        }
        initializeWorkspace()

        val updatedContents = "gibberish"
        rellFile.writeText(updatedContents)
        workspaceManager.didOpen(rellFile.toURI(), 1, updatedContents)

        val rellFileResource = workspaceManager.indexers[sourceDir.toURI()]!!.fileUriResourceMap[rellFile.toURI()]!!
        assertThat(rellFileResource.parseTree.text).contains(updatedContents)
        assertThat(workspaceManager.openDocuments.keys).containsOnly(rellFile.toURI())
    }

    @Test
    fun `Opening Git file URI is skipped by indexer`() {
        val rellFileUri = File(sourceDir, "rell_file.rell").apply {
            writeText(
                """
                module;
                function main() {
                    return "main";
                }
            """.trimIndent()
            )
        }.toURI()

        initializeWorkspace()

        val gitFileUri = URI(rellFileUri.toString().replace("file:/", "git:/"))
        val gitFileContent = "gitcontent"
        workspaceManager.didOpen(gitFileUri, 1, gitFileContent)

        val indexer = workspaceManager.indexers[sourceDir.toURI()]!!
        val gitFileResource = indexer.fileUriResourceMap[gitFileUri]
        assertThat(gitFileResource).isNull()
        assertThat(indexer.fileUriResourceMap.keys).contains(rellFileUri)
        assertThat(workspaceManager.openDocuments.keys).containsOnly(gitFileUri)
    }

    @Test
    fun `Correct indexer returned for new file`() {
        var sourceDirUri = sourceDir.toURI()
        File(sourceDir, "rell_file.rell").apply {
            writeText(
                """
                module;
                function main() {
                    return "main";
                }
            """.trimIndent()
            )
        }

        initializeWorkspace()

        assertThat(workspaceManager.indexers.keys).containsOnly(sourceDirUri)
        val expectedIndexer = workspaceManager.indexers[sourceDirUri]!!
        val newRellFileUri = File(sourceDir, "new_rell_file.rell").apply {
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
        assertThat(workspaceManager.indexers.keys).containsOnly(sourceDirUri)
        assertThat(indexer === expectedIndexer).isTrue()
    }

    @Test
    fun `Correct indexer returned for existing file`() {
        val rellFileUri = File(sourceDir, "rell_file.rell").apply {
            writeText(
                """
                module;
                function main() {
                    return "main";
                }
            """.trimIndent()
            )
        }.toURI()
        initializeWorkspace()
        val indexer = workspaceManager.getIndexerFor(rellFileUri)

        assertThat(indexer.fileUriResourceMap.keys).containsOnly(rellFileUri)
    }

    @Test
    fun `Correct indexer for single file opening (without workspace)`() {
        val singleRellFileUri = File(sourceDir, "rell_file.rell").apply {
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
    fun `Closing file works`() {
        val rellFileUri = File(sourceDir, "rell_file.rell").apply {
            writeText(
                """
                module;
                function main() {
                    return "main";
                }
            """.trimIndent()
            )
        }.toURI()
        initializeWorkspace()

        workspaceManager.didOpen(rellFileUri, 1, "gibberish")
        assertThat(workspaceManager.openDocuments.keys).containsOnly(rellFileUri)
        workspaceManager.didClose(rellFileUri)
        assertThat(workspaceManager.openDocuments.keys).isEmpty()
    }

    @Test
    fun `didChangeTextDocumentContent correctly updates in-memory representation of file`() {
        val rellFileContent = """
                module;
                function main() {
                    return "main";
                }
            """.trimIndent()
        val rellFile = File(sourceDir, "rell_file.rell").apply {
            writeText(rellFileContent)
        }
        initializeWorkspace()
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
        assertThat(workspaceManager.openDocuments[rellFileUri]!!.content).isEqualTo(expectedRellFileContent)
    }

    @Test
    fun `Adding, renaming or deleting file correctly updates index`() {
        val renameFile = File(sourceDir, "rell_file.rell").apply {
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
        val deleteFileUri = File(sourceDir, "delete_file.rell").apply {
            writeText(
                """
                module;
                function main() {
                    return "delete file";
                }
            """.trimIndent()
            )
        }.toURI()
        initializeWorkspace()

        val indexer = workspaceManager.indexers[sourceDir.toURI()]!!
        assertThat(indexer.fileUriResourceMap).hasSize(2)
        assertThat(indexer.fileUriResourceMap.keys).containsOnly(beforeRenameFileUri, deleteFileUri)

        val newFileUri = File(sourceDir, "new_file.rell").apply {
            writeText(
                """
                module;
                function main() {
                    return "new file";
                }
            """.trimIndent()
            )
        }.toURI()

        val newNameFile = File(sourceDir, "much_cooler_name.rell")
        renameFile.renameTo(newNameFile)

        val renamedFileUri = newNameFile.toURI()
        workspaceManager.didChangeFiles(listOf(newFileUri, renamedFileUri), listOf(deleteFileUri, beforeRenameFileUri))

        assertThat(indexer.fileUriResourceMap.keys).containsAll(newFileUri, renamedFileUri)
        assertThat(indexer.fileUriResourceMap.keys).doesNotContain(deleteFileUri)
        assertThat(indexer.fileUriResourceMap.keys).doesNotContain(beforeRenameFileUri)
    }

    @Test
    fun `Saving file updates current and all dependent files in index`() {
        val rellFileContent = """
                module;
                function some_function() {
                    return "main";
                }
            """.trimIndent()
        val rellFile = File(sourceDir, "rell_file.rell").apply {
            writeText(rellFileContent)
        }
        val rellFileUri = rellFile.toURI()
        val importerFileUri = File(sourceDir, "importer.rell").apply {
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
        initializeWorkspace()
        workspaceManager.didOpen(rellFileUri, 1, rellFileContent)

        val indexer = workspaceManager.indexers[sourceDir.toURI()]!!
        assertThat(indexer.fileUriResourceMap[importerFileUri]!!.semanticErrors).isEmpty()

        val newContent = "gibberish"
        rellFile.writeText(newContent)
        workspaceManager.didSave(rellFile.toURI())

        assertThat(indexer.fileUriResourceMap[importerFileUri]!!.semanticErrors).isNotEmpty()
        assertThat(indexer.fileUriResourceMap[rellFileUri]!!.parseTree.text).contains(newContent)
    }

    @Test
    fun `Go to definition for imported object`() {
        val rellFile = File(sourceDir, "rell_file.rell").apply {
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

        val importedFileUri = File(sourceDir, "imported.rell").apply {
            writeText(
                """
                    module;
                    function some_function() {
                        return "main";
                    }
            """.trimIndent()
            )
        }.toURI()

        initializeWorkspace()
        workspaceManager.didOpen(rellFile.toURI(), 1, rellFile.readText())
        val candidate = workspaceManager.getDefinitionLocations(rellFile.toURI(), Position(3, 15))
        assertThat(candidate.left!![0].uri).isEqualTo(importedFileUri.toString())
        assertThat(candidate.left!![0].range.start).isEqualTo(Position(1, 9))
        assertThat(candidate.left!![0].range.end).isEqualTo(Position(1, 22))
    }

    @Test
    fun `Go to definition for local link`() {
        val localLinkFile = File(sourceDir, "local_link.rell").apply {
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

        initializeWorkspace()

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
    fun `Go to definition for local link when cursor is left of first character`() {
        val localLinkFile = File(sourceDir, "local_link.rell").apply {
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


        initializeWorkspace()
        workspaceManager.didOpen(localLinkFile.toURI(), 1, localLinkFile.readText())

        val candidate = workspaceManager.getDefinitionLocations(
            localLinkFile.toURI(),
            Position(3, 12)
        )

        assertThat(candidate.left!![0].uri.contains(localLinkFile.toString())).isTrue()
        assertThat(candidate.left!![0].range.start).isEqualTo(Position(2, 8))
        assertThat(candidate.left!![0].range.end).isEqualTo(Position(2, 23))
    }

    @Test
    fun `Go to definition for local link when cursor is right of last character`(@TempDir tempDir: File) {
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
            Position(3, 27)
        )
        assertThat(candidate.left!![0].uri.contains(localLinkFile.toString())).isTrue()
        assertThat(candidate.left!![0].range.start).isEqualTo(Position(2, 8))
        assertThat(candidate.left!![0].range.end).isEqualTo(Position(2, 23))
    }

    @Test
    fun `Go to definition for module file`() {
        val rellFileContent = """
                module;
                import importer.*;
                function main() {
                    return some_function();
                }
            """.trimIndent()
        val importerFileUri = File(sourceDir, "rell_file.rell").apply {
            writeText(rellFileContent)
        }.toURI()

        val importerFile = File(sourceDir, "importer.rell").apply {
            writeText(
                """
            module;
            function some_function() {
                return "main";
            }
            """.trimIndent()
            )
        }.toURI()
        initializeWorkspace()
        workspaceManager.didOpen(importerFileUri, 1, rellFileContent)

        val candidate = workspaceManager.getDefinitionLocations(importerFileUri, Position(1, 11))
        assertThat(candidate.left!![0].uri).isEqualTo(importerFile.toString())
        assertThat(candidate.left!![0].range.start).isEqualTo(Position(0, 0))
    }

    @Test
    fun `Find all references`() {
        val classLoader = javaClass.getClassLoader()
        val workspaceFile = File(classLoader.getResource("rellReferences")!!.file)
        val mainFile = File(workspaceFile, "src/main.rell")
        initializeWorkspace(workspaceFile)
        workspaceManager.didOpen(mainFile.toURI(), 1, mainFile.readText())
        val references = workspaceManager.getReferenceLocations(mainFile.toURI(), Position(2, 16))
        assertThat(references).hasSize(4)
    }
}
