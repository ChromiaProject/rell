/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.*
import net.postchain.gtv.merkle.proof.GtvMerkleProofTreeFactory
import net.postchain.gtv.merkle.proof.toGtvVirtual
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.C_FeatureSwitch
import net.postchain.rell.base.lib.type.GtvRtConversion_Set
import net.postchain.rell.base.lib.type.Rt_TextValue
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.rr.RR_EnumAttr
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.sql.PreparedStatementParams
import net.postchain.rell.base.sql.ResultSetRow
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.checkEquals
import org.jooq.DataType
import org.jooq.impl.SQLDataType

// =============================================================================
// R_TypeSqlAdapter hierarchy — moved from model/r_type.kt to runtime/
// =============================================================================

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

object R_TypeSqlAdapter_None {
    fun create(typeName: String): R_TypeSqlAdapter = Impl(typeName)

    private class Impl(private val typeName: String): R_TypeSqlAdapter(null) {
        override fun isSqlCompatible(compilerOptions: C_CompilerOptions): Boolean = false

        override fun toSqlValue(value: Rt_Value): Any {
            throw Rt_Utils.errNotSupported("Type cannot be converted to SQL: $typeName")
        }

        override fun toSql(params: PreparedStatementParams, idx: Int, value: Rt_Value) {
            throw Rt_Utils.errNotSupported("Type cannot be converted to SQL: $typeName")
        }

        override fun fromSql(row: ResultSetRow, idx: Int, nullable: Boolean): Rt_Value {
            throw Rt_Utils.errNotSupported("Type cannot be converted from SQL: $typeName")
        }

        override fun metaName(sqlCtx: Rt_SqlContext): String {
            throw Rt_Utils.errNotSupported("Type has no meta name: $typeName")
        }
    }
}

abstract class R_TypeSqlAdapter_Some(sqlType: DataType<*>?): R_TypeSqlAdapter(sqlType) {
    override fun isSqlCompatible(compilerOptions: C_CompilerOptions) = true

    protected fun checkSqlNull(suspect: Boolean, row: ResultSetRow, typeName: String, nullable: Boolean): Rt_Value? {
        return if (suspect && row.wasNull()) {
            if (nullable) {
                Rt_NullValue
            } else {
                errSqlNull(typeName)
            }
        } else {
            null
        }
    }

    protected fun checkSqlNull(typeName: String, nullable: Boolean): Rt_Value {
        return if (nullable) Rt_NullValue else errSqlNull(typeName)
    }

    private fun errSqlNull(typeName: String): Nothing =
        throw Rt_Exception.common("sql_null:$typeName", "SQL value is NULL for type $typeName")
}

abstract class R_TypeSqlAdapter_Primitive(
    protected val name: String,
    sqlType: DataType<*>
): R_TypeSqlAdapter_Some(sqlType) {
    final override fun metaName(sqlCtx: Rt_SqlContext): String = "sys:$name"
}

// =============================================================================
// SQL adapters for model types (entity, enum, nullable)
// =============================================================================

class R_TypeSqlAdapter_Entity(
    private val lazyRtType: Lazy<Rt_Type>,
    private val typeName: String,
    private val metaName: String,
    private val externalChainIndex: Int,
): R_TypeSqlAdapter_Some(SQLDataType.BIGINT) {
    override fun toSqlValue(value: Rt_Value) = value.asObjectId()

    override fun toSql(params: PreparedStatementParams, idx: Int, value: Rt_Value) {
        params.setLong(idx, value.asObjectId())
    }

    override fun fromSql(row: ResultSetRow, idx: Int, nullable: Boolean): Rt_Value {
        val v = row.getLong(idx)
        return checkSqlNull(v == 0L, row, typeName, nullable) ?: Rt_EntityValue(lazyRtType.value, v)
    }

    override fun metaName(sqlCtx: Rt_SqlContext): String {
        val chain =
            if (externalChainIndex < 0) sqlCtx.mainChainMapping() else sqlCtx.chainMappingByIndex(externalChainIndex)
        return "class:${chain.chainId}:$metaName"
    }
}

class R_TypeSqlAdapter_Enum(
    private val lazyRtType: Lazy<Rt_Type>,
    private val typeName: String,
    private val attrs: ImmList<RR_EnumAttr>,
): R_TypeSqlAdapter_Some(SQLDataType.INTEGER) {
    override fun toSqlValue(value: Rt_Value) = value.asEnum().value

    override fun toSql(params: PreparedStatementParams, idx: Int, value: Rt_Value) {
        params.setInt(idx, value.asEnum().value)
    }

    override fun fromSql(row: ResultSetRow, idx: Int, nullable: Boolean): Rt_Value {
        val v = row.getInt(idx)
        val res = checkSqlNull(v == 0, row, typeName, nullable)
        return if (res != null) res else {
            val attr = if (v < 0 || v >= attrs.size) null else attrs[v]
            requireNotNull(attr) { "$typeName: $v" }
            Rt_RR_EnumValue(lazyRtType, attr)
        }
    }

    override fun metaName(sqlCtx: Rt_SqlContext): String {
        return "enum:$typeName"
    }
}

class R_TypeSqlAdapter_Nullable(private val valueAdapter: R_TypeSqlAdapter): R_TypeSqlAdapter_Some(null) {
    override fun isSqlCompatible(compilerOptions: C_CompilerOptions): Boolean {
        val enabled = SQL_COMPATIBILITY_SWITCH.isActive(compilerOptions)
        return enabled && valueAdapter.isSqlCompatible(compilerOptions)
    }

    override fun isAllowedForEntityAttributes(compilerOptions: C_CompilerOptions) = false

    override fun metaName(sqlCtx: Rt_SqlContext): String {
        throw Rt_Utils.errNotSupported("Nullable entity attributes are not supported")
    }

    override fun toSqlValue(value: Rt_Value) = valueAdapter.toSqlValue(value)

    override fun toSql(params: PreparedStatementParams, idx: Int, value: Rt_Value) {
        params.setBoolean(idx, value.asBoolean())
    }

    override fun fromSql(row: ResultSetRow, idx: Int, nullable: Boolean): Rt_Value {
        return valueAdapter.fromSql(row, idx, true)
    }

    companion object {
        private val SQL_COMPATIBILITY_SWITCH = C_FeatureSwitch("0.13.10")
    }
}

object GtvRtConversion_Null: GtvRtConversion {
    override val directCompatibility = GtvCompatibility(fromGtv = true, toGtv = true)

    override fun rtToGtv(rt: Rt_Value, pretty: Boolean): Gtv {
        checkEquals(rt, Rt_NullValue)
        return GtvNull
    }

    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        check(gtv.isNull())
        return Rt_NullValue
    }
}

class GtvRtConversion_Entity(
    private val rtType: Lazy<Rt_Type>,
    private val typeName: String,
    gtvCompatible: Boolean,
    private val rEntity: R_EntityDefinition,
): GtvRtConversion {
    override val directCompatibility = GtvCompatibility(gtvCompatible, gtvCompatible)

    override fun rtToGtv(rt: Rt_Value, pretty: Boolean) = GtvInteger(rt.asObjectId())

    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val rowid = GtvRtUtils.gtvToInteger(ctx, gtv, typeName)
        return ctx.rtValue {
            ctx.trackRecord(rEntity, rowid)
            Rt_EntityValue(rtType.value, rowid)
        }
    }
}

class GtvRtConversion_Enum(private val enum: R_EnumDefinition): GtvRtConversion {
    override val directCompatibility = GtvCompatibility(fromGtv = true, toGtv = true)

    override fun rtToGtv(rt: Rt_Value, pretty: Boolean): Gtv {
        val e = rt.asEnum()
        return if (pretty) {
            GtvString(e.name)
        } else {
            GtvInteger(e.value.toLong())
        }
    }

    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val attr = if (ctx.pretty && gtv.type == GtvType.STRING) {
            val name = GtvRtUtils.gtvToString(ctx, gtv, enum.type.strCode())
            val attr = enum.attr(name)
            if (attr == null) {
                val code = "enum:bad_value:$name"
                throw GtvRtUtils.errGtvType(ctx, enum.type.strCode(), code, "invalid value: '$name'")
            }
            attr
        } else {
            val value = GtvRtUtils.gtvToInteger(ctx, gtv, enum.type.strCode())
            val attr = enum.attr(value)
            if (attr == null) {
                val code = "enum:bad_value:$value"
                throw GtvRtUtils.errGtvType(ctx, enum.type.strCode(), code, "invalid value: $value")
            }
            attr
        }
        return enum.rtGetValue(attr)
    }
}

class GtvRtConversion_Nullable(valueConversion: Lazy<Rt_TypeGtvConversion>): GtvRtConversion {
    private val valueConversion by valueConversion

    override val directCompatibility = GtvCompatibility(fromGtv = true, toGtv = true)

    override fun rtToGtv(rt: Rt_Value, pretty: Boolean): Gtv {
        return if (rt == Rt_NullValue) {
            GtvNull
        } else {
            valueConversion.rtToGtv(rt, pretty)
        }
    }

    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        return if (gtv.isNull()) {
            Rt_NullValue
        } else {
            valueConversion.gtvToRt(ctx, gtv)
        }
    }
}

class GtvRtConversion_Struct(private val struct: R_Struct): GtvRtConversion {
    private val arrayConv = ArrayConv()
    private val attrConversions by lazy { struct.attributesList.map { rTypeToRtType(it.type).gtvConversion!! } }

    override val directCompatibility = GtvCompatibility(fromGtv = true, toGtv = true)

    override fun rtToGtv(rt: Rt_Value, pretty: Boolean): Gtv {
        val attrs = struct.attributesList
        return if (pretty) {
            val rtStruct = rt.asStruct()
            val gtvFields = attrs
                .mapIndexed { i, attr -> attr.name to attrConversions[i].rtToGtv(rtStruct.get(i), pretty) }
                .toMap()
            GtvFactory.gtv(gtvFields)
        } else {
            val rtStruct = rt.asStruct()
            GtvArray(Array(attrs.size) { i -> attrConversions[i].rtToGtv(rtStruct.get(i), pretty) })
        }
    }

    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        return when {
            ctx.pretty && gtv.type == GtvType.DICT -> gtvToRtDict(ctx, gtv)
            else -> arrayConv.convert(ctx, gtv)
        }
    }

    private fun gtvToRtDict(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val type = struct.type
        val gtvFields = GtvRtUtils.gtvToMap(ctx, gtv, type.name)

        val attrs = struct.attributesList
        val rtAttrs = attrs
            .mapIndexed { i, attr ->
                val gtvAttr = gtvFields[attr.name]
                gtvToRtAttr(ctx, attr, i, gtvAttr, struct.rDefBase)
            }

        for (key in gtvFields.keys) {
            if (key !in struct.strAttributes) {
                val typeName = struct.name
                throw GtvRtUtils.errGtv(
                    ctx, "struct_badkey:$typeName:$key",
                    "Wrong key in Gtv dictionary for type '$typeName': '$key'",
                )
            }
        }

        return ctx.rtValue {
            Rt_StructValue.createValidated(type, rtAttrs.toMutableList())
        }
    }

    private fun gtvToRtAttr(
        ctx: GtvToRtContext,
        attr: R_Attribute,
        attrIndex: Int,
        gtvAttr: Gtv?,
        rDefBase: R_DefinitionBase?,
    ): Rt_Value {
        if (gtvAttr != null) {
            val attrCtx = ctx.updateSymbol(GtvToRtSymbol_Attr(struct.name, attr))
            return attrConversions[attrIndex].gtvToRt(attrCtx, gtvAttr)
        }
        return ctx.getDefaultValue(rDefBase, attr, struct.name, "struct")
    }

    private inner class ArrayConv {
        private val attrs = struct.attributesList
        private val minAttrCount = attrs.indexOfLast { !it.hasExpr } + 1

        fun convert(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
            val type = struct.type
            val gtvAttrValues = gtvToAttrValues(ctx, gtv, type.name, struct, minAttrCount)

            val rtAttrs = attrs
                .mapIndexed { i, attr ->
                    val gtvAttr = gtvAttrValues.getOrNull(i)
                    gtvToRtAttr(ctx, attr, i, gtvAttr, struct.rDefBase)
                }

            return ctx.rtValue {
                Rt_StructValue.createValidated(type, rtAttrs.toMutableList())
            }
        }
    }

    companion object {
        fun gtvToAttrValues(
            ctx: GtvToRtContext,
            gtv: Gtv,
            typeName: String,
            struct: R_Struct,
            minCount: Int,
        ): List<Gtv> {
            val maxCount = struct.attributesList.size
            check(minCount <= maxCount)

            val gtvFields = GtvRtUtils.gtvToArrayAny(ctx, gtv, typeName)
            val actualCount = gtvFields.size

            if (actualCount !in minCount..maxCount) {
                throw errWrongArraySize(ctx, typeName, minCount, maxCount, actualCount)
            }

            return gtvFields.toList()
        }

        fun errWrongArraySize(
            ctx: GtvToRtContext,
            typeName: String,
            minCount: Int,
            maxCount: Int,
            actualCount: Int,
        ): Rt_Exception {
            val expCountStr = if (minCount == maxCount) "$minCount" else "$minCount..$maxCount"
            return GtvRtUtils.errGtv(
                ctx, "struct_size:$typeName:$minCount:$maxCount:$actualCount",
                "Wrong Gtv array size for struct '$typeName': $actualCount instead of $expCountStr",
            )
        }
    }
}

class GtvRtConversion_Tuple(
    private val typeName: String,
    private val fieldNames: ImmList<String?>,
    fieldConversions: Lazy<ImmList<Rt_TypeGtvConversion>>,
    private val rtType: Lazy<Rt_Type>,
): GtvRtConversion {
    private val fieldConversions by fieldConversions
    private val allFieldsNamed = fieldNames.all { it != null }

    override val directCompatibility = GtvCompatibility(fromGtv = true, toGtv = true)

    override fun rtToGtv(rt: Rt_Value, pretty: Boolean): Gtv {
        return if (pretty && allFieldsNamed) rtToGtvPretty(rt) else rtToGtvCompact(rt)
    }

    private fun rtToGtvPretty(rt: Rt_Value): Gtv {
        val rtFields = rt.asTuple()
        checkEquals(rtFields.size, fieldNames.size)
        val gtv = rtFields
            .mapIndexed { i, rtField ->
                fieldNames[i]!! to fieldConversions[i].rtToGtv(rtField, true)
            }
            .toMap()
        return GtvFactory.gtv(gtv)
    }

    private fun rtToGtvCompact(rt: Rt_Value): Gtv {
        val rtFields = rt.asTuple()
        checkEquals(rtFields.size, fieldNames.size)
        val gtvFields = rtFields
            .mapIndexed { i, rtField ->
                fieldConversions[i].rtToGtv(rtField, false)
            }
            .toTypedArray()
        return GtvArray(gtvFields)
    }

    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        return if (ctx.pretty && allFieldsNamed && gtv.type == GtvType.DICT) {
            gtvToRtDict(ctx, gtv)
        } else {
            gtvToRtArray(ctx, gtv)
        }
    }

    private fun gtvToRtDict(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val gtvFields = GtvRtUtils.gtvToMap(ctx, gtv, typeName)
        checkFieldCount(ctx, typeName, gtvFields.size, fieldNames.size, "dictionary")

        val rtFields = fieldNames.mapIndexed { i, name ->
            val key = name!!
            if (key !in gtvFields) {
                throw GtvRtUtils.errGtv(ctx, "tuple_nokey:$key", "Key missing in Gtv dictionary: '$key'")
            }
            val gtvField = gtvFields.getValue(key)
            fieldConversions[i].gtvToRt(ctx, gtvField)
        }

        return ctx.rtValue {
            Rt_TupleValue(rtType.value, rtFields)
        }
    }

    private fun gtvToRtArray(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val gtvFields = gtvArrayToFields(ctx, typeName, fieldNames.size, gtv)
        val rtFields = gtvFields.mapIndexed { i, gtvField ->
            fieldConversions[i].gtvToRt(ctx, gtvField)
        }
        return ctx.rtValue {
            Rt_TupleValue(rtType.value, rtFields)
        }
    }

    companion object {
        fun gtvArrayToFields(ctx: GtvToRtContext, typeName: String, fieldCount: Int, gtv: Gtv): List<Gtv> {
            val gtvFields = GtvRtUtils.gtvToArrayAny(ctx, gtv, typeName)
            checkFieldCount(ctx, typeName, gtvFields.size, fieldCount, "array")
            return gtvFields.toList()
        }

        private fun checkFieldCount(
            ctx: GtvToRtContext,
            typeName: String,
            actualCount: Int,
            expectedCount: Int,
            structure: String
        ) {
            if (actualCount != expectedCount) {
                throw GtvRtUtils.errGtv(
                    ctx, "tuple_count:$expectedCount:$actualCount",
                    "Wrong Gtv $structure size: $actualCount instead of $expectedCount",
                )
            }
        }
    }
}

sealed class GtvRtConversion_Virtual: GtvRtConversion {
    final override val directCompatibility = GtvCompatibility(fromGtv = true, toGtv = false)
    final override fun rtToGtv(rt: Rt_Value, pretty: Boolean) =
        throw Rt_GtvError.exception("virtual:to_gtv", "Cannot convert virtual to Gtv")

    companion object {
        fun deserialize(ctx: GtvToRtContext, gtv: Gtv): Gtv {
            if (gtv !is GtvArray) {
                val cls = gtv.javaClass.simpleName
                throw GtvRtUtils.errGtv(ctx, "virtual:type:$cls", "Wrong Gtv type: $cls")
            }

            val proof = try {
                GtvMerkleProofTreeFactory().deserialize(gtv)
            } catch (e: Exception) {
                throw GtvRtUtils.errGtv(
                    ctx, "virtual:deserialize:${e.javaClass.canonicalName}",
                    "Virtual proof deserialization failed: ${e.message}",
                )
            }

            val virtual = proof.toGtvVirtual()
            return virtual
        }

        fun decodeVirtualElement(ctx: GtvToRtContext, type: R_Type, gtv: Gtv): Rt_Value {
            return when (type) {
                is R_StructType -> GtvRtConversion_VirtualStruct.decodeVirtualStruct(ctx, type.struct.virtualType, gtv)
                is R_ListType -> GtvRtConversion_VirtualList.decodeVirtualList(ctx, type.virtualType, gtv)
                is R_SetType -> GtvRtConversion_VirtualSet.decodeVirtualSet(ctx, type.virtualType, gtv)
                is R_MapType -> GtvRtConversion_VirtualMap.decodeVirtualMap(ctx, type.virtualType, gtv)
                is R_TupleType -> GtvRtConversion_VirtualTuple.decodeVirtualTuple(ctx, type.virtualType, gtv)
                is R_NullableType -> if (gtv.isNull()) Rt_NullValue else decodeVirtualElement(ctx, type.valueType, gtv)
                else -> rTypeToRtType(type).gtvConversion!!.gtvToRt(ctx, gtv)
            }
        }
    }
}

class GtvRtConversion_VirtualStruct(private val type: R_VirtualStructType): GtvRtConversion_Virtual() {
    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val virtual = deserialize(ctx, gtv)
        return decodeVirtualStruct(ctx, type, virtual)
    }

    companion object {
        fun decodeVirtualStruct(ctx: GtvToRtContext, type: R_VirtualStructType, v: Gtv): Rt_Value {
            val attrValues = decodeAttrs(ctx, type, v)
            val rtAttrValues = type.innerType.struct.attributesList.mapIndexed { i, attr ->
                val gtvAttr = if (i < attrValues.size) attrValues[i] else null
                if (gtvAttr == null) null else {
                    val attrCtx = ctx.updateSymbol(GtvToRtSymbol_Attr(type.name, attr))
                    decodeVirtualElement(attrCtx, attr.type, gtvAttr)
                }
            }
            return Rt_VirtualStructValue(
                v,
                rTypeToRtType(type),
                rTypeToRtType(type.innerType),
                type.innerType.name,
                type.innerType.struct.attributesList.map { it.name },
                rtAttrValues,
            )
        }

        private fun decodeAttrs(ctx: GtvToRtContext, type: R_VirtualStructType, v: Gtv): List<Gtv?> {
            val struct = type.innerType.struct
            return if (v !is GtvVirtual) {
                GtvRtConversion_Struct.gtvToAttrValues(ctx, v, type.name, struct, struct.attributes.size)
            } else {
                decodeVirtualArray(ctx, type.name, v, struct.attributes.size)
            }
        }

        fun decodeVirtualArray(ctx: GtvToRtContext, typeName: String, v: Gtv, maxSize: Int): List<Gtv?> {
            if (v !is GtvVirtualArray) {
                val cls = v.javaClass.simpleName
                throw GtvRtUtils.errGtv(ctx, "virtual:deserialized_type:$cls", "Wrong deserialized Gtv type: $cls")
            }

            val actualCount = v.array.size
            if (actualCount > maxSize) {
                throw GtvRtConversion_Struct.errWrongArraySize(ctx, typeName, maxSize, maxSize, actualCount)
            }

            return v.array.toList()
        }
    }
}

class GtvRtConversion_VirtualTuple(val type: R_VirtualTupleType): GtvRtConversion_Virtual() {
    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val virtual = deserialize(ctx, gtv)
        return decodeVirtualTuple(ctx, type, virtual)
    }

    companion object {
        fun decodeVirtualTuple(ctx: GtvToRtContext, type: R_VirtualTupleType, v: Gtv): Rt_Value {
            val fieldValues = decodeFields(ctx, type, v)
            val rtFieldValues = type.innerType.fields.mapIndexed { i, attr ->
                val gtvAttr = if (i < fieldValues.size) fieldValues[i] else null
                if (gtvAttr == null) null else decodeVirtualElement(ctx, attr.type, gtvAttr)
            }
            return Rt_VirtualTupleValue(
                v,
                rTypeToRtType(type),
                rTypeToRtType(type.innerType),
                type.innerType.fields.map { it.name?.str },
                rtFieldValues,
            )
        }

        private fun decodeFields(ctx: GtvToRtContext, type: R_VirtualTupleType, v: Gtv): List<Gtv?> {
            return if (v !is GtvVirtual) {
                GtvRtConversion_Tuple.gtvArrayToFields(ctx, type.name, type.innerType.fields.size, v)
            } else {
                GtvRtConversion_VirtualStruct.decodeVirtualArray(ctx, type.name, v, type.innerType.fields.size)
            }
        }
    }
}

class GtvRtConversion_VirtualList(private val type: R_VirtualListType): GtvRtConversion_Virtual() {
    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val virtual = deserialize(ctx, gtv)
        return decodeVirtualList(ctx, type, virtual)
    }

    companion object {
        fun decodeVirtualList(ctx: GtvToRtContext, type: R_VirtualListType, v: Gtv): Rt_Value {
            val rtElements = decodeVirtualElements(ctx, type.innerType, v)
            return Rt_VirtualListValue(v, rTypeToRtType(type), rTypeToRtType(type.innerType), rtElements)
        }

        fun decodeVirtualElements(ctx: GtvToRtContext, innerType: R_CollectionType, v: Gtv): List<Rt_Value?> {
            val gtvElements = decodeElements(ctx, v, innerType.name)
            val rtElements = gtvElements.map {
                if (it == null) null else decodeVirtualElement(ctx, innerType.elementType, it)
            }
            return rtElements
        }

        private fun decodeElements(ctx: GtvToRtContext, v: Gtv, typeName: String): List<Gtv?> {
            if (v !is GtvVirtual) {
                return GtvRtUtils.gtvToArrayAny(ctx, v, typeName).toList()
            }
            if (v !is GtvVirtualArray) {
                val cls = v.javaClass.simpleName
                throw GtvRtUtils.errGtv(ctx, "virtual:deserialized_type:$cls", "Wrong deserialized Gtv type: $cls")
            }
            return v.array.toList()
        }
    }
}

class GtvRtConversion_VirtualSet(private val type: R_VirtualSetType): GtvRtConversion_Virtual() {
    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val virtual = deserialize(ctx, gtv)
        return decodeVirtualSet(ctx, type, virtual)
    }

    companion object {
        fun decodeVirtualSet(ctx: GtvToRtContext, type: R_VirtualSetType, v: Gtv): Rt_Value {
            val rtList = GtvRtConversion_VirtualList.decodeVirtualElements(ctx, type.innerType, v)
            val rtSet = GtvRtConversion_Set.listToSet(ctx, rtList.filterNotNull())
            return Rt_VirtualSetValue(v, rTypeToRtType(type), rTypeToRtType(type.innerType), rtSet)
        }
    }
}

class GtvRtConversion_VirtualMap(private val type: R_VirtualMapType): GtvRtConversion_Virtual() {
    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val virtual = deserialize(ctx, gtv)
        return decodeVirtualMap(ctx, type, virtual)
    }

    companion object {
        fun decodeVirtualMap(ctx: GtvToRtContext, type: R_VirtualMapType, v: Gtv): Rt_Value {
            val gtvMap = decodeMap(ctx, v, type.name)
            val rtMap = gtvMap
                .mapValues { (_, v) -> decodeVirtualElement(ctx, type.innerType.valueType, v) }
                .mapKeys { (k, _) -> Rt_TextValue.get(k) }
            return Rt_VirtualMapValue(
                v,
                rTypeToRtType(type),
                rTypeToRtType(type.virtualEntryType),
                rTypeToRtType(type.innerType),
                rtMap,
            )
        }

        private fun decodeMap(ctx: GtvToRtContext, v: Gtv, typeName: String): Map<String, Gtv> {
            if (v !is GtvVirtual) {
                return GtvRtUtils.gtvToMap(ctx, v, typeName)
            }
            if (v !is GtvVirtualDictionary) {
                val cls = v.javaClass.simpleName
                throw GtvRtUtils.errGtv(ctx, "virtual:deserialized_type:$cls", "Wrong deserialized Gtv type: $cls")
            }
            return v.dict
        }
    }
}
