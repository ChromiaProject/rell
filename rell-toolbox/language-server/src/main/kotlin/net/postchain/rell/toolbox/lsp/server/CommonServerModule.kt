package net.postchain.rell.toolbox.lsp.server

import net.postchain.rell.toolbox.linter.FormattingStyleLinter
import net.postchain.rell.toolbox.linter.RellLinter
import net.postchain.rell.toolbox.lsp.caching.RellIndexCachingService
import net.postchain.rell.toolbox.lsp.caching.RellIndexSerializer
import net.postchain.rell.toolbox.lsp.completion.CompletionItemFactory
import net.postchain.rell.toolbox.lsp.completion.RellCompletionService
import net.postchain.rell.toolbox.lsp.editorconfig.RellFormatterOptionsResolver
import net.postchain.rell.toolbox.lsp.editorconfig.RellLinterOptionsResolver
import net.postchain.rell.toolbox.lsp.inlayhints.RellInlayHintsManager
import net.postchain.rell.toolbox.lsp.inlayhints.RellInlayHintsProvider
import net.postchain.rell.toolbox.lsp.references.RellReferenceService
import net.postchain.rell.toolbox.lsp.symbols.RellCompletionSymbolService
import net.postchain.rell.toolbox.lsp.symbols.RellSymbolService
import net.postchain.rell.toolbox.lsp.template.ProjectTemplateService
import net.postchain.rell.toolbox.lsp.testrunner.RellTestRunner
import net.postchain.rell.toolbox.lsp.tokens.RellSemanticTokensManager
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val commonServerModule = module {
    singleOf(::RellSymbolService)
    singleOf(::RellCompletionSymbolService)
    singleOf(::ProjectTemplateService)
    singleOf(::RellLinter)
    singleOf(::FormattingStyleLinter)
    singleOf(::RellIndexCachingService)
    singleOf(::RellIndexSerializer)
    singleOf(::RellReferenceService)
    singleOf(::RellRenamingService)
    singleOf(::RellWorkspaceManager)
    singleOf(::RellRequestManager)
    singleOf(::RellFormatterOptionsResolver)
    singleOf(::RellLinterOptionsResolver)
    singleOf(::RellLanguageServerTerminator)
    singleOf(::CapabilitiesProvider)
    singleOf(::RellSemanticTokensManager)
    singleOf(::RellTestRunner)
    singleOf(::RellLanguageServer)
    singleOf(::RellFormattingManager)
    singleOf(::CompletionItemFactory)
    singleOf(::RellCompletionService)
    singleOf(::RellTextDocumentService)
    singleOf(::RellWorkspaceService)
    singleOf(::RellDocumentManager)
    singleOf(::RellDiagnosticsManager)
    singleOf(::FileChangeHandler)
    singleOf(::RellIndexingManager)
    singleOf(::RellInlayHintsManager)
    singleOf(::RellInlayHintsProvider)
}
