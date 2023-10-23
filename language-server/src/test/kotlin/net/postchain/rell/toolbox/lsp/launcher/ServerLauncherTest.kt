package net.postchain.rell.toolbox.lsp.launcher

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.rell.toolbox.lsp.server.LanguageServerImpl
import net.postchain.rell.toolbox.lsp.server.RellLanguageServer
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.KoinApplication
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.core.logger.PrintLogger
import org.koin.dsl.module
import util.TestClient
import java.io.PipedInputStream
import java.io.PipedOutputStream


class ServerLauncherTest {
    lateinit var client: LanguageServer
    lateinit var koinApplication: KoinApplication

    @BeforeEach
    fun setup() {
        koinApplication = startKoin {
            logger(PrintLogger(Level.INFO))
            modules(module {
                single<RellLanguageServer> { LanguageServerImpl() }
            })
        }

        val clientInputStream = PipedInputStream()
        val clientOutputStream = PipedOutputStream(clientInputStream)

        val serverLauncher = ServerLauncher(clientOutputStream)
        serverLauncher.launch(arrayOf())

        val clientLauncher = LSPLauncher.createClientLauncher(
            TestClient(),
            clientInputStream,
            serverLauncher.serverOutputStream
        )
        clientLauncher.startListening()

        client = clientLauncher.remoteProxy
    }

    @AfterEach
    fun teardown() {
        GlobalContext.stopKoin()
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
