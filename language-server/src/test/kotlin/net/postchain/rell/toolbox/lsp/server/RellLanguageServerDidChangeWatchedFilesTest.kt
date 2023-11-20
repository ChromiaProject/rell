package net.postchain.rell.toolbox.lsp.server

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.FileEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.testcontainers.shaded.org.awaitility.Awaitility.await
import util.TestClient
import util.TestClientServerLauncher
import util.TestServerModule
import java.io.File
import kotlin.io.path.createDirectory

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
}
