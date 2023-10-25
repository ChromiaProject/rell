package net.postchain.rell.toolbox.lsp

import net.postchain.rell.toolbox.lsp.launcher.ServerLauncher
import net.postchain.rell.toolbox.lsp.launcher.SocketServerLauncher
import net.postchain.rell.toolbox.lsp.server.LanguageServerImpl
import net.postchain.rell.toolbox.lsp.server.RellLanguageServer
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.core.logger.PrintLogger
import org.koin.core.module.Module
import org.koin.dsl.module

fun main(args: Array<String>) {

    //TODO: Should read from args. Keeping it simple now for development
    val debug = false

    startKoin {
        logger(PrintLogger(Level.INFO))
        modules(
            getServerModule(debug)
        )
    }
}

fun getServerModule(debug: Boolean): Module {
    if (debug) return socketServerModule
    return serverModule
}

val socketServerModule = module {
    single<RellLanguageServer> { LanguageServerImpl() }
    single { SocketServerLauncher(get()) }
}

val serverModule = module {
    single<RellLanguageServer> { LanguageServerImpl() }
    single { ServerLauncher(System.`in`, System.out, get()) }
}
