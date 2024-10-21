package net.postchain.rell.toolbox.lsp.includeDefinition

interface LspIncludeDefinitionProvider {
    fun getIncludeDefinition(): Boolean
}

class DefaultLspIncludeDefinitionProvider : LspIncludeDefinitionProvider {
    private val includeDefinition: Boolean =
        System.getProperty("LspIncludeDefinition", "true").toBoolean()

    override fun getIncludeDefinition(): Boolean {
        return includeDefinition
    }
}
