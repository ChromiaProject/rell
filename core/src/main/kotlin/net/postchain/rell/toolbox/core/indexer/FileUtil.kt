package net.postchain.rell.toolbox.core.indexer

import java.io.File
import java.net.URI

 fun findRellFilesInWorkspace(file: File, uris: MutableList<URI>) {
        //TODO: Verify path. From xtext impl:
        // "we need to convert the given file to a decoded emf file uri
        // e.g. file:///Users/x/y/z
        // or file:///C:/x/y/z"

        if (file.isDirectory()) {
            val files = file.listFiles()
            if (files != null) {
                for (f in files) {
                    findRellFilesInWorkspace(f, uris)
                }
            }
        } else {
            if (file.extension == "rell") {
                uris.add(file.toURI())
            }
        }
 }

fun findRellFilesInWorkspace(file: File, uris: MutableMap<String, URI>) {
    if (file.isDirectory()) {
        val files = file.listFiles()
        if (files != null) {
            for (f in files) {
                findRellFilesInWorkspace(f, uris)
            }
        }
    } else {
        if (file.extension == "rell") {
            uris[file.parent + "/" + file.name] = file.toURI()
        }
    }
}