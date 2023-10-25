package net.postchain.rell.toolbox.lsp

import net.postchain.rell.toolbox.lsp.launcher.ServerLauncher
import net.postchain.rell.toolbox.lsp.launcher.SocketServerLauncher
import net.postchain.rell.toolbox.lsp.server.RellLanguageServer
import net.postchain.rell.toolbox.util.initializeLogger
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module

fun main(args: Array<String>) {

    //TODO: Should read from args. Keeping it simple now for development
    val debug = false
    val launcherType = LauncherType.SOCKET

    val serverModule = getServerModule(launcherType)
    initializeLogger(debug, launcherType)

    startKoin {
        modules(
            serverModule
        )
    }
}

fun getServerModule(launcherType: LauncherType): Module {
    return when (launcherType) {
        LauncherType.STDIO -> serverModule
        LauncherType.SOCKET -> socketServerModule
    }
}

val socketServerModule = module {
    single<RellLanguageServer> { RellLanguageServer() }
    single { SocketServerLauncher(get()) }
}

val serverModule = module {
    single<RellLanguageServer> { RellLanguageServer() }
    single { ServerLauncher(System.`in`, System.out, get()) }
}

enum class LauncherType {
    SOCKET, STDIO;
}
