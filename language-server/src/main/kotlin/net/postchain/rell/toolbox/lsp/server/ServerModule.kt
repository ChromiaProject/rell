package net.postchain.rell.toolbox.lsp.server

import net.postchain.rell.toolbox.core.tokens.RellSemanticTokensManager
import net.postchain.rell.toolbox.lsp.caching.RellIndexCachingService
import net.postchain.rell.toolbox.lsp.caching.RellIndexSerializer
import net.postchain.rell.toolbox.lsp.editorconfig.RellFormatterOptionsResolver
import net.postchain.rell.toolbox.lsp.launcher.AbstractServerLauncher
import net.postchain.rell.toolbox.lsp.launcher.SocketServerLauncher
import net.postchain.rell.toolbox.lsp.launcher.StdioServerLauncher
import net.postchain.rell.toolbox.lsp.references.RellReferenceService
import net.postchain.rell.toolbox.lsp.symbols.RellSymbolService
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module


val serverModule = module {
    single { RellSymbolService() }
    single { RellIndexSerializer() }
    singleOf(::RellIndexCachingService)
    single { RellReferenceService(get()) }
    singleOf(::RellWorkspaceManager)
    single { RellRequestManager() }
    single { RellFormatterOptionsResolver(get()) }
    singleOf(::RellLanguageServerTerminator)
    single { CapabilitiesProvider() }
    single { RellSemanticTokensManager() }

    singleOf(::RellLanguageServer)
    singleOf(::RellFormattingManager)

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
    SOCKET, STDIO;
}

