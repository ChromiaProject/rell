/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.Gtv
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.rr.RR_EnumAttr
import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.utils.mapToImmList

/**
 * Defers materializing a GTV conversion until first use — unblocks circular type references
 * during type-graph construction. Acts as the conversion itself by delegating both directions
 * to the wrapped instance.
 */
class Rt_LazyGtvAdapter(
    provider: () -> Rt_GtvCompatibleValueClass<*>,
): Rt_UntypedGtvConversion("?") {
    private val conv by lazy(provider)
    override fun toGtv(value: Rt_Value, pretty: Boolean): Gtv = conv.rtToGtv(value, pretty)
    override fun fromGtv(ctx: GtvToRtContext, gtv: Gtv): Rt_Value = conv.gtvToRt(ctx, gtv)
}

/**
 * Lightweight pre-interpreter [Rt_ValueClass] derived from an [R_Type]. Carries `rrType`
 * and `name`; intended for compile-time GTV encode/decode paths where the runtime
 * [Rt_Interpreter] is not yet available.
 *
 * For [R_EnumType] we materialize a capability-bearing stub: enum values produced by
 * [R_EnumDefinition.rtValues] are interned and reused at runtime (e.g. as SQL bind
 * parameters via the at-expression path), so the surrounding `type` must carry a usable
 * [Rt_SqlCompatibleValueClass] and [Rt_GtvCompatibleValueClass]. Without it, binding an
 * enum-typed column blew up with `No SQL adapter for type: <enum>`.
 *
 * Other R_Types stay on [Rt_GenericRrType] — their pre-interpreter consumers only read
 * `name` (and never call back into capabilities of the surrounding rtType).
 */
internal fun rTypeStub(rType: R_Type): Rt_ValueClass<*> = when (rType) {
    is R_EnumType -> Rt_R_EnumStubType(rType)
    else -> Rt_GenericRrType(rTypeToRRType(rType), rType.name)
}

/**
 * Capability-bearing R-driven enum stub. Mirrors the runtime [Rt_EnumType] (built via the
 * RR registry in the interpreter) for callers that only hold an [R_EnumDefinition] — the
 * enum values cache, GTV decoders, etc. — without forcing a full interpreter context.
 */
private class Rt_R_EnumStubType(rType: R_EnumType): Rt_ValueClass<Rt_Value> {
    override val name: String = rType.name
    override val rrType: RR_Type = rTypeToRRType(rType)
    private val attrs = rType.enum.attrs.mapToImmList { RR_EnumAttr(it.rName, it.value) }
    private val enumDef = rType.enum

    override val sqlAdapter: Rt_SqlCompatibleValueClass<*> by lazy {
        Rt_SqlAdapter_Enum(lazyRtType = lazyOf(this), typeName = name, attrs = attrs)
    }
    override val gtvConversion: Rt_GtvCompatibleValueClass<*> by lazy {
        Rt_RR_EnumValue.gtvConversion(enumDef)
    }

    override fun toString() = name
    override fun equals(other: Any?) = other is Rt_R_EnumStubType && name == other.name
    override fun hashCode() = name.hashCode()
}

/**
 * Build a GTV conversion from an [R_Type], recursively. Mirrors `Rt_InterpreterImpl.buildRtType`'s
 * GTV branch (private impl detail; reach via [Rt_Interpreter.resolveType]) but operates on R_Type
 * instead of RR_Type, so it can be called pre-interpreter
 * (e.g. compile-time constant encoding, module_args validation, Dokka reflection). Returns null
 * for R_Types with no GTV representation.
 */
internal fun gtvConversionFromR(rType: R_Type): Rt_GtvCompatibleValueClass<*>? = when (rType) {
    is R_EntityType -> {
        val entity = rType.rEntity
        Rt_LazyGtvAdapter {
            Rt_EntityValue.gtvConversion(
                rtType = lazy { rTypeStub(rType) },
                typeName = rType.strCode(),
                track = { ctx, rowid -> ctx.trackRecord(entity, rowid) },
            )
        }
    }

    is R_EnumType -> Rt_LazyGtvAdapter { Rt_RR_EnumValue.gtvConversion(rType.enum) }
    is R_StructType -> Rt_LazyGtvAdapter { Rt_StructValue.gtvConversion(rType.struct) }
    is R_VirtualStructType -> Rt_LazyGtvAdapter { Rt_VirtualStructValue.gtvConversion(rType) }
    is R_VirtualTupleType -> Rt_LazyGtvAdapter { Rt_VirtualTupleValue.gtvConversion(rType) }
    is R_VirtualListType -> Rt_LazyGtvAdapter { Rt_VirtualListValue.gtvConversion(rType) }
    is R_VirtualSetType -> Rt_LazyGtvAdapter { Rt_VirtualSetValue.gtvConversion(rType) }
    is R_VirtualMapType -> Rt_LazyGtvAdapter { Rt_VirtualMapValue.gtvConversion(rType) }
    is R_NullableType -> Rt_LazyGtvAdapter {
        Rt_NullValue.gtvConversionNullable(lazy { gtvConversionFromR(rType.valueType)!! })
    }

    is R_TupleType -> Rt_LazyGtvAdapter {
        Rt_TupleValue.gtvConversion(
            typeName = rType.strCode(),
            fieldNames = rType.fields.mapToImmList { it.name?.str },
            fieldConversions = lazy { rType.fields.mapToImmList { gtvConversionFromR(it.type)!! } },
            rtType = lazy { rTypeStub(rType) },
        )
    }

    is R_ListType -> Rt_LazyGtvAdapter {
        Rt_ListValue.gtvConversion(
            typeName = rType.strCode(),
            elementConversion = lazy { gtvConversionFromR(rType.elementType)!! },
            rtType = lazy { rTypeStub(rType) },
        )
    }

    is R_SetType -> Rt_LazyGtvAdapter {
        Rt_SetValue.gtvConversion(
            typeName = rType.strCode(),
            elementConversion = lazy { gtvConversionFromR(rType.elementType)!! },
            rtType = lazy { rTypeStub(rType) },
        )
    }

    is R_MapType -> Rt_LazyGtvAdapter {
        Rt_MapValue.gtvConversion(
            typeName = rType.strCode(),
            isTextKey = rType.keyType is R_TextType,
            keyConversion = lazy { gtvConversionFromR(rType.keyType)!! },
            valueConversion = lazy { gtvConversionFromR(rType.valueType)!! },
            rtType = lazy { rTypeStub(rType) },
        )
    }
    // Primitives, null, and fallback: pure-RR dispatch
    else -> {
        val conv = createGtvConversion(rTypeToRRType(rType)) ?: return null
        Rt_LazyGtvAdapter { conv }
    }
}

/**
 * Create GTV conversion purely from [RR_Type] — handles primitives and null only.
 * Returns null for types that require definition data (entity/struct/enum/virtual)
 * or composite recursion through R_Type (nullable/tuple/list/set/map).
 * The interpreter has its own full pure-RR equivalent.
 */
fun createGtvConversion(rrType: RR_Type): Rt_GtvCompatibleValueClass<*>? = when (rrType) {
    is RR_Type.Primitive -> when (rrType.kind) {
        RR_PrimitiveKind.BOOLEAN -> Rt_BooleanValue
        RR_PrimitiveKind.INTEGER -> Rt_IntValue
        RR_PrimitiveKind.BIG_INTEGER -> Rt_BigIntegerValue
        RR_PrimitiveKind.DECIMAL -> Rt_DecimalValue
        RR_PrimitiveKind.TEXT -> Rt_TextValue
        RR_PrimitiveKind.BYTE_ARRAY -> Rt_ByteArrayValue
        RR_PrimitiveKind.ROWID -> Rt_RowidValue
        RR_PrimitiveKind.JSON -> Rt_JsonValue
        RR_PrimitiveKind.GTV -> Rt_GtvValue
        else -> return null
    }

    is RR_Type.Null -> Rt_NullValue.gtvConversion
    else -> null
}
