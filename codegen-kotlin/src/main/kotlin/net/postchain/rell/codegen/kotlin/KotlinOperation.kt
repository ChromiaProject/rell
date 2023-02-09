package net.postchain.rell.codegen.kotlin

import net.postchain.client.transaction.TransactionBuilder
import net.postchain.rell.codegen.deps.CamelCaseClassName
import net.postchain.rell.codegen.section.Operation
import net.postchain.rell.model.R_OperationDefinition
import net.postchain.rell.model.R_Type

class KotlinOperation(op: R_OperationDefinition) : ExtensionMethodSection(
    CamelCaseClassName.fromRellOperation(op),
    op.mountName,
    TransactionBuilder::class,
    "addOperation",
    op.params(),
    null
), Operation {

    override fun format() = """
       |/**
       | * Operation ${className.rellName}
       | */
       |${super.format()}
    """.trimMargin()

    override fun formatGtvParameters(): String {
        if (params.isEmpty()) return ""
        return ", ${params.joinToString(",\n\t") { parameterToGtv(it) }}"
    }

    override fun formatReturnType(type: R_Type?): String = ""

    override fun returnStructure(returnType: R_Type?): String = ""
}
