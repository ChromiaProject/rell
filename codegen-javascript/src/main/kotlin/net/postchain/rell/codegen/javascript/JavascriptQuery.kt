package net.postchain.rell.codegen.javascript

import net.postchain.rell.base.model.R_QueryDefinition
import net.postchain.rell.codegen.deps.CamelCaseClassName
import net.postchain.rell.codegen.section.Query
import net.postchain.rell.codegen.util.snakeToLowerCamelCase

class JavascriptQuery(queryDef: R_QueryDefinition) : JavascriptFunction(
        CamelCaseClassName.fromRellQuery(queryDef),
        queryDef.mountName,
        queryDef.params(),
        queryDef.docSymbol,
        false,
    "QueryObject"
), Query {
    override val imports = listOf("")
    override val moduleName: String
        get() = className.module

    override fun formatBody() = "return { name: \"$mountName\", args: ${formatQueryParameters()} };"
    override fun formatReturnType() = "QueryObject"

    private fun formatQueryParameters(): String {
        if (params.isEmpty()) return "undefined"
        return params.joinToString(",\n\t") {  it.name.str.snakeToLowerCamelCase() }
    }
}