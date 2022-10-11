package net.postchain.rell.codegen.kotlin

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvNull
import net.postchain.gtv.mapper.Name
import net.postchain.gtv.mapper.Nullable
import net.postchain.rell.codegen.deps.CamelCaseClassName
import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.deps.DependencyFinder
import net.postchain.rell.codegen.kotlin.util.attributeToGtv
import net.postchain.rell.codegen.kotlin.util.rTypeToString
import net.postchain.rell.codegen.section.DocumentSection
import net.postchain.rell.codegen.util.GeneratedAnnotation
import net.postchain.rell.codegen.util.snakeToLowerCamelCase
import net.postchain.rell.codegen.util.snakeToUpperCamelCase
import net.postchain.rell.model.*
import javax.annotation.processing.Generated
import kotlin.reflect.KClass

abstract class ExtensionMethodSection(
    protected val className: ClassName,
    private val simpleName: String,
    private val extendedClass: KClass<*>,
    private val extendenMethod: String,
    protected val params: List<R_Param>,
    private val returnType: R_Type?
) : DocumentSection {
    override val moduleName: String
        get() = className.module

    final override val imports: List<String> = listOf(
        "import ${extendedClass.qualifiedName}",
        "import ${Generated::class.qualifiedName}",
        "import ${Gtv::class.qualifiedName}",
        "import ${GtvArray::class.qualifiedName}",
        "import ${GtvNull::class.qualifiedName}",
        "import ${Name::class.qualifiedName}",
        "import ${Nullable::class.qualifiedName}", // TODO: Propagate imports from [returnStructure]
        "import net.postchain.gtv.GtvFactory.gtv",
        "import net.postchain.gtv.mapper.toObject",
    )
    final override val deps: Set<ClassName>

    init {
        val returnDeps = DependencyFinder.findDependencies(returnType)
        val paramDeps = DependencyFinder.findDependencies(params.map { it.type })
        deps = paramDeps + returnDeps
    }

    override fun format() = """
        |${GeneratedAnnotation.createAnnotation(className.rellName)}
        |fun ${extendedClass.simpleName}.${className.name}(${formatInputParameters()}) = 
        |   $extendenMethod("$simpleName"${formatGtvParameters()})${formatReturnType(returnType)}
        |${returnStructure(returnType)}
    """.trimMargin()


    private fun formatInputParameters(): String {
        if (params.isEmpty()) return ""
        return params.joinToString(",\n\t") { "${it.name.snakeToLowerCamelCase()}: ${rTypeToString(it.type)}" }
    }

    abstract fun formatGtvParameters(): String

    internal fun parameterToGtv(param: R_Param): String {
        return attributeToGtv(param.name.snakeToLowerCamelCase(), param.type)
    }

    private fun formatReturnType(type: R_Type?): String {
        return when (type) {
            null -> ""
            is R_NullableType -> ".let { if (it is GtvNull) null else it${formatReturnType(type.valueType)} }"
            is R_BooleanType -> ".asBoolean()"
            is R_EnumType -> ".let { ${
                type.name.substringAfter(":").snakeToUpperCamelCase()
            }.valueOf(it.asString()) }"
            is R_TextType -> ".asString()"
            is R_IntegerType -> ".asInteger()"
            is R_ByteArrayType -> ".asByteArray()"
            is R_EntityType -> ".asInteger()"            // Note that entities are encoded as GtvInteger
            is R_DecimalType -> ".let { BigDecimal(it.asString()) }"            // Note that decimals are encoded as GtvString(?)
            is R_RowidType -> ".asInteger()"             // Same as EntityType
            is R_MapType -> formatMapReturnType(type)
            is R_StructType -> ".toObject<${CamelCaseClassName.fromString(type.name).name}>()"
            is R_ListType -> ".asArray().map { it${formatReturnType(type.elementType)} }"
            is R_SetType -> ".asArray().map { it${formatReturnType(type.elementType)} }.toSet()"
            is R_TupleType -> formatTupleType(type)
            else -> ""                                  // All structs (should be "unknown structs"
        }
    }

    private fun formatMapReturnType(type: R_MapType) = if (type.keyType !is R_TextType) "" else
        ".asDict().mapValues { (k, v) -> v${formatReturnType(type.valueType)} }"

    private fun formatTupleType(type: R_TupleType): String {
        if (!type.name.contains(":")) return ".asArray()"
        return ".toObject<${simpleName.snakeToUpperCamelCase()}Result>()"
    }

    // Creates a Data class if the return-type is a named tuple
    private fun returnStructure(returnType: R_Type?): String {
        if (returnType == null) return ""
        if (returnType is R_NullableType) return returnStructure(returnType.valueType)
        if (returnType is R_CollectionType) return returnStructure(returnType.elementType)
        if (returnType !is R_TupleType || !returnType.name.contains(":")) return "" // Non-tuples and unnamed tuples
        val resultObject = DataClassSection(
            CamelCaseClassName("", simpleName.snakeToUpperCamelCase() + "Result", className.module),
            returnType.fields.associateBy({ it.name!!.str }, { it.type })
        )
        return "\n${resultObject.format()}"
    }
}
