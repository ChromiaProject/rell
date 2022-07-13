package net.postchain.rell.codegen.kotlin

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvNull
import net.postchain.gtv.mapper.Name
import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.deps.DependencyFinder
import net.postchain.rell.codegen.kotlin.util.attributeToGtv
import net.postchain.rell.codegen.kotlin.util.rTypeToString
import net.postchain.rell.codegen.section.DocumentSection
import net.postchain.rell.codegen.util.GeneratedAnnotation
import net.postchain.rell.codegen.util.snakeToLowerCamelCase
import net.postchain.rell.model.*
import java.math.BigDecimal
import javax.annotation.processing.Generated

open class DataClassSection(
    protected val className: ClassName,
    private val attributes: Map<String, R_Type>
) : DocumentSection {
    override val moduleName: String
        get() = className.module

    override val imports = listOf(
        "import ${BigDecimal::class.qualifiedName}",
        "import ${Gtv::class.qualifiedName}",
        "import ${GtvArray::class.qualifiedName}",
        "import ${GtvNull::class.qualifiedName}",
        "import ${Generated::class.qualifiedName}",
        "import ${Name::class.qualifiedName}",
        "import net.postchain.gtv.GtvFactory.gtv",
    )


    override val deps = DependencyFinder.findDependencies(attributes.values)

    private val classFields = attributes.map { formatAttribute(it.key, it.value) }

    private fun formatAttribute(name: String, type: R_Type): String {
        return "@Name(\"$name\") val ${name.snakeToLowerCamelCase()}: ${rTypeToString(type)}"
    }

    override fun format() = """
        |${GeneratedAnnotation.createAnnotation(className.rellName)}
        |data class ${className.name}(
        |    ${classFields.joinToString(",\n\t")}
        |) {
        |    /**
        |     * Formats this structure as a [GtvArray]
        |     */
        |    fun toGtv(): Gtv {
        |        return ${formatGtv()}
        |    }
        |}
    """.trimMargin()

    private fun formatGtv(): String {
        return "gtv(\n\t\t\t${
            attributes.map { attributeToGtv(it.key.snakeToLowerCamelCase(), it.value) }.joinToString(",\n\t\t\t")
        }\n\t\t)"
    }
}
