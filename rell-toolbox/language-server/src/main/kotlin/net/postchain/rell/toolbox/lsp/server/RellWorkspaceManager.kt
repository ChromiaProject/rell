/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.server

import com.google.gson.JsonObject
import io.github.oshai.kotlinlogging.KotlinLogging
import net.postchain.rell.base.utils.ide.IdeSymbolInfo
import net.postchain.rell.base.utils.ide.IdeSymbolKind
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.lsp.completion.RellCompletionService
import net.postchain.rell.toolbox.lsp.diagnostics.DiagnosticsPublisher
import net.postchain.rell.toolbox.lsp.editing.CodeActionService
import net.postchain.rell.toolbox.lsp.editing.Document
import net.postchain.rell.toolbox.lsp.hover.formatDocSymbol
import net.postchain.rell.toolbox.lsp.references.RellReferenceService
import net.postchain.rell.toolbox.lsp.symbols.RellSymbolService
import net.postchain.rell.toolbox.parser.RellBaseVisitor
import net.postchain.rell.toolbox.parser.RellParser
import org.antlr.v4.runtime.misc.Interval
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior
import org.eclipse.lsp4j.PrepareRenameResult
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.Either3
import java.io.File
import java.net.URI
import java.util.concurrent.CompletableFuture

class RellWorkspaceManager(
    private val rellSymbolService: RellSymbolService,
    private val rellReferenceService: RellReferenceService,
    private val completionService: RellCompletionService,
    private val documentManager: RellDocumentManager,
    private val indexingManager: RellIndexingManager,
    private val diagnosticsManager: RellDiagnosticsManager,
    private val renamingService: RellRenamingService
) {

    fun initialize(
        workspaceFolders: List<WorkspaceFolder>,
        diagnosticsPublisher: DiagnosticsPublisher,
        notificationPublisher: (type: NotificationType, message: String) -> Unit
    ) {
        diagnosticsManager.setDiagnosticsPublisher(diagnosticsPublisher)
        diagnosticsManager.setNotificationPublisher(notificationPublisher)
        indexingManager.initialize(workspaceFolders)
    }

    fun getHoverDocumentation(params: HoverParams): MarkupContent {
        val fileUri = parseFileUri(params.textDocument.uri) ?: return MarkupContent("plaintext", "")
        val indexer = indexingManager.getIndexerFor(fileUri)
        val document = documentManager.getOpenDocument(fileUri) ?: return MarkupContent("plaintext", "")

        val symbolLocation = rellSymbolService.getSymbolLocationsWithSymbol(document, indexer, params.position)
        return MarkupContent("markdown", formatDocSymbol(symbolLocation?.second?.doc))
    }

    fun didOpen(fileUri: URI, version: Int, content: String) {
        if (fileUri.scheme == "file" && File(fileUri).exists()) {
            documentManager.openDocument(fileUri, version, content)
            indexingManager.updateFileContent(fileUri, content, skipCache = true)
        }
    }

    fun didClose(fileUri: URI) {
        documentManager.closeDocument(fileUri)
    }

    fun didChangeTextDocumentContent(fileUri: URI, contentChanges: List<TextDocumentContentChangeEvent>) {
        val updatedDocument = documentManager.applyTextDocumentChanges(fileUri, contentChanges)
        indexingManager.updateFileContent(fileUri, updatedDocument.content)
    }

    fun didChangeFiles(dirtyFiles: List<URI>, deletedFiles: List<URI>, updateAffectedFiles: Boolean = false) {
        indexingManager.handleFileChanges(dirtyFiles, deletedFiles, updateAffectedFiles)
    }

    fun didChangeFolders(dirtyFolders: List<URI>, deletedFolders: List<URI>) {
        indexingManager.handleFolderChanges(dirtyFolders, deletedFolders)
    }

    fun didSave(fileUri: URI) {
        val contents = documentManager.getOpenDocument(fileUri)
        if (contents == null) {
            logger.warn { "The document $fileUri has not been opened." }
            return
        }
        val indexer = indexingManager.getIndexerFor(fileUri)
        val affectedUris = indexer.findAffectedFiles(fileUri)
        didChangeFiles(affectedUris.toList() + fileUri, listOf())
    }

    fun didCreateChromiaConfig(chromiaConfigFiles: List<URI>) {
        indexingManager.indexFromRoots(chromiaConfigFiles)
    }

    fun getDefinitionLocations(
        fileUri: URI,
        position: Position
    ): Either<MutableList<out Location>, MutableList<out LocationLink>> {
        val indexer = indexingManager.getIndexerFor(fileUri)
        val document = documentManager.getOpenDocument(fileUri) ?: return Either.forLeft(mutableListOf())
        return Either.forLeft(rellSymbolService.getSymbolLocations(document, indexer, position))
    }

    private fun getDefinitionLocationsAndSymbolInfo(
        fileUri: URI,
        position: Position
    ): Pair<Location, IdeSymbolInfo>? {
        val indexer = indexingManager.getIndexerFor(fileUri)
        val document = documentManager.getOpenDocument(fileUri) ?: return null

        return rellSymbolService.getSymbolLocationsWithSymbol(document, indexer, position)
    }

    fun getDocumentSymbols(fileUri: URI): List<Either<SymbolInformation, DocumentSymbol>> {
        val resource = indexingManager.getResource(fileUri) ?: return listOf()
        val document = documentManager.getOpenDocument(fileUri) ?: return listOf()
        val documentSymbol = rellSymbolService.getDocumentSymbols(fileUri, document, resource) ?: return listOf()
        return listOf(Either.forRight(documentSymbol))
    }

    fun getReferenceLocations(fileUri: URI, position: Position?, includeDefinition: Boolean = true): List<Location> {
        val indexer = indexingManager.getIndexerFor(fileUri)
        val document = documentManager.getOpenDocument(fileUri) ?: return listOf()

        val result: MutableList<Location> = mutableListOf()
        if (includeDefinition && position != null) {
            val locationIdeSymbolInfoPair = getDefinitionLocationsAndSymbolInfo(fileUri, position)
            if (locationIdeSymbolInfoPair != null &&
                locationIdeSymbolInfoPair.second.kind != IdeSymbolKind.DEF_IMPORT_MODULE
            ) {
                result.add(locationIdeSymbolInfoPair.first)
            }
        }

        result.addAll(rellReferenceService.getReferenceLocations(fileUri, document, indexer, position))
        return result
    }

    fun prepareRename(
        fileUri: URI,
        position: Position
    ): Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior> {
        return renamingService.prepareRename(fileUri, position)
    }


    fun getCodeActions(fileUri: URI, range: Range): List<Either<Command, CodeAction>> {
        val indexer = indexingManager.getIndexerFor(fileUri)
        return CodeActionService.getCodeActions(fileUri, range, indexer)
    }

    fun getCodeActionForFile(fileUri: URI): CodeAction {
        val indexer = indexingManager.getIndexerFor(fileUri)
        return CodeActionService.getCodeActionForFile(fileUri, indexer)
    }

    fun rename(fileUri: URI, position: Position, newName: String): WorkspaceEdit {
        return renamingService.rename(fileUri, position, newName) { uri, pos, includeDefinition ->
            getReferenceLocations(uri, pos, includeDefinition)
        }
    }






    fun getCompletions(fileUri: URI, position: Position): List<CompletionItem> {
        val document = documentManager.getOpenDocument(fileUri) ?: return listOf()
        val indexer = indexingManager.getIndexerForOrNull(fileUri) ?: return listOf()
        val offset = document.getOffSet(position)

        return completionService.getCompletions(fileUri, offset, indexer, document)
    }

    fun resolveCompletionItem(unresolved: CompletionItem): CompletionItem {
        val data = unresolved.data as? JsonObject ?: return unresolved
        val fileUri = parseFileUri(data.get("fileUri").asString) ?: return unresolved
        val offset = data.get("offset").asInt

        return try {
            val document = documentManager.getDocument(fileUri)
            val position = document.getPosition(offset)
            val replacementText = completionService.getReplacementText(document, offset, unresolved.insertText)

            unresolved.apply {
                this.textEdit = Either.forLeft(
                    TextEdit(Range(position, position), replacementText)
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "Error resolving completion item for $fileUri at offset $offset" }
            unresolved
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
