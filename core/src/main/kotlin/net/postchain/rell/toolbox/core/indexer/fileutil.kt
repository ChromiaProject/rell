package net.postchain.rell.toolbox.core.indexer

import java.io.File
import java.net.URI

fun findRellFilesInWorkspace(file: File, uris: MutableList<URI>) {

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