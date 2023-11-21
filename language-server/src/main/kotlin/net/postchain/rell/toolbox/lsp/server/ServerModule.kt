package net.postchain.rell.toolbox.lsp.server

import net.postchain.rell.toolbox.core.tokens.RellSemanticTokensManager
import net.postchain.rell.toolbox.lsp.launcher.AbstractServerLauncher
import net.postchain.rell.toolbox.lsp.launcher.SocketServerLauncher
import net.postchain.rell.toolbox.lsp.launcher.StdioServerLauncher
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module


val serverModule = module {
    single { RellWorkspaceManager() }
    single { RellRequestManager() }
    single { RellLanguageServerTerminator() }
    single { CapabilitiesProvider() }
    single { RellSemanticTokensManager() }

    singleOf(::RellLanguageServer)

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

