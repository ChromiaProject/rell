package net.postchain.rell.codegen.javascript

import net.postchain.rell.codegen.deps.CamelCaseClassName
import net.postchain.rell.codegen.util.snakeToLowerCamelCase
import net.postchain.rell.model.R_OperationDefinition

class JavascriptOperation(op: R_OperationDefinition) : JavascriptFunction(
        CamelCaseClassName.fromRellOperation(op),
        op.mountName,
        op.params(),
        false,
) {
    override val imports = listOf("")

    override fun formatBody() = "tx.addOperation(\"$mountName\"${formatOperationParameters()})"

    override fun formatInputParameters() = "tx${super.formatInputParameters().let { if (it.isNotBlank()) ",\n\t$it" else "" }}"

    private fun formatOperationParameters(): String {
        if (params.isEmpty()) return ""
        return ", ${params.joinToString(",\n\t") { super.parameterTransformer(it.name.str.snakeToLowerCamelCase(), it.type) }}"
    }
}
