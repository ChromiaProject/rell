package net.postchain.rell.toolbox.lsp.server

import net.postchain.rell.toolbox.formatter.FormatterOptions
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.indexer.findRellFilesInWorkspace
import net.postchain.rell.toolbox.linter.FormattingStyleLinter
import net.postchain.rell.toolbox.linter.LinterOptions
import net.postchain.rell.toolbox.linter.RellLinter
import net.postchain.rell.toolbox.lsp.caching.RellIndexCachingService
import net.postchain.rell.toolbox.lsp.editorconfig.RellFormatterOptionsResolver
import net.postchain.rell.toolbox.lsp.editorconfig.RellLinterOptionsResolver
import org.eclipse.lsp4j.WorkspaceFolder
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.toPath
import kotlin.time.Duration.Companion.minutes

class RellIndexingManager(
    private val indexCachingService: RellIndexCachingService,
    private val diagnosticsManager: RellDiagnosticsManager,
    private val rellLinter: RellLinter,
    private val formattingStyleLinter: FormattingStyleLinter,
    private val formatterOptionsResolver: RellFormatterOptionsResolver,
    private val linterOptionsResolver: RellLinterOptionsResolver
) {
    var indexCachingEnabled: Boolean = false
    val indexers: MutableMap<URI, WorkspaceIndexer> = ConcurrentHashMap()
    internal val orphanIndexers: MutableMap<URI, WorkspaceIndexer> = ConcurrentHashMap()
    private lateinit var workspaceFolders: List<WorkspaceFolder>

    private val workspaceFolderUris
        get() = workspaceFolders.map {
            parseFileUri(it.uri) ?: throw IllegalArgumentException("Invalid workspace folder ${it.uri}")
        }

    fun initialize(workspaceFolders: List<WorkspaceFolder>) {
        this.workspaceFolders = workspaceFolders
        runIndexers()
        handleNestedIndexers()
        diagnosticsManager.reportAllDiagnostics(getAllIndexers())
    }

    fun runIndexers() {
        val newIndexers = workspaceFolderUris.flatMap { workspaceFolder ->
            val indexRoots = IndexRoot.findIndexRoots(workspaceFolder)
            if (indexRoots.isEmpty()) {
                listOf(doIndex(WorkspaceDirectoryResolver.findSourceDirURI(workspaceFolder), workspaceFolder))
            } else {
                indexRoots.map { indexRoot ->
                    doIndex(indexRoot.sourceRootUri, indexRoot.chromiaConfigDirUri)
                }
            }
        }.associateBy { it.workspaceUri }

        val orphanIndexer = createOrphanIndexers(newIndexers.keys)

        indexers.clear()
        indexers.putAll(newIndexers)
        orphanIndexers.putAll(orphanIndexer)

        if (indexCachingEnabled) {
            indexCachingService.persistOnDiskPeriodically(indexers.values, 1.minutes)
        }
        indexCachingService.cleanupOldCaches()
    }

    fun indexFromRoots(chromiaConfigFiles: List<URI>) {
        val newIndexers =
            chromiaConfigFiles.map { IndexRoot.fromChromiaConfig(it.toPath()) }.map { indexRoot ->
                doIndex(indexRoot.sourceRootUri, indexRoot.chromiaConfigDirUri)
            }.associateBy { it.workspaceUri }

        indexers.putAll(newIndexers)
        cleanUpOrphans(newIndexers)
    }

    private fun createOrphanIndexers(excludeFolderUris: Set<URI>): Map<URI, WorkspaceIndexer> {
        val result = workspaceFolderUris.map { workspaceFolder ->
            doIndex(workspaceFolder, workspaceFolder, excludeFolderUris.map { it.toPath() }.toSet())
        }.filter {
            it.fileUriResourceMap.isNotEmpty()
        }.associateBy { it.workspaceUri }

        return result
    }

    private fun getLinterAndFormatterOptions(workspaceFolderUri: URI): Pair<LinterOptions, FormatterOptions> {
        val formatterOptions = formatterOptionsResolver.getWorkspaceFormattingOptions(workspaceFolderUri)
        val linterOptions = linterOptionsResolver.getLinterConfig(workspaceFolderUri)
        return Pair(linterOptions, formatterOptions)
    }

    private fun doIndex(
        resolvedSourceDirUri: URI,
        workspaceFolderUri: URI,
        excludeFolders: Set<Path> = emptySet()
    ): WorkspaceIndexer {
        val cachedIndexer =
            if (indexCachingEnabled) indexCachingService.getWorkspaceIndexer(resolvedSourceDirUri) else null

        val (linterOptions, formatterOptions) = getLinterAndFormatterOptions(workspaceFolderUri)

        val indexer =
            WorkspaceIndexer(
                resolvedSourceDirUri,
                rellLinter,
                linterOptions,
                formattingStyleLinter,
                formatterOptions,
                workspaceFolderUri,
                excludeFolders
            )
        indexer.initialFileIndexBuild(cachedIndexer)
        return indexer
    }

    private fun doSingleFileIndex(fileUri: URI): WorkspaceIndexer {
        val sourceDirUri = WorkspaceDirectoryResolver.findSourceDirURI(fileUri)
        val projectRootUri = WorkspaceDirectoryResolver.findProjectRootUriFromChild(sourceDirUri)

        val (linterOptions, formatterOptions) = getLinterAndFormatterOptions(sourceDirUri)

        val indexer = WorkspaceIndexer(
            sourceDirUri,
            rellLinter,
            linterOptions,
            formattingStyleLinter,
            formatterOptions,
            projectRootUri
        )
        val cachedIndexer = if (indexCachingEnabled) indexCachingService.getWorkspaceIndexer(sourceDirUri) else null

        indexer.initialFileIndexBuild(cachedIndexer)
        indexers[sourceDirUri] = indexer
        diagnosticsManager.reportDiagnostics(indexer)
        return indexer
    }

    fun getIndexerForConfigFile(uri: URI): WorkspaceIndexer? {
        val configFolder = uri.toPath().parent
        return indexers.values.find {
            it.workspaceUri.toPath().startsWith(configFolder)
        }
    }

    fun getIndexerFor(fileUri: URI): WorkspaceIndexer = getIndexerForOrNull(fileUri) ?: doSingleFileIndex(fileUri)

    private fun getIndexerForFolderOrNull(fileUri: URI): WorkspaceIndexer? {
        return indexers.values
            .filter { fileUri.startsWith(it.projectRootUri) }
            .maxByOrNull { it.projectRootUri?.path?.length ?: 0 }
            ?: getOrphanIndexer(fileUri)
    }

    fun getIndexerForOrNull(fileUri: URI): WorkspaceIndexer? {
        for (indexer in indexers.entries) {
            val filePath = fileUri.path.trimEnd('/')
            val indexerPath = indexer.key.path?.trimEnd('/') ?: continue
            if (filePath.startsWith(indexerPath)) {
                return indexer.value
            }
        }
        return getOrphanIndexer(fileUri)
    }

    private fun getOrphanIndexer(fileUri: URI): WorkspaceIndexer? {
        for (indexer in orphanIndexers.entries) {
            if (fileUri.path.startsWith(indexer.value.projectRootUri?.path ?: continue)) {
                return indexer.value
            }
        }
        return null
    }

    fun getAllIndexers(): List<WorkspaceIndexer> = indexers.values.toList()

    fun getResource(fileUri: URI): Resource? =
        getIndexerFor(fileUri).getResource(fileUri)

    fun updateFileContent(fileUri: URI, content: String) {
        val indexer = getIndexerFor(fileUri)
        indexer.updateFileUriResourceMap(fileUri, content)
        diagnosticsManager.reportDiagnostics(indexer, listOf(fileUri))
    }

    fun handleFileChanges(dirtyFiles: List<URI>, deletedFiles: List<URI>, updateAffectedFiles: Boolean): List<URI> {
        val affectedUris = mutableSetOf<URI>()

        handleDeletedFiles(deletedFiles, updateAffectedFiles, affectedUris)
        handleDirtyFiles(dirtyFiles, updateAffectedFiles, affectedUris)

        affectedUris.removeAll((deletedFiles + dirtyFiles).toSet())
        updateAffectedFiles(affectedUris)

        val allUris = (dirtyFiles + deletedFiles + affectedUris)
        reportDiagnostics(allUris)
        return allUris
    }

    private fun reportDiagnostics(fileUris: List<URI>) {
        if (fileUris.isNotEmpty()) {
            for (indexer in indexers.values) {
                diagnosticsManager.reportDiagnostics(indexer, fileUris)
            }
        }
    }

    fun handleFolderChanges(dirtyFolders: List<URI>, deletedFolders: List<URI>) {
        if (isFolderRename(dirtyFolders, deletedFolders)) {
            handleFolderRename(dirtyFolders, deletedFolders)
        } else {
            syncFileAndFolderState(dirtyFolders, deletedFolders)
        }
    }

    private fun isFolderRename(dirtyFolders: List<URI>, deletedFolders: List<URI>): Boolean {
        return dirtyFolders.size == 1 && deletedFolders.size == 1
    }

    private fun handleFolderRename(dirtyFolders: List<URI>, deletedFolders: List<URI>) {
        val deletedFolderUri = deletedFolders.firstOrNull() ?: return
        val newFolderUri = dirtyFolders.firstOrNull() ?: return

        getIndexerForFolderOrNull(deletedFolderUri)?.let { indexer ->
            if (deletedFolderIsIndexerRoot(indexer, deletedFolderUri)) {
                folderRenameOnIndexerProjectRoot(indexer, newFolderUri)
            } else {
                syncFileAndFolderState(dirtyFolders, deletedFolders)
            }
        }
    }

    private fun deletedFolderIsIndexerRoot(indexer: WorkspaceIndexer, deletedFolderUri: URI) =
        indexer.projectRootUri?.path?.trimEnd('/') == deletedFolderUri.path.trimEnd('/')

    private fun folderRenameOnIndexerProjectRoot(indexer: WorkspaceIndexer, newFolderUri: URI) {
        val indexRoots = IndexRoot.findIndexRoots(newFolderUri)

        val newIndexers = if (indexRoots.isEmpty()) {
            listOf(doIndex(WorkspaceDirectoryResolver.findSourceDirURI(newFolderUri), newFolderUri))
        } else {
            indexRoots.map { indexRoot ->
                doIndex(indexRoot.sourceRootUri, indexRoot.chromiaConfigDirUri)
            }
        }.associateBy { it.workspaceUri }

        indexers.putAll(newIndexers)
        indexers.remove(indexer.workspaceUri)
        cleanUpOrphans(newIndexers)
    }

    private fun syncFileAndFolderState(dirtyFolders: List<URI>, deletedFolders: List<URI>) {
        val deletedFiles = mutableListOf<URI>()
        val dirtyFiles = mutableListOf<URI>()

        deletedFolders.forEach { uri ->
            getIndexerForOrNull(uri)?.let { indexer ->
                deletedFiles.addAll(indexer.getFileUrisWithPrefix(uri))
            }
        }

        dirtyFolders.forEach { uri ->
            findRellFilesInWorkspace(File(uri), dirtyFiles)
        }

        // Need to do two passes of the files to guarantee that imports and modules are resolved correctly
        dirtyFiles.addAll(dirtyFiles)
        handleFileChanges(dirtyFiles, deletedFiles, true)
    }

    private fun handleDeletedFiles(
        deletedFiles: List<URI>,
        updateAffectedFiles: Boolean,
        affectedUris: MutableSet<URI>
    ) {
        deletedFiles.forEach { uri ->
            getIndexerFor(uri).let { indexer ->
                if (updateAffectedFiles) {
                    affectedUris.addAll(indexer.findAffectedFiles(uri))
                }
                indexer.removeFileUriResourceMap(uri)
                diagnosticsManager.clearDiagnostics(uri)
            }
        }
    }

    private fun handleDirtyFiles(dirtyFiles: List<URI>, updateAffectedFiles: Boolean, affectedUris: MutableSet<URI>) {
        dirtyFiles.forEach { uri ->
            getIndexerFor(uri).let { indexer ->
                val updatedResource = indexer.updateFileUriResourceMap(uri)
                if (updateAffectedFiles && updatedResource != null) {
                    affectedUris.addAll(indexer.findAffectedFiles(uri))
                }
            }
        }
    }

    private fun updateAffectedFiles(affectedUris: Set<URI>) {
        affectedUris.forEach { uri ->
            getIndexerFor(uri).updateFileUriResourceMap(uri)
        }
    }

    // TODO: Clean up depth of nesting
    private fun cleanUpOrphans(indexers: Map<URI, WorkspaceIndexer>) {
        indexers.values.forEach { indexer ->
            orphanIndexers.forEach { orphanIndexer ->
                orphanIndexer.value.fileUriResourceMap.keys.forEach { uri ->
                    if (indexer.fileUriResourceMap[uri] != null) {
                        orphanIndexer.value.removeFileUriResourceMap(uri)
                    }
                }
            }
        }
    }

    private fun handleNestedIndexers() {
        val nestedProjects = indexers.keys.flatMap { key ->
            indexers.keys.filter { it != key && it.startsWith(key) }
                .takeIf { it.isNotEmpty() }
                ?.toMutableList()
                ?.apply { add(key) }
                ?: emptyList()
        }.map { it.path }

        if (nestedProjects.isNotEmpty()) {
            diagnosticsManager.sendNotification(
                NotificationType.WARNING,
                "Nested Rell projects detected. The projects with overlapping paths:\n" +
                    nestedProjects.joinToString() +
                    "\n Consider restructuring the project directories to avoid nested configurations."
            )
        }
    }
}
