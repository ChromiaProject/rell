package net.postchain.rell.toolbox.lsp.server

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import java.io.File
import kotlin.io.path.createDirectory
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.FileEvent
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.testcontainers.shaded.org.awaitility.Awaitility.await
import util.TestClient
import util.TestClientServerLauncher
import util.TestServerModule

class RellLanguageServerDidChangeWatchedFilesTest {
    private lateinit var clientServerLauncher: TestClientServerLauncher
    private lateinit var server: RellLanguageServer
    private lateinit var workspaceManager: RellWorkspaceManager
    private lateinit var testClient: TestClient
    private lateinit var srcDir: File
    private val serverModule = TestServerModule()

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun setupBeforeEach() {
        srcDir = File(tempDir, "src").toPath().createDirectory().toFile()
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
        clientServerLauncher.initializeServer(srcDir.toURI())
        val indexer = workspaceManager.indexers[srcDir.toURI()]!!
        val newFileUri = File(srcDir.toString(), "new_file.rell").apply {
            writeText(
                """
                module;
                val a = "a";
            """.trimIndent()
            )
        }.toURI()

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
        val rellFile = File(srcDir.toString(), "code.rell").apply {
            writeText(
                """
                module;
                function foo() {
                    val x = 123;
                }
            """.trimIndent()
            )
        }.toURI()
        clientServerLauncher.initializeServer(tempDir.toURI())
        val indexer = workspaceManager.indexers[srcDir.toURI()]!!
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
        val rellFileUri = File(srcDir.toString(), "code.rell").apply {
            writeText(
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
        }.toURI()
        clientServerLauncher.initializeServer(srcDir.toURI())
        val indexer = workspaceManager.indexers[srcDir.toURI()]!!
        val configFileUri = File(tempDir.toString(), ".rell_lint").apply {
            writeText(
                """
                [*.rell]
                rule_naming_convention=true
            """.trimIndent()
            )
        }.toURI()

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
        val affectedFileUri = File(srcDir.toString(), "affected_file.rell").apply {
            writeText(
                """
                module;
                import new_file.*;
            """.trimIndent()
            )
        }.toURI()
        clientServerLauncher.initializeServer(srcDir.toURI())
        val indexer = workspaceManager.indexers[srcDir.toURI()]!!

        val newFileUri = File(srcDir.toString(), "new_file.rell").apply {
            writeText(
                """
                module;
            """.trimIndent()
            )
        }.toURI()

        val fileEvent = FileEvent(newFileUri.toString(), FileChangeType.Created)
        val didChangeParams = DidChangeWatchedFilesParams(listOf(fileEvent))
        server.didChangeWatchedFiles(didChangeParams)
        await().until { testClient.diagnostics.isNotEmpty() }

        assertThat(indexer.fileUriResourceMap.keys).containsOnly(affectedFileUri, newFileUri)
        assertThat(testClient.diagnostics.keys).containsOnly(affectedFileUri.toString(), newFileUri.toString())
    }

    @Test
    fun `didChangeWatched file deleted`() {
        val newFileUri = File(srcDir.toString(), "new_file.rell").apply {
            writeText(
                """
                module;
            """.trimIndent()
            )
        }.toURI()
        clientServerLauncher.initializeServer(srcDir.toURI())
        val indexer = workspaceManager.indexers[srcDir.toURI()]!!
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
        val newFileUri = File(srcDir.toString(), "new_file.rell").apply {
            writeText(
                """
                module;
            """.trimIndent()
            )
        }.toURI()
        clientServerLauncher.initializeServer(srcDir.toURI())
        val indexer = workspaceManager.indexers[srcDir.toURI()]!!

        val fileEvent = FileEvent(newFileUri.toString(), FileChangeType.Changed)
        val didChangeParams = DidChangeWatchedFilesParams(listOf(fileEvent))
        server.didChangeWatchedFiles(didChangeParams)
        await().until { testClient.diagnostics.isNotEmpty() }

        assertThat(testClient.diagnostics.keys).containsOnly(newFileUri.toString())
        assertThat(indexer.fileUriResourceMap.keys).containsOnly(newFileUri)
    }

    @Test
    fun `didChangeWatched file created and deleted in same event (renaming)`() {
        val oldFileUri = File(srcDir.toString(), "new_file.rell").apply {
            writeText(
                """
                module;
            """.trimIndent()
            )
        }.toURI()

        clientServerLauncher.initializeServer(srcDir.toURI())
        val indexer = workspaceManager.indexers[srcDir.toURI()]!!
        val resourcesBeforeDeletion = indexer.fileUriResourceMap.toMap()

        val renamedFileUri = File(srcDir.toString(), "renamed_file.rell").apply {
            writeText(
                """
                module;
            """.trimIndent()
            )
        }.toURI()

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
        val rellRenameFolderDir = File(javaClass.getClassLoader().getResource("folderRename")!!.file)
        val tempCopyDestination = tempDir.resolve("folderRename")
        await().until { rellRenameFolderDir.copyRecursively(tempCopyDestination) }

        val testWorkspaceSrc = tempCopyDestination.resolve("src")
        clientServerLauncher.initializeServer(testWorkspaceSrc.toURI())
        val indexer = workspaceManager.indexers[testWorkspaceSrc.toURI()]!!

        indexer.getAllIssues().forEach { (_, issues) ->
            assertThat(issues.size).isEqualTo(0)
        }

        val submoduleFolder = testWorkspaceSrc.listFiles()!!.first { it.endsWith("submodule") }
        val newFolderName = File(submoduleFolder.path.replace("submodule", "newmodule"))
        submoduleFolder.renameTo(newFolderName)

        val fileEventDelete = FileEvent(submoduleFolder.toURI().toString().trimEnd('/'), FileChangeType.Deleted)
        val fileEventCreate = FileEvent(newFolderName.toURI().toString().trimEnd('/'), FileChangeType.Created)
        val didChangeParams = DidChangeWatchedFilesParams(listOf(fileEventDelete, fileEventCreate))

        server.didChangeWatchedFiles(didChangeParams)
        await().until { testClient.diagnostics.size == 5 }

        val issues = indexer.getAllIssues()
        assertThat(issues.keys.map { it.toString() }).containsExactlyInAnyOrder(
            "file:$tempDir/folderRename/src/newmodule/another_importing.rell",
            "file:$tempDir/folderRename/src/newmodule/module.rell",
            "file:$tempDir/folderRename/src/main.rell",
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

        val rellFile = File(srcDir, "main.rell").apply {
            parentFile.mkdirs()
            writeText(fileContent)
        }.toURI()


        val configContent = """
                blockchains:
                  rellDappWithLib:
                    module: main
                
                compile:
                  rellVersion: 0.13.4
            """.trimIndent()
        val chromiaConfigFile = File(tempDir, "chromia.yml").apply {
            writeText(configContent)
        }

        clientServerLauncher.initializeServer(tempDir.toURI())
        val indexer = workspaceManager.indexers[srcDir.toURI()]!!

        chromiaConfigFile.writeText(configContent.replace("0.13.4", "0.14.1"))

        val fileEvent = FileEvent(chromiaConfigFile.toURI().toString(), FileChangeType.Changed)
        val didChangeParams = DidChangeWatchedFilesParams(listOf(fileEvent))
        server.didChangeWatchedFiles(didChangeParams)
        await().until { testClient.diagnostics.isNotEmpty() }

        val diagnostics = testClient.diagnostics
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
