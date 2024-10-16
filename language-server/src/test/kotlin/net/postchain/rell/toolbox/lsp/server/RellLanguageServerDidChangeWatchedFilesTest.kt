package net.postchain.rell.toolbox.lsp.server

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import net.postchain.rell.toolbox.lsp.TestClient
import net.postchain.rell.toolbox.lsp.TestClientServerLauncher
import net.postchain.rell.toolbox.lsp.TestServerModule
import net.postchain.rell.toolbox.testing.testData
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.FileEvent
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.WorkDoneProgressBegin
import org.eclipse.lsp4j.WorkDoneProgressEnd
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.testcontainers.shaded.org.awaitility.Awaitility.await
import java.io.File

class RellLanguageServerDidChangeWatchedFilesTest {
    private lateinit var clientServerLauncher: TestClientServerLauncher
    private lateinit var server: RellLanguageServer
    private lateinit var workspaceManager: RellWorkspaceManager
    private lateinit var testClient: TestClient
    private val serverModule = TestServerModule()

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun setupBeforeEach() {
        val koinApp = serverModule.startKoin()

        clientServerLauncher = TestClientServerLauncher(koinApp)
        clientServerLauncher.launch()
        testClient = clientServerLauncher.testClient
        server = koinApp.koin.get<RellLanguageServer>()
        workspaceManager = koinApp.koin.get<RellWorkspaceManager>()
    }

    @AfterEach
    fun tearDown() {
        serverModule.stopKoinGlobalContext()
        clientServerLauncher.stop()
    }

    @Test
    fun `didChangeWatched file created`() {
        val testDataBuilder = testData(tempDir)
        clientServerLauncher.initializeServer(testDataBuilder.sourceFolderUri)
        val indexer = workspaceManager.indexers[testDataBuilder.sourceFolderUri]!!
        testDataBuilder.addFile("new_file.rell", "module;")
        val newFileUri = testDataBuilder.createSourceFile(
            "new_file.rell",
            """
                module;
                val a = "a";
            """.trimIndent()
        ).toURI()

        val fileEvent = FileEvent(newFileUri.toString(), FileChangeType.Created)
        val didChangeParams = DidChangeWatchedFilesParams(listOf(fileEvent))
        server.didChangeWatchedFiles(didChangeParams)
        await().until { testClient.diagnostics.isNotEmpty() }

        assertThat(indexer.fileUriResourceMap.keys).containsOnly(newFileUri)
        assertThat(testClient.diagnostics.keys).containsOnly(newFileUri.toString())
        assertThat(testClient.diagnostics[newFileUri.toString()]!!.size).isEqualTo(0)
    }

    @Test
    fun `didChangeWatched linter config created`() {
        val rellFilePath = "code.rell"
        val testDataBuilder = testData(tempDir) {
            addFile(
                rellFilePath,
                """
                module;
                function foo() {
                    val x = 123;
                }
                """.trimIndent()
            )
        }
        val rellFile = testDataBuilder.sourceFile(rellFilePath).toURI()
        clientServerLauncher.initializeServer(tempDir.toURI())
        val indexer = workspaceManager.indexers[testDataBuilder.sourceFolderUri]!!
        val configFileUri = File(tempDir.toString(), ".rell_lint").apply {
            writeText(
                """
                [*.rell]
                rule_unused_variable=true
                """.trimIndent()
            )
        }.toURI()

        val fileEvent = FileEvent(configFileUri.toString(), FileChangeType.Created)
        val didChangeParams = DidChangeWatchedFilesParams(listOf(fileEvent))
        server.didChangeWatchedFiles(didChangeParams)
        await().until { testClient.diagnostics.isNotEmpty() }

        val diagnostics = testClient.diagnostics
        assertThat(indexer.fileUriResourceMap.keys).containsOnly(rellFile)
        assertThat(diagnostics.keys).containsOnly(rellFile.toString())
        assertThat(diagnostics[rellFile.toString()]!!).containsOnly(
            Diagnostic(
                Range(Position(2, 8), Position(2, 8)),
                "Variable 'x' is never used",
                DiagnosticSeverity.Warning,
                null,
                "linter_issue:rule_unused_variable"
            ),
        )
    }

    @Test
    fun `didChangeWatched linter config created at a wrong location will not add linter issues to diagnostics`() {
        val rellFilePath = "code.rell"
        val testDataBuilder = testData(tempDir) {
            addFile(
                rellFilePath,
                """
                module;
                /**
                * @returned wrong doc tag (should be return)
                */
                function Foo() {
                    val x = 123;
                }
                """.trimIndent()
            )
        }
        clientServerLauncher.initializeServer(testDataBuilder.sourceFolderUri)
        val indexer = workspaceManager.indexers[testDataBuilder.sourceFolderUri]!!
        val configFileUri = File(testDataBuilder.workspaceFolder.toString(), ".rell_lint").apply {
            writeText(
                """
                [*.rell]
                rule_naming_convention=true
                """.trimIndent()
            )
        }.toURI()

        val rellFileUri = testDataBuilder.sourceFile(rellFilePath).toURI()
        val fileEvent = FileEvent(configFileUri.toString(), FileChangeType.Created)
        val rellFileUpdate = FileEvent(rellFileUri.toString(), FileChangeType.Changed)
        val didChangeParams = DidChangeWatchedFilesParams(listOf(fileEvent, rellFileUpdate))

        server.didChangeWatchedFiles(didChangeParams)

        await().until { testClient.diagnostics.isNotEmpty() }

        val diagnostics = testClient.diagnostics
        assertThat(indexer.fileUriResourceMap.keys).containsOnly(rellFileUri)
        assertThat(diagnostics.keys).containsOnly(rellFileUri.toString())
        assertThat(diagnostics[rellFileUri.toString()]!!).containsOnly(
            Diagnostic(
                Range(Position(2, 2), Position(2, 2)),
                "Invalid comment tag: @returned",
                DiagnosticSeverity.Warning,
                null,
                "comment:tag:unknown:returned"
            ),
        )
    }

    @Test
    fun `didChangeWatched file created affects existing file`() {
        val rellFilePath = "affected_file.rell"
        val testDataBuilder = testData(tempDir) {
            addFile(
                rellFilePath,
                """
                module;
                import new_file.*;
                """.trimIndent()
            )
        }
        val affectedFileUri = testDataBuilder.sourceFile(rellFilePath).toURI()

        clientServerLauncher.initializeServer(testDataBuilder.sourceFolderUri)
        val indexer = workspaceManager.indexers[testDataBuilder.sourceFolderUri]!!

        val newFileUri = testDataBuilder.createSourceFile("new_file.rell", "module;").toURI()

        val fileEvent = FileEvent(newFileUri.toString(), FileChangeType.Created)
        val didChangeParams = DidChangeWatchedFilesParams(listOf(fileEvent))
        server.didChangeWatchedFiles(didChangeParams)
        await().until { testClient.diagnostics.isNotEmpty() }

        assertThat(indexer.fileUriResourceMap.keys).containsOnly(affectedFileUri, newFileUri)
        assertThat(testClient.diagnostics.keys).containsOnly(affectedFileUri.toString(), newFileUri.toString())
    }

    @Test
    fun `didChangeWatched file deleted`() {
        val rellFile = "new_file.rell"
        val testDataBuilder = testData(tempDir) {
            emptyRellModule(rellFile)
        }
        val newFileUri = testDataBuilder.sourceFile(rellFile).toURI()
        clientServerLauncher.initializeServer(testDataBuilder.sourceFolderUri)
        val indexer = workspaceManager.indexers[testDataBuilder.sourceFolderUri]!!
        val resourcesBeforeDeletion = indexer.fileUriResourceMap.toMap()

        val fileEvent = FileEvent(newFileUri.toString(), FileChangeType.Deleted)
        val didChangeParams = DidChangeWatchedFilesParams(listOf(fileEvent))
        server.didChangeWatchedFiles(didChangeParams)
        await().until { testClient.diagnostics.isNotEmpty() }

        assertThat(resourcesBeforeDeletion.keys).containsOnly(newFileUri)
        assertThat(indexer.fileUriResourceMap).isEmpty()
    }

    @Test
    fun `didChangeWatched file changed`() {
        val rellFile = "new_file.rell"
        val testDataBuilder = testData(tempDir) {
            emptyRellModule(rellFile)
        }
        val newFileUri = testDataBuilder.sourceFile(rellFile).toURI()
        clientServerLauncher.initializeServer(testDataBuilder.sourceFolderUri)
        val indexer = workspaceManager.indexers[testDataBuilder.sourceFolderUri]!!

        val fileEvent = FileEvent(newFileUri.toString(), FileChangeType.Changed)
        val didChangeParams = DidChangeWatchedFilesParams(listOf(fileEvent))
        server.didChangeWatchedFiles(didChangeParams)
        await().until { testClient.diagnostics.isNotEmpty() }

        assertThat(testClient.diagnostics.keys).containsOnly(newFileUri.toString())
        assertThat(indexer.fileUriResourceMap.keys).containsOnly(newFileUri)
    }

    @Test
    fun `didChangeWatched file created and deleted in same event (renaming)`() {
        val rellFile = "new_file.rell"
        val testDataBuilder = testData(tempDir) {
            emptyRellModule(rellFile)
        }
        val oldFileUri = testDataBuilder.sourceFile(rellFile).toURI()
        clientServerLauncher.initializeServer(testDataBuilder.sourceFolderUri)
        val indexer = workspaceManager.indexers[testDataBuilder.sourceFolderUri]!!
        val resourcesBeforeDeletion = indexer.fileUriResourceMap.toMap()

        val renamedFileUri = testDataBuilder.createSourceFile("renamed_file.rell", "module;").toURI()

        val fileEventDelete = FileEvent(oldFileUri.toString(), FileChangeType.Deleted)
        val fileEventCreate = FileEvent(renamedFileUri.toString(), FileChangeType.Created)
        val didChangeParamsRename = DidChangeWatchedFilesParams(listOf(fileEventDelete, fileEventCreate))
        server.didChangeWatchedFiles(didChangeParamsRename)
        await().until { testClient.diagnostics.isNotEmpty() }

        assertThat(resourcesBeforeDeletion.keys).containsOnly(oldFileUri)
        assertThat(indexer.fileUriResourceMap.keys).containsOnly(renamedFileUri)
    }

    @Test
    fun `didChangeWatched folder renamed`() {
        val testDataBuilder = testData(tempDir) {
            addMainFile(
                """
                module;
                import submodule.*;

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

        clientServerLauncher.initializeServer(testDataBuilder.sourceFolderUri)
        val indexer = workspaceManager.indexers[testDataBuilder.sourceFolderUri]!!

        indexer.getAllIssues().forEach { (_, issues) ->
            assertThat(issues.size).isEqualTo(0)
        }

        val submoduleFolder = testDataBuilder.sourceFolder.listFiles()!!.first { it.endsWith("submodule") }
        val newFolderName = File(submoduleFolder.path.replace("submodule", "newmodule"))
        submoduleFolder.renameTo(newFolderName)

        val fileEventDelete = FileEvent(submoduleFolder.toURI().toString().trimEnd('/'), FileChangeType.Deleted)
        val fileEventCreate = FileEvent(newFolderName.toURI().toString().trimEnd('/'), FileChangeType.Created)
        val didChangeParams = DidChangeWatchedFilesParams(listOf(fileEventDelete, fileEventCreate))

        server.didChangeWatchedFiles(didChangeParams)
        await().until { testClient.diagnostics.size == 5 }

        val issues = indexer.getAllIssues()
        assertThat(issues.keys.map { it.toString() }).containsExactlyInAnyOrder(
            "file:$tempDir/src/newmodule/another_importing.rell",
            "file:$tempDir/src/newmodule/module.rell",
            "file:$tempDir/src/main.rell",
        )

        issues.forEach { (uri, issues) ->
            if (uri.toString().endsWith("main.rell")) {
                assertThat(issues.size).isEqualTo(1)
                assertThat(issues.first().message).isEqualTo("Module 'submodule' not found")
            } else {
                assertThat(issues.size).isEqualTo(0)
            }
        }
    }

    @Test
    fun `didChangeWatched chromia yml rell compile version updated`() {
        val fileContent = """
         module;
            
         entity foo {
            name;
            bool: boolean;
         }
                
         function bar() {
            val abc = foo @? { .name == "hello" };
            require(exists(abc), "abc is real");
            require(abc?.bool, "b");
         }
        """.trimIndent()

        val testDataBuilder = testData(tempDir) {
            addMainFile(fileContent)
            config {
                blockchains(
                    """
                    blockchains:
                      rellDappWithLib:
                        module: main
                    """.trimIndent()
                )
                compile(
                    """
                    compile:
                      rellVersion: 0.13.4
                    """.trimIndent()
                )
            }
        }
        val rellFile = testDataBuilder.mainFileUri
        clientServerLauncher.initializeServer(testDataBuilder.workspaceFolderUri)
        val indexer = workspaceManager.indexers[testDataBuilder.sourceFolderUri]!!
        val configContent = testDataBuilder.chromiaConfigFile.readText()
        testDataBuilder.chromiaConfigFile.writeText(configContent.replace("0.13.4", "0.14.1"))

        val fileEvent = FileEvent(testDataBuilder.chromiaConfigFileUri.toString(), FileChangeType.Changed)
        val didChangeParams = DidChangeWatchedFilesParams(listOf(fileEvent))
        server.didChangeWatchedFiles(didChangeParams)
        await().until { testClient.diagnostics.isNotEmpty() }

        val diagnostics = testClient.diagnostics

        val token = "rell-indexing"
        await().until { testClient.progressNotifications.size == 2 }
        assertThat(testClient.progressNotifications).containsExactly(
            ProgressParams(
                Either.forLeft(token),
                Either.forLeft(WorkDoneProgressBegin().apply { title = token })
            ),
            ProgressParams(
                Either.forLeft(token),
                Either.forLeft(WorkDoneProgressEnd())
            )
        )

        assertThat(indexer.fileUriResourceMap.keys).containsOnly(rellFile)
        assertThat(diagnostics.keys).containsOnly(rellFile.toString())
        assertThat(diagnostics[rellFile.toString()]!!).containsOnly(
            Diagnostic(
                Range(Position(10, 11), Position(10, 11)),
                "Variable 'abc' cannot be null at this location",
                DiagnosticSeverity.Warning,
                null,
                "expr:smartnull:var:never:[abc]"
            ),
        )
    }

    // TODO: Add test to make sure we aren't creating redundant single file indexers when renaming or adding new files
}
