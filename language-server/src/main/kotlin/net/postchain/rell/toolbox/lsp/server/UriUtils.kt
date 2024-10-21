package net.postchain.rell.toolbox.lsp.server

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
