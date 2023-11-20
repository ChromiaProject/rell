package net.postchain.rell.toolbox.lsp.server

import assertk.assertThat
import assertk.assertions.isNotEmpty
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
import org.koin.core.qualifier.named
import util.TestClient
import util.TestServerModule
import java.io.File
import java.io.IOException
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import kotlin.test.assertEquals


class InitializationTest {
    private lateinit var thread: Thread
    private lateinit var client: LanguageServer
    private lateinit var testClient: TestClient
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
        val serverResponse = client.initialize(initParams).get()
        client.initialized(InitializedParams())

        Thread.sleep(500)

        assertThat(testClient.diagnostics).isNotEmpty()
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
        var testWorkspaceFileURIs: MutableList<URI> = mutableListOf()
        val classLoader = javaClass.getClassLoader()
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
