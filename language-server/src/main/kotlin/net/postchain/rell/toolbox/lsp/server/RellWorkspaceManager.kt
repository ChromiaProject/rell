package net.postchain.rell.toolbox.lsp.server

import io.github.oshai.kotlinlogging.KotlinLogging
import net.postchain.rell.toolbox.core.indexer.RellIssue
import net.postchain.rell.toolbox.core.indexer.Resource
import net.postchain.rell.toolbox.core.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.lsp.editing.Document
import org.antlr.v4.runtime.misc.Interval
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.io.File
import java.net.URI


class RellWorkspaceManager {

    private val logger = KotlinLogging.logger {}

    private lateinit var workspaceFolders: List<WorkspaceFolder>
    private lateinit var diagnosticsPublisher: (uri: URI, List<RellIssue>) -> Unit
    val indexers: MutableMap<URI, WorkspaceIndexer> = mutableMapOf()
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
    }

    private fun doIndex(workspaceFolderUri: URI): WorkspaceIndexer {
        val resolvedSourceDirUri = findSourceDirURI(workspaceFolderUri)
        val indexer = WorkspaceIndexer(resolvedSourceDirUri)
        indexer.initialFileIndexBuild()
        return indexer
    }

    private fun findSourceDirURI(workspaceUri: URI): URI {
        val workspaceFolder = File(workspaceUri)

        val rellSrcFolder = workspaceFolder.resolve("rell/src")
        val rellFolder = workspaceFolder.resolve("rell")
        val srcFolder = workspaceFolder.resolve("src")

        val sourceFolder = when {
            rellSrcFolder.exists() && rellFolder.isDirectory -> rellSrcFolder
            rellFolder.exists() && rellFolder.isDirectory -> rellFolder
            srcFolder.exists() && srcFolder.isDirectory -> srcFolder
            else -> null
        }
        return sourceFolder?.toURI() ?: workspaceUri
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

    private fun reportDiagnostics(fileUris: List<URI>) {
        for (indexer in indexers.values) {
            reportDiagnostics(indexer, fileUris)
        }
    }

    fun didOpen(fileUri: URI, version: Int, content: String) {
        openDocuments[fileUri] = Document(version, content)
        val indexer = getIndexerFor(fileUri)
        indexer.updateFileUriResourceMap(fileUri, content)
        reportDiagnostics(indexer, listOf(fileUri))
    }

    //TODO: Revisit how we get the indexer. Would this approach work if the have two indexer active where one
    // is indexed from from a child folder from the other indexer.
    fun getIndexerFor(fileUri: URI): WorkspaceIndexer {
//        for (indexer in indexers.values) {
//            if (indexer.hasFile(uri)) {
//                return indexer
//            }
//        }

        for (indexer in indexers.entries) {
            if (fileUri.path.startsWith(indexer.key.path)) {
                return indexer.value
            }
        }

        return doSingleFileIndex(fileUri)
    }

    private fun doSingleFileIndex(fileUri: URI): WorkspaceIndexer {
        val parentUri = File(fileUri).parentFile.toURI()
        val indexer = WorkspaceIndexer(parentUri)
        indexer.initialFileIndexBuild()
        indexers[parentUri] = indexer
        reportDiagnostics(indexer)
        return indexer
    }

    fun didClose(fileUri: URI) {
        openDocuments.remove(fileUri)
        // TODO: Do we need to update on close?
        didChangeFiles(listOf(fileUri), listOf())
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
        indexer.updateFileUriResourceMap(fileUri, updatedDocument.contents)
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

    private fun isDocumentOpen(fileUri: URI): Boolean {
        return openDocuments.containsKey(fileUri)
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

    fun getDefinitionCandidates (fileUri: URI, position: Position) : Either<MutableList<out Location>, MutableList<out LocationLink>> {
        val indexer = getIndexerFor(fileUri)
        val ws = indexer.workspaceUri
        val document = openDocuments[fileUri]!! //TODO FIX Fallback
        val resource = indexer.getResource(fileUri)!! //TODO FIX Fallback
        val offset = document.getOffSet(position) - 1 // line starts on 0
        val results = resource.locationInfo
            .filter { it.key.properlyContains(Interval.of(offset, offset)) }
            .values
            .toList()

        val returnValue: MutableList<Location> = mutableListOf()
        results.forEach {
            val link = it.link ?: return@forEach
            val globalId = link.globalId()
            if (globalId != null) {
                val file = globalId.file
                val symId = globalId.symId
                var uri = URI(file.toString())

                if (uri != null) {
                    uri = URI(ws.toString() + uri.toString())
                    val pos = getResource(uri)!!.symbolInfos.entries.find { it.value.defId == symId }!!.key //TODO fix fallback
                    returnValue.add(Location(uri.toString(), Range(Position(pos.line(), pos.column()), Position(pos.line(), pos.column())))) //TODO make preatty
                }

            }
        }

        return Either.forLeft(returnValue)
    }
}
