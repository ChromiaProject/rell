/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.references

import net.postchain.rell.toolbox.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.indexer.references.ReferenceIndexer
import net.postchain.rell.toolbox.lsp.editing.Document
import net.postchain.rell.toolbox.lsp.symbols.RellSymbolService
import net.postchain.rell.toolbox.util.toLspLocation
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import java.net.URI

class RellReferenceService(val symbolService: RellSymbolService) {

    fun getReferenceLocations(
        fileUri: URI,
        document: Document,
        indexer: WorkspaceIndexer,
        position: Position?
    ): List<Location> {
        position ?: return listOf()
        val symbolInfoWithInterval = symbolService.getSymbolInfoWithInterval(document, indexer, position)
            ?: return listOf()
        val referenceIndexer = ReferenceIndexer(indexer.workspaceUri, indexer.fileUriResourceMap)
        return toLspLocations(referenceIndexer.findAllReferences(fileUri, symbolInfoWithInterval))
    }

    private fun toLspLocations(references: List<net.postchain.rell.toolbox.common.Location>): List<Location> {
        return references.map { it.toLspLocation() }
    }
}
