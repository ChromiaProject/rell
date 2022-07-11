package net.postchain.rell.codegen.kotlin

import net.postchain.client.core.PostchainClient
import net.postchain.rell.codegen.deps.CamelCaseClassName
import net.postchain.rell.codegen.section.Query
import net.postchain.rell.codegen.util.snakeToLowerCamelCase
import net.postchain.rell.model.*

class KotlinQuery(queryDef: R_QueryDefinition, basePackage: String) : KotlinExtensionSection(
    CamelCaseClassName.fromRellQuery(queryDef),
    queryDef.simpleName,
    PostchainClient::class,
    "querySync",
    queryDef.params(),
    queryDef.type(),
    basePackage
), Query {

    override fun format() = """
        |/**
        | * Query ${className.rellName} 
        | */
        |${super.format()}
    """.trimMargin()

    override fun formatGtvParameters(): String {
        if (params.isEmpty()) return ", gtv(mapOf())"
        return ", gtv(mapOf(" + params.joinToString(", ") { "\"${it.name}\" to ${parameterToGtv(it)}" } + "))"
    }
}
