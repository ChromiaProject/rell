/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.*
import net.postchain.rell.base.lib.type.*
import net.postchain.rell.base.lmodel.dsl.Ld_FunctionMetaBodyDsl
import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_TupleField
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.runtime.utils.Rt_ListComparator
import net.postchain.rell.base.runtime.utils.Rt_TupleComparator
import net.postchain.rell.base.utils.mapToImmList
import net.postchain.rell.base.utils.toImmList

/**
 * Runtime-side builders for composite [Rt_Type]s.
 *
 * These exist so that DSL `bodyMeta { ... }` blocks can work with [Rt_Type] directly
 * instead of touching the compiler's `R_Type` to construct `list<T>` / `set<T>` / `map<K,V>`
 * runtime types. They construct the outer [Rt_Type] directly from inner [Rt_Type] capabilities —
 * no round trip through `R_Type`.
 */

/** Runtime type `list<elem>`. */
fun rtListType(elem: Rt_Type): Rt_Type {
    val name = "list<${elem.name}>"
    return Rt_Type(
        rrType = RR_Type.List(elem.rrType!!),
        name = name,
        sqlAdapter = null,
        gtvConversion = listLikeGtvConversion(elem, name, isSet = false),
        comparator = elem.comparator?.let { Rt_ListComparator(it) },
        nativeConversion = null,
    )
}

/** Runtime type `set<elem>`. */
fun rtSetType(elem: Rt_Type): Rt_Type {
    val name = "set<${elem.name}>"
    return Rt_Type(
        rrType = RR_Type.Set(elem.rrType!!),
        name = name,
        sqlAdapter = null,
        gtvConversion = listLikeGtvConversion(elem, name, isSet = true),
        comparator = null,
        nativeConversion = null,
    )
}

/** Runtime type `map<key, value>`. */
fun rtMapType(key: Rt_Type, value: Rt_Type): Rt_Type {
    val name = "map<${key.name},${value.name}>"
    return Rt_Type(
        rrType = RR_Type.Map(key.rrType!!, value.rrType!!),
        name = name,
        sqlAdapter = null,
        gtvConversion = mapGtvConversion(key, value, name),
        comparator = null,
        nativeConversion = null,
    )
}

/** Runtime type for a tuple with the given element types (unnamed fields). */
fun rtTupleType(vararg elements: Rt_Type): Rt_Type {
    val fields = elements.map { RR_TupleField(null, it.rrType!!) }.toImmList()
    val name = elements.joinToString(",", "(", ")") { it.name }
    val fieldComparators = elements.map { it.comparator }
    val comparator = if (fieldComparators.all { it != null }) {
        Rt_TupleComparator(fieldComparators.mapToImmList { it!! })
    } else null
    val fieldConversions = elements.map { it.gtvConversion }
    val gtvConversion = if (fieldConversions.all { it != null }) {
        tupleGtvConversion(name, fieldConversions.map { it!! })
    } else null
    val result = Rt_Type(
        rrType = RR_Type.Tuple(fields),
        name = name,
        sqlAdapter = null,
        gtvConversion = gtvConversion,
        comparator = comparator,
        nativeConversion = null,
    )
    if (gtvConversion is TupleGtvConversion) {
        gtvConversion.rtType = result
    }
    return result
}

private fun tupleGtvConversion(
    typeName: String,
    fieldConversions: List<Rt_TypeGtvConversion>,
): TupleGtvConversion = TupleGtvConversion(typeName, fieldConversions)

private class TupleGtvConversion(
    private val typeName: String,
    private val fieldConversions: List<Rt_TypeGtvConversion>,
): Rt_TypeGtvConversion {
    lateinit var rtType: Rt_Type

    override fun rtToGtv(value: Rt_Value, pretty: Boolean): Gtv {
        val rtFields = value.asTuple()
        val gtvFields = rtFields.mapIndexed { i, rtField ->
            fieldConversions[i].rtToGtv(rtField, pretty)
        }.toTypedArray()
        return GtvArray(gtvFields)
    }

    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val arr = GtvRtUtils.gtvToArray(ctx, gtv, typeName, fieldConversions.size, fieldConversions.size)
        val rtFields = arr.mapIndexed { i, gtvField -> fieldConversions[i].gtvToRt(ctx, gtvField) }
        return Rt_TupleValue(rtType, rtFields)
    }
}

private fun listLikeGtvConversion(elem: Rt_Type, typeName: String, isSet: Boolean): Rt_TypeGtvConversion? {
    val elemConv = elem.gtvConversion ?: return null
    return object: Rt_TypeGtvConversion {
        override fun rtToGtv(value: Rt_Value, pretty: Boolean): Gtv {
            val items = if (isSet) value.asSet() else value.asList()
            val arr = items.map { elemConv.rtToGtv(it, pretty) }.toTypedArray()
            return GtvArray(arr)
        }

        override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
            val arr = GtvRtUtils.gtvToArray(ctx, gtv, typeName, 0, Int.MAX_VALUE)
            val items = arr.map { elemConv.gtvToRt(ctx, it) }
            return if (isSet) {
                val set = GtvRtConversion_Set.listToSet(ctx, items)
                Rt_SetValue(rtSetType(elem), set)
            } else {
                Rt_ListValue(rtListType(elem), items.toMutableList())
            }
        }
    }
}

private fun mapGtvConversion(key: Rt_Type, value: Rt_Type, typeName: String): Rt_TypeGtvConversion? {
    val keyConv = key.gtvConversion ?: return null
    val valConv = value.gtvConversion ?: return null
    val keyIsText = (key.rrType as? RR_Type.Primitive)?.kind == RR_PrimitiveKind.TEXT
    return object: Rt_TypeGtvConversion {
        override fun rtToGtv(value: Rt_Value, pretty: Boolean): Gtv {
            val map = value.asMap()
            return if (pretty && keyIsText) {
                val gtvMap = map.entries.associate { (k, v) -> k.asString() to valConv.rtToGtv(v, pretty) }
                GtvFactory.gtv(gtvMap)
            } else {
                val pairs = map.entries.map { (k, v) ->
                    GtvArray(arrayOf(keyConv.rtToGtv(k, pretty), valConv.rtToGtv(v, pretty)))
                }.toTypedArray<Gtv>()
                GtvArray(pairs)
            }
        }

        override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
            val result = mutableMapOf<Rt_Value, Rt_Value>()
            if (keyIsText && gtv.type == GtvType.DICT) {
                val dict = GtvRtUtils.gtvToMap(ctx, gtv, typeName)
                for ((k, v) in dict) {
                    result[Rt_TextValue.get(k)] = valConv.gtvToRt(ctx, v)
                }
            } else {
                val arr = GtvRtUtils.gtvToArray(ctx, gtv, typeName, 0, Int.MAX_VALUE)
                for (pair in arr) {
                    val pairArr = GtvRtUtils.gtvToArray(ctx, pair, typeName, 2, 2)
                    val k = keyConv.gtvToRt(ctx, pairArr[0])
                    val v = valConv.gtvToRt(ctx, pairArr[1])
                    if (k in result) {
                        throw GtvRtUtils.errGtv(ctx, "map_dup_key:${k.strCode()}", "Duplicate map key: ${k.str()}")
                    }
                    result[k] = v
                }
            }
            return Rt_MapValue(rtMapType(key, value), result)
        }
    }
}

// =============================================================================
// Ld_FunctionMetaBodyDsl extensions — let bodyMeta blocks work with Rt_Type directly
// =============================================================================

/** Type argument [name] as [Rt_Type]. Mirrors `fnBodyMeta.typeArg(name)` but returns [Rt_Type]. */
fun Ld_FunctionMetaBodyDsl.typeArgRt(name: String): Rt_Type = rTypeToRtType(fnBodyMeta.typeArg(name))

/** Two type arguments as [Rt_Type]s. */
fun Ld_FunctionMetaBodyDsl.typeArgsRt(name1: String, name2: String): Pair<Rt_Type, Rt_Type> {
    val (a, b) = fnBodyMeta.typeArgs(name1, name2)
    return rTypeToRtType(a) to rTypeToRtType(b)
}

/** Self type as [Rt_Type]. */
val Ld_FunctionMetaBodyDsl.selfTypeRt: Rt_Type get() = rTypeToRtType(fnBodyMeta.rSelfType)

/** Result type as [Rt_Type]. */
val Ld_FunctionMetaBodyDsl.resultTypeRt: Rt_Type get() = rTypeToRtType(fnBodyMeta.rResultType)
