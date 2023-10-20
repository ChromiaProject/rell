package net.postchain.rell.toolbox.lsp.launcher

import net.postchain.rell.toolbox.lsp.server.RellLanguageServer
import org.eclipse.lsp4j.launch.LSPLauncher
import org.koin.java.KoinJavaComponent
import java.io.PrintWriter
import java.net.ServerSocket

class ServerLauncher {
    private val languageServer: RellLanguageServer by KoinJavaComponent.inject(RellLanguageServer::class.java)
    private val lspPort = 5008
    private val trace = "-trace"
    private val noValidate = "-noValidate"
    fun launch(args: Array<String>) {
        try {
            //TODO: StdioSocketServer default
            launchSocketServer(args)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun launchSocketServer(args: Array<String>) {
        println("Launching Socket Server")
        val validate: Boolean = shouldValidate(args)
        val trace: PrintWriter? = getTrace(args)

        ServerSocket(lspPort).use { socket ->
            val client = socket.accept()
            println("InputStream Test" + client.getInputStream())
            val launcher = LSPLauncher.createServerLauncher(
                languageServer,
                client.getInputStream(),
                client.getOutputStream(),
                validate,
                trace
            )
            languageServer.connect(launcher.remoteProxy)
            launcher.startListening()
        }
    }

    private fun getTrace(args: Array<String>) = if (args.contains(trace)) PrintWriter(System.out) else null

    private fun shouldValidate(args: Array<String>) = args.contains(noValidate)
}
