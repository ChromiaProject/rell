package net.postchain.rell.toolbox.lsp

import net.postchain.rell.toolbox.linter.FormattingStyleLinter
import net.postchain.rell.toolbox.linter.RellLinter
import net.postchain.rell.toolbox.lsp.caching.RellIndexCachingService
import net.postchain.rell.toolbox.lsp.caching.RellIndexSerializer
import net.postchain.rell.toolbox.lsp.editorconfig.RellFormatterOptionsResolver
import net.postchain.rell.toolbox.lsp.editorconfig.RellLinterOptionsResolver
import net.postchain.rell.toolbox.lsp.launcher.AbstractServerLauncher
import net.postchain.rell.toolbox.lsp.launcher.SocketServerLauncher
import net.postchain.rell.toolbox.lsp.launcher.StdioServerLauncher
import net.postchain.rell.toolbox.lsp.references.RellReferenceService
import net.postchain.rell.toolbox.lsp.server.CapabilitiesProvider
import net.postchain.rell.toolbox.lsp.server.LauncherType
import net.postchain.rell.toolbox.lsp.server.RellFormattingManager
import net.postchain.rell.toolbox.lsp.server.RellLanguageServer
import net.postchain.rell.toolbox.lsp.server.RellLanguageServerTerminator
import net.postchain.rell.toolbox.lsp.server.RellRequestManager
import net.postchain.rell.toolbox.lsp.server.RellWorkspaceManager
import net.postchain.rell.toolbox.lsp.symbols.RellSymbolService
import net.postchain.rell.toolbox.lsp.template.NewProjectTemplateService
import net.postchain.rell.toolbox.lsp.testrunner.RellTestRunner
import net.postchain.rell.toolbox.lsp.tokens.RellSemanticTokensManager
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
        single { RellSymbolService() }
        single { NewProjectTemplateService() }
        single { RellLinter() }
        single { FormattingStyleLinter() }
        singleOf(::RellIndexCachingService)
        singleOf(::RellIndexSerializer)
        single { RellReferenceService(get()) }
        singleOf(::RellWorkspaceManager)
        single { RellFormatterOptionsResolver() }
        single { RellLinterOptionsResolver() }
        single { RellRequestManager() }
        singleOf(::RellLanguageServerTerminator)
        single { CapabilitiesProvider() }
        single { RellSemanticTokensManager() }

        singleOf(::RellTestRunner)
        singleOf(::RellLanguageServer)
        singleOf(::RellFormattingManager)

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
