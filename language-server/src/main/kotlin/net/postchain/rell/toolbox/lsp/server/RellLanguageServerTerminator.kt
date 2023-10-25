package net.postchain.rell.toolbox.lsp.server

import kotlin.system.exitProcess

class RellLanguageServerTerminator {

    //TODO: False as default. Needs shutdown request implementation in language server
    private var hasBeenShutdown = true
    fun shutdown() {
        hasBeenShutdown = true
    }

    fun exit() {
        if (hasBeenShutdown) {
            exitProcess(0);
        } else {
            exitProcess(1);
        }
    }
}