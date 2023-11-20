package net.postchain.rell.toolbox.core.indexer

import io.github.oshai.kotlinlogging.KotlinLogging
import net.postchain.rell.toolbox.core.parser.AntlrRellParser
import java.io.File
import java.net.URI

class WorkspaceIndexer(val workspaceUri: URI) {
    private val logger = KotlinLogging.logger {}
    private val resourceFactory = RellResourceFactory(workspaceUri, AntlrRellParser())
    var fileUriResourceMap = mutableMapOf<URI, Resource>()
    fun initialFileIndexBuild() {
        val dirtyFiles: MutableList<URI> = mutableListOf()
        val rellUris = addRellFilesUri()
        rellUris.forEach { fileUri ->
            val resource = resourceFactory.buildRellResource(fileUri)

            if (resource.imports.isNotEmpty()) {
                dirtyFiles.add(fileUri)
            }
            fileUriResourceMap[fileUri] = resource
        }

        dirtyFiles.forEach { fileUri ->
            val resource =
                resourceFactory.buildRellResource(fileUriResourceMap[fileUri]!!, fileUri)
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
        fileUriResourceMap[fileUri] = resourceFactory.buildRellResource(fileUri)
    }

    fun updateFileUriResourceMap(fileUri: URI, fileContent: String) {
        if (isGitScheme(fileUri)) {
            logger.info { "Skipping indexing of file $fileUri because it is a git file" }
            return
        }
        fileUriResourceMap[fileUri] = resourceFactory.buildRellResource(fileUri, fileContent)
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
        //TODO make it so it can find affected files that are importing an affected file
        var shallowCopy = fileUriResourceMap.toMutableMap()
        val changedFileResource: Resource = fileUriResourceMap[fileUri]!!
        shallowCopy.remove(fileUri)
        var filesToUpdate: MutableSet<URI> = mutableSetOf(fileUri)

        shallowCopy.forEach { (key, value) ->
            if (value.imports.isNullOrEmpty()) {
                //
            } else {
                if (value.imports.contains(changedFileResource.rName)) {
                    filesToUpdate.add(key)
                }
            }
        }
        return filesToUpdate.toSet()

    }

    fun addRellFilesUri(): List<URI> {
        val uris: MutableList<URI> = ArrayList()
        findRellFilesInWorkspace(File(workspaceUri), uris)
        return uris.toList()
    }
}
