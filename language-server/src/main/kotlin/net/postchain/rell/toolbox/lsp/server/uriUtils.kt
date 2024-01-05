package net.postchain.rell.toolbox.lsp.server

import java.net.URI
import java.net.URLDecoder

fun parseFileUri(fileUri: String): URI {
    var uri = URLDecoder.decode(fileUri, "UTF-8")
    uri = uri.replace("file:///", "file:/")
    return URI(uri)
}
