/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvType
import net.postchain.rell.base.compiler.base.def.C_GlobalFunction
import net.postchain.rell.base.compiler.base.def.C_StructGlobalFunction
import net.postchain.rell.base.compiler.base.lib.C_LibType
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lib.type.Lib_Type_Struct
import net.postchain.rell.base.lib.type.R_OperationType
import net.postchain.rell.base.lib.type.Rt_RangeValue
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_ValueRecursionDetector
import net.postchain.rell.base.utils.doc.DocCode
import net.postchain.rell.base.utils.mapToImmList

class R_StructType(val struct: R_Struct): R_SimpleType(struct.name) {
    override fun equals0(other: R_Type) = false
    override fun hashCode0() = System.identityHashCode(this)

    override fun isReference() = true
    override fun isDirectMutable() = struct.isDirectlyMutable()
    override fun isDirectPure() = true
    override fun completeFlags() = struct.flags.typeFlags

    override fun componentTypes() = struct.attributesList.mapToImmList { it.type }
    override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_Struct(struct)

    override fun toMetaGtv() = struct.typeMetaGtv

    override fun getLibType0(): C_LibType {
        val constructorFn: C_GlobalFunction = C_StructGlobalFunction(struct)
        val valueMembers = lazy { Lib_Type_Struct.getValueMembers(struct) }

        val ms = struct.mirrorStructs
        return if (ms != null) {
            val typeDef = if (struct == ms.immutable) Lib_Rell.IMMUTABLE_MIRROR_STRUCT else Lib_Rell.MUTABLE_MIRROR_STRUCT
            C_LibType.make(typeDef, ms.innerType, constructorFn = constructorFn, valueMembers = valueMembers)
        } else {
            C_LibType.make(this, DocCode.link(struct.name), constructorFn = constructorFn, valueMembers = valueMembers)
        }
    }

    companion object {
        internal val IMMUTABLE_META = R_TypeMeta.make { t ->
            val ms = when (t) {
                is R_EntityType -> t.rEntity.mirrorStructs
                is R_OperationType -> t.rOperation.mirrorStructs
                else -> null
            }
            ms?.immutable?.type
        }

        internal val MUTABLE_META = R_TypeMeta.make { t ->
            val ms = when (t) {
                is R_EntityType -> t.rEntity.mirrorStructs
                is R_OperationType -> t.rOperation.mirrorStructs
                else -> null
            }
            ms?.mutable?.type
        }
    }
}

class Rt_StructValue(private val type: R_StructType, private val attributes: MutableList<Rt_Value>): Rt_Value() {
    override val valueType = Rt_CoreValueTypes.STRUCT.type()

    override fun type() = type
    override fun asStruct() = this
    override fun equals(other: Any?) = other === this || (other is Rt_StructValue && attributes == other.attributes)
    override fun hashCode() = type.hashCode() * 31 + attributes.hashCode()

    override fun str(format: StrFormat) = str(this, type, type.struct, attributes, format)
    override fun strCode(showTupleFieldNames: Boolean) = strCode(this, type, type.struct, attributes)

    override fun strPretty(indent: Int): String {
        val indentStr = "    ".repeat(indent)
        return STR_RECURSION_DETECTOR.calculate(this) {
            val attrs = attributes.withIndex().joinToString(",") { (i, attr) ->
                val n = type.struct.attributesList[i].name
                val v = attr.strPretty(indent + 1)
                "\n$indentStr    $n = $v"
            }
            "${type.name}{$attrs\n$indentStr}"
        } ?: "${type.name}{...}"
    }

    fun get(index: Int): Rt_Value {
        return attributes[index]
    }

    fun set(index: Int, value: Rt_Value) {
        type.struct.attributesList[index].validator?.check(value)?.raise()
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
            return createValidated(type, values)
        }
    }

    companion object {
        fun createValidated(type: R_StructType, attributes: MutableList<Rt_Value>): Rt_StructValue {
            for (attr in type.struct.attributesList) {
                attr.validator?.check(attributes[attr.index])?.raise()
            }
            return Rt_StructValue(type, attributes)
        }

        private val STR_RECURSION_DETECTOR = Rt_ValueRecursionDetector()

        fun str(self: Rt_Value, type: R_Type, struct: R_Struct, attributes: List<Rt_Value?>, format: StrFormat): String {
            return STR_RECURSION_DETECTOR.calculate(self) {
                val attrs = attributes.withIndex().joinToString(",") { (i, attr) ->
                    val n = struct.attributesList[i].name
                    val v = attr?.str(format)
                    "$n=$v"
                }
                "${type.name}{$attrs}"
            } ?: "${type.name}{...}"
        }

        fun strCode(self: Rt_Value, type: R_Type, struct: R_Struct, attributes: List<Rt_Value?>): String {
            return STR_RECURSION_DETECTOR.calculate(self) {
                val attrs = attributes.indices.joinToString(",") { attributeStrCode(struct, attributes, it) }
                "${type.name}[$attrs]"
            } ?: "${type.name}[...]"
        }

        private fun attributeStrCode(struct: R_Struct, attributes: List<Rt_Value?>, idx: Int): String {
            val name = struct.attributesList[idx].name
            val value = attributes[idx]
            val valueStr = value?.strCode()
            return "$name=$valueStr"
        }
    }
}

class GtvRtConversion_Struct(private val struct: R_Struct): GtvRtConversion() {
    private val arrayConv = ArrayConv()

    override fun directCompatibility() = R_GtvCompatibility(true, true)

    override fun rtToGtv(rt: Rt_Value, pretty: Boolean): Gtv {
        val attrs = struct.attributesList
        return if (pretty) {
            val rtStruct = rt.asStruct()
            val gtvFields = attrs
                .mapIndexed { i, attr -> attr.name to attr.type.rtToGtv(rtStruct.get(i), pretty) }
                .toMap()
            GtvFactory.gtv(gtvFields)
        } else {
            val rtStruct = rt.asStruct()
            val gtvFields = attrs
                .mapIndexed { i, attr -> attr.type.rtToGtv(rtStruct.get(i), pretty) }
                .toTypedArray()
            GtvArray(gtvFields)
        }
    }

    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        return when {
            ctx.pretty && gtv.type == GtvType.DICT -> gtvToRtDict(ctx, gtv)
            else -> arrayConv.convert(ctx, gtv)
        }
    }

    private fun gtvToRtDict(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val type = struct.type
        val gtvFields = GtvRtUtils.gtvToMap(ctx, gtv, type)

        val attrs = struct.attributesList
        val rtAttrs = attrs
            .map { attr ->
                val gtvAttr = gtvFields[attr.name]
                gtvToRtAttr(ctx, attr, gtvAttr, struct.rDefBase)
            }

        for (key in gtvFields.keys) {
            if (key !in struct.strAttributes) {
                val typeName = struct.name
                throw GtvRtUtils.errGtv(ctx, "struct_badkey:$typeName:$key",
                    "Wrong key in Gtv dictionary for type '$typeName': '$key'")
            }
        }

        return ctx.rtValue {
            Rt_StructValue.createValidated(type, rtAttrs.toMutableList())
        }
    }

    private fun gtvToRtAttr(
        ctx: GtvToRtContext,
        attr: R_Attribute,
        gtvAttr: Gtv?,
        rDefBase: R_DefinitionBase?,
    ): Rt_Value {
        if (gtvAttr != null) {
            val attrCtx = ctx.updateSymbol(GtvToRtSymbol_Attr(struct.name, attr))
            return attr.type.gtvToRt(attrCtx, gtvAttr)
        }

        val expr = attr.expr
        if (ctx.defaultValueEvaluator != null && rDefBase != null && expr != null) {
            return ctx.rtValue {
                ctx.defaultValueEvaluator.evaluate(rDefBase, expr)
            }
        }

        val typeName = struct.name
        throw GtvRtUtils.errGtv(ctx,
            "struct_noattr:$typeName:${attr.name}",
            "Missing struct attribute value: '$typeName.${attr.name}'",
        )
    }

    private inner class ArrayConv {
        private val attrs = struct.attributesList
        private val minAttrCount = attrs.indexOfLast { !it.hasExpr } + 1

        fun convert(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
            val type = struct.type
            val gtvAttrValues = gtvToAttrValues(ctx, gtv, type, struct, minAttrCount)

            val rtAttrs = attrs
                .mapIndexed { i, attr ->
                    val gtvAttr = gtvAttrValues.getOrNull(i)
                    gtvToRtAttr(ctx, attr, gtvAttr, struct.rDefBase)
                }

            return ctx.rtValue {
                Rt_StructValue.createValidated(type, rtAttrs.toMutableList())
            }
        }
    }

    companion object {
        fun gtvToAttrValues(
            ctx: GtvToRtContext,
            gtv: Gtv,
            type: R_Type,
            struct: R_Struct,
            minCount: Int,
        ): List<Gtv> {
            val maxCount = struct.attributesList.size
            check(minCount <= maxCount)

            val gtvFields = GtvRtUtils.gtvToArray(ctx, gtv, type)
            val actualCount = gtvFields.size

            if (actualCount < minCount || actualCount > maxCount) {
                throw errWrongArraySize(ctx, type, minCount, maxCount, actualCount)
            }

            return gtvFields.toList()
        }

        fun errWrongArraySize(
            ctx: GtvToRtContext,
            type: R_Type,
            minCount: Int,
            maxCount: Int,
            actualCount: Int,
        ): Rt_Exception {
            val typeName = type.name
            val expCountStr = if (minCount == maxCount) "$minCount" else "$minCount..$maxCount"
            return GtvRtUtils.errGtv(ctx, "struct_size:$typeName:$minCount:$maxCount:$actualCount",
                    "Wrong Gtv array size for struct '$typeName': $actualCount instead of $expCountStr")
        }
    }
}
