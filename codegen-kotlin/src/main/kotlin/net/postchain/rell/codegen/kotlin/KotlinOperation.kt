package net.postchain.rell.codegen.kotlin

import net.postchain.client.core.GTXTransactionBuilder
import net.postchain.rell.codegen.deps.CamelCaseClassName
import net.postchain.rell.codegen.section.Operation
import net.postchain.rell.model.*

class KotlinOperation(op: R_OperationDefinition) : ExtensionMethodSection(
    CamelCaseClassName.fromRellOperation(op),
    op.simpleName,
    GTXTransactionBuilder::class,
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
}
