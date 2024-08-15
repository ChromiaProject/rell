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

    private const val SINCE0 = "0.6.0"

    val NAMESPACE = Ld_NamespaceDsl.make {
        alias("name", "text", since = SINCE0)
        alias("tuid", "text", since = SINCE0)

        type("text", rType = R_TextType, since = SINCE0) {
            comment("An immutable data type for representing character strings.")

            staticFunction("from_bytes", result = "text", pure = true, since = "0.9.0") {
                comment("Creates text from bytes, optionally handling invalid UTF-8 encoding.")
                param("bytes", type = "byte_array", comment = "The byte array to convert to text.")
                param("ignore_errors", type = "boolean", arity = L_ParamArity.ZERO_ONE) {
                    comment("""
                        If true, replaces invalid characters with a placeholder;
                        if false, throws an exception on encountering invalid bytes.
                    """)
                }
                bodyOpt1 { bytes, ignoreErrors ->
                    val ignoreErr = ignoreErrors?.asBoolean() ?: false
                    val byteArray = bytes.asByteArray()
                    val s = if (ignoreErr) {
                        String(byteArray, CHARSET)
                    } else {
                        val decoder = CHARSET.newDecoder()
                        val byteBuffer = ByteBuffer.wrap(byteArray)
                        val charBuffer = Rt_Utils.wrapErr("fn:text.from_bytes") {
                            decoder.decode(byteBuffer)
                        }
                        charBuffer.toString()
                    }
                    Rt_TextValue.get(s)
                }
            }

            function("empty", result = "boolean", pure = true, since = SINCE0) {
                comment("Returns true if the text is empty, otherwise returns false.")
                dbFunctionTemplate("text.empty", 1, "(LENGTH(#0) = 0)")
                body { text ->
                    val s = text.asString()
                    Rt_BooleanValue.get(s.isEmpty())
                }
            }

            function("size", result = "integer", pure = true, since = SINCE0) {
                alias("len", C_MessageType.ERROR, since = SINCE0)
                dbFunctionSimple("text.size", "LENGTH")
                comment("Returns the number of characters in the text.")
                body { text ->
                    val s = text.asString()
                    Rt_IntValue.get(s.length.toLong())
                }
            }

            function("upper_case", result = "text", pure = true, since = "0.9.0") {
                alias("upperCase", C_MessageType.ERROR, since = SINCE0)
                dbFunctionSimple("text.upper_case", "UPPER")
                comment("Returns a new text with all characters converted to uppercase.")
                body { text ->
                    val s = text.asString()
                    Rt_TextValue.get(s.toUpperCase())
                }
            }

            function("lower_case", result = "text", pure = true, since = "0.9.0") {
                alias("lowerCase", C_MessageType.ERROR, since = SINCE0)
                dbFunctionSimple("text.lower_case", "LOWER")
                comment("Returns a new text with all characters converted to lowercase.")
                body { text ->
                    val s = text.asString()
                    Rt_TextValue.get(s.toLowerCase())
                }
            }

            function("compare_to", result = "integer", pure = true, since = "0.9.0") {
                alias("compareTo", C_MessageType.ERROR, since = SINCE0)
                comment("""
                    Compares this text to another text.
                    @return the value 0 if equal, a negative number if less than `other`,
                    or a value greater than 0 if greater than `other`.
                """)
                param("other", type = "text", comment = "The text to compare to.")
                body { text, other ->
                    val s1 = text.asString()
                    val s2 = other.asString()
                    Rt_IntValue.get(s1.compareTo(s2).toLong())
                }
            }

            function("contains", result = "boolean", pure = true, since = SINCE0) {
                comment("Returns true if this text contains the specified substring, otherwise returns false.")
                param("text", type = "text", comment = "The substring to search for.")
                dbFunctionTemplate("text.contains", 2, "(STRPOS(#0, #1) > 0)")
                body { text, substring ->
                    val s1 = text.asString()
                    val s2 = substring.asString()
                    Rt_BooleanValue.get(s1.contains(s2))
                }
            }

            function("starts_with", result = "boolean", pure = true, since = "0.9.0") {
                alias("startsWith", C_MessageType.ERROR, since = SINCE0)
                comment("Returns true if this text starts with the specified prefix, otherwise returns false.")
                param("prefix", type = "text", comment = "The prefix to check.")
                dbFunctionTemplate("text.starts_with", 2, "(LEFT(#0, LENGTH(#1)) = #1)")
                body { text, prefix ->
                    val s1 = text.asString()
                    val s2 = prefix.asString()
                    Rt_BooleanValue.get(s1.startsWith(s2))
                }
            }

            function("ends_with", result = "boolean", pure = true, since = "0.9.0") {
                alias("endsWith", C_MessageType.ERROR, since = SINCE0)
                comment("Returns true if this text ends with the specified suffix, otherwise returns false.")
                param("suffix", type = "text", comment = "The suffix to check.")
                dbFunctionTemplate("text.ends_with", 2, "(RIGHT(#0, LENGTH(#1)) = #1)")
                body { text, suffix ->
                    val s1 = text.asString()
                    val s2 = suffix.asString()
                    Rt_BooleanValue.get(s1.endsWith(s2))
                }
            }

            function("format", result = "text", pure = true, since = SINCE0) {
                comment("""
                    Uses this string as a format string and returns a string obtained
                    by substituting the specified arguments. Uses `java.lang.String.format` internally.
                """)
                param("args", type = "anything", arity = L_ParamArity.ZERO_MANY) {
                    comment("""
                        Arguments referenced by the format specifiers in the format string.
                        If there are more arguments than format specifiers, the extra arguments are ignored.
                        The number of arguments is variable and may be zero.
                   """)
                }
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

            function("replace", result = "text", pure = true, since = SINCE0) {
                comment("""
                    Returns a new text resulting from replacing all occurrences of old text in this text with new text.
                """)
                param("old_value", type = "text", comment = "The substring to be replaced.")
                param("new_value", type = "text", comment = "The replacement substring.")
                dbFunctionTemplate("text.replace", 3, "REPLACE(#0, #1, #2)")
                body { text, old, new ->
                    val s1 = text.asString()
                    val s2 = old.asString()
                    val s3 = new.asString()
                    Rt_TextValue.get(s1.replace(s2, s3))
                }
            }

            function("split", result = "list<text>", pure = true, since = SINCE0) {
                comment("Splits this text around matches of the given delimiter.")
                param("delimiter", type = "text", comment = "The delimiter to split the text by.")
                body { text, delimiter ->
                    val s1 = text.asString()
                    val s2 = delimiter.asString()
                    val arr = s1.split(s2)
                    val list = MutableList(arr.size) { Rt_TextValue.get(arr[it]) }
                    Rt_ListValue(SPLIT_TYPE, list)
                }
            }

            function("trim", result = "text", pure = true, since = SINCE0) {
                comment("Returns a new text with leading and trailing whitespace removed.")
                //dbFunction(Db_SysFunction.template("text.trim", 1, "TRIM(#0, ' '||CHR(9)||CHR(10)||CHR(13))"))
                body { text ->
                    val s = text.asString()
                    Rt_TextValue.get(s.trim())
                }
            }

            function("like", result = "boolean", pure = true, since = "0.10.4") {
                comment("""
                    Returns true if this text matches the specified pattern using SQL LIKE syntax.

                    Example:

                    ```rell
                    val names = ["Alice", "Victor", "Viktor", "Victoria"];
                    print(names @* { .like("Vi_tor") });  // prints [Victor, Viktor]
                    print(names @* { .like("Vic%") }); // prints [Victor, Victoria]
                    ```
                """)
                param("pattern", type = "text") {
                    comment("""
                        The pattern to match against. `_` means any character and `%` means any number of caracters
                    """)
                }
                dbFunctionTemplate("text.like", 2, "((#0) LIKE (#1))")
                body { text, pattern ->
                    val s = text.asString()
                    val p = pattern.asString()
                    val res = Rt_TextValue.like(s, p)
                    Rt_BooleanValue.get(res)
                }
            }

            function("matches", result = "boolean", pure = true, since = SINCE0) {
                comment("""
                    Returns true if this text matches the given regular expression.
                    Uses `java.util.regex.Pattern` internally.
                """)
                param("regex", type = "text", comment = "The regular expression to match against.")
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

            function("char_at", result = "integer", pure = true, since = "0.9.0") {
                alias("charAt", C_MessageType.ERROR, since = SINCE0)
                comment("Get a 16-bit code of a character at the specified index.")
                param("index", type = "integer", comment = "The index of the character.")
                dbFunctionTemplate("text.char_at", 2, "ASCII(${SqlConstants.FN_TEXT_GETCHAR}(#0, (#1)::INT))")
                body { text, index ->
                    val s = text.asString()
                    val i = index.asInteger()
                    if (i < 0 || i >= s.length) {
                        throw Rt_Exception.common(
                            "fn:text.char_at:index:${s.length}:$i",
                            "Index out of bounds: $i (length ${s.length})"
                        )
                    }
                    val c = s[i.toInt()]
                    Rt_IntValue.get(c.toLong())
                }
            }

            function("index_of", result = "integer", pure = true, since = "0.9.0") {
                alias("indexOf", C_MessageType.ERROR, since = SINCE0)
                comment("""
                    Returns the position of the first occurrence of the specified substring within this text,
                    or -1 if the text is not found.
                """)
                param("text", type = "text", comment = "The substring to search for.")
                dbFunctionTemplate("text.index_of", 2, "(STRPOS(#0, #1) - 1)")
                body { text, substring ->
                    val s1 = text.asString()
                    val s2 = substring.asString()
                    Rt_IntValue.get(s1.indexOf(s2).toLong())
                }
            }

            function("index_of", result = "integer", pure = true, since = "0.9.0") {
                comment("""
                    Returns the position of the first occurrence of the specified substring within this text,
                    starting at the specified index, or -1 if the text is not found.
                """)
                alias("indexOf", C_MessageType.ERROR, since = SINCE0)
                param("text", type = "text", comment = "The substring to search for.")
                param("start", type = "integer", comment = "The index to start the search from.")
                body { text, substring, start ->
                    val s1 = text.asString()
                    val s2 = substring.asString()
                    val startIndex = start.asInteger()
                    if (startIndex < 0 || startIndex >= s1.length) {
                        throw Rt_Exception.common(
                            "fn:text.index_of:index:${s1.length}:$startIndex",
                            "Index out of bounds: $startIndex (length ${s1.length})"
                        )
                    }
                    Rt_IntValue.get(s1.indexOf(s2, startIndex.toInt()).toLong())
                }
            }


            function("last_index_of", result = "integer", pure = true, since = "0.9.0") {
                alias("lastIndexOf", C_MessageType.ERROR, since = SINCE0)
                comment("""
                    Returns the index within this text of the last occurrence of the specified string,
                    or -1 if not found.
                """)
                param("text", type = "text", comment = "The substring to search for.")
                body { text, substring ->
                    val s1 = text.asString()
                    val s2 = substring.asString()
                    Rt_IntValue.get(s1.lastIndexOf(s2).toLong())
                }
            }

            function("last_index_of", result = "integer", pure = true, since = "0.9.0") {
                comment("""
                    Returns the index within this text of the last occurrence of the specified string,
                    starting from the specified startIndex, or -1 if not found.
                """)
                alias("lastIndexOf", C_MessageType.ERROR, since = SINCE0)
                param("text", type = "text", comment = "The substring to search for.")
                param("max", type = "integer", comment = "The index to search before.")
                body { text, substring, max ->
                    val s1 = text.asString()
                    val s2 = substring.asString()
                    val maxIndex = max.asInteger()
                    if (maxIndex < 0 || maxIndex >= s1.length) {
                        throw Rt_Exception.common(
                            "fn:text.last_index_of:index:${s1.length}:$maxIndex",
                            "Index out of bounds: $maxIndex (length ${s1.length})"
                        )
                    }
                    Rt_IntValue.get(s1.lastIndexOf(s2, maxIndex.toInt()).toLong())
                }
            }

            function("repeat", result = "text", pure = true, since = "0.11.0") {
                comment("Returns the text repeated `n` times. SQL compatible.")
                param("n", type = "integer", comment = "The number of times to repeat the text.")
                dbFunctionTemplate("text.repeat", 2, "${SqlConstants.FN_TEXT_REPEAT}(#0, (#1)::INT)")
                body { text, n ->
                    val s = text.asString()
                    val repeatCount = n.asInteger()
                    Lib_Type_List.rtCheckRepeatArgs(s.length, repeatCount, "text")
                    if (s.isEmpty() || repeatCount == 1L) text else {
                        val res = s.repeat(repeatCount.toInt())
                        Rt_TextValue.get(res)
                    }
                }
            }

            function("reversed", result = "text", pure = true, since = "0.11.0") {
                comment("Returns a reversed copy of the text. SQL compatible.")
                dbFunctionTemplate("text.reversed", 1, "REVERSE(#0)")
                body { text ->
                    val s = text.asString()
                    if (s.length <= 1) text else {
                        val res = s.reversed()
                        Rt_TextValue.get(res)
                    }
                }
            }

            function("sub", result = "text", pure = true, since = SINCE0) {
                comment("Returns a substring of this text starting from the specified index.")
                param("start", type = "integer", comment = "The starting index of the substring.")
                dbFunctionTemplate("text.sub/1", 2, "${SqlConstants.FN_TEXT_SUBSTR1}(#0, (#1)::INT)")
                body { text, start ->
                    val s = text.asString()
                    val startIndex = start.asInteger()
                    calcSub(s, startIndex, s.length.toLong())
                }
            }

            function("sub", result = "text", pure = true, since = SINCE0) {
                comment("""
                    Returns a substring of this text from the specified start index (inclusive)
                    to the specified end index (exclusive).
                """)
                param("start", type = "integer", comment = "The start index of the substring.")
                param("end", type = "integer", comment = "The end index of the substring.")
                dbFunctionTemplate("text.sub/2", 3, "${SqlConstants.FN_TEXT_SUBSTR2}(#0, (#1)::INT, (#2)::INT)")
                body { text, start, end ->
                    val s = text.asString()
                    val startIndex = start.asInteger()
                    val endIndex = end.asInteger()
                    calcSub(s, startIndex, endIndex)
                }
            }

            function("to_bytes", result = "byte_array", pure = true, since = "0.9.0") {
                alias("encode", C_MessageType.ERROR, since = SINCE0)
                comment("Converts the text to UTF-8 encoded bytes.")
                body { text ->
                    val s = text.asString()
                    val byteArray = s.toByteArray(CHARSET)
                    Rt_ByteArrayValue.get(byteArray)
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

    override fun str(format: StrFormat): String = value
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
