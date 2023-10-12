package net.postchain.rell.codegen.typescript

import net.postchain.rell.base.model.*
import net.postchain.rell.codegen.deps.CamelCaseClassName
import net.postchain.rell.codegen.section.Query
import net.postchain.rell.codegen.typescript.util.parameterTransformer
import net.postchain.rell.codegen.typescript.util.rTypeToString
import net.postchain.rell.codegen.util.capitalize
import net.postchain.rell.codegen.util.snakeToLowerCamelCase

class TypescriptQuery(queryDef: R_QueryDefinition) : TypescriptFunction(
        CamelCaseClassName.fromRellQuery(queryDef),
        queryDef.mountName,
        queryDef.params(),
        false,
        queryDef.type(),
        "QueryObject"
), Query {
    override val imports: List<String> = listOf("import { QueryObject } from \"postchain-client\";")
    private val returnStructure = returnStructure(returnType)
    override val moduleName: String
        get() = className.module

    override fun formatBody() = "return { name: \"$mountName\", args: ${formatQueryParameters()} };"

    private fun formatQueryParameters(): String {
        if (params.isEmpty()) return "undefined"
        return "{ " + params.joinToString(", ") { "${it.name.str}: ${parameterTransformer(it.name.str.snakeToLowerCamelCase(), it.type)}" } + " }"
    }

    override fun formatReturnType(): String = "QueryObject<${if (returnStructure.isNotBlank()) buildReturnType() else rTypeToString(returnType!!)}>"

    override fun returnStructure(returnType: R_Type?): String {
        if (returnType == null) return ""
        if (returnType is R_NullableType) return returnStructure(returnType.valueType)
        if (returnType is R_CollectionType) return returnStructure(returnType.elementType)
        if (returnType !is R_TupleType || !returnType.name.contains(":")) return "" // Non-tuples and unnamed tuples
        val resultObject = DataTypeSection(
                CamelCaseClassName("", buildReturnType(false), className.module),
                returnType.fields.associateBy({ it.name!!.str }, { it.type })
        )
        return resultObject.format()
    }

    private fun buildReturnType(extendType: Boolean = true): String {
        val typeName = "${capitalize(className.className)}ReturnType"
        if (!extendType) return typeName
        if (returnType is R_NullableType) return "$typeName | undefined"
        if (returnType is R_CollectionType) return "$typeName[]"
        return typeName
    }

}
