/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.caching

import net.postchain.rell.toolbox.formatter.FormatterOptions
import net.postchain.rell.toolbox.linter.LinterOptions
import java.net.URI

internal class SerializableWorkspaceIndexer(
    val workspaceUri: URI,
    val serializableResources: List<SerializableResource>,
    val linterOptions: LinterOptions,
    val formatterOptions: FormatterOptions,
    val projectRootUri: URI? = null,
    val metaData: SerializableMetaData? = null,
)

internal class SerializableMetaData(
    val languageServerVersion: String,
    /** The `compile.rellVersion` the cached resources were compiled with. */
    val rellCompatibilityVersion: String,
)
