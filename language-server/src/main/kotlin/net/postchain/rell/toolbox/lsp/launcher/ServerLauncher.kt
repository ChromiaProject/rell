package net.postchain.rell.toolbox.lsp.launcher

import org.eclipse.lsp4j.launch.LSPLauncher
import java.io.*

class ServerLauncher(private val clientOutputStream: OutputStream) : AbstractServerLauncher() {
    private val serverInputStream: InputStream
    val serverOutputStream: OutputStream

    init {
        this.serverInputStream = PipedInputStream()
        this.serverOutputStream = PipedOutputStream(serverInputStream)
    }

    override fun launch(args: Array<String>) {
        try {
            logger.info { "Launching Language Server with Stdio as input and output stream..." }
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
            logger.info { "Server are listening on input stream" }
        } catch (e: Exception) {
            logger.error { "Exception while running language server: ${e.message}" }
        }
    }

}
