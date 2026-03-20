package net.postchain.rell.toolbox.lsp.inlayhints

data class RellInlayHintsConfig(
    val isVariableTypesEnabled: Boolean = false,
    val isReturnTypesEnabled: Boolean = false,
    val isParameterNamesEnabled: Boolean = false
)
