package net.postchain.rell.toolbox.core.indexer

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.toPath
import net.postchain.rell.base.compiler.base.utils.C_SourceFile
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.utils.ide.IdeSymbolKind
import net.postchain.rell.toolbox.core.parser.AntlrRellParser
import net.postchain.rell.toolbox.formatter.FormatterOptions
import net.postchain.rell.toolbox.linter.FormattingStyleLinter
import net.postchain.rell.toolbox.linter.LinterOptions
import net.postchain.rell.toolbox.linter.RellLinter

class WorkspaceIndexer(
    val workspaceUri: URI,
    private val rellLinter: RellLinter,
    private val linterOptions: LinterOptions,
    private val formattingStyleLinter: FormattingStyleLinter,
    private val formatterOptions: FormatterOptions,
) {
    private val logger = KotlinLogging.logger {}
    private val resourceFactory = RellResourceFactory(workspaceUri, AntlrRellParser())
    private val rellCompilerUtils = RellCompilerUtils()
    var fileUriResourceMap = ConcurrentHashMap<URI, Resource>()
    private var fileMap: ConcurrentHashMap<C_SourcePath, C_SourceFile> = ConcurrentHashMap()

    fun updateConfig(fileUri: URI) {
        val configFile = File(fileUri)
        if (isLinterConfig(fileUri)) {
            linterOptions.updateOptionsFromFile(configFile)
        }
        if (isFormatterConfig(fileUri)) {
            formatterOptions.updateOptionsFromFile(configFile)
        }
    }

    fun initialFileIndexBuild(cachedIndexer: WorkspaceIndexer? = null) {
        val rellUris = addRellFilesUri()
        val sources = readAllSource(rellUris)
        fileMap = resourceFactory.buildFileMap(sources)

        val alreadyLintedFiles = useDataFromCachedIndexer(cachedIndexer, sources)
        val dirtyFiles = mutableListOf<URI>()

        for (source in sources) {
            val (fileUri, fileContent) = source
            if (fileUriResourceMap.containsKey(fileUri)) {
                continue
            }
            val resource = resourceFactory.buildRellResource(fileUri, fileContent, fileMap)
            if (resource.imports.isNotEmpty() || hasImplicitImports(resource)) {
                dirtyFiles.add(fileUri)
            }
            fileUriResourceMap[fileUri] = resource
        }

        for (fileUri in dirtyFiles) {
            fileUriResourceMap[fileUri] = resourceFactory.buildRellResource(fileUri, fileMap)
        }

        for (source in sources) {
            val (fileUri, fileContent) = source
            if (alreadyLintedFiles.contains(fileUri)) {
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
            } catch (e: IOException) {
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
        return resource.linterIssues.map(RellIssue::fromLinterIssue)
    }

    private fun getFormatterIssues(resource: Resource): List<RellIssue> {
        return resource.formatterIssues.map(RellIssue::fromFormatterIssue)
    }

    //Change in source code
    fun updateFileUriResourceMap(fileUri: URI) {
        if (!File(fileUri).exists()) {
            removeFileUriResourceMap(fileUri)
        } else {
            val fileContent = File(fileUri).readText()
            val resource = resourceFactory.buildRellResource(fileUri, fileContent, fileMap)

            runLinter(resource, fileContent)

            fileUriResourceMap[fileUri] = resource
            resourceFactory.updateFileMap(fileMap, fileUri)
        }
    }

    fun updateFileUriResourceMap(fileUri: URI, fileContent: String) {
        if (isGitScheme(fileUri)) {
            logger.info { "Skipping indexing of file $fileUri because it is a git file" }
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

    //Rename and Move file
    fun updateFileUriResourceMap(oldFileUri: URI, newFileUri: URI) {
        val resource = fileUriResourceMap[oldFileUri]
        if (resource != null) {
            fileUriResourceMap[newFileUri] = resource
            removeFileUriResourceMap(oldFileUri)
        } else {
            logger.warn { "Could not find resource for $oldFileUri. Re-parsing file..." }
            updateFileUriResourceMap(newFileUri)
        }
    }

    private fun isGitScheme(gitUri: URI) = "git" == gitUri.scheme

    fun removeFileUriResourceMap(fileUri: URI) {
        fileUriResourceMap.remove(fileUri)
        fileMap.remove(createSourcePath(fileUri))
    }

    private fun createSourcePath(fileUri: URI): C_SourcePath {
        return rellCompilerUtils.createCompilerSourcePath(fileUri, workspaceUri)
    }

    fun findAffectedFiles(fileUri: URI): Set<URI> {
        val shallowCopy = fileUriResourceMap.toMutableMap()
        val changedFileResource: Resource = fileUriResourceMap[fileUri]!!
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
        resourceMap.forEach { (key, value) ->
            if (key.toString().endsWith("/module.rell") && value.rName != null) {
                implicitImports[value.rName] = value.imports
            }
        }
        return implicitImports
    }

    private fun addRellFilesUri(): List<URI> {
        val uris: MutableList<URI> = ArrayList()
        findRellFilesInWorkspace(File(workspaceUri), uris)
        return uris.toList()
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

    fun isLinterOrFormatterConfigFile(uri: URI) = isLinterConfig(uri) || isFormatterConfig(uri)
    private fun isLinterConfig(uri: URI) = uri.toPath().fileName.toString() == LinterOptions.CONFIG_FILE_NAME
    private fun isFormatterConfig(uri: URI) = uri.toPath().fileName.toString() in setOf(
        FormatterOptions.PREFERRED_RELL_FORMAT_FILE_NAME,
        FormatterOptions.DEPRECATED_RELL_FORMAT_FILE_NAME
    )
}
