/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.symbols

import net.postchain.rell.base.utils.ide.IdeSymbolInfo
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.lsp.editing.Document
import org.antlr.v4.runtime.misc.Interval
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SymbolKind
import java.net.URI

class RellCompletionSymbolService(private val symbolService: RellSymbolService) {
    fun getActiveImportSymbol(
        fileUri: URI,
        offset: Int,
        document: Document,
        indexer: WorkspaceIndexer
    ): DocumentSymbol? {
        val resource = indexer.getResource(fileUri) ?: return null
        return symbolService.getDocumentSymbols(
            fileUri,
            document,
            resource
        )?.children?.let { findPackageSymbol(it, offset, document) }
    }

    fun getSymbolInfoForImportedModule(
        symbol: DocumentSymbol,
        document: Document,
        indexer: WorkspaceIndexer
    ): List<IdeSymbolInfo> {
        val (startOffset, endOffset) = document.getStartAndEndOffset(symbol.range)
        val importPath = document.getTextIn(Interval.of(startOffset, endOffset)).substringBeforeLast(".")
        val position = Position(symbol.range.start.line, importPath.length)
        val symbolLocation = symbolService.getSymbolLocationsWithSymbol(document, indexer, position)
        val moduleName = symbolLocation?.second?.doc?.symbolName?.toString() ?: return listOf()
        return getModuleSymbols(indexer, moduleName)
    }

    fun findEnclosingFileOrNamespace(
        fileUri: URI,
        document: Document,
        resource: Resource,
        offset: Int
    ): DocumentSymbol? {
        val position = document.getPosition(offset)
        val documentSymbols = symbolService.getDocumentSymbols(fileUri, document, resource)
        return documentSymbols?.let { findEnclosingFileOrNamespaceSymbol(it, position) }
    }

    private fun findPackageSymbol(
        symbols: List<DocumentSymbol>,
        offset: Int,
        document: Document
    ): DocumentSymbol? {
        return symbols.firstOrNull { symbol ->
            document.offSetInRange(offset, symbol.range)
        }?.let { symbol ->
            when (symbol.kind) {
                SymbolKind.Package -> symbol
                SymbolKind.Namespace -> symbol.children?.let { findPackageSymbol(it, offset, document) }
                else -> null
            }
        }
    }

    private fun getModuleSymbols(indexer: WorkspaceIndexer, moduleName: String): List<IdeSymbolInfo> {
        return indexer.resources
            .asSequence()
            .filter { resource ->
                resource.moduleInfo?.name?.toString() == moduleName
            }
            .mapNotNull { resource ->
                if (isResourceFromCache(resource)) {
                    indexer.updateFileUriResourceMap(resource.fileUri)
                } else {
                    resource
                }
            }
            .flatMap { resource ->
                resource.symbolInfos.values.asSequence()
                    .filter {
                        it.link == null && it.kind in RellRelevantImportSymbol.getAllIdeSymbolKinds()
                    }
            }
            .toList()
    }

    private fun isResourceFromCache(resource: Resource): Boolean {
        return resource.symbolInfos.any { it.value.doc == null }
    }

    private fun findEnclosingFileOrNamespaceSymbol(node: DocumentSymbol, position: Position): DocumentSymbol? {
        if (!containsPosition(node.range, position)) {
            return null
        }

        val matchingChild = findChildContainingPosition(node, position)
        return when (node.kind) {
            SymbolKind.File -> when {
                matchingChild == null -> node
                matchingChild.kind == SymbolKind.Namespace ->
                    findEnclosingFileOrNamespaceSymbol(matchingChild, position)
                else -> null
            }

            SymbolKind.Namespace -> when {
                matchingChild == null -> node
                matchingChild.kind == SymbolKind.Namespace ->
                    findEnclosingFileOrNamespaceSymbol(matchingChild, position)
                else -> null
            }

            else -> null
        }
    }

    private fun findChildContainingPosition(node: DocumentSymbol, position: Position): DocumentSymbol? {
        return node.children?.firstOrNull { child ->
            containsPosition(child.range, position)
        }
    }

    private fun containsPosition(range: Range, position: Position): Boolean {
        return when {
            position.line < range.start.line -> false
            position.line > range.end.line -> false
            position.line == range.start.line && position.character < range.start.character -> false
            position.line == range.end.line && position.character > range.end.character -> false
            else -> true
        }
    }
}
