/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvString
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lmodel.L_ParamArity
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_GtvCompatibility
import net.postchain.rell.base.model.R_PrimitiveType
import net.postchain.rell.base.model.R_TypeSqlAdapter
import net.postchain.rell.base.model.R_TypeSqlAdapter_Primitive
import net.postchain.rell.base.model.expr.Db_SysFunction
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_Comparator
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.sql.SqlConstants
import org.jooq.util.postgres.PostgresDataType
import java.nio.ByteBuffer
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.*
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

object Lib_Type_Text {
    val DB_SUBSCRIPT: Db_SysFunction =
        Db_SysFunction.template("text.[]", 2, "${SqlConstants.FN_TEXT_GETCHAR}(#0, (#1)::INT)")

    private val CHARSET = Charsets.UTF_8
    private val SPLIT_TYPE = R_ListType(R_TextType)

    val NAMESPACE = Ld_NamespaceDsl.make {
        alias("name", "text")
        alias("tuid", "text")

        type("text", rType = R_TextType) {

            staticFunction("from_bytes", result = "text", pure = true) {
                param("bytes", type = "byte_array")
                param("ignore_errors", type = "boolean", arity = L_ParamArity.ZERO_ONE)
                bodyOpt1 { a, b ->
                    val ignoreErr = b?.asBoolean() ?: false
                    val bytes = a.asByteArray()
                    val s = if (ignoreErr) {
                        String(bytes, CHARSET)
                    } else {
                        val decoder = CHARSET.newDecoder()
                        val byteBuffer = ByteBuffer.wrap(bytes)
                        val charBuffer = Rt_Utils.wrapErr("fn:text.from_bytes") {
                            decoder.decode(byteBuffer)
                        }
                        charBuffer.toString()
                    }
                    Rt_TextValue.get(s)
                }
            }

            function("empty", result = "boolean", pure = true) {
                dbFunctionTemplate("text.empty", 1, "(LENGTH(#0) = 0)")
                body { a ->
                    val s = a.asString()
                    Rt_BooleanValue.get(s.isEmpty())
                }
            }

            function("size", result = "integer", pure = true) {
                alias("len", C_MessageType.ERROR)
                dbFunctionSimple("text.size", "LENGTH")
                body { a ->
                    val s = a.asString()
                    Rt_IntValue.get(s.length.toLong())
                }
            }

            function("upper_case", result = "text", pure = true) {
                alias("upperCase", C_MessageType.ERROR)
                dbFunctionSimple("text.upper_case", "UPPER")
                body { a ->
                    val s = a.asString()
                    Rt_TextValue.get(s.toUpperCase())
                }
            }

            function("lower_case", result = "text", pure = true) {
                alias("lowerCase", C_MessageType.ERROR)
                dbFunctionSimple("text.lower_case", "LOWER")
                body { a ->
                    val s = a.asString()
                    Rt_TextValue.get(s.toLowerCase())
                }
            }

            function("compare_to", result = "integer", pure = true) {
                alias("compareTo", C_MessageType.ERROR)
                param("other", type = "text")
                body { a, b ->
                    val s1 = a.asString()
                    val s2 = b.asString()
                    Rt_IntValue.get(s1.compareTo(s2).toLong())
                }
            }

            function("contains", result = "boolean", pure = true) {
                param("text", type = "text")
                dbFunctionTemplate("text.contains", 2, "(STRPOS(#0, #1) > 0)")
                body { a, b ->
                    val s1 = a.asString()
                    val s2 = b.asString()
                    Rt_BooleanValue.get(s1.contains(s2))
                }
            }

            function("starts_with", result = "boolean", pure = true) {
                alias("startsWith", C_MessageType.ERROR)
                param("prefix", type = "text")
                dbFunctionTemplate("text.starts_with", 2, "(LEFT(#0, LENGTH(#1)) = #1)")
                body { a, b ->
                    val s1 = a.asString()
                    val s2 = b.asString()
                    Rt_BooleanValue.get(s1.startsWith(s2))
                }
            }

            function("ends_with", result = "boolean", pure = true) {
                alias("endsWith", C_MessageType.ERROR)
                param("suffix", type = "text")
                dbFunctionTemplate("text.ends_with", 2, "(RIGHT(#0, LENGTH(#1)) = #1)")
                body { a, b ->
                    val s1 = a.asString()
                    val s2 = b.asString()
                    Rt_BooleanValue.get(s1.endsWith(s2))
                }
            }

            function("format", result = "text", pure = true) {
                param("args", type = "anything", arity = L_ParamArity.ZERO_MANY)
                bodyN { args ->
                    Rt_Utils.check(args.isNotEmpty()) { "fn:text.format:no_args" toCodeMsg "No arguments" }
                    val s = args[0].asString()
                    val anys = args.drop(1).map { it.toFormatArg() }.toTypedArray()
                    val r = try {
                        s.format(Locale.US, *anys)
                    } catch (e: IllegalFormatException) {
                        s
                    }
                    Rt_TextValue.get(r)
                }
            }

            function("replace", result = "text", pure = true) {
                param("old_value", type = "text")
                param("new_value", type = "text")
                dbFunctionTemplate("text.replace", 3, "REPLACE(#0, #1, #2)")
                body { a, b, c ->
                    val s1 = a.asString()
                    val s2 = b.asString()
                    val s3 = c.asString()
                    Rt_TextValue.get(s1.replace(s2, s3))
                }
            }

            function("split", result = "list<text>", pure = true) {
                param("delimiter", type = "text")
                body { a, b ->
                    val s1 = a.asString()
                    val s2 = b.asString()
                    val arr = s1.split(s2)
                    val list = MutableList(arr.size) { Rt_TextValue.get(arr[it]) }
                    Rt_ListValue(SPLIT_TYPE, list)
                }
            }

            function("trim", result = "text", pure = true) {
                //dbFunction(Db_SysFunction.template("text.trim", 1, "TRIM(#0, ' '||CHR(9)||CHR(10)||CHR(13))"))
                body { a ->
                    val s = a.asString()
                    Rt_TextValue.get(s.trim())
                }
            }

            function("like", result = "boolean", pure = true) {
                param("pattern", type = "text")
                dbFunctionTemplate("text.like", 2, "((#0) LIKE (#1))")
                body { a, b ->
                    val s = a.asString()
                    val pattern = b.asString()
                    val res = Rt_TextValue.like(s, pattern)
                    Rt_BooleanValue.get(res)
                }
            }

            function("matches", result = "boolean", pure = true) {
                param("regex", type = "text")
                body { a, b ->
                    val s = a.asString()
                    val pattern = b.asString()
                    val res = try {
                        Pattern.matches(pattern, s)
                    } catch (e: PatternSyntaxException) {
                        throw Rt_Exception.common("fn:text.matches:bad_regex", "Invalid regular expression: $pattern")
                    }
                    Rt_BooleanValue.get(res)
                }
            }

            function("char_at", result = "integer", pure = true) {
                alias("charAt", C_MessageType.ERROR)
                param("index", type = "integer")
                dbFunctionTemplate("text.char_at", 2, "ASCII(${SqlConstants.FN_TEXT_GETCHAR}(#0, (#1)::INT))")
                body { a, b ->
                    val s = a.asString()
                    val index = b.asInteger()
                    if (index < 0 || index >= s.length) {
                        throw Rt_Exception.common(
                            "fn:text.char_at:index:${s.length}:$index",
                            "Index out of bounds: $index (length ${s.length})"
                        )
                    }
                    val r = s[index.toInt()]
                    Rt_IntValue.get(r.toLong())
                }
            }

            function("index_of", result = "integer", pure = true) {
                alias("indexOf", C_MessageType.ERROR)
                param("text", type = "text")
                dbFunctionTemplate("text.index_of", 2, "(STRPOS(#0, #1) - 1)")
                body { a, b ->
                    val s1 = a.asString()
                    val s2 = b.asString()
                    Rt_IntValue.get(s1.indexOf(s2).toLong())
                }
            }

            function("index_of", result = "integer", pure = true) {
                alias("indexOf", C_MessageType.ERROR)
                param("text", type = "text")
                param("start", type = "integer")
                body { a, b, c ->
                    val s1 = a.asString()
                    val s2 = b.asString()
                    val start = c.asInteger()
                    if (start < 0 || start >= s1.length) {
                        throw Rt_Exception.common(
                            "fn:text.index_of:index:${s1.length}:$start",
                            "Index out of bounds: $start (length ${s1.length})"
                        )
                    }
                    Rt_IntValue.get(s1.indexOf(s2, start.toInt()).toLong())
                }
            }

            function("last_index_of", result = "integer", pure = true) {
                alias("lastIndexOf", C_MessageType.ERROR)
                param("text", type = "text")
                body { a, b ->
                    val s1 = a.asString()
                    val s2 = b.asString()
                    Rt_IntValue.get(s1.lastIndexOf(s2).toLong())
                }
            }

            function("last_index_of", result = "integer", pure = true) {
                alias("lastIndexOf", C_MessageType.ERROR)
                param("text", type = "text")
                param("max", type = "integer")
                body { a, b, c ->
                    val s1 = a.asString()
                    val s2 = b.asString()
                    val start = c.asInteger()
                    if (start < 0 || start >= s1.length) {
                        throw Rt_Exception.common(
                            "fn:text.last_index_of:index:${s1.length}:$start",
                            "Index out of bounds: $start (length ${s1.length})"
                        )
                    }
                    Rt_IntValue.get(s1.lastIndexOf(s2, start.toInt()).toLong())
                }
            }

            function("repeat", result = "text", pure = true) {
                param("n", type = "integer")
                dbFunctionTemplate("text.repeat", 2, "${SqlConstants.FN_TEXT_REPEAT}(#0, (#1)::INT)")
                body { a, b ->
                    val s = a.asString()
                    val n = b.asInteger()
                    Lib_Type_List.rtCheckRepeatArgs(s.length, n, "text")
                    if (s.isEmpty() || n == 1L) a else {
                        val res = s.repeat(n.toInt())
                        Rt_TextValue.get(res)
                    }
                }
            }

            function("reversed", result = "text", pure = true) {
                dbFunctionTemplate("text.reversed", 1, "REVERSE(#0)")
                body { a ->
                    val s = a.asString()
                    if (s.length <= 1) a else {
                        val res = s.reversed()
                        Rt_TextValue.get(res)
                    }
                }
            }

            function("sub", result = "text", pure = true) {
                param("start", type = "integer")
                dbFunctionTemplate("text.sub/1", 2, "${SqlConstants.FN_TEXT_SUBSTR1}(#0, (#1)::INT)")
                body { a, b ->
                    val s = a.asString()
                    val start = b.asInteger()
                    calcSub(s, start, s.length.toLong())
                }
            }

            function("sub", result = "text", pure = true) {
                param("start", type = "integer")
                param("end", type = "integer")
                dbFunctionTemplate("text.sub/2", 3, "${SqlConstants.FN_TEXT_SUBSTR2}(#0, (#1)::INT, (#2)::INT)")
                body { a, b, c ->
                    val s = a.asString()
                    val start = b.asInteger()
                    val end = c.asInteger()
                    calcSub(s, start, end)
                }
            }

            function("to_bytes", result = "byte_array", pure = true) {
                alias("encode", C_MessageType.ERROR)
                body { a ->
                    val s = a.asString()
                    val ba = s.toByteArray(CHARSET)
                    Rt_ByteArrayValue.get(ba)
                }
            }
        }
    }

    private fun calcSub(s: String, start: Long, end: Long): Rt_Value {
        val len = s.length
        if (start < 0 || start > len || end < start || end > len) {
            throw Rt_Exception.common(
                "fn:text.sub:range:$len:$start:$end",
                "Invalid range: start = $start, end = $end (length $len)"
            )
        }
        return Rt_TextValue.get(s.substring(start.toInt(), end.toInt()))
    }
}

object R_TextType: R_PrimitiveType("text") {
    override fun defaultValue() = Rt_TextValue.EMPTY
    override fun comparator() = Rt_Comparator.create { it.asString() }
    override fun fromCli(s: String): Rt_Value = Rt_TextValue.get(s)
    override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_Text
    override fun createSqlAdapter(): R_TypeSqlAdapter = R_TypeSqlAdapter_Text

    override fun getLibTypeDef() = Lib_Rell.TEXT_TYPE

    private object R_TypeSqlAdapter_Text: R_TypeSqlAdapter_Primitive("text", PostgresDataType.TEXT) {
        override fun toSqlValue(value: Rt_Value) = value.asString()

        override fun toSql(stmt: PreparedStatement, idx: Int, value: Rt_Value) {
            stmt.setString(idx, value.asString())
        }

        override fun fromSql(rs: ResultSet, idx: Int, nullable: Boolean): Rt_Value {
            val v = rs.getString(idx)
            return checkSqlNull(v, R_TextType, nullable) ?: Rt_TextValue.get(v)
        }
    }
}

class Rt_TextValue private constructor(val value: String): Rt_Value() {
    override val valueType = Rt_CoreValueTypes.TEXT.type()

    override fun type() = R_TextType
    override fun asString() = value
    override fun toFormatArg() = value

    override fun strCode(showTupleFieldNames: Boolean): String {
        val esc = escape(value)
        return "text[$esc]"
    }

    override fun str(): String = value
    override fun equals(other: Any?) = other === this || (other is Rt_TextValue && value == other.value)
    override fun hashCode() = value.hashCode()

    companion object {
        val EMPTY: Rt_Value = Rt_TextValue("")

        fun get(s: String): Rt_Value {
            return if (s.isEmpty()) EMPTY else Rt_TextValue(s)
        }

        fun like(s: String, pattern: String): Boolean {
            val regex = likePatternToRegex(pattern, '_', '%')
            val m = regex.matcher(s)
            return m.matches()
        }

        fun likePatternToRegex(pattern: String, one: Char, many: Char): Pattern {
            val buf = StringBuilder()
            val raw = StringBuilder()
            var esc = false

            for (c in pattern) {
                if (esc) {
                    raw.append(c)
                    esc = false
                } else if (c == '\\') {
                    esc = true
                } else if (c == one || c == many) {
                    if (raw.isNotEmpty()) buf.append(Pattern.quote(raw.toString()))
                    raw.setLength(0)
                    buf.append(if (c == many) ".*" else ".")
                } else {
                    raw.append(c)
                }
            }

            if (raw.isNotEmpty()) buf.append(Pattern.quote(raw.toString()))
            val s = buf.toString()
            return Pattern.compile(s, Pattern.DOTALL)
        }

        private fun escape(s: String): String {
            if (s.isEmpty()) return ""

            val buf = StringBuilder(s.length)
            for (c in s) {
                if (c == '\t') {
                    buf.append("\\t")
                } else if (c == '\r') {
                    buf.append("\\r")
                } else if (c == '\n') {
                    buf.append("\\n")
                } else if (c == '\b') {
                    buf.append("\\b")
                } else if (c == '\\') {
                    buf.append("\\\\")
                } else if (c >= '\u0020' && c < '\u0080') {
                    buf.append(c)
                } else {
                    buf.append("\\u")
                    buf.append(String.format("%04x", c.toInt()))
                }
            }

            return buf.toString()
        }
    }
}

private object GtvRtConversion_Text: GtvRtConversion() {
    override fun directCompatibility() = R_GtvCompatibility(true, true)
    override fun rtToGtv(rt: Rt_Value, pretty: Boolean) = GtvString(rt.asString())

    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val s = GtvRtUtils.gtvToString(ctx, gtv, R_TextType)
        return ctx.rtValue {
            Rt_TextValue.get(s)
        }
    }
}
