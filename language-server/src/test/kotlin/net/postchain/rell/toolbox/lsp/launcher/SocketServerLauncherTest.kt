package net.postchain.rell.toolbox.lsp.launcher

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
import java.net.Socket
import kotlin.test.assertEquals


class SocketServerLauncherTest {
    private lateinit var thread: Thread
    private lateinit var client: LanguageServer
    private val serverModule = TestServerModule()

    @BeforeEach
    fun setup() {
        thread = Thread {
            val koinApp = serverModule.startKoin()
            val serverLauncher = koinApp.koin.get<AbstractServerLauncher>(named(LauncherType.SOCKET))
            serverLauncher.launch(arrayOf())
        }
        thread.start()

        val socket = Socket("0.0.0.0", 5008);
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
        assertEquals(
            serverResponse.toString(), "InitializeResult [\n" +
                    "  capabilities = null\n" +
                    "  serverInfo = null\n" +
                    "]"
        )
    }
}
