/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import com.google.common.collect.Iterables
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvByteArray
import net.postchain.rell.base.compiler.base.lib.C_SysFunctionBody
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.lib.Lib_Crypto
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.Db_SysFunction
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_Comparator
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.sql.SqlConstants
import net.postchain.rell.base.utils.CommonUtils
import org.bouncycastle.util.Arrays
import org.jooq.util.postgres.PostgresDataType
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.*

object Lib_Type_ByteArray {
    val DB_SUBSCRIPT: Db_SysFunction = Db_SysFunction.template("byte_array.[]", 2, "GET_BYTE(#0, (#1)::INT)")

    private val FromHex = C_SysFunctionBody.simple(pure = true) { a ->
        val s = a.asString()
        val bytes = Rt_Utils.wrapErr("fn:byte_array.from_hex") {
            CommonUtils.hexToBytes(s)
        }
        Rt_ByteArrayValue.get(bytes)
    }

    private val FromList = C_SysFunctionBody.simple(pure = true) { a ->
        val s = a.asList()
        val r = ByteArray(s.size)
        for (i in s.indices) {
            val b = s[i].asInteger()
            if (b < 0 || b > 255) throw Rt_Exception.common("fn:byte_array.from_list:$b", "Byte value out of range: $b")
            r[i] = b.toByte()
        }
        Rt_ByteArrayValue.get(r)
    }

    private val LIST_OF_INTEGER = R_ListType(R_IntegerType)

    val NAMESPACE = Ld_NamespaceDsl.make {
        alias("pubkey", "byte_array")

        type("byte_array", rType = R_ByteArrayType) {
            comment("An array of bytes. This type is immutable.")
            parent(type = "iterable<integer>")

            constructor {
                comment("Creates a byte_array from a hexadecimal string.")
                param("hex", type = "text", comment = "The hexadecimal string.")
                bodyRaw(FromHex)
            }

            constructor {
                comment("Creates a byte_array from a list of integers.")
                deprecated(newName = "byte_array.from_list")
                param("list", type = "list<integer>", comment = "The list of integers.")
                bodyRaw(FromList)
            }

            staticFunction("from_list", result = "byte_array") {
                comment("Creates a byte_array from a list of integers.")
                param("list", type = "list<integer>", comment = "The list of integers.")
                bodyRaw(FromList)
            }

            staticFunction("from_hex", result = "byte_array") {
                comment("Creates a byte_array from a hexadecimal string.")
                param("value", type = "text", comment = "The hexadecimal string.")
                bodyRaw(FromHex)
            }

            staticFunction("from_base64", result = "byte_array") {
                comment("Creates a byte_array from a Base64 string.")
                param("value", type = "text", comment = "The Base64 string.")
                body { value ->
                    val s = value.asString()
                    val bytes = Rt_Utils.wrapErr("fn:byte_array.from_base64") {
                        Base64.getDecoder().decode(s)
                    }
                    Rt_ByteArrayValue.get(bytes)
                }
            }

            function("empty", "boolean", pure = true) {
                comment("Returns true if the byte_array is empty, otherwise returns false.")
                dbFunctionTemplate("byte_array.empty", 1, "(LENGTH(#0) = 0)")
                body { array ->
                    val byteArray = array.asByteArray()
                    Rt_BooleanValue.get(byteArray.isEmpty())
                }
            }

            function("size", "integer", pure = true) {
                comment("Returns the number of bytes.")
                alias("len", C_MessageType.ERROR)
                dbFunctionTemplate("byte_array.size", 1, "LENGTH(#0)")
                body { array ->
                    val byteArray = array.asByteArray()
                    Rt_IntValue.get(byteArray.size.toLong())
                }
            }

            function("decode", "text", pure = true) {
                deprecated(newName = "text.from_bytes")
                body { a ->
                    val ba = a.asByteArray()
                    Rt_TextValue.get(String(ba))
                }
            }

            function("to_list", "list<integer>", pure = true) {
                alias("toList", C_MessageType.ERROR)
                comment("Converts the byte_array to a list of integers.")
                body { a ->
                    val ba = a.asByteArray()
                    val list = MutableList<Rt_Value>(ba.size) { Rt_IntValue.get(ba[it].toLong() and 0xFF) }
                    Rt_ListValue(LIST_OF_INTEGER, list)
                }
            }

            function("repeat", "byte_array", pure = true) {
                comment("Repeats the byte_array 'n' times.")
                param("n", "integer", comment = "The number of times to repeat the byte_array.")
                body { a, b ->
                    val bs = a.asByteArray()
                    val n = b.asInteger()
                    val s = bs.size
                    val total = Lib_Type_List.rtCheckRepeatArgs(s, n, "byte_array")
                    if (bs.isEmpty() || n == 1L) a else {
                        val res = ByteArray(total) { bs[it % s] }
                        Rt_ByteArrayValue.get(res)
                    }
                }
            }

            function("reversed", "byte_array", pure = true) {
                comment("Returns a reversed copy of the byte_array.")
                body { a ->
                    val bs = a.asByteArray()
                    if (bs.size <= 1) a else {
                        val n = bs.size
                        val res = ByteArray(n) { bs[n - 1 - it] }
                        Rt_ByteArrayValue.get(res)
                    }
                }
            }

            function("sub", "byte_array", pure = true) {
                comment("Returns a sub-array of the byte_array from the specified start index.")
                param("start", "integer", comment = "The start index of the sub-array.")
                dbFunctionTemplate("byte_array.sub/1", 2, "${SqlConstants.FN_BYTEA_SUBSTR1}(#0, (#1)::INT)")
                body { a, b ->
                    val ba = a.asByteArray()
                    val start = b.asInteger()
                    calcSub(ba, start, ba.size.toLong())
                }
            }

            function("sub", "byte_array", pure = true) {
                comment("Returns a sub-array of the byte_array from the specified start index to the end index.")
                param("start", "integer", comment = "The start index of the sub-array.")
                param("end", "integer", comment = "The end index of the sub-array.")
                dbFunctionTemplate("byte_array.sub/2", 3, "${SqlConstants.FN_BYTEA_SUBSTR2}(#0, (#1)::INT, (#2)::INT)")
                body { a, b, c ->
                    val ba = a.asByteArray()
                    val start = b.asInteger()
                    val end = c.asInteger()
                    calcSub(ba, start, end)
                }
            }

            function("to_hex", "text", pure = true) {
                comment("Returns a hexadecimal representation of the byte_array.")
                dbFunctionTemplate("byte_array.to_hex", 1, "ENCODE(#0, 'HEX')")
                body { a ->
                    val ba = a.asByteArray()
                    val r = CommonUtils.bytesToHex(ba)
                    Rt_TextValue.get(r)
                }
            }

            function("to_base64", "text", pure = true) {
                comment("Returns a Base64 representation of the byte_array.")
                dbFunctionTemplate("byte_array.to_base64", 1, "ENCODE(#0, 'BASE64')")
                body { a ->
                    val ba = a.asByteArray()
                    val r = Base64.getEncoder().encodeToString(ba)
                    Rt_TextValue.get(r)
                }
            }

            function("sha256", "byte_array") {
                comment("Returns the SHA256 digest of the byte_array.")
                bodyRaw(Lib_Crypto.Sha256)
            }
        }
    }

    private fun calcSub(obj: ByteArray, start: Long, end: Long): Rt_Value {
        val len = obj.size
        if (start < 0 || start > len || end < start || end > len) {
            throw Rt_Exception.common("fn:byte_array.sub:range:$len:$start:$end",
                "Invalid range: start = $start, end = $end (length $len)")
        }
        val r = obj.copyOfRange(start.toInt(), end.toInt())
        return Rt_ByteArrayValue.get(r)
    }
}

object R_ByteArrayType: R_PrimitiveType("byte_array") {
    override fun defaultValue() = Rt_ByteArrayValue.get(byteArrayOf())
    override fun comparator() = Rt_Comparator({ it.asByteArray() }, { x, y -> Arrays.compareUnsigned(x, y) })
    override fun fromCli(s: String): Rt_Value = Rt_ByteArrayValue.get(CommonUtils.hexToBytes(s))

    override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_ByteArray
    override fun createSqlAdapter(): R_TypeSqlAdapter = R_TypeSqlAdapter_ByteArray

    override fun getLibTypeDef() = Lib_Rell.BYTE_ARRAY_TYPE

    private object R_TypeSqlAdapter_ByteArray: R_TypeSqlAdapter_Primitive("byte_array", PostgresDataType.BYTEA) {
        override fun toSqlValue(value: Rt_Value) = value.asByteArray()
        override fun toSql(stmt: PreparedStatement, idx: Int, value: Rt_Value) = stmt.setBytes(idx, value.asByteArray())

        override fun fromSql(rs: ResultSet, idx: Int, nullable: Boolean): Rt_Value {
            val v = rs.getBytes(idx)
            return checkSqlNull(v, R_ByteArrayType, nullable) ?: Rt_ByteArrayValue.get(v)
        }
    }
}

class Rt_ByteArrayValue private constructor(private val value: ByteArray): Rt_Value() {
    override val valueType = Rt_CoreValueTypes.BYTE_ARRAY.type()

    override fun type() = R_ByteArrayType
    override fun asByteArray() = value
    override fun toFormatArg() = str()
    override fun strCode(showTupleFieldNames: Boolean) = "byte_array[${CommonUtils.bytesToHex(value)}]"
    override fun str(format: StrFormat) = "0x" + CommonUtils.bytesToHex(value)
    override fun equals(other: Any?) = other === this || (other is Rt_ByteArrayValue && value.contentEquals(other.value))
    override fun hashCode() = value.contentHashCode()

    override fun asIterable(): Iterable<Rt_Value> {
        return Iterables.transform(value.asIterable()) {
            val signed = it!!.toInt()
            val unsigned = if (signed >= 0) signed else (signed + 256)
            Rt_IntValue.get(unsigned.toLong())
        }
    }

    companion object {
        val EMPTY: Rt_Value = Rt_ByteArrayValue(ByteArray(0))

        fun get(value: ByteArray): Rt_Value {
            return if (value.isEmpty()) EMPTY else Rt_ByteArrayValue(value)
        }
    }
}

private object GtvRtConversion_ByteArray: GtvRtConversion() {
    override fun directCompatibility() = R_GtvCompatibility(true, true)
    override fun rtToGtv(rt: Rt_Value, pretty: Boolean) = GtvByteArray(rt.asByteArray())

    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val v = GtvRtUtils.gtvToByteArray(ctx, gtv, R_ByteArrayType)
        return ctx.rtValue {
            Rt_ByteArrayValue.get(v)
        }
    }
}
