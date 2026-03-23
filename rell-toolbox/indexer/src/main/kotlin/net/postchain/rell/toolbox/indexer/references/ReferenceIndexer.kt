/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.indexer.references

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.utils.ide.IdeGlobalSymbolLink
import net.postchain.rell.base.utils.ide.IdeLocalSymbolLink
import net.postchain.rell.base.utils.ide.IdeModuleSymbolLink
import net.postchain.rell.base.utils.ide.IdeSymbolId
import net.postchain.rell.base.utils.ide.IdeSymbolInfo
import net.postchain.rell.toolbox.common.Location
import net.postchain.rell.toolbox.common.Position
import net.postchain.rell.toolbox.common.Range
import net.postchain.rell.toolbox.indexer.IdeSymbolInfoWithInterval
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.transformer.AntlrPos
import org.antlr.v4.runtime.misc.Interval
import java.io.File
import java.net.URI

class ReferenceIndexer(private val workspaceUri: URI, fileUriResourceMap: MutableMap<URI, Resource>) {
    private val globalReferenceMap = mutableMapOf<GlobalReference, MutableSet<S_Pos>>()
    private val moduleReferenceMap = mutableMapOf<URI, MutableSet<URI>>()
    private val localReferenceMap = mutableMapOf<LocalReference, MutableSet<S_Pos>>()

    init {
        update(fileUriResourceMap)
    }

    private fun update(fileUriResourceMap: Map<URI, Resource>) {
        fileUriResourceMap.forEach { (uri, resource) ->
            resource.symbolInfos.forEach { (position, symbolInfo) ->
                addToIndex(position, symbolInfo, uri)
            }
        }
    }

    fun findAllReferences(fileUri: URI, symbolInfoWithInterval: IdeSymbolInfoWithInterval?): List<Location> {
        if (symbolInfoWithInterval == null) return listOf()
        val symbolInfo = symbolInfoWithInterval.ideSymbolInfo
        val interval = symbolInfoWithInterval.interval
        val link = symbolInfo.link
        val defId = symbolInfo.defId
        return when {
            (defId == null && link == null) || link is IdeLocalSymbolLink -> findLocalReferences(
                symbolInfo,
                interval,
                fileUri
            )

            link is IdeModuleSymbolLink -> findModuleReferences(symbolInfo)
            defId != null || link is IdeGlobalSymbolLink -> findGlobalReferences(symbolInfo, fileUri)
            else -> listOf()
        }
    }

    private fun findGlobalReferences(
        symbolInfo: IdeSymbolInfo,
        fileUri: URI
    ): List<Location> {
        val symbolFileUri = getSymbolUri(symbolInfo, fileUri, workspaceUri) ?: return listOf()
        val symbolId = getSymbolId(symbolInfo) ?: return listOf()
        val globalReference = GlobalReference(symbolFileUri, symbolId)
        return toLocations(globalReferenceMap[globalReference])
    }

    private fun findModuleReferences(symbolInfo: IdeSymbolInfo): List<Location> {
        val moduleFile = (symbolInfo.link as IdeModuleSymbolLink).moduleFile()
        val moduleFileUri = URI(workspaceUri.toString() + URI(moduleFile.toString()))
        return toModuleLocation(moduleReferenceMap[moduleFileUri])
    }

    private fun findLocalReferences(
        symbolInfo: IdeSymbolInfo,
        interval: Interval,
        fileUri: URI
    ): List<Location> {
        val localInterval = when (symbolInfo.link) {
            is IdeLocalSymbolLink -> {
                val localPos = (symbolInfo.link as IdeLocalSymbolLink).localPos() as AntlrPos
                Interval(localPos.node.start.startIndex, localPos.node.stop.stopIndex)
            }

            else -> interval
        }
        return toLocations(localReferenceMap[LocalReference(fileUri, localInterval)])
    }

    private fun addToIndex(position: S_Pos, symbolInfo: IdeSymbolInfo, fileUri: URI) {
        val link = symbolInfo.link
        if (link is IdeGlobalSymbolLink) {
            addToGlobalIndex(link, position)
        }

        if (link is IdeModuleSymbolLink) {
            addToModuleIndex(link, fileUri)
        }

        if (link is IdeLocalSymbolLink) {
            addToLocalIndex(link, fileUri, position)
        }
    }

    private fun addToGlobalIndex(
        link: IdeGlobalSymbolLink,
        position: S_Pos
    ) {
        val targetFileUri = URI(workspaceUri.toString() + link.globalId().file)
        val globalReference = GlobalReference(targetFileUri, link.globalId().symId)
        if (globalReferenceMap.containsKey(globalReference)) {
            globalReferenceMap[globalReference]?.add(position)
        } else {
            globalReferenceMap[globalReference] = mutableSetOf(position)
        }
    }

    private fun addToModuleIndex(link: IdeModuleSymbolLink, fileUri: URI) {
        val moduleFile = link.moduleFile()
        val moduleFileUri = URI(workspaceUri.toString() + URI(moduleFile.toString()))

        if (moduleReferenceMap.containsKey(moduleFileUri)) {
            moduleReferenceMap[moduleFileUri]?.add(fileUri)
        } else {
            moduleReferenceMap[moduleFileUri] = mutableSetOf(fileUri)
        }
    }

    private fun addToLocalIndex(
        link: IdeLocalSymbolLink,
        fileUri: URI,
        position: S_Pos
    ) {
        val localPos = (link.localPos() as AntlrPos)
        val interval = Interval(localPos.node.start.startIndex, localPos.node.stop.stopIndex)
        val localReference = LocalReference(fileUri, interval)
        if (localReferenceMap.containsKey(localReference)) {
            localReferenceMap[localReference]?.add(position)
        } else {
            localReferenceMap[localReference] = mutableSetOf(position)
        }
    }

    private fun getSymbolId(symbolInfo: IdeSymbolInfo?): IdeSymbolId? {
        return when {
            symbolInfo?.defId != null -> symbolInfo.defId
            symbolInfo?.link is IdeGlobalSymbolLink -> (symbolInfo.link as IdeGlobalSymbolLink).globalId().symId
            else -> null
        }
    }

    private fun getSymbolUri(symbolInfo: IdeSymbolInfo?, fileUri: URI, workspaceUri: URI): URI? {
        return when {
            symbolInfo?.defId != null -> fileUri
            symbolInfo?.link is IdeGlobalSymbolLink -> {
                val globalId = (symbolInfo.link as IdeGlobalSymbolLink).globalId()
                val targetFileUri = URI(globalId.file.toString())
                URI(workspaceUri.toString() + targetFileUri.toString())
            }

            else -> null
        }
    }

    private fun toLocations(positions: Set<S_Pos>?): List<Location> {
        if (positions == null) return listOf()
        return positions.map {
            val pos = it as AntlrPos
            val referenceFileUri = File(workspaceUri.path + pos.path.str()).toURI().toString()
            val range = Range(
                Position(pos.line() - 1, pos.column() - 1),
                Position(pos.line() - 1, pos.column() - 1 + pos.node.text.length)
            )
            Location(referenceFileUri, range)
        }
    }

    private fun toModuleLocation(fileUris: Set<URI>?): List<Location> {
        if (fileUris == null) return listOf()
        return fileUris.map {
            Location(
                it.toString(),
                Range(
                    Position(0, 1),
                    Position(0, 1)
                )
            )
        }
    }
}
