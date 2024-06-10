package net.postchain.rell.toolbox.lsp.server

import java.net.URI

fun parseFileUri(fileUri: String): URI? {
    if (!fileUri.startsWith("file:/")) {
        return null
    }
    val uri = fileUri.replace("file:///", "file:/")
    return URI(uri)
}

fun URI.isRellFile(): Boolean {
    return this.toString().endsWith(".rell")
}