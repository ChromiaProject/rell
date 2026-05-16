/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.lmodel.L_ParamArity
import net.postchain.rell.base.lmodel.dsl.Ld_BodyResult
import net.postchain.rell.base.lmodel.dsl.Ld_FunctionDsl
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.expr.Db_SysFunction
import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.sql.SqlConstants
import net.postchain.rell.base.utils.formatEx
import java.nio.ByteBuffer
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

object Lib_Type_Text {
    val DB_SUBSCRIPT: Db_SysFunction =
        Db_SysFunction.template("text.[]", 2, "${SqlConstants.FN_TEXT_GETCHAR}(#0, (#1)::INT)")

    private val CHARSET = Charsets.UTF_8
    private val LIST_OF_TEXT = Rt_ListType(Rt_PrimitiveTypes.TEXT)
    private val MAP_OF_TEXT_TO_TEXT = Rt_MapType(Rt_PrimitiveTypes.TEXT, Rt_PrimitiveTypes.TEXT)

    private const val SINCE0 = "0.6.0"

    val NAMESPACE = Ld_NamespaceDsl.make {
        alias("name", "text", since = SINCE0)
        alias("tuid", "text", since = SINCE0)

        type(Rt_TextValue, "text", since = SINCE0) {
            rrType(RR_Type.Primitive(RR_PrimitiveKind.TEXT))
            comment("An immutable character string data type.")

            staticFunction("from_bytes", result = "text", pure = true, since = "0.9.0") {
                """
                    Create a text representation of the given bytes.

                    By default, an exception is thrown if invalid UTF-8 characters are encountered, however if the
                    optional parameter `ignore_errors` is provided as `true`, invalid characters are instead replaced
                    with a placeholder.

                    @throws exception if invalid UTF-8 characters are encountered and `ignore_errors` is `false`
                """.comment()
                val bytes by param(Rt_ByteArrayValue, comment = "The byte array to convert to text.")
                val ignore_errors by paramOpt(
                    Rt_BooleanValue,
                    comment = "if true, invalid characters are replaced with placeholders; if false, an exception " +
                        "is thrown (defaults to `false`)",
                )
                body(Rt_TextValue) {
                    val ignoreErr = ignore_errors?.value ?: false
                    val s = if (ignoreErr) {
                        String(bytes.value, CHARSET)
                    } else {
                        val decoder = CHARSET.newDecoder()
                        val byteBuffer = ByteBuffer.wrap(bytes.value)
                        val charBuffer = Rt_Utils.wrapErr("fn:text.from_bytes") {
                            decoder.decode(byteBuffer)
                        }
                        charBuffer.toString()
                    }
                    s
                }
            }

            function("empty", pure = true, since = SINCE0) {
                """
                    Returns true if this text is empty, false otherwise.

                    `x.empty()` is equivalent to `x.size() == 0`.
                """.comment()
                val self by self()
                dbFunctionTemplate("text.empty", 1, "(LENGTH(#0) = 0)")
                body(Rt_BooleanValue) {
                    self.value.isEmpty()
                }
            }

            function("size", pure = true, since = SINCE0) {
                val self by self()
                alias("len", C_MessageType.ERROR, since = SINCE0)
                dbFunctionSimple("text.size", "LENGTH")
                comment("Returns the number of characters in this text.")
                body(Rt_IntValue) {
                    self.value.length.toLong()
                }
            }

            function("upper_case", pure = true, since = "0.9.0") {
                val self by self()
                alias("upperCase", C_MessageType.ERROR, since = SINCE0)
                dbFunctionSimple("text.upper_case", "UPPER")
                comment("Returns the text obtained by converting all alphabetic characters in this text to upper case.")
                body(Rt_TextValue) {
                    self.value.uppercase()
                }
            }

            function("lower_case", pure = true, since = "0.9.0") {
                val self by self()
                alias("lowerCase", C_MessageType.ERROR, since = SINCE0)
                dbFunctionSimple("text.lower_case", "LOWER")
                comment("Returns the text obtained by converting all alphabetic characters in this text to lower case.")
                body(Rt_TextValue) {
                    self.value.lowercase()
                }
            }

            function("compare_to", pure = true, since = "0.9.0") {
                """
                    Compares this text to another text lexicographically, based on the Unicode value of each character
                    in each text object.
                    @return `0` if this text and `other` are equal, a negative integer if lexicographically less than
                    `other`, and a positive integer if lexicographically greater than `other`.
                """.comment()
                val self by self()
                val other by param(Rt_TextValue, comment = "the text to compare against this text")
                alias("compareTo", C_MessageType.ERROR, since = SINCE0)
                body(Rt_IntValue) {
                    self.value.compareTo(other.value).toLong()
                }
            }

            function("contains", pure = true, since = SINCE0) {
                """
                    Checks if this text contains the specified substring.

                    Note that for all texts `t`, `t.contains('')` is true, and for all texts `u` and `v` such
                    that `u == v`, `u.contains(v)` is true.

                    `t.contains(u)` is equivalent to `t.index_of(u) >= 0`.
                    @return true if this text contains the specified substring, false otherwise
                """.comment()
                val self by self()
                val text by param(Rt_TextValue, comment = "the substring for which to search")
                dbFunctionTemplate("text.contains", 2, "(STRPOS(#0, #1) > 0)")
                body(Rt_BooleanValue) {
                    self.value.contains(text.value)
                }
            }

            function("starts_with", pure = true, since = "0.9.0") {
                """
                    Checks if this text starts with the specified prefix.

                    Note that for all texts `t`, `t.starts_with('')` is true, and for all texts `u` and `v` such
                    that `u == v`, `u.starts_with(v)` is true.
                    @return true if this text starts with the specified prefix, false otherwise
                """.comment()
                val self by self()
                val prefix by param(Rt_TextValue, comment = "the prefix to check")
                alias("startsWith", C_MessageType.ERROR, since = SINCE0)
                dbFunctionTemplate("text.starts_with", 2, "(LEFT(#0, LENGTH(#1)) = #1)")
                body(Rt_BooleanValue) {
                    self.value.startsWith(prefix.value)
                }
            }

            function("ends_with", pure = true, since = "0.9.0") {
                """
                    Checks if this text ends with the specified suffix.

                    Note that for all texts `t`, `t.ends_with('')` is true, and for all texts `u` and `v` such
                    that `u == v`, `u.ends_with(v)` is true.
                    @return true if this text ends with the specified suffix, false otherwise
                """.comment()
                val self by self()
                val suffix by param(Rt_TextValue, comment = "the suffix to check")
                alias("endsWith", C_MessageType.ERROR, since = SINCE0)
                dbFunctionTemplate("text.ends_with", 2, "(RIGHT(#0, LENGTH(#1)) = #1)")
                body(Rt_BooleanValue) {
                    self.value.endsWith(suffix.value)
                }
            }

            function("format", result = "text", pure = true, since = SINCE0) {
                """
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
                """.comment()
                val self by self()
                param("args", type = "anything", arity = L_ParamArity.ZERO_MANY) {
                    """
                        Arguments referenced by the format specifiers in this format string.
                        The number of arguments is variable and may be zero.
                   """.comment()
                }
                bodyN { args ->
                    Rt_Utils.check(args.isNotEmpty()) { "fn:text.format:no_args" to "No arguments" }
                    val s = self.value
                    val anys = args.drop(1).map { it.toFormatArg() }.toTypedArray()
                    val r = try {
                        s.formatEx(*anys)
                    } catch (_: IllegalFormatException) {
                        s
                    }
                    Rt_TextValue.get(r)
                }
            }

            function("replace", pure = true, since = SINCE0) {
                """
                    Returns the text obtained by replacing all occurrences of the substring `old_value` in this text
                    with `new_value`.
                """.comment()
                val self by self()
                val old_value by param(Rt_TextValue, comment = "the substring to be replaced")
                val new_value by param(Rt_TextValue, comment = "the replacement substring")
                dbFunctionTemplate("text.replace", 3, "REPLACE(#0, #1, #2)")
                body(Rt_TextValue) {
                    self.value.replace(old_value.value, new_value.value)
                }
            }

            function("split", result = "list<text>", pure = true, since = SINCE0) {
                """
                    Splits this text around matches of the given delimiter.

                    Examples:

                    - `'the cow jumped over the moon'.split(' ')` returns `['the', 'cow', 'jumped', 'over', 'the', 'moon']`.
                    - `'giggling'.split('g')` returns `['', 'i', '', 'lin', '']`.
                    - `'espresso'.split('a')` returns `['espresso']`.

                    @return a `list<text>` which is the result of splitting this text at each occurrence of `delimiter`
                """.comment()
                val self by self()
                val delimiter by param(Rt_TextValue, comment = "the delimiter on which to split")
                body {
                    val arr = self.value.split(delimiter.value)
                    val list = MutableList<Rt_Value>(arr.size) { Rt_TextValue.get(arr[it]) }
                    Rt_ListValue(LIST_OF_TEXT, list)
                }
            }

            function("trim", pure = true, since = SINCE0) {
                // TODO: document which characters count as whitespace.
                comment("Returns text matching this one, but with leading and trailing whitespace removed.")
                //dbFunction(Db_SysFunction.template("text.trim", 1, "TRIM(#0, ' '||CHR(9)||CHR(10)||CHR(13))"))
                val self by self()
                body(Rt_TextValue) {
                    self.value.trim()
                }
            }

            function("like", pure = true, since = "0.10.4") {
                """
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
                """.comment()
                val self by self()
                val pattern by param(Rt_TextValue, comment = "the pattern to match against")
                dbFunctionTemplate("text.like", 2, "((#0) LIKE (#1))")
                body(Rt_BooleanValue) {
                    Rt_TextValue.like(self.value, pattern.value)
                }
            }

            function("matches", result = "boolean", pure = true, since = SINCE0) {
                """
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
                """.comment()
                val self by self()
                val regex by param(Rt_TextValue, comment = "the regular expression to match against")
                matcherBody({ self }, { regex }) { Rt_BooleanValue.get(it.matches()) }
            }

            function("match_groups", result = "list<text>?", pure = true, since = "0.14.13") {
                """
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
                """.comment()
                val self by self()
                val regex by param(Rt_TextValue, comment = "the regular expression to match against")
                matchedMatcherOrNullBody({ self }, { regex }) { m ->
                    val matches = mutableListOf<Rt_Value>()
                    for (i in 0 .. m.groupCount()) {
                        matches.add(Rt_TextValue.get(m.group(i) ?: ""))
                    }
                    Rt_ListValue(LIST_OF_TEXT, matches)
                }
            }

            function("match_named_groups", result = "map<text, text>?", pure = true, since = "0.14.13") {
                """
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
                """.comment()
                val self by self()
                val regex by param(Rt_TextValue, comment = "the regular expression to match against")
                matchedMatcherOrNullBody({ self }, { regex }) { m ->
                    val matches = mutableMapOf<Rt_Value, Rt_Value>()
                    val namedGroups = m.namedGroups().entries.sortedBy { it.value }
                    for ((name, index) in namedGroups) {
                        val groupText = m.group(index)
                        if (groupText != null) {
                            matches[Rt_TextValue.get(name)] = Rt_TextValue.get(groupText)
                        }
                    }
                    Rt_MapValue(MAP_OF_TEXT_TO_TEXT, matches)
                }
            }

            function("regex_replace", since = "0.14.16") {
                $$"""
                    Returns the text obtained by replacing all subsequences in this text which match the given regular
                    expression `regex`, with the text `replacement`.

                    Supports named and numbered group references in the `replacement` text.

                    Examples:
                    - `'aaaaacaaaaabaaaaacaaaaa'.regex_replace('(b|c)', 'd')` returns `'aaaaadaaaaadaaaaadaaaaa'`.
                    - `'aaaaacaaaaabaaaaacaaaaa'.regex_replace('a+', '')` returns `'cbc'`.
                    - `'johnsmith@chromaway.com'.regex_replace('[a-z]+', '0')` returns `'0@0.0'`.
                    - `'John Doe'.regex_replace('(\\w+) (\\w+)', '$2, $1')` returns `'Doe, John'`.
                    - `'2025-10-27'.regex_replace('(?<y>\\d{4})-(?<m>\\d{2})-(?<d>\\d{2})', '${d}/${m}/${y}')`
                        returns `'27/10/2025'`.

                    @return text equal to this, but with regions that match `regex` replaced with `replacement`
                    @throws exception if `regex` is not a valid regular expression
                """.comment()
                val self by self()
                val regex by param(Rt_TextValue, comment = "the regular expression to search with")
                val replacement by param(Rt_TextValue, comment = "the replacement text")
                //dbFunctionTemplate("text.regex_replace", 3, "REGEXP_REPLACE(#0, #1, #2, 'g')")
                body(Rt_TextValue) {
                    val compiledRegex = try {
                        Pattern.compile(regex.value)
                    } catch (_: PatternSyntaxException) {
                        throw Rt_Exception.common(
                            "fn:text.$fnSimpleName:bad_regex",
                            "Invalid regular expression: ${regex.value}",
                        )
                    }
                    compiledRegex.matcher(self.value).replaceAll(replacement.value)
                }
            }

            function("char_at", pure = true, since = "0.9.0") {
                """
                    Retrieves the 16-bit code of the character at the specified index within this text.
                    @throws exception if `index` is greater than or equal to the size of this text
                """.comment()
                val self by self()
                val index by param(Rt_IntValue, comment = "the index of the character")
                alias("charAt", C_MessageType.ERROR, since = SINCE0)
                dbFunctionTemplate("text.char_at", 2, "ASCII(${SqlConstants.FN_TEXT_GETCHAR}(#0, (#1)::INT))")
                body(Rt_IntValue) {
                    val s = self.value
                    val i = index.value
                    if (i < 0 || i >= s.length) {
                        throw Rt_Exception.common(
                            "fn:text.char_at:index:${s.length}:$i",
                            "Index out of bounds: $i (length ${s.length})"
                        )
                    }
                    val c = s[i.toInt()]
                    c.code.toLong()
                }
            }

            function("index_of", pure = true, since = "0.9.0") {
                """
                    Search for the first occurrence of the specified substring within this text.
                    @return the index of the first occurrence of the substring within this text, or `-1` if
                    the substring does not occur in this text
                """.comment()
                val self by self()
                val text by param(Rt_TextValue, comment = "the substring for which to search")
                alias("indexOf", C_MessageType.ERROR, since = SINCE0)
                dbFunctionTemplate("text.index_of", 2, "(STRPOS(#0, #1) - 1)")
                body(Rt_IntValue) {
                    self.value.indexOf(text.value).toLong()
                }
            }

            function("index_of", pure = true, since = "0.9.0") {
                """
                    Returns the position of the first occurrence of the specified substring within this text,
                    starting at the specified index, or -1 if the text is not found.
                """.comment()
                val self by self()
                val text by param(Rt_TextValue, comment = "the substring for which to search")
                val start by param(Rt_IntValue, comment = "the index from which to start the search")
                alias("indexOf", C_MessageType.ERROR, since = SINCE0)
                body(Rt_IntValue) {
                    val s1 = self.value
                    if (start.value < 0 || start.value >= s1.length) {
                        throw Rt_Exception.common(
                            "fn:text.index_of:index:${s1.length}:${start.value}",
                            "Index out of bounds: ${start.value} (length ${s1.length})"
                        )
                    }
                    s1.indexOf(text.value, start.value.toInt()).toLong()
                }
            }

            function("last_index_of", pure = true, since = "0.9.0") {
                """
                    Returns the index within this text of the last occurrence of the specified string,
                    or -1 if not found.
                """.comment()
                val self by self()
                val text by param(Rt_TextValue, comment = "the substring for which to search")
                alias("lastIndexOf", C_MessageType.ERROR, since = SINCE0)
                body(Rt_IntValue) {
                    self.value.lastIndexOf(text.value).toLong()
                }
            }

            function("last_index_of", pure = true, since = "0.9.0") {
                """
                    Returns the index within this text of the last occurrence of the specified string,
                    starting from the specified startIndex, or -1 if not found.
                """.comment()
                val self by self()
                val text by param(Rt_TextValue, comment = "the substring for which to search")
                val max by param(Rt_IntValue, comment = "the index from which to start the reverse-search")
                alias("lastIndexOf", C_MessageType.ERROR, since = SINCE0)
                body(Rt_IntValue) {
                    val s1 = self.value
                    if (max.value < 0 || max.value >= s1.length) {
                        throw Rt_Exception.common(
                            "fn:text.last_index_of:index:${s1.length}:${max.value}",
                            "Index out of bounds: ${max.value} (length ${s1.length})"
                        )
                    }
                    s1.lastIndexOf(text.value, max.value.toInt()).toLong()
                }
            }

            function("repeat", pure = true, since = "0.11.0") {
                """
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
                """.comment()
                val self by self()
                val n by param(Rt_IntValue, comment = "the number of times to repeat this text")
                dbFunctionTemplate("text.repeat", 2, "${SqlConstants.FN_TEXT_REPEAT}(#0, (#1)::INT)")
                body(Rt_TextValue) {
                    val s = self.value
                    Lib_Type_List.rtCheckRepeatArgs(s.length, n.value, "text")
                    if (s.isEmpty() || n.value == 1L) s else {
                        s.repeat(n.value.toInt())
                    }
                }
            }

            function("reversed", pure = true, since = "0.11.0") {
                """
                    Returns a reversed copy of this text. SQL compatible.

                    Examples:
                    - `''.reversed()` returns `''`
                    - `'a'.reversed()` returns `'a'`
                    - `'abc'.reversed()` returns `'cba'`
                    @return text equal to this text, except the order of the characters is reversed
                """.comment()
                val self by self()
                dbFunctionTemplate("text.reversed", 1, "REVERSE(#0)")
                body(Rt_TextValue) {
                    if (self.value.length <= 1) {
                        self.value
                    } else {
                        self.value.reversed()
                    }
                }
            }

            function("sub", result = "text", pure = true, since = SINCE0) {
                """Returns a substring of this text starting from the specified index (inclusive).""".comment()
                val self by self()
                val start by param(Rt_IntValue, comment = "the starting index of the substring")
                dbFunctionTemplate("text.sub/1", 2, "${SqlConstants.FN_TEXT_SUBSTR1}(#0, (#1)::INT)")
                body {
                    calcSub(self.value, start.value, self.value.length.toLong())
                }
            }

            function("sub", result = "text", pure = true, since = SINCE0) {
                """
                    Returns a substring of this text from the specified start index (inclusive)
                    to the specified end index (exclusive).
                """.comment()
                val self by self()
                val start by param(Rt_IntValue, comment = "the start index of the substring")
                val end by param(Rt_IntValue, comment = "the end index of the substring")
                dbFunctionTemplate("text.sub/2", 3, "${SqlConstants.FN_TEXT_SUBSTR2}(#0, (#1)::INT, (#2)::INT)")
                body {
                    calcSub(self.value, start.value, end.value)
                }
            }

            function("to_bytes", pure = true, since = "0.9.0") {
                val self by self()
                alias("encode", C_MessageType.ERROR, since = SINCE0)
                comment("Converts this text to an array of UTF-8 encoded bytes.")
                body(Rt_ByteArrayValue) {
                    self.value.toByteArray(CHARSET)
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

    private fun Ld_FunctionDsl.matcherBody(
        self: () -> Rt_TextValue,
        regex: () -> Rt_TextValue,
        rCode: (Matcher) -> Rt_Value,
    ): Ld_BodyResult = body {
        val string = self().value
        val pattern = regex().value
        val matcher = try {
            Pattern.compile(pattern).matcher(string)
        } catch (_: PatternSyntaxException) {
            throw Rt_Exception.common("fn:text.$fnSimpleName:bad_regex", "Invalid regular expression: $pattern")
        }
        rCode(matcher)
    }

    private fun Ld_FunctionDsl.matchedMatcherOrNullBody(
        self: () -> Rt_TextValue,
        regex: () -> Rt_TextValue,
        rCode: (Matcher) -> Rt_Value,
    ): Ld_BodyResult =
        matcherBody(self, regex) { if (!it.matches()) Rt_NullValue else rCode(it) }
}
