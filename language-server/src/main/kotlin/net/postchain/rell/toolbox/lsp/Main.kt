package net.postchain.rell.toolbox.lsp

import net.postchain.rell.toolbox.lsp.launcher.AbstractServerLauncher
import net.postchain.rell.toolbox.lsp.server.LauncherType
import net.postchain.rell.toolbox.lsp.server.serverModule
import net.postchain.rell.toolbox.util.initializeLogger
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named

fun main(args: Array<String>) {

    //TODO: Should read from args. Keeping it simple now for development
    val debug = false
    val launcherType = LauncherType.SOCKET

    initializeLogger(debug, launcherType)

    val app = startKoin {
        modules(
            serverModule
        )
    }

    val server = app.koin.get<AbstractServerLauncher>(named(launcherType))
    server.launch(args)
}

