/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvString
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lmodel.L_ParamArity
import net.postchain.rell.base.lmodel.dsl.Ld_BodyResult
import net.postchain.rell.base.lmodel.dsl.Ld_FunctionDsl
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.Db_SysFunction
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_Comparator
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.sql.PreparedStatementParams
import net.postchain.rell.base.sql.ResultSetRow
import net.postchain.rell.base.sql.SqlConstants
import net.postchain.rell.base.utils.formatEx
import org.jooq.impl.SQLDataType
import java.nio.ByteBuffer
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

object Lib_Type_Text {
    val DB_SUBSCRIPT: Db_SysFunction =
        Db_SysFunction.template("text.[]", 2, "${SqlConstants.FN_TEXT_GETCHAR}(#0, (#1)::INT)")

    private val CHARSET = Charsets.UTF_8
    private val LIST_OF_TEXT = R_ListType(R_TextType)
    private val MAP_OF_TEXT_TO_TEXT = R_MapType(R_MapKeyValueTypes(R_TextType, R_TextType))

    private const val SINCE0 = "0.6.0"

    val NAMESPACE = Ld_NamespaceDsl.make {
        alias("name", "text", since = SINCE0)
        alias("tuid", "text", since = SINCE0)

        type("text", rType = R_TextType, since = SINCE0) {
            comment("An immutable character string data type.")

            staticFunction("from_bytes", result = "text", pure = true, since = "0.9.0") {
                comment("""
                    Create a text representation of the given bytes.

                    By default, an exception is thrown if invalid UTF-8 characters are encountered, however if the
                    optional parameter `ignore_errors` is provided as `true`, invalid characters are instead replaced
                    with a placeholder.

                    @throws exception if invalid UTF-8 characters are encountered and `ignore_errors` is `false`
                """)
                param("bytes", type = "byte_array", comment = "The byte array to convert to text.")
                param("ignore_errors", type = "boolean", arity = L_ParamArity.ZERO_ONE) {
                    comment("""
                        if true, invalid characters are replaced with placeholders; if false, an exception is thrown
                        (defaults to `false`)
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
                comment("""
                    Returns true if this text is empty, false otherwise.

                    `x.empty()` is equivalent to `x.size() == 0`.
                """)
                dbFunctionTemplate("text.empty", 1, "(LENGTH(#0) = 0)")
                body { text ->
                    val s = text.asString()
                    Rt_BooleanValue.get(s.isEmpty())
                }
            }

            function("size", result = "integer", pure = true, since = SINCE0) {
                alias("len", C_MessageType.ERROR, since = SINCE0)
                dbFunctionSimple("text.size", "LENGTH")
                comment("Returns the number of characters in this text.")
                body { text ->
                    val s = text.asString()
                    Rt_IntValue.get(s.length.toLong())
                }
            }

            function("upper_case", result = "text", pure = true, since = "0.9.0") {
                alias("upperCase", C_MessageType.ERROR, since = SINCE0)
                dbFunctionSimple("text.upper_case", "UPPER")
                comment("Returns the text obtained by converting all alphabetic characters in this text to upper case.")
                body { text ->
                    val s = text.asString()
                    Rt_TextValue.get(s.uppercase())
                }
            }

            function("lower_case", result = "text", pure = true, since = "0.9.0") {
                alias("lowerCase", C_MessageType.ERROR, since = SINCE0)
                dbFunctionSimple("text.lower_case", "LOWER")
                comment("Returns the text obtained by converting all alphabetic characters in this text to lower case.")
                body { text ->
                    val s = text.asString()
                    Rt_TextValue.get(s.lowercase())
                }
            }

            function("compare_to", result = "integer", pure = true, since = "0.9.0") {
                alias("compareTo", C_MessageType.ERROR, since = SINCE0)
                comment("""
                    Compares this text to another text lexicographically, based on the Unicode value of each character
                    in each text object.
                    @return `0` if this text and `other` are equal, a negative integer if lexicographically less than
                    `other`, and a positive integer if lexicographically greater than `other`.
                """)
                param("other", type = "text", comment = "the text to compare against this text")
                body { text, other ->
                    val s1 = text.asString()
                    val s2 = other.asString()
                    Rt_IntValue.get(s1.compareTo(s2).toLong())
                }
            }

            function("contains", result = "boolean", pure = true, since = SINCE0) {
                comment("""
                    Checks if this text contains the specified substring.

                    Note that for all texts `t`, `t.contains('')` is true, and for all texts `u` and `v` such
                    that `u == v`, `u.contains(v)` is true.

                    `t.contains(u)` is equivalent to `t.index_of(u) >= 0`.
                    @return true if this text contains the specified substring, false otherwise
                """)
                param("text", type = "text", comment = "the substring for which to search")
                dbFunctionTemplate("text.contains", 2, "(STRPOS(#0, #1) > 0)")
                body { text, substring ->
                    val s1 = text.asString()
                    val s2 = substring.asString()
                    Rt_BooleanValue.get(s1.contains(s2))
                }
            }

            function("starts_with", result = "boolean", pure = true, since = "0.9.0") {
                alias("startsWith", C_MessageType.ERROR, since = SINCE0)
                comment("""
                    Checks if this text starts with the specified prefix.

                    Note that for all texts `t`, `t.starts_with('')` is true, and for all texts `u` and `v` such
                    that `u == v`, `u.starts_with(v)` is true.
                    @return true if this text starts with the specified prefix, false otherwise
                """)
                param("prefix", type = "text", comment = "the prefix to check")
                dbFunctionTemplate("text.starts_with", 2, "(LEFT(#0, LENGTH(#1)) = #1)")
                body { text, prefix ->
                    val s1 = text.asString()
                    val s2 = prefix.asString()
                    Rt_BooleanValue.get(s1.startsWith(s2))
                }
            }

            function("ends_with", result = "boolean", pure = true, since = "0.9.0") {
                alias("endsWith", C_MessageType.ERROR, since = SINCE0)
                comment("""
                    Checks if this text ends with the specified suffix.

                    Note that for all texts `t`, `t.ends_with('')` is true, and for all texts `u` and `v` such
                    that `u == v`, `u.ends_with(v)` is true.
                    @return true if this text ends with the specified suffix, false otherwise
                """)
                param("suffix", type = "text", comment = "the suffix to check")
                dbFunctionTemplate("text.ends_with", 2, "(RIGHT(#0, LENGTH(#1)) = #1)")
                body { text, suffix ->
                    val s1 = text.asString()
                    val s2 = suffix.asString()
                    Rt_BooleanValue.get(s1.endsWith(s2))
                }
            }

            function("format", result = "text", pure = true, since = SINCE0) {
                comment("""
                    Uses this text as a format string and returns the text obtained by substituting the specified
                    arguments.

                    Supports many of the format specifiers found in other programming languages, including:

                    - `%s` for `text`
                    - `%d` for `integer`s and `big_integer`s in decimal (base 10) representation
                    - `%o` for `integer`s and `big_integer`s in octal (base 8) representation
                    - `%x` for `integer`s and `big_integer`s in hexadecimal (base 16) representation
                    - `%f` for `decimal` (supports precision, e.g. `%.2f` for two decimal places)
                    - `%b` for `boolean`s (output in lower-case, i.e. `true` and `false`)
                    - `%B` for `boolean`s (output in upper-case, i.e. `TRUE` and `FALSE`)

                    Examples:

                    - `'My integer is %d.'.format(123)` returns `'My integer is 123.'`.
                    - `'See you...%10s.'.format('later')` returns `'See you...     later.'`.
                    - `'Earnings: daily=%f, weekly=%f, monthly=%f.'.format(312.45, 534.78, 2199.67)` returns
                        `'Earnings: daily=312.450000, weekly=534.780000, monthly=2199.670000.'`.
                    - `''%d %o %x'.format(14, 14, 14)'` returns `'14 16 e'`.

                    If any format specifier is incompatible with the type of its corresponding argument, or if there are
                    more specifiers requiring substitution than there are arguments, all format specifiers are left
                    unsubstituted in the output text.

                    All format specifiers are also left unsubstituted in the output text when any argument is `null`,
                    except when it matches the specifier is `%s`, in which case the text `'null'` is substituted
                    (assuming the other arguments and specifiers are correctly matched).

                    If there are more arguments than format specifiers, the extra arguments are ignored, but matched
                    arguments are still substituted (assuming they match correctly).

                    @return formatted text
                """)
                param("args", type = "anything", arity = L_ParamArity.ZERO_MANY) {
                    comment("""
                        Arguments referenced by the format specifiers in this format string.
                        The number of arguments is variable and may be zero.
                   """)
                }
                bodyN { args ->
                    Rt_Utils.check(args.isNotEmpty()) { "fn:text.format:no_args" toCodeMsg "No arguments" }
                    val s = args[0].asString()
                    val anys = args.drop(1).map { it.toFormatArg() }.toTypedArray()
                    val r = try {
                        s.formatEx(*anys)
                    } catch (_: IllegalFormatException) {
                        s
                    }
                    Rt_TextValue.get(r)
                }
            }

            function("replace", result = "text", pure = true, since = SINCE0) {
                comment("""
                    Returns the text obtained by replacing all occurrences of the substring `old_value` in this text
                    with `new_value`.
                """)
                param("old_value", type = "text", comment = "the substring to be replaced")
                param("new_value", type = "text", comment = "the replacement substring")
                dbFunctionTemplate("text.replace", 3, "REPLACE(#0, #1, #2)")
                body { text, old, new ->
                    val s1 = text.asString()
                    val s2 = old.asString()
                    val s3 = new.asString()
                    Rt_TextValue.get(s1.replace(s2, s3))
                }
            }

            function("split", result = "list<text>", pure = true, since = SINCE0) {
                comment("""
                    Splits this text around matches of the given delimiter.

                    Examples:

                    - `'the cow jumped over the moon'.split(' ')` returns `['the', 'cow', 'jumped', 'over', 'the', 'moon']`.
                    - `'giggling'.split('g')` returns `['', 'i', '', 'lin', '']`.
                    - `'espresso'.split('a')` returns `['espresso']`.

                    @return a `list<text>` which is the result of splitting this text at each occurrence of `delimiter`
                """)
                param("delimiter", type = "text", comment = "the delimiter on which to split")
                body { text, delimiter ->
                    val s1 = text.asString()
                    val s2 = delimiter.asString()
                    val arr = s1.split(s2)
                    val list = MutableList(arr.size) { Rt_TextValue.get(arr[it]) }
                    Rt_ListValue(LIST_OF_TEXT, list)
                }
            }

            function("trim", result = "text", pure = true, since = SINCE0) {
                // TODO: document which characters count as whitespace.
                comment("Returns text matching this one, but with leading and trailing whitespace removed.")
                //dbFunction(Db_SysFunction.template("text.trim", 1, "TRIM(#0, ' '||CHR(9)||CHR(10)||CHR(13))"))
                body { text ->
                    val s = text.asString()
                    Rt_TextValue.get(s.trim())
                }
            }

            function("like", result = "boolean", pure = true, since = "0.10.4") {
                comment("""
                    Matches this text against the specified SQL LIKE pattern.

                    Use `'_'` to match any single character, and `'%'` to match any sequence of zero or more characters.
                    The escape sequences `'\\_'`, `'\\%'` and `'\\\\'` match the literal characters `'_'`, `'%'` and
                    `'\'` respectively.

                    Examples:

                    ```rell
                    val names = ["Alice", "Victor", "Viktor", "Victoria", "V\\ctor"];
                    print(names @* { .like("Vi_tor") });  // prints [Victor, Viktor]
                    print(names @* { .like("Vic%") }); // prints [Victor, Victoria]
                    print(names @* { .like("V\\\\c%") }); // prints [V\ctor]
                    ```

                    @see 1. <a href="https://www.postgresql.org/docs/current/functions-matching.html#FUNCTIONS-LIKE">Pattern Matching: LIKE - PostgreSQL Documentation</a>
                """)
                param("pattern", type = "text", comment = "the pattern to match against")
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
                    Matches this text against the specified regular expression.

                    Regular expressions use the same syntax as [`java.util.regex.Pattern`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/regex/Pattern.html).

                    Examples:

                    ```rell
                    val names = ["Alice", "Victor", "Viktor", "Victoria", "V\\ctor"];
                    print(names @* { .matches("Vi[a-z]tor") });  // prints [Victor, Viktor]
                    print(names @* { .matches("Vic.*") }); // prints [Victor, Victoria]
                    print(names @* { .matches("V\\\\c.*") }); // prints [V\ctor]
                    ```

                    @throws exception if `regex` is not a valid regular expression
                    @see 1. <a href="https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/regex/Pattern.html"><code>java.util.regex.Pattern</code> - Java SE 21 & JDK 21</a>
                """)
                param("regex", type = "text", comment = "The regular expression to match against.")
                matcherBody { Rt_BooleanValue.get(it.matches()) }
            }

            function("match_groups", result = "list<text>?", pure = true, since = "0.14.13") {
                comment("""
                    Match this text against the specified regular expression, returning all match groups in a list.

                    Attempts to match this entire text, as opposed to searching for a match.

                    Match groups in a regular expression are defined by any parentheses that the regular expression
                    contains, and the groups are ordered by the position of their opening parentheses. For example, the
                    regular expression `(X(Y))(Z)` contains 3 matching groups, which are:

                    - `(X(Y))`
                    - `(Y)`
                    - `(Z)`

                    The zeroth element in the returned list is the entire match, i.e. this exact text value, assuming
                    the regular expression matches this text. The subsequent list elements are the match subgroups.

                    Examples:

                    - `'johnsmith@chromaway.com'.match_groups('([a-z]+)@([a-z]+[.][a-z]+)')` returns
                        `['johnsmith@chromaway.com', 'johnsmith', 'chromaway.com']`.
                    - `'XYZ'.match_groups('(X(Y))(Z)')` returns `['XYZ', 'XY', 'Y', 'Z']`.
                    - `'XYZ'.match_groups('((X(Y))(Z))')` returns `['XYZ', 'XYZ' ,'XY', 'Y', 'Z']`.
                    - `'X'.match_groups('(X)|(Y)')` returns `['X', 'X', '']` (the third group (`(Y)`) matches nothing,
                        hence the empty string).

                    Matched non-capturing groups (notated `(?:X)`, where `X` is a regular expression) are supported, and
                    are not included in the returned list.

                    Named capturing groups (notated `(?<name>X)`, where `X` is a regular expression) can be used, but
                    the returned value provides no way to access named groups by their name (though they are present in
                    the returned list). To extract groups by name, use instead `text.match_named_groups()`.

                    @return a `list<text>` containing all match groups (the zeroth of which is the entire matched text),
                    or `null` if this text does not match the given regular expression
                    @throws exception if `regex` is not a valid regular expression
                    @see 1. <a href="https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/regex/Pattern.html#cg"><code>java.util.regex.Pattern</code> - Groups and capturing (Java SE 21 & JDK 21)</a>
                    @see 2. <a href="match_named_groups.html"><code>text.match_named_groups</code> - Rell Standard Library</a>
                """)
                param("regex", type = "text", comment = "The regular expression to match against.")
                matchedMatcherOrNullBody { m ->
                    val matches = mutableListOf<Rt_Value>()
                    for (i in 0 .. m.groupCount()) {
                        matches.add(Rt_TextValue.get(m.group(i) ?: ""))
                    }
                    Rt_ListValue(LIST_OF_TEXT, matches)
                }
            }

            function("match_named_groups", result = "map<text, text>?", pure = true, since = "0.14.13") {
                comment("""
                    Match this text against the specified regular expression, returning a map whose keys are the names
                    of each named group in the regular expression, and values are the text that was matched to the
                    corresponding group.

                    Attempts to match this entire text, as opposed to searching for a match.

                    Unnamed groups and their matching text are not included in the returned map (use instead
                    `text.match_groups()` to extract these).

                    Match groups in a regular expression are defined by any parentheses that the regular expression
                    contains, and the groups are ordered by the position of their opening parentheses. For example, the
                    regular expression `(X(Y))(Z)` contains 3 matching groups, which are:

                    - `(X(Y))`
                    - `(Y)`
                    - `(Z)`

                    Named groups are match groups in a regular expression for which a name is specified. The match
                    groups in the above example (`(X(Y))(Z)`) could be assigned names in the following manner:

                    ```
                    (?<x>X(?<y>Y))(?<z>Z)
                    ```

                    This is an equivalent regular expression, but the text matched by each group can be referenced by
                    the group's name.

                    Examples:

                    - `'XYZ'.match_named_groups('(?<x>X(?<y>Y))(?<z>Z)')` returns `['x': 'XY', 'y': 'Y', 'z': 'Z']`.
                    - `'XYZ'.match_named_groups('(?<x>X(Y))(Z)')` returns `['x': 'XY']`.
                    - `'johnsmith@chromaway.com'.match_groups('(?<user>[a-z]+)@(?<domain>[a-z]+[.][a-z]+)')` returns
                        `['user': 'johnsmith', 'domain': 'chromaway.com']`.
                    - `'X'.match_named_groups('(?<x>X)|(?<y>Y)')` returns `['x': 'X']`.
                    - `'X'.match_named_groups('(?<x>X)(?<y>Y?)')` returns `['x': 'X', 'y': '']`.

                    Matched non-capturing groups (notated `(?:X)`, where `X` is a regular expression) are supported, and
                    are not included in the returned map. Named groups that match the empty string with the `?` and `*`
                    quantifiers are included in the returned map, but named groups within unmatched alternatives are
                    not.

                    @return a `map<text, text>` containing the match groups and their matching text obtained by matching
                    this text to the given regular expression; or `null` if this text does not match the given regular
                    expression
                    @throws exception if `regex` is not a valid regular expression
                    @see 1. <a href="https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/regex/Pattern.html#cg"><code>java.util.regex.Pattern</code> - Groups and capturing (Java SE 21 & JDK 21)</a>
                    @see 2. <a href="match_groups.html"><code>text.match_groups</code> - Rell Standard Library</a>
                """)
                param("regex", type = "text", comment = "The regular expression to match against.")
                matchedMatcherOrNullBody { m ->
                    val matches = mutableMapOf<Rt_Value, Rt_Value>()
                    val namedGroups = m.namedGroups().entries.sortedBy { it.value }
                    for ((name, index) in namedGroups) {
                        val groupText = m.group(index)
                        if (groupText != null) {
                            matches.put(Rt_TextValue.get(name), Rt_TextValue.get(groupText))
                        }
                    }
                    Rt_MapValue(MAP_OF_TEXT_TO_TEXT, matches)
                }
            }

            function("char_at", result = "integer", pure = true, since = "0.9.0") {
                alias("charAt", C_MessageType.ERROR, since = SINCE0)
                comment("""
                    Retrieves the 16-bit code of the character at the specified index within this text.
                    @throws exception if `index` is greater than or equal to the size of this text
                """)
                param("index", type = "integer", comment = "the index of the character")
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
                    Rt_IntValue.get(c.code.toLong())
                }
            }

            function("index_of", result = "integer", pure = true, since = "0.9.0") {
                alias("indexOf", C_MessageType.ERROR, since = SINCE0)
                comment("""
                    Search for the first occurrence of the specified substring within this text.
                    @return the index of the first occurrence of the substring within this text, or `-1` if
                    the substring does not occur in this text
                """)
                param("text", type = "text", comment = "the substring for which to search")
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
                param("text", type = "text", comment = "the substring for which to search")
                param("start", type = "integer", comment = "the index from which to start the search")
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
                param("text", type = "text", comment = "the substring for which to search")
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
                param("text", type = "text", comment = "the substring for which to search")
                param("max", type = "integer", comment = "the index from which to start the reverse-search")
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
                comment("""
                    Repeat this text `n` times. SQL compatible.

                    Examples:
                    - `'abc'.repeat(3)` returns `'abcabcabc'`
                    - `''.repeat(3)` returns `''`
                    - `'abc'.repeat(0)` returns `''`

                    @throws exception when:
                    - `n` is negative
                    - `n` is greater than `(2^31)-1`
                    - the resulting text has size greater than `(2^31)-1`
                    @return text equal to this text, except repeated `n` times
                """)
                param("n", type = "integer", comment = "the number of times to repeat this text")
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
                comment("""
                    Returns a reversed copy of this text. SQL compatible.

                    Examples:
                    - `''.reversed()` returns `''`
                    - `'a'.reversed()` returns `'a'`
                    - `'abc'.reversed()` returns `'cba'`
                    @return text equal to this text, except the order of the characters is reversed
                """)
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
                comment("""Returns a substring of this text starting from the specified index (inclusive).""")
                param("start", type = "integer", comment = "the starting index of the substring")
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
                param("start", type = "integer", comment = "the start index of the substring")
                param("end", type = "integer", comment = "the end index of the substring")
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
                comment("Converts this text to an array of UTF-8 encoded bytes.")
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

    private fun Ld_FunctionDsl.matcherBody(rCode: (Matcher) -> Rt_Value): Ld_BodyResult = body { a, b ->
        val string = a.asString()
        val pattern = b.asString()
        val matcher = try {
            Pattern.compile(pattern).matcher(string)
        } catch (_: PatternSyntaxException) {
            throw Rt_Exception.common("fn:text.$fnSimpleName:bad_regex", "Invalid regular expression: $pattern")
        }
        rCode(matcher)
    }

    private fun Ld_FunctionDsl.matchedMatcherOrNullBody(rCode: (Matcher) -> Rt_Value): Ld_BodyResult =
        matcherBody { if (!it.matches()) Rt_NullValue else rCode(it) }
}

object R_TextType: R_PrimitiveType("text") {
    override fun defaultValue() = Rt_TextValue.EMPTY
    override fun comparator() = Rt_Comparator.create { it.asString() }
    override fun fromCli(s: String): Rt_Value = Rt_TextValue.get(s)
    override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_Text
    override fun createSqlAdapter(): R_TypeSqlAdapter = R_TypeSqlAdapter_Text

    override fun getLibTypeDef() = Lib_Rell.TEXT_TYPE

    private object R_TypeSqlAdapter_Text: R_TypeSqlAdapter_Primitive("text", SQLDataType.CLOB) {
        override fun toSqlValue(value: Rt_Value) = value.asString()

        override fun toSql(params: PreparedStatementParams, idx: Int, value: Rt_Value) {
            params.setString(idx, value.asString())
        }

        override fun fromSql(row: ResultSetRow, idx: Int, nullable: Boolean): Rt_Value {
            val v = row.getString(idx)
            return if (v != null) Rt_TextValue.get(v) else checkSqlNull(R_TextType, nullable)
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
                    buf.append("%04x".formatEx(c.code))
                }
            }

            return buf.toString()
        }
    }
}

private object GtvRtConversion_Text: GtvRtConversion() {
    override fun directCompatibility() = R_GtvCompatibility(fromGtv = true, toGtv = true)
    override fun rtToGtv(rt: Rt_Value, pretty: Boolean) = GtvString(rt.asString())

    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val s = GtvRtUtils.gtvToString(ctx, gtv, R_TextType)
        return Rt_TextValue.get(s)
    }
}
