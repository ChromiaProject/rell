package util

import net.postchain.rell.toolbox.lsp.launcher.AbstractServerLauncher
import net.postchain.rell.toolbox.lsp.launcher.SocketServerLauncher
import net.postchain.rell.toolbox.lsp.launcher.StdioServerLauncher
import net.postchain.rell.toolbox.lsp.server.LauncherType
import net.postchain.rell.toolbox.lsp.server.RellDocumentService
import net.postchain.rell.toolbox.lsp.server.RellLanguageServer
import net.postchain.rell.toolbox.lsp.server.RellWorkspaceService
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import org.koin.core.KoinApplication
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

class TestServerModule {

    fun startKoin(): KoinApplication {
        return startKoin {
            modules(
                serverModule
            )
        }
    }

    fun stopKoinGlobalContext() {
        GlobalContext.stopKoin()
    }

    private val serverModule = module {
        single<WorkspaceService> { RellWorkspaceService() }

        single<TextDocumentService> { RellDocumentService() }

        singleOf(::RellLanguageServer)
        
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
