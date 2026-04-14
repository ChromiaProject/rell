/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.server

import net.postchain.rell.toolbox.chromia.ChromiaModelProvider
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

fun parseFileUri(fileUri: String): URI? {
    if (!fileUri.startsWith("file:/")) {
        return null
    }
    val decodedUri = URLDecoder.decode(fileUri, StandardCharsets.UTF_8)
    val formattedUri = decodedUri.replace("file:///", "file:/")
        .replace(" ", "%20")
    return URI(formattedUri)
}

fun URI.isRellFile(): Boolean {
    return this.toString().endsWith(".rell")
}

fun URI.isChromiaConfig(): Boolean {
    return this.toString().endsWith(ChromiaModelProvider.DEFAULT_CHROMIA_MODEL_FILENAME)
}

const val DEPENDENCY_MARKER_FILE = ".deps"

fun URI.isDependencyMarkerFile(): Boolean {
    return this.toString().endsWith(DEPENDENCY_MARKER_FILE)
}

fun URI.startsWith(other: URI?): Boolean {
    return if (other == null) {
        false
    } else {
        ensureSuffix(this.path).startsWith(other.path)
    }
}


private fun ensureSuffix(path: String): String {
    return if (path.endsWith("/")) path else "$path/"
}