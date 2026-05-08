/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.rr

import net.postchain.rell.base.utils.ImmList

/**
 * Serializable type identity covering all Rell types: primitives, `entity`, `struct`, `enum`, `object`,
 * collections (`list`, `set`, `map`), tuples, function types, nullable types, and `virtual` types.
 *
 * Definition-backed types use `defIndex` — a position in the flat definition arrays of [RR_App].
 * Constructable during deserialization without a compilation context.
 */
sealed interface RR_Type {

    @JvmRecord
    data class Primitive(val kind: RR_PrimitiveKind): RR_Type

    data object Null: RR_Type

    /** Entity definition at `RR_App.allEntities[defIndex]`. */
    @JvmRecord
    data class Entity(val defIndex: Int): RR_Type

    /** Struct definition at `RR_App.allStructs[defIndex]`. */
    @JvmRecord
    data class Struct(val defIndex: Int): RR_Type

    /** Enum definition at `RR_App.allEnums[defIndex]`. */
    @JvmRecord
    data class Enum(val defIndex: Int): RR_Type

    /** Object definition at `RR_App.allObjects[defIndex]`. */
    @JvmRecord
    data class Object(val defIndex: Int): RR_Type

    @JvmRecord
    data class Nullable(val value: RR_Type): RR_Type

    @JvmRecord
    data class List(val element: RR_Type): RR_Type
    @JvmRecord
    data class Set(val element: RR_Type): RR_Type
    @JvmRecord
    data class Map(val key: RR_Type, val value: RR_Type): RR_Type

    @JvmRecord
    data class Tuple(val fields: ImmList<RR_TupleField>): RR_Type

    @JvmRecord
    data class Function(val params: ImmList<RR_Type>, val result: RR_Type): RR_Type

    @JvmRecord
    data class VirtualList(val element: RR_Type): RR_Type
    @JvmRecord
    data class VirtualSet(val element: RR_Type): RR_Type
    @JvmRecord
    data class VirtualMap(val key: RR_Type, val value: RR_Type): RR_Type
    @JvmRecord
    data class VirtualStruct(val defIndex: Int): RR_Type
    @JvmRecord
    data class VirtualTuple(val fields: ImmList<RR_TupleField>): RR_Type

    /** Catch-all for library-defined generic types (e.g. `iterable<T>`). */
    @JvmRecord
    data class Generic(val name: String, val args: ImmList<RR_Type>): RR_Type

    /** Operation type (mirrors struct for operations). */
    @JvmRecord
    data class Operation(val defIndex: Int): RR_Type

    /** Error type — should not appear in valid models. */
    data object Error: RR_Type
}

@JvmRecord
data class RR_TupleField(val name: String?, val type: RR_Type)

enum class RR_PrimitiveKind {
    BOOLEAN,
    INTEGER,
    BIG_INTEGER,
    DECIMAL,
    TEXT,
    BYTE_ARRAY,
    ROWID,
    GUID,
    SIGNER,
    JSON,
    GTV,
    RANGE,
    UNIT,
}
