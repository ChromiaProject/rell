package net.postchain.rell.toolbox.lsp.includeDefinition

interface LspSystemPropertiesProvider {
    fun getIncludeDefinition(): Boolean
    fun getIssueCaching(): Boolean
}

class DefaultLspSystemPropertiesProvider : LspSystemPropertiesProvider {
    private val includeDefinition: Boolean =
        System.getProperty("LspIncludeDefinition", "true").toBoolean()
    private val issueCaching: Boolean =
        System.getProperty("LspIssueCaching", "true").toBoolean()

    override fun getIncludeDefinition() = includeDefinition
    override fun getIssueCaching() = issueCaching
}
