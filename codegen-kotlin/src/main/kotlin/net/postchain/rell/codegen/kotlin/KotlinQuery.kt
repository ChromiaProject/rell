package net.postchain.rell.codegen.kotlin

import net.postchain.client.core.PostchainQuery
import net.postchain.rell.codegen.deps.CamelCaseClassName
import net.postchain.rell.codegen.section.Query
import net.postchain.rell.model.*

class KotlinQuery(queryDef: R_QueryDefinition) : ExtensionMethodSection(
    CamelCaseClassName.fromRellQuery(queryDef),
    queryDef.simpleName,
    PostchainQuery::class,
    "querySync",
    queryDef.params(),
    queryDef.type()
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
