/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.launcher

import assertk.assertThat
import assertk.assertions.isNotNull
import net.postchain.rell.toolbox.lsp.TestClient
import net.postchain.rell.toolbox.lsp.TestServerModule
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.parameter.parametersOf
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream

class StdioServerLauncherTest {
    private val serverModule = TestServerModule()
    private lateinit var client: LanguageServer

    @BeforeEach
    fun setup() {
        val serverStreams = createStreams()
        val clientStreams = createStreams()

        val koinApp = serverModule.startKoin()
        val params = parametersOf(serverStreams.inputStream, clientStreams.outputStream)
        val serverLauncher = koinApp.koin.get<AbstractServerLauncher> { params }
        serverLauncher.launch(arrayOf())

        val clientLauncher = LSPLauncher.createClientLauncher(
            TestClient(),
            clientStreams.inputStream,
            serverStreams.outputStream
        )
        clientLauncher.startListening()

        client = clientLauncher.remoteProxy
    }

    @AfterEach
    fun tearDown() {
        serverModule.stopKoinGlobalContext()
    }

    @Test
    fun `Initiates language server request`() {
        val serverResponse = client.initialize(InitializeParams()).get()
        assertThat(serverResponse.capabilities).isNotNull()
    }

    data class StreamPair(val inputStream: InputStream, val outputStream: OutputStream)

    private fun createStreams(): StreamPair {
        val inputStream = PipedInputStream()
        val outputStream = PipedOutputStream(inputStream)
        return StreamPair(inputStream, outputStream)
    }
}
