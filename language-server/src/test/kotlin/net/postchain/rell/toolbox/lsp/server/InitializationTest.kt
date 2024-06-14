package net.postchain.rell.toolbox.lsp.server

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import java.io.File
import java.io.IOException
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.nio.file.Path
import net.postchain.rell.toolbox.core.indexer.findRellFilesInWorkspace
import net.postchain.rell.toolbox.lsp.launcher.AbstractServerLauncher
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.koin.core.qualifier.named
import org.testcontainers.shaded.org.awaitility.Awaitility.await
import util.TestClient
import util.TestServerModule

@Suppress("JAVA_CLASS_ON_COMPANION")
class InitializationTest {
    private lateinit var thread: Thread
    private lateinit var client: LanguageServer
    private lateinit var testClient: TestClient
    private lateinit var workspaceManager: RellWorkspaceManager
    private val serverModule = TestServerModule()

    @BeforeEach
    fun setupBeforeEach() {
        val koinApp = serverModule.startKoin()
        val serverLauncher = koinApp.koin.get<AbstractServerLauncher>(named(LauncherType.SOCKET))
        thread = Thread {
            serverLauncher.launch(arrayOf())
        }
        thread.start()
        testClient = TestClient()
        val socket = connectToServer()
        val clientLauncher =
            LSPLauncher.createClientLauncher(testClient, socket.getInputStream(), socket.getOutputStream())
        clientLauncher.startListening()

        client = clientLauncher.remoteProxy
        workspaceManager = koinApp.koin.get<RellWorkspaceManager>()
    }

    @AfterEach
    fun tearDown() {
        serverModule.stopKoinGlobalContext()
        thread.interrupt()
    }

    @Test
    fun `Initializers are run and errors are populated`() {
        val initParams = InitializeParams()
        initParams.workspaceFolders = listOf(WorkspaceFolder(testWorkspaceFolder.toURI().toString()))
        client.initialize(initParams).get()
        client.initialized(InitializedParams())

        await().until { testClient.diagnostics.isNotEmpty() }

        val rellFiles = testWorkspaceFolder.walk().filter { it.isFile && it.extension == "rell" }
            .map { parseFileUri(it.toURI().toString()).toString() }.toList().toTypedArray()

        assertThat(testClient.diagnostics.keys).containsExactlyInAnyOrder(*rellFiles)
    }

    @Test
    fun `Workspace folder is used as workspace uri with trailing slash when source dir is not found`(@TempDir dir: Path) {
        val pathAsString = dir.toUri().toString().removeSuffix("/")
        val initParams = InitializeParams()
        initParams.workspaceFolders = listOf(WorkspaceFolder(pathAsString))

        client.initialize(initParams).get()
        client.initialized(InitializedParams())

        await().until { workspaceManager.indexers.isNotEmpty() }

        val indexers = workspaceManager.indexers
        val expectedWorkspaceUri = parseFileUri("$pathAsString/")
        assertThat(indexers.keys).containsOnly(expectedWorkspaceUri)
        assertThat(indexers[expectedWorkspaceUri]!!.fileUriResourceMap).hasSize(0)
    }

    private fun connectToServer(attempt: Int = 0): Socket {
        val maxRetryAttempts = 5
        if (attempt >= maxRetryAttempts) {
            throw SocketTimeoutException("Failed to connect to server after $maxRetryAttempts retries.")
        }
        return try {
            Socket("127.0.0.1", 5008);
        } catch (e: IOException) {
            Thread.sleep(500)
            connectToServer(attempt + 1)
        }
    }

    companion object {
        private var testWorkspaceFileURIs: MutableList<URI> = mutableListOf()
        private val classLoader = javaClass.getClassLoader()
        val testWorkspaceFolder = File(classLoader.getResource("rellDappWithErrors").file)

        @JvmStatic
        @BeforeAll
        fun setupBeforeAll() {
            findRellFilesInWorkspace(
                testWorkspaceFolder,
                testWorkspaceFileURIs
            )
        }
    }
}
