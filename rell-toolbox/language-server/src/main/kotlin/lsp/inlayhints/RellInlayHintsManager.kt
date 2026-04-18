/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.inlayhints

import net.postchain.rell.toolbox.lsp.server.RellIndexingManager
import org.eclipse.lsp4j.InlayHint
import org.eclipse.lsp4j.Range
import java.net.URI

class RellInlayHintsManager(
    private val indexingManager: RellIndexingManager,
    private val inlayHintsProvider: RellInlayHintsProvider,
) {
    var inlayHintsConfig = RellInlayHintsConfig()

    // TODO: implement type hints for 'struct(..)', 'create entity(..)' ...
    fun getInlayHints(fileUri: URI, range: Range): List<InlayHint> =
        indexingManager.getResource(fileUri)?.let { resource ->
            inlayHintsProvider.provideInlayHints(resource, range, inlayHintsConfig)
        } ?: emptyList()

    fun resolveInlayHint(hint: InlayHint): InlayHint = hint

    fun updateConfig(newConfig: RellInlayHintsConfig) {
        this.inlayHintsConfig = newConfig
    }
}
