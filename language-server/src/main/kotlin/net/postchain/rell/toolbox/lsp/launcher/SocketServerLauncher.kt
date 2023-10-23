package net.postchain.rell.toolbox.lsp.launcher

import org.eclipse.lsp4j.launch.LSPLauncher
import java.io.PrintWriter
import java.net.ServerSocket

class SocketServerLauncher : AbstractServerLauncher() {
    private val lspPort = 5008

    override fun launch(args: Array<String>) {
        try {
            logger.info { "Launching Language Server on socket: $lspPort..." }
            val validate: Boolean = shouldValidate(args)
            val trace: PrintWriter? = getTrace(args)

            ServerSocket(lspPort).use { socket ->
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
                logger.info { "Server are listening on input stream" }
            }

        } catch (e: Exception) {
            logger.error { "Exception while running language server: ${e.message}" }
        }
    }
}
