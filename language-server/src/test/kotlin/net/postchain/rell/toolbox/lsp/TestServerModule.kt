package net.postchain.rell.toolbox.lsp

import net.postchain.rell.toolbox.linter.FormattingStyleLinter
import net.postchain.rell.toolbox.linter.RellLinter
import net.postchain.rell.toolbox.lsp.caching.RellIndexCachingService
import net.postchain.rell.toolbox.lsp.caching.RellIndexSerializer
import net.postchain.rell.toolbox.lsp.completion.RellCompletionService
import net.postchain.rell.toolbox.lsp.editorconfig.RellFormatterOptionsResolver
import net.postchain.rell.toolbox.lsp.editorconfig.RellLinterOptionsResolver
import net.postchain.rell.toolbox.lsp.includeDefinition.LspSystemPropertiesProvider
import net.postchain.rell.toolbox.lsp.launcher.AbstractServerLauncher
import net.postchain.rell.toolbox.lsp.inlayhints.RellInlayHintsManager
import net.postchain.rell.toolbox.lsp.inlayhints.RellInlayHintsProvider
import net.postchain.rell.toolbox.lsp.launcher.SocketServerLauncher
import net.postchain.rell.toolbox.lsp.launcher.StdioServerLauncher
import net.postchain.rell.toolbox.lsp.references.RellReferenceService
import net.postchain.rell.toolbox.lsp.server.CapabilitiesProvider
import net.postchain.rell.toolbox.lsp.server.LauncherType
import net.postchain.rell.toolbox.lsp.server.RellDiagnosticsManager
import net.postchain.rell.toolbox.lsp.server.RellDocumentManager
import net.postchain.rell.toolbox.lsp.server.RellFormattingManager
import net.postchain.rell.toolbox.lsp.server.RellIndexingManager
import net.postchain.rell.toolbox.lsp.server.RellLanguageServer
import net.postchain.rell.toolbox.lsp.server.RellLanguageServerTerminator
import net.postchain.rell.toolbox.lsp.server.RellRequestManager
import net.postchain.rell.toolbox.lsp.server.RellTextDocumentService
import net.postchain.rell.toolbox.lsp.server.RellWorkspaceManager
import net.postchain.rell.toolbox.lsp.server.RellWorkspaceService
import net.postchain.rell.toolbox.lsp.server.utils.TestLspSystemPropertiesProvider
import net.postchain.rell.toolbox.lsp.symbols.RellCompletionSymbolService
import net.postchain.rell.toolbox.lsp.symbols.RellSymbolService
import net.postchain.rell.toolbox.lsp.template.ProjectTemplateService
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
        singleOf(::RellSymbolService)
        singleOf(::RellCompletionSymbolService)
        singleOf(::ProjectTemplateService)
        singleOf(::RellLinter)
        singleOf(::FormattingStyleLinter)
        singleOf(::RellIndexCachingService)
        singleOf(::RellIndexSerializer)
        singleOf(::RellReferenceService)
        singleOf(::RellWorkspaceManager)
        singleOf(::RellFormatterOptionsResolver)
        singleOf(::RellLinterOptionsResolver)
        singleOf(::RellRequestManager)
        singleOf(::RellLanguageServerTerminator)
        singleOf(::CapabilitiesProvider)
        singleOf(::RellSemanticTokensManager)
        single<LspSystemPropertiesProvider> {
            TestLspSystemPropertiesProvider(includeDefinition, issueCaching, resolveCompletion)
        }
        singleOf(::RellTestRunner)
        singleOf(::RellLanguageServer)
        singleOf(::RellFormattingManager)
        singleOf(::RellCompletionService)
        singleOf(::RellTextDocumentService)
        singleOf(::RellWorkspaceService)
        singleOf(::RellDocumentManager)
        singleOf(::RellDiagnosticsManager)
        singleOf(::RellIndexingManager)
        singleOf(::RellInlayHintsManager)
        singleOf(::RellInlayHintsProvider)

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
