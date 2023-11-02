package net.postchain.rell.toolbox.core.indexer

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.net.URI

//1. onchange (absURI for editerad fil)
//2. vi parsar om editerad fil
//3. För alla resources som importerar editerad fil parsa om

class WorkspaceIndexer(private val workspaceURI: URI) {
    private val logger = KotlinLogging.logger {}
    private val resourceDescription = RellResourceDescription(workspaceURI)

    //TODO: Should we inject this?

    var fileUriResourceMap: HashMap<URI, Resource> = HashMap()
    fun initialFileIndexBuild() {
        val rellUris = addRellFilesUri()
        rellUris.forEach {
            fileUriResourceMap[it] = resourceDescription.buildRellResource(it)
        }
    }

    fun updateFileUriResourceMap(uri: URI) {
        fileUriResourceMap[uri] = resourceDescription.buildRellResource(uri)
    }

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
