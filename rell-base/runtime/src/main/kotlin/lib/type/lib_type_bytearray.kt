/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvByteArray
import net.postchain.rell.base.compiler.base.lib.C_SysFunctionBody
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.lib.Lib_Crypto
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.GtvCompatibility
import net.postchain.rell.base.model.expr.Db_SysFunction
import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.sql.PreparedStatementParams
import net.postchain.rell.base.sql.ResultSetRow
import net.postchain.rell.base.sql.SqlConstants
import net.postchain.rell.base.utils.CommonUtils
import net.postchain.rell.base.utils.immSetOf
import org.jooq.impl.SQLDataType
import java.util.*
import kotlin.reflect.full.createType

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
            if (b !in 0..255) throw Rt_Exception.common("fn:byte_array.from_list:$b", "Byte value out of range: $b")
            r[i] = b.toByte()
        }
        Rt_ByteArrayValue.get(r)
    }

    private const val SINCE0 = "0.6.0"

    private val LIST_OF_INTEGER: Rt_Type = rtListType(Rt_PrimitiveTypes.INTEGER)

    val NAMESPACE = Ld_NamespaceDsl.make {
        alias("pubkey", "byte_array", since = SINCE0)

        type("byte_array", since = SINCE0) {
            rrType(RR_Type.Primitive(RR_PrimitiveKind.BYTE_ARRAY))
            comment("An array of bytes. This type is immutable.")
            parent(type = "iterable<integer>")

            constructor(since = SINCE0) {
                comment("Construct a byte array from a hexadecimal string.")
                param("hex", type = "text", comment = "the hexadecimal string")
                bodyRaw(FromHex)
            }

            constructor(since = SINCE0) {
                comment("Construct a byte_array from a list of integers.")
                deprecated(newName = "byte_array.from_list")
                param("list", type = "list<integer>", comment = "the list of integers")
                bodyRaw(FromList)
            }

            staticFunction("from_list", result = "byte_array", since = "0.9.0") {
                comment("""
                    Create a `byte_array` from a list of integers.

                    The inverse of `byte_array.to_list()`.

                    All integers in the passed list are treated as single-byte values, i.e. they must be in the range
                    `0 <= x < 256`, and therefore the returned `byte_array` will be equal in size to the passed list.
                    @throws exception if any element in the list is less than zero or greater than 255
                """)
                param("list", type = "list<integer>", comment = "the list of integers")
                bodyRaw(FromList)
            }

            staticFunction("from_hex", result = "byte_array", since = "0.9.0") {
                comment("""
                    Create a byte array from a hexadecimal string.

                    The given hexadecimal string must have even length, since two hexadecimal characters encode one
                    byte.
                    @throws exception if `value` has odd length or contains invalid characters
                """)
                param("value", type = "text", comment = "the hexadecimal string")
                bodyRaw(FromHex)
            }

            staticFunction("from_base64", result = "byte_array", since = "0.9.0") {
                comment("""
                    Create a byte array from a base-64 string.

                    Valid base-64 strings may include the characters `a-z`, `A-Z`, `0-9`, `+` and `/` as significant
                    characters, and '=' as padding.
                    @throws exception if `value` contains invalid characters
                """)
                param("value", type = "text", comment = "the base-64 string")
                body { value ->
                    val s = value.asString()
                    val bytes = Rt_Utils.wrapErr("fn:byte_array.from_base64") {
                        Base64.getDecoder().decode(s)
                    }
                    Rt_ByteArrayValue.get(bytes)
                }
            }

            function("empty", "boolean", pure = true, since = SINCE0) {
                comment("""
                    Returns `true` if this `byte_array` is empty, `false` otherwise.

                    `x.empty()` is equivalent to `x.size() == 0`.
                """)
                dbFunctionTemplate("byte_array.empty", 1, "(LENGTH(#0) = 0)")
                body { array ->
                    val byteArray = array.asByteArray()
                    Rt_BooleanValue.get(byteArray.isEmpty())
                }
            }

            function("size", "integer", pure = true, since = SINCE0) {
                comment("Returns the number of bytes in this `byte_array`.")
                alias("len", C_MessageType.ERROR, since = SINCE0)
                dbFunctionTemplate("byte_array.size", 1, "LENGTH(#0)")
                body { array ->
                    val byteArray = array.asByteArray()
                    Rt_IntValue.get(byteArray.size.toLong())
                }
            }

            function("decode", "text", pure = true, since = SINCE0) {
                deprecated(newName = "text.from_bytes")
                body { a ->
                    val ba = a.asByteArray()
                    Rt_TextValue.get(String(ba))
                }
            }

            function("to_list", "list<integer>", pure = true, since = "0.9.0") {
                alias("toList", C_MessageType.ERROR, since = SINCE0)
                comment("""
                    Converts this `byte_array` to a list of integers.

                    The inverse of `byte_array.from_list(list<integer>)`.

                    Each byte in the array is converted to a single integer `0 <= x < 256` in the returned list.
                """)
                body { a ->
                    val ba = a.asByteArray()
                    val list = MutableList(ba.size) { Rt_IntValue.get(ba[it].toLong() and 0xFF) }
                    Rt_ListValue(LIST_OF_INTEGER, list)
                }
            }

            function("repeat", "byte_array", pure = true, since = "0.11.0") {
                comment("""
                    Repeats this byte_array `n` times.

                    Examples:
                    - `x'1234abcd'.repeat(3)` returns `x'1234abcd1234abcd1234abcd'`
                    - `x''.repeat(3)` returns `x''`
                    - `x'1234abcd'.repeat(0)` returns `x''`

                    @throws exception when:
                    - `n` is negative
                    - `n` is greater than `(2^31)-1`
                    - the resulting byte array has size greater than `(2^31)-1`
                """)
                param("n", "integer", comment = "the number of times to repeat this byte_array")
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

            function("reversed", "byte_array", pure = true, since = "0.11.0") {
                comment("Returns a reversed copy of this `byte_array`.")
                body { a ->
                    val bs = a.asByteArray()
                    if (bs.size <= 1) a else {
                        val n = bs.size
                        val res = ByteArray(n) { bs[n - 1 - it] }
                        Rt_ByteArrayValue.get(res)
                    }
                }
            }

            function("sub", "byte_array", pure = true, since = SINCE0) {
                comment("""
                    Returns a sub-array of this byte array starting from the specified index (inclusive).
                    @throws exception if the `start` index is out of range
                """)
                param("start", "integer", comment = "the start index of the sub-array")
                dbFunctionTemplate("byte_array.sub/1", 2, "${SqlConstants.FN_BYTEA_SUBSTR1}(#0, (#1)::INT)")
                body { a, b ->
                    val ba = a.asByteArray()
                    val start = b.asInteger()
                    calcSub(ba, start, ba.size.toLong())
                }
            }

            function("sub", "byte_array", pure = true, since = SINCE0) {
                comment("""
                    Returns a sub-array of this byte array from the specified start index (inclusive)
                    to the specified end index (exclusive).
                    @throws exception when:
                    - the `start` or `end` indexes are out of range
                    - the `start` index is greater than the `end` index
                """)
                param("start", "integer", comment = "the start index of the sub-array")
                param("end", "integer", comment = "the end index of the sub-array")
                dbFunctionTemplate("byte_array.sub/2", 3, "${SqlConstants.FN_BYTEA_SUBSTR2}(#0, (#1)::INT, (#2)::INT)")
                body { a, b, c ->
                    val ba = a.asByteArray()
                    val start = b.asInteger()
                    val end = c.asInteger()
                    calcSub(ba, start, end)
                }
            }

            function("to_hex", "text", pure = true, since = "0.9.0") {
                comment("""
                    Returns a hexadecimal `text` representation of this `byte_array`.

                    Inverse of `byte_array.from_hex(text)`.
                """)
                dbFunctionTemplate("byte_array.to_hex", 1, "ENCODE(#0, 'HEX')")
                body { a ->
                    val ba = a.asByteArray()
                    val r = CommonUtils.bytesToHex(ba)
                    Rt_TextValue.get(r)
                }
            }

            function("to_base64", "text", pure = true, since = "0.9.0") {
                comment("""
                    Returns a base-64 `text` representation of this `byte_array`.

                    Inverse of `byte_array.from_base64(text)`.
                """)
                dbFunctionTemplate("byte_array.to_base64", 1, "ENCODE(#0, 'BASE64')")
                body { a ->
                    val ba = a.asByteArray()
                    val r = Base64.getEncoder().encodeToString(ba)
                    Rt_TextValue.get(r)
                }
            }

            function("sha256", "byte_array", since = "0.10.0") {
                comment("""
                    Calculates the SHA-256 digest (hash) of this byte array.
                    @return a SHA-256 digest as a byte array of length 32
                """)
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

object Rt_NativeConversion_ByteArray: Rt_TypeNativeConversion {
    override val nativeTypes = immSetOf(ByteArray::class.createType())
    override fun rtToNative(value: Rt_Value) = value.asByteArray().copyOf()
    override fun nativeToRt(value: Any?) = Rt_ByteArrayValue.get((value as ByteArray).copyOf())
}

object R_TypeSqlAdapter_ByteArray: R_TypeSqlAdapter_Primitive("byte_array", SQLDataType.BLOB) {
    override fun toSqlValue(value: Rt_Value) = value.asByteArray()

    override fun toSql(params: PreparedStatementParams, idx: Int, value: Rt_Value) {
        params.setBytes(idx, value.asByteArray())
    }

    override fun fromSql(row: ResultSetRow, idx: Int, nullable: Boolean): Rt_Value {
        val v = row.getBytes(idx)
        return if (v != null) Rt_ByteArrayValue.get(v) else checkSqlNull(name, nullable)
    }
}

class Rt_ByteArrayValue private constructor(private val value: ByteArray): Rt_Value() {
    override val valueType = Rt_CoreValueTypes.BYTE_ARRAY.type()

    override fun type() = Rt_PrimitiveTypes.BYTE_ARRAY
    override fun asByteArray() = value
    override fun strCode(showTupleFieldNames: Boolean) = "byte_array[${CommonUtils.bytesToHex(value)}]"
    override fun str(format: StrFormat) = "0x" + CommonUtils.bytesToHex(value)
    override fun equals(other: Any?) = other === this || (other is Rt_ByteArrayValue && value.contentEquals(other.value))
    override fun hashCode() = value.contentHashCode()

    override fun asIterable(): Iterable<Rt_Value> {
        return value.map {
            val signed = it.toInt()
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

object GtvRtConversion_ByteArray: GtvRtConversion {
    override val directCompatibility = GtvCompatibility(fromGtv = true, toGtv = true)
    override fun rtToGtv(rt: Rt_Value, pretty: Boolean) = GtvByteArray(rt.asByteArray())

    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val v = GtvRtUtils.gtvToByteArray(ctx, gtv, "byte_array")
        return Rt_ByteArrayValue.get(v)
    }
}
