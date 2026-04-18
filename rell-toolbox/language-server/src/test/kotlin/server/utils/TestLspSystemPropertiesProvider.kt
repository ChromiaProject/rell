/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.server.utils

import net.postchain.rell.toolbox.lsp.includeDefinition.LspSystemPropertiesProvider

class TestLspSystemPropertiesProvider(
    private val includeDefinition: Boolean,
    private val issueCaching: Boolean,
    private val resolveCompletion: Boolean,
) : LspSystemPropertiesProvider {

    override fun getIncludeDefinition() = includeDefinition
    override fun getIssueCaching() = issueCaching
    override fun getResolveCompletion() = resolveCompletion
}
