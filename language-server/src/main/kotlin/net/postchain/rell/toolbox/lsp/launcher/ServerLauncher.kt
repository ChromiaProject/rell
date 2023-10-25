package net.postchain.rell.toolbox.lsp.launcher

import io.github.oshai.kotlinlogging.KotlinLogging
import net.postchain.rell.toolbox.lsp.server.RellLanguageServer
import org.eclipse.lsp4j.launch.LSPLauncher
import java.io.*


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
            redirectStandardStreams(trace)
            languageServer.connect(launcher.remoteProxy)
            launcher.startListening()
            logger.info { "Rell Language Server started." }
        } catch (e: Exception) {
            logger.error { "Exception while running Rell Language Server: ${e.message}" }
        }
    }

    private fun redirectStandardStreams(trace: PrintWriter?) {
        val newOutputStream = if (trace == null) {
            ByteArrayOutputStream.nullOutputStream()
        } else {
            val outputFile = File("output.log")
            FileOutputStream(outputFile)
        }
        val newInputStream = ByteArrayInputStream(byteArrayOf(0))
        System.setIn(newInputStream)
        System.setOut(PrintStream(newOutputStream))
    }
}
