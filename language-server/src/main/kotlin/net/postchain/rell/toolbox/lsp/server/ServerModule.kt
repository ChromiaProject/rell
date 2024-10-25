package net.postchain.rell.toolbox.lsp.server

import net.postchain.rell.toolbox.linter.FormattingStyleLinter
import net.postchain.rell.toolbox.linter.RellLinter
import net.postchain.rell.toolbox.lsp.caching.RellIndexCachingService
import net.postchain.rell.toolbox.lsp.caching.RellIndexSerializer
import net.postchain.rell.toolbox.lsp.completion.RellCompletionService
import net.postchain.rell.toolbox.lsp.editorconfig.RellFormatterOptionsResolver
import net.postchain.rell.toolbox.lsp.editorconfig.RellLinterOptionsResolver
import net.postchain.rell.toolbox.lsp.launcher.AbstractServerLauncher
import net.postchain.rell.toolbox.lsp.launcher.SocketServerLauncher
import net.postchain.rell.toolbox.lsp.launcher.StdioServerLauncher
import net.postchain.rell.toolbox.lsp.includeDefinition.DefaultLspIncludeDefinitionProvider
import net.postchain.rell.toolbox.lsp.includeDefinition.LspIncludeDefinitionProvider
import net.postchain.rell.toolbox.lsp.references.RellReferenceService
import net.postchain.rell.toolbox.lsp.symbols.RellSymbolService
import net.postchain.rell.toolbox.lsp.template.NewProjectTemplateService
import net.postchain.rell.toolbox.lsp.testrunner.RellTestRunner
import net.postchain.rell.toolbox.lsp.tokens.RellSemanticTokensManager
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val serverModule = module {
    single { RellSymbolService() }
    single { NewProjectTemplateService() }
    single { RellLinter() }
    single { FormattingStyleLinter() }
    singleOf(::RellIndexCachingService)
    singleOf(::RellIndexSerializer)
    single { RellReferenceService(get()) }
    singleOf(::RellWorkspaceManager)
    single { RellRequestManager() }
    single { RellFormatterOptionsResolver() }
    single { RellLinterOptionsResolver() }
    singleOf(::RellLanguageServerTerminator)
    single { CapabilitiesProvider() }
    single { RellSemanticTokensManager() }
    single<LspIncludeDefinitionProvider> { DefaultLspIncludeDefinitionProvider() }
    singleOf(::RellTestRunner)
    singleOf(::RellLanguageServer)
    singleOf(::RellFormattingManager)
    singleOf(::RellCompletionService)
    singleOf(::RellTextDocumentService)
    singleOf(::RellWorkspaceService)

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
