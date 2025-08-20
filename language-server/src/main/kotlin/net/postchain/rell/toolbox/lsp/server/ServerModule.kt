package net.postchain.rell.toolbox.lsp.server

import net.postchain.rell.toolbox.lsp.includeDefinition.DefaultLspSystemPropertiesProvider
import net.postchain.rell.toolbox.lsp.includeDefinition.LspSystemPropertiesProvider
import net.postchain.rell.toolbox.lsp.launcher.AbstractServerLauncher
import net.postchain.rell.toolbox.lsp.launcher.SocketServerLauncher
import net.postchain.rell.toolbox.lsp.launcher.StdioServerLauncher
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val serverModule = module {
    includes(commonServerModule)

    single<LspSystemPropertiesProvider> { DefaultLspSystemPropertiesProvider() }

    single(named(LauncherType.STDIO)) {
        StdioServerLauncher(
            System.`in`,
            System.out,
            get()
        )
    } bind AbstractServerLauncher::class

    single(named(LauncherType.SOCKET)) { SocketServerLauncher(get()) } bind AbstractServerLauncher::class
}

enum class LauncherType {
    SOCKET, STDIO
}
