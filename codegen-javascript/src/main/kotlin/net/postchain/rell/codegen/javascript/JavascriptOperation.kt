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

    override fun formatBody() = buildString {
        append("return { name: \"$mountName\"")
        if (params.isNotEmpty()) {
            append(", args: ${formatOperationParameters()}")
        }
        append(" };")
    }

    override fun formatReturnType() = "Operation"

    private fun formatOperationParameters(): String {
        if (params.isEmpty()) return ""
        return params.joinToString(",\n\t", "[", "]") {
                super.parameterTransformer(
                    it.name.str.snakeToLowerCamelCase(),
                    it.type
                )
            }
    }
}
