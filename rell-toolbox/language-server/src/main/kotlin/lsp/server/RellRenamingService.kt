/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.server

import net.postchain.rell.base.utils.ide.IdeSymbolInfo
import net.postchain.rell.base.utils.ide.IdeSymbolKind
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.lsp.editing.Document
import net.postchain.rell.toolbox.lsp.symbols.RellSymbolService
import net.postchain.rell.base.compiler.parser.antlr.RellBaseVisitor
import net.postchain.rell.base.compiler.parser.antlr.RellParser
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either3
import java.net.URI

val TYPE_DEFINITIONS =
    setOf(IdeSymbolKind.DEF_ENTITY, IdeSymbolKind.DEF_STRUCT, IdeSymbolKind.DEF_ENUM, IdeSymbolKind.DEF_TYPE)

class RellRenamingService(
    private val rellSymbolService: RellSymbolService,
    private val documentManager: RellDocumentManager,
    private val indexingManager: RellIndexingManager
) {

    fun prepareRename(
        fileUri: URI,
        position: Position
    ): Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior> {
        val indexer = indexingManager.getIndexerFor(fileUri)
        val document = documentManager.getOpenDocument(fileUri)
            ?: return Either3.forThird(PrepareRenameDefaultBehavior())

        val location = rellSymbolService.getSymbolLocationForRenaming(document, indexer, position)
            ?: return Either3.forThird(PrepareRenameDefaultBehavior())

        val placeholder = getPlaceholderText(document, indexer, fileUri, position)
        return if (placeholder.isNotEmpty()) {
            Either3.forSecond(PrepareRenameResult(location.range, placeholder))
        } else {
            Either3.forFirst(location.range)
        }
    }

    fun rename(fileUri: URI, position: Position, newName: String, getReferenceLocations: (URI, Position, Boolean) -> List<Location>): WorkspaceEdit {
        val indexer = indexingManager.getIndexerFor(fileUri)
        val document = documentManager.getOpenDocument(fileUri) ?: return WorkspaceEdit()
        val (renamingTriggerSymbol, interval) = rellSymbolService.getSymbolInfoWithInterval(document, indexer, position)
            ?: return WorkspaceEdit()

        val oldName = document.getTextIn(interval)
        val locations = getReferenceLocations(fileUri, position, true)
        val changes = mutableMapOf<String, MutableList<TextEdit>>()

        locations.forEach { location ->
            val change = renameLocation(
                indexer,
                location,
                renamingTriggerSymbol,
                oldName,
                newName,
            )
            changes[location.uri]?.add(change) ?: changes.put(location.uri, mutableListOf(change))
        }
        return WorkspaceEdit(changes)
    }

    private fun getPlaceholderText(
        document: Document,
        indexer: WorkspaceIndexer,
        fileUri: URI,
        position: Position
    ): String {
        val resource = indexer.getResource(fileUri) ?: return ""
        val clickedSymbol = rellSymbolService.getSymbolForDocument(document, resource, position) ?: return ""
        return document.getTextIn(clickedSymbol.interval)
    }

    private fun renameLocation(
        indexer: WorkspaceIndexer,
        location: Location,
        renamingTriggerSymbol: IdeSymbolInfo,
        oldName: String,
        newName: String,
    ): TextEdit {
        val locationFileUri = URI(location.uri)
        val symbolToRename = rellSymbolService.getSymbolInfoWithInterval(
            documentManager.getDocument(locationFileUri),
            indexer,
            location.range.start
        )?.ideSymbolInfo ?: return TextEdit(location.range, newName)

        return if (symbolToRename.isTypeDefinition()) {
            val resource = indexer.getResource(locationFileUri)
            val fullNameWithRange = resource?.let { findAnonAttrFullName(it, location) }
            val text = determineNewText(renamingTriggerSymbol, symbolToRename, oldName, newName, fullNameWithRange)
            TextEdit(fullNameWithRange?.range ?: location.range, text)
        } else {
            TextEdit(location.range, newName)
        }
    }

    private fun determineNewText(
        renamingTriggerSymbol: IdeSymbolInfo,
        symbolToRename: IdeSymbolInfo,
        oldName: String,
        newName: String,
        fullNameWithRange: FullNameWithRange?
    ): String {
        return when {
            renamingTriggerSymbol.isTypeReference() -> {
                "$oldName: ${updateNewName(newName, fullNameWithRange)}"
            }

            renamingTriggerSymbol.isNotTypeReference() -> {
                when {
                    symbolToRename.containsParameter(oldName) -> {
                        if (renamingTriggerSymbol.isLocalParam()) {
                            "$newName: ${updateOldName(oldName, fullNameWithRange)}"
                        } else {
                            "$oldName: ${updateNewName(newName, fullNameWithRange)}"
                        }
                    }

                    symbolToRename.containsAttribute(oldName) -> {
                        "$oldName: ${updateNewName(newName, fullNameWithRange)}"
                    }

                    else -> newName
                }
            }

            else -> {
                "$newName: ${updateOldName(oldName, fullNameWithRange)}"
            }
        }
    }

    private fun updateNewName(newName: String, fullNameWithRange: FullNameWithRange?): String {
        return if (fullNameWithRange?.isQualifiedName() == true) {
            val partialFullName = fullNameWithRange.fullName.dropLast(1).joinToString(separator = ".")
            "$partialFullName.$newName"
        } else {
            newName
        }
    }

    private fun updateOldName(oldName: String, fullNameWithRange: FullNameWithRange?): String {
        return if (fullNameWithRange?.isQualifiedName() == true) {
            fullNameWithRange.fullName.joinToString(separator = ".")
        } else {
            oldName
        }
    }

    data class FullNameWithRange(val fullName: List<String>, val range: Range) {
        fun isQualifiedName(): Boolean = fullName.size > 1
    }

    private fun findAnonAttrFullName(resource: Resource, location: Location): FullNameWithRange? {
        val visitor = object : RellBaseVisitor<Unit>() {
            var result: FullNameWithRange? = null
            override fun visitAnonAttrHeader(ctx: RellParser.AnonAttrHeaderContext) {
                val startPos = ctx.start.line
                if (startPos == location.range.start.line + 1 &&
                    ctx.stop.charPositionInLine == location.range.start.character
                ) {
                    val qualifiedName = ctx.qualifiedName()
                    val range = Range(
                        Position(
                            qualifiedName.start.line - 1,
                            qualifiedName.start.charPositionInLine
                        ),
                        Position(
                            qualifiedName.stop.line - 1,
                            qualifiedName.stop.charPositionInLine +
                                qualifiedName.stop.text.length
                        )
                    )
                    result = FullNameWithRange(
                        qualifiedName.RULE_ID().map { it.text },
                        range
                    )
                }
            }
        }
        visitor.visit(resource.parseTree)
        return visitor.result
    }

    private fun IdeSymbolInfo.isTypeDefinition(): Boolean =
        this.kind in TYPE_DEFINITIONS && this.defId != null && this.link != null

    private fun IdeSymbolInfo.isReference(): Boolean = this.link != null

    private fun IdeSymbolInfo.isExplicitTypeReference(): Boolean =
        this.kind in TYPE_DEFINITIONS && this.defId == null

    private fun IdeSymbolInfo.isTypeReference(): Boolean = this.isReference() && this.isExplicitTypeReference()

    private fun IdeSymbolInfo.isNotTypeReference(): Boolean = !this.isReference() && !this.isExplicitTypeReference()

    private fun IdeSymbolInfo.containsParameter(name: String): Boolean =
        this.defId?.encode()?.contains("param[$name]") == true

    private fun IdeSymbolInfo.containsAttribute(name: String): Boolean =
        this.defId?.encode()?.contains("attr[$name]") == true

    private fun IdeSymbolInfo.isLocalParam(): Boolean = this.kind == IdeSymbolKind.LOC_PARAMETER
}
