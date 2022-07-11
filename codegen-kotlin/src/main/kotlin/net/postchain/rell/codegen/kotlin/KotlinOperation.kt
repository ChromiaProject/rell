package net.postchain.rell.codegen.kotlin

import net.postchain.client.core.GTXTransactionBuilder
import net.postchain.rell.codegen.section.Query
import net.postchain.rell.codegen.util.snakeToUpperCamelCase
import net.postchain.rell.model.*

class KotlinOperation(queryDef: R_OperationDefinition, basePackage: String) : KotlinExtensionSection(
    queryDef.appLevelName,
    queryDef.simpleName,
    "add${queryDef.simpleName.snakeToUpperCamelCase()}Operation",
    GTXTransactionBuilder::class,
    "addOperation",
    queryDef.params(),
    null,
    basePackage
), Query {
    override val moduleName = queryDef.defId.module.substringBefore("[")

    override fun formatGtvParameters(): String {
        return ""
    }
}
