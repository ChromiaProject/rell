package net.postchain.rell.toolbox.lsp.server

import net.postchain.rell.toolbox.indexer.WorkspaceIndexer
import java.net.URI
import java.nio.file.Path

interface IndexerRegistry {
    fun getAllIndexersMap(): MutableMap<URI, WorkspaceIndexer>
    fun getIndexerFor(fileUri: URI): WorkspaceIndexer
    fun getIndexerForOrNull(fileUri: URI): WorkspaceIndexer?
    fun getIndexerForFolderOrNull(fileUri: URI): WorkspaceIndexer?
    fun doIndex(
        resolvedSourceDirUri: URI,
        workspaceFolderUri: URI,
        excludeFolders: Set<Path> = emptySet(),
        skipCache: Boolean = false
    ): WorkspaceIndexer
    fun cleanUpOrphans(indexers: Map<URI, WorkspaceIndexer>)
}
