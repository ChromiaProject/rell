/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen.kotlin

import net.postchain.common.BlockchainRid
import net.postchain.common.types.RowId
import net.postchain.common.types.WrappedByteArray
import net.postchain.crypto.PubKey
import net.postchain.gtv.Gtv
import net.postchain.gtv.mapper.Name
import net.postchain.gtv.mapper.Nullable
import net.postchain.rell.base.model.R_NullableType
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.utils.doc.DocSymbol
import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.deps.DependencyFinder
import net.postchain.rell.codegen.kotlin.util.rTypeToString
import net.postchain.rell.codegen.section.DocumentSection
import net.postchain.rell.codegen.util.GeneratedAnnotation
import net.postchain.rell.codegen.util.snakeToLowerCamelCase
import java.math.BigDecimal
import java.math.BigInteger
import javax.annotation.processing.Generated

open class DataClassSection(
    protected val className: ClassName,
    attributes: Map<String, R_Type>,
    override val docSymbol: DocSymbol,
) : DocumentSection {
    override val moduleName: String
        get() = className.module

    override val imports = listOf(
        "import ${BigDecimal::class.qualifiedName}",
        "import ${BigInteger::class.qualifiedName}",
        "import ${WrappedByteArray::class.qualifiedName}",
        "import ${RowId::class.qualifiedName}",
        "import ${Gtv::class.qualifiedName}",
        "import ${Generated::class.qualifiedName}",
        "import ${Name::class.qualifiedName}",
        "import ${Nullable::class.qualifiedName}",
        "import ${BlockchainRid::class.qualifiedName}",
        "import ${PubKey::class.qualifiedName}",
        "import net.postchain.gtv.GtvFactory.gtv",
    )

    override val deps = DependencyFinder.findDependencies(attributes.values)

    private val classFields = attributes.map { formatAttribute(it.key, it.value) }

    private fun formatAttribute(name: String, type: R_Type) =
        "@param:Name(\"$name\")${nullableAnnotation(type)} val ${name.snakeToLowerCamelCase()}: ${rTypeToString(name, type, primitiveTypes = false, aliases = false)}"

    private fun nullableAnnotation(type: R_Type) = if (type is R_NullableType) " @param:Nullable" else ""

    override fun format() = """
        |${GeneratedAnnotation.createAnnotation(className.rellName)}
        |data class ${className.className}(
        |${"\t"}${classFields.joinToString(",\n\t")}
        |)
    """.trimMargin()
}
