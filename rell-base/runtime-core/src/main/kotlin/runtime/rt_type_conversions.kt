/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.*
import net.postchain.gtv.merkle.proof.GtvMerkleProofTreeFactory
import net.postchain.gtv.merkle.proof.toGtvVirtual
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.C_FeatureSwitch
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.rr.RR_EnumAttr
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.sql.PreparedStatementParams
import net.postchain.rell.base.sql.ResultSetRow
import net.postchain.rell.base.utils.ImmList
import org.jooq.DataType
import org.jooq.impl.SQLDataType

/**
 * Untyped base for GTV converters that can't pin a single [Rt_Value] subtype (entity / enum /
 * nullable / virtual / list / set / map / tuple / struct). Implements
 * [Rt_GtvCompatibleValueClass]<Rt_Value> directly with a degenerate `klass`/`cast`.
 * Subclasses override [toGtv] / [fromGtv] (the typed pair, which collapse to untyped here).
 */
abstract class Rt_UntypedGtvConversion(
    final override val name: String,
): Rt_GtvCompatibleValueClass<Rt_Value> {
    final override val klass = Rt_Value::class
    final override fun cast(v: Rt_Value): Rt_Value = v
}

/**
 * Untyped base for SQL adapters that can't pin a single [Rt_Value] subtype (entity / enum /
 * nullable / no-SQL fallback). Implements [Rt_SqlCompatibleValueClass]<Rt_Value> directly with
 * a degenerate `klass`/`cast`. Each subclass overrides [toSqlValue] / [toSql] / [fromSql].
 */
abstract class Rt_UntypedSqlAdapter(
    final override val name: String,
): Rt_SqlCompatibleValueClass<Rt_Value> {
    final override val klass = Rt_Value::class
    final override fun cast(v: Rt_Value): Rt_Value = v
}

class Rt_SqlAdapter_Entity(
    private val lazyRtType: Lazy<Rt_ValueClass<*>>,
    typeName: String,
    private val tableMetaName: String,
    private val externalChainIndex: Int,
): Rt_UntypedSqlAdapter(typeName) {
    override val sqlType: DataType<Long>
        get() = SQLDataType.BIGINT

    override fun toSqlValue(value: Rt_Value): Any = value.asObjectId()
    override fun toSql(value: Rt_Value, params: PreparedStatementParams, idx: Int) {
        params.setLong(idx, value.asObjectId())
    }
    override fun fromSql(row: ResultSetRow, idx: Int, nullable: Boolean): Rt_Value {
        val v = row.getLong(idx)
        return Rt_SqlNull.check(v == 0L, row, name, nullable) ?: Rt_EntityValue(lazyRtType.value, v)
    }
    override fun metaName(sqlCtx: Rt_SqlContext): String {
        val chain =
            if (externalChainIndex < 0) sqlCtx.mainChainMapping() else sqlCtx.chainMappingByIndex(externalChainIndex)
        return "class:${chain.chainId}:$tableMetaName"
    }
}

class Rt_SqlAdapter_Enum(
    private val lazyRtType: Lazy<Rt_ValueClass<*>>,
    typeName: String,
    private val attrs: ImmList<RR_EnumAttr>,
): Rt_UntypedSqlAdapter(typeName) {
    override val sqlType: DataType<Int>
        get() = SQLDataType.INTEGER

    override fun toSqlValue(value: Rt_Value): Any = value.asEnum().value
    override fun toSql(value: Rt_Value, params: PreparedStatementParams, idx: Int) {
        params.setInt(idx, value.asEnum().value)
    }
    override fun fromSql(row: ResultSetRow, idx: Int, nullable: Boolean): Rt_Value {
        val v = row.getInt(idx)
        return Rt_SqlNull.check(v == 0, row, name, nullable) ?: run {
            val attr = if (v < 0 || v >= attrs.size) null else attrs[v]
            requireNotNull(attr) { "$name: $v" }
            Rt_RR_EnumValue(lazyRtType.value, attr)
        }
    }
    override fun metaName(sqlCtx: Rt_SqlContext): String = "enum:$name"
}

class Rt_SqlAdapter_Nullable(
    private val inner: Rt_SqlCompatibleValueClass<*>,
): Rt_UntypedSqlAdapter(inner.name) {
    override val sqlType
        get() = null

    override fun isSqlCompatible(opts: C_CompilerOptions): Boolean =
        SQL_COMPATIBILITY_SWITCH.isActive(opts) && inner.isSqlCompatible(opts)

    override fun isAllowedForEntityAttributes(opts: C_CompilerOptions) = false

    override fun toSqlValue(value: Rt_Value): Any = inner.rtToSqlValue(value)

    override fun toSql(value: Rt_Value, params: PreparedStatementParams, idx: Int) {
        params.setBoolean(idx, value.asBoolean())
    }

    override fun fromSql(row: ResultSetRow, idx: Int, nullable: Boolean): Rt_Value =
        inner.fromSql(row, idx, true)

    override fun metaName(sqlCtx: Rt_SqlContext): String =
        throw Rt_Utils.errNotSupported("Nullable entity attributes are not supported")

    companion object {
        private val SQL_COMPATIBILITY_SWITCH = C_FeatureSwitch("0.13.10")
    }
}



// =============================================================================
// Virtual GTV decoders — top-level helpers shared across virtual factories.
// =============================================================================

internal fun deserializeVirtual(ctx: GtvToRtContext, gtv: Gtv): Gtv {
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
    return proof.toGtvVirtual()
}

internal fun decodeVirtualElement(ctx: GtvToRtContext, type: R_Type, gtv: Gtv): Rt_Value {
    return when (type) {
        is R_StructType -> decodeVirtualStruct(ctx, type.struct.virtualType, gtv)
        is R_ListType -> decodeVirtualList(ctx, type.virtualType, gtv)
        is R_SetType -> decodeVirtualSet(ctx, type.virtualType, gtv)
        is R_MapType -> decodeVirtualMap(ctx, type.virtualType, gtv)
        is R_TupleType -> decodeVirtualTuple(ctx, type.virtualType, gtv)
        is R_NullableType -> if (gtv.isNull()) Rt_NullValue else decodeVirtualElement(ctx, type.valueType, gtv)
        else -> gtvConversionFromR(type)!!.gtvToRt(ctx, gtv)
    }
}

internal fun decodeVirtualArray(ctx: GtvToRtContext, typeName: String, v: Gtv, maxSize: Int): List<Gtv?> {
    if (v !is GtvVirtualArray) {
        val cls = v.javaClass.simpleName
        throw GtvRtUtils.errGtv(ctx, "virtual:deserialized_type:$cls", "Wrong deserialized Gtv type: $cls")
    }
    val actualCount = v.array.size
    if (actualCount > maxSize) {
        throw Rt_StructValue.errWrongArraySize(ctx, typeName, maxSize, maxSize, actualCount)
    }
    return v.array.toList()
}

internal fun decodeVirtualElements(ctx: GtvToRtContext, innerType: R_CollectionType, v: Gtv): List<Rt_Value?> {
    val gtvElements = when (v) {
        !is GtvVirtual -> GtvRtUtils.gtvToArrayAny(ctx, v, innerType.name).toList()

        !is GtvVirtualArray -> {
            val cls = v.javaClass.simpleName
            throw GtvRtUtils.errGtv(ctx, "virtual:deserialized_type:$cls", "Wrong deserialized Gtv type: $cls")
        }

        else -> v.array.toList()
    }

    return gtvElements.map { if (it == null) null else decodeVirtualElement(ctx, innerType.elementType, it) }
}

internal fun decodeVirtualStruct(ctx: GtvToRtContext, type: R_VirtualStructType, v: Gtv): Rt_Value {
    val struct = type.innerType.struct
    val attrValues = if (v !is GtvVirtual) {
        Rt_StructValue.gtvToAttrValues(ctx, v, type.name, struct, struct.attributes.size)
    } else {
        decodeVirtualArray(ctx, type.name, v, struct.attributes.size)
    }
    val rtAttrValues = type.innerType.struct.attributesList.mapIndexed { i, attr ->
        val gtvAttr = attrValues.getOrNull(i)
        if (gtvAttr == null) null else {
            val attrCtx = ctx.updateSymbol(GtvToRtSymbol_Attr(type.name, attr))
            decodeVirtualElement(attrCtx, attr.type, gtvAttr)
        }
    }
    return Rt_VirtualStructValue(
        v,
        rTypeStub(type),
        rTypeStub(type.innerType),
        type.innerType.name,
        type.innerType.struct.attributesList.map { it.name },
        rtAttrValues,
    )
}

internal fun decodeVirtualTuple(ctx: GtvToRtContext, type: R_VirtualTupleType, v: Gtv): Rt_Value {
    val fieldValues = if (v !is GtvVirtual) {
        Rt_TupleValue.gtvArrayToFields(ctx, type.name, type.innerType.fields.size, v)
    } else {
        decodeVirtualArray(ctx, type.name, v, type.innerType.fields.size)
    }
    val rtFieldValues = type.innerType.fields.mapIndexed { i, attr ->
        val gtvAttr = fieldValues.getOrNull(i)
        if (gtvAttr == null) null else decodeVirtualElement(ctx, attr.type, gtvAttr)
    }
    return Rt_VirtualTupleValue(
        v,
        rTypeStub(type),
        rTypeStub(type.innerType),
        type.innerType.fields.map { it.name?.str },
        rtFieldValues,
    )
}

internal fun decodeVirtualList(ctx: GtvToRtContext, type: R_VirtualListType, v: Gtv): Rt_Value {
    val rtElements = decodeVirtualElements(ctx, type.innerType, v)
    return Rt_VirtualListValue(v, rTypeStub(type), rTypeStub(type.innerType), rtElements)
}

internal fun decodeVirtualSet(ctx: GtvToRtContext, type: R_VirtualSetType, v: Gtv): Rt_Value {
    val rtList = decodeVirtualElements(ctx, type.innerType, v)
    val rtSet = Rt_SetValue.listToSet(ctx, rtList.filterNotNull())
    return Rt_VirtualSetValue(v, rTypeStub(type), rTypeStub(type.innerType), rtSet)
}

internal fun decodeVirtualMap(ctx: GtvToRtContext, type: R_VirtualMapType, v: Gtv): Rt_Value {
    val gtvMap: Map<String, Gtv> = when (v) {
        !is GtvVirtual -> GtvRtUtils.gtvToMap(ctx, v, type.name)

        !is GtvVirtualDictionary -> {
            val cls = v.javaClass.simpleName
            throw GtvRtUtils.errGtv(ctx, "virtual:deserialized_type:$cls", "Wrong deserialized Gtv type: $cls")
        }

        else -> v.dict
    }

    val rtMap = gtvMap
        .mapValues { (_, gv) -> decodeVirtualElement(ctx, type.innerType.valueType, gv) }
        .mapKeys { (k, _) -> Rt_TextValue.get(k) as Rt_Value }

    return Rt_VirtualMapValue(
        v,
        rTypeStub(type),
        rTypeStub(type.virtualEntryType),
        rTypeStub(type.innerType),
        rtMap,
    )
}
