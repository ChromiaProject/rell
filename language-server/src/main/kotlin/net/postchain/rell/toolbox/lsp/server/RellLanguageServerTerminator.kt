package net.postchain.rell.toolbox.lsp.server

import net.postchain.rell.toolbox.lsp.caching.RellIndexCachingService
import kotlin.system.exitProcess

class RellLanguageServerTerminator(
    private val requestManager: RellRequestManager,
    private val indexCachingService: RellIndexCachingService
) {

    private var hasBeenShutdown = false
    fun shutdown() {
        hasBeenShutdown = true
        requestManager.shutdown()
        indexCachingService.shutdown()
    }

    fun exit() {
        if (hasBeenShutdown) {
            exitProcess(0)
        } else {
            exitProcess(1)
        }
    }
}
