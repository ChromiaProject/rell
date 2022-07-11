package net.postchain.rell.codegen.kotlin

import net.postchain.client.core.PostchainClient
import net.postchain.rell.codegen.section.Query
import net.postchain.rell.codegen.util.snakeToLowerCamelCase
import net.postchain.rell.model.*

class KotlinQuery(queryDef: R_QueryDefinition, basePackage: String) : KotlinExtensionSection(
    queryDef.appLevelName,
    queryDef.simpleName,
    queryDef.simpleName.snakeToLowerCamelCase(),
    PostchainClient::class,
    "querySync",
    queryDef.params(),
    queryDef.type(),
    basePackage
), Query {
    val name = queryDef.simpleName
    override val moduleName = queryDef.defId.module.substringBefore("[")

    override fun format() = """
        |/**
        | * Query $appLevelName 
        | */
        |${super.format()}
    """.trimMargin()

    override fun formatGtvParameters(): String {
        if (params.isEmpty()) return ", gtv(mapOf())"
        return ", gtv(mapOf(" + params.joinToString(", ") { "\"${it.name}\" to ${parameterToGtv(it)}" } + "))"
    }

    private fun parameterToGtv(param: R_Param): String {
        return when (param.type) {
            is R_StructType -> "${param.name}.toGtv()"
            is R_ListType -> "gtv(${param.name}.map { gtv(it) })"
            is R_SetType -> "gtv(${param.name}.map { gtv(it) })"
            else -> "gtv(${param.name})"
        }
    }
}
