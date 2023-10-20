package net.postchain.rell.toolbox.lsp.launcher

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.rell.toolbox.lsp.server.LanguageServerImpl
import net.postchain.rell.toolbox.lsp.server.RellLanguageServer
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.launch.LSPLauncher
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.core.logger.PrintLogger
import org.koin.dsl.module
import util.TestClient
import java.net.Socket


class ServerLauncherTest {

    @BeforeEach
    fun setup() {
        startKoin {
            logger(PrintLogger(Level.INFO))
            modules(module {
                single<RellLanguageServer> { LanguageServerImpl() }
            })
        }
        Thread { ServerLauncher().launch(arrayOf()) }.start()
    }

    @Test
    fun `Socket Launcher initiates language server`() {
        val socket = Socket("0.0.0.0", 5008);

        val clientLauncher =
            LSPLauncher.createClientLauncher(TestClient(), socket.getInputStream(), socket.getOutputStream())
        clientLauncher.startListening()

        val remoteProxy = clientLauncher.remoteProxy
        val params = InitializeParams()
        val serverResponse = remoteProxy.initialize(params).get()

        //TODO: Fix assertions
        assertThat(serverResponse.serverInfo).isEqualTo(null)
        assertThat(serverResponse.capabilities).isEqualTo(null)
    }

}
