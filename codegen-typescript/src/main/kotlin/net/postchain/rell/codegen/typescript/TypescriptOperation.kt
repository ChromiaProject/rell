package net.postchain.rell.codegen.typescript

import net.postchain.rell.base.model.R_OperationDefinition
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.codegen.deps.CamelCaseClassName
import net.postchain.rell.codegen.section.Operation
import net.postchain.rell.codegen.typescript.util.parameterTransformer
import net.postchain.rell.codegen.util.snakeToLowerCamelCase

class TypescriptOperation(op: R_OperationDefinition) : TypescriptFunction(
        CamelCaseClassName.fromRellOperation(op),
        op.mountName,
        op.params(),
        false,
        null
), Operation {
    override val imports: List<String> = listOf("import { Itransaction } from \"postchain-client\";")

    override fun formatBody() = "tx.addOperation(\"$mountName\"${formatOperationParameters()})"

    private fun formatOperationParameters(): String {
        if (params.isEmpty()) return ""
        return ", ${params.joinToString(",\n\t") { parameterTransformer(it.name.str.snakeToLowerCamelCase(), it.type) }}"
    }

    override fun formatInputParameters() = "tx: Itransaction${super.formatInputParameters().let { if (it.isNotBlank()) ",\n\t$it" else "" }}"

    override fun formatReturnType(): String = "void"

    override fun returnStructure(returnType: R_Type?): String = ""
}
