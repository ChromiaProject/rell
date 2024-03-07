package net.postchain.rell.toolbox.lsp.symbols

import java.net.URI
import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.utils.ide.IdeApi
import net.postchain.rell.base.utils.ide.IdeFilePath
import net.postchain.rell.base.utils.ide.IdeGlobalSymbolLink
import net.postchain.rell.base.utils.ide.IdeLocalSymbolLink
import net.postchain.rell.base.utils.ide.IdeModuleSymbolLink
import net.postchain.rell.base.utils.ide.IdeSymbolGlobalId
import net.postchain.rell.base.utils.ide.IdeSymbolInfo
import net.postchain.rell.base.utils.ide.IdeSymbolKind
import net.postchain.rell.toolbox.core.compiler.AntlrPos
import net.postchain.rell.toolbox.core.indexer.IdeSymbolInfoWithInterval
import net.postchain.rell.toolbox.core.indexer.Resource
import net.postchain.rell.toolbox.core.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.lsp.editing.Document
import org.antlr.v4.runtime.misc.Interval
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.jsonrpc.messages.Either

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

    fun getSymbolLocationForRenaming(document: Document,
                                     indexer: WorkspaceIndexer,
                                     position: Position): Location? {
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
        globalId: IdeSymbolGlobalId, workspaceUri: URI, indexer: WorkspaceIndexer
    ): MutableList<Location> {
        val targetFile = globalId.file
        val symId = globalId.symId
        var targetFileUri = URI(targetFile.toString())

        targetFileUri = URI(workspaceUri.toString() + targetFileUri.toString())

        // TODO: optimization opportunity. lookup instead of iterating
        val symbolInfo = indexer.getResource(targetFileUri)!!.symbolInfos.entries.find { it.value.defId == symId }
        // TODO: This is hiding a NullPointerException BUG. Fix it!!
        // Go to definition fails as it.value.defId members have different structure then symId, causing ClassCastException
            ?: return mutableListOf()
        val pos = symbolInfo.key as AntlrPos
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
        val endPos = document.getPosition(nodePositionInterval.b + 1) //column starts at value 1

        return mutableListOf(Location(document.fileUri.toString(), Range(startPos, endPos)))
    }

    fun getSymbolForDocument(
        document: Document, resource: Resource, position: Position
    ): IdeSymbolInfoWithInterval? {
        val offset = document.getOffSet(position)
        return resource.locationInfo[Interval.of(offset - 1, offset)]
    }

    //TODO maybe move out to utils if needed elsewhere
    private fun formatWorkspaceUri(workspaceUri: URI): URI {
        return if (workspaceUri.toString().endsWith("/")) {
            workspaceUri
        } else {
            URI("$workspaceUri/")
        }
    }

    fun getDocumentSymbols(
        fileUri: URI, document: Document, resource: Resource
    ): List<Either<SymbolInformation, DocumentSymbol>> {
        if (document.content.isEmpty()) {
            return listOf()
        }
        val fileNodeInfo = createFileNodeInfo(fileUri, document)
        val documentSymbol = getDocumentSymbolsWithRoot(fileNodeInfo, resource)
        return listOf(Either.forRight(documentSymbol))
    }

    fun getDocumentSymbolsWithRoot(rootNodeInfo: NodeInfo, resource: Resource): DocumentSymbol {
        val outlineTreeBuilder = OutlineTreeBuilder(rootNodeInfo, null)
        IdeApi.buildOutlineTree(outlineTreeBuilder, resource.ast)
        return outlineTreeBuilder.build(null).toDocumentSymbol()
    }

    private fun createFileNodeInfo(
        fileUri: URI, document: Document
    ): NodeInfo {
        val fileName = lastSegment(fileUri)
        val range = Range(document.getPosition(0), document.getPosition(document.content.length - 1))
        return NodeInfo(fileName, range, range, SymbolKind.File)
    }

    private fun lastSegment(uri: URI): String {
        val path = uri.getPath()
        return path.substring(path.lastIndexOf('/') + 1)
    }

    companion object {
        val NON_RENAMEABLE_SYMBOLS = setOf(
            IdeSymbolKind.DEF_IMPORT_MODULE,
            IdeSymbolKind.MOD_ANNOTATION,
            IdeSymbolKind.EXPR_IMPORT_ALIAS,
            IdeSymbolKind.DEF_IMPORT_ALIAS,
            IdeSymbolKind.DEF_TYPE,
            IdeSymbolKind.UNKNOWN
        )
    }
}
