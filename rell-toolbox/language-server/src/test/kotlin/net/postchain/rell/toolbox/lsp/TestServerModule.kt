package net.postchain.rell.toolbox.lsp

import net.postchain.rell.toolbox.lsp.includeDefinition.LspSystemPropertiesProvider
import net.postchain.rell.toolbox.lsp.launcher.AbstractServerLauncher
import net.postchain.rell.toolbox.lsp.launcher.SocketServerLauncher
import net.postchain.rell.toolbox.lsp.launcher.StdioServerLauncher
import net.postchain.rell.toolbox.lsp.server.LauncherType
import net.postchain.rell.toolbox.lsp.server.commonServerModule
import net.postchain.rell.toolbox.lsp.server.utils.TestLspSystemPropertiesProvider
import org.koin.core.KoinApplication
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

class TestServerModule {

    fun startKoin(includeDefinition: Boolean = true, issueCaching: Boolean = false, resolveCompletion: Boolean = false): KoinApplication {
        return startKoin {
            modules(
                serverModule(includeDefinition, issueCaching, resolveCompletion)
            )
        }
    }

    fun stopKoinGlobalContext() {
        GlobalContext.stopKoin()
    }

    private fun serverModule(includeDefinition: Boolean, issueCaching: Boolean, resolveCompletion: Boolean) = module {
        includes(commonServerModule)

        single<LspSystemPropertiesProvider> {
            TestLspSystemPropertiesProvider(includeDefinition, issueCaching, resolveCompletion)
        }

        single { params ->
            StdioServerLauncher(
                params.get(),
                params.get(),
                get()
            )
        } bind AbstractServerLauncher::class

        single(named(LauncherType.SOCKET)) { SocketServerLauncher(get()) } bind AbstractServerLauncher::class
    }
}
