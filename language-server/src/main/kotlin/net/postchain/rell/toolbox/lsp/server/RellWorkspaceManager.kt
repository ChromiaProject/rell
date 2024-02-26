package net.postchain.rell.toolbox.lsp.server

import io.github.oshai.kotlinlogging.KotlinLogging
import net.postchain.rell.base.utils.ide.IdeSymbolInfo
import net.postchain.rell.base.utils.ide.IdeSymbolKind
import net.postchain.rell.toolbox.core.indexer.RellIssue
import net.postchain.rell.toolbox.core.indexer.Resource
import net.postchain.rell.toolbox.core.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.core.indexer.findRellFilesInWorkspace
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
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes


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
        val path = Paths.get(uri)
        var depth = 0
        var currentPath = path
        while (currentPath.parent != null && depth < 5) { // todo make this variable configurable
            depth++
            val srcDirectory = currentPath.resolve("src")
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

    fun getDocument(uri: URI): Document? {
        return openDocuments[uri]
    }

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

    fun rename(fileUri: URI, position: Position, newName: String): WorkspaceEdit {
        val locations = getReferenceLocations(fileUri, position, true)
        val changes = mutableMapOf<String, MutableList<TextEdit>>()
        locations.forEach { location ->
            val uri = location.uri
            val change = TextEdit(location.range, newName)
            changes[uri]?.add(change) ?: changes.put(uri, mutableListOf(change))
        }
        return WorkspaceEdit(changes)
    }

    fun prepareRename(
        fileUri: URI,
        position: Position
    ): Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior> {
        val indexer = getIndexerFor(fileUri)
        val document = openDocuments[fileUri] ?: return Either3.forThird(PrepareRenameDefaultBehavior())

        val location = rellSymbolService.getSymbolLocationForRenaming(document, indexer, position)
            ?: return Either3.forThird(PrepareRenameDefaultBehavior())

        return Either3.forFirst(location.range)
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
