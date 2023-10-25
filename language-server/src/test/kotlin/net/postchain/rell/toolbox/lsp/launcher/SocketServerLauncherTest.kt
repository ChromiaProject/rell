package net.postchain.rell.toolbox.lsp.launcher

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.rell.toolbox.lsp.server.LanguageServerImpl
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import util.TestClient
import java.net.Socket


class SocketServerLauncherTest {
    private lateinit var thread: Thread
    private lateinit var client: LanguageServer

    @BeforeEach
    fun setup() {
        thread = Thread { SocketServerLauncher(LanguageServerImpl()).launch(arrayOf()) }
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

        assertThat(serverResponse.serverInfo).isEqualTo(null)
        assertThat(serverResponse.capabilities).isEqualTo(null)
    }

    companion object {
        @JvmStatic
        @AfterAll
        fun printMemoryUsage(): Unit {
            val rt = Runtime.getRuntime()
            val total = rt.totalMemory().toDouble() / 1000000.0
            val free = rt.freeMemory().toDouble() / 1000000.0
            println("Memory after test: ${total - free} MB used / $total MB total")
        }
    }
}
