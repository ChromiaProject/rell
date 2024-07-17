package net.postchain.rell.toolbox.lsp.launcher

import assertk.assertThat
import assertk.assertions.isNotNull
import net.postchain.rell.toolbox.lsp.server.LauncherType
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.qualifier.named
import util.TestClient
import util.TestServerModule
import java.io.IOException
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.test.assertEquals


class SocketServerLauncherTest {
    private lateinit var thread: Thread
    private lateinit var client: LanguageServer
    private val serverModule = TestServerModule()

    @BeforeEach
    fun setup() {
        val koinApp = serverModule.startKoin()
        val serverLauncher = koinApp.koin.get<AbstractServerLauncher>(named(LauncherType.SOCKET))
        thread = Thread {
            serverLauncher.launch(arrayOf())
        }
        thread.start()

        val socket = connectToServer()
        val clientLauncher =
            LSPLauncher.createClientLauncher(TestClient(), socket.getInputStream(), socket.getOutputStream())
        clientLauncher.startListening()

        client = clientLauncher.remoteProxy
    }

    @AfterEach
    fun tearDown() {
        serverModule.stopKoinGlobalContext()
        thread.interrupt()
    }

    @Test
    fun `Initiates language server request`() {
        val serverResponse = client.initialize(InitializeParams()).get()
        assertThat(serverResponse.capabilities).isNotNull()
    }

    private fun connectToServer(attempt: Int = 0): Socket {
        val maxRetryAttempts = 5
        if (attempt >= maxRetryAttempts) {
            throw SocketTimeoutException("Failed to connect to server after $maxRetryAttempts retries.")
        }
        return try {
            Socket("127.0.0.1", 5008)
        } catch (e: IOException) {
            Thread.sleep(500)
            connectToServer(attempt + 1)
        }
    }
}
