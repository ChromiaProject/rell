package net.postchain.rell.codegen.typescript

import net.postchain.rell.codegen.deps.CamelCaseClassName
import net.postchain.rell.codegen.section.Query
import net.postchain.rell.codegen.typescript.util.formatNamedTuple
import net.postchain.rell.codegen.typescript.util.rTypeToString
import net.postchain.rell.codegen.util.capitalize
import net.postchain.rell.codegen.util.snakeToLowerCamelCase
import net.postchain.rell.model.*

class TypescriptQuery(queryDef: R_QueryDefinition) : Query {
    override val imports: List<String> = listOf("")
    private val className = CamelCaseClassName.fromRellQuery(queryDef)
    private val mountName = queryDef.mountName
    private val params = queryDef.params()
    private val returnType = queryDef.type()

    private val returnStructure = returnStructure(returnType);

    override val moduleName: String
        get() = className.module

    override fun format(): String {
        return """
        |    /**
        |    * Query ${className.rellName} 
        |    */
        |${returnStructure(returnType)}
        |${queryFormat()}
        """.trimMargin()
    }

    private fun queryFormat() = """
        |   ${className.name}: async function (${formatInputParameters()}): Promise<${formatReturnType()}> {
        |       return await gtxClient.query("$mountName"${formatQueryParameters()});
        |    },
    """.trimMargin()

    private fun formatInputParameters(): String {
        if (params.isEmpty()) return ""
        return params.joinToString(",\n\t") { "${it.name.str.snakeToLowerCamelCase()}: ${rTypeToString(it.type)}" }
    }

    private fun formatQueryParameters(): String {
        if (params.isEmpty()) return ""
        return params.joinToString(",\n\t") { ", {${it.name.str.snakeToLowerCamelCase()}}" }
    }

    private fun formatReturnType() = if (returnStructure.isNotBlank()) buildReturnType() else rTypeToString(returnType)

    private fun returnStructure(returnType: R_Type?): String {
        if (returnType == null) return ""
        if (returnType is R_NullableType) return "${returnStructure(returnType.valueType)}"
        if (returnType is R_CollectionType) return "${returnStructure(returnType.elementType)}"
        if (returnType !is R_TupleType || !returnType.name.contains(":")) return "" // Non-tuples and unnamed tuples
        return "\n${formatReturnTupleObject(returnType)}"
    }

    private fun formatReturnTupleObject(type: R_TupleType): String {
        val formatedType = when (returnType) {
            is R_NullableType -> formatNamedTuple(type) + " | null"
            is R_CollectionType -> formatNamedTuple(type) + "[]"
            else -> formatNamedTuple(type)
        }
        return """
        |   type ${buildReturnType()} = $formatedType
    """.trimMargin()
    }

    private fun buildReturnType() = "${capitalize(className.name)}ReturnType"
}
