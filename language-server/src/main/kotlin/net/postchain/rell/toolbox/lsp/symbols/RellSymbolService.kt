package net.postchain.rell.toolbox.lsp.symbols

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.utils.ide.IdeApi
import net.postchain.rell.base.utils.ide.IdeFilePath
import net.postchain.rell.base.utils.ide.IdeGlobalSymbolLink
import net.postchain.rell.base.utils.ide.IdeLocalSymbolLink
import net.postchain.rell.base.utils.ide.IdeModuleSymbolLink
import net.postchain.rell.base.utils.ide.IdeSymbolGlobalId
import net.postchain.rell.base.utils.ide.IdeSymbolInfo
import net.postchain.rell.base.utils.ide.IdeSymbolKind
import net.postchain.rell.toolbox.indexer.IdeSymbolInfoWithInterval
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.lsp.editing.Document
import net.postchain.rell.toolbox.transformer.AntlrPos
import org.antlr.v4.runtime.misc.Interval
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.WorkspaceSymbol
import java.net.URI

class RellSymbolService {

    fun getSymbolLocations(document: Document, indexer: WorkspaceIndexer, position: Position): MutableList<Location> {
        val resource = indexer.getResource(document.fileUri) ?: return mutableListOf()
        val workspaceUri = formatWorkspaceUri(indexer.workspaceUri)
        val symbol = getSymbolForDocument(document, resource, position)?.ideSymbolInfo

        val link = symbol?.link ?: return mutableListOf()

        return when (link) {
            is IdeGlobalSymbolLink -> getGlobalLink(link.globalId(), workspaceUri, indexer)
            is IdeModuleSymbolLink -> getModuleLink(link.moduleFile(), workspaceUri)
            is IdeLocalSymbolLink -> getLocalLink(link.localPos(), resource, document)
            else -> {
                mutableListOf()
            }
        }
    }

    fun getSymbolInfoWithInterval(
        document: Document,
        indexer: WorkspaceIndexer,
        position: Position
    ): IdeSymbolInfoWithInterval? {
        return indexer.getResource(document.fileUri)?.let { getSymbolForDocument(document, it, position) }
    }

    fun getSymbolLocationsWithSymbol(
        document: Document,
        indexer: WorkspaceIndexer,
        position: Position
    ): Pair<Location, IdeSymbolInfo>? {
        val resource = indexer.getResource(document.fileUri) ?: return null
        val workspaceUri = formatWorkspaceUri(indexer.workspaceUri)
        val symbolWithInterval = getSymbolForDocument(document, resource, position)
        val symbol = symbolWithInterval?.ideSymbolInfo ?: return null

        val link = symbol.link

        if (symbol.defId != null || link == null) {
            val startPos = document.getPosition(symbolWithInterval.interval.a)
            val endPos = document.getPosition(symbolWithInterval.interval.b + 1)
            return Pair(Location(document.fileUri.toString(), Range(startPos, endPos)), symbol)
        }

        return when (link) {
            is IdeGlobalSymbolLink -> Pair(getGlobalLink(link.globalId(), workspaceUri, indexer)[0], symbol)
            is IdeModuleSymbolLink -> Pair(getModuleLink(link.moduleFile(), workspaceUri)[0], symbol)
            is IdeLocalSymbolLink -> Pair(getLocalLink(link.localPos(), resource, document)[0], symbol)
            else -> {
                null
            }
        }
    }

    fun getSymbolLocationForRenaming(
        document: Document,
        indexer: WorkspaceIndexer,
        position: Position
    ): Location? {
        val (location, symbolInfo) = getSymbolLocationsWithSymbol(document, indexer, position) ?: return null
        return if (isEligibleForRenaming(symbolInfo)) {
            location
        } else {
            null
        }
    }

    private fun isEligibleForRenaming(symbolInfo: IdeSymbolInfo): Boolean {
        return symbolInfo.kind !in NON_RENAMEABLE_SYMBOLS
    }

    private fun getGlobalLink(
        globalId: IdeSymbolGlobalId,
        workspaceUri: URI,
        indexer: WorkspaceIndexer
    ): MutableList<Location> {
        val targetFile = globalId.file
        val symId = globalId.symId
        var targetFileUri = URI(targetFile.toString())

        targetFileUri = URI(workspaceUri.toString() + targetFileUri.toString())

        val pos = indexer.getResource(targetFileUri)!!.userSymbols[symId] as? AntlrPos
            ?: return mutableListOf()
        val symbolLength = pos.node.text.length

        val startPosition = Position(pos.line() - 1, pos.column() - 1)
        val endPosition = Position(pos.line() - 1, pos.column() - 1 + symbolLength)
        return mutableListOf(Location(targetFileUri.toString(), Range(startPosition, endPosition)))
    }

    private fun getModuleLink(moduleFile: IdeFilePath, workspaceUri: URI): MutableList<Location> {
        var uri = URI(moduleFile.toString())
        uri = URI(workspaceUri.toString() + uri.toString())
        return mutableListOf(Location(uri.toString(), Range(Position(0, 0), Position(100, 1))))
    }

    private fun getLocalLink(localPos: S_Pos, resource: Resource, document: Document): MutableList<Location> {
        val nodePositionInterval = getSymbolForDocument(
            document, resource, Position(localPos.line() - 1, localPos.column())
        )?.interval ?: return mutableListOf()

        val startPos = document.getPosition(nodePositionInterval.a)
        val endPos = document.getPosition(nodePositionInterval.b + 1) // column starts at value 1

        return mutableListOf(Location(document.fileUri.toString(), Range(startPos, endPos)))
    }

    fun getSymbolForDocument(
        document: Document,
        resource: Resource,
        position: Position
    ): IdeSymbolInfoWithInterval? {
        val offset = document.getOffSet(position)
        return resource.locationInfo[Interval.of(offset - 1, offset)]
    }

    private fun formatWorkspaceUri(workspaceUri: URI): URI {
        return if (workspaceUri.toString().endsWith("/")) {
            workspaceUri
        } else {
            URI("$workspaceUri/")
        }
    }

    fun getDocumentSymbols(
        fileUri: URI,
        document: Document,
        resource: Resource
    ): DocumentSymbol? {
        if (document.content.isEmpty()) {
            return null
        }
        val fileNodeInfo = createFileNodeInfo(fileUri, document)
        return getDocumentSymbolsWithRoot(fileNodeInfo, resource)
    }

    fun getDocumentSymbolsWithRoot(rootNodeInfo: NodeInfo, resource: Resource): DocumentSymbol {
        val outlineTreeBuilder = OutlineTreeBuilder(rootNodeInfo, null)
        IdeApi.buildOutlineTree(outlineTreeBuilder, resource.ast)
        return outlineTreeBuilder.build().toDocumentSymbol()
    }

    fun getWorkspaceSymbols(query: String, indexers: List<WorkspaceIndexer>): List<WorkspaceSymbol> {
        val filterPredicate = { symbol: WorkspaceSymbol ->
            symbol.name.contains(query, ignoreCase = true) || query.isEmpty()
        }

        return indexers.flatMap { indexer ->
            indexer.resources.flatMap { resource ->
                getResourceSymbols(resource, filterPredicate)
            }
        }
    }

    fun getActiveImportSymbol(
        fileUri: URI,
        offset: Int,
        document: Document,
        indexer: WorkspaceIndexer
    ): DocumentSymbol? {
        val resource = indexer.getResource(fileUri) ?: return null
        return getDocumentSymbols(
            fileUri,
            document,
            resource
        )?.children?.let { findPackageSymbol(it, offset, document) }
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

    fun getSymbolInfoForImportedModule(
        symbol: DocumentSymbol,
        document: Document,
        indexer: WorkspaceIndexer
    ): List<IdeSymbolInfo> {
        val (startOffset, endOffset) = document.getStartAndEndOffset(symbol.range)
        val importPath = document.getTextIn(Interval.of(startOffset, endOffset)).substringBeforeLast(".")
        val position = Position(symbol.range.start.line, importPath.length)
        val symbolLocation = getSymbolLocationsWithSymbol(document, indexer, position)
        val moduleName = symbolLocation?.second?.doc?.symbolName?.toString() ?: return listOf()
        return getModuleSymbols(indexer, moduleName)
    }

    private fun getModuleSymbols(indexer: WorkspaceIndexer, moduleName: String): List<IdeSymbolInfo> {
        return indexer.resources
            .asSequence()
            .filter { resource ->
                resource.moduleInfo?.name?.toString() == moduleName
            }
            .flatMap { resource ->
                resource.symbolInfos.values.asSequence()
                    .filter {
                        it.link == null && it.kind in RellRelevantImportSymbol.getAllIdeSymbolKinds()
                    }
            }
            .toList()
    }

    private fun getResourceSymbols(
        resource: Resource,
        filterPredicate: (WorkspaceSymbol) -> Boolean
    ): List<WorkspaceSymbol> {
        val workspaceSymbolsBuilder = WorkspaceSymbolsBuilder(resource.fileUri, filterPredicate)
        IdeApi.buildOutlineTree(workspaceSymbolsBuilder, resource.ast)
        return workspaceSymbolsBuilder.build()
    }

    private fun createFileNodeInfo(
        fileUri: URI,
        document: Document
    ): NodeInfo {
        val fileName = lastSegment(fileUri)
        val range = Range(document.getPosition(0), document.getPosition(document.content.length - 1))
        return NodeInfo(fileName, range, range, SymbolKind.File)
    }

    private fun lastSegment(uri: URI): String {
        val path = uri.getPath()
        return path.substring(path.lastIndexOf('/') + 1)
    }

    fun findEnclosingFileOrNamespace(
        fileUri: URI,
        document: Document,
        resource: Resource,
        offset: Int
    ): DocumentSymbol? {
        if (document.content.isEmpty()) {
            return null
        }
        val fileNodeInfo = createFileNodeInfo(fileUri, document)
        val outlineNode = OutlineTreeBuilder(fileNodeInfo, null).also { builder ->
            IdeApi.buildOutlineTree(builder, resource.ast)
        }.build()
        val position = document.getPosition(offset)
        return findEnclosingFileOrNamespaceSymbol(outlineNode, position)
    }

    private fun findEnclosingFileOrNamespaceSymbol(node: OutlineNode, position: Position): DocumentSymbol? {
        val documentSymbol = node.toDocumentSymbol()

        if (!containsPosition(node.getInfo().fullRegion, position)) {
            return null
        }

        val matchingChild = findChildContainingPosition(node, position)
        return when (documentSymbol.kind) {
            SymbolKind.File -> when {
                matchingChild == null -> documentSymbol
                matchingChild.toDocumentSymbol().kind == SymbolKind.Namespace ->
                    findEnclosingFileOrNamespaceSymbol(matchingChild, position)
                else -> null
            }

            SymbolKind.Namespace -> return when {
                matchingChild == null -> documentSymbol
                matchingChild.toDocumentSymbol().kind == SymbolKind.Namespace ->
                    findEnclosingFileOrNamespaceSymbol(matchingChild, position)
                else -> null
            }
            else -> null
        }
    }

    private fun findChildContainingPosition(node: OutlineNode, position: Position): OutlineNode? {
        return node.getChildren().firstOrNull { child ->
            containsPosition(child.getInfo().fullRegion, position)
        }
    }

    // TODO: Basically the same check as we have in Document.offSetInRange. Make one (global?) util instead
    private fun containsPosition(range: Range?, position: Position): Boolean {
        if (range == null) return false
        return when {
            position.line < range.start.line -> false
            position.line > range.end.line -> false
            position.line == range.start.line && position.character < range.start.character -> false
            position.line == range.end.line && position.character > range.end.character -> false
            else -> true
        }
    }

    companion object {
        val NON_RENAMEABLE_SYMBOLS = setOf(
            IdeSymbolKind.DEF_IMPORT_MODULE,
            IdeSymbolKind.MOD_ANNOTATION,
            IdeSymbolKind.EXPR_IMPORT_ALIAS,
            IdeSymbolKind.DEF_IMPORT_ALIAS,
            IdeSymbolKind.DEF_TYPE,
            IdeSymbolKind.UNKNOWN,
            IdeSymbolKind.DEF_FUNCTION_SYSTEM,
        )
    }
}
