package net.postchain.rell.codegen.kotlin

import net.postchain.rell.codegen.deps.CamelCaseClassName
import net.postchain.rell.codegen.section.Query
import net.postchain.rell.model.*

class KotlinQuery(queryDef: R_QueryDefinition) : ExtensionMethodSection(
    CamelCaseClassName.fromRellQuery(queryDef),
    queryDef.simpleName,
    // TODO: This is a temporary fix as Postchain 3.7 has introduced [PostchainQuery] interface.
    // When postchain version is increased, use `PostchainQuery::class` instead
    "net.postchain.client.core.PostchainQuery",
    "PostchainQuery",
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
