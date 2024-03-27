package net.postchain.rell.toolbox.lsp

import net.postchain.rell.toolbox.core.annotaions.ExcludeFromJacocoGeneratedReport
import net.postchain.rell.toolbox.lsp.launcher.AbstractServerLauncher
import net.postchain.rell.toolbox.lsp.server.LauncherType
import net.postchain.rell.toolbox.lsp.server.serverModule
import net.postchain.rell.toolbox.util.initializeLogger
import org.apache.logging.log4j.Level
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import java.util.Locale

@ExcludeFromJacocoGeneratedReport
fun main() {
    Locale.setDefault(Locale.US)

    val logLevel = Level.INFO
    val launcherType = LauncherType.STDIO

    initializeLogger(logLevel, launcherType)
    val app = startKoin {
        modules(
            serverModule
        )
    }

    val server = app.koin.get<AbstractServerLauncher>(named(launcherType))
    server.launch(arrayOf("-trace"))
}
