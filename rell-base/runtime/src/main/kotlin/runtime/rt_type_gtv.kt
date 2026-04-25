/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.Gtv
import net.postchain.rell.base.lib.type.*
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.utils.mapToImmList

internal class Rt_TypeGtvConversionLazy(provider: () -> GtvRtConversion): Rt_TypeGtvConversion {
    private val conv by lazy(provider)
    override fun rtToGtv(value: Rt_Value, pretty: Boolean): Gtv = conv.rtToGtv(value, pretty)
    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value = conv.gtvToRt(ctx, gtv)
}

/**
 * Build GTV conversion for the [rTypeToRtType] bridge.
 * Definition-backed types (entity/struct/enum/virtual) are handled inline with [rType] data.
 * Primitives, null, and composites delegate to the pure-RR [createGtvConversion].
 */
internal fun buildBridgeGtvConversion(rType: R_Type, rrType: RR_Type): Rt_TypeGtvConversion? {
    return when (rType) {
        is R_EntityType -> {
            val entity = rType.rEntity
            Rt_TypeGtvConversionLazy {
                GtvRtConversion_Entity(
                    rtType = lazy { rTypeToRtType(rType) },
                    typeName = rType.strCode(),
                    gtvCompatible = entity.flags.gtv,
                    rEntity = entity,
                )
            }
        }

        is R_EnumType -> Rt_TypeGtvConversionLazy { GtvRtConversion_Enum(rType.enum) }
        is R_StructType -> Rt_TypeGtvConversionLazy { GtvRtConversion_Struct(rType.struct) }
        is R_VirtualStructType -> Rt_TypeGtvConversionLazy { GtvRtConversion_VirtualStruct(rType) }
        is R_VirtualTupleType -> Rt_TypeGtvConversionLazy { GtvRtConversion_VirtualTuple(rType) }
        is R_VirtualListType -> Rt_TypeGtvConversionLazy { GtvRtConversion_VirtualList(rType) }
        is R_VirtualSetType -> Rt_TypeGtvConversionLazy { GtvRtConversion_VirtualSet(rType) }
        is R_VirtualMapType -> Rt_TypeGtvConversionLazy { GtvRtConversion_VirtualMap(rType) }
        is R_NullableType -> Rt_TypeGtvConversionLazy {
            GtvRtConversion_Nullable(lazy { rTypeToRtType(rType.valueType).gtvConversion!! })
        }

        is R_TupleType -> Rt_TypeGtvConversionLazy {
            GtvRtConversion_Tuple(
                typeName = rType.strCode(),
                fieldNames = rType.fields.mapToImmList { it.name?.str },
                fieldConversions = lazy { rType.fields.mapToImmList { rTypeToRtType(it.type).gtvConversion!! } },
                rtType = lazy { rTypeToRtType(rType) },
            )
        }

        is R_ListType -> Rt_TypeGtvConversionLazy {
            GtvRtConversion_List(
                typeName = rType.strCode(),
                elementConversion = lazy { rTypeToRtType(rType.elementType).gtvConversion!! },
                rtType = lazy { rTypeToRtType(rType) },
            )
        }

        is R_SetType -> Rt_TypeGtvConversionLazy {
            GtvRtConversion_Set(
                typeName = rType.strCode(),
                elementConversion = lazy { rTypeToRtType(rType.elementType).gtvConversion!! },
                rtType = lazy { rTypeToRtType(rType) },
            )
        }

        is R_MapType -> Rt_TypeGtvConversionLazy {
            GtvRtConversion_Map(
                typeName = rType.strCode(),
                isTextKey = rType.keyType is R_TextType,
                keyConversion = lazy { rTypeToRtType(rType.keyType).gtvConversion!! },
                valueConversion = lazy { rTypeToRtType(rType.valueType).gtvConversion!! },
                rtType = lazy { rTypeToRtType(rType) },
            )
        }
        // Primitives, null, and fallback: pure-RR dispatch
        else -> {
            val conv = createGtvConversion(rrType) ?: return null
            Rt_TypeGtvConversionLazy { conv }
        }
    }
}

/**
 * Create GTV conversion purely from [RR_Type] — handles primitives and null only.
 * Returns null for types that require definition data (entity/struct/enum/virtual)
 * or composite recursion through R_Type (nullable/tuple/list/set/map).
 * The interpreter has its own full pure-RR equivalent.
 */
internal fun createGtvConversion(rrType: RR_Type): GtvRtConversion? = when (rrType) {
    is RR_Type.Primitive -> when (rrType.kind) {
        RR_PrimitiveKind.BOOLEAN -> GtvRtConversion_Boolean
        RR_PrimitiveKind.INTEGER -> GtvRtConversion_Integer
        RR_PrimitiveKind.BIG_INTEGER -> GtvRtConversion_BigInteger
        RR_PrimitiveKind.DECIMAL -> GtvRtConversion_Decimal
        RR_PrimitiveKind.TEXT -> GtvRtConversion_Text
        RR_PrimitiveKind.BYTE_ARRAY -> GtvRtConversion_ByteArray
        RR_PrimitiveKind.ROWID -> GtvRtConversion_Rowid
        RR_PrimitiveKind.JSON -> GtvRtConversion_Json
        RR_PrimitiveKind.GTV -> GtvRtConversion_Gtv
        else -> GtvRtConversion_None
    }

    is RR_Type.Null -> GtvRtConversion_Null
    else -> null
}
