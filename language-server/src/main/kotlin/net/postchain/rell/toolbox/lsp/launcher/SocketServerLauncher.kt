package net.postchain.rell.toolbox.lsp.launcher

import io.github.oshai.kotlinlogging.KotlinLogging
import net.postchain.rell.toolbox.lsp.server.RellLanguageServer
import org.eclipse.lsp4j.launch.LSPLauncher
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket

class SocketServerLauncher(languageServer: RellLanguageServer) : AbstractServerLauncher(languageServer) {
    private val logger = KotlinLogging.logger {}
    private val lspPort = 5008

    override fun launch(args: Array<String>) {
        try {
            logger.info { "Starting Rell Language Server on port: $lspPort..." }
            val validate: Boolean = shouldValidate(args)
            val trace: PrintWriter? = setTracePrintWriter(args)

            val maxQueueLengthOfIncommingConnections = 50
            ServerSocket(
                lspPort,
                maxQueueLengthOfIncommingConnections,
                InetAddress.getLoopbackAddress()
            ).use { socket ->
                val client = socket.accept()
                val launcher = LSPLauncher.createServerLauncher(
                    languageServer,
                    client.getInputStream(),
                    client.getOutputStream(),
                    validate,
                    trace
                )
                languageServer.connect(launcher.remoteProxy)
                launcher.startListening()
                logger.info { "Rell Language Server started." }
            }

        } catch (e: Exception) {
            logger.error { "Exception while running language server: ${e.message}" }
        }
    }

    override fun setTracePrintWriter(args: Array<String>): PrintWriter? =
        if (args.contains(trace)) PrintWriter(System.out) else null

}
