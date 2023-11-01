package net.postchain.rell.toolbox.core.indexer

import net.postchain.rell.toolbox.core.parser.AntlrRellParser
import java.io.File
import java.net.URI

class RellResourceDescription {
    val parser = AntlrRellParser()
    fun buildRellResource(workspaceURI: URI, uri: URI): Resource {
        //TODO verfiy correct path behaviour
        val parseTree = parser.parse(File(uri).readText())
        return Resource(uri, workspaceURI, parseTree)
            //fileUriModuleInfoMap[uri] = placeholder(moduleInfo!!, relativePath, uri.toString())
    }
}