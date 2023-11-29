package net.postchain.rell.toolbox.lsp.symbols

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.utils.ide.IdeFilePath
import net.postchain.rell.base.utils.ide.IdeGlobalSymbolLink
import net.postchain.rell.base.utils.ide.IdeLocalSymbolLink
import net.postchain.rell.base.utils.ide.IdeModuleSymbolLink
import net.postchain.rell.base.utils.ide.IdeSymbolGlobalId
import net.postchain.rell.toolbox.core.compiler.AntlrPos
import net.postchain.rell.toolbox.core.indexer.IdeSymbolInfoWithInterval
import net.postchain.rell.toolbox.core.indexer.Resource
import net.postchain.rell.toolbox.core.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.lsp.editing.Document
import org.antlr.v4.runtime.misc.Interval
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
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

    private fun getGlobalLink(
        globalId: IdeSymbolGlobalId,
        workspaceUri: URI,
        indexer: WorkspaceIndexer
    ): MutableList<Location> {
        val targetFile = globalId.file
        val symId = globalId.symId
        var targetFileUri = URI(targetFile.toString())

        targetFileUri = URI(workspaceUri.toString() + targetFileUri.toString())

        // TODO: optimization opportunity. lookup instead of iterating
        val symbolInfo = indexer.getResource(targetFileUri)!!.symbolInfos.entries.find { it.value.defId == symId }!!
        val pos = symbolInfo.key as AntlrPos
        val symbolLength = pos.node.text.length

        val startPosition = Position(pos.line() - 1, pos.column() - 1)
        val endPosition = Position(pos.line() - 1, pos.column() - 1 + symbolLength)
        return mutableListOf(Location(targetFileUri.toString(), Range(startPosition, endPosition)))
    }


    private fun getModuleLink(moduleFile: IdeFilePath, workspaceUri: URI): MutableList<Location> {
        var uri = URI(moduleFile.toString())
        uri = URI(workspaceUri.toString() + uri.toString())
        return mutableListOf(Location(uri.toString(), Range(Position(0, 1), Position(100, 1))))
    }

    private fun getLocalLink(localPos: S_Pos, resource: Resource, document: Document): MutableList<Location> {
        val nodePositionInterval =
            getSymbolForDocument(
                document,
                resource,
                Position(localPos.line() - 1, localPos.column())
            )?.interval ?: return mutableListOf()

        val startPos = document.getPosition(nodePositionInterval.a)
        val endPos = document.getPosition(nodePositionInterval.b + 1) //column starts at value 1

        return mutableListOf(Location(document.fileUri.toString(), Range(startPos, endPos)))
    }

    private fun getSymbolForDocument(
        document: Document,
        resource: Resource,
        position: Position
    ): IdeSymbolInfoWithInterval? {
        val offset = document.getOffSet(position) - 1 // line starts on 0
        return resource.locationInfo[Interval.of(offset, offset)]
    }

    //TODO maybe move out to utils if needed elsewhere
    private fun formatWorkspaceUri(workspaceUri: URI): URI {
        return if (workspaceUri.toString().endsWith("/")) {
            workspaceUri
        } else {
            URI("$workspaceUri/")
        }
    }
}
