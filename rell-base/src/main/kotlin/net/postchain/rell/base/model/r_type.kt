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
import net.postchain.rell.base.mtype.*
import net.postchain.rell.base.mtype.M_GenericTypeAddon
import net.postchain.rell.base.mtype.M_Types
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.runtime.utils.toGtv
import net.postchain.rell.base.sql.PreparedStatementParams
import net.postchain.rell.base.sql.ResultSetRow
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.DocCode
import net.postchain.rell.base.utils.doc.DocType
import net.postchain.rell.base.utils.doc.DocUtils
import org.jooq.DataType
import java.util.*
import kotlin.reflect.KType

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

internal interface R_TypeNativeConversion {
    val nativeTypes: ImmSet<KType>
    fun rtToNative(value: Rt_Value): Any?
    fun nativeToRt(value: Any?): Rt_Value
}

internal object R_TypeNativeConversion_Null: R_TypeNativeConversion {
    override val nativeTypes = immSetOf<KType>()
    override fun rtToNative(value: Rt_Value) = null
    override fun nativeToRt(value: Any?) = Rt_NullValue
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
        internal fun mkFactory(factory: (ImmList<R_Type>) -> R_Type?): R_TypeMeta = R_TypeMeta_Factory(factory)

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

abstract class R_Type internal constructor(
    val name: String,
    val defName: C_DefinitionName = C_DefinitionName(C_LibUtils.DEFAULT_MODULE_STR, name),
) {
    internal val toTextFunctionLazy = LazyString.of { "$name.to_text" }

    private val gtvConversion by lazy { createGtvConversion() }
    internal val sqlAdapter by lazy { createSqlAdapter() }
    internal val nativeConversion: R_TypeNativeConversion? by lazy { createNativeConversion() }

    internal val libType: C_LibType by lazy {
        getLibType0()
    }

    val mType: M_Type get() = libType.mType

    internal val typeMeta: R_TypeMeta? by lazy {
        getTypeMeta0()
    }

    private val lazyHashCode: Int by lazy {
        val h0 = hashCode0()
        Objects.hash(javaClass, h0)
    }

    internal val parentType: R_Type? by lazy {
        calcParentType()
    }

    internal val typeExtractors: ImmMap<String, (R_Type) -> R_Type?> by lazy {
        calcTypeExtractors()
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

    internal open fun isAbstract(): Boolean = false
    internal open fun isValid(): Boolean = true
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
    internal open fun createNativeConversion(): R_TypeNativeConversion? = null

    open fun fromCli(s: String): Rt_Value = throw UnsupportedOperationException()

    fun rtToGtv(rt: Rt_Value, pretty: Boolean): Gtv = gtvConversion.rtToGtv(rt, pretty)
    fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv) = gtvConversion.gtvToRt(ctx, gtv)
    internal abstract fun createGtvConversion(): GtvRtConversion

    open fun str(): String = strCode()
    abstract fun strCode(): String

    final override fun toString(): String {
        CommonUtils.failIfUnitTest()
        return str()
    }

    internal open fun getTypeMeta0(): R_TypeMeta? = libType.getRTypeMeta()
    internal fun hasTypeVariable(): Boolean = completeFlags().hasTypeVariable

    internal open fun getTypeArgs(): ImmList<R_Type> {
        return M_TypeUtils.getTypeArgs(mType).values.mapNotNullToImmList {
            it.getExactType()?.let { mArgType -> L_TypeUtils.getRTypeOrNull(mArgType) }
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
    internal open fun isAssignableArg(type: R_Type): Boolean = type == this
    internal open fun calcCommonType(other: R_Type): R_Type? = null
    internal open fun calcTypeExtractors(): ImmMap<String, (R_Type) -> R_Type?> = immMapOf()

    internal open fun calcParentType(): R_Type? {
        return mType.getParentType()?.let { L_TypeUtils.getRTypeOrNull(it) } ?: R_GenericType("any")
    }

    internal open fun getTypeAdapter(sourceType: R_Type): C_TypeAdapter? {
        val assignable = isAssignableFrom(sourceType)
        return if (assignable) C_TypeAdapter_Direct else null
    }

    internal open fun docType(): DocType {
        val s = strCode()
        return DocType.raw(s)
    }

    abstract fun toMetaGtv(): Gtv

    internal abstract fun getLibType0(): C_LibType

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

abstract class R_SimpleType internal constructor(
    name: String,
    defName: C_DefinitionName = C_DefinitionName(C_LibUtils.DEFAULT_MODULE_STR, name),
): R_Type(name, defName) {
    final override fun strCode(): String = name
    final override fun isCacheable() = true
    final override fun getTypeMeta0() = R_TypeMeta.mkSimple(this)
    final override fun getTypeArgs() = immListOf<R_Type>()
    override fun docType() = DocType.name(name)
}

abstract class R_CompositeType internal constructor(
    name: String,
    defName: C_DefinitionName = C_DefinitionName(C_LibUtils.DEFAULT_MODULE_STR, name),
): R_Type(name, defName) {
    override fun explicitComponentTypes(): ImmList<R_Type> = getTypeArgs()
}

/** Unique type - has a single type instance, but may have multiple value instances. */
abstract class R_UniqueType internal constructor(name: String, defName: C_DefinitionName): R_Type(name, defName) {
    final override fun equals0(other: R_Type) = false
    final override fun hashCode0() = System.identityHashCode(this)
    final override fun strCode(): String = name
    final override fun toMetaGtv() = name.toGtv()
    final override fun isCacheable() = true
    final override fun getTypeMeta0() = R_TypeMeta.mkSimple(this)
    final override fun getTypeArgs() = immListOf<R_Type>()
    override fun docType() = DocType.name(name)
}

abstract class R_LibUniqueType internal constructor(
    name: String,
    defName: C_DefinitionName = C_LibUtils.defName(name),
): R_UniqueType(name, defName) {
    private val libTypeDefLazy: C_LibTypeDef by lazy {
        getLibTypeDef()
    }

    internal abstract fun getLibTypeDef(): C_LibTypeDef

    override fun getLibType0(): C_LibType = C_LibType.make(libTypeDefLazy)
    override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_None
}

abstract class R_PrimitiveType(name: String): R_LibUniqueType(name)

internal class R_SubType(internal val valueType: R_Type): R_CompositeType("-${valueType.name}") {
    override fun equals0(other: R_Type) = other is R_SubType && valueType == other.valueType
    override fun hashCode0() = valueType.hashCode()
    override fun createGtvConversion() = GtvRtConversion_None
    override fun calcTypeExtractors() = valueType.typeExtractors
    override fun strCode() = name
    override fun str() = name
    override fun getLibType0() = valueType.libType
    override fun isAssignableArg(type: R_Type) = valueType.isAssignableFrom(type)

    override fun toMetaGtv() = mapOf(
        "type" to "subtype".toGtv(),
        "value" to valueType.toMetaGtv(),
    ).toGtv()
}

abstract class R_BaseGenericType internal constructor(
    internal val typeName: String,
    internal val args: ImmList<R_Type>,
): R_CompositeType(calcName(typeName, args)) {
    companion object {
        private fun calcName(typeName: String, args: ImmList<R_Type>): String {
            return when {
                args.isEmpty() -> typeName
                else -> "$typeName<${args.joinToString(","){it.strCode()}}>"
            }
        }
    }
}

abstract class R_LibGenericType internal constructor(
    typeName: String,
    args: ImmList<R_Type>,
): R_BaseGenericType(typeName, args)

internal class R_GenericType(
    typeName: String,
    args: ImmList<R_Type> = immListOf(),
    private val isAbstract: Boolean = false,
    private val parentMType: M_Type? = null,
    private val addon: M_GenericTypeAddon? = null,
): R_BaseGenericType(typeName, args) {
    override fun equals0(other: R_Type) = other is R_GenericType && typeName == other.typeName && args == other.args
    override fun hashCode0() = Objects.hash(typeName, args)
    override fun strCode(): String = name
    override fun toMetaGtv() = name.toGtv()
    override fun getTypeMeta0() = R_TypeMeta.mkFactory { R_GenericType(typeName, it) }
    override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_None
    override fun getLibType0() = C_LibType.make(this, DocCode.raw(name), name = typeName)

    override fun isAbstract() = isAbstract
    override fun isValid() = false

    override fun isAssignableFrom(type: R_Type): Boolean {
        var curType: R_Type? = type
        while (curType != null) {
            val curType2 = curType
            if (curType2 is R_GenericType && typeName == curType2.typeName && args.size == curType2.args.size) {
                if (args.withIndex().all { (i, arg) -> isAssignableArg(arg, curType2.args[i]) }) {
                    return true
                }
            }
            curType = curType.parentType
        }

        if (super.isAssignableFrom(type)) {
            return true
        }

        if (addon != null && addon.isSpecialSuperTypeOf(type.mType)) {
            return true
        }

        return false
    }

    private fun isAssignableArg(argType: R_Type, srcType: R_Type): Boolean {
        val realSrcType = when (srcType) {
            is R_SubType -> srcType.valueType
            else -> srcType
        }
        return argType.isAssignableArg(realSrcType)
    }

    override fun calcParentType(): R_Type? {
        return when {
            parentMType != null -> L_TypeUtils.getRTypeOrNull(parentMType)
            typeName == "anything" -> null
            typeName == "any" -> R_GenericType("anything")
            else -> R_GenericType("any")
        }
    }

    override fun calcTypeExtractors(): ImmMap<String, (R_Type) -> R_Type?> {
        return args.flatMapIndexed { i, arg ->
                arg.typeExtractors.map {
                    it.key to { srcType: R_Type ->
                        if (srcType is R_GenericType && srcType.typeName == typeName && srcType.args.size == args.size) {
                            val srcArg = srcType.args[i]
                            val realSrcArg = when (srcArg) {
                                is R_SubType -> srcArg.valueType
                                else -> srcArg
                            }
                            it.value(realSrcArg)
                        } else null
                    }
                }
            }
            .toImmMap()
    }

    override fun docType(): DocType {
        return when {
            args.isEmpty() -> DocType.name(typeName)
            else -> DocUtils.docTypeGeneric0(typeName, args)
        }
    }
}

internal class R_VariableType(name: String): R_SimpleType(name) {
    override fun equals0(other: R_Type) = this === other
    override fun hashCode0() = System.identityHashCode(this)

    override fun createGtvConversion() = GtvRtConversion_None
    override fun calcTypeExtractors() = immMapOf(name to { type: R_Type -> type })

    override fun toMetaGtv() = mapOf(
        "type" to "variable".toGtv(),
        "name" to name.toGtv(),
    ).toGtv()

    override fun getLibType0(): C_LibType {
        return C_LibType.make(M_Types.param(M_TypeParam(name)))
    }

    override fun replaceTypeArgs(map: ImmMap<String, R_Type>): R_Type {
        return map[name] ?: this
    }
}
