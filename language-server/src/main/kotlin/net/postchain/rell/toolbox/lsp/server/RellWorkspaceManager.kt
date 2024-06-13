package net.postchain.rell.toolbox.lsp.server

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes
import net.postchain.rell.base.utils.ide.IdeSymbolInfo
import net.postchain.rell.base.utils.ide.IdeSymbolKind
import net.postchain.rell.toolbox.core.indexer.RellIssue
import net.postchain.rell.toolbox.core.indexer.Resource
import net.postchain.rell.toolbox.core.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.core.indexer.findRellFilesInWorkspace
import net.postchain.rell.toolbox.core.parser.RellBaseVisitor
import net.postchain.rell.toolbox.core.parser.RellParser
import net.postchain.rell.toolbox.lsp.caching.RellIndexCachingService
import net.postchain.rell.toolbox.lsp.editing.Document
import net.postchain.rell.toolbox.lsp.hover.formatDocSymbol
import net.postchain.rell.toolbox.lsp.references.RellReferenceService
import net.postchain.rell.toolbox.lsp.symbols.RellSymbolService
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

val TYPE_DEFINITIONS =
    setOf(IdeSymbolKind.DEF_ENTITY, IdeSymbolKind.DEF_STRUCT, IdeSymbolKind.DEF_ENUM, IdeSymbolKind.DEF_TYPE)

class RellWorkspaceManager(
    private val rellSymbolService: RellSymbolService,
    private val rellReferenceService: RellReferenceService,
    private val indexCachingService: RellIndexCachingService
) {

    var indexCachingEnabled: Boolean = false

    private lateinit var workspaceFolders: List<WorkspaceFolder>
    private lateinit var diagnosticsPublisher: (uri: URI, List<RellIssue>) -> Unit
    val indexers: MutableMap<URI, WorkspaceIndexer> = ConcurrentHashMap()
    val openDocuments: MutableMap<URI, Document> = mutableMapOf()

    private val workspaceFolderUris get() = workspaceFolders.map { URI(it.uri) }

    //TODO: Should we have this the contractor or a init{} constructor
    fun initialize(workspaceFolders: List<WorkspaceFolder>, diagnosticsPublisher: (uri: URI, List<RellIssue>) -> Unit) {
        this.workspaceFolders = workspaceFolders
        this.diagnosticsPublisher = diagnosticsPublisher

        runIndexers()
    }

    fun runIndexers() {
        val newIndexers = mutableMapOf<URI, WorkspaceIndexer>()
        workspaceFolderUris.forEach { workspaceFolder ->
            val indexer = doIndex(workspaceFolder)
            newIndexers[indexer.workspaceUri] = indexer
            reportDiagnostics(indexer)
        }
        indexers.clear()
        indexers.putAll(newIndexers)

        if (indexCachingEnabled) {
            indexCachingService.persistOnDiskPeriodically(indexers.values, 1.minutes)
        }
        indexCachingService.cleanupOldCaches()
    }

    private fun doIndex(workspaceFolderUri: URI): WorkspaceIndexer {
        val resolvedSourceDirUri = findSourceDirURI(workspaceFolderUri)
        val cachedIndexer =
            if (indexCachingEnabled) indexCachingService.getWorkspaceIndexer(resolvedSourceDirUri) else null
        val indexer = WorkspaceIndexer(resolvedSourceDirUri)
        indexer.initialFileIndexBuild(cachedIndexer)
        return indexer
    }

    private fun findSourceDirURI(workspaceUri: URI): URI {
        val workspaceFolder = File(workspaceUri)

        val rellSrcFolder = workspaceFolder.resolve("rell/src")
        val rellFolder = workspaceFolder.resolve("rell")
        val srcFolder = workspaceFolder.resolve("src")
        val parentSrcFolder = findSrcParentDirectory(workspaceUri)

        val sourceFolder = when {
            rellSrcFolder.exists() && rellFolder.isDirectory -> rellSrcFolder
            rellFolder.exists() && rellFolder.isDirectory -> rellFolder
            srcFolder.exists() && srcFolder.isDirectory -> srcFolder
            parentSrcFolder != null -> parentSrcFolder
            else -> null
        }
        return sourceFolder?.toURI() ?: workspaceUri
    }

    private fun findSrcParentDirectory(uri: URI): File? {
        if (!uri.path.endsWith(".rell")) return null

        val path = Paths.get(uri)
        var depth = 0
        var currentPath = path
        while (currentPath.parent != null && depth < 5) { // todo make this variable configurable
            depth++
            val srcDirectory = currentPath.resolveSibling("src")
            if (Files.exists(srcDirectory) && Files.isDirectory(srcDirectory)) {
                return srcDirectory.toFile()
            }
            currentPath = currentPath.parent
        }
        return null
    }

    private fun reportDiagnostics(indexer: WorkspaceIndexer, fileUris: List<URI> = listOf()) {
        var issues = indexer.getAllIssues()
        if (fileUris.isNotEmpty()) {
            issues = issues.filter { (uri, _) -> fileUris.contains(uri) }
        }
        issues.forEach { (uri, issues) ->
            diagnosticsPublisher(uri, issues)
        }
    }

    fun getHoverDocumentation(params: HoverParams): MarkupContent {
        val fileUri = parseFileUri(params.textDocument.uri) ?: return MarkupContent("plaintext", "")
        val indexer = getIndexerFor(fileUri)
        val document = openDocuments[fileUri] ?: return MarkupContent("plaintext", "")
        val symbolLocation = rellSymbolService.getSymbolLocationsWithSymbol(document, indexer, params.position)
        return MarkupContent("markdown", formatDocSymbol(symbolLocation?.second?.doc))
    }

    private fun reportDiagnostics(fileUris: List<URI>) {
        if (fileUris.isNotEmpty()) {
            for (indexer in indexers.values) {
                reportDiagnostics(indexer, fileUris)
            }
        }
    }

    fun didOpen(fileUri: URI, version: Int, content: String) {
        openDocuments[fileUri] = Document(fileUri, version, content)
        val indexer = getIndexerFor(fileUri)
        indexer.updateFileUriResourceMap(fileUri, content)
        reportDiagnostics(indexer, listOf(fileUri))
    }

    //TODO: Revisit how we get the indexer. Would this approach work if the have two indexer active where one
    // is indexed from from a child folder from the other indexer.
    fun getIndexerFor(fileUri: URI): WorkspaceIndexer {
        for (indexer in indexers.entries) {
            if (fileUri.path.startsWith(indexer.key.path)) {
                return indexer.value
            }
        }
        return doSingleFileIndex(fileUri)
    }

    private fun doSingleFileIndex(fileUri: URI): WorkspaceIndexer {
        val workspace = findSourceDirURI(fileUri)
        val indexer = WorkspaceIndexer(workspace)
        val cachedIndexer = if (indexCachingEnabled) indexCachingService.getWorkspaceIndexer(workspace) else null

        indexer.initialFileIndexBuild(cachedIndexer)
        indexers[workspace] = indexer
        reportDiagnostics(indexer)
        return indexer
    }

    fun didClose(fileUri: URI) {
        openDocuments.remove(fileUri)
    }


    fun didChangeTextDocumentContent(fileUri: URI, version: Int, contentChanges: List<TextDocumentContentChangeEvent>) {
        val document = openDocuments[fileUri]
        if (document == null) {
            logger.error { "The document $fileUri has not been opened." }
            return
        }
        val updatedDocument = document.applyTextDocumentChanges(contentChanges)
        val indexer = getIndexerFor(fileUri)
        openDocuments[fileUri] = updatedDocument
        indexer.updateFileUriResourceMap(fileUri, updatedDocument.content)
        reportDiagnostics(indexer, listOf(fileUri))
    }

    fun didChangeFiles(dirtyFiles: List<URI>, deletedFiles: List<URI>, updateAffectedFiles: Boolean = false) {
        val affectedUris = mutableSetOf<URI>()

        deletedFiles.forEach { uri ->
            getIndexerFor(uri).let { indexer ->
                if (updateAffectedFiles) {
                    affectedUris.addAll(indexer.findAffectedFiles(uri))
                }
                indexer.removeFileUriResourceMap(uri)
                diagnosticsPublisher(uri, listOf())
            }
        }

        dirtyFiles.forEach { uri ->
            getIndexerFor(uri).let { indexer ->
                indexer.updateFileUriResourceMap(uri)
                if (updateAffectedFiles) {
                    affectedUris.addAll(indexer.findAffectedFiles(uri))
                }
            }
        }
        affectedUris.removeAll((deletedFiles + dirtyFiles).toSet())
        affectedUris.forEach { uri ->
            getIndexerFor(uri).updateFileUriResourceMap(uri)
        }

        val allUris = listOf(dirtyFiles, deletedFiles, affectedUris).flatten()
        reportDiagnostics(allUris)
    }

    fun didChangeFolders(dirtyFolders: List<URI>, deletedFolders: List<URI>) {
        val deletedFiles = mutableListOf<URI>()
        val dirtyFiles = mutableListOf<URI>()
        deletedFolders.forEach { uri ->
            getIndexerFor(uri).let { indexer ->
                deletedFiles.addAll(indexer.getFileUrisWithPrefix(uri))
            }
        }

        dirtyFolders.forEach { uri ->
            findRellFilesInWorkspace(File(uri), dirtyFiles)
        }

        //Need to do two passes of the files to guarantee that imports and modules are resolved correctly
        dirtyFiles.addAll(dirtyFiles)
        didChangeFiles(dirtyFiles, deletedFiles, true)
    }

    fun didSave(fileUri: URI) {
        val contents = openDocuments[fileUri]
        if (contents == null) {
            logger.error { "The document $fileUri has not been opened." }
            return
        }
        val indexer = getIndexerFor(fileUri)
        indexer.let {
            val affectedUris = indexer.findAffectedFiles(fileUri)
            didChangeFiles(affectedUris.toList() + fileUri, listOf())
        }
    }

    fun getResource(fileUri: URI): Resource? {
        val indexer = getIndexerFor(fileUri)
        return indexer.getResource(fileUri)
    }


    fun getDefinitionLocations(
        fileUri: URI,
        position: Position
    ): Either<MutableList<out Location>, MutableList<out LocationLink>> {
        val indexer = getIndexerFor(fileUri)
        val document = openDocuments[fileUri] ?: return Either.forLeft(mutableListOf())
        return Either.forLeft(rellSymbolService.getSymbolLocations(document, indexer, position))
    }

    private fun getDefinitionLocationsAndSymbolInfo(
        fileUri: URI,
        position: Position
    ): Pair<Location, IdeSymbolInfo>? {
        val indexer = getIndexerFor(fileUri)
        val document = openDocuments[fileUri] ?: return null

        return rellSymbolService.getSymbolLocationsWithSymbol(document, indexer, position)
    }

    fun getDocumentSymbols(fileUri: URI): List<Either<SymbolInformation, DocumentSymbol>> {
        val resource = getResource(fileUri) ?: return listOf()
        val document = openDocuments[fileUri] ?: return listOf()
        return rellSymbolService.getDocumentSymbols(fileUri, document, resource)
    }

    private fun getDocument(uri: URI): Document = getOpenDocument(uri) ?: Document(
        uri,
        version = 0,
        content = File(uri).readText()
    )

    fun getOpenDocument(uri: URI): Document? = openDocuments[uri]

    fun getReferenceLocations(fileUri: URI, position: Position?, includeDefinition: Boolean = true): List<Location> {
        val indexer = getIndexerFor(fileUri)
        val document = openDocuments[fileUri] ?: return listOf()

        val result: MutableList<Location> = mutableListOf()
        if (includeDefinition && position != null) {
            val locationIdeSymbolInfoPair = getDefinitionLocationsAndSymbolInfo(fileUri, position)
            if (locationIdeSymbolInfoPair != null && locationIdeSymbolInfoPair.second.kind != IdeSymbolKind.DEF_IMPORT_MODULE)
                result.add(locationIdeSymbolInfoPair.first)
        }

        result.addAll(rellReferenceService.getReferenceLocations(fileUri, document, indexer, position))
        return result
    }

    fun prepareRename(
        fileUri: URI,
        position: Position
    ): Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior> {
        val indexer = getIndexerFor(fileUri)
        val document = openDocuments[fileUri] ?: return Either3.forThird(PrepareRenameDefaultBehavior())

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

    fun rename(fileUri: URI, position: Position, newName: String): WorkspaceEdit {
        val indexer = getIndexerFor(fileUri)
        val document = openDocuments[fileUri] ?: return WorkspaceEdit()
        val (_, interval) =
            rellSymbolService.getSymbolInfoWithInterval(document, indexer, position)
                ?: return WorkspaceEdit()
        val oldName = document.getTextIn(interval)

        val locations = getReferenceLocations(fileUri, position, true)
        val changes = mutableMapOf<String, MutableList<TextEdit>>()
        locations.forEach { location ->
            val change = renameLocation(indexer, location, oldName, newName)
            changes[location.uri]?.add(change) ?: changes.put(location.uri, mutableListOf(change))
        }
        return WorkspaceEdit(changes)
    }

    private fun renameLocation(
        indexer: WorkspaceIndexer,
        location: Location,
        oldName: String,
        newName: String
    ): TextEdit {
        val locationFileUri = URI(location.uri)
        val symbolInfo = rellSymbolService.getSymbolInfoWithInterval(
            getDocument(locationFileUri),
            indexer,
            location.range.start
        )?.ideSymbolInfo
        return if (symbolInfo != null) {
            if (symbolInfo.kind in TYPE_DEFINITIONS && symbolInfo.defId != null && symbolInfo.link != null) {
                val resource = indexer.getResource(locationFileUri)
                val fullNameWithRange = resource?.let { findAnonAttrFullName(it, location) }
                if (fullNameWithRange != null && fullNameWithRange.fullName.size > 1) {
                    TextEdit(
                        fullNameWithRange.range,
                        "$oldName: ${fullNameWithRange.fullName.dropLast(1).joinToString(separator = ".")}.$newName"
                    )
                } else {
                    TextEdit(location.range, "$oldName: $newName")
                }
            } else {
                TextEdit(location.range, newName)
            }
        } else {
            TextEdit(location.range, newName)
        }
    }

    data class FullNameWithRange(val fullName: List<String>, val range: Range)

    private fun findAnonAttrFullName(resource: Resource, location: Location): FullNameWithRange? {
        val visitor = object : RellBaseVisitor<Unit>() {
            var result: FullNameWithRange? = null
            override fun visitRuleX_AnonAttrHeader(ctx: RellParser.RuleX_AnonAttrHeaderContext) {
                val startPos = ctx.start.line
                if (startPos == location.range.start.line + 1 && ctx.stop.charPositionInLine == location.range.start.character) {
                    result = FullNameWithRange(
                        ctx.ruleX_QualifiedName().ruleX_Name().map { it.text },
                        Range(
                            Position(
                                ctx.ruleX_QualifiedName().start.line - 1,
                                ctx.ruleX_QualifiedName().start.charPositionInLine
                            ),
                            Position(
                                ctx.ruleX_QualifiedName().stop.line - 1,
                                ctx.ruleX_QualifiedName().stop.charPositionInLine + ctx.ruleX_QualifiedName().stop.text.length
                            )
                        )
                    )
                }
            }
        }
        visitor.visit(resource.parseTree)
        return visitor.result
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
