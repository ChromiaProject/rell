package net.postchain.rell.toolbox.lsp

import net.postchain.rell.toolbox.lsp.launcher.ServerLauncher
import net.postchain.rell.toolbox.lsp.server.LanguageServerImpl
import net.postchain.rell.toolbox.lsp.server.RellLanguageServer
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.core.logger.PrintLogger
import org.koin.dsl.module

fun main(args: Array<String>) {
    startKoin {
        logger(PrintLogger(Level.INFO))
        modules(module {
            single<RellLanguageServer> { LanguageServerImpl() }
        })
    }
    ServerLauncher().launch(args)
}