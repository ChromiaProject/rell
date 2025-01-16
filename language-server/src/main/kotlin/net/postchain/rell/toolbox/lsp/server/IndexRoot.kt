package net.postchain.rell.toolbox.lsp.server

import java.net.URI
import java.nio.file.Path

class IndexRoot(val chromiaConfigPath: Path, val sourceRootPath: Path) {
    val sourceRootUri: URI by lazy {
        parseFileUri(sourceRootPath.toUri().toString()) ?: error("Failed to parse source path URI")
    }
    val chromiaConfigDirUri: URI by lazy {
        parseFileUri(chromiaConfigPath.parent.toUri().toString()) ?: error("Failed to parse chromia model parent URI")
    }
}
