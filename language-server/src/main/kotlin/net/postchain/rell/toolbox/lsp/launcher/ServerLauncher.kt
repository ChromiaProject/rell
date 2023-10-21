package net.postchain.rell.toolbox.lsp.launcher

import io.github.oshai.kotlinlogging.KotlinLogging
import net.postchain.rell.toolbox.lsp.server.RellLanguageServer
import org.eclipse.lsp4j.launch.LSPLauncher
import org.koin.java.KoinJavaComponent
import java.io.PrintWriter
import java.net.ServerSocket

class ServerLauncher {
    private val languageServer: RellLanguageServer by KoinJavaComponent.inject(RellLanguageServer::class.java)
    private val logger = KotlinLogging.logger {}
    private val lspPort = 5008
    private val trace = "-trace"
    private val noValidate = "-noValidate"
    fun launch(args: Array<String>) {
        try {
            if (args.contains("-socket")) {
                launchSocketServer(args)
            } else {
                launchStdioServer(args)
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun launchSocketServer(args: Array<String>) {
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
    }

    private fun launchStdioServer(args: Array<String>) {
        logger.info { "Launching Language Server with Stdio as input and output stream..." }
        val validate: Boolean = shouldValidate(args)
        val trace: PrintWriter? = getTrace(args)
        val launcher = LSPLauncher.createServerLauncher(
            languageServer,
            System.`in`,
            System.out,
            validate,
            trace
        )
        languageServer.connect(launcher.remoteProxy)
        launcher.startListening()
        logger.info { "Server are listening on input stream" }
    }

    private fun getTrace(args: Array<String>) = if (args.contains(trace)) PrintWriter(System.out) else null

    private fun shouldValidate(args: Array<String>) = args.contains(noValidate)
}
