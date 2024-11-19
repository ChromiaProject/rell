package net.postchain.rell.toolbox.lsp.server

import net.postchain.rell.toolbox.chromia.ChromiaModelProvider
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
import java.nio.file.Files
import java.nio.file.Paths
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
    private lateinit var workspaceFolders: List<WorkspaceFolder>

    private val workspaceFolderUris get() = workspaceFolders.map {
        parseFileUri(it.uri) ?: throw IllegalArgumentException("Invalid workspace folder ${it.uri}")
    }

    fun initialize(workspaceFolders: List<WorkspaceFolder>) {
        this.workspaceFolders = workspaceFolders
        runIndexers()
        diagnosticsManager.reportAllDiagnostics(getAllIndexers())
    }

    fun runIndexers() {
        val newIndexers = mutableMapOf<URI, WorkspaceIndexer>()
        workspaceFolderUris.forEach { workspaceFolder ->
            val indexer = doIndex(workspaceFolder)
            newIndexers[indexer.workspaceUri] = indexer
            diagnosticsManager.reportDiagnostics(indexer)
        }
        indexers.clear()
        indexers.putAll(newIndexers)

        if (indexCachingEnabled) {
            indexCachingService.persistOnDiskPeriodically(indexers.values, 1.minutes)
        }
        indexCachingService.cleanupOldCaches()
    }

    private fun getLinterAndFormatterOptions(workspaceFolderUri: URI): Pair<LinterOptions, FormatterOptions> {
        val formatterOptions = formatterOptionsResolver.getWorkspaceFormattingOptions(workspaceFolderUri)
        val linterOptions = linterOptionsResolver.getLinterConfig(workspaceFolderUri)
        return Pair(linterOptions, formatterOptions)
    }

    private fun doIndex(workspaceFolderUri: URI): WorkspaceIndexer {
        val resolvedSourceDirUri = findSourceDirURI(workspaceFolderUri)
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
                workspaceFolderUri
            )
        indexer.initialFileIndexBuild(cachedIndexer)
        return indexer
    }

    fun findSourceDirURI(workspaceUri: URI): URI {
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
        return sourceFolder?.toURI() ?: workspaceFolder.toURI()
    }

    private fun findSrcParentDirectory(uri: URI): File? {
        if (!uri.path.endsWith(".rell")) return null

        val path = Paths.get(uri)
        var depth = 0
        var currentPath = path
        while (currentPath.parent != null && depth < MAX_DEPTH) {
            depth++
            val srcDirectory = currentPath.resolveSibling("src")
            if (Files.exists(srcDirectory) && Files.isDirectory(srcDirectory)) {
                return srcDirectory.toFile()
            }
            currentPath = currentPath.parent
        }
        return null
    }

    private fun doSingleFileIndex(fileUri: URI): WorkspaceIndexer {
        val sourceDirUri = findSourceDirURI(fileUri)
        val projectRootUri = findProjectRootURI(sourceDirUri)

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

    // TODO: Revisit how we get the indexer. Would this approach work if the have two indexer active where one
    // is indexed from from a child folder from the other indexer.
    fun getIndexerFor(fileUri: URI): WorkspaceIndexer = getIndexerForOrNull(fileUri) ?: doSingleFileIndex(fileUri)

    fun getIndexerForOrNull(fileUri: URI): WorkspaceIndexer? {
        for (indexer in indexers.entries) {
            if (fileUri.path.startsWith(indexer.key.path)) {
                return indexer.value
            }
        }
        return null
    }

    private fun findProjectRootURI(sourceDirUri: URI): URI? {
        val parentDir = File(sourceDirUri).parentFile ?: return null
        val grandParentDir = parentDir.parentFile ?: return null

        return when {
            parentDir.resolve(ChromiaModelProvider.DEFAULT_CHROMIA_MODEL_FILENAME).exists() -> parentDir.toURI()
            grandParentDir.resolve(ChromiaModelProvider.DEFAULT_CHROMIA_MODEL_FILENAME)
                .exists() -> grandParentDir.toURI()

            else -> null
        }
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

    fun handleFolderChanges(dirtyFolders: List<URI>, deletedFolders: List<URI>): Pair<List<URI>, List<URI>> {
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

        return Pair(dirtyFiles, deletedFiles)
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
                indexer.updateFileUriResourceMap(uri)
                if (updateAffectedFiles) {
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

    companion object {
        private const val MAX_DEPTH = 5
    }
}
