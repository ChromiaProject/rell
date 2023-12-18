package net.postchain.rell.toolbox.lsp.references

import net.postchain.rell.toolbox.core.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.lsp.editing.Document
import net.postchain.rell.toolbox.lsp.symbols.RellSymbolService
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import java.net.URI

class RellReferenceService(val symbolService: RellSymbolService) {

    fun getReferenceLocations(
        fileUri: URI, document: Document, indexer: WorkspaceIndexer, position: Position?
    ): List<Location> {
        position ?: return listOf()
        val resource = indexer.getResource(fileUri) ?: return listOf()
        val symbolInfoWithInterval = symbolService.getSymbolForDocument(document, resource, position)
            ?: return listOf()
        val referenceIndexer = ReferenceIndexer(indexer.workspaceUri, indexer.fileUriResourceMap)
        return referenceIndexer.findAllReferences(fileUri, symbolInfoWithInterval)
    }
}