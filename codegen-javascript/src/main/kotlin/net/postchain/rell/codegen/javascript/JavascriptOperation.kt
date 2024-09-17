package net.postchain.rell.codegen.javascript

import net.postchain.rell.base.model.R_OperationDefinition
import net.postchain.rell.codegen.deps.CamelCaseClassName
import net.postchain.rell.codegen.section.Operation
import net.postchain.rell.codegen.util.snakeToLowerCamelCase

class JavascriptOperation(op: R_OperationDefinition) : JavascriptFunction(
        CamelCaseClassName.fromRellOperation(op),
        op.mountName,
        op.params(),
        op.docSymbol,
        false,
), Operation {
    override val imports = listOf("")

    override fun formatBody() = "tx.addOperation(\"$mountName\"${formatOperationParameters()})"
    override fun formatReturnType() = "Operation"

    override fun formatInputParameters() = "tx${super.formatInputParameters().let { if (it.isNotBlank()) ",\n\t$it" else "" }}"

    private fun formatOperationParameters(): String {
        if (params.isEmpty()) return ""
        return ", ${params.joinToString(",\n\t") { super.parameterTransformer(it.name.str.snakeToLowerCamelCase(), it.type) }}"
    }
}
