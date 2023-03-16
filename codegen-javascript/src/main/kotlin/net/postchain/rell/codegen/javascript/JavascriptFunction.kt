package net.postchain.rell.codegen.javascript

import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.section.DocumentSection
import net.postchain.rell.codegen.util.snakeToLowerCamelCase
import net.postchain.rell.model.*

abstract class JavascriptFunction(
        protected val className: ClassName,
        protected val mountName: R_MountName,
        protected val params: List<R_Param>,
        private val async: Boolean,
) : DocumentSection {
    override val moduleName get() = className.module

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
        return "\t" + params.joinToString(",\n\t")
        { "${rTypeToTypecheck(it.type, it.name.str.snakeToLowerCamelCase())}\n" }
    }

    private fun rTypeToTypecheck(type: R_Type, paramName: String): String {
        return when (type) {
            is R_NullableType -> formatNullableAssertion(type.valueType, paramName)
            is R_BooleanType -> "${JavascriptBuiltinType.BooleanAssertion.builtin.functionName}($paramName)"
            is R_IntegerType -> "${JavascriptBuiltinType.NumberAssertion.builtin.functionName}($paramName)"
            is R_DecimalType -> "${JavascriptBuiltinType.NumberAssertion.builtin.functionName}($paramName)"
            is R_TextType -> "${JavascriptBuiltinType.StringAssertion.builtin.functionName}($paramName)"
            is R_ByteArrayType -> "${JavascriptBuiltinType.BufferAssertion.builtin.functionName}($paramName)"
            is R_RowidType -> "${JavascriptBuiltinType.NumberAssertion.builtin.functionName}($paramName)"
            is R_EntityType -> "${JavascriptBuiltinType.NumberAssertion.builtin.functionName}($paramName)"
            is R_JsonType -> "${JavascriptBuiltinType.StringAssertion.builtin.functionName}($paramName)"
            is R_SetType -> "${JavascriptBuiltinType.SetAssertion.builtin.functionName}($paramName)"
            is R_ListType -> "${JavascriptBuiltinType.ArrayAssertion.builtin.functionName}($paramName)"
            is R_MapType -> "${JavascriptBuiltinType.ObjectAssertion.builtin.functionName}($paramName)"
            is R_StructType -> "${JavascriptBuiltinType.ObjectAssertion.builtin.functionName}($paramName)"
            is R_EnumType -> "${JavascriptBuiltinType.ObjectAssertion.builtin.functionName}($paramName)"
            is R_TupleType -> formatTupleAssertion(type, paramName)

            else -> "${JavascriptBuiltinType.AnyAssertion.builtin.functionName}($paramName)"
        }
    }

    private fun formatNullableAssertion(valueType: R_Type, paramName: String): String {
        return StringBuilder()
                .append(JavascriptBuiltinType.BooleanAssertion.builtin.functionName)
                .append("(")
                .append("${JavascriptBuiltinType.NullAssertion.builtin.functionName}($paramName)")
                .append(" || ")
                .append(rTypeToTypecheck(valueType, paramName))
                .append(")")
                .toString()
    }

    private fun formatTupleAssertion(type: R_Type, paramName: String): String {
        return if (type.name.contains(":")) "${JavascriptBuiltinType.ObjectAssertion.builtin.functionName}($paramName)"
        else "${JavascriptBuiltinType.ArrayAssertion.builtin.functionName}($paramName)"
    }

    protected fun parameterTransformer(name: String, type: R_Type): String = when (type) {
        is R_SetType -> "Array.from($name)"
        else -> name
    }

    abstract fun formatBody(): String

}
