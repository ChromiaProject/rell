/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvNull
import net.postchain.rell.base.compiler.ast.S_VirtualType
import net.postchain.rell.base.compiler.base.core.*
import net.postchain.rell.base.compiler.base.def.C_GlobalFunction
import net.postchain.rell.base.compiler.base.def.C_StructGlobalFunction
import net.postchain.rell.base.compiler.base.fn.C_FunctionCallParameters
import net.postchain.rell.base.compiler.base.lib.C_LibType
import net.postchain.rell.base.compiler.base.lib.C_LibTypeDef
import net.postchain.rell.base.compiler.base.lib.C_LibUtils
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lib.type.*
import net.postchain.rell.base.mtype.M_TupleTypeUtils
import net.postchain.rell.base.mtype.M_Type
import net.postchain.rell.base.mtype.M_Types
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.*
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.DocCode
import org.jooq.DataType
import org.jooq.SQLDialect
import org.jooq.impl.DefaultDataType
import org.jooq.impl.SQLDataType
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.*

class R_GtvCompatibility(val fromGtv: Boolean, val toGtv: Boolean)

class R_TypeFlags(
        val mutable: Boolean,
        val gtv: R_GtvCompatibility,
        val virtualable: Boolean,
        val pure: Boolean
) {
    companion object {
        fun combine(flags: Collection<R_TypeFlags>): R_TypeFlags {
            var mutable = false
            var fromGtv = true
            var toGtv = true
            var virtualable = true
            var pure = true

            for (f in flags) {
                mutable = mutable || f.mutable
                fromGtv = fromGtv && f.gtv.fromGtv
                toGtv = toGtv && f.gtv.toGtv
                virtualable = virtualable && f.virtualable
                pure = pure && f.pure
            }

            return R_TypeFlags(
                    mutable = mutable,
                    gtv = R_GtvCompatibility(fromGtv, toGtv),
                    virtualable = virtualable,
                    pure = pure
            )
        }
    }
}

sealed class R_TypeSqlAdapter(val sqlType: DataType<*>?) {
    abstract fun isSqlCompatible(): Boolean
    abstract fun toSqlValue(value: Rt_Value): Any
    abstract fun toSql(stmt: PreparedStatement, idx: Int, value: Rt_Value)
    abstract fun fromSql(rs: ResultSet, idx: Int, nullable: Boolean): Rt_Value
    abstract fun metaName(sqlCtx: Rt_SqlContext): String
}

private class R_TypeSqlAdapter_None(private val type: R_Type): R_TypeSqlAdapter(null) {
    override fun isSqlCompatible(): Boolean = false

    override fun toSqlValue(value: Rt_Value): Any {
        throw Rt_Utils.errNotSupported("Type cannot be converted to SQL: ${type.strCode()}")
    }

    override fun toSql(stmt: PreparedStatement, idx: Int, value: Rt_Value) {
        throw Rt_Utils.errNotSupported("Type cannot be converted to SQL: ${type.strCode()}")
    }

    override fun fromSql(rs: ResultSet, idx: Int, nullable: Boolean): Rt_Value {
        throw Rt_Utils.errNotSupported("Type cannot be converted from SQL: ${type.strCode()}")
    }

    override fun metaName(sqlCtx: Rt_SqlContext): String {
        throw Rt_Utils.errNotSupported("Type has no meta name: ${type.strCode()}")
    }
}

abstract class R_TypeSqlAdapter_Some(sqlType: DataType<*>?): R_TypeSqlAdapter(sqlType) {
    override fun isSqlCompatible() = true

    protected fun checkSqlNull(suspect: Boolean, rs: ResultSet, type: R_Type, nullable: Boolean): Rt_Value? {
        return if (suspect && rs.wasNull()) {
            if (nullable) {
                Rt_NullValue
            } else {
                throw errSqlNull(type)
            }
        } else {
            null
        }
    }

    protected fun checkSqlNull(value: Any?, type: R_Type, nullable: Boolean): Rt_Value? {
        if (value == null) {
            if (nullable) {
                return Rt_NullValue
            } else {
                throw errSqlNull(type)
            }
        } else {
            return null
        }
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

    protected open fun isDirectMutable(): Boolean = false
    protected open fun isDirectVirtualable(): Boolean = true
    protected open fun isDirectPure(): Boolean = true

    fun directFlags(): R_TypeFlags {
        val gtvConv = gtvConversion
        return R_TypeFlags(
                mutable = isDirectMutable(),
                gtv = gtvConv.directCompatibility(),
                virtualable = isDirectVirtualable(),
                pure = isDirectPure()
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

    open fun componentTypes(): List<R_Type> = listOf()

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
        override fun toSqlValue(value: Rt_Value) = throw Rt_Utils.errNotSupported("Error")
        override fun toSql(stmt: PreparedStatement, idx: Int, value: Rt_Value) = throw Rt_Utils.errNotSupported("Error")
        override fun fromSql(rs: ResultSet, idx: Int, nullable: Boolean) = throw Rt_Utils.errNotSupported("Error")
        override fun metaName(sqlCtx: Rt_SqlContext) = throw Rt_Utils.errNotSupported("Error")
    }
}

/** Simple type - a type that does not have inner type components. Shall have a single type instance, but may have
 * multiple value instances. */
sealed class R_SimpleType(name: String, defName: C_DefinitionName): R_Type(name, defName) {
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

private val GTX_SIGNER_SQL_DATA_TYPE = DefaultDataType(null as SQLDialect?, ByteArray::class.java, "gtx_signer")

object R_NullType: R_SimpleType("null", C_LibUtils.defName("null")) {
    override fun defaultValue() = Rt_NullValue
    override fun comparator() = Rt_Comparator.create { 0 }
    override fun calcCommonType(other: R_Type): R_Type = R_NullableType(other)
    override fun createGtvConversion() = GtvRtConversion_Null
    override fun getLibType0() = C_LibType.make(M_Types.NULL)
}

class R_EntityType(val rEntity: R_EntityDefinition): R_Type(rEntity.appLevelName, rEntity.cDefName) {
    init {
        checkEquals(rEntity.type, null) // during initialization
    }

    override fun equals0(other: R_Type): Boolean = other is R_EntityType && other.rEntity == rEntity
    override fun hashCode0(): Int = rEntity.hashCode()

    override fun comparator() = Rt_Comparator.create { it.asObjectId() }
    override fun fromCli(s: String): Rt_Value = Rt_EntityValue(this, s.toLong())
    override fun strCode(): String = name

    override fun isDirectPure() = false
    override fun isCacheable() = true

    override fun createGtvConversion() = GtvRtConversion_Entity(this)
    override fun createSqlAdapter(): R_TypeSqlAdapter = R_TypeSqlAdapter_Entity(this)

    override fun toMetaGtv() = rEntity.appLevelName.toGtv()

    override fun getLibType0() = C_LibType.make(
        this,
        DocCode.link(rEntity.moduleLevelName),
        valueMembers = lazy { Lib_Type_Entity.getValueMembers(this) },
    )

    private class R_TypeSqlAdapter_Entity(private val type: R_EntityType): R_TypeSqlAdapter_Some(SQLDataType.BIGINT) {
        override fun toSqlValue(value: Rt_Value) = value.asObjectId()
        override fun toSql(stmt: PreparedStatement, idx: Int, value: Rt_Value) = stmt.setLong(idx, value.asObjectId())

        override fun fromSql(rs: ResultSet, idx: Int, nullable: Boolean): Rt_Value {
            val v = rs.getLong(idx)
            return checkSqlNull(v == 0L, rs, type, nullable) ?: Rt_EntityValue(type, v)
        }

        override fun metaName(sqlCtx: Rt_SqlContext): String {
            val rEntity = type.rEntity
            val chain = sqlCtx.chainMapping(rEntity.external?.chain)
            val metaName = rEntity.metaName
            return "class:${chain.chainId}:$metaName"
        }
    }
}

class R_ObjectType(val rObject: R_ObjectDefinition): R_Type(rObject.appLevelName, rObject.cDefName) {
    init {
        checkEquals(rObject.type, null) // during initialization
    }

    override fun equals0(other: R_Type): Boolean = other is R_ObjectType && other.rObject == rObject
    override fun hashCode0(): Int = rObject.hashCode()

    override fun isDirectVirtualable() = false
    override fun isDirectPure() = false
    override fun isCacheable() = true
    override fun createGtvConversion() = GtvRtConversion_None
    override fun strCode(): String = name
    override fun toMetaGtv() = rObject.appLevelName.toGtv()

    override fun getLibType0() = C_LibType.make(
        this,
        DocCode.link(rObject.moduleLevelName),
        valueMembers = lazy { Lib_Type_Object.getMemberValues(this) },
    )
}

class R_StructType(val struct: R_Struct): R_Type(struct.name) {
    override fun equals0(other: R_Type) = false
    override fun hashCode0() = System.identityHashCode(this)

    override fun isReference() = true
    override fun isDirectMutable() = struct.isDirectlyMutable()
    override fun isDirectPure() = true
    override fun isCacheable() = true
    override fun completeFlags() = struct.flags.typeFlags

    override fun componentTypes() = struct.attributesList.map { it.type }.toList()
    override fun createGtvConversion() = GtvRtConversion_Struct(struct)

    override fun strCode(): String = name
    override fun toMetaGtv() = struct.typeMetaGtv

    override fun getLibType0(): C_LibType {
        val constructorFn: C_GlobalFunction = C_StructGlobalFunction(struct)
        val valueMembers = lazy { Lib_Type_Struct.getValueMembers(struct) }

        val ms = struct.mirrorStructs
        return if (ms != null) {
            val typeDef = if (struct == ms.immutable) Lib_Rell.IMMUTABLE_MIRROR_STRUCT else Lib_Rell.MUTABLE_MIRROR_STRUCT
            C_LibType.make(typeDef, ms.innerType, constructorFn = constructorFn, valueMembers = valueMembers)
        } else {
            C_LibType.make(this, DocCode.link(struct.name), constructorFn = constructorFn, valueMembers = valueMembers)
        }
    }
}

class R_EnumType(val enum: R_EnumDefinition): R_Type(enum.appLevelName, enum.cDefName) {
    init {
        checkEquals(enum.type, null) // during initialization
    }

    val values: List<Rt_Value> = enum.attrs.map { Rt_EnumValue(this, it) }.toImmList()
    val valuesSet: Set<Rt_Value> = values.toImmSet()

    fun getValueOrNull(index: Int): Rt_Value? {
        return values.getOrNull(index)
    }

    fun getValue(attr: R_EnumAttr): Rt_Value {
        val i = attr.value
        check(enum.attrs[i] === attr)
        return values[i]
    }

    override fun equals0(other: R_Type) = false
    override fun hashCode0() = System.identityHashCode(this)

    override fun comparator() = Rt_Comparator.create { it.asEnum().value }

    override fun fromCli(s: String): Rt_Value {
        val attr = enum.attr(s)
        requireNotNull(attr) { "$name: $s" }
        return values[attr.value]
    }

    override fun isDirectPure() = true
    override fun isCacheable() = true

    override fun createGtvConversion() = GtvRtConversion_Enum(enum)
    override fun createSqlAdapter(): R_TypeSqlAdapter = R_TypeSqlAdapter_Enum(this)

    override fun strCode() = name
    override fun toMetaGtv() = enum.appLevelName.toGtv()

    override fun getLibType0() = C_LibType.make(
        this,
        DocCode.link(enum.moduleLevelName),
        staticMembers = Lib_Type_Enum.getStaticMembers(this),
    )

    private class R_TypeSqlAdapter_Enum(private val type: R_EnumType): R_TypeSqlAdapter_Some(SQLDataType.INTEGER) {
        override fun toSqlValue(value: Rt_Value) = value.asEnum().value
        override fun toSql(stmt: PreparedStatement, idx: Int, value: Rt_Value) = stmt.setInt(idx, value.asEnum().value)

        override fun fromSql(rs: ResultSet, idx: Int, nullable: Boolean): Rt_Value {
            val v = rs.getInt(idx)
            val res = checkSqlNull(v == 0, rs, type, nullable)
            return if (res != null) res else {
                val value = type.getValueOrNull(v)
                requireNotNull(value) { "$type: $v" }
            }
        }

        override fun metaName(sqlCtx: Rt_SqlContext): String {
            return "enum:${type.name}"
        }
    }

    private class Rt_EnumValue(private val type: R_EnumType, private val attr: R_EnumAttr): Rt_Value() {
        override val valueType = Rt_CoreValueTypes.ENUM.type()

        override fun type() = type
        override fun asEnum() = attr
        override fun toFormatArg() = attr.name
        override fun equals(other: Any?) = other is Rt_EnumValue && attr == other.attr
        override fun hashCode() = type.hashCode() * 31 + attr.value

        override fun str() = attr.name
        override fun strCode(showTupleFieldNames: Boolean) = "${type.name}[${attr.name}]"
    }
}

class R_NullableType(val valueType: R_Type): R_Type(calcName(valueType)) {
    init {
        check(valueType != R_NullType)
        check(valueType !is R_NullableType)
    }

    override fun equals0(other: R_Type) = other is R_NullableType && valueType == other.valueType
    override fun hashCode0() = valueType.hashCode()
    override fun componentTypes() = listOf(valueType)

    override fun isReference() = valueType.isReference()
    override fun isError() = valueType.isError()
    override fun isDirectMutable() = false
    override fun isDirectPure() = true

    override fun defaultValue() = Rt_NullValue
    override fun comparator() = valueType.comparator()
    override fun fromCli(s: String): Rt_Value = if (s == "null") Rt_NullValue else valueType.fromCli(s)
    override fun strCode() = name
    override fun getLibType0() = C_LibType.make(M_Types.nullable(valueType.mType))

    override fun isAssignableFrom(type: R_Type): Boolean {
        return type == this
                || type == R_NullType
                || (type is R_NullableType && valueType.isAssignableFrom(type.valueType))
                || valueType.isAssignableFrom(type)
    }

    override fun getTypeAdapter(sourceType: R_Type): C_TypeAdapter? {
        var adapter = super.getTypeAdapter(sourceType)
        if (adapter != null) {
            return adapter
        }

        if (sourceType is R_NullableType) {
            adapter = valueType.getTypeAdapter(sourceType.valueType)
            return if (adapter == null) null else C_TypeAdapter_Nullable(this, adapter)
        } else {
            return valueType.getTypeAdapter(sourceType)
        }
    }

    override fun createGtvConversion() = GtvRtConversion_Nullable(this)
    override fun createSqlAdapter(): R_TypeSqlAdapter = R_TypeSqlAdapter_Nullable()

    override fun toMetaGtv() = mapOf(
            "type" to "nullable".toGtv(),
            "value" to valueType.toMetaGtv()
    ).toGtv()

    companion object {
        private fun calcName(valueType: R_Type): String {
            return when (valueType) {
                is R_FunctionType -> "(${valueType.name})?"
                else -> "${valueType.name}?"
            }
        }
    }

    private inner class R_TypeSqlAdapter_Nullable: R_TypeSqlAdapter_Some(null) {
        override fun isSqlCompatible() = false
        override fun metaName(sqlCtx: Rt_SqlContext) = throw Rt_Utils.errNotSupported("Nullable entity attributes are not supported")

        override fun toSqlValue(value: Rt_Value) = valueType.sqlAdapter.toSqlValue(value)

        override fun toSql(stmt: PreparedStatement, idx: Int, value: Rt_Value) {
            stmt.setBoolean(idx, value.asBoolean())
        }

        override fun fromSql(rs: ResultSet, idx: Int, nullable: Boolean): Rt_Value {
            return valueType.sqlAdapter.fromSql(rs, idx, true)
        }
    }
}

class R_TupleField(val name: R_IdeName?, val type: R_Type) {
    fun str(): String = strCode()

    fun strCode(): String {
        return if (name != null) "${name}:${type.strCode()}" else type.strCode()
    }

    override fun toString(): String {
        CommonUtils.failIfUnitTest()
        return str()
    }

    override fun equals(other: Any?): Boolean = other === this || (other is R_TupleField && name == other.name && type == other.type)
    override fun hashCode() = Objects.hash(name, type)

    fun toMetaGtv() = mapOf(
            "name" to (name?.rName?.str?.toGtv() ?: GtvNull),
            "type" to type.toMetaGtv()
    ).toGtv()
}

class R_TupleType(fields: List<R_TupleField>): R_Type(calcName(fields)) {
    val fields = fields.toImmList()
    val virtualType = R_VirtualTupleType(this)

    init {
        check(this.fields.isNotEmpty())
    }

    private val isError = fields.any { it.type.isError() }

    override fun equals0(other: R_Type): Boolean = other is R_TupleType && fields == other.fields
    override fun hashCode0() = fields.hashCode()

    override fun isReference() = true
    override fun isError() = isError
    override fun isDirectMutable() = false
    override fun isDirectPure() = true

    override fun strCode() = name

    override fun getLibType0(): C_LibType {
        val fieldTypes = fields.map { it.type.mType }
        val fieldNames = M_TupleTypeUtils.makeNames(fields) { it.name?.str }
        val mType = M_Types.tuple(fieldTypes, fieldNames)
        return C_LibType.make(
            mType,
            valueMembers = lazy { Lib_Type_Tuple.getValueMembers(this) },
        )
    }

    override fun componentTypes() = fields.map { it.type }.toList()

    override fun isAssignableFrom(type: R_Type): Boolean {
        if (type !is R_TupleType) return false
        if (fields.size != type.fields.size) return false

        for (i in fields.indices) {
            val field = fields[i]
            val otherField = type.fields[i]
            if (field.name != otherField.name) return false
            if (!field.type.isAssignableFrom(otherField.type)) return false
        }

        return true
    }

    override fun calcCommonType(other: R_Type): R_Type? {
        if (other !is R_TupleType) return null
        if (fields.size != other.fields.size) return null

        val resFields = fields.mapIndexed { i, field ->
            val otherField = other.fields[i]
            if (field.name != otherField.name) return null

            val type = commonTypeOpt(field.type, otherField.type)
            if (type == null) return null

            when {
                type == field.type -> field
                type == otherField.type -> otherField
                else -> R_TupleField(field.name, type)
            }
        }

        return R_TupleType(resFields)
    }

    override fun createGtvConversion() = GtvRtConversion_Tuple(this)

    override fun comparator(): Comparator<Rt_Value>? {
        val fieldComparators = mutableListOf<Comparator<Rt_Value>>()
        for (field in fields) {
            val comparator = field.type.comparator()
            comparator ?: return null
            fieldComparators.add(comparator)
        }
        return Rt_TupleComparator(fieldComparators)
    }

    override fun toMetaGtv() = mapOf(
            "type" to "tuple".toGtv(),
            "fields" to fields.map { it.toMetaGtv() }.toGtv()
    ).toGtv()

    companion object {
        private fun calcName(fields: List<R_TupleField>): String {
            val fieldsStr = fields.joinToString(",") { it.strCode() }
            val comma = if (fields.size == 1 && fields[0].name == null) "," else ""
            return "($fieldsStr$comma)"
        }

        fun create(vararg fields: R_Type): R_TupleType {
            return create(fields.toList())
        }

        fun create(fields: List<R_Type>): R_TupleType {
            val fieldsList = fields.map { R_TupleField(null, it) }
            return R_TupleType(fieldsList)
        }

        fun createNamed(vararg fields: Pair<String?, R_Type>): R_TupleType {
            val fieldsList = fields.map {
                val name = it.first?.let { s -> R_IdeName(R_Name.of(s), C_IdeSymbolInfo.MEM_TUPLE_ATTR) }
                R_TupleField(name, it.second)
            }
            return R_TupleType(fieldsList)
        }
    }
}

sealed class R_VirtualType(private val baseInnerType: R_Type): R_Type("virtual<${baseInnerType.name}>") {
    private val isError = baseInnerType.isError()

    final override fun isReference() = true
    final override fun isError() = isError
    final override fun strCode() = name
    final override fun isDirectPure() = false    // Maybe it's actually pure.

    final override fun toMetaGtv() = mapOf(
            "type" to "virtual".toGtv(),
            "value" to baseInnerType.toMetaGtv()
    ).toGtv()
}

sealed class R_VirtualCollectionType(val innerCollectionType: R_CollectionType): R_VirtualType(innerCollectionType) {
    val virtualElementType: R_Type = S_VirtualType.virtualMemberType(innerCollectionType.elementType)

    protected abstract fun typeDef(): C_LibTypeDef

    final override fun getLibType0() = C_LibType.make(typeDef(), innerCollectionType.elementType, virtualElementType)
}

class R_VirtualListType(val innerType: R_ListType): R_VirtualCollectionType(innerType) {
    override fun equals0(other: R_Type): Boolean = other is R_VirtualListType && innerType == other.innerType
    override fun hashCode0() = innerType.hashCode()
    override fun createGtvConversion() = GtvRtConversion_VirtualList(this)
    override fun typeDef() = Lib_Rell.VIRTUAL_LIST_TYPE
}

class R_VirtualSetType(val innerType: R_SetType): R_VirtualCollectionType(innerType) {
    override fun equals0(other: R_Type): Boolean = other is R_VirtualSetType && innerType == other.innerType
    override fun hashCode0() = innerType.hashCode()
    override fun createGtvConversion() = GtvRtConversion_VirtualSet(this)
    override fun typeDef() = Lib_Rell.VIRTUAL_SET_TYPE
}

class R_VirtualMapType(val innerType: R_MapType): R_VirtualType(innerType) {
    private val virtualValueType: R_Type = S_VirtualType.virtualMemberType(innerType.valueType)
    val virtualEntryType: R_TupleType = R_TupleType.create(innerType.keyType, virtualValueType)

    override fun equals0(other: R_Type): Boolean = other is R_VirtualMapType && innerType == other.innerType
    override fun hashCode0() = innerType.hashCode()
    override fun createGtvConversion() = GtvRtConversion_VirtualMap(this)

    override fun getLibType0(): C_LibType {
        return C_LibType.make(Lib_Rell.VIRTUAL_MAP_TYPE, innerType.keyType, innerType.valueType, virtualValueType)
    }
}

class R_VirtualTupleType(val innerType: R_TupleType): R_VirtualType(innerType) {
    override fun equals0(other: R_Type): Boolean = other is R_VirtualTupleType && innerType == other.innerType
    override fun hashCode0() = innerType.hashCode()
    override fun createGtvConversion() = GtvRtConversion_VirtualTuple(this)

    override fun getLibType0() = C_LibType.make(
        Lib_Rell.VIRTUAL_TYPE,
        innerType,
        valueMembers = lazy { Lib_Type_VirtualTuple.getValueMembers(this) },
    )
}

class R_VirtualStructType(val innerType: R_StructType): R_VirtualType(innerType) {
    override fun equals0(other: R_Type): Boolean = other is R_VirtualStructType && innerType == other.innerType
    override fun hashCode0() = innerType.hashCode()
    override fun isCacheable() = true
    override fun createGtvConversion() = GtvRtConversion_VirtualStruct(this)

    override fun getLibType0() = C_LibType.make(
        Lib_Rell.VIRTUAL_TYPE,
        innerType,
        valueMembers = lazy { Lib_Type_VirtualStruct.getValueMembers(this) },
    )
}

class R_FunctionType(params: List<R_Type>, val result: R_Type): R_Type(calcName(params, result)) {
    val params = params.toImmList()

    val callParameters by lazy { C_FunctionCallParameters.fromTypes(this.params) }

    private val isError = result.isError() || params.any { it.isError() }

    override fun equals0(other: R_Type) = other is R_FunctionType && params == other.params && result == other.result
    override fun hashCode0() = Objects.hash(params, result)

    override fun isDirectVirtualable() = false
    override fun isDirectPure() = false
    override fun isReference() = true
    override fun isError() = isError
    override fun createGtvConversion() = GtvRtConversion_None

    override fun isAssignableFrom(type: R_Type): Boolean {
        return type is R_FunctionType
                && params.size == type.params.size
                && (result == R_UnitType || result.isAssignableFrom(type.result))
                && params.indices.all { type.params[it].isAssignableFrom(params[it]) }
    }

    override fun strCode(): String = name

    override fun toMetaGtv() = mapOf(
            "type" to "function".toGtv(),
            "params" to params.map { it.toMetaGtv() }.toGtv(),
            "result" to result.toMetaGtv()
    ).toGtv()

    override fun getLibType0(): C_LibType {
        val mResult = result.mType
        val mParams = params.map { it.mType }
        val mType = M_Types.function(mResult, mParams)
        return C_LibType.make(mType)
    }

    companion object {
        private fun calcName(params: List<R_Type>, result: R_Type): String {
            val paramsStr = params.joinToString(",") { it.name }
            return "($paramsStr)->${result.name}"
        }
    }
}
