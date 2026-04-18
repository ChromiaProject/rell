/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.rell.base.compiler.base.core.C_DefinitionType
import net.postchain.rell.base.compiler.base.modifier.C_ModifierTargetType
import net.postchain.rell.base.lib.type.R_ByteArrayType
import net.postchain.rell.base.lib.type.R_TextType
import net.postchain.rell.base.runtime.Rt_Exception
import net.postchain.rell.base.runtime.Rt_Value
import org.jooq.Condition
import org.jooq.Constraint
import org.jooq.Field
import org.jooq.impl.DSL.*
import java.util.*

abstract class R_AttrValidator internal constructor(internal val metadata: R_AttrValidatorMetadata) {
    abstract fun check(value: Rt_Value): Error?
    abstract fun genSqlCheckConstraint(sqlConstraintName: String, sqlTable: String, attr: R_Attribute): Constraint

    data class Error(val code: String, val msg: String) {
        fun raise() {
            throw Rt_Exception.common(code, msg)
        }
    }
}

internal data class R_AttrValidatorMetadata(
    val name: R_Name, // name of the parameter/attribute being validated
    val type: R_Type, // data type of the parameter/attribute being validated
    val valueTargetType: C_ModifierTargetType, // parameter or attribute? (anything else would be an error)
    val ownerName: R_DefinitionName, // the name of the definition to which it belongs
    val ownerDefType: C_DefinitionType, // the kind of definition that it belongs to (struct, function etc.)
)

internal class R_SizeAttrValidator(
    internal val min: Long?,
    internal val max: Long?,
    private val sizeAdapter: R_SizeAdapter,
    metadata: R_AttrValidatorMetadata,
): R_AttrValidator(metadata) {
    init {
        require(min != null || max != null) { "min and max cannot both be null" }
    }

    override fun check(value: Rt_Value): Error? {
        val size = sizeAdapter.getSize(value)
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

    override fun genSqlCheckConstraint(sqlConstraintName: String, sqlTable: String, attr: R_Attribute): Constraint {
        val boundsExprs = mutableListOf<Condition>()
        val lengthExpr = sizeAdapter.genSqlSize(field(name(attr.sqlMapping), String::class.java))
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
        fun getSizeAdapter(type: R_Type): R_SizeAdapter? {
            return when (type) {
                // is R_ListType -> R_ListSizeAdapter
                // is R_MapType -> R_MapSizeAdapter
                // is R_SetType -> R_SetSizeAdapter
                is R_ByteArrayType -> R_ByteArraySizeAdapter
                is R_TextType -> R_TextSizeAdapter
                // is R_JsonType -> R_JsonSizeAdapter
                else -> null
            }
        }
    }

    internal interface R_SizeAdapter {
        fun getSize(value: Rt_Value): Int
        fun genSqlSize(sqlArgExpr: Field<String>): Field<Int>
    }

    /*
    TODO: Support size on mutable collections. Might have to implement some kind of reference tracking, which could be
    tricky. Another option would be to support only immutable collections.
    private object R_ListSizeAdapter: R_SizeAdapter {
        override fun getSize(value: Rt_Value): Int = value.asList().size
    }

    private object R_MapSizeAdapter: R_SizeAdapter {
        override fun getSize(value: Rt_Value): Int = value.asMap().size
    }

    private object R_SetSizeAdapter: R_SizeAdapter {
        override fun getSize(value: Rt_Value): Int = value.asSet().size
    }
    */

    private object R_ByteArraySizeAdapter: R_SizeAdapter {
        override fun getSize(value: Rt_Value): Int = value.asByteArray().size
        override fun genSqlSize(sqlArgExpr: Field<String>): Field<Int> = octetLength(sqlArgExpr)
    }

    private object R_TextSizeAdapter: R_SizeAdapter {
        override fun getSize(value: Rt_Value): Int = value.asString().length
        override fun genSqlSize(sqlArgExpr: Field<String>): Field<Int> = length(sqlArgExpr)
    }

    /*
    TODO: JSON also disabled since it's not clear what 'size' should mean for JSON. If it's the size of the internal
    text, one can use a workaround - the @size can be on a text attribute, and convert to JSON where required.
    private object R_JsonSizeAdapter: R_SizeAdapter {
        override fun getSize(value: Rt_Value): Int = value.asJsonString().length
    }
    */
}
