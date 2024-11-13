package net.postchain.rell.toolbox.lsp.server

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsAll
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.containsOnly
import assertk.assertions.doesNotContain
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNull
import assertk.assertions.isTrue
import net.postchain.rell.toolbox.lsp.references.setupReferenceTestProject
import net.postchain.rell.toolbox.lsp.server.utils.WorkspaceManagerTestBase
import net.postchain.rell.toolbox.testing.testData
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.WorkspaceFolder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URI
import kotlin.io.path.createDirectories

class RellWorkspaceManagerTest : WorkspaceManagerTestBase() {

    private val rellFilePath = "rell_file.rell"
    private val rellFileContent = """
                module;
                function main() {
                    return "main";
                }
    """.trimIndent()

    @Test
    fun `Initialization correctly index relevant files`() {
        val testDataBuilder = testData(workspace) {
            addFile(rellFilePath, rellFileContent)
            addWorkspaceFile(
                "excluded.rell",
                """
                module;
                function excluded() {
                    return "excluded";
                }
                """.trimIndent()
            )
            addFile("not_a_rell_file.json", "{module}")
        }
        val rellFile = testDataBuilder.sourceFile(rellFilePath)

        initializeWorkspace()
        val indexers = workspaceManager.indexers
        val sourceDirUri = sourceDir.toURI()

        assertThat(indexers).hasSize(1)
        assertThat(indexers.keys.first()).isEqualTo(sourceDirUri)
        assertThat(indexers[sourceDirUri]!!.fileUriResourceMap).hasSize(1)
        assertThat(indexers[sourceDirUri]!!.fileUriResourceMap.keys).containsOnly(rellFile.toURI())
    }

    @Test
    fun `Initialization correctly index rell files within source directory`() {
        testData(workspace) {
            addMainFile(
                """
                module;
                import import_file.*;
                function main() {
                    return "main";
                }
                """.trimIndent()
            )
            addFile(
                "import_file.rell",
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
    fun `Indexer for single file should only have opened file when src out of depth search`() {
        val mainFilePath = "one/two/three/four/five/main.rell"
        val testDataBuilder = testData(workspace) {
            addFile(
                mainFilePath,
                """
                module;
                import import_file.*;
                function main() {
                    return "main";
                }
                """.trimIndent()
            )
            addFile(
                "one/two/three/four/five/import_file.rell",
                """
                module;
                function foo() {
                    return "foo";
                }
                """.trimIndent()
            )
        }
        val mainFile = testDataBuilder.sourceFile(mainFilePath)
        initializeWorkspace(mainFile)

        val indexers = workspaceManager.indexers

        assertThat(indexers).hasSize(1)
        assertThat(indexers.keys.first()).isEqualTo(mainFile.toURI())
        assertThat(indexers[mainFile.toURI()]!!.fileUriResourceMap).hasSize(1)
    }

    @Test
    fun `Indexer for single file finds src dir as the root of project`() {
        val mainFilePath = "one/two/three/main.rell"
        val childFilePath = "one/two/three/import_file.rell"
        val testDataBuilder = testData(workspace) {
            addFile(
                mainFilePath,
                """
                module;
                function main() {
                    return "main";
                }
                """.trimIndent()
            )
            addFile(
                childFilePath,
                """
                module;
                function foo() {
                    return "foo";
                }
                """.trimIndent()
            )
        }

        val mainFile = testDataBuilder.sourceFile(mainFilePath)
        val childFile = testDataBuilder.sourceFile(childFilePath)
        initializeWorkspace(childFile)

        val indexers = workspaceManager.indexers
        val sourceDirUri = sourceDir.toURI()

        assertThat(indexers).hasSize(1)
        assertThat(indexers.keys.first()).isEqualTo(sourceDirUri)
        assertThat(indexers[sourceDirUri]!!.fileUriResourceMap).hasSize(2)
        assertThat(indexers[sourceDirUri]!!.fileUriResourceMap.keys).containsOnly(mainFile.toURI(), childFile.toURI())
    }

    @Test
    fun `Indexer should not look for parent src if opened uri is not a rell file`() {
        val childDirs = File(sourceDir, "one/two/three").toPath().createDirectories().toFile()
        testData(workspace) {
            addMainFile(
                """
                module;
                import import_file.*;
                function main() {
                    return "main";
                }
                """.trimIndent()
            )
        }

        initializeWorkspace(childDirs)
        val indexers = workspaceManager.indexers

        assertThat(indexers).hasSize(1)
        assertThat(indexers.keys.first()).isEqualTo(childDirs.toURI())
        assertThat(indexers[childDirs.toURI()]!!.fileUriResourceMap).hasSize(0)
    }

    @Test
    fun `Opening file triggers resource update`() {
        val testDataBuilder = testData(workspace) {
            addFile(rellFilePath, rellFileContent)
        }
        val rellFile = testDataBuilder.sourceFile(rellFilePath)
        initializeWorkspace()

        val updatedContents = "gibberish"
        rellFile.writeText(updatedContents)
        workspaceManager.didOpen(rellFile.toURI(), 1, updatedContents)

        val rellFileResource = workspaceManager.indexers[sourceDir.toURI()]!!.fileUriResourceMap[rellFile.toURI()]!!
        assertThat(rellFileResource.parseTree.text).contains(updatedContents)
        assertThat(documentManager.getOpenDocuments().keys).containsOnly(rellFile.toURI())
    }

    @Test
    fun `Document manager handles files from multiple workspaces at the same time`() {
        val content = """
                module;
                function some_function() {
                    return "main";
                }
        """.trimIndent()
        val workspace1 = File(workspace, "workspace1").apply { mkdir() }
        val workspace2 = File(workspace, "workspace2").apply { mkdir() }
        val testDataBuilder1 = testData(workspace1) {
            addMainFile(content)
        }
        val testDataBuilder2 = testData(workspace2) {
            addMainFile(content)
        }
        val file1 = testDataBuilder1.mainFile
        val file2 = testDataBuilder2.mainFile
        val file1Uri = file1.toURI()
        val file2Uri = file2.toURI()
        initializeWorkspaces(listOf(workspace1, workspace2))

        workspaceManager.didOpen(file1Uri, 1, file1.readText())
        workspaceManager.didOpen(file2Uri, 1, file2.readText())

        assertThat(documentManager.getOpenDocuments().keys).containsOnly(file1Uri, file2Uri)
    }

    @Test
    fun `Opening Git file URI is skipped by indexer`() {
        val testDataBuilder = testData(workspace) {
            addFile(rellFilePath, rellFileContent)
        }
        val rellFileUri = testDataBuilder.sourceFile(rellFilePath).toURI()

        initializeWorkspace()

        val gitFileUri = URI(rellFileUri.toString().replace("file:/", "git:/"))
        val gitFileContent = "gitcontent"
        workspaceManager.didOpen(gitFileUri, 1, gitFileContent)

        val indexer = workspaceManager.indexers[sourceDir.toURI()]!!
        val gitFileResource = indexer.fileUriResourceMap[gitFileUri]
        assertThat(gitFileResource).isNull()
        assertThat(indexer.fileUriResourceMap.keys).contains(rellFileUri)
        assertThat(documentManager.getOpenDocuments().keys).containsOnly(gitFileUri)
    }

    @Test
    fun `Correct indexer returned for new file`() {
        val testDataBuilder = testData(workspace) {
            addFile(rellFilePath, rellFileContent)
        }

        initializeWorkspace()

        assertThat(workspaceManager.indexers.keys).containsOnly(testDataBuilder.sourceFolderUri)
        val expectedIndexer = workspaceManager.indexers[testDataBuilder.sourceFolderUri]!!
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
        assertThat(workspaceManager.indexers.keys).containsOnly(testDataBuilder.sourceFolderUri)
        assertThat(indexer === expectedIndexer).isTrue()
    }

    @Test
    fun `Correct indexer returned for existing file`() {
        val testDataBuilder = testData(workspace) {
            addFile(rellFilePath, rellFileContent)
        }
        val rellFileUri = testDataBuilder.sourceFile(rellFilePath).toURI()
        initializeWorkspace()
        val indexer = workspaceManager.getIndexerFor(rellFileUri)

        assertThat(indexer.fileUriResourceMap.keys).containsOnly(rellFileUri)
    }

    @Test
    fun `Correct indexer for single file opening (without workspace)`() {
        val testDataBuilder = testData(workspace) {
            addFile(rellFilePath, rellFileContent)
        }
        val singleRellFileUri = testDataBuilder.sourceFile(rellFilePath).toURI()
        workspaceManager.initialize(listOf(), ::populateDiagnostics)

        val indexer = workspaceManager.getIndexerFor(singleRellFileUri)

        assertThat(indexer.fileUriResourceMap.keys).containsOnly(singleRellFileUri)
    }

    @Test
    fun `Closing file works`() {
        val testDataBuilder = testData(workspace) {
            addFile(rellFilePath, rellFileContent)
        }
        val rellFileUri = testDataBuilder.sourceFile(rellFilePath).toURI()
        initializeWorkspace()

        workspaceManager.didOpen(rellFileUri, 1, "gibberish")
        assertThat(documentManager.getOpenDocuments().keys).containsOnly(rellFileUri)
        workspaceManager.didClose(rellFileUri)
        assertThat(documentManager.getOpenDocuments().keys).isEmpty()
    }

    @Test
    fun `didChangeTextDocumentContent correctly updates in-memory representation of file`() {
        val testDataBuilder = testData(workspace) {
            addFile(rellFilePath, rellFileContent)
        }
        val rellFile = testDataBuilder.sourceFile(rellFilePath)
        initializeWorkspace()
        workspaceManager.didOpen(rellFile.toURI(), 1, rellFileContent)

        val updateEvents = listOf(
            TextDocumentContentChangeEvent().apply {
                range = Range(Position(0, 0), Position(1, 0))
                text = "gibberish "
            }
        )
        workspaceManager.didChangeTextDocumentContent(rellFile.toURI(), updateEvents)

        val rellFileUri = rellFile.toURI()
        val expectedRellFileContent = """
                gibberish function main() {
                    return "main";
                }
        """.trimIndent()

        assertThat(documentManager.getOpenDocuments().keys).containsOnly(rellFileUri)
        assertThat(documentManager.getOpenDocuments()[rellFileUri]!!.content).isEqualTo(expectedRellFileContent)
    }

    @Test
    fun `didChangeTextDocumentContent correctly updates file when ordered sequential edits are part of one event`() {
        val testDataBuilder = testData(workspace) {
            addFile(rellFilePath, rellFileContent)
        }
        val rellFile = testDataBuilder.sourceFile(rellFilePath)
        initializeWorkspace()
        workspaceManager.didOpen(rellFile.toURI(), 1, rellFileContent)

        val updateEvents = listOf(
            TextDocumentContentChangeEvent(Range(Position(2, 4), Position(2, 4)), " "),
            TextDocumentContentChangeEvent(Range(Position(2, 5), Position(2, 5)), " "),
            TextDocumentContentChangeEvent(Range(Position(2, 6), Position(2, 6)), " "),
            TextDocumentContentChangeEvent(Range(Position(2, 7), Position(2, 7)), " "),
        )

        workspaceManager.didChangeTextDocumentContent(rellFile.toURI(), updateEvents)

        val rellFileUri = rellFile.toURI()
        val expectedRellFileContent = """
                module;
                function main() {
                        return "main";
                }
        """.trimIndent()

        assertThat(documentManager.getOpenDocuments().keys).containsOnly(rellFileUri)
        assertThat(documentManager.getOpenDocuments()[rellFileUri]!!.content).isEqualTo(expectedRellFileContent)
    }

    @Test
    fun `Adding, renaming or deleting file correctly updates index`() {
        val deleteFilePath = "delete_file.rell"
        val testDataBuilder = testData(workspace) {
            addFile(
                rellFilePath,
                """
                module;
                function main() {
                    return "rename file";
                }
                """.trimIndent()
            )
            addFile(
                deleteFilePath,
                """
                module;
                function main() {
                    return "delete file";
                }
                """.trimIndent()
            )
        }
        val renameFile = testDataBuilder.sourceFile(rellFilePath)
        val beforeRenameFileUri = renameFile.toURI()
        val deleteFileUri = testDataBuilder.sourceFile(deleteFilePath).toURI()
        initializeWorkspace()

        val indexer = workspaceManager.indexers[testDataBuilder.sourceFolderUri]!!
        assertThat(indexer.fileUriResourceMap).hasSize(2)
        assertThat(indexer.fileUriResourceMap.keys).containsOnly(beforeRenameFileUri, deleteFileUri)

        val newFileUri = testDataBuilder.createSourceFile(
            "new_file.rell",
            """
                module;
                function main() {
                    return "new file";
                }
            """.trimIndent()
        ).toURI()

        val newNameFile = File(testDataBuilder.sourceFolder, "much_cooler_name.rell")
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
        val importerFilePath = "importer.rell"
        val testDataBuilder = testData(workspace) {
            addFile(rellFilePath, rellFileContent)
            addFile(
                importerFilePath,
                """
                module;
                import rell_file.*;
                function main() {
                    return some_function();
                }
                """.trimIndent()
            )
        }
        val rellFile = testDataBuilder.sourceFile(rellFilePath)
        val rellFileUri = rellFile.toURI()
        val importerFileUri = testDataBuilder.sourceFile(importerFilePath).toURI()
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
        val importedFilePath = "imported.rell"
        val testDataBuilder = testData(workspace) {
            addFile(
                rellFilePath,
                """
                    module;
                    import imported.*;
                    function main() {
                        return some_function();
                    }
                """.trimIndent()
            )
            addFile(
                importedFilePath,
                """
                    module;
                    function some_function() {
                        return "main";
                    }
                """.trimIndent()
            )
        }
        val rellFile = testDataBuilder.sourceFile(rellFilePath)
        val importedFileUri = testDataBuilder.sourceFile(importedFilePath).toURI()

        initializeWorkspace()
        workspaceManager.didOpen(rellFile.toURI(), 1, rellFile.readText())
        val candidate = workspaceManager.getDefinitionLocations(rellFile.toURI(), Position(3, 15))
        assertThat(candidate.left!![0].uri).isEqualTo(importedFileUri.toString())
        assertThat(candidate.left!![0].range.start).isEqualTo(Position(1, 9))
        assertThat(candidate.left!![0].range.end).isEqualTo(Position(1, 22))
    }

    @Test
    fun `Go to definition for local link`() {
        val localLinkFilePath = "local_link.rell"
        val testDataBuilder = testData(workspace) {
            addFile(
                localLinkFilePath,
                """
                    module;
                    function foo() {
                        val a_long_val_name = 2;
                        val b = a_long_val_name;
                    }
                """.trimIndent()
            )
        }
        val localLinkFile = testDataBuilder.sourceFile(localLinkFilePath)

        initializeWorkspace()

        workspaceManager.didOpen(localLinkFile.toURI(), 1, localLinkFile.readText())
        val candidate = workspaceManager.getDefinitionLocations(
            localLinkFile.toURI(),
            Position(3, 20)
        ) // TODO: Believe we are off with one line here

        assertThat(candidate.left!![0].uri.contains(localLinkFile.toString())).isTrue()
        assertThat(candidate.left!![0].range.start).isEqualTo(Position(2, 8))
        assertThat(candidate.left!![0].range.end).isEqualTo(Position(2, 23))
    }

    @Test
    fun `Go to definition for local link when cursor is left of first character`() {
        val localLinkFilePath = "local_link.rell"
        val testDataBuilder = testData(workspace) {
            addFile(
                localLinkFilePath,
                """
                module;
                function foo() {
                    val a_long_val_name = 2;
                    val b = a_long_val_name;
                }
                """.trimIndent()
            )
        }
        val localLinkFile = testDataBuilder.sourceFile(localLinkFilePath)

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
        val localLinkFilePath = "local_link.rell"
        val testDataBuilder = testData(workspace) {
            addFile(
                localLinkFilePath,
                """
                module;
                function foo() {
                    val a_long_val_name = 2;
                    val b = a_long_val_name;
                }
                """.trimIndent()
            )
        }
        val localLinkFile = testDataBuilder.sourceFile(localLinkFilePath)

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
        val testDataBuilder = testData(workspace) {
            addFile(rellFilePath, rellFileContent)
        }
        val importerFileUri = testDataBuilder.sourceFile(rellFilePath).toURI()
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
        val workspaceFile = setupReferenceTestProject(workspace).workspaceFolder
        val mainFile = File(workspaceFile, "src/main.rell")
        initializeWorkspace(workspaceFile)
        workspaceManager.didOpen(mainFile.toURI(), 1, mainFile.readText())
        val references = workspaceManager.getReferenceLocations(mainFile.toURI(), Position(2, 16))
        assertThat(references).containsExactlyInAnyOrder(
            Location(mainFile.toURI().toString(), Range(Position(2, 9), Position(2, 24))),
            Location(mainFile.toURI().toString(), Range(Position(12, 17), Position(12, 32))),
            Location(
                File(workspaceFile, "src/submodule/another_importing.rell").toURI().toString(),
                Range(Position(2, 17), Position(2, 32))
            ),
            Location(
                File(workspaceFile, "src/importing.rell").toURI().toString(),
                Range(Position(3, 17), Position(3, 32))
            ),
        )
    }

    @Test
    fun `Find all references without definition`() {
        val workspaceFile = setupReferenceTestProject(workspace).workspaceFolder
        val mainFile = File(workspaceFile, "src/main.rell")
        initializeWorkspace(workspaceFile)
        workspaceManager.didOpen(mainFile.toURI(), 1, mainFile.readText())
        val references = workspaceManager.getReferenceLocations(
            mainFile.toURI(),
            Position(2, 16),
            false
        )

        assertThat(references).containsExactlyInAnyOrder(
            Location(mainFile.toURI().toString(), Range(Position(12, 17), Position(12, 32))),
            Location(
                File(workspaceFile, "src/submodule/another_importing.rell").toURI().toString(),
                Range(Position(2, 17), Position(2, 32))
            ),
            Location(
                File(workspaceFile, "src/importing.rell").toURI().toString(),
                Range(Position(3, 17), Position(3, 32))
            ),
        )
    }

    @Test
    fun `Find all references for entity attribute with implicit name`() {
        val rellFileContent = """
            module;
            entity my_entity {
              a_property: text;
              another_entity;
            }
            
            entity another_entity {
              another_property: text;
            }
            
            function q() = my_entity @* { .another_entity.another_property == "test" };
        """.trimIndent()
        val testDataBuilder = testData(workspace) {
            addFile(rellFilePath, rellFileContent)
        }
        val rellFile = testDataBuilder.sourceFile(rellFilePath)
        initializeWorkspace()
        workspaceManager.didOpen(rellFile.toURI(), 1, rellFileContent)
        val references = workspaceManager.getReferenceLocations(rellFile.toURI(), Position(6, 7))
        assertThat(references).containsExactlyInAnyOrder(
            Location(rellFile.toURI().toString(), Range(Position(3, 2), Position(3, 16))),
            Location(rellFile.toURI().toString(), Range(Position(6, 7), Position(6, 21))),
        )
    }

    @Test
    fun `Find all references for entity attribute with explicit name same as type`() {
        val rellFileContent = """
            module;
            entity my_entity {
              a_property: text;
              another_entity: another_entity;
            }
            
            entity another_entity {
              another_property: text;
            }
            
            function q() = my_entity @* { .another_entity.another_property == "test" };
        """.trimIndent()
        val testDataBuilder = testData(workspace) {
            addFile(rellFilePath, rellFileContent)
        }
        val rellFile = testDataBuilder.sourceFile(rellFilePath)
        initializeWorkspace()
        workspaceManager.didOpen(rellFile.toURI(), 1, rellFileContent)
        val references = workspaceManager.getReferenceLocations(rellFile.toURI(), Position(6, 7))
        assertThat(references).containsExactlyInAnyOrder(
            Location(rellFile.toURI().toString(), Range(Position(3, 18), Position(3, 32))),
            Location(rellFile.toURI().toString(), Range(Position(6, 7), Position(6, 21))),
        )
    }

    @Test
    fun `Find all references for entity attribute with explicit name different from type`() {
        val rellFileContent = """
            module;
            entity my_entity {
              a_property: text;
              ref_property: another_entity;
            }
            
            entity another_entity {
              another_property: text;
            }
            
            function q() = my_entity @* { .ref_property.another_property == "test" };
        """.trimIndent()
        val testDataBuilder = testData(workspace) {
            addFile(rellFilePath, rellFileContent)
        }
        val rellFile = testDataBuilder.sourceFile(rellFilePath)
        initializeWorkspace()
        workspaceManager.didOpen(rellFile.toURI(), 1, rellFileContent)
        val references = workspaceManager.getReferenceLocations(rellFile.toURI(), Position(6, 7))
        assertThat(references).containsExactlyInAnyOrder(
            Location(rellFile.toURI().toString(), Range(Position(3, 16), Position(3, 30))),
            Location(rellFile.toURI().toString(), Range(Position(6, 7), Position(6, 21))),
        )
    }
}
