package net.postchain.rell.toolbox.indexer

import com.chromia.cli.model.ChromiaModel
import io.github.oshai.kotlinlogging.KotlinLogging
import net.postchain.rell.base.compiler.base.utils.C_SourceFile
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.utils.ide.IdeSymbolKind
import net.postchain.rell.toolbox.chromia.ChromiaModelProvider
import net.postchain.rell.toolbox.formatter.FormatterOptions
import net.postchain.rell.toolbox.linter.AbstractFormattingStyleLinter
import net.postchain.rell.toolbox.linter.AbstractRellLinter
import net.postchain.rell.toolbox.linter.LinterOptions
import net.postchain.rell.toolbox.parser.AntlrRellParser
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.toPath

class WorkspaceIndexer(
    val workspaceUri: URI,
    private val rellLinter: AbstractRellLinter,
    private val linterOptions: LinterOptions,
    private val formattingStyleLinter: AbstractFormattingStyleLinter,
    private val formatterOptions: FormatterOptions,
    val projectRootUri: URI? = null,
    val excludeFolders: Set<Path> = emptySet()
) {
    private val logger = KotlinLogging.logger {}
    private val chromiaModelProvider = ChromiaModelProvider(projectRootUri)
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
        fileMap = resourceFactory.buildFileMap(sources)

        val alreadyLintedFiles = useDataFromCachedIndexer(cachedIndexer, sources)
        val dirtyFiles = mutableListOf<URI>()

        for (source in sources) {
            val (fileUri, fileContent) = source
            if (!isValidFileUri(fileUri)) {
                continue
            }
            if (fileUriResourceMap.containsKey(fileUri) && !reindex) {
                continue
            }
            val resource = resourceFactory.buildRellResource(fileUri, fileContent, fileMap)
            if (resource.imports.isNotEmpty() || hasImplicitImports(resource)) {
                dirtyFiles.add(fileUri)
            }
            fileUriResourceMap[fileUri] = resource
        }

        for (fileUri in dirtyFiles) {
            val fileContent = sources[fileUri] ?: continue
            fileUriResourceMap[fileUri] = resourceFactory.buildRellResource(fileUri, fileContent, fileMap)
        }

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

    private fun ideConfigOptionsMatch(cachedIndexer: WorkspaceIndexer? = null): Boolean {
        return linterOptions == cachedIndexer?.linterOptions && formatterOptions == cachedIndexer.formatterOptions
    }

    private fun hasImplicitImports(resource: Resource) =
        resource.locationInfo.filter { it.value.ideSymbolInfo.kind == IdeSymbolKind.UNKNOWN }.isNotEmpty()

    private fun useDataFromCachedIndexer(cachedIndexer: WorkspaceIndexer?, sources: Map<URI, String>): Set<URI> {
        if (cachedIndexer == null) return setOf()
        val linterOptionsMatch = ideConfigOptionsMatch(cachedIndexer)
        val alreadyLintedFiles = mutableSetOf<URI>()

        sources.forEach { (fileUri, fileContent) ->
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
            } catch (@Suppress("SwallowedException") e: Exception) {
                logger.warn { "Could not read file $fileUri" }
                continue
            }
            sources[fileUri] = fileContent
        }
        return sources
    }

    fun getAllIssues(): Map<URI, List<RellIssue>> {
        val issues: MutableMap<URI, List<RellIssue>> = mutableMapOf()
        fileUriResourceMap.forEach { (uri, resource) ->
            issues[uri] = collectIssues(resource)
        }
        return issues
    }

    fun getAllLintAndFormatIssues(): Map<URI, List<RellIssue>> {
        val issues: MutableMap<URI, List<RellIssue>> = mutableMapOf()
        fileUriResourceMap.forEach { (uri, resource) ->
            issues[uri] = getLinterIssues(resource) + getFormatterIssues(resource)
        }
        return issues
    }

    private fun collectIssues(resource: Resource): List<RellIssue> {
        return listOf(
            getSyntaxErrors(resource),
            getSemanticErrors(resource),
            getLinterIssues(resource),
            getFormatterIssues(resource)
        ).flatten()
    }

    private fun getSyntaxErrors(resource: Resource): List<RellIssue> {
        return resource.syntaxErrors.map(RellIssue::fromSyntaxError)
    }

    private fun getSemanticErrors(resource: Resource): List<RellIssue> {
        return resource.fileSpecificSemanticErrors.map(RellIssue::fromCMessage)
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
            logger.info { "Skipping indexing of file $fileUri because it is a git file" }
            return false
        }
        if (isInsideDotGitFolder(fileUri)) {
            logger.info { "Skipping indexing of file $fileUri because it is inside a .git folder" }
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

        shallowCopy.forEach { (key, value) ->
            if (value.imports.contains(changedFileResource.rName) ||
                implicitImports[value.rName]?.contains(changedFileResource.rName) == true ||
                value.rName == changedFileResource.rName
            ) {
                filesToUpdate.add(key)
            }
        }
        return filesToUpdate.toSet()
    }

    private fun calculateImplicitImports(resourceMap: Map<URI, Resource>): Map<R_ModuleName, Collection<R_ModuleName>> {
        val implicitImports: MutableMap<R_ModuleName, Collection<R_ModuleName>> = mutableMapOf()
        resourceMap.forEach { (fileUri, value) ->
            val moduleName = value.rName
            if (fileUri.toString().endsWith("/module.rell") && moduleName != null) {
                implicitImports[moduleName] = value.imports
            }
        }
        return implicitImports
    }

    private fun addRellFilesUri(): List<URI> {
        return File(workspaceUri).walkTopDown().filter {
            it.isFile && it.extension == "rell" &&
                excludeFolders.none { excludeFolder -> it.toPath().startsWith(excludeFolder) }
        }.map { it.toURI() }.toList()
    }

    fun getResource(uri: URI): Resource? {
        return fileUriResourceMap[uri]
    }

    fun getFileUrisWithPrefix(uri: URI): Collection<URI> {
        return fileUriResourceMap.keys.filter {
            it.toString().startsWith(uri.toString())
        }
    }

    fun runLinter() {
        fileUriResourceMap.entries.forEach { (fileUri, resource) ->
            val fileContent = File(fileUri).readText()
            runLinter(resource, fileContent)
        }
    }

    private fun runLinter(resource: Resource, fileContent: String) {
        rellLinter.enhanceWithLintIssues(linterOptions, resource)
        formattingStyleLinter.enhanceWithFormatterIssues(linterOptions, formatterOptions, resource, fileContent)
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

    private fun isChromiaModelFile(uri: URI): Boolean {
        return uri.toPath().fileName.toString() == ChromiaModelProvider.DEFAULT_CHROMIA_MODEL_FILENAME
    }

    private fun isInProjectRoot(uri: URI): Boolean {
        val parent = File(uri).parentFile ?: return false
        return parent.toPath() == projectRootUri?.toPath()
    }
}
