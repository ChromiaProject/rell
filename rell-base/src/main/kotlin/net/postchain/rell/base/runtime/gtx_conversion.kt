/*
 * Copyright (C) 2023 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.common.exception.UserMistake
import net.postchain.gtv.*
import net.postchain.gtv.merkle.proof.GtvMerkleProofTreeFactory
import net.postchain.gtv.merkle.proof.toGtvVirtual
import net.postchain.rell.base.compiler.base.utils.C_CodeMsg
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.lib.type.*
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.R_Expr
import net.postchain.rell.base.sql.SqlExecutor
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.immListOf
import org.apache.commons.collections4.MultiValuedMap
import org.apache.commons.collections4.multimap.HashSetValuedHashMap
import java.math.BigInteger

const val GTV_QUERY_PRETTY = true
const val GTV_OPERATION_PRETTY = false

fun interface GtvToRtDefaultValueEvaluator {
    fun evaluate(expr: R_Expr): Rt_Value
}

private class GtvToRtState(
    val pretty: Boolean,
    val validateOnly: Boolean,
    val defaultValueEvaluator: GtvToRtDefaultValueEvaluator?,
    val strictGtvConversion: Boolean,
) {
    private val entityRowids: MultiValuedMap<R_EntityDefinition, Long> = HashSetValuedHashMap()

    fun trackRecord(entity: R_EntityDefinition, rowid: Long) {
        entityRowids.put(entity, rowid)
    }

    fun finish(exeCtx: Rt_ExecutionContext) {
        for (rEntities in entityRowids.keySet()) {
            val rowids = entityRowids.get(rEntities)
            checkRowids(exeCtx.sqlExec, exeCtx.sqlCtx, rEntities, rowids)
        }
    }

    private fun checkRowids(sqlExec: SqlExecutor, sqlCtx: Rt_SqlContext, rEntity: R_EntityDefinition, rowids: Collection<Long>) {
        val existingIds = selectExistingIds(sqlExec, sqlCtx, rEntity, rowids)
        val missingIds = rowids.toSet() - existingIds
        if (missingIds.isNotEmpty()) {
            val s = missingIds.toList().sorted()
            val name = rEntity.appLevelName
            throw Rt_GtvError.exception("obj_missing:[$name]:${missingIds.joinToString(",")}", "Missing objects of entity '$name': $s")
        }
    }

    private fun selectExistingIds(sqlExec: SqlExecutor, sqlCtx: Rt_SqlContext, rEntity: R_EntityDefinition, rowids: Collection<Long>): Set<Long> {
        val buf = StringBuilder()
        buf.append("\"").append(rEntity.sqlMapping.rowidColumn()).append("\" IN (")
        rowids.joinTo(buf, ",")
        buf.append(")")
        val whereSql = buf.toString()

        val sql = rEntity.sqlMapping.selectExistingObjects(sqlCtx, whereSql)
        val existingIds = mutableSetOf<Long>()
        sqlExec.executeQuery(sql, {}) { existingIds.add(it.getLong(1)) }
        return existingIds
    }
}

sealed class GtvToRtSymbol {
    abstract fun codeMsg(): C_CodeMsg
}

class GtvToRtSymbol_Param(private val param: R_FunctionParam): GtvToRtSymbol() {
    override fun codeMsg() = "param:${param.name}" toCodeMsg "parameter: ${param.name}"
}

class GtvToRtSymbol_Attr(private val typeName: String, private val attr: R_Attribute): GtvToRtSymbol() {
    override fun codeMsg() = "attr:[$typeName]:${attr.name}" toCodeMsg "attribute: $typeName.${attr.name}"
}

class GtvToRtContext private constructor(
    private val state: GtvToRtState,
    val symbol: GtvToRtSymbol?,
    private val keepSymbol: Boolean,
) {
    val pretty = state.pretty
    val strictGtvConversion = state.strictGtvConversion
    val defaultValueEvaluator = state.defaultValueEvaluator

    fun updateSymbol(symbol: GtvToRtSymbol, keep: Boolean = false): GtvToRtContext {
        if (this.symbol != null && this.keepSymbol) return this
        return if (symbol === this.symbol) this else GtvToRtContext(state, symbol, keep)
    }

    fun rtValue(supplier: () -> Rt_Value): Rt_Value {
        return if (state.validateOnly) Rt_UnitValue else supplier()
    }

    fun trackRecord(entity: R_EntityDefinition, rowid: Long) = state.trackRecord(entity, rowid)
    fun finish(exeCtx: Rt_ExecutionContext) = state.finish(exeCtx)

    companion object {
        fun make(
            pretty: Boolean,
            validateOnly: Boolean = false,
            defaultValueEvaluator: GtvToRtDefaultValueEvaluator? = null,
            strictGtvConversion: Boolean = false,
        ): GtvToRtContext {
            val state = GtvToRtState(
                pretty = pretty,
                validateOnly = validateOnly,
                defaultValueEvaluator = defaultValueEvaluator,
                strictGtvConversion = strictGtvConversion
            )
            return GtvToRtContext(state, null, false)
        }
    }
}

abstract class GtvRtConversion {
    abstract fun directCompatibility(): R_GtvCompatibility
    abstract fun rtToGtv(rt: Rt_Value, pretty: Boolean): Gtv
    abstract fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value
}

object GtvRtConversion_None: GtvRtConversion() {
    override fun directCompatibility() = R_GtvCompatibility(false, false)
    override fun rtToGtv(rt: Rt_Value, pretty: Boolean) = throw UnsupportedOperationException()
    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv) = throw UnsupportedOperationException()
}

object GtvRtConversion_Null: GtvRtConversion() {
    override fun directCompatibility() = R_GtvCompatibility(true, true)

    override fun rtToGtv(rt: Rt_Value, pretty: Boolean): Gtv {
        checkEquals(rt, Rt_NullValue)
        return GtvNull
    }

    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        check(gtv.isNull())
        return Rt_NullValue
    }
}

class GtvRtConversion_Entity(val type: R_EntityType): GtvRtConversion() {
    override fun directCompatibility() = R_GtvCompatibility(type.rEntity.flags.gtv, type.rEntity.flags.gtv)

    override fun rtToGtv(rt: Rt_Value, pretty: Boolean) = GtvInteger(rt.asObjectId())

    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val rowid = GtvRtUtils.gtvToInteger(ctx, gtv, type)
        ctx.trackRecord(type.rEntity, rowid)
        return ctx.rtValue {
            Rt_EntityValue(type, rowid)
        }
    }
}

class GtvRtConversion_Struct(private val struct: R_Struct): GtvRtConversion() {
    override fun directCompatibility() = R_GtvCompatibility(true, true)

    override fun rtToGtv(rt: Rt_Value, pretty: Boolean): Gtv {
        val attrs = struct.attributesList
        return if (pretty) {
            val rtStruct = rt.asStruct()
            val gtvFields = attrs
                .mapIndexed { i, attr -> attr.name to attr.type.rtToGtv(rtStruct.get(i), pretty) }
                .toMap()
            GtvFactory.gtv(gtvFields)
        } else {
            val rtStruct = rt.asStruct()
            val gtvFields = attrs
                .mapIndexed { i, attr -> attr.type.rtToGtv(rtStruct.get(i), pretty) }
                .toTypedArray()
            GtvArray(gtvFields)
        }
    }

    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        return if (ctx.pretty && gtv.type == GtvType.DICT) gtvToRtDict(ctx, gtv) else gtvToRtArray(ctx, gtv)
    }

    private fun gtvToRtDict(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val type = struct.type
        val gtvFields = GtvRtUtils.gtvToMap(ctx, gtv, type)

        val attrs = struct.attributesList
        val rtFields = attrs
            .map { attr ->
                gtvToRtDictAttr(ctx, gtvFields, attr)
            }

        for (key in gtvFields.keys) {
            if (key !in struct.strAttributes) {
                val typeName = struct.name
                throw GtvRtUtils.errGtv(ctx, "struct_badkey:$typeName:$key",
                    "Wrong key in Gtv dictionary for type '$typeName': '$key'")
            }
        }

        return ctx.rtValue {
            Rt_StructValue(type, rtFields.toMutableList())
        }
    }

    private fun gtvToRtDictAttr(
        ctx: GtvToRtContext,
        gtvFields: Map<String, Gtv>,
        attr: R_Attribute,
    ): Rt_Value {
        val key = attr.name

        val gtvField = gtvFields[key]
        if (gtvField != null) {
            val attrCtx = ctx.updateSymbol(GtvToRtSymbol_Attr(struct.name, attr))
            return attr.type.gtvToRt(attrCtx, gtvField)
        }

        val expr = attr.expr
        if (ctx.defaultValueEvaluator != null && expr != null) {
            return ctx.rtValue {
                ctx.defaultValueEvaluator.evaluate(expr)
            }
        }

        val typeName = struct.name
        throw GtvRtUtils.errGtv(
            ctx, "struct_nokey:$typeName:$key",
            "Key missing in Gtv dictionary: field '$typeName.$key'"
        )
    }

    private fun gtvToRtArray(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val type = struct.type
        val gtvAttrValues = gtvToAttrValues(ctx, type, struct, gtv)
        val attrs = struct.attributesList

        val rtAttrValues = gtvAttrValues
            .mapIndexed { i, gtvField ->
                val attr = attrs[i]
                val attrCtx = ctx.updateSymbol(GtvToRtSymbol_Attr(struct.name, attr))
                attr.type.gtvToRt(attrCtx, gtvField)
            }

        return ctx.rtValue {
            Rt_StructValue(type, rtAttrValues.toMutableList())
        }
    }

    companion object {
        fun gtvToAttrValues(ctx: GtvToRtContext, type: R_Type, struct: R_Struct, gtv: Gtv): List<Gtv> {
            val gtvFields = GtvRtUtils.gtvToArray(ctx, gtv, type)
            checkFieldCount(ctx, type, struct, gtvFields.size)
            return gtvFields.toList()
        }

        private fun checkFieldCount(ctx: GtvToRtContext, type: R_Type, struct: R_Struct, actualCount: Int) {
            val expectedCount = struct.attributesList.size
            if (actualCount != expectedCount) {
                throw errWrongArraySize(ctx, type, expectedCount, actualCount)
            }
        }

        fun errWrongArraySize(ctx: GtvToRtContext, type: R_Type, expectedCount: Int, actualCount: Int): Rt_Exception {
            val typeName = type.name
            return GtvRtUtils.errGtv(ctx, "struct_size:$typeName:$expectedCount:$actualCount",
                    "Wrong Gtv array size for struct '$typeName': $actualCount instead of $expectedCount")
        }
    }
}

class GtvRtConversion_Enum(private val enum: R_EnumDefinition): GtvRtConversion() {
    override fun directCompatibility() = R_GtvCompatibility(true, true)

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
            val name = GtvRtUtils.gtvToString(ctx, gtv, enum.type)
            val attr = enum.attr(name)
            if (attr == null) {
                val code = "enum:bad_value:$name"
                throw GtvRtUtils.errGtvType(ctx, enum.type, code, "invalid value: '$name'")
            }
            attr
        } else {
            val value = GtvRtUtils.gtvToInteger(ctx, gtv, enum.type)
            val attr = enum.attr(value)
            if (attr == null) {
                val code = "enum:bad_value:$value"
                throw GtvRtUtils.errGtvType(ctx, enum.type, code, "invalid value: $value")
            }
            attr
        }
        return enum.type.getValue(attr)
    }
}

class GtvRtConversion_Nullable(val type: R_NullableType): GtvRtConversion() {
    override fun directCompatibility() = R_GtvCompatibility(true, true)

    override fun rtToGtv(rt: Rt_Value, pretty: Boolean): Gtv {
        return if (rt == Rt_NullValue) {
            GtvNull
        } else {
            type.valueType.rtToGtv(rt, pretty)
        }
    }

    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        return if (gtv.isNull()) {
            Rt_NullValue
        } else {
            type.valueType.gtvToRt(ctx, gtv)
        }
    }
}

class GtvRtConversion_Tuple(val type: R_TupleType): GtvRtConversion() {
    override fun directCompatibility() = R_GtvCompatibility(true, true)

    override fun rtToGtv(rt: Rt_Value, pretty: Boolean): Gtv {
        return if (pretty && type.fields.all { it.name != null }) rtToGtvPretty(rt) else rtToGtvCompact(rt)
    }

    private fun rtToGtvPretty(rt: Rt_Value): Gtv {
        val rtFields = rt.asTuple()
        checkEquals(rtFields.size, type.fields.size)
        val gtv = rtFields
            .mapIndexed { i, rtField ->
                val field = type.fields[i]
                field.name!!.str to field.type.rtToGtv(rtField, true)
            }
            .toMap()
        return GtvFactory.gtv(gtv)
    }

    private fun rtToGtvCompact(rt: Rt_Value): Gtv {
        val rtFields = rt.asTuple()
        checkEquals(rtFields.size, type.fields.size)
        val gtvFields = rtFields
            .mapIndexed { i, rtField ->
                type.fields[i].type.rtToGtv(rtField, false)
            }
            .toTypedArray()
        return GtvArray(gtvFields)
    }

    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        return if (ctx.pretty && type.fields.all { it.name != null } && gtv.type == GtvType.DICT) {
            gtvToRtDict(ctx, gtv)
        } else {
            gtvToRtArray(ctx, gtv)
        }
    }

    private fun gtvToRtDict(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val gtvFields = GtvRtUtils.gtvToMap(ctx, gtv, type)
        checkFieldCount(ctx, type, gtvFields.size, "dictionary")

        val rtFields = type.fields.mapIndexed { _, field ->
            val key = field.name!!
            if (key.str !in gtvFields) {
                throw GtvRtUtils.errGtv(ctx, "tuple_nokey:$key", "Key missing in Gtv dictionary: '$key'")
            }
            val gtvField = gtvFields.getValue(key.str)
            field.type.gtvToRt(ctx, gtvField)
        }

        return ctx.rtValue {
            Rt_TupleValue(type, rtFields)
        }
    }

    private fun gtvToRtArray(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val gtvFields = gtvArrayToFields(ctx, type, gtv)
        val rtFields = gtvFields.mapIndexed { i, gtvField ->
            type.fields[i].type.gtvToRt(ctx, gtvField)
        }
        return ctx.rtValue {
            Rt_TupleValue(type, rtFields)
        }
    }

    companion object {
        fun gtvArrayToFields(ctx: GtvToRtContext, type: R_TupleType, gtv: Gtv): List<Gtv> {
            val gtvFields = GtvRtUtils.gtvToArray(ctx, gtv, type)
            checkFieldCount(ctx, type, gtvFields.size, "array")
            return gtvFields.toList()
        }

        private fun checkFieldCount(ctx: GtvToRtContext, type: R_TupleType, actualCount: Int, structure: String) {
            val expectedCount = type.fields.size
            if (actualCount != expectedCount) {
                throw GtvRtUtils.errGtv(ctx, "tuple_count:$expectedCount:$actualCount",
                        "Wrong Gtv $structure size: $actualCount instead of $expectedCount")
            }
        }
    }
}

sealed class GtvRtConversion_Virtual: GtvRtConversion() {
    final override fun directCompatibility() = R_GtvCompatibility(true, false)
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
                throw GtvRtUtils.errGtv(ctx, "virtual:deserialize:${e.javaClass.canonicalName}",
                        "Virtual proof deserialization failed: ${e.message}")
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
                else -> type.gtvToRt(ctx, gtv)
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
            return Rt_VirtualStructValue(v, type, rtAttrValues)
        }

        private fun decodeAttrs(ctx: GtvToRtContext, type: R_VirtualStructType, v: Gtv): List<Gtv?> {
            return if (v !is GtvVirtual) {
                GtvRtConversion_Struct.gtvToAttrValues(ctx, type, type.innerType.struct, v)
            } else {
                decodeVirtualArray(ctx, type, v, type.innerType.struct.attributes.size)
            }
        }

        fun decodeVirtualArray(ctx: GtvToRtContext, type: R_Type, v: Gtv, maxSize: Int): List<Gtv?> {
            if (v !is GtvVirtualArray) {
                val cls = v.javaClass.simpleName
                throw GtvRtUtils.errGtv(ctx, "virtual:deserialized_type:$cls", "Wrong deserialized Gtv type: $cls")
            }

            val actualCount = v.array.size
            if (actualCount > maxSize) {
                throw GtvRtConversion_Struct.errWrongArraySize(ctx, type, maxSize, actualCount)
            }

            return v.array.toList()
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
            return Rt_VirtualListValue(v, type, rtElements)
        }

        fun decodeVirtualElements(ctx: GtvToRtContext, innerType: R_CollectionType, v: Gtv): List<Rt_Value?> {
            val gtvElements = decodeElements(ctx, v, innerType)
            val rtElements = gtvElements.map {
                if (it == null) null else decodeVirtualElement(ctx, innerType.elementType, it)
            }
            return rtElements
        }

        private fun decodeElements(ctx: GtvToRtContext, v: Gtv, type: R_Type): List<Gtv?> {
            if (v !is GtvVirtual) {
                return GtvRtUtils.gtvToArray(ctx, v, type).toList()
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
            return Rt_VirtualSetValue(v, type, rtSet)
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
            val gtvMap = decodeMap(ctx, v, type)
            val rtMap = gtvMap
                    .mapValues { (_, v) -> decodeVirtualElement(ctx, type.innerType.valueType, v) }
                    .mapKeys { (k, _) -> Rt_TextValue.get(k) }
            return Rt_VirtualMapValue(v, type, rtMap)
        }

        private fun decodeMap(ctx: GtvToRtContext, v: Gtv, type: R_Type): Map<String, Gtv> {
            if (v !is GtvVirtual) {
                return GtvRtUtils.gtvToMap(ctx, v, type)
            }
            if (v !is GtvVirtualDictionary) {
                val cls = v.javaClass.simpleName
                throw GtvRtUtils.errGtv(ctx, "virtual:deserialized_type:$cls", "Wrong deserialized Gtv type: $cls")
            }
            return v.dict
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
            return Rt_VirtualTupleValue(v, type, rtFieldValues)
        }

        private fun decodeFields(ctx: GtvToRtContext, type: R_VirtualTupleType, v: Gtv): List<Gtv?> {
            return if (v !is GtvVirtual) {
                GtvRtConversion_Tuple.gtvArrayToFields(ctx, type.innerType, v)
            } else {
                GtvRtConversion_VirtualStruct.decodeVirtualArray(ctx, type, v, type.innerType.fields.size)
            }
        }
    }
}

object GtvRtUtils {
    fun gtvToInteger(ctx: GtvToRtContext, gtv: Gtv, rellType: R_Type): Long {
        return when (gtv.type) {
            GtvType.INTEGER -> {
                gtv.asInteger()
            }
            GtvType.BIGINTEGER -> {
                val v = gtv.asBigInteger()
                if (ctx.strictGtvConversion) {
                    throw errGtvType(ctx, rellType, gtv, GtvType.INTEGER, "invalid value: '$v'")
                }
                try {
                    return v.longValueExact()
                } catch (e: ArithmeticException) {
                    throw errGtvType(ctx, rellType, "out_of_range:$v", "value out of range: $v")
                }
            }
            else -> throw errGtvType(ctx, rellType, gtv, GtvType.INTEGER, null)
        }
    }

    fun gtvToBigInteger(ctx: GtvToRtContext, gtv: Gtv, rellType: R_Type): BigInteger {
        return when {
            gtv.type == GtvType.BIGINTEGER -> try {
                gtv.asBigInteger()
            } catch (e: UserMistake) {
                throw errGtvType(ctx, rellType, gtv, GtvType.BIGINTEGER, e)
            }
            ctx.pretty && gtv.type == GtvType.INTEGER -> try {
                gtv.asInteger().toBigInteger()
            } catch (e: UserMistake) {
                throw errGtvType(ctx, rellType, gtv, GtvType.BIGINTEGER, e)
            }
            else -> throw errGtvType(ctx, rellType, gtv, GtvType.BIGINTEGER, null)
        }
    }

    fun gtvToBoolean(ctx: GtvToRtContext, gtv: Gtv, rellType: R_Type): Boolean {
        val i = try {
            gtv.asInteger()
        } catch (e: UserMistake) {
            throw errGtvType(ctx, rellType, gtv, GtvType.INTEGER, e)
        }

        return when (i) {
            0L -> false
            1L -> true
            else -> throw errGtvType(ctx, rellType, "bad_value:$i", "expected 0 or 1, actual $i")
        }
    }

    fun gtvToString(ctx: GtvToRtContext, gtv: Gtv, rellType: R_Type): String {
        try {
            return gtv.asString()
        } catch (e: UserMistake) {
            throw errGtvType(ctx, rellType, gtv, GtvType.STRING, e)
        }
    }

    fun gtvToByteArray(ctx: GtvToRtContext, gtv: Gtv, rellType: R_Type): ByteArray {
        try {
            return gtv.asByteArray(convert = !ctx.strictGtvConversion)
        } catch (e: UserMistake) {
            val exp = immListOf(GtvType.BYTEARRAY, GtvType.STRING)
            if (gtv.type in exp) {
                throw errGtvType(ctx, rellType, "bad_value:${gtv.type}", e.message ?: "invalid value")
            } else {
                val code = "${exp.joinToString(",")}:${gtv.type}"
                val msg = "expected $exp, actual ${gtv.type}"
                throw errGtvType(ctx, rellType, code, msg)
            }
        }
    }

    fun gtvToJson(ctx: GtvToRtContext, gtv: Gtv, rellType: R_Type): Rt_Value {
        val str = try {
            gtv.asString()
        } catch (e: UserMistake) {
            throw errGtvType(ctx, rellType, gtv, GtvType.STRING, e)
        }
        try {
            return Rt_JsonValue.parse(str)
        } catch (e: IllegalArgumentException) {
            throw errGtvType(ctx, rellType, "bad_value", e.message ?: "invalid value")
        }
    }

    fun gtvToArray(ctx: GtvToRtContext, gtv: Gtv, size: Int, errCode: String, rellType: R_Type): Array<out Gtv> {
        val array = gtvToArray(ctx, gtv, rellType)
        val actSize = array.size
        if (actSize != size) {
            throw errGtvType(ctx, rellType, "$errCode:$size:$actSize", "wrong array size: $actSize instead of $size")
        }
        return array
    }

    fun gtvToArray(ctx: GtvToRtContext, gtv: Gtv, rellType: R_Type): Array<out Gtv> {
        try {
            return gtv.asArray()
        } catch (e: UserMistake) {
            throw errGtvType(ctx, rellType, gtv, GtvType.ARRAY, e)
        }
    }

    fun gtvToMap(ctx: GtvToRtContext, gtv: Gtv, rellType: R_Type): Map<String, Gtv> {
        try {
            return gtv.asDict()
        } catch (e: UserMistake) {
            throw errGtvType(ctx, rellType, gtv, GtvType.DICT, e)
        }
    }

    private fun errGtvType(ctx: GtvToRtContext, rellType: R_Type, actualGtv: Gtv, expectedGtvType: GtvType, e: UserMistake): Rt_Exception {
        return errGtvType(ctx, rellType, actualGtv, expectedGtvType, e.message)
    }

    private fun errGtvType(ctx: GtvToRtContext, rellType: R_Type, actualGtv: Gtv, expectedGtvType: GtvType, errMsg: String?): Rt_Exception {
        val code = "$expectedGtvType:${actualGtv.type}"
        val msg = when {
            actualGtv.type != expectedGtvType -> "expected $expectedGtvType, actual ${actualGtv.type}"
            !errMsg.isNullOrBlank() -> errMsg
            else -> actualGtv.type.name
        }
        return errGtvType(ctx, rellType, code, msg)
    }

    fun errGtvType(ctx: GtvToRtContext, rellType: R_Type, code: String, msg: String): Rt_Exception {
        val fullCode = "type:[${rellType.strCode()}]:$code"
        val fullMsg = "Decoding type '${rellType.str()}': $msg"
        return errGtv(ctx, fullCode, fullMsg)
    }

    fun errGtv(ctx: GtvToRtContext, code: String, msg: String): Rt_Exception {
        var code2 = code
        var msg2 = msg
        if (ctx.symbol != null) {
            val symCodeMsg = ctx.symbol.codeMsg()
            code2 = "$code:${symCodeMsg.code}"
            msg2 = "$msg (${symCodeMsg.msg})"
        }
        return Rt_GtvError.exception(code2, msg2)
    }
}
