package net.postchain.rell.toolbox.lsp.server.utils

import net.postchain.rell.toolbox.lsp.includeDefinition.LspIncludeDefinitionProvider

class TestLspIncludeDefinitionProvider(private val includeDefinition: Boolean) : LspIncludeDefinitionProvider {
    override fun getIncludeDefinition(): Boolean {
        return includeDefinition
    }
}
