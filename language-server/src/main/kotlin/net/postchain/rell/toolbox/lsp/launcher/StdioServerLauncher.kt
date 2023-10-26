package net.postchain.rell.toolbox.lsp.launcher

import io.github.oshai.kotlinlogging.KotlinLogging
import net.postchain.rell.toolbox.lsp.server.RellLanguageServer
import net.postchain.rell.toolbox.util.getCurrentLogFileName
import org.eclipse.lsp4j.launch.LSPLauncher
import java.io.*


class StdioServerLauncher(
    private val serverInputStream: InputStream,
    private val clientOutputStream: OutputStream,
    languageServer: RellLanguageServer
) : AbstractServerLauncher(languageServer) {
    private val logger = KotlinLogging.logger {}

    override fun launch(args: Array<String>) {
        try {
            logger.info { "Starting Rell Language Server..." }
            val validate: Boolean = shouldValidate(args)
            val trace: PrintWriter? = setTracePrintWriter(args)
            val launcher = LSPLauncher.createServerLauncher(
                languageServer,
                serverInputStream,
                clientOutputStream,
                validate,
                trace
            )
            redirectStandardStreams()
            languageServer.connect(launcher.remoteProxy)
            launcher.startListening()
            logger.info { "Rell Language Server started." }
        } catch (e: Exception) {
            logger.error { "Exception while running Rell Language Server: ${e.message}" }
        }
    }

    // When trace is enabled we print the trace to the same file as the logger currently is writing too.
    // If trace is not set, we return null
    override fun setTracePrintWriter(args: Array<String>): PrintWriter? {
        val currentLogFileName = getCurrentLogFileName()
        return if (args.contains(trace) && currentLogFileName.isNotEmpty()) {
            PrintWriter(FileOutputStream(getCurrentLogFileName(), true), true)
        } else null
    }

    // We redirect the standard input and output streams, so that we do not get any interference from these
    // streams while our server is listening and writing to them.
    private fun redirectStandardStreams() {
        val newInputStream = ByteArrayInputStream(byteArrayOf(0))
        val newOutputStream = ByteArrayOutputStream.nullOutputStream()
        System.setIn(newInputStream)
        System.setOut(PrintStream(newOutputStream))
    }
}
