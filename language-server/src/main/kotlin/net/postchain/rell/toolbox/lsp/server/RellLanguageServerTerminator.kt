package net.postchain.rell.toolbox.lsp.server

import kotlin.system.exitProcess

class RellLanguageServerTerminator {

    private var hasBeenShutdown = false
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