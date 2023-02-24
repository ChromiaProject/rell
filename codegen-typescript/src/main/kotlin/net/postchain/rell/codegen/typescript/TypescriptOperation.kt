package net.postchain.rell.codegen.typescript

import net.postchain.rell.codegen.deps.CamelCaseClassName
import net.postchain.rell.codegen.section.Operation
import net.postchain.rell.codegen.typescript.util.parameterTransformer
import net.postchain.rell.codegen.util.snakeToLowerCamelCase
import net.postchain.rell.model.R_OperationDefinition
import net.postchain.rell.model.R_Type

class TypescriptOperation(op: R_OperationDefinition) : TypescriptFunction(
        CamelCaseClassName.fromRellOperation(op),
        op.mountName,
        op.params(),
        false,
        null
), Operation {

    override fun formatBody() = "tx.addOperation(\"$mountName\"${formatOperationParameters()})"

    private fun formatOperationParameters(): String {
        if (params.isEmpty()) return ""
        return ", ${params.joinToString(",\n\t") { parameterTransformer(it.name.str.snakeToLowerCamelCase(), it.type) }}"
    }

    override fun formatInputParameters() = "tx: Itransaction${super.formatInputParameters().let { if (it.isNotBlank()) ",\n\t$it" else "" }}"

    override fun formatReturnType(): String = "void"

    override fun returnStructure(returnType: R_Type?): String = ""
}
