package net.postchain.rell.toolbox.lsp.launcher

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.rell.toolbox.lsp.server.LanguageServerImpl
import net.postchain.rell.toolbox.lsp.server.RellLanguageServer
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageServer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.core.logger.PrintLogger
import org.koin.dsl.module
import util.TestClient
import java.net.Socket


class ServerLauncherTest {
    lateinit var thread: Thread
    lateinit var client: LanguageServer

    @BeforeEach
    fun setup() {
        startKoin {
            logger(PrintLogger(Level.INFO))
            modules(module {
                single<RellLanguageServer> { LanguageServerImpl() }
            })
        }

        thread = Thread { ServerLauncher().launch(arrayOf()) }
        thread.start()

        val socket = Socket("0.0.0.0", 5008);
        val clientLauncher =
            LSPLauncher.createClientLauncher(TestClient(), socket.getInputStream(), socket.getOutputStream())
        clientLauncher.startListening()

        client = clientLauncher.remoteProxy
    }

    @Test
    fun `Socket Launcher initiates language server`() {
        val serverResponse = client.initialize(InitializeParams()).get()

        //TODO: Fix assertions
        assertThat(serverResponse.serverInfo).isEqualTo(null)
        assertThat(serverResponse.capabilities).isEqualTo(null)
    }
}
