package net.postchain.rell.toolbox.lsp.server

import net.postchain.rell.toolbox.formatter.FormatterOptions
import net.postchain.rell.toolbox.indexer.IndexingState
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.indexer.calculateChecksum
import net.postchain.rell.toolbox.linter.FormattingStyleLinter
import net.postchain.rell.toolbox.linter.LinterOptions
import net.postchain.rell.toolbox.linter.RellLinter
import net.postchain.rell.toolbox.lsp.caching.RellIndexCachingService
import net.postchain.rell.toolbox.lsp.editorconfig.RellFormatterOptionsResolver
import net.postchain.rell.toolbox.lsp.editorconfig.RellLinterOptionsResolver
import org.eclipse.lsp4j.WorkspaceFolder
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
) : IndexerRegistry {
    private val fileChangeHandler: FileChangeHandler = FileChangeHandler(diagnosticsManager, this)
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
        indexCachingService.cleanOldCacheFolder()
        runIndexers()
        handleNestedIndexers()
    }

    fun runIndexers(indexingStateHandler: ((IndexingState) -> Unit)? = null, skipCache: Boolean = false) {
        try {
            indexingStateHandler?.invoke(IndexingState.BEGIN)

            val newIndexers = workspaceFolderUris.flatMap { workspaceFolder ->
                val indexRoots = IndexRoot.findIndexRoots(workspaceFolder)
                if (indexRoots.isEmpty()) {
                    listOf(doIndex(WorkspaceDirectoryResolver.findSourceDirURI(workspaceFolder), workspaceFolder))
                } else {
                    indexRoots.map { indexRoot ->
                        doIndex(indexRoot.sourceRootUri, indexRoot.chromiaConfigDirUri, skipCache = skipCache)
                    }
                }
            }.associateBy { it.workspaceUri }

            val orphanIndexer = createOrphanIndexers(newIndexers.keys)

            indexers.clear()
            indexers.putAll(newIndexers)
            orphanIndexers.putAll(orphanIndexer)

            diagnosticsManager.reportAllDiagnostics(getAllIndexers())

            if (indexCachingEnabled) {
                indexCachingService.persistOnDiskPeriodically(indexers.values, 1.minutes)
            }
            indexCachingService.cleanupOldCaches()
        } finally {
            indexingStateHandler?.invoke(IndexingState.END)
        }
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

    override fun doIndex(
        resolvedSourceDirUri: URI,
        workspaceFolderUri: URI,
        excludeFolders: Set<Path>,
        skipCache: Boolean,
    ): WorkspaceIndexer {
        val cachedIndexer = if (indexCachingEnabled && !skipCache) {
            indexCachingService.getWorkspaceIndexer(resolvedSourceDirUri)
        } else {
            null
        }

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

    override fun getIndexerFor(fileUri: URI): WorkspaceIndexer =
        getIndexerForOrNull(fileUri) ?: doSingleFileIndex(fileUri)

    override fun getIndexerForFolderOrNull(fileUri: URI): WorkspaceIndexer? {
        return indexers.values
            .filter { fileUri.startsWith(it.projectRootUri) }
            .maxByOrNull { it.projectRootUri?.path?.length ?: 0 }
            ?: getOrphanIndexer(fileUri)
    }

    override fun getIndexerForOrNull(fileUri: URI): WorkspaceIndexer? {
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

    fun getAllIndexers(): List<WorkspaceIndexer> = indexers.values.toList() + orphanIndexers.values.toList()

    override fun getAllIndexersMap(): MutableMap<URI, WorkspaceIndexer> = indexers

    fun getResource(fileUri: URI): Resource? =
        getIndexerFor(fileUri).getResource(fileUri)

    fun updateFileContent(fileUri: URI, content: String, skipCache: Boolean = false) {
        val indexer = getIndexerFor(fileUri)
        indexer.updateFileUriResourceMap(fileUri, content)
        diagnosticsManager.reportDiagnostics(indexer, listOf(fileUri), skipCache)
    }

    fun handleFileChanges(dirtyFiles: List<URI>, deletedFiles: List<URI>, updateAffectedFiles: Boolean): List<URI> {
        return fileChangeHandler.handleFileChanges(
            dirtyFiles,
            deletedFiles,
            updateAffectedFiles
        )
    }

    fun handleFolderChanges(dirtyFolders: List<URI>, deletedFolders: List<URI>) {
        fileChangeHandler.handleFolderChanges(dirtyFolders, deletedFolders)
    }

    // TODO: Clean up depth of nesting
    override fun cleanUpOrphans(indexers: Map<URI, WorkspaceIndexer>) {
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

    fun resourceHasChanged(fileUri: URI): Boolean {
        return try {
            calculateChecksum(fileUri) != getResource(fileUri)?.checksum
        } catch (_: Exception) {
            true
        }
    }
}
