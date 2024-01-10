package net.postchain.rell.toolbox.lsp.caching

import java.net.URI

class SerializableWorkspaceIndexer(
    val workspaceUri: URI,
    val serializableResources: List<SerializableResource>
)
