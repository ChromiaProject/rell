/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.rell.base.model.rr.*
import net.postchain.rell.base.runtime.utils.Rt_ListComparator
import net.postchain.rell.base.runtime.utils.Rt_TupleComparator
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.mapToImmList

// =============================================================================
// Parameterized type-classes — one per RR_Type kind
//
// Each class derives all four capabilities (gtvConversion / sqlAdapter / comparator /
// nativeConversion) from its constructor parameters. The previous bag-style
// One concrete subclass per RR_Type kind, replacing the previous bag-style holder.
// =============================================================================

class Rt_NullableType(val inner: Rt_ValueClass<*>): Rt_ValueClass<Rt_Value> {
    override val rrType: RR_Type = RR_Type.Nullable(inner.rrType!!)
    override val name = if (inner is Rt_FunctionType) "(${inner.name})?" else "${inner.name}?"
    override val klass = Rt_Value::class
    override fun cast(v: Rt_Value): Rt_Value = v

    override val gtvConversion: Rt_GtvCompatibleValueClass<*>? by lazy {
        inner.gtvConversion?.let { c -> Rt_NullValue.gtvConversionNullable(lazyOf(c)) }
    }

    override val sqlAdapter: Rt_SqlCompatibleValueClass<*>? by lazy {
        inner.sqlAdapter?.let { Rt_SqlAdapter_Nullable(it) }
    }

    override val comparator: Comparator<Rt_Value>? by lazy {
        inner.comparator?.let { c ->
            Comparator { a, b ->
                when {
                    a == Rt_NullValue && b == Rt_NullValue -> 0
                    a == Rt_NullValue -> -1
                    b == Rt_NullValue -> 1
                    else -> c.compare(a, b)
                }
            }
        }
    }

    override val nativeConversion: Rt_NativeAdapter? by lazy {
        inner.nativeConversion?.let { Rt_NativeAdapter_Nullable(it) }
    }

    override fun toString() = name
    override fun equals(other: Any?) = other is Rt_NullableType && name == other.name
    override fun hashCode() = name.hashCode()
}

class Rt_ListType(val element: Rt_ValueClass<*>): Rt_ValueClass<Rt_ListValue> {
    override val rrType: RR_Type = RR_Type.List(element.rrType!!)
    override val name = "list<${element.name}>"
    override val klass = Rt_ListValue::class

    override val gtvConversion: Rt_GtvCompatibleValueClass<*>? by lazy {
        element.gtvConversion?.let { c ->
            Rt_ListValue.gtvConversion(name, lazyOf(c), lazyOf(this))
        }
    }

    override val comparator: Comparator<Rt_Value>? by lazy {
        element.comparator?.let { Rt_ListComparator(it) }
    }

    override fun toString() = name
    override fun equals(other: Any?) = other is Rt_ListType && name == other.name
    override fun hashCode() = name.hashCode()
}

class Rt_SetType(val element: Rt_ValueClass<*>): Rt_ValueClass<Rt_SetValue> {
    override val rrType: RR_Type = RR_Type.Set(element.rrType!!)
    override val name = "set<${element.name}>"
    override val klass = Rt_SetValue::class

    override val gtvConversion: Rt_GtvCompatibleValueClass<*>? by lazy {
        element.gtvConversion?.let { c ->
            Rt_SetValue.gtvConversion(name, lazyOf(c), lazyOf(this))
        }
    }

    override fun toString() = name
    override fun equals(other: Any?) = other is Rt_SetType && name == other.name
    override fun hashCode() = name.hashCode()
}

class Rt_MapType(val key: Rt_ValueClass<*>, val value: Rt_ValueClass<*>): Rt_ValueClass<Rt_MapValue> {
    override val rrType: RR_Type = RR_Type.Map(key.rrType!!, value.rrType!!)
    override val name = "map<${key.name},${value.name}>"
    override val klass = Rt_MapValue::class

    override val gtvConversion: Rt_GtvCompatibleValueClass<*>? by lazy {
        val keyConv = key.gtvConversion
        val valConv = value.gtvConversion
        if (keyConv == null || valConv == null) null else {
            val isTextKey = (key.rrType as? RR_Type.Primitive)?.kind == RR_PrimitiveKind.TEXT
            Rt_MapValue.gtvConversion(name, isTextKey, lazyOf(keyConv), lazyOf(valConv), lazyOf(this))
        }
    }

    override fun toString() = name
    override fun equals(other: Any?) = other is Rt_MapType && name == other.name
    override fun hashCode() = name.hashCode()
}

class Rt_TupleType(
    val fields: ImmList<RR_TupleField>,
    val fieldClasses: ImmList<Rt_ValueClass<*>>,
): Rt_ValueClass<Rt_TupleValue> {
    override val rrType: RR_Type = RR_Type.Tuple(fields)
    override val name = fields.indices.joinToString(",", "(", ")") { i ->
        val f = fields[i]
        val typeName = fieldClasses[i].name
        if (f.name != null) "${f.name}:$typeName" else typeName
    }
    override val klass = Rt_TupleValue::class

    override val gtvConversion: Rt_GtvCompatibleValueClass<*>? by lazy {
        val convs = fieldClasses.map { it.gtvConversion }
        if (convs.any { it == null }) null else {
            Rt_TupleValue.gtvConversion(
                typeName = name,
                fieldNames = fields.mapToImmList { it.name },
                fieldConversions = lazyOf(convs.mapToImmList { it!! }),
                rtType = lazyOf(this),
            )
        }
    }

    override val comparator: Comparator<Rt_Value>? by lazy {
        val cmps = fieldClasses.map { it.comparator }
        if (cmps.any { it == null }) null else Rt_TupleComparator(cmps.mapToImmList { it!! })
    }

    override fun toString() = name
    override fun equals(other: Any?) = other is Rt_TupleType && name == other.name
    override fun hashCode() = name.hashCode()
}

class Rt_EntityType(
    private val rrApp: RR_App,
    val defIndex: Int,
): Rt_ValueClass<Rt_EntityValue> {
    private val def get() = rrApp.allEntities[defIndex]
    override val rrType: RR_Type = RR_Type.Entity(defIndex)
    override val name: String get() = def.base.appLevelName
    override val klass = Rt_EntityValue::class

    override val sqlAdapter: Rt_SqlCompatibleValueClass<*> by lazy {
        Rt_SqlAdapter_Entity(
            lazyRtType = lazyOf(this),
            typeName = name,
            tableMetaName = def.sqlMapping.metaName,
            externalChainIndex = def.sqlMapping.externalChainIndex,
        )
    }

    override val gtvConversion: Rt_GtvCompatibleValueClass<*> by lazy {
        Rt_EntityValue.gtvConversion(
            rtType = lazyOf(this),
            typeName = name,
        ) { ctx, rowid -> ctx.trackRecordRR(def, rowid) }
    }

    override val comparator: Comparator<Rt_Value> = Comparator { a, b ->
        a.asObjectId().compareTo(b.asObjectId())
    }

    override fun toString() = name
    override fun equals(other: Any?) = other is Rt_EntityType && rrApp === other.rrApp && defIndex == other.defIndex
    override fun hashCode() = defIndex
}

class Rt_EnumType(
    private val rrApp: RR_App,
    val defIndex: Int,
): Rt_ValueClass<Rt_RR_EnumValue> {
    private val def get() = rrApp.allEnums[defIndex]
    override val rrType: RR_Type = RR_Type.Enum(defIndex)
    override val name: String get() = def.base.appLevelName
    override val klass = Rt_RR_EnumValue::class

    private val rtValues: List<Rt_Value> by lazy {
        def.attrs.map { attr -> Rt_RR_EnumValue(lazyOf(this), attr) }
    }

    override val sqlAdapter: Rt_SqlCompatibleValueClass<*> by lazy {
        Rt_SqlAdapter_Enum(lazyRtType = lazyOf(this), typeName = name, attrs = def.attrs)
    }

    override val gtvConversion: Rt_GtvCompatibleValueClass<*> by lazy {
        Rt_RR_EnumValue.gtvConversion(
            typeName = name,
            rtByName = { s -> def.attr(s)?.let { rtValues[it.value] } },
            rtByValue = { v -> def.attr(v)?.let { rtValues[it.value] } },
        )
    }

    override val comparator: Comparator<Rt_Value> = Comparator { a, b ->
        a.asEnum().value.compareTo(b.asEnum().value)
    }

    override fun toString() = name
    override fun equals(other: Any?) = other is Rt_EnumType && rrApp === other.rrApp && defIndex == other.defIndex
    override fun hashCode() = defIndex
}

class Rt_StructType(
    private val rrApp: RR_App,
    val defIndex: Int,
    private val structGtvConversion: (RR_Type.Struct, RR_StructDefinition) -> Rt_GtvCompatibleValueClass<*>,
): Rt_ValueClass<Rt_StructValue> {
    private val def get() = rrApp.allStructs[defIndex]
    override val rrType: RR_Type.Struct = RR_Type.Struct(defIndex)
    override val name: String get() = def.struct.name
    override val klass = Rt_StructValue::class

    override val gtvConversion: Rt_GtvCompatibleValueClass<*> by lazy {
        structGtvConversion(rrType, def)
    }

    override fun toString() = name
    override fun equals(other: Any?) = other is Rt_StructType && rrApp === other.rrApp && defIndex == other.defIndex
    override fun hashCode() = defIndex
}

class Rt_ObjectType(rrApp: RR_App, defIndex: Int): Rt_ValueClass<Rt_ObjectValue> {
    override val rrType: RR_Type = RR_Type.Object(defIndex)
    override val name: String = rrApp.allObjects[defIndex].base.appLevelName
    override val klass = Rt_ObjectValue::class

    override fun toString() = name
    override fun equals(other: Any?) = other is Rt_ObjectType && name == other.name
    override fun hashCode() = name.hashCode()
}

class Rt_OperationType(rrApp: RR_App, defIndex: Int): Rt_ValueClass<Rt_Value> {
    override val rrType: RR_Type = RR_Type.Operation(defIndex)
    override val name: String = rrApp.allOperations[defIndex].base.appLevelName
    override val klass = Rt_Value::class
    override fun cast(v: Rt_Value): Rt_Value = v

    override fun toString() = name
    override fun equals(other: Any?) = other is Rt_OperationType && name == other.name
    override fun hashCode() = name.hashCode()
}

class Rt_FunctionType(
    val params: ImmList<Rt_ValueClass<*>>,
    val result: Rt_ValueClass<*>,
): Rt_ValueClass<Rt_FunctionValue> {
    override val rrType: RR_Type =
        RR_Type.Function(params.mapToImmList { it.rrType!! }, result.rrType!!)
    override val name = "(${params.joinToString(",") { it.name }})->${result.name}"
    override val klass = Rt_FunctionValue::class

    override fun toString() = name
    override fun equals(other: Any?) = other is Rt_FunctionType && name == other.name
    override fun hashCode() = name.hashCode()
}

class Rt_VirtualListType(
    val element: Rt_ValueClass<*>,
    private val gtvConversionFactory: () -> Rt_GtvCompatibleValueClass<*>? = { null },
): Rt_ValueClass<Rt_VirtualListValue> {
    override val rrType: RR_Type = RR_Type.VirtualList(element.rrType!!)
    override val name = "virtual<list<${element.name}>>"
    override val klass = Rt_VirtualListValue::class

    override val gtvConversion: Rt_GtvCompatibleValueClass<*>? by lazy { gtvConversionFactory() }

    override fun toString() = name
    override fun equals(other: Any?) = other is Rt_VirtualListType && name == other.name
    override fun hashCode() = name.hashCode()
}

class Rt_VirtualSetType(
    val element: Rt_ValueClass<*>,
    private val gtvConversionFactory: () -> Rt_GtvCompatibleValueClass<*>? = { null },
): Rt_ValueClass<Rt_VirtualSetValue> {
    override val rrType: RR_Type = RR_Type.VirtualSet(element.rrType!!)
    override val name = "virtual<set<${element.name}>>"
    override val klass = Rt_VirtualSetValue::class

    override val gtvConversion: Rt_GtvCompatibleValueClass<*>? by lazy { gtvConversionFactory() }

    override fun toString() = name
    override fun equals(other: Any?) = other is Rt_VirtualSetType && name == other.name
    override fun hashCode() = name.hashCode()
}

class Rt_VirtualMapType(
    val key: Rt_ValueClass<*>,
    val value: Rt_ValueClass<*>,
    private val gtvConversionFactory: () -> Rt_GtvCompatibleValueClass<*>? = { null },
): Rt_ValueClass<Rt_VirtualMapValue> {
    override val rrType: RR_Type = RR_Type.VirtualMap(key.rrType!!, value.rrType!!)
    override val name = "virtual<map<${key.name},${value.name}>>"
    override val klass = Rt_VirtualMapValue::class

    override val gtvConversion: Rt_GtvCompatibleValueClass<*>? by lazy { gtvConversionFactory() }

    override fun toString() = name
    override fun equals(other: Any?) = other is Rt_VirtualMapType && name == other.name
    override fun hashCode() = name.hashCode()
}

class Rt_VirtualTupleType(
    val fields: ImmList<RR_TupleField>,
    val fieldClasses: ImmList<Rt_ValueClass<*>>,
    private val gtvConversionFactory: () -> Rt_GtvCompatibleValueClass<*>? = { null },
): Rt_ValueClass<Rt_VirtualTupleValue> {
    override val rrType: RR_Type = RR_Type.VirtualTuple(fields)
    override val name = "virtual<${fields.indices.joinToString(",", "(", ")") { i ->
        val f = fields[i]
        val typeName = fieldClasses[i].name
        if (f.name != null) "${f.name}:$typeName" else typeName
    }}>"
    override val klass = Rt_VirtualTupleValue::class

    override val gtvConversion: Rt_GtvCompatibleValueClass<*>? by lazy { gtvConversionFactory() }

    override fun toString() = name
    override fun equals(other: Any?) = other is Rt_VirtualTupleType && name == other.name
    override fun hashCode() = name.hashCode()
}

class Rt_VirtualStructType(
    rrApp: RR_App,
    defIndex: Int,
    private val gtvConversionFactory: () -> Rt_GtvCompatibleValueClass<*>? = { null },
): Rt_ValueClass<Rt_VirtualStructValue> {
    override val rrType: RR_Type = RR_Type.VirtualStruct(defIndex)
    override val name = "virtual<${rrApp.allStructs[defIndex].struct.name}>"
    override val klass = Rt_VirtualStructValue::class

    override val gtvConversion: Rt_GtvCompatibleValueClass<*>? by lazy { gtvConversionFactory() }

    override fun toString() = name
    override fun equals(other: Any?) = other is Rt_VirtualStructType && name == other.name
    override fun hashCode() = name.hashCode()
}

/** Stdlib library type — name-only carrier (no GTV/SQL/comparator). */
class Rt_LibType(override val name: String): Rt_ValueClass<Rt_Value> {
    override val rrType: RR_Type = RR_Type.Generic(name, net.postchain.rell.base.utils.immListOf())
    override val klass = Rt_Value::class
    override fun cast(v: Rt_Value): Rt_Value = v

    override fun toString() = name
    override fun equals(other: Any?) = other is Rt_LibType && name == other.name
    override fun hashCode() = name.hashCode()
}

/** Generic-RR fallback type — used for app-foreign types we can't classify further. */
class Rt_GenericRrType(override val rrType: RR_Type, override val name: String): Rt_ValueClass<Rt_Value> {
    override val klass = Rt_Value::class
    override fun cast(v: Rt_Value): Rt_Value = v

    override fun toString() = name
    override fun equals(other: Any?) = other is Rt_GenericRrType && name == other.name
    override fun hashCode() = name.hashCode()
}


