package net.postchain.rell.toolbox.lsp.server.utils

import net.postchain.rell.toolbox.lsp.includeDefinition.LspSystemPropertiesProvider

class TestLspSystemPropertiesProvider(
    private val includeDefinition: Boolean,
    private val issueCaching: Boolean,
) : LspSystemPropertiesProvider {

    override fun getIncludeDefinition() = includeDefinition
    override fun getIssueCaching() = issueCaching
}
