package net.postchain.rell.toolbox.core.indexer

import io.github.oshai.kotlinlogging.KotlinLogging
import net.postchain.rell.base.compiler.base.utils.C_SourceFile
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.utils.ide.IdeSymbolKind
import net.postchain.rell.toolbox.core.parser.AntlrRellParser
import java.io.File
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

class WorkspaceIndexer(val workspaceUri: URI) {
    private val logger = KotlinLogging.logger {}
    private val resourceFactory = RellResourceFactory(workspaceUri, AntlrRellParser())
    var fileUriResourceMap = ConcurrentHashMap<URI, Resource>()
    var fileMap: MutableMap<C_SourcePath, C_SourceFile> = mutableMapOf()
    fun initialFileIndexBuild(cachedIndexer: WorkspaceIndexer? = null) {

        if (cachedIndexer != null) {
            fileMap = cachedIndexer.fileMap
        }

        val dirtyFiles: MutableList<URI> = mutableListOf()
        val rellUris = addRellFilesUri()
        rellUris.forEach { fileUri ->
            val fileContent = File(fileUri).readText()
            val checksum = calculateChecksum(fileContent)
            val cachedResource = cachedIndexer?.getResource(fileUri)

            val resource = if (cachedResource != null && cachedResource.checksum == checksum) {
                cachedResource
            } else {
                resourceFactory.buildRellResource(fileUri, fileContent, fileMap)
            }

            if (resource.imports.isNotEmpty() || resource.locationInfo.filter { it.value.ideSymbolInfo.kind == IdeSymbolKind.UNKNOWN}.isNotEmpty()) {
                dirtyFiles.add(fileUri)
            }
            fileUriResourceMap[fileUri] = resource
        }

        dirtyFiles.forEach { fileUri ->
            val checksum = calculateChecksum(fileUri)
            val cachedResource = cachedIndexer?.getResource(fileUri)
            val resource = if (cachedResource != null && cachedResource.checksum == checksum) {
                cachedResource
            } else {
                resourceFactory.buildRellResource(fileUri, fileMap)
            }
            fileUriResourceMap[fileUri] = resource
        }

    }

    fun hasFile(fileUri: URI): Boolean {
        return fileUriResourceMap.containsKey(fileUri)
    }

    fun getAllIssues(): Map<URI, List<RellIssue>> {
        val issues: MutableMap<URI, List<RellIssue>> = mutableMapOf()
        fileUriResourceMap.forEach { (uri, resource) ->
            issues[uri] = collectIssues(resource)
        }
        return issues
    }

    private fun collectIssues(resource: Resource): List<RellIssue> {
        return listOf(getSyntaxErrors(resource), getSemanticErrors(resource)).flatten()
    }

    private fun getSyntaxErrors(resource: Resource): List<RellIssue> {
        return resource.syntaxErrors.map(RellIssue::fromSyntaxError)
    }

    private fun getSemanticErrors(resource: Resource): List<RellIssue> {
        return resource.fileSpecificSemanticErrors.map(RellIssue::fromCMessage)
    }

    //Change in source code
    fun updateFileUriResourceMap(fileUri: URI) {
        fileUriResourceMap[fileUri] = resourceFactory.buildRellResource(fileUri, fileMap)
    }

    fun updateFileUriResourceMap(fileUri: URI, fileContent: String) {
        if (isGitScheme(fileUri)) {
            logger.info { "Skipping indexing of file $fileUri because it is a git file" }
            return
        }
        fileUriResourceMap[fileUri] = resourceFactory.buildRellResource(fileUri, fileContent, fileMap)
    }

    //Rename and Move file
    fun updateFileUriResourceMap(oldFileUri: URI, newFileUri: URI) {
        val resource = fileUriResourceMap[oldFileUri]
        if (resource != null) {
            fileUriResourceMap[newFileUri] = resource
            fileUriResourceMap.remove(oldFileUri)
        } else {
            logger.warn { "Could not find resource for $oldFileUri. Re-parsing file..." }
            updateFileUriResourceMap(newFileUri)
        }
    }

    private fun isGitScheme(gitUri: URI) = "git" == gitUri.scheme

    fun removeFileUriResourceMap(fileUri: URI) {
        fileUriResourceMap.remove(fileUri)
    }

    fun findAffectedFiles(fileUri: URI): Set<URI> {
        var shallowCopy = fileUriResourceMap.toMutableMap()
        val changedFileResource: Resource = fileUriResourceMap[fileUri]!!
        shallowCopy.remove(fileUri)
        var filesToUpdate: MutableSet<URI> = mutableSetOf(fileUri)

        val implicitImports = calculateImplicitImports(shallowCopy)

        shallowCopy.forEach { (key, value) ->
            if (value.imports.contains(changedFileResource.rName) ||
                implicitImports[value.rName]?.contains(changedFileResource.rName) == true
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

    fun addRellFilesUri(): List<URI> {
        val uris: MutableList<URI> = ArrayList()
        findRellFilesInWorkspace(File(workspaceUri), uris)
        return uris.toList()
    }

    fun getResource(uri: URI): Resource? {
        return fileUriResourceMap[uri]
    }
}
