package net.postchain.rell.toolbox.lsp

import net.postchain.rell.toolbox.lsp.launcher.AbstractServerLauncher
import net.postchain.rell.toolbox.lsp.server.LauncherType
import net.postchain.rell.toolbox.lsp.server.RellLanguageServer
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageServer
import org.koin.core.KoinApplication
import org.koin.core.qualifier.named
import org.testcontainers.shaded.org.awaitility.Awaitility.await
import java.io.IOException
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI

class TestClientServerLauncher(private val koinApp: KoinApplication) {
    private lateinit var thread: Thread
    private lateinit var client: LanguageServer
    val testClient: TestClient = TestClient()

    fun launch() {
        val serverLauncher = koinApp.koin.get<AbstractServerLauncher>(named(LauncherType.SOCKET))
        thread = Thread {
            serverLauncher.launch(arrayOf())
        }
        thread.start()
        val socket = connectToServer()
        val clientLauncher =
            LSPLauncher.createClientLauncher(testClient, socket.getInputStream(), socket.getOutputStream())
        clientLauncher.startListening()

        client = clientLauncher.remoteProxy
    }

    fun stop() {
        thread.interrupt()
    }

    fun initializeServer(workspaceUri: URI, clearDiagnostic: Boolean = true) {
        val initParams = InitializeParams()
        initParams.workspaceFolders = listOf(WorkspaceFolder(workspaceUri.toString()))
        client.initialize(initParams).get()
        client.initialized(InitializedParams())

        val server = koinApp.koin.get<RellLanguageServer>()
        await().until { server.initialized.isDone }

        if (clearDiagnostic) {
            testClient.clearDiagnostics()
        }
    }

    private fun connectToServer(attempt: Int = 0): Socket {
        val maxRetryAttempts = 5
        if (attempt >= maxRetryAttempts) {
            throw SocketTimeoutException("Failed to connect to server after $maxRetryAttempts retries.")
        }
        return try {
            Socket("127.0.0.1", 5008)
        } catch (@Suppress("SwallowedException") e: IOException) {
            Thread.sleep(500)
            connectToServer(attempt + 1)
        }
    }
}
