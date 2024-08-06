package net.postchain.rell.toolbox.lsp.caching

import java.net.URI
import net.postchain.rell.toolbox.formatter.FormatterOptions
import net.postchain.rell.toolbox.linter.LinterOptions

class SerializableWorkspaceIndexer(
    val workspaceUri: URI,
    val serializableResources: List<SerializableResource>,
    val linterOptions: LinterOptions,
    val formatterOptions: FormatterOptions,
    val projectRootUri: URI? = null
)
