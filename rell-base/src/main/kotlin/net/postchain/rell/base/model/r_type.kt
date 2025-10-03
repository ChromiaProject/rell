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
import net.postchain.rell.base.lib.R_RellErrorType
import net.postchain.rell.base.lmodel.L_TypeUtils
import net.postchain.rell.base.mtype.M_Type
import net.postchain.rell.base.mtype.M_TypeUtils
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.runtime.utils.toGtv
import net.postchain.rell.base.sql.PreparedStatementParams
import net.postchain.rell.base.sql.ResultSetRow
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.DocCode
import org.jooq.DataType

class R_GtvCompatibility(val fromGtv: Boolean, val toGtv: Boolean)

class R_TypeFlags(
    val pure: Boolean,
    val mutable: Boolean,
    val gtv: R_GtvCompatibility,
    val virtualable: Boolean,
    val mixedTuple: Boolean,
    val hasTypeVariable: Boolean,
) {
    companion object {
        fun combine(flags: Collection<R_TypeFlags>): R_TypeFlags {
            var pure = true
            var mutable = false
            var fromGtv = true
            var toGtv = true
            var virtualable = true
            var mixedTuple = false
            var hasTypeVariable = false

            for (f in flags) {
                pure = pure && f.pure
                mutable = mutable || f.mutable
                fromGtv = fromGtv && f.gtv.fromGtv
                toGtv = toGtv && f.gtv.toGtv
                virtualable = virtualable && f.virtualable
                mixedTuple = mixedTuple || f.mixedTuple
                hasTypeVariable = hasTypeVariable || f.hasTypeVariable
            }

            return R_TypeFlags(
                pure = pure,
                mutable = mutable,
                gtv = R_GtvCompatibility(fromGtv, toGtv),
                virtualable = virtualable,
                mixedTuple = mixedTuple,
                hasTypeVariable = hasTypeVariable,
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

abstract class R_TypeMeta {
    abstract fun getTypeOrNull(args: ImmList<R_Type>): R_Type?

    private class R_TypeMeta_Simple(private val rType: R_Type): R_TypeMeta() {
        override fun getTypeOrNull(args: ImmList<R_Type>): R_Type {
            checkEquals(args.size, 0)
            return rType
        }
    }

    private class R_TypeMeta_Factory(
        private val factory: (ImmList<R_Type>) -> R_Type?,
    ): R_TypeMeta() {
        override fun getTypeOrNull(args: ImmList<R_Type>) = factory(args)
    }

    companion object {
        internal fun mkSimple(type: R_Type): R_TypeMeta = R_TypeMeta_Simple(type)

        internal fun make(factory: (R_Type) -> R_Type?): R_TypeMeta {
            return R_TypeMeta_Factory { args ->
                checkEquals(args.size, 1)
                factory(args[0])
            }
        }

        internal fun make(factory: (R_Type, R_Type) -> R_Type?): R_TypeMeta {
            return R_TypeMeta_Factory { args ->
                checkEquals(args.size, 2)
                factory(args[0], args[1])
            }
        }

        internal fun make(factory: (R_Type, R_Type, R_Type) -> R_Type?): R_TypeMeta {
            return R_TypeMeta_Factory { args ->
                checkEquals(args.size, 3)
                factory(args[0], args[1], args[2])
            }
        }
    }
}

abstract class R_Type(
    val name: String,
    val defName: C_DefinitionName = C_DefinitionName(C_LibUtils.DEFAULT_MODULE_STR, name),
) {
    internal val toTextFunctionLazy = LazyString.of { "$name.to_text" }

    private val gtvConversion by lazy { createGtvConversion() }
    internal val sqlAdapter by lazy { createSqlAdapter() }

    internal val libType: C_LibType by lazy {
        getLibType0()
    }

    val mType: M_Type get() = libType.mType

    private val typeMeta: R_TypeMeta? by lazy {
        getTypeMeta0()
    }

    private val lazyHashCode: Int by lazy {
        val h0 = hashCode0()
        java.util.Objects.hash(javaClass, h0)
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

    internal open fun isReference(): Boolean = false
    internal open fun isCacheable(): Boolean = false
    internal open fun defaultValue(): Rt_Value? = null
    internal open fun comparator(): Comparator<Rt_Value>? = null

    internal open fun isError(): Boolean = false
    internal fun isNotError() = !isError()

    protected open fun isDirectPure(): Boolean = true
    protected open fun isDirectMutable(): Boolean = false
    protected open fun isDirectVirtualable(): Boolean = true
    protected open fun isDirectMixedTuple(): Boolean = false

    internal fun directFlags(): R_TypeFlags {
        return R_TypeFlags(
            pure = isDirectPure(),
            mutable = isDirectMutable(),
            gtv = gtvConversion.directCompatibility(),
            virtualable = isDirectVirtualable(),
            mixedTuple = isDirectMixedTuple(),
            hasTypeVariable = this is R_VariableType,
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

    protected open fun getTypeMeta0(): R_TypeMeta? = libType.getRTypeMeta()
    internal fun hasTypeVariable(): Boolean = completeFlags().hasTypeVariable

    internal open fun getTypeArgs(): ImmList<R_Type> {
        return M_TypeUtils.getTypeArgs(mType).values.mapNotNullToImmList {
            it.getExactType()?.let { mArgType -> L_TypeUtils.getRType(mArgType) }
        }
    }

    internal open fun replaceTypeArgs(map: ImmMap<String, R_Type>): R_Type {
        val args = getTypeArgs()
        if (args.isEmpty() || args.none { it.hasTypeVariable() }) {
            return this
        }

        val resArgs = args.mapToImmList { it.replaceTypeArgs(map) }
        val resType = typeMeta?.getTypeOrNull(resArgs)
        return resType ?: this
    }

    internal open fun explicitComponentTypes(): ImmList<R_Type> = immListOf()
    internal open fun componentTypes(): ImmList<R_Type> = explicitComponentTypes()

    internal open fun isAssignableFrom(type: R_Type): Boolean = type == this
    protected open fun calcCommonType(other: R_Type): R_Type? = null

    internal open fun getTypeAdapter(sourceType: R_Type): C_TypeAdapter? {
        val assignable = isAssignableFrom(sourceType)
        return if (assignable) C_TypeAdapter_Direct else null
    }

    abstract fun toMetaGtv(): Gtv

    protected abstract fun getLibType0(): C_LibType

    companion object {
        internal fun commonTypeOpt(a: R_Type, b: R_Type): R_Type? {
            if (a == R_CtErrorType || a == R_RellErrorType) {
                return b
            } else if (b == R_CtErrorType || b == R_RellErrorType) {
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

object R_CtErrorType: R_UniqueType("<error>", C_LibUtils.defName("<error>")) {
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

abstract class R_SimpleType(
    name: String,
    defName: C_DefinitionName = C_DefinitionName(C_LibUtils.DEFAULT_MODULE_STR, name),
): R_Type(name, defName) {
    final override fun strCode(): String = name
    final override fun isCacheable() = true

    final override fun getTypeMeta0() = R_TypeMeta.mkSimple(this)
    final override fun getTypeArgs() = immListOf<R_Type>()
}

abstract class R_CompositeType(
    name: String,
    defName: C_DefinitionName = C_DefinitionName(C_LibUtils.DEFAULT_MODULE_STR, name),
): R_Type(name, defName) {
    override fun explicitComponentTypes(): ImmList<R_Type> = getTypeArgs()
}

/** Unique type - has a single type instance, but may have multiple value instances. */
abstract class R_UniqueType(name: String, defName: C_DefinitionName): R_Type(name, defName) {
    final override fun equals0(other: R_Type) = false
    final override fun hashCode0() = System.identityHashCode(this)
    final override fun strCode(): String = name
    final override fun toMetaGtv() = name.toGtv()
    final override fun isCacheable() = true
    final override fun getTypeMeta0() = R_TypeMeta.mkSimple(this)
    final override fun getTypeArgs() = immListOf<R_Type>()
}

abstract class R_LibUniqueType(
    name: String,
    defName: C_DefinitionName = C_LibUtils.defName(name),
): R_UniqueType(name, defName) {
    private val libTypeDefLazy: C_LibTypeDef by lazy {
        getLibTypeDef()
    }

    protected abstract fun getLibTypeDef(): C_LibTypeDef

    final override fun getLibType0(): C_LibType = C_LibType.make(libTypeDefLazy)
}

abstract class R_PrimitiveType(name: String): R_LibUniqueType(name)

internal class R_VariableType(name: String): R_SimpleType(name) {
    override fun equals0(other: R_Type) = this === other
    override fun hashCode0() = System.identityHashCode(this)

    override fun createGtvConversion() = GtvRtConversion_None

    override fun toMetaGtv() = mapOf(
        "type" to "variable".toGtv(),
        "name" to name.toGtv(),
    ).toGtv()

    override fun getLibType0(): C_LibType {
        val doc = DocCode.raw(name)
        return C_LibType.make(this, doc)
    }

    override fun replaceTypeArgs(map: ImmMap<String, R_Type>): R_Type {
        return map[name] ?: this
    }
}
