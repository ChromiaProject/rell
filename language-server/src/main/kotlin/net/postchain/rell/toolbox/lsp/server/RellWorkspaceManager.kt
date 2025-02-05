package net.postchain.rell.toolbox.lsp.server

import io.github.oshai.kotlinlogging.KotlinLogging
import net.postchain.rell.base.utils.ide.IdeSymbolInfo
import net.postchain.rell.base.utils.ide.IdeSymbolKind
import net.postchain.rell.toolbox.indexer.RellIssue
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.lsp.completion.RellCompletionService
import net.postchain.rell.toolbox.lsp.editing.CodeActionService
import net.postchain.rell.toolbox.lsp.editing.Document
import net.postchain.rell.toolbox.lsp.hover.formatDocSymbol
import net.postchain.rell.toolbox.lsp.references.RellReferenceService
import net.postchain.rell.toolbox.lsp.symbols.RellSymbolService
import net.postchain.rell.toolbox.parser.RellBaseVisitor
import net.postchain.rell.toolbox.parser.RellParser
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

val TYPE_DEFINITIONS =
    setOf(IdeSymbolKind.DEF_ENTITY, IdeSymbolKind.DEF_STRUCT, IdeSymbolKind.DEF_ENUM, IdeSymbolKind.DEF_TYPE)

class RellWorkspaceManager(
    private val rellSymbolService: RellSymbolService,
    private val rellReferenceService: RellReferenceService,
    private val completionService: RellCompletionService,
    private val documentManager: RellDocumentManager,
    private val indexingManager: RellIndexingManager,
    private val diagnosticsManager: RellDiagnosticsManager
) {

    fun initialize(
        workspaceFolders: List<WorkspaceFolder>,
        diagnosticsPublisher: (uri: URI, List<RellIssue>) -> Unit,
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
            indexingManager.updateFileContent(fileUri, content)
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
            logger.error { "The document $fileUri has not been opened." }
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
        val documentSymbol = rellSymbolService.getDocumentSymbols(fileUri, document, resource)
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

    fun getCodeActions(fileUri: URI, range: Range): List<Either<Command, CodeAction>> {
        val indexer = indexingManager.getIndexerFor(fileUri)
        return CodeActionService.getCodeActions(fileUri, range, indexer)
    }

    fun getCodeActionForFile(fileUri: URI): CodeAction {
        val indexer = indexingManager.getIndexerFor(fileUri)
        return CodeActionService.getCodeActionForFile(fileUri, indexer)
    }

    fun rename(fileUri: URI, position: Position, newName: String): WorkspaceEdit {
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
            override fun visitRuleX_AnonAttrHeader(ctx: RellParser.RuleX_AnonAttrHeaderContext) {
                val startPos = ctx.start.line
                if (startPos == location.range.start.line + 1 &&
                    ctx.stop.charPositionInLine == location.range.start.character
                ) {
                    val range = Range(
                        Position(
                            ctx.ruleX_QualifiedNameNode().start.line - 1,
                            ctx.ruleX_QualifiedNameNode().start.charPositionInLine
                        ),
                        Position(
                            ctx.ruleX_QualifiedNameNode().stop.line - 1,
                            ctx.ruleX_QualifiedNameNode().stop.charPositionInLine +
                                ctx.ruleX_QualifiedNameNode().stop.text.length
                        )
                    )
                    result = FullNameWithRange(
                        ctx.ruleX_QualifiedNameNode().ruleX_NameNode().map { it.text },
                        range
                    )
                }
            }
        }
        visitor.visit(resource.parseTree)
        return visitor.result
    }

    fun getCompletions(fileUri: URI, position: Position): List<CompletionItem> {
        val document = documentManager.getOpenDocument(fileUri) ?: return listOf()
        val indexer = indexingManager.getIndexerForOrNull(fileUri) ?: return listOf()
        val offset = document.getOffSet(position)

        return completionService.getCompletions(fileUri, offset, indexer, document)
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
