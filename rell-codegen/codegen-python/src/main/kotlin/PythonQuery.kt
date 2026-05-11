/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen.python

import net.postchain.rell.base.model.*
import net.postchain.rell.codegen.deps.CamelCaseClassName
import net.postchain.rell.codegen.section.Query

class PythonQuery(queryDef: R_QueryDefinition) : PythonFunction(
    CamelCaseClassName.fromRellQuery(queryDef),
        queryDef.mountName,
        queryDef.params(),
        queryDef.docSymbol,
        queryDef.type(),
    ""
), Query {

    override val imports: List<String> = super.imports(PyFunctionImplementations.QUERY)

    override fun formatReturnObject(): String = buildString {
        append("return QueryObject(")
        append("""name="${mountName.str()}"""")
        append(", args=")
        append(formatReturnObjectArgs())
        append(")")
    }

    override fun formatReturnObjectArgs(): String {
        val parameters = params.joinToString(", ") { param ->
            val paramName = param.name.str.lowercase()
            when (val type = param.type) {
                is R_StructType -> {
                    """"$paramName": $paramName.to_dict()"""
                }
                is R_GtvType -> {
                    """"$paramName": $paramName"""
                }
                is R_EnumType -> {
                    """"$paramName": $paramName.value"""
                }
                is R_ListType -> {
                    formatCollectionType(paramName, type)
                }
                is R_SetType -> {
                    formatCollectionType(paramName, type, isSetType = true)
                }
                is R_NullableType -> {
                    val innerFormat = when (val innerType = type.valueType) {
                        is R_StructType -> "$paramName.to_dict() if $paramName else None"
                        is R_ListType -> formatCollectionType(paramName, innerType, nullable = true)
                        is R_SetType -> formatCollectionType(paramName, innerType, nullable = true, isSetType = true)
                        is R_MapType -> formatMapType(paramName, innerType, nullable = true)
                        else -> paramName
                    }
                    """"$paramName": $innerFormat"""
                }
                is R_MapType -> {
                    formatMapType(paramName, type)
                }
                else -> """"$paramName": $paramName"""
            }
        }
        return "{$parameters}"
    }

    private fun formatCollectionType(
            paramName: String,
            type: R_CollectionType,
            nullable: Boolean = false,
            isSetType: Boolean = false
    ): String {
        val elementType = type.elementType
        val transform = when (elementType) {
            is R_StructType -> "[item.to_dict() for item in $paramName]"
            is R_EnumType -> "[item.value for item in $paramName]"
            else -> if (isSetType) "list($paramName)" else paramName
        }

        return if (nullable) {
            "$transform if $paramName else None"
        } else {
            """"$paramName": $transform"""
        }
    }

    private fun formatMapType(paramName: String, type: R_MapType, nullable: Boolean = false): String {
        val keyType = type.keyType
        val valueType = type.valueType

        val keyTransform = when (keyType) {
            is R_StructType -> "k.to_dict()"
            is R_EnumType -> "k.value"
            else -> "k"
        }

        val valueTransform = when (valueType) {
            is R_StructType -> "v.to_dict()"
            is R_ListType -> "list(v)"
            is R_SetType -> "list(v)"
            is R_EnumType -> "v.value"
            is R_MapType -> formatMapType("v", valueType)
            else -> "v"
        }

        val mapExpression = if (keyType.name == "text") {
            "{$keyTransform: $valueTransform for k, v in $paramName.items()}"
        } else {
            "[[$keyTransform, $valueTransform] for k, v in $paramName.items()]"
        }

        return if (nullable) {
            "$mapExpression if $paramName else None"
        } else {
            """"$paramName": $mapExpression"""
        }
    }

    override fun formatReturnType(): String = "QueryObject"
}