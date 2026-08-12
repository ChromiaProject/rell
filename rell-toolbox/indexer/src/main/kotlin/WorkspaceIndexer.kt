/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.indexer

import io.github.oshai.kotlinlogging.KotlinLogging
import net.postchain.rell.base.compiler.base.utils.C_SourceFile
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.toolbox.chromia.ChromiaModelProvider
import net.postchain.rell.toolbox.chromia.RellCompatibility
import net.postchain.rell.toolbox.chromia.model.ChromiaModel
import net.postchain.rell.toolbox.common.fileName
import net.postchain.rell.toolbox.formatter.FormatterOptions
import net.postchain.rell.toolbox.linter.AbstractFormattingStyleLinter
import net.postchain.rell.toolbox.linter.AbstractRellLinter
import net.postchain.rell.toolbox.linter.LinterOptions
import net.postchain.rell.toolbox.parser.AntlrRellParser
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.toPath

class WorkspaceIndexer(
    val workspaceUri: URI,
    private val rellLinter: AbstractRellLinter,
    private val linterOptions: LinterOptions,
    private val formattingStyleLinter: AbstractFormattingStyleLinter,
    val formatterOptions: FormatterOptions,
    val projectRootUri: URI? = null,
    val excludeFolders: Set<Path> = emptySet(),
    /** The settings file governing this index root when it is not a `chromia.yml` found by name. */
    val chromiaConfigUri: URI? = null,
) {
    private val logger = KotlinLogging.logger {}
    private val chromiaModelProvider = ChromiaModelProvider(projectRootUri, chromiaConfigUri?.toPath())

    /** The Rell version this index root is analysed with — see `compile.rellVersion`. */
    val compatibility: RellCompatibility
        get() = chromiaModelProvider.getCompatibility()

    private var ignoreReportingUris: Set<URI> =
        chromiaModelProvider.resolveIgnoreReportingUris(workspaceUri)
    private val resourceFactory = RellResourceFactory(workspaceUri, AntlrRellParser(), chromiaModelProvider)
    private val rellCompilerUtils = RellCompilerUtils()
    var fileUriResourceMap = ConcurrentHashMap<URI, Resource>()
    var fileMap: ConcurrentHashMap<C_SourcePath, C_SourceFile> = ConcurrentHashMap()
    val resources: Collection<Resource>
        get() = fileUriResourceMap.values

    fun updateConfig(fileUri: URI, indexingStateNotifier: (state: IndexingState) -> Unit) {
        val configFile = File(fileUri)
        if (isLinterConfig(fileUri)) {
            linterOptions.updateOptionsFromFile(configFile)
            runLinter()
        }
        if (isFormatterConfig(fileUri)) {
            formatterOptions.updateOptionsFromFile(configFile)
            runLinter()
        }
        if (isChromiaModelFile(fileUri)) {
            val newModel = chromiaModelProvider.loadChromiaModel()
            val oldModel = chromiaModelProvider.getChromiaModel()
            chromiaModelProvider.updateChromiaModel(projectRootUri, newModel)
            val oldIgnoreReportingUris = ignoreReportingUris
            ignoreReportingUris = chromiaModelProvider.resolveIgnoreReportingUris(workspaceUri)

            if (shouldReindex(newModel, oldModel)) {
                try {
                    indexingStateNotifier(IndexingState.BEGIN)
                    initialFileIndexBuild(reindex = true)
                } finally {
                    indexingStateNotifier(IndexingState.END)
                }
            } else {
                if (shouldRunLinter(ignoreReportingUris, oldIgnoreReportingUris)) {
                    runLinter()
                }
            }
        }
    }

    private fun shouldRunLinter(newIgnoreReportingUris: Set<URI>, oldIgnoreReportingUris: Set<URI>): Boolean {
        return oldIgnoreReportingUris != newIgnoreReportingUris
    }

    private fun shouldReindex(newModel: ChromiaModel?, oldModel: ChromiaModel?): Boolean {
        return oldModel?.compile?.rellVersion != newModel?.compile?.rellVersion
    }

    fun initialFileIndexBuild(cachedIndexer: WorkspaceIndexer? = null, reindex: Boolean = false) {
        val rellUris = addRellFilesUri()
        val sources = readAllSource(rellUris)
        val parsedFiles = resourceFactory.parseFiles(sources)
        fileMap = resourceFactory.buildFileMap(sources, parsedFiles)

        val alreadyLintedFiles = useDataFromCachedIndexer(cachedIndexer, sources)

        // A single pass suffices: fileMap covers the whole workspace before any file is compiled,
        // so imports and modules resolve on the first compile. The second pass dates back to when
        // fileMap was filled inside this loop, and files indexed early could not see later ones.
        val filesToCompile = sources.keys.filter { fileUri ->
            isValidFileUri(fileUri) && (reindex || !fileUriResourceMap.containsKey(fileUri))
        }
        compileResources(filesToCompile, sources, parsedFiles)

        for (source in sources) {
            val (fileUri, fileContent) = source
            if (alreadyLintedFiles.contains(fileUri) && !reindex) {
                continue
            }
            fileUriResourceMap[fileUri]?.let {
                runLinter(it, fileContent)
            }
        }
    }

    /**
     * Compile each file against the completed [fileMap]. Compilation dominates initial indexing and
     * each file is independent — the AST holds no compilation state and every compile builds its own
     * source-directory view — so the files are spread over a few threads.
     *
     * The first file is compiled on the calling thread: the library framework's class initialization
     * is circular by construction, and driving it from several threads at once risks a class-init
     * deadlock. Once it is initialized, the rest can run concurrently.
     */
    private fun compileResources(
        fileUris: List<URI>,
        sources: Map<URI, String>,
        parsedFiles: Map<URI, ParsedRellFile>,
    ) {
        if (fileUris.isEmpty()) {
            return
        }

        fun compile(fileUri: URI) {
            val fileContent = sources[fileUri] ?: return
            fileUriResourceMap[fileUri] =
                resourceFactory.buildRellResource(fileUri, fileContent, fileMap, parsedFiles[fileUri])
        }

        compile(fileUris.first())
        val rest = fileUris.drop(1)
        if (rest.isEmpty()) {
            return
        }

        val executor = Executors.newFixedThreadPool(indexingParallelism(), IndexingThreadFactory())
        try {
            executor.invokeAll(rest.map { fileUri -> Callable { compile(fileUri) } }).forEach { it.get() }
        } finally {
            executor.shutdown()
        }
    }

    private fun ideConfigOptionsMatch(cachedIndexer: WorkspaceIndexer? = null): Boolean {
        return linterOptions == cachedIndexer?.linterOptions && formatterOptions == cachedIndexer.formatterOptions
    }

    private fun useDataFromCachedIndexer(cachedIndexer: WorkspaceIndexer?, sources: Map<URI, String>): Set<URI> {
        if (cachedIndexer == null) return setOf()
        val linterOptionsMatch = ideConfigOptionsMatch(cachedIndexer)
        val alreadyLintedFiles = mutableSetOf<URI>()

        for ((fileUri, fileContent) in sources) {
            val checksum = calculateChecksum(fileContent)
            val cachedResource = cachedIndexer.getResource(fileUri)
            if (cachedResource != null && cachedResource.checksum == checksum && getResource(fileUri) == null) {
                fileUriResourceMap[fileUri] = cachedResource
                if (linterOptionsMatch) {
                    alreadyLintedFiles.add(fileUri)
                }
            }
        }

        return alreadyLintedFiles
    }

    private fun readAllSource(rellUris: List<URI>): Map<URI, String> {
        val sources = mutableMapOf<URI, String>()
        for (fileUri in rellUris) {
            val fileContent = try {
                File(fileUri).readText()
            } catch (@Suppress("SwallowedException") _: Exception) {
                logger.warn { "Could not read file ${fileUri.fileName()}" }
                continue
            }
            sources[fileUri] = fileContent
        }
        return sources
    }

    fun getAllIssues(): Map<URI, List<RellIssue>> {
        val issues: MutableMap<URI, List<RellIssue>> = mutableMapOf()

        for ((uri, resource) in fileUriResourceMap) {
            issues[uri] = collectIssues(resource)
        }

        return issues
    }

    fun getAllLintAndFormatIssues(): Map<URI, List<RellIssue>> {
        val issues: MutableMap<URI, List<RellIssue>> = mutableMapOf()

        for ((uri, resource) in fileUriResourceMap) {
            issues[uri] = getLinterIssues(resource) + getFormatterIssues(resource)
        }

        return issues
    }

    private fun collectIssues(resource: Resource): List<RellIssue> = listOf(
        getSyntaxErrors(resource),
        getSemanticErrors(resource),
        getLinterIssues(resource),
        getFormatterIssues(resource)
    ).flatten()

    private fun getSyntaxErrors(resource: Resource): List<RellIssue> {
        return resource.syntaxErrors.map { RellIssue.fromSyntaxError(it, resource.tokenStream) }
    }

    private fun getSemanticErrors(resource: Resource): List<RellIssue> {
        return resource.fileSpecificSemanticErrors.map { RellIssue.fromCMessage(it, resource.tokenStream) }
    }

    private fun getLinterIssues(resource: Resource): List<RellIssue> {
        return if (shouldIgnoreReportingIssue(resource)) {
            emptyList()
        } else {
            resource.linterIssues.map(RellIssue::fromLinterIssue)
        }
    }

    private fun getFormatterIssues(resource: Resource): List<RellIssue> {
        return if (shouldIgnoreReportingIssue(resource)) {
            emptyList()
        } else {
            resource.formatterIssues.map(RellIssue::fromFormatterIssue)
        }
    }

    private fun shouldIgnoreReportingIssue(resource: Resource): Boolean {
        return ignoreReportingUris.any { resource.fileUri.path.startsWith(it.path) }
    }

    // Change in source code
    fun updateFileUriResourceMap(fileUri: URI): Resource? {
        if (!isValidFileUri(fileUri)) {
            return null
        }
        return if (!File(fileUri).exists()) {
            removeFileUriResourceMap(fileUri)
            null
        } else {
            val fileContent = File(fileUri).readText()
            val resource = resourceFactory.buildRellResource(fileUri, fileContent, fileMap)

            runLinter(resource, fileContent)

            fileUriResourceMap[fileUri] = resource
            resourceFactory.updateFileMap(fileMap, fileUri, fileContent)
            resource
        }
    }

    fun updateFileUriResourceMap(fileUri: URI, fileContent: String) {
        if (!isValidFileUri(fileUri)) {
            return
        }
        if (!File(fileUri).exists()) {
            removeFileUriResourceMap(fileUri)
        } else {
            val resource = resourceFactory.buildRellResource(fileUri, fileContent, fileMap)
            runLinter(resource, fileContent)
            fileUriResourceMap[fileUri] = resource
            resourceFactory.updateFileMap(fileMap, fileUri, fileContent)
        }
    }

    private fun isValidFileUri(fileUri: URI): Boolean {
        if (isGitScheme(fileUri)) {
            logger.info { "Skipping indexing of file ${fileUri.fileName()} because it is a git file" }
            return false
        }
        if (isInsideDotGitFolder(fileUri)) {
            logger.info { "Skipping indexing of file ${fileUri.fileName()} because it is inside a .git folder" }
            return false
        }
        return true
    }

    private fun isGitScheme(gitUri: URI) = "git" == gitUri.scheme

    private fun isInsideDotGitFolder(fileUri: URI) = fileUri.path.contains("/.git/")

    fun removeFileUriResourceMap(fileUri: URI) {
        fileUriResourceMap.remove(fileUri)
        fileMap.remove(createSourcePath(fileUri))
    }

    private fun createSourcePath(fileUri: URI): C_SourcePath {
        return rellCompilerUtils.createCompilerSourcePath(fileUri, workspaceUri)
    }

    fun findAffectedFiles(fileUri: URI): Set<URI> {
        val changedFileResource: Resource = fileUriResourceMap[fileUri] ?: return emptySet()
        val shallowCopy = fileUriResourceMap.toMutableMap()
        shallowCopy.remove(fileUri)
        val filesToUpdate: MutableSet<URI> = mutableSetOf(fileUri)

        val implicitImports = calculateImplicitImports(shallowCopy)

        for ((key, value) in shallowCopy) {
            if (value.imports.contains(changedFileResource.rName) ||
                implicitImports[value.rName]?.contains(changedFileResource.rName) == true ||
                value.rName == changedFileResource.rName
            ) {
                filesToUpdate.add(key)
            }
        }
        return filesToUpdate.toSet()
    }

    private fun calculateImplicitImports(resourceMap: Map<URI, Resource>): Map<ModuleName, Collection<ModuleName>> {
        val implicitImports: MutableMap<ModuleName, Collection<ModuleName>> = mutableMapOf()

        for ((fileUri, value) in resourceMap) {
            val moduleName = value.rName
            if (fileUri.toString().endsWith("/module.rell") && moduleName != null) {
                implicitImports[moduleName] = value.imports
            }
        }

        return implicitImports
    }

    private fun addRellFilesUri(): List<URI> {
        return File(workspaceUri).walkTopDown()
            .onEnter { !isExcludedDir(it) }
            // Extension is a pure string check; testing it before isFile avoids a stat() per
            // non-Rell file, which dominates the walk in workspaces with node_modules.
            .filter { it.extension == "rell" && it.isFile }
            .map { it.toURI() }
            .toList()
    }

    private fun isExcludedDir(dir: File): Boolean {
        if (dir.name == ".git") {
            return true
        }
        if (excludeFolders.isEmpty()) {
            return false
        }
        val path = dir.toPath()
        return excludeFolders.any { path.startsWith(it) }
    }

    fun getResource(uri: URI): Resource? {
        return fileUriResourceMap[uri]
    }

    fun getFileUrisWithPrefix(uri: URI): Collection<URI> {
        return fileUriResourceMap.keys.filter {
            it.toString().startsWith(uri.toString())
        }
    }

    internal fun runLinter() {
        for ((fileUri, resource) in fileUriResourceMap.entries) {
            val fileContent = File(fileUri).readText()
            runLinter(resource, fileContent)
        }
    }

    /**
     * Reloads the linter options from [configFile] and re-lints the workspace. Unlike
     * [updateConfig], this accepts a config living in a parent of the workspace root, matching
     * the lookup the initial options load uses.
     */
    fun reloadLinterConfig(configFile: File) {
        linterOptions.updateOptionsFromFile(configFile)
        runLinter()
    }

    private fun runLinter(resource: Resource, fileContent: String) {
        // Lint results are decoration: a linter/formatter crash on one file must not fail
        // indexing or the LSP request that triggered it.
        try {
            rellLinter.enhanceWithLintIssues(linterOptions, resource)
            formattingStyleLinter.enhanceWithFormatterIssues(linterOptions, formatterOptions, resource, fileContent)
        } catch (e: Exception) {
            logger.warn(e) { "Linting failed for ${resource.fileUri}" }
        }
    }

    fun isConfigFile(uri: URI) =
        isLinterConfig(uri) || isFormatterConfig(uri) || isChromiaModelFile(uri)

    private fun isLinterConfig(uri: URI): Boolean {
        return uri.toPath().fileName.toString() == LinterOptions.CONFIG_FILE_NAME && isInProjectRoot(uri)
    }

    private fun isFormatterConfig(uri: URI): Boolean {
        return uri.toPath().fileName.toString() in setOf(
            FormatterOptions.PREFERRED_RELL_FORMAT_FILE_NAME,
            FormatterOptions.DEPRECATED_RELL_FORMAT_FILE_NAME
        ) && isInProjectRoot(uri)
    }

    private fun isChromiaModelFile(uri: URI): Boolean =
        (chromiaConfigUri != null && uri.toPath() == chromiaConfigUri.toPath()) ||
            uri.toPath().fileName.toString() == ChromiaModelProvider.DEFAULT_CHROMIA_MODEL_FILENAME

    private fun isInProjectRoot(uri: URI): Boolean {
        val parent = uri.toPath().parent ?: return false
        // Accept every folder the options resolvers read configs from — the workspace root and up
        // to two parents (see RellLinterOptionsResolver) — plus the chromia project root, so a
        // config edit anywhere the config is honored also triggers a reload. Checking only the
        // project root silently dropped change events for the very file the options were loaded
        // from.
        val workspacePath = workspaceUri.toPath()
        val validRoots = listOfNotNull(
            workspacePath,
            workspacePath.parent,
            workspacePath.parent?.parent,
            projectRootUri?.toPath(),
        )
        return parent in validRoots
    }

    private companion object {
        /**
         * Deliberately a small share of the machine: the language server is a background process
         * next to the editor and a build, and indexing must not take the whole CPU.
         */
        private fun indexingParallelism(): Int = (Runtime.getRuntime().availableProcessors() / 4).coerceIn(1, 4)
    }

    private class IndexingThreadFactory : ThreadFactory {
        private val counter = AtomicInteger()

        override fun newThread(r: Runnable): Thread =
            Thread(r, "rell-indexer-${counter.incrementAndGet()}").apply { isDaemon = true }
    }
}
