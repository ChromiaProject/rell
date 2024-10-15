/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.Gtv
import net.postchain.rell.base.lib.type.Rt_MapValue
import net.postchain.rell.base.lib.type.Rt_RangeValue
import net.postchain.rell.base.model.*
import net.postchain.rell.base.utils.CommonUtils
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.reflect.KClass
import kotlin.reflect.cast

abstract class Rt_ValueRef {
    abstract fun get(): Rt_Value
    abstract fun set(value: Rt_Value)
}

sealed class Rt_ValueType(val name: String) {
    final override fun toString() = name
}

enum class Rt_CoreValueTypes {
    UNIT,
    BOOLEAN,
    INTEGER,
    BIG_INTEGER,
    DECIMAL,
    TEXT,
    BYTE_ARRAY,
    ROWID,
    ENTITY,
    NULL,
    ITERABLE,
    COLLECTION,
    LIST,
    SET,
    MAP,
    MUTABLE_MAP,
    TUPLE,
    STRUCT,
    ENUM,
    OBJECT,
    JSON,
    RANGE,
    GTV,
    FUNCTION,
    VIRTUAL,
    VIRTUAL_COLLECTION,
    VIRTUAL_LIST,
    VIRTUAL_SET,
    VIRTUAL_MAP,
    VIRTUAL_TUPLE,
    VIRTUAL_STRUCT,
    LAZY,
    ;

    fun type(): Rt_ValueType = Rt_CoreValueType(this)
}

private class Rt_CoreValueType(coreType: Rt_CoreValueTypes): Rt_ValueType(coreType.name)

class Rt_LibValueType private constructor(name: String): Rt_ValueType(name) {
    init {
        val v = try { Rt_CoreValueTypes.valueOf(name) } catch (e: java.lang.IllegalArgumentException) { null }
        check(v == null) { name }
    }

    companion object {
        fun of(name: String): Rt_ValueType = Rt_LibValueType(name)
    }
}

abstract class Rt_Value {
    enum class StrFormat {
        V1,
        V2,
    }

    protected abstract val valueType: Rt_ValueType

    abstract fun type(): R_Type

    open fun asBoolean(): Boolean = throw errType(Rt_CoreValueTypes.BOOLEAN)
    open fun asInteger(): Long = throw errType(Rt_CoreValueTypes.INTEGER)
    open fun asBigInteger(): BigInteger = throw errType(Rt_CoreValueTypes.BIG_INTEGER)
    open fun asDecimal(): BigDecimal = throw errType(Rt_CoreValueTypes.DECIMAL)
    open fun asRowid(): Long = throw errType(Rt_CoreValueTypes.ROWID)
    open fun asString(): String = throw errType(Rt_CoreValueTypes.TEXT)
    open fun asByteArray(): ByteArray = throw errType(Rt_CoreValueTypes.BYTE_ARRAY)
    open fun asJsonString(): String = throw errType(Rt_CoreValueTypes.JSON)
    open fun asIterable(): Iterable<Rt_Value> = throw errType(Rt_CoreValueTypes.ITERABLE)
    open fun asCollection(): MutableCollection<Rt_Value> = throw errType(Rt_CoreValueTypes.COLLECTION)
    open fun asList(): MutableList<Rt_Value> = throw errType(Rt_CoreValueTypes.LIST)
    open fun asVirtualCollection(): Rt_VirtualCollectionValue = throw errType(Rt_CoreValueTypes.VIRTUAL_COLLECTION)
    open fun asVirtualList(): Rt_VirtualListValue = throw errType(Rt_CoreValueTypes.VIRTUAL_LIST)
    open fun asVirtualSet(): Rt_VirtualSetValue = throw errType(Rt_CoreValueTypes.VIRTUAL_SET)
    open fun asSet(): MutableSet<Rt_Value> = throw errType(Rt_CoreValueTypes.SET)
    open fun asMap(): Map<Rt_Value, Rt_Value> = throw errType(Rt_CoreValueTypes.MAP)
    open fun asMutableMap(): MutableMap<Rt_Value, Rt_Value> = throw errType(Rt_CoreValueTypes.MUTABLE_MAP)
    open fun asMapValue(): Rt_MapValue = throw errType(Rt_CoreValueTypes.MAP)
    open fun asTuple(): List<Rt_Value> = throw errType(Rt_CoreValueTypes.TUPLE)
    open fun asVirtualTuple(): Rt_VirtualTupleValue = throw errType(Rt_CoreValueTypes.VIRTUAL_TUPLE)
    open fun asStruct(): Rt_StructValue = throw errType(Rt_CoreValueTypes.STRUCT)
    open fun asVirtual(): Rt_VirtualValue = throw errType(Rt_CoreValueTypes.VIRTUAL)
    open fun asVirtualStruct(): Rt_VirtualStructValue = throw errType(Rt_CoreValueTypes.VIRTUAL_STRUCT)
    open fun asEnum(): R_EnumAttr = throw errType(Rt_CoreValueTypes.ENUM)
    open fun asRange(): Rt_RangeValue = throw errType(Rt_CoreValueTypes.RANGE)
    open fun asObjectId(): Long = throw errType(Rt_CoreValueTypes.ENTITY)
    open fun asGtv(): Gtv = throw errType(Rt_CoreValueTypes.GTV)
    open fun asFunction(): Rt_FunctionValue = throw errType(Rt_CoreValueTypes.FUNCTION)
    open fun asLazyValue(): Rt_Value = throw errType(Rt_CoreValueTypes.LAZY)

    fun <T: Rt_Value> asType(cls: KClass<T>, valueType: Rt_ValueType): T {
        if (!cls.isInstance(this)) {
            throw errType(valueType)
        }
        return cls.cast(this)
    }

    open fun toFormatArg(): Any = toString()

    abstract fun str(format: StrFormat = StrFormat.V1): String
    abstract fun strCode(showTupleFieldNames: Boolean = true): String

    open fun strPretty(indent: Int): String {
        val maxLen = 120
        val s = str(StrFormat.V2)
        return if (s.length <= maxLen) s else (s.substring(0, maxLen) + "...")
    }

    final override fun toString(): String {
        // Calling toString() is considered wrong. Throwing exception in unit tests and returning str() in production
        // mode as a fallback.
        CommonUtils.failIfUnitTest()
        return str(StrFormat.V1)
    }

    private fun errType(expected: Rt_CoreValueTypes) = errType(expected.type())
    private fun errType(expected: Rt_ValueType) = Rt_ValueTypeError.exception(expected, valueType)
}
