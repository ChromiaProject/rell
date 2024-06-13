package net.postchain.rell.toolbox.lsp.server

import java.net.URI

fun parseFileUri(fileUri: String): URI? {
    if (!fileUri.startsWith("file:/")) {
        return null
    }
    return URI(fileUri.replace("file:///", "file:/"))
}

fun URI.isRellFile(): Boolean {
    return this.toString().endsWith(".rell")
}
