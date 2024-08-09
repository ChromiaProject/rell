package net.postchain.rell.codegen.kotlin

import net.postchain.common.BlockchainRid
import net.postchain.common.types.RowId
import net.postchain.common.types.WrappedByteArray
import net.postchain.crypto.PubKey
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvNull
import net.postchain.gtv.mapper.GtvObjectMapper
import net.postchain.gtv.mapper.Name
import net.postchain.gtv.mapper.Nullable
import net.postchain.rell.base.model.R_FunctionParam
import net.postchain.rell.base.model.R_MountName
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.deps.DependencyFinder
import net.postchain.rell.codegen.kotlin.util.attributeToGtv
import net.postchain.rell.codegen.kotlin.util.rTypeToString
import net.postchain.rell.codegen.section.DocumentSection
import net.postchain.rell.codegen.util.GeneratedAnnotation
import net.postchain.rell.codegen.util.snakeToLowerCamelCase
import java.math.BigDecimal
import java.math.BigInteger
import javax.annotation.processing.Generated
import kotlin.reflect.KClass

abstract class ExtensionMethodSection(
        protected val kind: String,
        protected val className: ClassName,
        protected val mountName: R_MountName,
        private val extendedClass: KClass<*>,
        private val extendenMethod: String,
        protected val params: List<R_FunctionParam>,
        private val returnType: R_Type?
) : DocumentSection {
    override val moduleName: String
        get() = className.module

    final override val imports: List<String> = listOf(
            "import ${BigDecimal::class.qualifiedName}",
            "import ${BigInteger::class.qualifiedName}",
            "import ${WrappedByteArray::class.qualifiedName}",
            "import ${RowId::class.qualifiedName}",
            "import net.postchain.common.wrap",
            "import ${extendedClass.qualifiedName}",
            "import ${Generated::class.qualifiedName}",
            "import ${Gtv::class.qualifiedName}",
            "import ${GtvArray::class.qualifiedName}",
            "import ${GtvNull::class.qualifiedName}",
            "import ${GtvObjectMapper::class.qualifiedName}",
            "import ${Name::class.qualifiedName}",
            "import ${BlockchainRid::class.qualifiedName}",
            "import ${PubKey::class.qualifiedName}",
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

    override fun format(): String {
        val functionString = """
        |const val ${className.constantName} = "$mountName"
        |/**
        | * $kind ${className.rellName} 
        | */
        |${GeneratedAnnotation.createAnnotation(className.rellName)}
        |fun ${extendedClass.simpleName}.${className.className}(${formatInputParameters()}) = 
        |   $extendenMethod(${className.constantName}${formatGtvParameters()})${formatReturnType(returnType)}
    """.trimMargin()
        val returnTypeString = "\n${returnStructure(returnType)}"
        return StringBuilder()
                .append(functionString)
                .append(returnTypeString.ifBlank { "" })
                .toString()
    }

    private fun formatInputParameters(): String {
        if (params.isEmpty()) return ""
        return params.joinToString(",\n\t") { "${it.name.str.snakeToLowerCamelCase()}: ${rTypeToString(it.name.str, it.type, primitiveTypes = true, aliases = true)}" }
    }

    abstract fun formatGtvParameters(): String

    internal fun parameterToGtv(param: R_FunctionParam): String {
        return attributeToGtv(param.name.str.snakeToLowerCamelCase(), param.type)
    }

    abstract fun formatReturnType(type: R_Type?, depth: Int = 0): String

    abstract fun returnStructure(returnType: R_Type?): String
}
