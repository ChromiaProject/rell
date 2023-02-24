package net.postchain.rell.codegen.typescript

import net.postchain.rell.codegen.deps.CamelCaseClassName
import net.postchain.rell.codegen.section.Query
import net.postchain.rell.codegen.typescript.util.formatNamedTuple
import net.postchain.rell.codegen.typescript.util.parameterTransformer
import net.postchain.rell.codegen.typescript.util.rTypeToString
import net.postchain.rell.codegen.util.capitalize
import net.postchain.rell.codegen.util.snakeToLowerCamelCase
import net.postchain.rell.model.*

class TypescriptQuery(queryDef: R_QueryDefinition) : TypescriptFunction(
        CamelCaseClassName.fromRellQuery(queryDef),
        queryDef.mountName,
        queryDef.params(),
        true,
        queryDef.type(),
), Query {
    override val imports: List<String> = listOf("")
    private val returnStructure = returnStructure(returnType)
    override val moduleName: String
        get() = className.module

    override fun formatBody() = "return await gtxClient.query(\"$mountName\"${formatQueryParameters()})"

    private fun formatQueryParameters(): String {
        if (params.isEmpty()) return ""
        return ", " + params.joinToString(",\n\t") { "{${parameterTransformer(it.name.str.snakeToLowerCamelCase(), it.type)}}" }
    }

    override fun formatReturnType(): String = "Promise<${if (returnStructure.isNotBlank()) buildReturnType() else rTypeToString(returnType!!)}>"

    override fun returnStructure(returnType: R_Type?): String {
        if (returnType == null) return ""
        if (returnType is R_NullableType) return returnStructure(returnType.valueType)
        if (returnType is R_CollectionType) return returnStructure(returnType.elementType)
        if (returnType !is R_TupleType || !returnType.name.contains(":")) return "" // Non-tuples and unnamed tuples
        return "\n${formatReturnTupleObject(returnType)}"
    }

    private fun formatReturnTupleObject(type: R_TupleType): String {
        val formattedType = when (returnType) {
            is R_NullableType -> formatNamedTuple(type) + " | null"
            is R_CollectionType -> formatNamedTuple(type) + "[]"
            else -> formatNamedTuple(type)
        }
        return """
        |   type ${buildReturnType()} = $formattedType
    """.trimMargin()
    }

    private fun buildReturnType() = "${capitalize(className.name)}ReturnType"
}
