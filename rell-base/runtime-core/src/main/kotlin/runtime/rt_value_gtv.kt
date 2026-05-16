/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.*
import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.immMapOf
import net.postchain.rell.base.utils.immSetOf
import java.math.BigInteger
import kotlin.reflect.full.createType

@ConsistentCopyVisibility
@JvmRecord
data class Rt_GtvValue private constructor(val value: Gtv): Rt_Value {
    override val name
        get() = Companion.name

    override val type
        get() = Rt_PrimitiveTypes.GTV


    override fun strCode(showTupleFieldNames: Boolean) = "gtv[${str(Rt_StrFormat.V2)}]"

    override fun str(format: Rt_StrFormat): String = when (format) {
        Rt_StrFormat.V1 -> toString(value)
        Rt_StrFormat.V2 -> value.toString()
    }

    override fun strPretty(indent: Int): String {
        if (value.type == GtvType.ARRAY) {
            val array = value.asArray()
            if (array.isNotEmpty()) {
                val indentStr = "    ".repeat(indent)
                return array.joinToString(",", "[", "\n$indentStr]") {
                    val s = Rt_GtvValue(it).strPretty(indent + 1)
                    "\n$indentStr    $s"
                }
            }
        } else if (value.type == GtvType.DICT) {
            val map = value.asDict()
            if (map.isNotEmpty()) {
                val indentStr = "    ".repeat(indent)
                return map.entries.joinToString(",", "[", "\n$indentStr]") {
                    val k = GtvFactory.gtv(it.key).toString()
                    val v = Rt_GtvValue(it.value).strPretty(indent + 1)
                    "\n$indentStr    $k: $v"
                }
            }
        }

        return super.strPretty(indent)
    }

    companion object:
        Rt_GtvCompatibleValueClass<Rt_GtvValue>,
        Rt_NativeCompatibleValueClass<Rt_GtvValue>,
        Rt_PrimitiveFactory<Rt_GtvValue, Gtv> {

        override val name
            get() = "gtv"
        override val rrType: RR_Type = RR_Type.Primitive(RR_PrimitiveKind.GTV)
        override val nativeTypes = immSetOf(Gtv::class.createType())

        val NULL: Rt_GtvValue = Rt_GtvValue(GtvNull)

        private val ZERO_INTEGER: Rt_GtvValue = Rt_GtvValue(GtvFactory.gtv(0))
        private val ZERO_BIG_INTEGER: Rt_GtvValue = Rt_GtvValue(GtvFactory.gtv(BigInteger.ZERO))
        private val EMPTY_STRING: Rt_GtvValue = Rt_GtvValue(GtvFactory.gtv(""))
        private val EMPTY_BYTE_ARRAY: Rt_GtvValue = Rt_GtvValue(GtvFactory.gtv(ByteArray(0)))
        private val EMPTY_ARRAY: Rt_GtvValue = Rt_GtvValue(GtvFactory.gtv(immListOf()))
        private val EMPTY_DICT: Rt_GtvValue = Rt_GtvValue(GtvFactory.gtv(immMapOf()))

        fun get(value: Gtv): Rt_GtvValue = when (value) {
            GtvNull -> NULL
            is GtvInteger -> if (value.integer == 0L) ZERO_INTEGER else Rt_GtvValue(value)
            is GtvBigInteger -> if (value.integer == BigInteger.ZERO) ZERO_BIG_INTEGER else Rt_GtvValue(value)
            is GtvString -> if (value.string.isEmpty()) EMPTY_STRING else Rt_GtvValue(value)
            is GtvByteArray -> if (value.bytearray.isEmpty()) EMPTY_BYTE_ARRAY else Rt_GtvValue(value)
            is GtvArray -> if (value.array.isEmpty()) EMPTY_ARRAY else Rt_GtvValue(value)
            is GtvDictionary -> if (value.dict.isEmpty()) EMPTY_DICT else Rt_GtvValue(value)
            else -> Rt_GtvValue(value)
        }

        override fun wrap(value: Gtv): Rt_GtvValue = get(value)

        override fun toGtv(value: Rt_GtvValue, pretty: Boolean): Gtv = value.value
        override fun fromGtv(ctx: GtvToRtContext, gtv: Gtv): Rt_GtvValue = get(gtv)

        override fun toNative(value: Rt_GtvValue): Any = value.value
        override fun fromNative(value: Any?): Rt_GtvValue = get(value as Gtv)

        private fun toString(value: Gtv): String = try {
            PostchainGtvUtils.gtvToJson(value)
        } catch (_: Exception) {
            value.toString() // Fallback, just in case (did not happen).
        }
    }
}
