/*
 * Copyright (C) 2023 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import com.google.common.collect.Iterables
import net.postchain.gtv.*
import net.postchain.rell.base.lib.type.*
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.R_FunctionCallTarget
import net.postchain.rell.base.model.expr.R_PartialArgMapping
import net.postchain.rell.base.model.expr.R_PartialCallMapping
import net.postchain.rell.base.runtime.utils.Rt_ValueRecursionDetector
import net.postchain.rell.base.utils.*
import java.math.BigDecimal
import java.math.BigInteger
import java.util.*
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
        V2
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

    final override fun toString(): String {
        // Calling toString() is considered wrong. Throwing exception in unit tests and returning str() in production
        // mode as a fallback.
        CommonUtils.failIfUnitTest()
        return str(StrFormat.V1)
    }

    private fun errType(expected: Rt_CoreValueTypes) = errType(expected.type())
    private fun errType(expected: Rt_ValueType) = Rt_ValueTypeError.exception(expected, valueType)
}

sealed class Rt_VirtualValue(val gtv: Gtv): Rt_Value() {
    override fun asVirtual() = this

    fun toFull(): Rt_Value {
        if (gtv is GtvVirtual) {
            val typeStr = type().name
            throw Rt_Exception.common("virtual:to_full:notfull:$typeStr", "Value of type $typeStr is not full")
        }
        val res = toFull0()
        return res
    }

    protected abstract fun toFull0(): Rt_Value

    companion object {
        fun toFull(v: Rt_Value): Rt_Value {
            return if (v is Rt_VirtualValue) v.toFull() else v
        }
    }
}

class Rt_EntityValue(val type: R_EntityType, val rowid: Long): Rt_Value() {
    override val valueType = Rt_CoreValueTypes.ENTITY.type()

    override fun type() = type
    override fun asObjectId() = rowid
    override fun toFormatArg() = str()
    override fun strCode(showTupleFieldNames: Boolean) = "${type.name}[$rowid]"
    override fun str(format: StrFormat) = strCode()
    override fun equals(other: Any?) = other === this || (other is Rt_EntityValue && type == other.type && rowid == other.rowid)
    override fun hashCode() = Objects.hash(type, rowid)
}

object Rt_NullValue: Rt_Value() {
    override val valueType = Rt_CoreValueTypes.NULL.type()

    override fun type() = R_NullType
    override fun toFormatArg() = str()
    override fun strCode(showTupleFieldNames: Boolean) = "null"
    override fun str(format: StrFormat) = "null"
}

sealed class Rt_VirtualCollectionValue(gtv: Gtv): Rt_VirtualValue(gtv) {
    override fun asVirtualCollection() = this
    abstract fun size(): Int
    abstract override fun asIterable(): Iterable<Rt_Value>
}

class Rt_VirtualListValue(
        gtv: Gtv,
        private val type: R_VirtualListType,
        private val elements: List<Rt_Value?>
): Rt_VirtualCollectionValue(gtv) {
    override val valueType = Rt_CoreValueTypes.VIRTUAL_LIST.type()

    override fun type() = type
    override fun asVirtualCollection() = this
    override fun asVirtualList() = this
    override fun toFormatArg() = elements
    override fun strCode(showTupleFieldNames: Boolean) = Rt_ListValue.strCode(type, elements)
    override fun str(format: StrFormat) = elements.joinToString(", ", "[", "]") { it?.str(format) ?: "null" }
    override fun equals(other: Any?) = other === this || (other is Rt_VirtualListValue && elements == other.elements)
    override fun hashCode() = elements.hashCode()

    override fun toFull0(): Rt_Value {
        val resElements = elements.map { toFull(it!!) }.toMutableList()
        return Rt_ListValue(type.innerType, resElements)
    }

    override fun size() = elements.size
    override fun asIterable() = elements.filterNotNull()

    fun contains(index: Long) = index >= 0 && index < elements.size && elements[index.toInt()] != null

    fun get(index: Long): Rt_Value {
        Rt_ListValue.checkIndex(elements.size, index)
        val value = elements[index.toInt()]
        if (value == null) {
            throw Rt_Exception.common("virtual_list:get:novalue:$index", "Element $index has no value")
        }
        return value
    }
}

class Rt_VirtualSetValue(
        gtv: Gtv,
        private val type: R_VirtualSetType,
        private val elements: Set<Rt_Value>
): Rt_VirtualCollectionValue(gtv) {
    override val valueType = Rt_CoreValueTypes.VIRTUAL_SET.type()

    override fun type() = type
    override fun asVirtualCollection() = this
    override fun asVirtualSet() = this
    override fun toFormatArg() = elements
    override fun strCode(showTupleFieldNames: Boolean) = Rt_SetValue.strCode(type, elements, showTupleFieldNames)
    override fun str(format: StrFormat): String = elements.joinToString(", ", "[", "]") { it.str(format) }
    override fun equals(other: Any?) = other === this || (other is Rt_VirtualSetValue && elements == other.elements)
    override fun hashCode() = elements.hashCode()

    override fun toFull0(): Rt_Value {
        val resElements = elements.map { toFull(it) }.toMutableSet()
        return Rt_SetValue(type.innerType, resElements)
    }

    override fun size() = elements.size
    override fun asIterable() = elements

    fun contains(value: Rt_Value) = elements.contains(value)
}

class Rt_VirtualMapValue(
    gtv: Gtv,
    private val type: R_VirtualMapType,
    private val map: Map<Rt_Value, Rt_Value>,
): Rt_VirtualValue(gtv) {
    override val valueType = Rt_CoreValueTypes.VIRTUAL_MAP.type()

    override fun type() = type
    override fun asMap() = map
    override fun toFormatArg() = map
    override fun strCode(showTupleFieldNames: Boolean) = Rt_MapValue.strCode(type, showTupleFieldNames, map)

    override fun str(format: StrFormat): String {
       return map
           .entries
           .joinToString(", ", "{", "}") { "${it.key.str(format)}=${it.value.str(format)}" }
    }

    override fun equals(other: Any?) = other === this || (other is Rt_VirtualMapValue && map == other.map)
    override fun hashCode() = map.hashCode()

    override fun asIterable(): Iterable<Rt_Value> {
        return Iterables.transform(map.entries) { entry ->
            Rt_TupleValue(type.virtualEntryType, immListOf(entry.key, entry.value))
        }
    }

    override fun toFull0(): Rt_Value {
        val resMap = map
                .mapKeys { (k, _) -> toFull(k) }
                .mapValues { (_, v) -> toFull(v) }
                .toMutableMap()
        return Rt_MapValue(type.innerType, resMap)
    }
}

class Rt_TupleValue(val type: R_TupleType, val elements: List<Rt_Value>): Rt_Value() {
    init {
        checkEquals(elements.size, type.fields.size)
    }

    override val valueType = Rt_CoreValueTypes.TUPLE.type()

    override fun type() = type
    override fun asTuple() = elements
    override fun toFormatArg() = str()
    override fun equals(other: Any?) = other === this || (other is Rt_TupleValue && elements == other.elements)
    override fun hashCode() = elements.hashCode()

    override fun str(format: StrFormat) = str("", type, elements, format)
    override fun strCode(showTupleFieldNames: Boolean) = strCode("", type, elements, showTupleFieldNames)

    companion object {
        fun make(type: R_TupleType, vararg elements: Rt_Value): Rt_Value {
            return Rt_TupleValue(type, elements.toImmList())
        }

        fun str(prefix: String, type: R_TupleType, elements: List<Rt_Value?>, format: StrFormat): String {
            val elems = elements.indices.joinToString(",") { elementStr(type, elements, it, format) }
            return "$prefix($elems)"
        }

        private fun elementStr(type: R_TupleType, elements: List<Rt_Value?>, idx: Int, format: StrFormat): String {
            val name = type.fields[idx].name
            val value = elements[idx]
            val valueStr = value?.str(format) ?: "null"
            return if (name == null) valueStr else "$name=$valueStr"
        }

        fun strCode(prefix: String, type: R_TupleType, elements: List<Rt_Value?>, showTupleFieldNames: Boolean): String {
            val elems = elements.indices.joinToString(",") {
                elementStrCode(type, elements, showTupleFieldNames, it)
            }
            return "$prefix($elems)"
        }

        private fun elementStrCode(
            type: R_TupleType,
            elements: List<Rt_Value?>,
            showTupleFieldNames: Boolean,
            idx: Int,
        ): String {
            val name = type.fields[idx].name
            val value = elements[idx]
            val valueStr = value?.strCode() ?: "null"
            return if (name == null || !showTupleFieldNames) valueStr else "$name=$valueStr"
        }
    }
}

class Rt_VirtualTupleValue(
        gtv: Gtv,
        private val type: R_VirtualTupleType,
        private val elements: List<Rt_Value?>
): Rt_VirtualValue(gtv) {
    override val valueType = Rt_CoreValueTypes.VIRTUAL_TUPLE.type()

    override fun type() = type
    override fun asVirtualTuple() = this
    override fun toFormatArg() = str()
    override fun equals(other: Any?) = other === this || (other is Rt_VirtualTupleValue && elements == other.elements)
    override fun hashCode() = elements.hashCode()

    override fun str(format: StrFormat) = Rt_TupleValue.str("virtual", type.innerType, elements, format)
    override fun strCode(showTupleFieldNames: Boolean) =
            Rt_TupleValue.strCode("virtual", type.innerType, elements, showTupleFieldNames)

    override fun toFull0(): Rt_Value {
        val resElements = elements.map { toFull(it!!) }
        return Rt_TupleValue(type.innerType, resElements)
    }

    fun get(index: Int): Rt_Value {
        val value = elements[index]
        if (value == null) {
            val attr = type.innerType.fields[index].name ?: "$index"
            throw Rt_Exception.common("virtual_tuple:get:novalue:$attr", "Field '$attr' has no value")
        }
        return value
    }
}

class Rt_StructValue(private val type: R_StructType, private val attributes: MutableList<Rt_Value>): Rt_Value() {
    override val valueType = Rt_CoreValueTypes.STRUCT.type()

    override fun type() = type
    override fun asStruct() = this
    override fun toFormatArg() = str()
    override fun equals(other: Any?) = other === this || (other is Rt_StructValue && attributes == other.attributes)
    override fun hashCode() = type.hashCode() * 31 + attributes.hashCode()

    override fun str(format: StrFormat) = str(this, type, type.struct, attributes, format)
    override fun strCode(showTupleFieldNames: Boolean) = strCode(this, type, type.struct, attributes)

    fun get(index: Int): Rt_Value {
        return attributes[index]
    }

    fun set(index: Int, value: Rt_Value) {
        attributes[index] = value
    }

    class Builder(private val type: R_StructType) {
        private val v0: Rt_Value = Rt_RangeValue(0, 0, 0)
        private val values = MutableList(type.struct.attributes.size) { v0 }
        private var done = false

        fun set(attr: R_Attribute, value: Rt_Value) {
            check(!done)
            require(value !== v0)
            val index = attr.index
            require(values[index] === v0) { "$index $attr" }
            values[index] = value
        }

        fun build(): Rt_Value {
            check(!done)
            done = true
            for (index in values.indices) {
                require(values[index] !== v0) { index }
            }
            return Rt_StructValue(type, values)
        }
    }

    companion object {
        private val STR_RECURSION_DETECTOR = Rt_ValueRecursionDetector()

        fun str(self: Rt_Value, type: R_Type, struct: R_Struct, attributes: List<out Rt_Value?>, format: StrFormat): String {
            return STR_RECURSION_DETECTOR.calculate(self) {
                val attrs = attributes.withIndex().joinToString(",") { (i, attr) ->
                    val n = struct.attributesList[i].name
                    val v = attr?.str(format)
                    "$n=$v"
                }
                "${type.name}{$attrs}"
            } ?: "${type.name}{...}"
        }

        fun strCode(self: Rt_Value, type: R_Type, struct: R_Struct, attributes: List<out Rt_Value?>): String {
            return STR_RECURSION_DETECTOR.calculate(self) {
                val attrs = attributes.indices.joinToString(",") { attributeStrCode(struct, attributes, it) }
                "${type.name}[$attrs]"
            } ?: "${type.name}[...]"
        }

        private fun attributeStrCode(struct: R_Struct, attributes: List<out Rt_Value?>, idx: Int): String {
            val name = struct.attributesList[idx].name
            val value = attributes[idx]
            val valueStr = value?.strCode()
            return "$name=$valueStr"
        }
    }
}

class Rt_VirtualStructValue(
        gtv: Gtv,
        private val type: R_VirtualStructType,
        private val attributes: List<Rt_Value?>
): Rt_VirtualValue(gtv) {
    override val valueType = Rt_CoreValueTypes.VIRTUAL_STRUCT.type()

    override fun type() = type
    override fun asVirtualStruct() = this
    override fun toFormatArg() = str()
    override fun equals(other: Any?) = other === this || (other is Rt_VirtualStructValue && attributes == other.attributes)
    override fun hashCode() = type.hashCode() * 31 + attributes.hashCode()

    override fun str(format: StrFormat) = Rt_StructValue.str(this, type, type.innerType.struct, attributes, format)
    override fun strCode(showTupleFieldNames: Boolean) =
            Rt_StructValue.strCode(this, type, type.innerType.struct, attributes)

    fun get(index: Int): Rt_Value {
        val value = attributes[index]
        if (value == null) {
            val typeName = type.innerType.name
            val attr = type.innerType.struct.attributesList[index].name
            throw Rt_Exception.common("virtual_struct:get:novalue:$typeName:$attr", "Attribute '$typeName.$attr' has no value")
        }
        return value
    }

    override fun toFull0(): Rt_Value {
        val fullAttrValues = attributes.map { toFull(it!!) }.toMutableList()
        return Rt_StructValue(type.innerType, fullAttrValues)
    }
}

class Rt_ObjectValue(private val type: R_ObjectType): Rt_Value() {
    override val valueType = Rt_CoreValueTypes.OBJECT.type()

    override fun type() = type
    override fun strCode(showTupleFieldNames: Boolean) = type.name
    override fun str(format: StrFormat) = type.name
}

class Rt_FunctionValue(
        private val type: R_Type,
        private val mapping: R_PartialCallMapping,
        private val target: R_FunctionCallTarget,
        private val baseValue: Rt_Value?,
        exprValues: List<Rt_Value>
): Rt_Value() {
    private val exprValues = let {
        checkEquals(exprValues.size, mapping.exprCount)
        exprValues.toImmList()
    }

    override val valueType = Rt_CoreValueTypes.FUNCTION.type()

    override fun type() = type
    override fun asFunction() = this

    override fun strCode(showTupleFieldNames: Boolean): String {
        return STR_RECURSION_DETECTOR.calculate(this) {
            val argsStr = mapping.args.joinToString(",") { if (it.wild) "*" else exprValues[it.index].strCode() }
            "fn[${target.strCode(baseValue)}($argsStr)]"
        } ?: "fn[...]"
    }

    override fun str(format: StrFormat) = "${target.str(baseValue, format)}(*)"

    fun call(callCtx: Rt_CallContext, args: List<Rt_Value>): Rt_Value {
        checkEquals(args.size, mapping.wildCount)
        val combinedArgs = mapping.args.map { if (it.wild) args[it.index] else exprValues[it.index] }
        return target.call(callCtx, baseValue, combinedArgs)
    }

    fun combine(newType: R_Type, newMapping: R_PartialCallMapping, newArgs: List<Rt_Value>): Rt_Value {
        checkEquals(newMapping.args.size, mapping.wildCount)
        checkEquals(newArgs.size, newMapping.exprCount)

        val resExprValues = exprValues + newArgs

        val resArgMappings = mapping.args.map { m1 ->
            if (m1.wild) {
                val m2 = newMapping.args[m1.index]
                if (m2.wild) m2 else R_PartialArgMapping(false, mapping.exprCount + m2.index)
            } else {
                m1
            }
        }

        val resMapping = R_PartialCallMapping(resExprValues.size, newMapping.wildCount, resArgMappings)
        return Rt_FunctionValue(newType, resMapping, target, baseValue, resExprValues)
    }

    companion object {
        private val STR_RECURSION_DETECTOR = Rt_ValueRecursionDetector()
    }
}
