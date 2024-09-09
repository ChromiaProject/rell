package net.postchain.rell.toolbox.lsp.launcher

import net.postchain.rell.toolbox.lsp.server.RellLanguageServer
import java.io.PrintWriter

abstract class AbstractServerLauncher(
    protected val languageServer: RellLanguageServer
) {
    protected val trace = "-trace"
    private val noValidate = "-noValidate"
    abstract fun launch(args: Array<String>)

    abstract fun setTracePrintWriter(args: Array<String>): PrintWriter?

    protected fun shouldValidate(args: Array<String>) = args.contains(noValidate)
}
