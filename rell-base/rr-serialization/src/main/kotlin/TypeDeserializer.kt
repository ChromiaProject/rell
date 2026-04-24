/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.serialization

import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_TupleField
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.utils.mapToImmList
import rell.ir.*
import rell.ir.Type as FbType

private val FB_TO_PRIMITIVE_KIND = mapOf(
    PrimitiveTypeKind.BOOLEAN to RR_PrimitiveKind.BOOLEAN,
    PrimitiveTypeKind.INTEGER to RR_PrimitiveKind.INTEGER,
    PrimitiveTypeKind.BIG_INTEGER to RR_PrimitiveKind.BIG_INTEGER,
    PrimitiveTypeKind.DECIMAL to RR_PrimitiveKind.DECIMAL,
    PrimitiveTypeKind.TEXT to RR_PrimitiveKind.TEXT,
    PrimitiveTypeKind.BYTE_ARRAY to RR_PrimitiveKind.BYTE_ARRAY,
    PrimitiveTypeKind.ROWID to RR_PrimitiveKind.ROWID,
    PrimitiveTypeKind.GUID to RR_PrimitiveKind.GUID,
    PrimitiveTypeKind.SIGNER to RR_PrimitiveKind.SIGNER,
    PrimitiveTypeKind.JSON to RR_PrimitiveKind.JSON,
    PrimitiveTypeKind.GTV to RR_PrimitiveKind.GTV,
    PrimitiveTypeKind.RANGE to RR_PrimitiveKind.RANGE,
    PrimitiveTypeKind.UNIT to RR_PrimitiveKind.UNIT,
)

fun deserializeType(fb: FbType?): RR_Type = withDeserializerDepth { deserializeTypeInner(fb) }

private fun deserializeTypeInner(fb: FbType?): RR_Type = when (fb?.typeType) {
    null -> RR_Type.Error
    TypeUnion.PrimitiveType -> {
        val prim = PrimitiveType().also { fb.type(it) }
        val kind = checkNotNull(FB_TO_PRIMITIVE_KIND[prim.kind]) { "Unknown primitive kind: ${prim.kind}" }
        RR_Type.Primitive(kind)
    }

    TypeUnion.NullType -> RR_Type.Null
    TypeUnion.EntityType -> {
        val t = EntityType().also { fb.type(it) }
        RR_Type.Entity(t.defIndex.toInt())
    }

    TypeUnion.StructType -> {
        val t = StructType().also { fb.type(it) }
        RR_Type.Struct(t.defIndex.toInt())
    }

    TypeUnion.EnumType -> {
        val t = EnumType().also { fb.type(it) }
        RR_Type.Enum(t.defIndex.toInt())
    }

    TypeUnion.ObjectType -> {
        val t = ObjectType().also { fb.type(it) }
        RR_Type.Object(t.defIndex.toInt())
    }

    TypeUnion.NullableType -> {
        val t = NullableType().also { fb.type(it) }
        RR_Type.Nullable(deserializeType(t.value))
    }

    TypeUnion.ListType -> {
        val t = ListType().also { fb.type(it) }
        RR_Type.List(deserializeType(t.element))
    }

    TypeUnion.SetType -> {
        val t = SetType().also { fb.type(it) }
        RR_Type.Set(deserializeType(t.element))
    }

    TypeUnion.MapType -> {
        val t = MapType().also { fb.type(it) }
        RR_Type.Map(deserializeType(t.key), deserializeType(t.value))
    }

    TypeUnion.TupleType -> {
        val t = TupleType().also { fb.type(it) }
        val fields = (0 until t.fieldsLength).mapToImmList { i ->
            val field = t.fields(i)
            RR_TupleField(field.name, deserializeType(field.type))
        }
        RR_Type.Tuple(fields)
    }

    TypeUnion.FunctionType -> {
        val t = FunctionType().also { fb.type(it) }
        val params = (0 until t.paramsLength).mapToImmList { deserializeType(t.params(it)) }
        RR_Type.Function(params, deserializeType(t.result))
    }

    TypeUnion.VirtualListType -> {
        val t = VirtualListType().also { fb.type(it) }
        RR_Type.VirtualList(deserializeType(t.element))
    }

    TypeUnion.VirtualSetType -> {
        val t = VirtualSetType().also { fb.type(it) }
        RR_Type.VirtualSet(deserializeType(t.element))
    }

    TypeUnion.VirtualMapType -> {
        val t = VirtualMapType().also { fb.type(it) }
        RR_Type.VirtualMap(deserializeType(t.key), deserializeType(t.value))
    }

    TypeUnion.VirtualStructType -> {
        val t = VirtualStructType().also { fb.type(it) }
        RR_Type.VirtualStruct(t.defIndex.toInt())
    }

    TypeUnion.VirtualTupleType -> {
        val t = VirtualTupleType().also { fb.type(it) }
        val fields = (0 until t.fieldsLength).mapToImmList { i ->
            val field = t.fields(i)
            RR_TupleField(field.name, deserializeType(field.type))
        }
        RR_Type.VirtualTuple(fields)
    }

    TypeUnion.GenericType -> {
        val t = GenericType().also { fb.type(it) }
        val args = (0 until t.argsLength).mapToImmList { deserializeType(t.args(it)) }
        RR_Type.Generic(t.name, args)
    }

    TypeUnion.OperationType -> {
        val t = OperationType().also { fb.type(it) }
        RR_Type.Operation(t.defIndex.toInt())
    }

    TypeUnion.ErrorType -> RR_Type.Error
    else -> RR_Type.Error
}
