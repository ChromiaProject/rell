package net.postchain.rell.toolbox.core.indexer

import io.github.oshai.kotlinlogging.KotlinLogging
import net.postchain.rell.toolbox.core.parser.AntlrRellParser
import net.postchain.rell.toolbox.core.parser.RellParser
import java.io.File
import java.net.URI

//data class WorkspaceIndex(val uri: URI, val resource: Resource)
data class Resource(val parseTree: RellParser.RuleX_RootParserContext)

class WorkspaceIndexer() {
    private val logger = KotlinLogging.logger {}

    //TODO: Should we inject this?
    private val parser = AntlrRellParser()
    var fileUriResourceMap: HashMap<URI, Resource> = HashMap()
    fun initialFileIndexBuild(rootURI: URI) {
        val rellUris = addRellFilesUri(rootURI)
        rellUris.forEach { uri -> updateFileUriResourceMap(uri) }
    }

    fun updateFileUriResourceMap(uri: URI) {
        fileUriResourceMap[uri] = Resource(parser.parse(File(uri).readText()))
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

    fun addRellFilesUri(uri: URI): List<URI> {
        val uris: MutableList<URI> = ArrayList()
        addRellFilesUri(File(uri), uris)
        return uris.toList()
    }

    private fun addRellFilesUri(file: File, uris: MutableList<URI>) {
        //TODO: Verify path. From xtext impl:
        // "we need to convert the given file to a decoded emf file uri
        // e.g. file:///Users/x/y/z
        // or file:///C:/x/y/z"

        if (file.isDirectory()) {
            val files = file.listFiles()
            if (files != null) {
                for (f in files) {
                    addRellFilesUri(f, uris)
                }
            }
        } else {
            if (file.extension == "rell") {
                uris.add(file.toURI())
            }
        }
    }
}
