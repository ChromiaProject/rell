package net.postchain.rell.toolbox.lsp.launcher

import net.postchain.rell.toolbox.lsp.server.RellLanguageServer
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageServer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import util.TestClient
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlin.test.assertEquals


class ServerLauncherTest {
    private lateinit var client: LanguageServer

    @BeforeEach
    fun setup() {
        val serverStreams = createStreams()
        val clientStreams = createStreams()

        val serverLauncher = ServerLauncher(serverStreams.inputStream, clientStreams.outputStream, RellLanguageServer())
        serverLauncher.launch(arrayOf())

        val clientLauncher = LSPLauncher.createClientLauncher(
            TestClient(),
            clientStreams.inputStream,
            serverStreams.outputStream
        )
        clientLauncher.startListening()

        client = clientLauncher.remoteProxy
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

    data class StreamPair(val inputStream: InputStream, val outputStream: OutputStream)

    private fun createStreams(): StreamPair {
        val inputStream = PipedInputStream()
        val outputStream = PipedOutputStream(inputStream)
        return StreamPair(inputStream, outputStream)
    }
}
