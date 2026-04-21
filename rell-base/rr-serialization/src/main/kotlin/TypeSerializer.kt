/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.serialization

import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_Type
import rell.ir.*
import rell.ir.Type as FbType

fun SerializerContext.serializeType(type: RR_Type): Int {
    val (unionType, unionOffset) = serializeTypeUnion(type)
    FbType.startType(builder)
    FbType.addTypeType(builder, unionType)
    FbType.addType(builder, unionOffset)
    return FbType.endType(builder)
}

private val PRIMITIVE_KIND_MAP = mapOf(
    RR_PrimitiveKind.BOOLEAN to PrimitiveTypeKind.BOOLEAN,
    RR_PrimitiveKind.INTEGER to PrimitiveTypeKind.INTEGER,
    RR_PrimitiveKind.BIG_INTEGER to PrimitiveTypeKind.BIG_INTEGER,
    RR_PrimitiveKind.DECIMAL to PrimitiveTypeKind.DECIMAL,
    RR_PrimitiveKind.TEXT to PrimitiveTypeKind.TEXT,
    RR_PrimitiveKind.BYTE_ARRAY to PrimitiveTypeKind.BYTE_ARRAY,
    RR_PrimitiveKind.ROWID to PrimitiveTypeKind.ROWID,
    RR_PrimitiveKind.GUID to PrimitiveTypeKind.GUID,
    RR_PrimitiveKind.SIGNER to PrimitiveTypeKind.SIGNER,
    RR_PrimitiveKind.JSON to PrimitiveTypeKind.JSON,
    RR_PrimitiveKind.GTV to PrimitiveTypeKind.GTV,
    RR_PrimitiveKind.RANGE to PrimitiveTypeKind.RANGE,
    RR_PrimitiveKind.UNIT to PrimitiveTypeKind.UNIT,
)

private fun SerializerContext.serializeTypeUnion(type: RR_Type): Pair<UByte, Int> = when (type) {
    is RR_Type.Primitive -> {
        val kind = PRIMITIVE_KIND_MAP.getValue(type.kind)
        TypeUnion.PrimitiveType to PrimitiveType.createPrimitiveType(builder, kind)
    }

    is RR_Type.Null -> {
        NullType.startNullType(builder)
        TypeUnion.NullType to NullType.endNullType(builder)
    }

    is RR_Type.Entity -> TypeUnion.EntityType to EntityType.createEntityType(builder, type.defIndex.toUInt())
    is RR_Type.Struct -> TypeUnion.StructType to StructType.createStructType(builder, type.defIndex.toUInt())
    is RR_Type.Enum -> TypeUnion.EnumType to EnumType.createEnumType(builder, type.defIndex.toUInt())
    is RR_Type.Object -> TypeUnion.ObjectType to ObjectType.createObjectType(builder, type.defIndex.toUInt())

    is RR_Type.List -> {
        val elem = serializeType(type.element)
        TypeUnion.ListType to ListType.createListType(builder, elem)
    }

    is RR_Type.Set -> {
        val elem = serializeType(type.element)
        TypeUnion.SetType to SetType.createSetType(builder, elem)
    }

    is RR_Type.Map -> {
        val key = serializeType(type.key)
        val value = serializeType(type.value)
        TypeUnion.MapType to MapType.createMapType(builder, key, value)
    }

    is RR_Type.Tuple -> {
        val fields = type.fields.map { field ->
            val nameOff = field.name?.let { createString(it) }
            val typeOff = serializeType(field.type)
            TupleField.startTupleField(builder)
            if (nameOff != null) TupleField.addName(builder, nameOff)
            TupleField.addType(builder, typeOff)
            TupleField.endTupleField(builder)
        }.toIntArray()
        val fieldsVec = builder.createVectorOfTables(fields)
        TypeUnion.TupleType to TupleType.createTupleType(builder, fieldsVec)
    }

    is RR_Type.Nullable -> {
        val inner = serializeType(type.value)
        TypeUnion.NullableType to NullableType.createNullableType(builder, inner)
    }

    is RR_Type.Function -> {
        val params = type.params.map { serializeType(it) }.toIntArray()
        val paramsVec = builder.createVectorOfTables(params)
        val result = serializeType(type.result)
        TypeUnion.FunctionType to FunctionType.createFunctionType(builder, paramsVec, result)
    }

    is RR_Type.VirtualList -> {
        val elem = serializeType(type.element)
        TypeUnion.VirtualListType to VirtualListType.createVirtualListType(builder, elem)
    }

    is RR_Type.VirtualSet -> {
        val elem = serializeType(type.element)
        TypeUnion.VirtualSetType to VirtualSetType.createVirtualSetType(builder, elem)
    }

    is RR_Type.VirtualMap -> {
        val key = serializeType(type.key)
        val value = serializeType(type.value)
        TypeUnion.VirtualMapType to VirtualMapType.createVirtualMapType(builder, key, value)
    }

    is RR_Type.VirtualStruct -> {
        TypeUnion.VirtualStructType to VirtualStructType.createVirtualStructType(builder, type.defIndex.toUInt())
    }

    is RR_Type.VirtualTuple -> {
        val fields = type.fields.map { field ->
            val nameOff = field.name?.let { createString(it) }
            val typeOff = serializeType(field.type)
            TupleField.startTupleField(builder)
            if (nameOff != null) TupleField.addName(builder, nameOff)
            TupleField.addType(builder, typeOff)
            TupleField.endTupleField(builder)
        }.toIntArray()
        val fieldsVec = builder.createVectorOfTables(fields)
        TypeUnion.VirtualTupleType to VirtualTupleType.createVirtualTupleType(builder, fieldsVec)
    }

    is RR_Type.Generic -> {
        val name = createString(type.name)
        val args = if (type.args.isEmpty()) 0 else {
            val argOffsets = type.args.map { serializeType(it) }.toIntArray()
            builder.createVectorOfTables(argOffsets)
        }
        TypeUnion.GenericType to GenericType.createGenericType(builder, name, args)
    }

    is RR_Type.Operation -> {
        TypeUnion.OperationType to OperationType.createOperationType(builder, type.defIndex.toUInt())
    }

    is RR_Type.Error -> {
        ErrorType.startErrorType(builder)
        TypeUnion.ErrorType to ErrorType.endErrorType(builder)
    }
}
