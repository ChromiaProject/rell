/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.rell.base.compiler.base.core.C_DefinitionType
import net.postchain.rell.base.compiler.base.modifier.C_ModifierTargetType
import org.jooq.Condition
import org.jooq.Constraint
import org.jooq.Field
import org.jooq.impl.DSL.*
import java.util.*

abstract class R_AttrValidator(val metadata: R_AttrValidatorMetadata) {
    /**
     * Checks the given runtime value against this validator's constraints.
     * The [value] is expected to be an `Rt_Value` at runtime.
     */
    abstract fun check(value: Any): Error?
    abstract fun genSqlCheckConstraint(sqlConstraintName: String, sqlTable: String, sqlMapping: String): Constraint

    data class Error(val code: String, val msg: String)
}

data class R_AttrValidatorMetadata(
    val name: Name, // name of the parameter/attribute being validated
    val type: R_Type, // data type of the parameter/attribute being validated
    val valueTargetType: C_ModifierTargetType, // parameter or attribute? (anything else would be an error)
    val ownerName: DefinitionName, // the name of the definition to which it belongs
    val ownerDefType: C_DefinitionType, // the kind of definition that it belongs to (struct, function etc.)
)

class R_SizeAttrValidator(
    val min: Long?,
    val max: Long?,
    private val sizeExtractor: (Any) -> Int,
    val sqlSizeAdapter: R_SqlSizeAdapter,
    metadata: R_AttrValidatorMetadata,
): R_AttrValidator(metadata) {
    init {
        require(min != null || max != null) { "min and max cannot both be null" }
    }

    override fun check(value: Any): Error? {
        val size = sizeExtractor(value)
        if (min != null && size < min) {
            val msg = buildTooSmallErrMsg(size)
            return Error(tooSmallErrCode, msg)
        }
        if (max != null && size > max) {
            val msg = buildTooLargeErrMsg(size)
            return Error(tooLargeErrCode, msg)
        }
        return null
    }

    override fun genSqlCheckConstraint(sqlConstraintName: String, sqlTable: String, sqlMapping: String): Constraint {
        val boundsExprs = mutableListOf<Condition>()
        val lengthExpr = sqlSizeAdapter.genSqlSize(field(name(sqlMapping), String::class.java))
        min?.let { boundsExprs.add(lengthExpr.ge(it.toInt())) }
        max?.let { boundsExprs.add(lengthExpr.le(it.toInt())) }
        return constraint(sqlConstraintName).check(boundsExprs.reduce { mn, mx -> mn.and(mx) })
    }

    private val defTypeStr: String by lazy { metadata.ownerDefType.name.lowercase(Locale.US) }
    private val codePrefix: String by lazy {
        "$defTypeStr:${metadata.ownerName.simpleName}:${metadata.valueTargetType.description}:${metadata.name.str}"
    }
    private val tooSmallErrCode: String by lazy { "$codePrefix:validator:size:too_small" }
    private val tooLargeErrCode: String by lazy { "$codePrefix:validator:size:too_large" }

    private fun buildTooSmallErrMsg(size: Int): String {
        val maxStr = if (max != null) " The specified maximum size is $max." else " No maximum is specified."
        return "${metadata.valueTargetType.descriptionCapitalized} ${metadata.name.str} of $defTypeStr " +
            "${metadata.ownerName.simpleName}: size too small: specified minimum is $min (inclusive), got $size.$maxStr"
    }

    private fun buildTooLargeErrMsg(size: Int): String {
        val minStr = if (min != null) " The specified minimum size is $min." else " No minimum is specified."
        return "${metadata.valueTargetType.descriptionCapitalized} ${metadata.name.str} of $defTypeStr " +
            "${metadata.ownerName.simpleName}: size too large: specified maximum is $max (inclusive), got $size.$minStr"
    }

    companion object {
        fun getSizeAdapter(type: R_Type): Pair<(Any) -> Int, R_SqlSizeAdapter>? {
            return when (type) {
                is R_ByteArrayType -> R_SizeExtractors.BYTE_ARRAY to R_SqlSizeAdapter.BYTE_ARRAY
                is R_TextType -> R_SizeExtractors.TEXT to R_SqlSizeAdapter.TEXT
                else -> null
            }
        }
    }
}

/**
 * Runtime value size extractors. Each lambda accepts an `Any` that is actually an `Rt_Value`
 * and returns the size as an `Int`. Using lambdas avoids importing `Rt_Value` in the model package.
 */
object R_SizeExtractors {
    /** Set from runtime during initialization. Extracts byte array size from an opaque Rt_Value. */
    lateinit var BYTE_ARRAY: (Any) -> Int
    /** Set from runtime during initialization. Extracts text length from an opaque Rt_Value. */
    lateinit var TEXT: (Any) -> Int
}

/** SQL-only size adapter for generating CHECK constraints. */
enum class R_SqlSizeAdapter {
    BYTE_ARRAY {
        override fun genSqlSize(sqlArgExpr: Field<String>): Field<Int> = octetLength(sqlArgExpr)
    },
    TEXT {
        override fun genSqlSize(sqlArgExpr: Field<String>): Field<Int> = length(sqlArgExpr)
    },
    ;

    abstract fun genSqlSize(sqlArgExpr: Field<String>): Field<Int>
}
