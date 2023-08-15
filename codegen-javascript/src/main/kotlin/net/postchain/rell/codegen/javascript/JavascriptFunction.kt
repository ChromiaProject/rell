package net.postchain.rell.codegen.javascript

import net.postchain.rell.base.model.*
import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.section.DocumentSection
import net.postchain.rell.codegen.util.snakeToLowerCamelCase

abstract class JavascriptFunction(
        protected val className: ClassName,
        protected val mountName: R_MountName,
        protected val params: List<R_Param>,
        private val async: Boolean,
) : DocumentSection {
    override val moduleName get() = className.module

    final override val deps: Set<ClassName> = params.map{ it.type } .map { if (it is R_NullableType) it.valueType else it }.map { rTypeToBuiltinType(it).builtin.className }.toSet()

    override fun format(): String {
        val functionName = className.className.snakeToLowerCamelCase()
        return """|export ${asyncAnnotation()}function $functionName(${formatInputParameters()}) {
        |${formatTypechecks()}${"\t"}${formatBody()}
        |}""".trimMargin()
    }

    private fun asyncAnnotation() = if (async) "async " else ""

    open fun formatInputParameters(): String {
        if (params.isEmpty()) return ""
        return params.joinToString(",\n\t") { it.name.str.snakeToLowerCamelCase() }
    }

    private fun formatTypechecks(): String {
        if (params.isEmpty()) return ""
        return "\t" + params.joinToString("\n\t")
        {
            if (it.type is R_NullableType) {
                "${formatNullableAssertion((it.type as R_NullableType).valueType, it.name.str.snakeToLowerCamelCase())}"
            } else {
                "${rTypeToBuiltinType(it.type).builtin.functionName}(${it.name.str.snakeToLowerCamelCase()})"
            }
        } + "\n"
    }

    private fun rTypeToBuiltinType(type: R_Type): JavascriptBuiltinType {
        return when (type) {
            is R_BooleanType -> JavascriptBuiltinType.BooleanAssertion
            is R_IntegerType -> JavascriptBuiltinType.NumberAssertion
            is R_BigIntegerType -> JavascriptBuiltinType.BigIntegerAssertion
            is R_DecimalType -> JavascriptBuiltinType.NumberAssertion
            is R_TextType -> JavascriptBuiltinType.StringAssertion
            is R_ByteArrayType -> JavascriptBuiltinType.BufferAssertion
            is R_RowidType -> JavascriptBuiltinType.NumberAssertion
            is R_EntityType -> JavascriptBuiltinType.NumberAssertion
            is R_JsonType -> JavascriptBuiltinType.StringAssertion
            is R_SetType -> JavascriptBuiltinType.SetAssertion
            is R_ListType -> JavascriptBuiltinType.ArrayAssertion
            is R_MapType -> JavascriptBuiltinType.ObjectAssertion
            is R_StructType -> JavascriptBuiltinType.ObjectAssertion
            is R_EnumType -> JavascriptBuiltinType.ObjectAssertion
            is R_TupleType -> rTupleToBuiltinType(type)

            else -> JavascriptBuiltinType.AnyAssertion
        }
    }

    private fun rTupleToBuiltinType(type: R_TupleType): JavascriptBuiltinType {
        return if (type.name.contains(":")) JavascriptBuiltinType.ObjectAssertion
        else JavascriptBuiltinType.ArrayAssertion
    }

    private fun formatNullableAssertion(valueType: R_Type, paramName: String): String {
        return """
            if ($paramName != null) ${rTypeToBuiltinType(valueType).builtin.functionName}($paramName) 
        """.trimIndent()
    }

    protected fun parameterTransformer(name: String, type: R_Type): String = when (type) {
        is R_SetType -> "Array.from($name)"
        else -> name
    }

    abstract fun formatBody(): String

}
