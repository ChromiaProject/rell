package net.postchain.rell.toolbox.core.indexer

import io.github.oshai.kotlinlogging.KotlinLogging
import net.postchain.rell.toolbox.core.parser.AntlrRellParser
import net.postchain.rell.toolbox.core.parser.RellParser
import java.io.File
import java.net.URI

//1. onchange (absURI for editerad fil)
//2. vi parsar om editerad fil
//3. För alla resources som importerar editerad fil parsa om


//data class WorkspaceIndex(val uri: URI, val resource: Resource)
data class Resource(val absoluteURI: URI) {
    private val parser = AntlrRellParser()
    val parseTree: RellParser.RuleX_RootParserContext = parser.parse(File(absoluteURI).readText())
}

class WorkspaceIndexer(private val workspaceURI: URI) {
    private val logger = KotlinLogging.logger {}

    //TODO: Should we inject this?

    var fileUriResourceMap: HashMap<URI, Resource> = HashMap()
    fun initialFileIndexBuild() {
        val rellUris = addRellFilesUri()
        rellUris.forEach { uri -> updateFileUriResourceMap(uri) }
    }

    fun updateFileUriResourceMap(uri: URI) {
        fileUriResourceMap[uri] = Resource(uri)
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

    fun addRellFilesUri(): List<URI> {
        val uris: MutableList<URI> = ArrayList()
        addRellFilesUri(File(workspaceURI), uris)
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
