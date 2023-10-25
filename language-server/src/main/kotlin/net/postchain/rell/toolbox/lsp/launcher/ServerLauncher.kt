package net.postchain.rell.toolbox.lsp.launcher

import io.github.oshai.kotlinlogging.KotlinLogging
import net.postchain.rell.toolbox.lsp.server.RellLanguageServer
import org.eclipse.lsp4j.launch.LSPLauncher
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintWriter

class ServerLauncher(
    private val serverInputStream: InputStream,
    private val clientOutputStream: OutputStream,
    languageServer: RellLanguageServer
) : AbstractServerLauncher(languageServer) {
    private val logger = KotlinLogging.logger {}

    override fun launch(args: Array<String>) {
        try {
            logger.info { "Starting Rell Language Server..." }
            val validate: Boolean = shouldValidate(args)
            val trace: PrintWriter? = getTrace(args)
            val launcher = LSPLauncher.createServerLauncher(
                languageServer,
                serverInputStream,
                clientOutputStream,
                validate,
                trace
            )
            languageServer.connect(launcher.remoteProxy)
            launcher.startListening()
            logger.info { "Rell Language Server started." }
        } catch (e: Exception) {
            logger.error { "Exception while running Rell Language Server: ${e.message}" }
        }
    }

}
