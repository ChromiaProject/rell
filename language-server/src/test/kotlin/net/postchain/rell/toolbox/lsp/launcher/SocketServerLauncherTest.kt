package net.postchain.rell.toolbox.lsp.launcher

import net.postchain.rell.toolbox.lsp.server.RellDocumentService
import net.postchain.rell.toolbox.lsp.server.RellLanguageServer
import net.postchain.rell.toolbox.lsp.server.RellWorkspaceService
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import util.TestClient
import java.net.Socket
import kotlin.test.assertEquals


class SocketServerLauncherTest {
    private lateinit var thread: Thread
    private lateinit var client: LanguageServer

    @BeforeEach
    fun setup() {
        thread = Thread {
            val rellLanguageServer = RellLanguageServer(RellDocumentService(), RellWorkspaceService())
            SocketServerLauncher(rellLanguageServer).launch(arrayOf())
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
