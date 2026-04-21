/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.core.C_DefinitionName
import net.postchain.rell.base.compiler.base.core.C_TypeAdapter
import net.postchain.rell.base.compiler.base.core.C_TypeAdapter_Direct
import net.postchain.rell.base.compiler.base.expr.C_TypeValueMember
import net.postchain.rell.base.compiler.base.lib.C_LibType
import net.postchain.rell.base.compiler.base.lib.C_LibTypeDef
import net.postchain.rell.base.compiler.base.lib.C_LibUtils
import net.postchain.rell.base.compiler.base.lib.C_TypeStaticMember
import net.postchain.rell.base.compiler.base.utils.C_FeatureSwitch
import net.postchain.rell.base.lmodel.L_TypeUtils
import net.postchain.rell.base.model.R_LibTypeMemberRegistry.getStaticMembers
import net.postchain.rell.base.model.R_LibTypeMemberRegistry.getTypeDef
import net.postchain.rell.base.model.R_LibTypeMemberRegistry.getValueMembers
import net.postchain.rell.base.mtype.*
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.DocCode
import net.postchain.rell.base.utils.doc.DocType
import net.postchain.rell.base.utils.doc.DocUtils
import java.util.*

/** Compile-time SQL metadata — no Rt_Value references or R_TypeSqlAdapter reference. */
class R_TypeSqlInfo(
    private val sqlCompatible: (C_CompilerOptions) -> Boolean,
    private val allowedForEntityAttributes: (C_CompilerOptions) -> Boolean,
) {
    fun isSqlCompatible(compilerOptions: C_CompilerOptions): Boolean = sqlCompatible(compilerOptions)
    fun isAllowedForEntityAttributes(compilerOptions: C_CompilerOptions): Boolean = allowedForEntityAttributes(compilerOptions)

    companion object {
        val NONE = R_TypeSqlInfo(sqlCompatible = { false }, allowedForEntityAttributes = { false })
    }
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
        fun mkSimple(type: R_Type): R_TypeMeta = R_TypeMeta_Simple(type)
        fun mkFactory(factory: (ImmList<R_Type>) -> R_Type?): R_TypeMeta = R_TypeMeta_Factory(factory)

        fun make(factory: (R_Type) -> R_Type?): R_TypeMeta {
            return R_TypeMeta_Factory { args ->
                checkEquals(args.size, 1)
                factory(args[0])
            }
        }

        fun make(factory: (R_Type, R_Type) -> R_Type?): R_TypeMeta {
            return R_TypeMeta_Factory { args ->
                checkEquals(args.size, 2)
                factory(args[0], args[1])
            }
        }

        fun make(factory: (R_Type, R_Type, R_Type) -> R_Type?): R_TypeMeta {
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
    val toTextFunctionLazy = LazyString.of { "$name.to_text" }

    /** Compile-time SQL metadata. */
    val sqlInfo: R_TypeSqlInfo by lazy { computeSqlInfo() }

    val libType: C_LibType by lazy {
        getLibType0()
    }

    val mType: M_Type get() = libType.mType

    val typeMeta: R_TypeMeta? by lazy {
        getTypeMeta0()
    }

    private val lazyHashCode: Int by lazy {
        val h0 = hashCode0()
        Objects.hash(javaClass, h0)
    }

    val parentType: R_Type? by lazy {
        calcParentType()
    }

    val typeExtractors: ImmMap<String, (R_Type) -> R_Type?> by lazy {
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

    open fun isReference(): Boolean = false
    open fun isCacheable(): Boolean = false
    open fun isComparable(): Boolean = false

    open fun isAbstract(): Boolean = false
    open fun isValid(): Boolean = true
    open fun isError(): Boolean = false
    fun isNotError() = !isError()

    protected open fun isDirectPure(): Boolean = true
    protected open fun isDirectMutable(): Boolean = false
    protected open fun isDirectVirtualable(): Boolean = true
    protected open fun isDirectMixedTuple(): Boolean = false

    fun directFlags(): TypeFlags = TypeFlags(
        pure = isDirectPure(),
        mutable = isDirectMutable(),
        gtv = computeDirectGtvCompatibility(),
        virtualable = isDirectVirtualable(),
        mixedTuple = isDirectMixedTuple(),
        hasTypeVariable = this is R_VariableType,
    )

    open fun completeFlags(): TypeFlags {
        val flags = mutableListOf(directFlags())
        for (sub in componentTypes()) {
            flags.add(sub.completeFlags())
        }
        return TypeFlags.combine(flags)
    }

    open fun str(): String = strCode()
    abstract fun strCode(): String

    final override fun toString(): String {
        CommonUtils.failIfUnitTest()
        return str()
    }

    open fun getTypeMeta0(): R_TypeMeta? = libType.getRTypeMeta()
    fun hasTypeVariable(): Boolean = completeFlags().hasTypeVariable

    open fun getTypeArgs(): ImmList<R_Type> {
        return M_TypeUtils.getTypeArgs(mType).values.mapNotNullToImmList {
            it.getExactType()?.let { mArgType -> L_TypeUtils.getRTypeOrNull(mArgType) }
        }
    }

    open fun replaceTypeArgs(map: ImmMap<String, R_Type>): R_Type {
        val args = getTypeArgs()
        if (args.isEmpty() || args.none { it.hasTypeVariable() }) {
            return this
        }

        val resArgs = args.mapToImmList { it.replaceTypeArgs(map) }
        val resType = typeMeta?.getTypeOrNull(resArgs)
        return resType ?: this
    }

    open fun explicitComponentTypes(): ImmList<R_Type> = immListOf()
    open fun componentTypes(): ImmList<R_Type> = explicitComponentTypes()

    open fun isAssignableFrom(type: R_Type): Boolean = type == this
    open fun isAssignableArg(type: R_Type): Boolean = type == this
    open fun calcCommonType(other: R_Type): R_Type? = null
    open fun calcTypeExtractors(): ImmMap<String, (R_Type) -> R_Type?> = immMapOf()

    open fun calcParentType(): R_Type? =
        mType.getParentType()?.let { L_TypeUtils.getRTypeOrNull(it) } ?: R_GenericType("any")

    open fun getTypeAdapter(sourceType: R_Type): C_TypeAdapter? {
        val assignable = isAssignableFrom(sourceType)
        return if (assignable) C_TypeAdapter_Direct else null
    }

    open fun docType(): DocType {
        val s = strCode()
        return DocType.raw(s)
    }

    private fun computeDirectGtvCompatibility(): GtvCompatibility = when (this) {
        is R_BooleanType, is R_IntegerType, is R_BigIntegerType, is R_DecimalType,
        is R_TextType, is R_ByteArrayType, is R_RowidType, is R_JsonType, is R_GtvType,
        is R_EntityType, is R_EnumType, is R_NullType,
        is R_NullableType, is R_StructType, is R_TupleType,
        is R_ListType, is R_SetType, is R_MapType ->
            GtvCompatibility(fromGtv = true, toGtv = true)

        // Virtual types support fromGtv (deserialization from Merkle proof) but NOT toGtv.
        is R_VirtualListType, is R_VirtualSetType, is R_VirtualMapType,
        is R_VirtualStructType, is R_VirtualTupleType ->
            GtvCompatibility(fromGtv = true, toGtv = false)

        else -> GtvCompatibility(fromGtv = false, toGtv = false)
    }

    private fun computeSqlInfo(): R_TypeSqlInfo = when (this) {
        R_CtErrorType -> R_TypeSqlInfo(sqlCompatible = { true }, allowedForEntityAttributes = { true })

        is R_BooleanType, is R_IntegerType, is R_BigIntegerType, is R_DecimalType,
        is R_TextType, is R_ByteArrayType, is R_RowidType, is R_JsonType,
        is R_EntityType, is R_EnumType ->
            R_TypeSqlInfo(sqlCompatible = { true }, allowedForEntityAttributes = { true })

        is R_NullableType -> R_TypeSqlInfo(
            sqlCompatible = { opts ->
                NULLABLE_SQL_SWITCH.isActive(opts) && valueType.sqlInfo.isSqlCompatible(opts)
            },
            allowedForEntityAttributes = { false },
        )

        else -> R_TypeSqlInfo.NONE
    }

    abstract fun getLibType0(): C_LibType

    companion object {
        private val NULLABLE_SQL_SWITCH = C_FeatureSwitch("0.13.10")

        fun commonTypeOpt(a: R_Type, b: R_Type): R_Type? {
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
    override fun isError() = true
    override fun isAssignableFrom(type: R_Type) = true
    override fun getLibType0() = C_LibType.make(this, DocCode.raw("<error>"))
    override fun calcCommonType(other: R_Type) = other
}

abstract class R_SimpleType(
    name: String,
    defName: C_DefinitionName = C_DefinitionName(C_LibUtils.DEFAULT_MODULE_STR, name),
): R_Type(name, defName) {
    final override fun strCode(): String = name
    final override fun isCacheable() = true
    final override fun getTypeMeta0() = R_TypeMeta.mkSimple(this)
    final override fun getTypeArgs() = immListOf<R_Type>()
    override fun docType() = DocType.name(name)
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
    final override fun isCacheable() = true
    final override fun getTypeMeta0() = R_TypeMeta.mkSimple(this)
    final override fun getTypeArgs() = immListOf<R_Type>()
    override fun docType() = DocType.name(name)
}

abstract class R_LibUniqueType(
    name: String,
    defName: C_DefinitionName = C_LibUtils.defName(name),
): R_UniqueType(name, defName) {
    override fun getLibType0(): C_LibType {
        val def = checkNotNull(libTypeDefRegistry[this]) {
            "C_LibTypeDef not registered for type: $name. Call registerLibTypeDef() during stdlib init."
        }
        return C_LibType.make(def)
    }

    companion object {
        private val libTypeDefRegistry = java.util.concurrent.ConcurrentHashMap<R_LibUniqueType, C_LibTypeDef>()
        val genericLibTypeDefRegistry = java.util.concurrent.ConcurrentHashMap<kotlin.reflect.KClass<*>, C_LibTypeDef>()

        fun registerLibTypeDef(type: R_LibUniqueType, def: C_LibTypeDef) {
            libTypeDefRegistry[type] = def
        }

        fun registerGenericLibTypeDef(typeClass: kotlin.reflect.KClass<*>, def: C_LibTypeDef) {
            genericLibTypeDefRegistry[typeClass] = def
        }
    }
}

abstract class R_PrimitiveType(name: String): R_LibUniqueType(name)

/**
 * Registry for late-bound lib type member providers and type defs.
 * Model types call [getValueMembers] / [getStaticMembers] / [getTypeDef] in their [R_Type.getLibType0] implementations.
 * The lib/ side registers the actual providers during stdlib init.
 */
object R_LibTypeMemberRegistry {
    private val valueMembers = java.util.concurrent.ConcurrentHashMap<kotlin.reflect.KClass<*>, (Any) -> Lazy<ImmList<C_TypeValueMember>>>()
    private val staticMembers = java.util.concurrent.ConcurrentHashMap<kotlin.reflect.KClass<*>, (Any) -> ImmList<C_TypeStaticMember>>()
    private val typeDefs = java.util.concurrent.ConcurrentHashMap<String, C_LibTypeDef>()

    fun <T: R_Type> registerValueMembers(typeClass: kotlin.reflect.KClass<T>, provider: (T) -> Lazy<ImmList<C_TypeValueMember>>) {
        @Suppress("UNCHECKED_CAST")
        valueMembers[typeClass] = provider as (Any) -> Lazy<ImmList<C_TypeValueMember>>
    }

    fun <T: R_Type> registerStaticMembers(typeClass: kotlin.reflect.KClass<T>, provider: (T) -> ImmList<C_TypeStaticMember>) {
        @Suppress("UNCHECKED_CAST")
        staticMembers[typeClass] = provider as (Any) -> ImmList<C_TypeStaticMember>
    }

    fun registerTypeDef(key: String, def: C_LibTypeDef) {
        typeDefs[key] = def
    }

    fun getValueMembers(type: R_Type): Lazy<ImmList<C_TypeValueMember>> {
        val provider = valueMembers[type::class] ?: return lazyOf(immListOf())
        return provider(type)
    }

    fun getStaticMembers(type: R_Type): ImmList<C_TypeStaticMember> {
        val provider = staticMembers[type::class] ?: return immListOf()
        return provider(type)
    }

    fun getTypeDef(key: String): C_LibTypeDef {
        return checkNotNull(typeDefs[key]) {
            "C_LibTypeDef not registered for key: $key. Call registerTypeDef() during stdlib init."
        }
    }
}

class R_SubType(val valueType: R_Type): R_CompositeType("-${valueType.name}") {
    override fun equals0(other: R_Type) = other is R_SubType && valueType == other.valueType
    override fun hashCode0() = valueType.hashCode()
    override fun calcTypeExtractors() = valueType.typeExtractors
    override fun strCode() = name
    override fun str() = name
    override fun getLibType0() = valueType.libType
    override fun isAssignableArg(type: R_Type) = valueType.isAssignableFrom(type)

}

abstract class R_BaseGenericType(
    val typeName: String,
    val args: ImmList<R_Type>,
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

abstract class R_LibGenericType(
    typeName: String,
    args: ImmList<R_Type>,
): R_BaseGenericType(typeName, args)

class R_GenericType(
    typeName: String,
    args: ImmList<R_Type> = immListOf(),
    private val isAbstract: Boolean = false,
    private val parentMType: M_Type? = null,
    private val addon: M_GenericTypeAddon? = null,
): R_BaseGenericType(typeName, args) {
    override fun equals0(other: R_Type) = other is R_GenericType && typeName == other.typeName && args == other.args
    override fun hashCode0() = Objects.hash(typeName, args)
    override fun strCode(): String = name
    override fun getTypeMeta0() = R_TypeMeta.mkFactory { R_GenericType(typeName, it) }
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

    override fun calcParentType(): R_Type? = when {
        parentMType != null -> L_TypeUtils.getRTypeOrNull(parentMType)
        typeName == "anything" -> null
        typeName == "any" -> R_GenericType("anything")
        else -> R_GenericType("any")
    }

    override fun calcTypeExtractors(): ImmMap<String, (R_Type) -> R_Type?> {
        return args.flatMapIndexed { i, arg ->
                arg.typeExtractors.map {
                    it.key to { srcType: R_Type ->
                        if (srcType is R_GenericType && srcType.typeName == typeName && srcType.args.size == args.size) {
                            val realSrcArg = when (val srcArg = srcType.args[i]) {
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

    override fun docType(): DocType = when {
        args.isEmpty() -> DocType.name(typeName)
        else -> DocUtils.docTypeGeneric0(typeName, args)
    }
}

class R_VariableType(name: String): R_SimpleType(name) {
    override fun equals0(other: R_Type) = this === other
    override fun hashCode0() = System.identityHashCode(this)

    override fun calcTypeExtractors() = immMapOf(name to { type: R_Type -> type })

    override fun getLibType0(): C_LibType = C_LibType.make(M_Types.param(M_TypeParam(name)))

    override fun replaceTypeArgs(map: ImmMap<String, R_Type>): R_Type = map[name] ?: this
}
