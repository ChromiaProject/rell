/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.common.exception.UserMistake
import net.postchain.gtv.*
import net.postchain.gtv.merkle.proof.GtvMerkleProofTreeFactory
import net.postchain.gtv.merkle.proof.toGtvVirtual
import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_StructDefinition
import net.postchain.rell.base.model.rr.RR_TupleField
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.mapToImmList

/**
 * GTV-conversion builders for [Rt_Interpreter]'s RR-driven [Rt_ValueClass] construction.
 *
 * Composite types delegate to inner types' own [Rt_ValueClass.gtvConversion] via [Rt_Interpreter.resolveType];
 * virtual types decode through the GTV Merkle proof tree.
 */

/**
 * Build a GTV conversion for an [RR_Type], delegating to inner types' own
 * [Rt_ValueClass.gtvConversion] when needed.
 */
internal fun Rt_InterpreterImpl.buildCompositeGtvConversion(type: RR_Type): Rt_GtvCompatibleValueClass<*>? = when (type) {
    is RR_Type.Primitive -> primitiveValueClass(type.kind)?.gtvConversion
    is RR_Type.Null -> Rt_LazyGtvAdapter { Rt_NullValue.gtvConversion }
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
 * [decodeVirtualElement] but works on [RR_Type] / [Rt_ValueClass].
 * For composite virtual targets, the inner type's gtvConversion is recursively invoked
 * on the GTV proof tree (already deserialized at the top level).
 */
private fun Rt_InterpreterImpl.decodeVirtualElementRR(ctx: GtvToRtContext, innerType: RR_Type, gtv: Gtv): Rt_Value =
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

private fun Rt_InterpreterImpl.decodeVirtualStructFromGtv(
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

private fun Rt_InterpreterImpl.decodeVirtualListFromGtv(
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

private fun Rt_InterpreterImpl.decodeVirtualMapFromGtv(
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

private fun Rt_InterpreterImpl.decodeVirtualTupleFromGtv(
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

private fun Rt_InterpreterImpl.buildVirtualStructGtvConversion(type: RR_Type.VirtualStruct): Rt_GtvCompatibleValueClass<*> {
    return object: Rt_UntypedGtvConversion(rrTypeName(type)) {
        override fun toGtv(value: Rt_Value, pretty: Boolean): Gtv =
            throw Rt_GtvError.exception("virtual:to_gtv", "Cannot convert virtual to Gtv")

        override fun fromGtv(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
            val virtual = deserializeVirtualGtv(ctx, gtv)
            return decodeVirtualStructFromGtv(ctx, RR_Type.Struct(type.defIndex), virtual)
        }
    }
}

private fun Rt_InterpreterImpl.buildVirtualListGtvConversion(type: RR_Type.VirtualList): Rt_GtvCompatibleValueClass<*> {
    return object: Rt_UntypedGtvConversion(rrTypeName(type)) {
        override fun toGtv(value: Rt_Value, pretty: Boolean): Gtv =
            throw Rt_GtvError.exception("virtual:to_gtv", "Cannot convert virtual to Gtv")

        override fun fromGtv(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
            val virtual = deserializeVirtualGtv(ctx, gtv)
            return decodeVirtualListFromGtv(ctx, type.element, virtual, asSet = false, outerRrType = type)
        }
    }
}

private fun Rt_InterpreterImpl.buildVirtualSetGtvConversion(type: RR_Type.VirtualSet): Rt_GtvCompatibleValueClass<*> {
    return object: Rt_UntypedGtvConversion(rrTypeName(type)) {
        override fun toGtv(value: Rt_Value, pretty: Boolean): Gtv =
            throw Rt_GtvError.exception("virtual:to_gtv", "Cannot convert virtual to Gtv")

        override fun fromGtv(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
            val virtual = deserializeVirtualGtv(ctx, gtv)
            return decodeVirtualListFromGtv(ctx, type.element, virtual, asSet = true, outerRrType = type)
        }
    }
}

private fun Rt_InterpreterImpl.buildVirtualMapGtvConversion(type: RR_Type.VirtualMap): Rt_GtvCompatibleValueClass<*> =
    object: Rt_UntypedGtvConversion(rrTypeName(type)) {
        override fun toGtv(value: Rt_Value, pretty: Boolean): Gtv =
            throw Rt_GtvError.exception("virtual:to_gtv", "Cannot convert virtual to Gtv")

        override fun fromGtv(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
            val virtual = deserializeVirtualGtv(ctx, gtv)
            return decodeVirtualMapFromGtv(ctx, RR_Type.Map(type.key, type.value), virtual)
        }
    }

private fun Rt_InterpreterImpl.buildVirtualTupleGtvConversion(type: RR_Type.VirtualTuple): Rt_GtvCompatibleValueClass<*> =
    object: Rt_UntypedGtvConversion(rrTypeName(type)) {
        override fun toGtv(value: Rt_Value, pretty: Boolean): Gtv =
            throw Rt_GtvError.exception("virtual:to_gtv", "Cannot convert virtual to Gtv")

        override fun fromGtv(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
            val virtual = deserializeVirtualGtv(ctx, gtv)
            return decodeVirtualTupleFromGtv(ctx, RR_Type.Tuple(type.fields), virtual)
        }
    }

private fun Rt_InterpreterImpl.buildNullableGtvConversion(type: RR_Type.Nullable): Rt_GtvCompatibleValueClass<*> =
    Rt_NullValue.gtvConversionNullable(lazy { resolveType(type.value).gtvConversion!! })

private fun Rt_InterpreterImpl.buildListLikeGtvConversion(
    outerType: RR_Type,
    elementType: RR_Type,
    isSet: Boolean,
): Rt_GtvCompatibleValueClass<*> {
    val typeName = rrTypeName(outerType)
    val elementConversion = lazy { resolveType(elementType).gtvConversion!! }
    val rtType = lazy { resolveType(outerType) }
    return if (isSet) {
        Rt_SetValue.gtvConversion(typeName, elementConversion, rtType)
    } else {
        Rt_ListValue.gtvConversion(typeName, elementConversion, rtType)
    }
}

private fun Rt_InterpreterImpl.buildMapGtvConversion(type: RR_Type.Map): Rt_GtvCompatibleValueClass<*> =
    Rt_MapValue.gtvConversion(
        typeName = rrTypeName(type),
        isTextKey = type.key is RR_Type.Primitive && (type.key as RR_Type.Primitive).kind == RR_PrimitiveKind.TEXT,
        keyConversion = lazy { resolveType(type.key).gtvConversion!! },
        valueConversion = lazy { resolveType(type.value).gtvConversion!! },
        rtType = lazy { resolveType(type) },
    )

private fun Rt_InterpreterImpl.buildTupleGtvConversion(type: RR_Type.Tuple): Rt_GtvCompatibleValueClass<*> =
    Rt_TupleValue.gtvConversion(
        typeName = rrTypeName(type),
        fieldNames = type.fields.mapToImmList { it.name },
        fieldConversions = lazy {
            type.fields.mapToImmList { resolveType(it.type).gtvConversion!! }
        },
        rtType = lazy { resolveType(type) },
    )

internal fun Rt_InterpreterImpl.buildStructGtvConversion(
    rrType: RR_Type.Struct,
    structDef: RR_StructDefinition,
): Rt_GtvCompatibleValueClass<*> = object: Rt_UntypedGtvConversion(structDef.base.appLevelName) {
    override fun toGtv(value: Rt_Value, pretty: Boolean): Gtv {
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

    override fun fromGtv(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
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
        // Apply attribute validators (size constraints, etc.) — matches the R_Type-rooted path.
        for ((i, attr) in attrs.withIndex()) {
            attr.sizeConstraint?.let { checkSizeConstraint(it, rtAttrs[i]) }
        }
        return ctx.rtValue { Rt_StructValue(rtTypeSelf, attrNamesList, rtAttrs.toMutableList()) }
    }
}
