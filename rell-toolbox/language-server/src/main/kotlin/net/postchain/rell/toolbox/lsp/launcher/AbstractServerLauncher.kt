package net.postchain.rell.toolbox.lsp.launcher

import io.sentry.Sentry
import net.postchain.rell.toolbox.lsp.server.RellLanguageServer
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import java.io.PrintWriter

abstract class AbstractServerLauncher(
    protected val languageServer: RellLanguageServer
) {
    protected val trace = "-trace"
    private val noValidate = "-noValidate"
    private val devMode = "-devMode"
    abstract fun launch(args: Array<String>)

    abstract fun setTracePrintWriter(args: Array<String>): PrintWriter?

    protected fun shouldValidate(args: Array<String>) = args.contains(noValidate)

    protected fun defaultExceptionHandler(throwable: Throwable, args: Array<String>): ResponseError? {
        if (!args.contains(devMode)) {
            val reportedException = throwable.cause ?: throwable
            Sentry.captureException(reportedException)
        }
        return RemoteEndpoint.DEFAULT_EXCEPTION_HANDLER.apply(throwable)
    }
}
