/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvByteArray
import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.sql.PreparedStatementParams
import net.postchain.rell.base.sql.ResultSetRow
import net.postchain.rell.base.utils.CommonUtils
import net.postchain.rell.base.utils.immSetOf
import org.jooq.DataType
import org.jooq.impl.SQLDataType
import kotlin.reflect.full.createType

class Rt_ByteArrayValue private constructor(internal val value: ByteArray):
    Rt_Value, Rt_IterableValue {
    override val name
        get() = Companion.name

    override val type
        get() = Rt_PrimitiveTypes.BYTE_ARRAY

    override fun strCode(showTupleFieldNames: Boolean) = "byte_array[${CommonUtils.bytesToHex(value)}]"
    override fun str(format: Rt_StrFormat) = "0x" + CommonUtils.bytesToHex(value)
    override fun equals(other: Any?) =
        other === this || (other is Rt_ByteArrayValue && value.contentEquals(other.value))

    override fun hashCode() = value.contentHashCode()

    override fun iterator(): Iterator<Rt_Value> = value.map {
        val signed = it.toInt()
        val unsigned = if (signed >= 0) signed else (signed + 256)
        Rt_IntValue.get(unsigned.toLong())
    }.iterator()

    companion object:
        Rt_GtvCompatibleValueClass<Rt_ByteArrayValue>,
        Rt_NativeCompatibleValueClass<Rt_ByteArrayValue>,
        Rt_SqlCompatibleValueClass<Rt_ByteArrayValue> {

        override val name
            get() = "byte_array"

        override val klass = Rt_ByteArrayValue::class
        override val rrType: RR_Type = RR_Type.Primitive(RR_PrimitiveKind.BYTE_ARRAY)
        override val nativeTypes = immSetOf(ByteArray::class.createType())

        override val sqlType: DataType<ByteArray>
            get() = SQLDataType.BLOB

        override val comparator: Comparator<Rt_Value> = Comparator { a, b ->
            val la = a.asByteArray()
            val lb = b.asByteArray()
            val len = minOf(la.size, lb.size)
            for (i in 0 until len) {
                val c = (la[i].toInt() and 0xFF).compareTo(lb[i].toInt() and 0xFF)
                if (c != 0) return@Comparator c
            }
            la.size.compareTo(lb.size)
        }

        val EMPTY: Rt_ByteArrayValue = Rt_ByteArrayValue(ByteArray(0))

        fun get(value: ByteArray): Rt_ByteArrayValue {
            return if (value.isEmpty()) EMPTY else Rt_ByteArrayValue(value)
        }

        override fun toGtv(value: Rt_ByteArrayValue, pretty: Boolean): Gtv = GtvByteArray(value.value)

        override fun fromGtv(ctx: GtvToRtContext, gtv: Gtv): Rt_ByteArrayValue =
            get(GtvRtUtils.gtvToByteArray(ctx, gtv, "byte_array"))

        override fun toNative(value: Rt_ByteArrayValue): Any = value.value.copyOf()

        override fun fromNative(value: Any?): Rt_ByteArrayValue = get((value as ByteArray).copyOf())

        override fun toSqlValue(value: Rt_ByteArrayValue): Any = value.value

        override fun toSql(value: Rt_ByteArrayValue, params: PreparedStatementParams, idx: Int) =
            params.setBytes(idx, value.value)

        override fun fromSql(row: ResultSetRow, idx: Int, nullable: Boolean): Rt_Value {
            val v = row.getBytes(idx)
            return if (v != null) get(v) else Rt_SqlNull.check(name, nullable)
        }
    }
}
