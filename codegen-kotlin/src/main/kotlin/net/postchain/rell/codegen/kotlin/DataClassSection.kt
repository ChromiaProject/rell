package net.postchain.rell.codegen.kotlin

import net.postchain.common.types.WrappedByteArray
import net.postchain.gtv.Gtv
import net.postchain.gtv.mapper.Name
import net.postchain.gtv.mapper.Nullable
import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.deps.DependencyFinder
import net.postchain.rell.codegen.kotlin.util.rTypeToString
import net.postchain.rell.codegen.section.DocumentSection
import net.postchain.rell.codegen.util.GeneratedAnnotation
import net.postchain.rell.codegen.util.snakeToLowerCamelCase
import net.postchain.rell.model.R_NullableType
import net.postchain.rell.model.R_Type
import java.math.BigDecimal
import javax.annotation.processing.Generated

open class DataClassSection(
    protected val className: ClassName,
    attributes: Map<String, R_Type>
) : DocumentSection {
    override val moduleName: String
        get() = className.module

    override val imports = listOf(
        "import ${BigDecimal::class.qualifiedName}",
        "import ${WrappedByteArray::class.qualifiedName}",
        "import ${Gtv::class.qualifiedName}",
        "import ${Generated::class.qualifiedName}",
        "import ${Name::class.qualifiedName}",
        "import ${Nullable::class.qualifiedName}",
        "import net.postchain.gtv.GtvFactory.gtv",
    )

    override val deps = DependencyFinder.findDependencies(attributes.values)

    private val classFields = attributes.map { formatAttribute(it.key, it.value) }

    private fun formatAttribute(name: String, type: R_Type) =
        "@Name(\"$name\")${nullableAnnotation(type)} val ${name.snakeToLowerCamelCase()}: ${rTypeToString(type)}"

    private fun nullableAnnotation(type: R_Type) = if (type is R_NullableType) " @Nullable" else ""

    override fun format() = """
        |${GeneratedAnnotation.createAnnotation(className.rellName)}
        |data class ${className.name}(
        |    ${classFields.joinToString(",\n\t")}
        |)
    """.trimMargin()
}
