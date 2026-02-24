/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.def

import net.postchain.rell.base.testutils.BaseRellTest
import kotlin.test.Test

internal class ParameterSizeAnnotationTest: BaseRellTest() {
    @Test fun testBasic() {
        chkCompile("function f(@size(1) l: byte_array): integer { return 1; }", "OK")
        chkCompile("function f(@size(1) l: text): integer { return 1; }", "OK")
        chkCompile("query f(@size(1) l: byte_array): integer { return 1; }", "OK")
        chkCompile("query f(@size(1) l: text): integer { return 1; }", "OK")
        chkCompile("operation f(@size(1) l: byte_array) {}", "OK")
        chkCompile("operation f(@size(1) l: text) {}", "OK")
    }

    @Test fun testInvalidOnUnsupportedTypes() {
        chkCompile("function s(@size(1, 5) l: boolean): boolean { return l; }", "ct_err:modifier:invalid:ann:size:invalid_type")
        chkCompile("function s(@size(1, 5) l: integer): integer { return l; }", "ct_err:modifier:invalid:ann:size:invalid_type")
        chkCompile("function s(@size(1, 5) l: decimal): decimal { return l; }", "ct_err:modifier:invalid:ann:size:invalid_type")
        chkCompile("function s(@size(1, 5) l: big_integer): big_integer { return l; }", "ct_err:modifier:invalid:ann:size:invalid_type")
        chkCompile("operation s(@min_size(1) l: boolean) {}", "ct_err:modifier:invalid:ann:min_size:invalid_type")
        chkCompile("operation s(@min_size(1) l: integer) {}", "ct_err:modifier:invalid:ann:min_size:invalid_type")
        chkCompile("operation s(@min_size(1) l: decimal) {}", "ct_err:modifier:invalid:ann:min_size:invalid_type")
        chkCompile("operation s(@min_size(1) l: big_integer) {}", "ct_err:modifier:invalid:ann:min_size:invalid_type")
        chkCompile("query s(@max_size(5) l: boolean): boolean { return l; }", "ct_err:modifier:invalid:ann:max_size:invalid_type")
        chkCompile("query s(@max_size(5) l: integer): integer { return l; }", "ct_err:modifier:invalid:ann:max_size:invalid_type")
        chkCompile("query s(@max_size(5) l: decimal): decimal { return l; }", "ct_err:modifier:invalid:ann:max_size:invalid_type")
        chkCompile("query s(@max_size(5) l: big_integer): big_integer { return l; }", "ct_err:modifier:invalid:ann:max_size:invalid_type")
    }

    @Test fun testInvalidArgCounts() {
        chkCompile("function s(@min_size(1, 5) l: byte_array): byte_array { return l; }", "ct_err:ann:min_size:arg_count:2")
        chkCompile("function s(@max_size(1, 5) l: text): text { return l; }", "ct_err:ann:max_size:arg_count:2")
        chkCompile("function s(@size(1, 5, 9) l: json): json { return l; }", "ct_err:ann:size:arg_count:3")
        chkCompile("operation s(@min_size(1, 5) l: byte_array) {}", "ct_err:ann:min_size:arg_count:2")
        chkCompile("operation s(@max_size(1, 5) l: text) {}", "ct_err:ann:max_size:arg_count:2")
        chkCompile("operation s(@size(1, 5, 9) l: json) {}", "ct_err:ann:size:arg_count:3")
        chkCompile("query s(@min_size(1, 5) l: byte_array): byte_array { return l; }", "ct_err:ann:min_size:arg_count:2")
        chkCompile("query s(@max_size(1, 5) l: text): text { return l; }", "ct_err:ann:max_size:arg_count:2")
        chkCompile("query s(@size(1, 5, 9) l: json): json { return l; }", "ct_err:ann:size:arg_count:3")
    }

    @Test fun testInvalidArgValues() {
        chkCompile("function s(@max_size(false) l: byte_array): byte_array { return l; }",
            "ct_err:modifier:invalid:ann:max_size:argument_value")
        chkCompile("function s(@size(null, 'hello') l: text): text { return l; }", "ct_err:modifier:invalid:ann:size:argument_value")
        chkCompile("function s(@min_size(x'') l: json): json { return l; }", "ct_err:modifier:invalid:ann:min_size:argument_value")
        chkCompile("operation s(@max_size(false) l: byte_array) {}",
            "ct_err:modifier:invalid:ann:max_size:argument_value")
        chkCompile("operation s(@size(null, 'hello') l: text) {}", "ct_err:modifier:invalid:ann:size:argument_value")
        chkCompile("operation s(@min_size(x'') l: json) {}", "ct_err:modifier:invalid:ann:min_size:argument_value")
        chkCompile("query s(@max_size(false) l: byte_array): byte_array { return l; }",
            "ct_err:modifier:invalid:ann:max_size:argument_value")
        chkCompile("query s(@size(null, 'hello') l: text): text { return l; }", "ct_err:modifier:invalid:ann:size:argument_value")
        chkCompile("query s(@min_size(x'') l: json): json { return l; }", "ct_err:modifier:invalid:ann:min_size:argument_value")
    }

    @Test fun testMultipleConflicting() {
        chkCompile("function s(@max_size(1) @size(2, 3) l: byte_array): byte_array { return l; }",
            "ct_err:modifier:invalid:ann:parameter:conflict:size_with_max_size")
        chkCompile("function s(@size(2) @min_size(1) l: text): text { return l; }",
            "ct_err:modifier:invalid:ann:parameter:conflict:size_with_min_size")

        chkCompile("function s(@size(1) @size(2) l: byte_array): byte_array { return l; }",
            "ct_err:modifier:dup:ann:size")
        chkCompile("function s(@min_size(1) @min_size(2) l: text): text { return l; }",
            "ct_err:modifier:dup:ann:min_size")

        chkCompile("operation s(@max_size(1) @size(2, 3) l: byte_array) {}",
            "ct_err:modifier:invalid:ann:parameter:conflict:size_with_max_size")
        chkCompile("operation s(@size(2) @min_size(1) l: text) {}",
            "ct_err:modifier:invalid:ann:parameter:conflict:size_with_min_size")

        chkCompile("operation s(@size(1) @size(2) l: byte_array) {}", "ct_err:modifier:dup:ann:size")
        chkCompile("operation s(@min_size(1) @min_size(2) l: text) {}", "ct_err:modifier:dup:ann:min_size")

        chkCompile("query s(@max_size(1) @size(2, 3) l: byte_array): byte_array { return l; }",
            "ct_err:modifier:invalid:ann:parameter:conflict:size_with_max_size")
        chkCompile("query s(@size(2) @min_size(1) l: text): text { return l; }",
            "ct_err:modifier:invalid:ann:parameter:conflict:size_with_min_size")

        chkCompile("query s(@size(1) @size(2) l: byte_array): byte_array { return l; }", "ct_err:modifier:dup:ann:size")
        chkCompile("query s(@min_size(1) @min_size(2) l: text): text { return l; }", "ct_err:modifier:dup:ann:min_size")
    }

    @Test fun testMultipleNonConflicting() {
        chkCompile("function s(@min_size(1) @max_size(2) l: text): text { return l; }", "OK")
        chkCompile("function s(@max_size(200) @min_size(40) l: byte_array): byte_array { return l; }", "OK")
        chkCompile("operation s(@min_size(1) @max_size(2) l: text) {}", "OK")
        chkCompile("operation s(@max_size(200) @min_size(40) l: byte_array) {}", "OK")
        chkCompile("query s(@min_size(1) @max_size(2) l: text): text { return l; }", "OK")
        chkCompile("query s(@max_size(200) @min_size(40) l: byte_array): byte_array { return l; }", "OK")
    }

    @Test fun testNegativeIllegal() {
        chkCompile("function s(@max_size(-2) l: text): text { return l; }", "ct_err:syntax")
        chkCompile("function s(@size(-1, 2) l: json): json { return l; }", "ct_err:syntax")
        chkCompile("function s(@size(-2) l: byte_array): byte_array { return l; }", "ct_err:syntax")

        chkCompile("operation s(@max_size(-2) l: text) {}", "ct_err:syntax")
        chkCompile("operation s(@size(-1, 2) l: json) {}", "ct_err:syntax")
        chkCompile("operation s(@size(-2) l: byte_array) {}", "ct_err:syntax")

        chkCompile("query s(@max_size(-2) l: text): text { return l; }", "ct_err:syntax")
        chkCompile("query s(@size(-1, 2) l: json): json { return l; }", "ct_err:syntax")
        chkCompile("query s(@size(-2) l: byte_array): byte_array { return l; }", "ct_err:syntax")
    }

    @Test fun testTooLargeIllegal() {
        chkCompile("function s(@min_size(1073741824) l: byte_array): byte_array { return l; }", "OK")
        chkCompile("function s(@min_size(1073741825) l: text): text { return l; }",
            "ct_err:modifier:invalid:ann:min_size:too_large")
        chkCompile("function s(@max_size(1073741825) l: byte_array): byte_array { return l; }",
            "ct_err:modifier:invalid:ann:max_size:too_large")
        chkCompile("operation s(@size(1073741824) l: text) {}", "OK")
        chkCompile("operation s(@size(1, 1073741824) l: byte_array) {}", "OK")
        chkCompile("query s(@size(1073741823, 1073741824) l: text): text { return l; }", "OK")
        chkCompile("query s(@size(1073741824, 1073741825) l: byte_array): byte_array { return l; }",
            "ct_err:modifier:invalid:ann:size:too_large")
    }

    @Test fun testMinGreaterThanMaxIllegal() {
        chkCompile("function s(@min_size(2) @max_size(1) l: byte_array): byte_array { return l; }",
            "ct_err:modifier:invalid:ann:min_size_max_size:min_greater_than_max")
        chkCompile("function s(@max_size(1) @min_size(2) l: byte_array): byte_array { return l; }",
            "ct_err:modifier:invalid:ann:min_size_max_size:min_greater_than_max")
        chkCompile("function s(@size(2, 1) l: text): text { return l; }",
            "ct_err:modifier:invalid:ann:size:min_greater_than_max")

        chkCompile("operation s(@min_size(2) @max_size(1) l: byte_array) {}",
            "ct_err:modifier:invalid:ann:min_size_max_size:min_greater_than_max")
        chkCompile("operation s(@max_size(1) @min_size(2) l: byte_array) {}",
            "ct_err:modifier:invalid:ann:min_size_max_size:min_greater_than_max")
        chkCompile("operation s(@size(2, 1) l: text) {}", "ct_err:modifier:invalid:ann:size:min_greater_than_max")

        chkCompile("query s(@min_size(2) @max_size(1) l: byte_array): byte_array { return l; }",
            "ct_err:modifier:invalid:ann:min_size_max_size:min_greater_than_max")
        chkCompile("query s(@max_size(1) @min_size(2) l: byte_array): byte_array { return l; }",
            "ct_err:modifier:invalid:ann:min_size_max_size:min_greater_than_max")
        chkCompile("query s(@size(2, 1) l: text): text { return l; }",
            "ct_err:modifier:invalid:ann:size:min_greater_than_max")
    }

    @Test fun testBadDefaultIsCompileTimeFailureFunction() {
        chkCompile("function s(@min_size(1) @max_size(2) l: byte_array = x'001122'): byte_array { return l; }",
            "ct_err:function:s:parameter:l:validator:size:too_large")
        chkCompile("function s(@min_size(1) @max_size(2) l: byte_array = x''): byte_array { return l; }",
            "ct_err:function:s:parameter:l:validator:size:too_small")
        chkCompile("function s(@size(1, 2) l: byte_array = x'001122'): byte_array { return l; }",
            "ct_err:function:s:parameter:l:validator:size:too_large")
        chkCompile("function s(@size(1, 2) l: byte_array = x''): byte_array { return l; }",
            "ct_err:function:s:parameter:l:validator:size:too_small")

        chkCompile("function s(@min_size(1) @max_size(2) l: text = 'hello'): text { return l; }",
            "ct_err:function:s:parameter:l:validator:size:too_large")
        chkCompile("function s(@min_size(1) @max_size(2) l: text = ''): text { return l; }",
            "ct_err:function:s:parameter:l:validator:size:too_small")
        chkCompile("function s(@size(1, 2) l: text = 'hello'): text { return l; }",
            "ct_err:function:s:parameter:l:validator:size:too_large")
        chkCompile("function s(@size(1, 2) l: text = ''): text { return l; }",
            "ct_err:function:s:parameter:l:validator:size:too_small")
    }

    @Test fun testBadDefaultIsCompileTimeFailureOperation() {
        chkCompile("operation s(@min_size(1) @max_size(2) l: byte_array = x'001122') {}",
            "ct_err:operation:s:parameter:l:validator:size:too_large")
        chkCompile("operation s(@min_size(1) @max_size(2) l: byte_array = x'') {}",
            "ct_err:operation:s:parameter:l:validator:size:too_small")
        chkCompile("operation s(@size(1, 2) l: byte_array = x'001122') {}",
            "ct_err:operation:s:parameter:l:validator:size:too_large")
        chkCompile("operation s(@size(1, 2) l: byte_array = x'') {}",
            "ct_err:operation:s:parameter:l:validator:size:too_small")

        chkCompile("operation s(@min_size(1) @max_size(2) l: text = 'hello') {}",
            "ct_err:operation:s:parameter:l:validator:size:too_large")
        chkCompile("operation s(@min_size(1) @max_size(2) l: text = '') {}",
            "ct_err:operation:s:parameter:l:validator:size:too_small")
        chkCompile("operation s(@size(1, 2) l: text = 'hello') {}",
            "ct_err:operation:s:parameter:l:validator:size:too_large")
        chkCompile("operation s(@size(1, 2) l: text = '') {}",
            "ct_err:operation:s:parameter:l:validator:size:too_small")
    }

    @Test fun testBadDefaultIsCompileTimeFailureQuery() {
        chkCompile("query s(@min_size(1) @max_size(2) l: byte_array = x'001122'): byte_array { return l; }",
            "ct_err:query:s:parameter:l:validator:size:too_large")
        chkCompile("query s(@min_size(1) @max_size(2) l: byte_array = x''): byte_array { return l; }",
            "ct_err:query:s:parameter:l:validator:size:too_small")
        chkCompile("query s(@size(1, 2) l: byte_array = x'001122'): byte_array { return l; }",
            "ct_err:query:s:parameter:l:validator:size:too_large")
        chkCompile("query s(@size(1, 2) l: byte_array = x''): byte_array { return l; }",
            "ct_err:query:s:parameter:l:validator:size:too_small")

        chkCompile("query s(@min_size(1) @max_size(2) l: text = 'hello'): text { return l; }",
            "ct_err:query:s:parameter:l:validator:size:too_large")
        chkCompile("query s(@min_size(1) @max_size(2) l: text = ''): text { return l; }",
            "ct_err:query:s:parameter:l:validator:size:too_small")
        chkCompile("query s(@size(1, 2) l: text = 'hello'): text { return l; }",
            "ct_err:query:s:parameter:l:validator:size:too_large")
        chkCompile("query s(@size(1, 2) l: text = ''): text { return l; }",
            "ct_err:query:s:parameter:l:validator:size:too_small")
    }

    @Test fun testFunctionCallByteArray() {
        def("function s(@size(1, 3) l: byte_array): byte_array { return l; }")
        def("function t(@min_size(2) l: byte_array): byte_array { return l; }")
        def("function u(@max_size(2) l: byte_array): byte_array { return l; }")

        chk("s(x'')", "rt_err:function:s:parameter:l:validator:size:too_small")
        chk("s(x'ABCD')", "byte_array[abcd]")
        chk("s(x'ABCDEF01')", "rt_err:function:s:parameter:l:validator:size:too_large")

        chk("t(x'62')", "rt_err:function:t:parameter:l:validator:size:too_small")
        chk("t(x'CAFEC0FFEE')", "byte_array[cafec0ffee]")

        chk("u(x'CAFEC0FFEE')", "rt_err:function:u:parameter:l:validator:size:too_large")
        chk("u(x'CAFE')", "byte_array[cafe]")
    }

    @Test fun testFunctionCallText() {
        def("function s(@size(1, 3) l: text): text { return l; }")
        def("function t(@min_size(2) l: text): text { return l; }")
        def("function u(@max_size(2) l: text): text { return l; }")

        chk("s('')", "rt_err:function:s:parameter:l:validator:size:too_small")
        chk("s('hi')", "text[hi]")
        chk("s('hello')", "rt_err:function:s:parameter:l:validator:size:too_large")

        chk("t('')", "rt_err:function:t:parameter:l:validator:size:too_small")
        chk("t('hi')", "text[hi]")

        chk("u('hello')", "rt_err:function:u:parameter:l:validator:size:too_large")
        chk("u('hi')", "text[hi]")
    }

    @Test fun testQueryCallByteArray() {
        def("query s(@size(1, 3) l: byte_array): byte_array { return l; }")
        def("query t(@min_size(2) l: byte_array): byte_array { return l; }")
        def("query u(@max_size(2) l: byte_array): byte_array { return l; }")

        chk("s(x'')", "rt_err:query:s:parameter:l:validator:size:too_small")
        chk("s(x'ABCD')", "byte_array[abcd]")
        chk("s(x'ABCDEF01')", "rt_err:query:s:parameter:l:validator:size:too_large")

        chk("t(x'62')", "rt_err:query:t:parameter:l:validator:size:too_small")
        chk("t(x'CAFEC0FFEE')", "byte_array[cafec0ffee]")

        chk("u(x'CAFEC0FFEE')", "rt_err:query:u:parameter:l:validator:size:too_large")
        chk("u(x'CAFE')", "byte_array[cafe]")
    }

    @Test fun testQueryCallText() {
        def("query s(@size(1, 3) l: text): text { return l; }")
        def("query t(@min_size(2) l: text): text { return l; }")
        def("query u(@max_size(2) l: text): text { return l; }")

        chk("s('')", "rt_err:query:s:parameter:l:validator:size:too_small")
        chk("s('hi')", "text[hi]")
        chk("s('hello')", "rt_err:query:s:parameter:l:validator:size:too_large")

        chk("t('')", "rt_err:query:t:parameter:l:validator:size:too_small")
        chk("t('hi')", "text[hi]")

        chk("u('hello')", "rt_err:query:u:parameter:l:validator:size:too_large")
        chk("u('hi')", "text[hi]")
    }

    @Test fun testStructOperationByteArray() {
        def("operation s(@size(1, 3) l: byte_array) {}")
        def("operation t(@min_size(2) l: byte_array) {}")
        def("operation u(@max_size(2) l: byte_array) {}")

        chk("struct<s>(x'')", "rt_err:operation:s:parameter:l:validator:size:too_small")
        chk("struct<s>(x'ABCD')", "struct<s>[l=byte_array[abcd]]")
        chk("struct<s>(x'ABCDEF01')", "rt_err:operation:s:parameter:l:validator:size:too_large")

        chk("struct<t>(x'62')", "rt_err:operation:t:parameter:l:validator:size:too_small")
        chk("struct<t>(x'CAFEC0FFEE')", "struct<t>[l=byte_array[cafec0ffee]]")

        chk("struct<u>(x'CAFEC0FFEE')", "rt_err:operation:u:parameter:l:validator:size:too_large")
        chk("struct<u>(x'CAFE')", "struct<u>[l=byte_array[cafe]]")
    }

    @Test fun testStructOperationText() {
        def("operation s(@size(1, 3) l: text) {}")
        def("operation t(@min_size(2) l: text) {}")
        def("operation u(@max_size(2) l: text) {}")

        chk("struct<s>('')", "rt_err:operation:s:parameter:l:validator:size:too_small")
        chk("struct<s>('hi')", "struct<s>[l=text[hi]]")
        chk("struct<s>('hello')", "rt_err:operation:s:parameter:l:validator:size:too_large")

        chk("struct<t>('')", "rt_err:operation:t:parameter:l:validator:size:too_small")
        chk("struct<t>('hi')", "struct<t>[l=text[hi]]")

        chk("struct<u>('hello')", "rt_err:operation:u:parameter:l:validator:size:too_large")
        chk("struct<u>('hi')", "struct<u>[l=text[hi]]")
    }
}
