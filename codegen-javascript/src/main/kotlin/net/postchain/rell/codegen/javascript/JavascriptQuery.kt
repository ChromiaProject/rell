package net.postchain.rell.codegen.javascript

import net.postchain.rell.codegen.deps.CamelCaseClassName
import net.postchain.rell.codegen.section.Query
import net.postchain.rell.codegen.util.snakeToLowerCamelCase
import net.postchain.rell.model.R_QueryDefinition

class JavascriptQuery(queryDef: R_QueryDefinition) : JavascriptFunction(
        CamelCaseClassName.fromRellQuery(queryDef),
        queryDef.mountName,
        queryDef.params(),
        true,
), Query {
    override val imports = listOf("")
    override val moduleName: String
        get() = className.module

    override fun formatBody() = "return await gtxClient.query(\"$mountName\"${formatQueryParameters()})"

    override fun formatInputParameters() = "gtxClient${super.formatInputParameters().let { if (it.isNotBlank()) ",\n\t$it" else "" }}"

    private fun formatQueryParameters(): String {
        if (params.isEmpty()) return ""
        return ", {" + params.joinToString(",\n\t") { "${it.name.str}: ${super.parameterTransformer(it.name.str.snakeToLowerCamelCase(), it.type)}" } + "}"
    }
}
