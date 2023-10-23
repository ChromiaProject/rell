package net.postchain.rell.toolbox.lsp.launcher

import io.github.oshai.kotlinlogging.KotlinLogging
import net.postchain.rell.toolbox.lsp.server.RellLanguageServer
import org.koin.java.KoinJavaComponent
import java.io.PrintWriter

abstract class AbstractServerLauncher {
    protected val languageServer: RellLanguageServer by KoinJavaComponent.inject(RellLanguageServer::class.java)
    protected val logger = KotlinLogging.logger {}
    protected val trace = "-trace"
    protected val noValidate = "-noValidate"
    abstract fun launch(args: Array<String>)

    protected fun getTrace(args: Array<String>) = if (args.contains(trace)) PrintWriter(System.out) else null

    protected fun shouldValidate(args: Array<String>) = args.contains(noValidate)
}