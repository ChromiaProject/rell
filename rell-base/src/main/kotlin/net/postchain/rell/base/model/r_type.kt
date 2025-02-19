/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.gtv.Gtv
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.core.C_DefinitionName
import net.postchain.rell.base.compiler.base.core.C_TypeAdapter
import net.postchain.rell.base.compiler.base.core.C_TypeAdapter_Direct
import net.postchain.rell.base.compiler.base.lib.C_LibType
import net.postchain.rell.base.compiler.base.lib.C_LibTypeDef
import net.postchain.rell.base.compiler.base.lib.C_LibUtils
import net.postchain.rell.base.mtype.M_Type
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.runtime.utils.toGtv
import net.postchain.rell.base.sql.PreparedStatementParams
import net.postchain.rell.base.sql.ResultSetRow
import net.postchain.rell.base.utils.CommonUtils
import net.postchain.rell.base.utils.LazyString
import net.postchain.rell.base.utils.doc.DocCode
import net.postchain.rell.base.utils.immListOf
import org.jooq.DataType
import java.util.*

class R_GtvCompatibility(val fromGtv: Boolean, val toGtv: Boolean)

class R_TypeFlags(
    val pure: Boolean,
    val mutable: Boolean,
    val gtv: R_GtvCompatibility,
    val virtualable: Boolean,
    val mixedTuple: Boolean,
) {
    companion object {
        fun combine(flags: Collection<R_TypeFlags>): R_TypeFlags {
            var pure = true
            var mutable = false
            var fromGtv = true
            var toGtv = true
            var virtualable = true
            var mixedTuple = false

            for (f in flags) {
                pure = pure && f.pure
                mutable = mutable || f.mutable
                fromGtv = fromGtv && f.gtv.fromGtv
                toGtv = toGtv && f.gtv.toGtv
                virtualable = virtualable && f.virtualable
                mixedTuple = mixedTuple || f.mixedTuple
            }

            return R_TypeFlags(
                pure = pure,
                mutable = mutable,
                gtv = R_GtvCompatibility(fromGtv, toGtv),
                virtualable = virtualable,
                mixedTuple = mixedTuple,
            )
        }
    }
}

sealed class R_TypeSqlAdapter(val sqlType: DataType<*>?) {
    abstract fun isSqlCompatible(compilerOptions: C_CompilerOptions): Boolean

    open fun isAllowedForEntityAttributes(compilerOptions: C_CompilerOptions): Boolean {
        return isSqlCompatible(compilerOptions)
    }

    abstract fun toSqlValue(value: Rt_Value): Any
    abstract fun toSql(params: PreparedStatementParams, idx: Int, value: Rt_Value)
    abstract fun fromSql(row: ResultSetRow, idx: Int, nullable: Boolean): Rt_Value
    abstract fun metaName(sqlCtx: Rt_SqlContext): String
}

private class R_TypeSqlAdapter_None(private val type: R_Type): R_TypeSqlAdapter(null) {
    override fun isSqlCompatible(compilerOptions: C_CompilerOptions): Boolean = false

    override fun toSqlValue(value: Rt_Value): Any {
        throw Rt_Utils.errNotSupported("Type cannot be converted to SQL: ${type.strCode()}")
    }

    override fun toSql(params: PreparedStatementParams, idx: Int, value: Rt_Value) {
        throw Rt_Utils.errNotSupported("Type cannot be converted to SQL: ${type.strCode()}")
    }

    override fun fromSql(row: ResultSetRow, idx: Int, nullable: Boolean): Rt_Value {
        throw Rt_Utils.errNotSupported("Type cannot be converted from SQL: ${type.strCode()}")
    }

    override fun metaName(sqlCtx: Rt_SqlContext): String {
        throw Rt_Utils.errNotSupported("Type has no meta name: ${type.strCode()}")
    }
}

abstract class R_TypeSqlAdapter_Some(sqlType: DataType<*>?): R_TypeSqlAdapter(sqlType) {
    override fun isSqlCompatible(compilerOptions: C_CompilerOptions) = true

    protected fun checkSqlNull(suspect: Boolean, row: ResultSetRow, type: R_Type, nullable: Boolean): Rt_Value? {
        return if (suspect && row.wasNull()) {
            if (nullable) {
                Rt_NullValue
            } else {
                throw errSqlNull(type)
            }
        } else {
            null
        }
    }

    protected fun checkSqlNull(type: R_Type, nullable: Boolean): Rt_Value {
        return if (nullable) Rt_NullValue else throw errSqlNull(type)
    }

    private fun errSqlNull(type: R_Type): Rt_Exception {
        return Rt_Exception.common("sql_null:${type.strCode()}", "SQL value is NULL for type ${type.str()}")
    }
}

abstract class R_TypeSqlAdapter_Primitive(
    private val name: String,
    sqlType: DataType<*>
): R_TypeSqlAdapter_Some(sqlType) {
    final override fun metaName(sqlCtx: Rt_SqlContext): String = "sys:$name"
}

abstract class R_Type(
    val name: String,
    val defName: C_DefinitionName = C_DefinitionName(C_LibUtils.DEFAULT_MODULE_STR, name),
) {
    val toTextFunctionLazy = LazyString.of { "$name.to_text" }

    private val gtvConversion by lazy { createGtvConversion() }
    val sqlAdapter = createSqlAdapter()

    val libType: C_LibType by lazy {
        getLibType0()
    }

    val mType: M_Type get() = libType.mType

    private val lazyHashCode: Int by lazy {
        val h0 = hashCode0()
        Objects.hash(javaClass, h0)
    }

    protected abstract fun equals0(other: R_Type): Boolean
    protected abstract fun hashCode0(): Int

    final override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other == null || other !is R_Type) return false
        if (lazyHashCode != other.lazyHashCode) return false
        return equals0(other)
    }

    final override fun hashCode(): Int = lazyHashCode

    open fun isReference(): Boolean = false
    open fun isCacheable(): Boolean = false
    open fun defaultValue(): Rt_Value? = null
    open fun comparator(): Comparator<Rt_Value>? = null

    open fun isError(): Boolean = false
    fun isNotError() = !isError()

    protected open fun isDirectPure(): Boolean = true
    protected open fun isDirectMutable(): Boolean = false
    protected open fun isDirectVirtualable(): Boolean = true
    protected open fun isDirectMixedTuple(): Boolean = false

    fun directFlags(): R_TypeFlags {
        return R_TypeFlags(
            pure = isDirectPure(),
            mutable = isDirectMutable(),
            gtv = gtvConversion.directCompatibility(),
            virtualable = isDirectVirtualable(),
            mixedTuple = isDirectMixedTuple(),
        )
    }

    open fun completeFlags(): R_TypeFlags {
        val flags = mutableListOf(directFlags())
        for (sub in componentTypes()) {
            flags.add(sub.completeFlags())
        }
        return R_TypeFlags.combine(flags)
    }

    protected open fun createSqlAdapter(): R_TypeSqlAdapter = R_TypeSqlAdapter_None(this)

    open fun fromCli(s: String): Rt_Value = throw UnsupportedOperationException()

    fun rtToGtv(rt: Rt_Value, pretty: Boolean): Gtv = gtvConversion.rtToGtv(rt, pretty)
    fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv) = gtvConversion.gtvToRt(ctx, gtv)
    protected abstract fun createGtvConversion(): GtvRtConversion

    open fun str(): String = strCode()
    abstract fun strCode(): String

    final override fun toString(): String {
        CommonUtils.failIfUnitTest()
        return str()
    }

    open fun explicitComponentTypes(): List<R_Type> = immListOf()
    open fun componentTypes(): List<R_Type> = explicitComponentTypes()

    open fun isAssignableFrom(type: R_Type): Boolean = type == this
    protected open fun calcCommonType(other: R_Type): R_Type? = null

    open fun getTypeAdapter(sourceType: R_Type): C_TypeAdapter? {
        val assignable = isAssignableFrom(sourceType)
        return if (assignable) C_TypeAdapter_Direct else null
    }

    abstract fun toMetaGtv(): Gtv

    protected abstract fun getLibType0(): C_LibType

    companion object {
        fun commonTypeOpt(a: R_Type, b: R_Type): R_Type? {
            if (a == R_CtErrorType) {
                return b
            } else if (b == R_CtErrorType) {
                return a
            } else if (a.isError()) {
                return b
            } else if (b.isError()) {
                return a
            }

            if (a.isAssignableFrom(b)) {
                return a
            } else if (b.isAssignableFrom(a)) {
                return b
            }

            val res = a.calcCommonType(b) ?: b.calcCommonType(a)
            return res
        }
    }
}

object R_CtErrorType: R_SimpleType("<error>", C_LibUtils.defName("<error>")) {
    override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_Null
    override fun createSqlAdapter(): R_TypeSqlAdapter = R_TypeSqlAdapter_CtError
    override fun isError() = true
    override fun isAssignableFrom(type: R_Type) = true
    override fun getLibType0() = C_LibType.make(this, DocCode.raw("<error>"))
    override fun calcCommonType(other: R_Type) = other

    private object R_TypeSqlAdapter_CtError: R_TypeSqlAdapter_Some(null) {
        override fun toSqlValue(value: Rt_Value) = throw err()
        override fun toSql(params: PreparedStatementParams, idx: Int, value: Rt_Value) = throw err()
        override fun fromSql(row: ResultSetRow, idx: Int, nullable: Boolean) = throw err()
        override fun metaName(sqlCtx: Rt_SqlContext) = throw err()
        private fun err(): Rt_Exception = Rt_Utils.errNotSupported("Error")
    }
}

/** Simple type - a type that does not have inner type components. Shall have a single type instance, but may have
 * multiple value instances. */
abstract class R_SimpleType(name: String, defName: C_DefinitionName): R_Type(name, defName) {
    final override fun equals0(other: R_Type) = false
    final override fun hashCode0() = System.identityHashCode(this)
    final override fun strCode(): String = name
    final override fun toMetaGtv() = name.toGtv()
    final override fun isCacheable() = true
}

abstract class R_LibSimpleType(name: String, defName: C_DefinitionName): R_SimpleType(name, defName) {
    private val libTypeDefLazy: C_LibTypeDef by lazy {
        getLibTypeDef()
    }

    protected abstract fun getLibTypeDef(): C_LibTypeDef

    final override fun getLibType0(): C_LibType = C_LibType.make(libTypeDefLazy)
}

abstract class R_PrimitiveType(name: String): R_LibSimpleType(name, C_LibUtils.defName(name))
