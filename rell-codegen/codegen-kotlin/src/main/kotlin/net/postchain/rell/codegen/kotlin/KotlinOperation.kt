package net.postchain.rell.codegen.kotlin

import net.postchain.client.transaction.TransactionBuilder
import net.postchain.rell.base.model.R_OperationDefinition
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.codegen.deps.CamelCaseClassName
import net.postchain.rell.codegen.section.Operation

class KotlinOperation(op: R_OperationDefinition) : ExtensionMethodSection(
    "Operation",
    CamelCaseClassName.fromRellOperation(op),
    op.mountName,
    TransactionBuilder::class,
    "addOperation",
    op.params(),
    null,
    op.docSymbol,
), Operation {

    override fun formatGtvParameters(): String {
        if (params.isEmpty()) return ""
        return ", ${params.joinToString(",\n\t") { parameterToGtv(it) }}"
    }

    override fun formatReturnType(type: R_Type?, depth: Int): String = ""

    override fun returnStructure(returnType: R_Type?): String = ""
}
