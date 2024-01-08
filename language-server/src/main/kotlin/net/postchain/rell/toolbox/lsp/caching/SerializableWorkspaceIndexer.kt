package net.postchain.rell.toolbox.lsp.caching

import net.postchain.rell.base.compiler.base.utils.C_SourceFile
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

class SerializableWorkspaceIndexer(
    val workspaceUri: URI,
    val serializableResources: List<SerializableResource>,
    val fileMap: ConcurrentHashMap<C_SourcePath, C_SourceFile>
)
