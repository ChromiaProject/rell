/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.gtv.*
import net.postchain.rell.base.compiler.base.core.C_IdeSymbolInfo
import net.postchain.rell.base.compiler.base.lib.C_LibType
import net.postchain.rell.base.lib.type.Lib_Type_Tuple
import net.postchain.rell.base.mtype.M_TupleTypeUtils
import net.postchain.rell.base.mtype.M_Types
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_TupleComparator
import net.postchain.rell.base.runtime.utils.toGtv
import net.postchain.rell.base.utils.CommonUtils
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.toImmList
import java.util.*

class R_TupleType(fields: List<R_TupleField>): R_Type(calcName(fields)) {
    val fields = fields.toImmList()
    val virtualType = R_VirtualTupleType(this)

    init {
        check(this.fields.isNotEmpty())
        for (i in this.fields.indices) {
            checkEquals(this.fields[i].index, i)
        }
    }

    private val isError = fields.any { it.type.isError() }

    override fun equals0(other: R_Type): Boolean = other is R_TupleType && fields == other.fields
    override fun hashCode0() = fields.hashCode()

    override fun isDirectPure() = true
    override fun isReference() = true
    override fun isError() = isError
    override fun isDirectMutable() = false
    override fun isDirectMixedTuple() = fields.any { it.name != null } && fields.any { it.name == null }

    override fun strCode() = name

    override fun getLibType0(): C_LibType {
        val fieldTypes = fields.map { it.type.mType }
        val fieldNames = M_TupleTypeUtils.makeNames(fields) { it.name?.str }
        val mType = M_Types.tuple(fieldTypes, fieldNames)
        return C_LibType.make(
            mType,
            valueMembers = lazy { Lib_Type_Tuple.getValueMembers(this) },
        )
    }

    override fun explicitComponentTypes() = fields.map { it.type }.toList()

    override fun isAssignableFrom(type: R_Type): Boolean {
        if (type !is R_TupleType) return false
        if (fields.size != type.fields.size) return false

        for (i in fields.indices) {
            val field = fields[i]
            val otherField = type.fields[i]
            if (field.name != otherField.name) return false
            if (!field.type.isAssignableFrom(otherField.type)) return false
        }

        return true
    }

    override fun calcCommonType(other: R_Type): R_Type? {
        if (other !is R_TupleType) return null
        if (fields.size != other.fields.size) return null

        val resFields = fields.mapIndexed { i, field ->
            val otherField = other.fields[i]
            if (field.name != otherField.name) return null

            val type = commonTypeOpt(field.type, otherField.type)
            if (type == null) return null

            when (type) {
                field.type -> field
                otherField.type -> otherField
                else -> R_TupleField(i, field.name, type)
            }
        }

        return R_TupleType(resFields)
    }

    override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_Tuple(this)

    override fun comparator(): Comparator<Rt_Value>? {
        val fieldComparators = mutableListOf<Comparator<Rt_Value>>()
        for (field in fields) {
            val comparator = field.type.comparator()
            comparator ?: return null
            fieldComparators.add(comparator)
        }
        return Rt_TupleComparator(fieldComparators)
    }

    override fun toMetaGtv() = mapOf(
            "type" to "tuple".toGtv(),
            "fields" to fields.map { it.toMetaGtv() }.toGtv()
    ).toGtv()

    companion object {
        private fun calcName(fields: List<R_TupleField>): String {
            val fieldsStr = fields.joinToString(",") { it.strCode() }
            val comma = if (fields.size == 1 && fields[0].name == null) "," else ""
            return "($fieldsStr$comma)"
        }

        fun create(vararg fields: R_Type): R_TupleType {
            return create(fields.toList())
        }

        fun create(fields: List<R_Type>): R_TupleType {
            val fieldsList = fields.mapIndexed { i, type -> R_TupleField(i, null, type) }
            return R_TupleType(fieldsList)
        }

        fun createNamed(vararg fields: Pair<String?, R_Type>): R_TupleType {
            val fieldsList = fields.mapIndexed { i, (name, type) ->
                val rIdeName = name?.let { s -> R_IdeName(R_Name.of(s), C_IdeSymbolInfo.MEM_TUPLE_ATTR) }
                R_TupleField(i, rIdeName, type)
            }
            return R_TupleType(fieldsList)
        }
    }
}

class R_TupleField(val index: Int, val name: R_IdeName?, val type: R_Type) {
    init {
        check(index >= 0)
    }

    fun str(): String = strCode()

    fun strCode(): String {
        return if (name != null) "${name}:${type.strCode()}" else type.strCode()
    }

    override fun toString(): String {
        CommonUtils.failIfUnitTest()
        return str()
    }

    override fun equals(other: Any?): Boolean = other === this || (other is R_TupleField && name == other.name && type == other.type)
    override fun hashCode() = Objects.hash(name, type)

    fun toMetaGtv() = mapOf(
        "name" to (name?.rName?.str?.toGtv() ?: GtvNull),
        "type" to type.toMetaGtv(),
    ).toGtv()
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

class GtvRtConversion_Tuple(val type: R_TupleType): GtvRtConversion() {
    override fun directCompatibility() = R_GtvCompatibility(true, true)

    override fun rtToGtv(rt: Rt_Value, pretty: Boolean): Gtv {
        return if (pretty && type.fields.all { it.name != null }) rtToGtvPretty(rt) else rtToGtvCompact(rt)
    }

    private fun rtToGtvPretty(rt: Rt_Value): Gtv {
        val rtFields = rt.asTuple()
        checkEquals(rtFields.size, type.fields.size)
        val gtv = rtFields
            .mapIndexed { i, rtField ->
                val field = type.fields[i]
                field.name!!.str to field.type.rtToGtv(rtField, true)
            }
            .toMap()
        return GtvFactory.gtv(gtv)
    }

    private fun rtToGtvCompact(rt: Rt_Value): Gtv {
        val rtFields = rt.asTuple()
        checkEquals(rtFields.size, type.fields.size)
        val gtvFields = rtFields
            .mapIndexed { i, rtField ->
                type.fields[i].type.rtToGtv(rtField, false)
            }
            .toTypedArray()
        return GtvArray(gtvFields)
    }

    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        return if (ctx.pretty && type.fields.all { it.name != null } && gtv.type == GtvType.DICT) {
            gtvToRtDict(ctx, gtv)
        } else {
            gtvToRtArray(ctx, gtv)
        }
    }

    private fun gtvToRtDict(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val gtvFields = GtvRtUtils.gtvToMap(ctx, gtv, type)
        checkFieldCount(ctx, type, gtvFields.size, "dictionary")

        val rtFields = type.fields.mapIndexed { _, field ->
            val key = field.name!!
            if (key.str !in gtvFields) {
                throw GtvRtUtils.errGtv(ctx, "tuple_nokey:$key", "Key missing in Gtv dictionary: '$key'")
            }
            val gtvField = gtvFields.getValue(key.str)
            field.type.gtvToRt(ctx, gtvField)
        }

        return ctx.rtValue {
            Rt_TupleValue(type, rtFields)
        }
    }

    private fun gtvToRtArray(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val gtvFields = gtvArrayToFields(ctx, type, gtv)
        val rtFields = gtvFields.mapIndexed { i, gtvField ->
            type.fields[i].type.gtvToRt(ctx, gtvField)
        }
        return ctx.rtValue {
            Rt_TupleValue(type, rtFields)
        }
    }

    companion object {
        fun gtvArrayToFields(ctx: GtvToRtContext, type: R_TupleType, gtv: Gtv): List<Gtv> {
            val gtvFields = GtvRtUtils.gtvToArray(ctx, gtv, type)
            checkFieldCount(ctx, type, gtvFields.size, "array")
            return gtvFields.toList()
        }

        private fun checkFieldCount(ctx: GtvToRtContext, type: R_TupleType, actualCount: Int, structure: String) {
            val expectedCount = type.fields.size
            if (actualCount != expectedCount) {
                throw GtvRtUtils.errGtv(ctx, "tuple_count:$expectedCount:$actualCount",
                        "Wrong Gtv $structure size: $actualCount instead of $expectedCount")
            }
        }
    }
}
