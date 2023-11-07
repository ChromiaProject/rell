package net.postchain.rell.toolbox.core.indexer

import io.github.oshai.kotlinlogging.KotlinLogging
import net.postchain.rell.base.compiler.base.utils.C_SourceFile
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import java.io.File
import java.net.URI

class WorkspaceIndexer(private val workspaceURI: URI) {
    private val logger = KotlinLogging.logger {}
    private val resourceFactory = RellResourceFactory(workspaceURI)

    var fileUriResourceMap: HashMap<URI, Resource> = HashMap()
    private var compilerResourceMap: MutableMap<C_SourcePath, C_SourceFile> = mutableMapOf()
    fun initialFileIndexBuild() {
        val dirtyFiles: MutableList<URI> = mutableListOf()
        val rellUris = addRellFilesUri()
        rellUris.forEach { fileURI ->
            val resource = resourceFactory.buildRellResource(fileURI, compilerResourceMap)

            if (resource.imports.isNotEmpty()) {
                dirtyFiles.add(fileURI)
            }
            fileUriResourceMap[fileURI] = resource
        }

        dirtyFiles.forEach { fileURI ->
            val resource = resourceFactory.buildRellResource(fileUriResourceMap[fileURI]!!, fileURI, compilerResourceMap)
            fileUriResourceMap[fileURI] = resource
        }

    }

    //Change in source code
    fun updateFileUriResourceMap(fileURI: URI) {
        fileUriResourceMap[fileURI] = resourceFactory.buildRellResource(fileURI, compilerResourceMap)
    }

    //Rename and Move file
    fun updateFileUriResourceMap(oldUri: URI, newUri: URI) {
        val resource = fileUriResourceMap[oldUri]
        if (resource != null) {
            fileUriResourceMap[newUri] = resource
            fileUriResourceMap.remove(oldUri)
        } else {
            logger.warn { "Could not find resource for $oldUri. Re-parsing file..." }
            updateFileUriResourceMap(newUri)
        }
    }

    fun findAffectedFiles(uri: URI): Set<URI> {
        //TODO make it so it can find affected files that are importing an affected file
        var shallowCopy = fileUriResourceMap.toMutableMap()
        val changedFileResource: Resource = fileUriResourceMap[uri]!!
        shallowCopy.remove(uri)
        var filesToUpdate: MutableSet<URI> = mutableSetOf(uri)

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
        findRellFilesInWorkspace(File(workspaceURI), uris)
        return uris.toList()
    }
}
