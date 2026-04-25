/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.common.exception.UserMistake
import net.postchain.gtv.*
import net.postchain.gtv.merkle.proof.GtvMerkleProofTreeFactory
import net.postchain.gtv.merkle.proof.toGtvVirtual
import net.postchain.rell.base.lib.type.*
import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_StructDefinition
import net.postchain.rell.base.model.rr.RR_TupleField
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.utils.immListOf

/**
 * GTV-conversion builders for [Rt_Interpreter]'s RR-driven [Rt_Type] construction.
 *
 * Composite types delegate to inner types' own [Rt_Type.gtvConversion] via [Rt_Interpreter.resolveType];
 * virtual types decode through the GTV Merkle proof tree.
 */

/**
 * Build a GTV conversion for an [RR_Type], delegating to inner types' own
 * [Rt_Type.gtvConversion] when needed. This is the canonical RR-driven path —
 * no [R_Type] involved.
 */
internal fun Rt_Interpreter.buildCompositeGtvConversion(type: RR_Type): Rt_TypeGtvConversion? = when (type) {
    is RR_Type.Primitive -> primitiveGtvConversion(type.kind)
    is RR_Type.Null -> Rt_TypeGtvConversionLazy { GtvRtConversion_Null }
    is RR_Type.Nullable -> buildNullableGtvConversion(type)
    is RR_Type.List -> buildListLikeGtvConversion(type, type.element, isSet = false)
    is RR_Type.Set -> buildListLikeGtvConversion(type, type.element, isSet = true)
    is RR_Type.Map -> buildMapGtvConversion(type)
    is RR_Type.Tuple -> buildTupleGtvConversion(type)
    is RR_Type.VirtualList -> buildVirtualListGtvConversion(type)
    is RR_Type.VirtualSet -> buildVirtualSetGtvConversion(type)
    is RR_Type.VirtualMap -> buildVirtualMapGtvConversion(type)
    is RR_Type.VirtualTuple -> buildVirtualTupleGtvConversion(type)
    is RR_Type.VirtualStruct -> buildVirtualStructGtvConversion(type)
    else -> null
}

/**
 * Decode an inner element of a virtual collection/struct/tuple. Mirrors
 * [GtvRtConversion_Virtual.decodeVirtualElement] but works on [RR_Type] / [Rt_Type].
 * For composite virtual targets, the inner type's gtvConversion is recursively invoked
 * on the GTV proof tree (already deserialized at the top level).
 */
private fun Rt_Interpreter.decodeVirtualElementRR(ctx: GtvToRtContext, innerType: RR_Type, gtv: Gtv): Rt_Value =
    when (innerType) {
        is RR_Type.Struct -> decodeVirtualStructFromGtv(ctx, innerType, gtv)
        is RR_Type.List -> decodeVirtualListFromGtv(
            ctx,
            innerType.element,
            gtv,
            asSet = false,
            outerRrType = RR_Type.VirtualList(innerType.element),
        )

        is RR_Type.Set -> decodeVirtualListFromGtv(
            ctx,
            innerType.element,
            gtv,
            asSet = true,
            outerRrType = RR_Type.VirtualSet(innerType.element),
        )

        is RR_Type.Map -> decodeVirtualMapFromGtv(ctx, innerType, gtv)
        is RR_Type.Tuple -> decodeVirtualTupleFromGtv(ctx, innerType, gtv)
        is RR_Type.Nullable -> {
            if (gtv.isNull()) Rt_NullValue
            else decodeVirtualElementRR(ctx, innerType.value, gtv)
        }

        else -> resolveType(innerType).gtvConversion!!.gtvToRt(ctx, gtv)
    }

private fun deserializeVirtualGtv(ctx: GtvToRtContext, gtv: Gtv): Gtv {
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

private fun decodeVirtualArrayElements(ctx: GtvToRtContext, gtv: Gtv): List<Gtv?> {
    if (gtv !is GtvVirtual) {
        return try {
            gtv.asArray().toList()
        } catch (_: UserMistake) {
            throw GtvRtUtils.errGtv(
                ctx, "virtual:gtv_type:${gtv.type}",
                "Expected ARRAY, got ${gtv.type}",
            )
        }
    }
    if (gtv !is GtvVirtualArray) {
        val cls = gtv.javaClass.simpleName
        throw GtvRtUtils.errGtv(ctx, "virtual:deserialized_type:$cls", "Wrong deserialized Gtv type: $cls")
    }
    return gtv.array.toList()
}

private fun decodeVirtualDictEntries(ctx: GtvToRtContext, gtv: Gtv): Map<String, Gtv> = when (gtv) {
    !is GtvVirtual -> {
        try {
            gtv.asDict()
        } catch (_: UserMistake) {
            throw GtvRtUtils.errGtv(
                ctx, "virtual:gtv_type:${gtv.type}",
                "Expected DICT, got ${gtv.type}",
            )
        }
    }

    !is GtvVirtualDictionary -> {
        val cls = gtv.javaClass.simpleName
        throw GtvRtUtils.errGtv(ctx, "virtual:deserialized_type:$cls", "Wrong deserialized Gtv type: $cls")
    }

    else -> gtv.dict
}

private fun Rt_Interpreter.decodeVirtualStructFromGtv(
    ctx: GtvToRtContext,
    type: RR_Type.Struct,
    gtv: Gtv,
): Rt_Value {
    val structDef = rrApp.allStructs[type.defIndex]
    val attrs = structDef.struct.attributesList
    val structName = structDef.struct.name
    val rrVirtualType = RR_Type.VirtualStruct(type.defIndex)
    val rtType = resolveType(rrVirtualType)
    val innerStructRtType = resolveType(type)

    val gtvFields: List<Gtv?> = decodeVirtualArrayElements(ctx, gtv)
    if (gtvFields.size > attrs.size) {
        throw GtvRtUtils.errGtv(
            ctx, "struct_size:$structName:${attrs.size}:${attrs.size}:${gtvFields.size}",
            "Wrong Gtv array size for struct '$structName': ${gtvFields.size} instead of ${attrs.size}",
        )
    }
    val rtAttrs: List<Rt_Value?> = attrs.mapIndexed { i, attr ->
        val gtvAttr = if (i < gtvFields.size) gtvFields[i] else null
        if (gtvAttr == null) null else {
            val attrCtx = ctx.updateSymbol(GtvToRtSymbol_AttrName(structName, attr.name))
            decodeVirtualElementRR(attrCtx, attr.type, gtvAttr)
        }
    }
    val attrNames = attrs.map { it.name }
    return Rt_VirtualStructValue(gtv, rtType, innerStructRtType, structName, attrNames, rtAttrs)
}

private fun Rt_Interpreter.decodeVirtualListFromGtv(
    ctx: GtvToRtContext,
    elementType: RR_Type,
    gtv: Gtv,
    asSet: Boolean,
    outerRrType: RR_Type,
): Rt_Value {
    val gtvElements = decodeVirtualArrayElements(ctx, gtv)
    val rtElements: List<Rt_Value?> = gtvElements.map {
        if (it == null) null else decodeVirtualElementRR(ctx, elementType, it)
    }
    val outerRtType = resolveType(outerRrType)
    val innerCollectionRtType = resolveType(
        if (asSet) RR_Type.Set(elementType) else RR_Type.List(elementType),
    )
    return if (asSet) {
        Rt_VirtualSetValue(gtv, outerRtType, innerCollectionRtType, rtElements.filterNotNull().toMutableSet())
    } else {
        Rt_VirtualListValue(gtv, outerRtType, innerCollectionRtType, rtElements)
    }
}

private fun Rt_Interpreter.decodeVirtualMapFromGtv(
    ctx: GtvToRtContext,
    mapType: RR_Type.Map,
    gtv: Gtv,
): Rt_Value {
    val gtvDict = decodeVirtualDictEntries(ctx, gtv)
    val rtMap: Map<Rt_Value, Rt_Value> = gtvDict
        .mapValues { (_, v) -> decodeVirtualElementRR(ctx, mapType.value, v) }
        .mapKeys { (k, _) -> Rt_TextValue.get(k) }
    val rrVirtualType = RR_Type.VirtualMap(mapType.key, mapType.value)
    val outerRtType = resolveType(rrVirtualType)
    val innerMapRtType = resolveType(mapType)
    // Virtual map entry type is (text, virtual<value>) — represented in the R_ model as a tuple type.
    val virtualEntryRtType = resolveType(
        RR_Type.Tuple(
            immListOf(
                RR_TupleField(null, mapType.key),
                RR_TupleField(null, wrapInVirtualIfApplicable(mapType.value)),
            ),
        ),
    )
    return Rt_VirtualMapValue(gtv, outerRtType, virtualEntryRtType, innerMapRtType, rtMap)
}

private fun Rt_Interpreter.decodeVirtualTupleFromGtv(
    ctx: GtvToRtContext,
    tupleType: RR_Type.Tuple,
    gtv: Gtv,
): Rt_Value {
    val gtvFields: List<Gtv?> = decodeVirtualArrayElements(ctx, gtv)
    if (gtvFields.size > tupleType.fields.size) {
        throw GtvRtUtils.errGtv(
            ctx, "virtual:tuple_size:${tupleType.fields.size}:${gtvFields.size}",
            "Wrong virtual tuple size: ${gtvFields.size} instead of ${tupleType.fields.size}",
        )
    }
    val rtFields: List<Rt_Value?> = tupleType.fields.mapIndexed { i, field ->
        val gtvField = if (i < gtvFields.size) gtvFields[i] else null
        if (gtvField == null) null else decodeVirtualElementRR(ctx, field.type, gtvField)
    }
    val rrVirtualType = RR_Type.VirtualTuple(tupleType.fields)
    val outerRtType = resolveType(rrVirtualType)
    val innerTupleRtType = resolveType(tupleType)
    val fieldNames = tupleType.fields.map { it.name }
    return Rt_VirtualTupleValue(gtv, outerRtType, innerTupleRtType, fieldNames, rtFields)
}

/** Inside a virtual<> wrapper, child composite types are themselves virtual. */
private fun wrapInVirtualIfApplicable(type: RR_Type): RR_Type = when (type) {
    is RR_Type.Struct -> RR_Type.VirtualStruct(type.defIndex)
    is RR_Type.List -> RR_Type.VirtualList(type.element)
    is RR_Type.Set -> RR_Type.VirtualSet(type.element)
    is RR_Type.Map -> RR_Type.VirtualMap(type.key, type.value)
    is RR_Type.Tuple -> RR_Type.VirtualTuple(type.fields)
    else -> type
}

private fun Rt_Interpreter.buildVirtualStructGtvConversion(type: RR_Type.VirtualStruct): Rt_TypeGtvConversion {
    return object: Rt_TypeGtvConversion {
        override fun rtToGtv(value: Rt_Value, pretty: Boolean): Gtv =
            throw Rt_GtvError.exception("virtual:to_gtv", "Cannot convert virtual to Gtv")

        override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
            val virtual = deserializeVirtualGtv(ctx, gtv)
            return decodeVirtualStructFromGtv(ctx, RR_Type.Struct(type.defIndex), virtual)
        }
    }
}

private fun Rt_Interpreter.buildVirtualListGtvConversion(type: RR_Type.VirtualList): Rt_TypeGtvConversion {
    return object: Rt_TypeGtvConversion {
        override fun rtToGtv(value: Rt_Value, pretty: Boolean): Gtv =
            throw Rt_GtvError.exception("virtual:to_gtv", "Cannot convert virtual to Gtv")

        override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
            val virtual = deserializeVirtualGtv(ctx, gtv)
            return decodeVirtualListFromGtv(ctx, type.element, virtual, asSet = false, outerRrType = type)
        }
    }
}

private fun Rt_Interpreter.buildVirtualSetGtvConversion(type: RR_Type.VirtualSet): Rt_TypeGtvConversion {
    return object: Rt_TypeGtvConversion {
        override fun rtToGtv(value: Rt_Value, pretty: Boolean): Gtv =
            throw Rt_GtvError.exception("virtual:to_gtv", "Cannot convert virtual to Gtv")

        override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
            val virtual = deserializeVirtualGtv(ctx, gtv)
            return decodeVirtualListFromGtv(ctx, type.element, virtual, asSet = true, outerRrType = type)
        }
    }
}

private fun Rt_Interpreter.buildVirtualMapGtvConversion(type: RR_Type.VirtualMap): Rt_TypeGtvConversion =
    object: Rt_TypeGtvConversion {
        override fun rtToGtv(value: Rt_Value, pretty: Boolean): Gtv =
            throw Rt_GtvError.exception("virtual:to_gtv", "Cannot convert virtual to Gtv")

        override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
            val virtual = deserializeVirtualGtv(ctx, gtv)
            return decodeVirtualMapFromGtv(ctx, RR_Type.Map(type.key, type.value), virtual)
        }
    }

private fun Rt_Interpreter.buildVirtualTupleGtvConversion(type: RR_Type.VirtualTuple): Rt_TypeGtvConversion =
    object: Rt_TypeGtvConversion {
        override fun rtToGtv(value: Rt_Value, pretty: Boolean): Gtv =
            throw Rt_GtvError.exception("virtual:to_gtv", "Cannot convert virtual to Gtv")

        override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
            val virtual = deserializeVirtualGtv(ctx, gtv)
            return decodeVirtualTupleFromGtv(ctx, RR_Type.Tuple(type.fields), virtual)
        }
    }

private fun Rt_Interpreter.buildNullableGtvConversion(type: RR_Type.Nullable): Rt_TypeGtvConversion =
    object: Rt_TypeGtvConversion {
        override fun rtToGtv(value: Rt_Value, pretty: Boolean): Gtv = if (value == Rt_NullValue) {
            GtvNull
        } else {
            resolveType(type.value).gtvConversion!!.rtToGtv(value, pretty)
        }

        override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value = if (gtv.type == GtvType.NULL) {
            Rt_NullValue
        } else {
            resolveType(type.value).gtvConversion!!.gtvToRt(ctx, gtv)
        }
    }

private fun Rt_Interpreter.buildListLikeGtvConversion(
    outerType: RR_Type,
    elementType: RR_Type,
    isSet: Boolean,
): Rt_TypeGtvConversion {
    val typeName = rrTypeName(outerType)
    return object: Rt_TypeGtvConversion {
        override fun rtToGtv(value: Rt_Value, pretty: Boolean): Gtv {
            val elemConv = resolveType(elementType).gtvConversion!!
            val items = if (isSet) value.asSet() else value.asList()
            val arr = items.map { elemConv.rtToGtv(it, pretty) }.toTypedArray()
            return GtvArray(arr)
        }

        override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
            val elemConv = resolveType(elementType).gtvConversion!!
            val arr = GtvRtUtils.gtvToArray(ctx, gtv, typeName, 0, Int.MAX_VALUE)
            val items = arr.map { elemConv.gtvToRt(ctx, it) }
            val outerRtType = resolveType(if (isSet) RR_Type.Set(elementType) else RR_Type.List(elementType))
            return if (isSet) {
                val set = GtvRtConversion_Set.listToSet(ctx, items)
                Rt_SetValue(outerRtType, set)
            } else {
                Rt_ListValue(outerRtType, items.toMutableList())
            }
        }
    }
}

private fun Rt_Interpreter.buildMapGtvConversion(type: RR_Type.Map): Rt_TypeGtvConversion =
    object: Rt_TypeGtvConversion {
        override fun rtToGtv(value: Rt_Value, pretty: Boolean): Gtv {
            val keyConv = resolveType(type.key).gtvConversion!!
            val valConv = resolveType(type.value).gtvConversion!!
            val map = value.asMap()
            val isStringKey =
                type.key is RR_Type.Primitive && (type.key as RR_Type.Primitive).kind == RR_PrimitiveKind.TEXT
            return if (pretty && isStringKey) {
                val gtvMap = map.entries.associate { (k, v) ->
                    k.asString() to valConv.rtToGtv(v, pretty)
                }
                GtvFactory.gtv(gtvMap)
            } else {
                val pairs = map.entries.map { (k, v) ->
                    GtvArray(arrayOf(keyConv.rtToGtv(k, pretty), valConv.rtToGtv(v, pretty)))
                }.toTypedArray<Gtv>()
                GtvArray(pairs)
            }
        }

        override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
            val keyConv = resolveType(type.key).gtvConversion!!
            val valConv = resolveType(type.value).gtvConversion!!
            val outerRtType = resolveType(type)
            val typeName = outerRtType.name
            val resultMap = mutableMapOf<Rt_Value, Rt_Value>()
            val keyIsText =
                type.key is RR_Type.Primitive && (type.key as RR_Type.Primitive).kind == RR_PrimitiveKind.TEXT
            if (keyIsText && gtv.type == GtvType.DICT) {
                val dict = GtvRtUtils.gtvToMap(ctx, gtv, typeName)
                for ((k, v) in dict) {
                    resultMap[Rt_TextValue.get(k)] = valConv.gtvToRt(ctx, v)
                }
            } else {
                val arr = GtvRtUtils.gtvToArray(ctx, gtv, typeName, 0, Int.MAX_VALUE)
                for (pair in arr) {
                    val pairArr = GtvRtUtils.gtvToArray(ctx, pair, typeName, 2, 2)
                    val key = keyConv.gtvToRt(ctx, pairArr[0])
                    val value = valConv.gtvToRt(ctx, pairArr[1])
                    if (key in resultMap) {
                        throw GtvRtUtils.errGtv(
                            ctx, "map_dup_key:${key.strCode()}",
                            "Duplicate map key: ${key.str()}",
                        )
                    }
                    resultMap[key] = value
                }
            }
            return Rt_MapValue(outerRtType, resultMap)
        }
    }

private fun Rt_Interpreter.buildTupleGtvConversion(type: RR_Type.Tuple): Rt_TypeGtvConversion {
    return object: Rt_TypeGtvConversion {
        override fun rtToGtv(value: Rt_Value, pretty: Boolean): Gtv {
            val elements = value.asTuple()
            val allNamed = type.fields.all { it.name != null }
            return if (pretty && allNamed) {
                val map = type.fields.mapIndexed { i, f ->
                    f.name!! to resolveType(f.type).gtvConversion!!.rtToGtv(elements[i], pretty)
                }.toMap()
                GtvFactory.gtv(map)
            } else {
                val arr = type.fields.mapIndexed { i, f ->
                    resolveType(f.type).gtvConversion!!.rtToGtv(elements[i], pretty)
                }.toTypedArray()
                GtvArray(arr)
            }
        }

        override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
            val outerRtType = resolveType(type)
            val typeName = outerRtType.name
            val allNamed = type.fields.all { it.name != null }
            val expectedCount = type.fields.size
            val values: List<Rt_Value> = if (ctx.pretty && allNamed && gtv.type == GtvType.DICT) {
                val dict = GtvRtUtils.gtvToMap(ctx, gtv, typeName)
                if (dict.size != expectedCount) {
                    throw GtvRtUtils.errGtv(
                        ctx, "tuple_count:$expectedCount:${dict.size}",
                        "Wrong Gtv dictionary size: ${dict.size} instead of $expectedCount",
                    )
                }
                type.fields.map { f ->
                    val key = f.name!!
                    val g = dict[key] ?: throw GtvRtUtils.errGtv(
                        ctx,
                        "tuple_nokey:$key", "Key missing in Gtv dictionary: '$key'",
                    )
                    resolveType(f.type).gtvConversion!!.gtvToRt(ctx, g)
                }
            } else {
                val arr = GtvRtUtils.gtvToArray(ctx, gtv, typeName, 0, Int.MAX_VALUE)
                if (arr.size != expectedCount) {
                    throw GtvRtUtils.errGtv(
                        ctx, "tuple_count:$expectedCount:${arr.size}",
                        "Wrong Gtv array size: ${arr.size} instead of $expectedCount",
                    )
                }
                type.fields.mapIndexed { i, f ->
                    resolveType(f.type).gtvConversion!!.gtvToRt(ctx, arr[i])
                }
            }
            return Rt_TupleValue(outerRtType, values)
        }
    }
}

internal fun Rt_Interpreter.buildStructGtvConversion(
    rrType: RR_Type.Struct,
    structDef: RR_StructDefinition,
): Rt_TypeGtvConversion {
    return object: Rt_TypeGtvConversion {
        override fun rtToGtv(value: Rt_Value, pretty: Boolean): Gtv {
            val rtStruct = value.asStruct()
            val attrs = structDef.struct.attributesList
            return if (pretty) {
                val gtvFields = attrs.mapIndexed { i, attr ->
                    attr.name to resolveType(attr.type).gtvConversion!!.rtToGtv(rtStruct.get(i), pretty)
                }.toMap()
                GtvFactory.gtv(gtvFields)
            } else {
                val gtvFields = attrs.mapIndexed { i, attr ->
                    resolveType(attr.type).gtvConversion!!.rtToGtv(rtStruct.get(i), pretty)
                }.toTypedArray()
                GtvArray(gtvFields)
            }
        }

        override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
            val attrs = structDef.struct.attributesList
            val rtTypeSelf = resolveType(rrType)
            // Attributes with default values can be omitted from the trailing positions of an array.
            val minArrayCount = attrs.indexOfLast { !it.hasDefaultValue } + 1

            val gtvDict: Map<String, Gtv>? = if (ctx.pretty && gtv.type == GtvType.DICT) {
                GtvRtUtils.gtvToMap(ctx, gtv, rtTypeSelf.name)
            } else null

            val gtvFields: List<Gtv?> = if (gtvDict != null) {
                attrs.map { attr -> gtvDict[attr.name] }
            } else {
                val rawArray = try {
                    gtv.asArray()
                } catch (_: UserMistake) {
                    throw GtvRtUtils.errGtvType(
                        ctx, rtTypeSelf.name,
                        "${GtvType.ARRAY}:${gtv.type}",
                        "expected ${GtvType.ARRAY}, actual ${gtv.type}",
                    )
                }
                if (rawArray.size < minArrayCount || rawArray.size > attrs.size) {
                    val expCountStr =
                        if (minArrayCount == attrs.size) "${attrs.size}" else "$minArrayCount..${attrs.size}"
                    throw GtvRtUtils.errGtv(
                        ctx,
                        "struct_size:${rtTypeSelf.name}:$minArrayCount:${attrs.size}:${rawArray.size}",
                        "Wrong Gtv array size for struct '${rtTypeSelf.name}': ${rawArray.size} instead of $expCountStr",
                    )
                }
                val list = rawArray.toMutableList<Gtv?>()
                while (list.size < attrs.size) list.add(null)
                list
            }

            val rtAttrs = attrs.mapIndexed { i, attr ->
                val gtvAttr = gtvFields.getOrNull(i)
                if (gtvAttr != null) {
                    val attrCtx = ctx.updateSymbol(GtvToRtSymbol_AttrName(rtTypeSelf.name, attr.name))
                    resolveType(attr.type).gtvConversion!!.gtvToRt(attrCtx, gtvAttr)
                } else {
                    ctx.getDefaultValueByDefId(
                        defId = structDef.base.defId,
                        attrIndex = i,
                        attrName = attr.name,
                        hasExpr = attr.hasDefaultValue,
                        typeName = rtTypeSelf.name,
                        kind = "struct",
                    )
                }
            }

            // Reject unknown keys AFTER all attributes have been decoded (matches the R_-based path).
            if (gtvDict != null) {
                val knownNames = attrs.mapTo(mutableSetOf()) { it.name }
                for (key in gtvDict.keys) {
                    if (key !in knownNames) {
                        throw GtvRtUtils.errGtv(
                            ctx, "struct_badkey:${rtTypeSelf.name}:$key",
                            "Wrong key in Gtv dictionary for type '${rtTypeSelf.name}': '$key'",
                        )
                    }
                }
            }

            val attrNamesList = attrs.map { it.name }
            return ctx.rtValue { Rt_StructValue(rtTypeSelf, attrNamesList, rtAttrs.toMutableList()) }
        }
    }
}
