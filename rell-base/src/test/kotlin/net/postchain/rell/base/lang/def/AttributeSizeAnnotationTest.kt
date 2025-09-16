package net.postchain.rell.base.lang.def

import net.postchain.rell.base.testutils.BaseRellTest
import org.junit.jupiter.api.Disabled
import kotlin.test.Test

internal class AttributeSizeAnnotationTest: BaseRellTest() {
    @Test fun testInvalidOnNonStruct() {
        chkCompile("entity s { @size(1) l: byte_array; }", "ct_err:modifier:invalid:ann:size:non_struct")
        chkCompile("entity s { @size(1, 5) l: text; }", "ct_err:modifier:invalid:ann:size:non_struct")
        chkCompile("entity s { @min_size(1) l: byte_array; }", "ct_err:modifier:invalid:ann:min_size:non_struct")
        chkCompile("object s { @size(2) l: text = 'hi'; }", "ct_err:modifier:invalid:ann:size:non_struct")
        chkCompile("object s { @size(1, 5) l: byte_array = x'AB'; }", "ct_err:modifier:invalid:ann:size:non_struct")
        chkCompile("object s { @min_size(1) l: byte_array = x'0011'; }",
            "ct_err:modifier:invalid:ann:min_size:non_struct")
        chkCompile("object s { @max_size(5) l: text = ''; }", "ct_err:modifier:invalid:ann:max_size:non_struct")
    }

    @Test fun testInvalidOnUnsupportedTypes() {
        chkCompile("struct s { @size(1, 5) l: boolean; }", "ct_err:modifier:invalid:ann:size:invalid_type")
        chkCompile("struct s { @size(1, 5) l: integer; }", "ct_err:modifier:invalid:ann:size:invalid_type")
        chkCompile("struct s { @size(1, 5) l: decimal; }", "ct_err:modifier:invalid:ann:size:invalid_type")
        chkCompile("struct s { @size(1, 5) l: big_integer; }", "ct_err:modifier:invalid:ann:size:invalid_type")
        chkCompile("struct s { @min_size(1) l: boolean; }", "ct_err:modifier:invalid:ann:min_size:invalid_type")
        chkCompile("struct s { @min_size(1) l: integer; }", "ct_err:modifier:invalid:ann:min_size:invalid_type")
        chkCompile("struct s { @min_size(1) l: decimal; }", "ct_err:modifier:invalid:ann:min_size:invalid_type")
        chkCompile("struct s { @min_size(1) l: big_integer; }", "ct_err:modifier:invalid:ann:min_size:invalid_type")
        chkCompile("struct s { @max_size(5) l: boolean; }", "ct_err:modifier:invalid:ann:max_size:invalid_type")
        chkCompile("struct s { @max_size(5) l: integer; }", "ct_err:modifier:invalid:ann:max_size:invalid_type")
        chkCompile("struct s { @max_size(5) l: decimal; }", "ct_err:modifier:invalid:ann:max_size:invalid_type")
        chkCompile("struct s { @max_size(5) l: big_integer; }", "ct_err:modifier:invalid:ann:max_size:invalid_type")
    }

    @Test fun testInvalidOnDisabledTypes() {
        chkCompile("struct s { @size(1, 5) l: list<integer>; }", "ct_err:modifier:invalid:ann:size:invalid_type")
        chkCompile("struct s { @size(1, 5) l: set<big_integer>; }", "ct_err:modifier:invalid:ann:size:invalid_type")
        chkCompile("struct s { @size(1, 5) l: map<text, boolean>; }", "ct_err:modifier:invalid:ann:size:invalid_type")
        chkCompile("struct s { @size(1, 5) l: json; }", "ct_err:modifier:invalid:ann:size:invalid_type")
        chkCompile("struct s { @min_size(1) l: list<text>; }", "ct_err:modifier:invalid:ann:min_size:invalid_type")
        chkCompile("struct s { @min_size(1) l: set<json>; }", "ct_err:modifier:invalid:ann:min_size:invalid_type")
        chkCompile("struct s { @min_size(1) l: map<integer, decimal>; }",
            "ct_err:modifier:invalid:ann:min_size:invalid_type")
        chkCompile("struct s { @min_size(1) l: json; }", "ct_err:modifier:invalid:ann:min_size:invalid_type")
        chkCompile("struct s { @max_size(5) l: list<decimal>; }", "ct_err:modifier:invalid:ann:max_size:invalid_type")
        chkCompile("struct s { @max_size(5) l: set<text>; }", "ct_err:modifier:invalid:ann:max_size:invalid_type")
        chkCompile("struct s { @max_size(5) l: map<json, decimal>; }",
            "ct_err:modifier:invalid:ann:max_size:invalid_type")
        chkCompile("struct s { @max_size(5) l: json; }", "ct_err:modifier:invalid:ann:max_size:invalid_type")
    }

    @Test fun testInvalidArgCountsPrimitive() {
        chkCompile("struct s { @min_size(1, 5) l: byte_array; }", "ct_err:ann:min_size:arg_count:2")
        chkCompile("struct s { @max_size(1, 5) l: text; }", "ct_err:ann:max_size:arg_count:2")
        chkCompile("struct s { @size(1, 5, 9) l: json; }", "ct_err:ann:size:arg_count:3")
    }

    @Disabled
    @Test fun testInvalidArgCountsCollection() {
        chkCompile("struct s { @size() l: list<integer>; }", "ct_err:ann:size:arg_count:0")
        chkCompile("struct s { @min_size() l: set<text>; }", "ct_err:ann:min_size:arg_count:0")
        chkCompile("struct s { @max_size() l: map<integer, boolean>; }", "ct_err:ann:max_size:arg_count:0")
        chkCompile("struct s { @min_size(1, 5, 9) l: list<integer>; }", "ct_err:ann:min_size:arg_count:3")
        chkCompile("struct s { @max_size(1, 5, 9) l: set<integer>; }", "ct_err:ann:max_size:arg_count:3")
    }

    @Test fun testInvalidArgValuesPrimitive() {
        chkCompile("struct s { @max_size(false) l: byte_array; }",
            "ct_err:modifier:invalid:ann:max_size:argument_value")
        chkCompile("struct s { @size(null, 'hello') l: text; }", "ct_err:modifier:invalid:ann:size:argument_value")
        chkCompile("struct s { @min_size(x'') l: json; }", "ct_err:modifier:invalid:ann:min_size:argument_value")
    }

    @Disabled
    @Test fun testInvalidArgValuesCollection() {
        chkCompile("struct s { @min_size(true) l: map<integer, boolean>; }",
            "ct_err:modifier:invalid:ann:min_size:argument_value")
        chkCompile("struct s { @max_size(3L) l: list<decimal>; }",
            "ct_err:modifier:invalid:ann:max_size:argument_value")
        chkCompile("struct s { @max_size(3.001) l: set<big_integer>; }",
            "ct_err:modifier:invalid:ann:max_size:argument_value")
    }

    @Test fun testMultipleConflictingPrimitive() {
        chkCompile("struct s { @max_size(1) @size(2, 3) l: byte_array; }",
            "ct_err:modifier:invalid:ann:attribute:conflict:size_with_max_size")
        chkCompile("struct s { @size(2) @min_size(1) l: text; }",
            "ct_err:modifier:invalid:ann:attribute:conflict:size_with_min_size")

        chkCompile("struct s { @size(1) @size(2) l: byte_array; }", "ct_err:modifier:dup:ann:size")
        chkCompile("struct s { @min_size(1) @min_size(2) l: text; }", "ct_err:modifier:dup:ann:min_size")
    }

    @Disabled
    @Test fun testMultipleConflictingJson() {
        chkCompile("struct s { @size(2, 3) @min_size(1) l: json; }",
            "ct_err:modifier:invalid:ann:attribute:conflict:size_with_min_size")

        chkCompile("struct s { @max_size(1) @max_size(2) l: json; }", "ct_err:modifier:dup:ann:max_size")
    }

    @Disabled
    @Test fun testMultipleConflictingCollection() {
        chkCompile("struct s { @min_size(1) @size(2) l: list<integer>; }",
            "ct_err:modifier:invalid:ann:attribute:conflict:size_with_min_size")
        chkCompile("struct s { @min_size(1) @size(2, 3) l: set<boolean>; }",
            "ct_err:modifier:invalid:ann:attribute:conflict:size_with_min_size")
        chkCompile("struct s { @max_size(1) @size(2) l: map<decimal, text>; }",
            "ct_err:modifier:invalid:ann:attribute:conflict:size_with_max_size")
        chkCompile("struct s { @size(2) @max_size(1) l: list<decimal>; }",
            "ct_err:modifier:invalid:ann:attribute:conflict:size_with_max_size")
        chkCompile("struct s { @size(2, 3) @max_size(1) l: set<big_integer>; }",
            "ct_err:modifier:invalid:ann:attribute:conflict:size_with_max_size")

        chkCompile("struct s { @size(1) @size(2) l: list<integer>; }", "ct_err:modifier:dup:ann:size")
        chkCompile("struct s { @min_size(1) @min_size(2) l: list<integer>; }", "ct_err:modifier:dup:ann:min_size")
        chkCompile("struct s { @max_size(1) @max_size(2) l: list<integer>; }", "ct_err:modifier:dup:ann:max_size")
    }

    @Test fun testMultipleNonConflictingPrimitive() {
        chkCompile("struct s { @min_size(1) @max_size(2) l: text; }", "OK")
        chkCompile("struct s { @max_size(200) @min_size(40) l: byte_array; }", "OK")
    }

    @Disabled
    @Test fun testMultipleNonConflictingJson() {
        chkCompile("struct s { @max_size(2) @min_size(1) l: json; }", "OK")
    }

    @Disabled
    @Test fun testMultipleNonConflictingCollection() {
        chkCompile("struct s { @min_size(1) @max_size(2) l: list<integer>; }", "OK")
        chkCompile("struct s { @max_size(1000) @min_size(0) l: set<decimal>; }", "OK")
        chkCompile("struct s { @min_size(2) @max_size(9999999) l: map<text, integer>; }", "OK")
    }

    @Test fun testNegativeIllegalPrimitive() {
        chkCompile("struct s { @max_size(-2) l: text; }", "ct_err:syntax")
        chkCompile("struct s { @size(-1, 2) l: json; }", "ct_err:syntax")
        chkCompile("struct s { @size(-2) l: byte_array; }", "ct_err:syntax")
    }

    @Disabled
    @Test fun testNegativeIllegalCollection() {
        chkCompile("struct s { @min_size(-1) l: set<boolean>; }", "ct_err:syntax")
        chkCompile("struct s { @size(-3, -1) l: list<integer>; }", "ct_err:syntax")
        chkCompile("struct s { @size(2, -17) l: map<boolean, boolean>; }", "ct_err:syntax")
    }

    @Test fun testTooLargeIllegalPrimitive() {
        chkCompile("struct s { @min_size(1073741824) l: byte_array; }", "OK")
        chkCompile("struct s { @min_size(1073741825) l: text; }", "ct_err:modifier:invalid:ann:min_size:too_large")
        chkCompile("struct s { @max_size(1073741825) l: byte_array; }",
            "ct_err:modifier:invalid:ann:max_size:too_large")
        chkCompile("struct s { @size(1073741824) l: text; }", "OK")

        chkCompile("struct s { @size(1, 1073741824) l: byte_array; }", "OK")

        chkCompile("struct s { @size(1073741823, 1073741824) l: text; }", "OK")
        chkCompile("struct s { @size(1073741824, 1073741825) l: byte_array; }",
            "ct_err:modifier:invalid:ann:size:too_large")
    }

    @Disabled
    @Test fun testTooLargeIllegalJson() {
        chkCompile("struct s { @max_size(1073741824) l: json; }", "OK")
        chkCompile("struct s { @size(1073741825) l: json; }", "ct_err:modifier:invalid:ann:size:too_large")

        chkCompile("struct s { @size(1, 1073741825) l: json; }", "ct_err:modifier:invalid:ann:size:too_large")

        chkCompile("struct s { @size(1073741825, 1073741826) l: json; }",
            "ct_err:[modifier:invalid:ann:size:too_large][modifier:invalid:ann:size:too_large]")
    }

    @Disabled
    @Test fun testTooLargeIllegalCollection() {
        chkCompile("struct s { @min_size(1073741824) l: list<integer>; }", "OK")
        chkCompile("struct s { @min_size(1073741825) l: set<big_integer>; }",
            "ct_err:modifier:invalid:ann:min_size:too_large")
        chkCompile("struct s { @max_size(1073741824) l: map<text, json>; }", "OK")
        chkCompile("struct s { @max_size(1073741825) l: list<set<boolean>>; }",
            "ct_err:modifier:invalid:ann:max_size:too_large")
        chkCompile("struct s { @size(1073741824) l: set<decimal>; }", "OK")
        chkCompile("struct s { @size(1073741825) l: map<json, integer>; }",
            "ct_err:modifier:invalid:ann:size:too_large")

        chkCompile("struct s { @size(1, 1073741824) l: set<boolean>; }", "OK")
        chkCompile("struct s { @size(1, 1073741825) l: map<decimal, text>; }",
            "ct_err:modifier:invalid:ann:size:too_large")

        chkCompile("struct s { @size(1073741823, 1073741824) l: list<integer>; }", "OK")
        chkCompile("struct s { @size(1073741824, 1073741825) l: set<text>; }",
            "ct_err:modifier:invalid:ann:size:too_large")
        chkCompile("struct s { @size(1073741825, 1073741826) l: map<text, text>; }", "ct_err:[modifier:invalid:ann:size:too_large][modifier:invalid:ann:size:too_large]")
    }

    @Test fun testMinGreaterThanMaxIllegalPrimitive() {
        chkCompile("struct s { @min_size(2) @max_size(1) l: byte_array; }",
            "ct_err:modifier:invalid:ann:min_size_max_size:min_greater_than_max")
        chkCompile("struct s { @max_size(1) @min_size(2) l: byte_array; }",
            "ct_err:modifier:invalid:ann:min_size_max_size:min_greater_than_max")
        chkCompile("struct s { @size(2, 1) l: text; }", "ct_err:modifier:invalid:ann:size:min_greater_than_max")
    }

    @Disabled
    @Test fun testMinGreaterThanMaxIllegalJson() {
        chkCompile("struct s { @max_size(1) @min_size(2) l: json; }",
            "ct_err:modifier:invalid:ann:min_size_max_size:min_greater_than_max")
    }

    @Disabled
    @Test fun testMinGreaterThanMaxIllegalCollection() {
        chkCompile("struct s { @min_size(2) @max_size(1) l: map<text, text>; }",
            "ct_err:modifier:invalid:ann:min_size_max_size:min_greater_than_max")
        chkCompile("struct s { @max_size(1) @min_size(2) l: set<text>; }",
            "ct_err:modifier:invalid:ann:min_size_max_size:min_greater_than_max")
        chkCompile("struct s { @size(2, 1) l: list<integer>; }",
            "ct_err:modifier:invalid:ann:size:min_greater_than_max")
    }

    @Test fun testBadDefaultIsCompileTimeFailurePrimitive() {
        chkCompile("struct s { @min_size(1) @max_size(2) l: byte_array = x'001122'; }",
            "ct_err:struct:s:attribute:l:validator:size:too_large")
        chkCompile("struct s { @min_size(1) @max_size(2) l: byte_array = x''; }",
            "ct_err:struct:s:attribute:l:validator:size:too_small")
        chkCompile("struct s { @size(1, 2) l: byte_array = x'001122'; }",
            "ct_err:struct:s:attribute:l:validator:size:too_large")
        chkCompile("struct s { @size(1, 2) l: byte_array = x''; }",
            "ct_err:struct:s:attribute:l:validator:size:too_small")

        chkCompile("struct s { @min_size(1) @max_size(2) l: text = 'hello'; }",
            "ct_err:struct:s:attribute:l:validator:size:too_large")
        chkCompile("struct s { @min_size(1) @max_size(2) l: text = ''; }",
            "ct_err:struct:s:attribute:l:validator:size:too_small")
        chkCompile("struct s { @size(1, 2) l: text = 'hello'; }",
            "ct_err:struct:s:attribute:l:validator:size:too_large")
        chkCompile("struct s { @size(1, 2) l: text = ''; }",
            "ct_err:struct:s:attribute:l:validator:size:too_small")
    }

    @Disabled
    @Test fun testBadDefaultsRuntimeJson() {
        def("struct a { @min_size(3) @max_size(6) l: json = json('[10, 11, 12, 13, 14, 15, 16]'); }")
        def("struct b { @min_size(3) @max_size(6) l: json = json('[]'); }")
        def("struct c { @size(3, 6) l: json = json('[10, 11, 12, 13, 14, 15, 16]'); }")
        def("struct d { @size(3, 6) l: json = json('[]'); }")
        chk("a()", "rt_err:struct:a:attribute:l:validator:size:too_large")
        chk("b()", "rt_err:struct:b:attribute:l:validator:size:too_small")
        chk("c()", "rt_err:struct:c:attribute:l:validator:size:too_large")
        chk("d()", "rt_err:struct:d:attribute:l:validator:size:too_small")
    }

    @Disabled
    @Test fun testBadDefaultIsCompileTimeFailureCollection() {
        chkCompile("struct s { @min_size(1) @max_size(2) l: list<integer> = [1, 2, 3]; }",
            "ct_err:struct:s:attribute:l:validator:size:too_large")
        chkCompile("struct s { @min_size(1) @max_size(2) l: list<integer> = []; }",
            "ct_err:struct:s:attribute:l:validator:size:too_small")
        chkCompile("struct s { @size(1, 2) l: list<integer> = [1, 2, 3]; }",
            "ct_err:struct:s:attribute:l:validator:size:too_large")
        chkCompile("struct s { @size(1, 2) l: list<integer> = []; }",
            "ct_err:struct:s:attribute:l:validator:size:too_small")

        chkCompile("struct s { @min_size(1) @max_size(2) l: map<integer, text> = [1:'a', 2:'b', 3:'c']; }",
            "ct_err:struct:s:attribute:l:validator:size:too_large")
        chkCompile("struct s { @min_size(1) @max_size(2) l: map<integer, text> = [:]; }",
            "ct_err:struct:s:attribute:l:validator:size:too_small")
        chkCompile("struct s { @size(1, 2) l: map<integer, text> = [1:'a', 2:'b', 3:'c']; }",
            "ct_err:struct:s:attribute:l:validator:size:too_large")
        chkCompile("struct s { @size(1, 2) l: map<integer, text> = [:]; }",
            "ct_err:struct:s:attribute:l:validator:size:too_small")
    }

    @Disabled
    @Test fun testBadDefaultsRuntimeSet() {
        def("struct a { @min_size(1) @max_size(2) l: set<integer> = set([1, 2, 3]); }")
        def("struct b { @min_size(1) @max_size(2) l: set<integer> = set<integer>([]); }")
        def("struct c { @size(1, 2) l: set<integer> = set([1, 2, 3]); }")
        def("struct d { @size(1, 2) l: set<integer> = set<integer>([]); }")
        chk("a()", "rt_err:struct:a:attribute:l:validator:size:too_large")
        chk("b()", "rt_err:struct:b:attribute:l:validator:size:too_small")
        chk("c()", "rt_err:struct:c:attribute:l:validator:size:too_large")
        chk("d()", "rt_err:struct:d:attribute:l:validator:size:too_small")

    }

    @Disabled
    @Test fun testStructCreationList() {
        def("struct s { @size(1, 3) l: list<integer>; }")
        def("struct t { @min_size(2) l: list<boolean>; }")
        def("struct u { @max_size(2) l: list<text>; }")

        chk("s([])", "rt_err:struct:s:attribute:l:validator:size:too_small")
        chk("s([0, 0])", "s[l=list<integer>[int[0],int[0]]]")
        chk("s([0, 0, 1, 0])", "rt_err:struct:s:attribute:l:validator:size:too_large")

        chk("t([true])", "rt_err:struct:t:attribute:l:validator:size:too_small")
        chk("t([true, false, true])", "t[l=list<boolean>[boolean[true],boolean[false],boolean[true]]]")

        chk("u(['', 'abc', 'hi'])", "rt_err:struct:u:attribute:l:validator:size:too_large")
        chk("u(['xyz'])", "u[l=list<text>[text[xyz]]]")
    }

    @Disabled
    @Test fun testStructUpdateList() {
        def("struct s { @size(1, 3) mutable l: list<integer>; }")
        def("struct t { @min_size(2) mutable l: list<boolean>; }")
        def("struct u { @max_size(2) mutable l: list<text>; }")

        chkEx("{ val x = s([0, 0]); x.l = []; return x; }", "rt_err:struct:s:attribute:l:validator:size:too_small")
        chkEx("{ val x = s([0, 0]); x.l = [0, 0, 0, 0]; return x; }",
            "rt_err:struct:s:attribute:l:validator:size:too_large")
        chkEx("{ val x = s([0, 0]); x.l = [1, 1]; return x; }", "s[l=list<integer>[int[1],int[1]]]")

        chkEx("{ val x = t([true, false]); x.l = []; return x; }",
            "rt_err:struct:t:attribute:l:validator:size:too_small")
        chkEx("{ val x = t([true, false]); x.l = [false, false]; return x; }",
            "t[l=list<boolean>[boolean[false],boolean[false]]]")

        chkEx("{ val x = u(['a', 'b']); x.l = ['a', 'b', 'c', 'd']; return x; }",
            "rt_err:struct:u:attribute:l:validator:size:too_large")
        chkEx("{ val x = u(['a', 'b']); x.l = ['x', 'y']; return x; }", "u[l=list<text>[text[x],text[y]]]")
    }

    @Disabled
    @Test fun testStructFromGtvList() {
        def("struct s { @size(1, 3) l: list<integer>; }")
        def("struct t { @min_size(2) l: list<boolean>; }")
        def("struct u { @max_size(2) l: list<text>; }")

        chk("s.from_gtv(gtv.from_json('[[]]'))", "rt_err:struct:s:attribute:l:validator:size:too_small")
        chk("s.from_gtv(gtv.from_json('[[1, 2, 3, 4]]'))", "rt_err:struct:s:attribute:l:validator:size:too_large")
        chk("s.from_gtv(gtv.from_json('[[1,2]]'))", "s[l=list<integer>[int[1],int[2]]]")

        chk("t.from_gtv(gtv.from_json('[[]]'))", "rt_err:struct:t:attribute:l:validator:size:too_small")
        chk("t.from_gtv(gtv.from_json('[[true,false]]'))", "t[l=list<boolean>[boolean[true],boolean[false]]]")

        chk("u.from_gtv(gtv.from_json('[[\"a\", \"b\", \"c\", \"d\"]]'))",
            "rt_err:struct:u:attribute:l:validator:size:too_large")
        chk("u.from_gtv(gtv.from_json('[[\"a\", \"b\"]]'))", "u[l=list<text>[text[a],text[b]]]")
    }

    @Disabled
    @Test fun testStructFromBytesList() {
        def("struct s { @size(1, 3) l: list<integer>; }")
        def("struct t { @min_size(2) l: list<boolean>; }")
        def("struct u { @max_size(2) l: list<text>; }")

        def("struct s2 { l: list<integer>; }")
        def("struct t2 { l: list<boolean>; }")
        def("struct u2 { l: list<text>; }")

        chk("s.from_bytes(s2([]).to_bytes())", "rt_err:struct:s:attribute:l:validator:size:too_small")
        chk("s.from_bytes(s2([1, 2, 3, 4]).to_bytes())", "rt_err:struct:s:attribute:l:validator:size:too_large")
        chk("s.from_bytes(s2([1, 2]).to_bytes())", "s[l=list<integer>[int[1],int[2]]]")

        chk("t.from_bytes(t2([]).to_bytes())", "rt_err:struct:t:attribute:l:validator:size:too_small")
        chk("t.from_bytes(t2([true, false]).to_bytes())", "t[l=list<boolean>[boolean[true],boolean[false]]]")

        chk("u.from_bytes(u2(['a', 'b', 'c', 'd']).to_bytes())", "rt_err:struct:u:attribute:l:validator:size:too_large")
        chk("u.from_bytes(u2(['a', 'b']).to_bytes())", "u[l=list<text>[text[a],text[b]]]")
    }

    @Disabled
    @Test fun testStructCreationSet() {
        def("struct s { @size(1, 3) l: set<integer>; }")
        def("struct t { @min_size(2) l: set<boolean>; }")
        def("struct u { @max_size(2) l: set<text>; }")

        chk("s(set<integer>([]))", "rt_err:struct:s:attribute:l:validator:size:too_small")
        chk("s(set([0, 1]))", "s[l=set<integer>[int[0],int[1]]]")
        chk("s(set([0, 1, 19, 2500]))", "rt_err:struct:s:attribute:l:validator:size:too_large")

        chk("t(set([true]))", "rt_err:struct:t:attribute:l:validator:size:too_small")
        chk("t(set([true, false]))", "t[l=set<boolean>[boolean[true],boolean[false]]]")

        chk("u(set(['', 'abc', 'hi']))", "rt_err:struct:u:attribute:l:validator:size:too_large")
        chk("u(set(['xyz']))", "u[l=set<text>[text[xyz]]]")
    }

    @Disabled
    @Test fun testStructUpdateSet() {
        def("struct s { @size(1, 3) mutable l: set<integer>; }")
        def("struct t { @min_size(2) mutable l: set<big_integer>; }")
        def("struct u { @max_size(2) mutable l: set<text>; }")

        chkEx("{ val x = s(set([0, 0])); x.l = set<integer>([]); return x; }",
            "rt_err:struct:s:attribute:l:validator:size:too_small")
        chkEx("{ val x = s(set([0, 0])); x.l = set([0, 1, 2, 3]); return x; }",
            "rt_err:struct:s:attribute:l:validator:size:too_large")
        chkEx("{ val x = s(set([0, 0])); x.l = set([1, 9001]); return x; }", "s[l=set<integer>[int[1],int[9001]]]")

        chkEx("{ val x = t(set([0L, 64L])); x.l = set<big_integer>([]); return x; }",
            "rt_err:struct:t:attribute:l:validator:size:too_small")
        chkEx("{ val x = t(set([0L, 64L])); x.l = set([-10L, 7L]); return x; }",
            "t[l=set<big_integer>[bigint[-10],bigint[7]]]")

        chkEx("{ val x = u(set(['a', 'b'])); x.l = set(['a', 'b', 'c', 'd']); return x; }",
            "rt_err:struct:u:attribute:l:validator:size:too_large")
        chkEx("{ val x = u(set(['a', 'b'])); x.l = set(['x', 'y']); return x; }", "u[l=set<text>[text[x],text[y]]]")
    }

    @Disabled
    @Test fun testStructFromGtvSet() {
        def("struct s { @size(1, 3) l: set<integer>; }")
        def("struct t { @min_size(2) l: set<boolean>; }")
        def("struct u { @max_size(2) l: set<text>; }")

        chk("s.from_gtv(gtv.from_json('[[]]'))", "rt_err:struct:s:attribute:l:validator:size:too_small")
        chk("s.from_gtv(gtv.from_json('[[1, 2, 3, 4]]'))", "rt_err:struct:s:attribute:l:validator:size:too_large")
        chk("s.from_gtv(gtv.from_json('[[1,2]]'))", "s[l=set<integer>[int[1],int[2]]]")

        chk("t.from_gtv(gtv.from_json('[[]]'))", "rt_err:struct:t:attribute:l:validator:size:too_small")
        chk("t.from_gtv(gtv.from_json('[[true, false]]'))", "t[l=set<boolean>[boolean[true],boolean[false]]]")

        chk("u.from_gtv(gtv.from_json('[[\"a\", \"b\", \"c\", \"d\"]]'))",
            "rt_err:struct:u:attribute:l:validator:size:too_large")
        chk("u.from_gtv(gtv.from_json('[[\"a\", \"b\"]]'))", "u[l=set<text>[text[a],text[b]]]")
    }

    @Disabled
    @Test fun testStructFromBytesSet() {
        def("struct s { @size(1, 3) l: set<integer>; }")
        def("struct t { @min_size(2) l: set<big_integer>; }")
        def("struct u { @max_size(2) l: set<text>; }")

        def("struct s2 { l: set<integer>; }")
        def("struct t2 { l: set<big_integer>; }")
        def("struct u2 { l: set<text>; }")

        chk("s.from_bytes(s2(set<integer>([])).to_bytes())", "rt_err:struct:s:attribute:l:validator:size:too_small")
        chk("s.from_bytes(s2(set([1, 2, 3, 4])).to_bytes())", "rt_err:struct:s:attribute:l:validator:size:too_large")
        chk("s.from_bytes(s2(set([1, 2])).to_bytes())", "s[l=set<integer>[int[1],int[2]]]")

        chk("t.from_bytes(t2(set<big_integer>([])).to_bytes())", "rt_err:struct:t:attribute:l:validator:size:too_small")
        chk("t.from_bytes(t2(set([0L, -3L])).to_bytes())", "t[l=set<big_integer>[bigint[0],bigint[-3]]]")

        chk("u.from_bytes(u2(set(['a', 'b', 'c', 'd'])).to_bytes())",
            "rt_err:struct:u:attribute:l:validator:size:too_large")
        chk("u.from_bytes(u2(set(['a', 'b'])).to_bytes())", "u[l=set<text>[text[a],text[b]]]")
    }

    @Disabled
    @Test fun testStructCreationMap() {
        def("struct s { @size(1, 3) l: map<integer, text>; }")
        def("struct t { @min_size(2) l: map<big_integer, boolean>; }")
        def("struct u { @max_size(2) l: map<text, decimal>; }")

        chk("s(map<integer, text>([:]))", "rt_err:struct:s:attribute:l:validator:size:too_small")
        chk("s(map([0: 'abc', 1: 'hoho']))", "s[l=map<integer,text>[int[0]=text[abc],int[1]=text[hoho]]]")
        chk("s(map([0: 'abc', 1: 'hoho', 7: 'cheese', 25: 'square']))",
            "rt_err:struct:s:attribute:l:validator:size:too_large")

        chk("t(map([0L: true]))", "rt_err:struct:t:attribute:l:validator:size:too_small")
        chk("t(map([0L: true, 2347612376L: false]))",
            "t[l=map<big_integer,boolean>[bigint[0]=boolean[true],bigint[2347612376]=boolean[false]]]")

        chk("u(map(['': 0.0, 'abc': 0.1134, 'hi': 3.1415]))", "rt_err:struct:u:attribute:l:validator:size:too_large")
        chk("u(map(['xyz': 6.66666666667]))", "u[l=map<text,decimal>[text[xyz]=dec[6.66666666667]]]")
    }

    @Disabled
    @Test fun testStructUpdateMap() {
        def("struct s { @size(1, 3) mutable l: map<integer, integer>; }")
        def("struct t { @min_size(2) mutable l: map<big_integer, boolean>; }")
        def("struct u { @max_size(2) mutable l: map<integer, text>; }")

        chkEx("{ val x = s([0: 1, 2: 3]); x.l = [:]; return x; }",
            "rt_err:struct:s:attribute:l:validator:size:too_small")
        chkEx("{ val x = s([0: 1, 2: 3]); x.l = [0: 1, 2: 3, 4: 5, 6: 7]; return x; }",
            "rt_err:struct:s:attribute:l:validator:size:too_large")
        chkEx("{ val x = s([0: 1, 2: 3]); x.l = [9: 8, 7: 6]; return x; }",
            "s[l=map<integer,integer>[int[9]=int[8],int[7]=int[6]]]")

        chkEx("{ val x = t([0L: false, 64L: true]); x.l = [:]; return x; }",
            "rt_err:struct:t:attribute:l:validator:size:too_small")
        chkEx("{ val x = t([0L: false, 64L: true]); x.l = map([-10L: true, 7L: true]); return x; }",
            "t[l=map<big_integer,boolean>[bigint[-10]=boolean[true],bigint[7]=boolean[true]]]")

        chkEx("{ val x = u([1: 'a', 3: 'b']); x.l = [0: 'a', 5: 'b', 10: 'c', 15: 'd']; return x; }",
            "rt_err:struct:u:attribute:l:validator:size:too_large")
        chkEx("{ val x = u([1: 'a', 3: 'b']); x.l = [7: 'x', 11: 'y']; return x; }",
            "u[l=map<integer,text>[int[7]=text[x],int[11]=text[y]]]")
    }

    @Disabled
    @Test fun testStructFromGtvMap() {
        def("struct s { @size(1, 3) l: map<integer, boolean>; }")
        def("struct t { @min_size(2) l: map<text, text>; }")
        def("struct u { @max_size(2) l: map<integer, decimal>; }")

        chk("s.from_gtv(gtv.from_json('[[]]'))", "rt_err:struct:s:attribute:l:validator:size:too_small")
        chk("s.from_gtv(gtv.from_json('[[[1, 1], [2, 0], [3, 1], [4, 0]]]'))",
            "rt_err:struct:s:attribute:l:validator:size:too_large")
        chk("s.from_gtv(gtv.from_json('[[[1, 1], [2, 0]]]'))",
            "s[l=map<integer,boolean>[int[1]=boolean[true],int[2]=boolean[false]]]")

        chk("t.from_gtv(gtv.from_json('[{}]'))", "rt_err:struct:t:attribute:l:validator:size:too_small")
        chk("t.from_gtv(gtv.from_json('[{\"a\": \"b\", \"c\":\"d\"}]'))",
            "t[l=map<text,text>[text[a]=text[b],text[c]=text[d]]]")

        chk("u.from_gtv(gtv.from_json('[[[1, 1.0], [2, 2.0], [3, 3.0], [4, 4.0]]]'))",
            "rt_err:struct:u:attribute:l:validator:size:too_large")
        chk("u.from_gtv(gtv.from_json('[[[1, 1.0], [2, 2.0]]]'))",
            "u[l=map<integer,decimal>[int[1]=dec[1],int[2]=dec[2]]]")
    }

    @Disabled
    @Test fun testStructFromBytesMap() {
        def("struct s { @size(1, 3) l: map<integer, boolean>; }")
        def("struct t { @min_size(2) l: map<text, text>; }")
        def("struct u { @max_size(2) l: map<big_integer, decimal>; }")

        def("struct s2 { l: map<integer, boolean>; }")
        def("struct t2 { l: map<text, text>; }")
        def("struct u2 { l: map<big_integer, decimal>; }")

        chk("s.from_bytes(s2([:]).to_bytes())", "rt_err:struct:s:attribute:l:validator:size:too_small")
        chk("s.from_bytes(s2([1: true, 2: false, 3: false, 4: true]).to_bytes())",
            "rt_err:struct:s:attribute:l:validator:size:too_large")
        chk("s.from_bytes(s2([1: true, 2: false]).to_bytes())",
            "s[l=map<integer,boolean>[int[1]=boolean[true],int[2]=boolean[false]]]")

        chk("t.from_bytes(t2([:]).to_bytes())", "rt_err:struct:t:attribute:l:validator:size:too_small")
        chk("t.from_bytes(t2(['a': 'b', 'c': 'd']).to_bytes())", "t[l=map<text,text>[text[a]=text[b],text[c]=text[d]]]")

        chk("u.from_bytes(u2([1L: 1.0, 2L: 2.0, 3L: 3.0, 4L: 4.0]).to_bytes())",
            "rt_err:struct:u:attribute:l:validator:size:too_large")
        chk("u.from_bytes(u2([1L: 1.0, 2L: 2.0]).to_bytes())",
            "u[l=map<big_integer,decimal>[bigint[1]=dec[1],bigint[2]=dec[2]]]")
    }

    @Test fun testStructCreationByteArray() {
        def("struct s { @size(1, 3) l: byte_array; }")
        def("struct t { @min_size(2) l: byte_array; }")
        def("struct u { @max_size(2) l: byte_array; }")

        chk("s(x'')", "rt_err:struct:s:attribute:l:validator:size:too_small")
        chk("s(x'ABCD')", "s[l=byte_array[abcd]]")
        chk("s(x'ABCDEF01')", "rt_err:struct:s:attribute:l:validator:size:too_large")

        chk("t(x'62')", "rt_err:struct:t:attribute:l:validator:size:too_small")
        chk("t(x'CAFEC0FFEE')", "t[l=byte_array[cafec0ffee]]")

        chk("u(x'CAFEC0FFEE')", "rt_err:struct:u:attribute:l:validator:size:too_large")
        chk("u(x'CAFE')", "u[l=byte_array[cafe]]")
    }

    @Test fun testStructUpdateByteArray() {
        def("struct s { @size(1, 3) mutable l: byte_array; }")
        def("struct t { @min_size(2) mutable l: byte_array; }")
        def("struct u { @max_size(2) mutable l: byte_array; }")

        chkEx("{ val x = s(x'0123'); x.l = x''; return x; }", "rt_err:struct:s:attribute:l:validator:size:too_small")
        chkEx("{ val x = s(x'0123'); x.l = x'DEADBEEF'; return x; }",
            "rt_err:struct:s:attribute:l:validator:size:too_large")
        chkEx("{ val x = s(x'0123'); x.l = x'CAFE'; return x; }", "s[l=byte_array[cafe]]")

        chkEx("{ val x = t(x'0123'); x.l = x''; return x; }", "rt_err:struct:t:attribute:l:validator:size:too_small")
        chkEx("{ val x = t(x'0123'); x.l = x'CAFE'; return x; }", "t[l=byte_array[cafe]]")

        chkEx("{ val x = u(x'0123'); x.l = x'DEADBEEF'; return x; }",
            "rt_err:struct:u:attribute:l:validator:size:too_large")
        chkEx("{ val x = u(x'0123'); x.l = x'CAFE'; return x; }", "u[l=byte_array[cafe]]")
    }

    @Test fun testStructFromGtvByteArray() {
        def("struct s { @size(1, 3) l: byte_array; }")
        def("struct t { @min_size(2) l: byte_array; }")
        def("struct u { @max_size(2) l: byte_array; }")

        chk("s.from_gtv(gtv.from_json('[\"\"]'))", "rt_err:struct:s:attribute:l:validator:size:too_small")
        chk("s.from_gtv(gtv.from_json('[\"DEADBEEF\"]'))", "rt_err:struct:s:attribute:l:validator:size:too_large")
        chk("s.from_gtv(gtv.from_json('[\"CAFE\"]'))", "s[l=byte_array[cafe]]")

        chk("t.from_gtv(gtv.from_json('[\"\"]'))", "rt_err:struct:t:attribute:l:validator:size:too_small")
        chk("t.from_gtv(gtv.from_json('[\"CAFE\"]'))", "t[l=byte_array[cafe]]")

        chk("u.from_gtv(gtv.from_json('[\"DEADBEEF\"]'))", "rt_err:struct:u:attribute:l:validator:size:too_large")
        chk("u.from_gtv(gtv.from_json('[\"CAFE\"]'))", "u[l=byte_array[cafe]]")
    }

    @Test fun testStructFromBytesByteArray() {
        def("struct s { @size(1, 3) l: byte_array; }")
        def("struct t { @min_size(2) l: byte_array; }")
        def("struct u { @max_size(2) l: byte_array; }")

        def("struct s2 { l: byte_array; }")
        def("struct t2 { l: byte_array; }")
        def("struct u2 { l: byte_array; }")

        chk("s.from_bytes(s2(x'').to_bytes())", "rt_err:struct:s:attribute:l:validator:size:too_small")
        chk("s.from_bytes(s2(x'DEADBEEF').to_bytes())", "rt_err:struct:s:attribute:l:validator:size:too_large")
        chk("s.from_bytes(s2(x'CAFE').to_bytes())", "s[l=byte_array[cafe]]")

        chk("t.from_bytes(t2(x'').to_bytes())", "rt_err:struct:t:attribute:l:validator:size:too_small")
        chk("t.from_bytes(t2(x'CAFE').to_bytes())", "t[l=byte_array[cafe]]")

        chk("u.from_bytes(u2(x'DEADBEEF').to_bytes())", "rt_err:struct:u:attribute:l:validator:size:too_large")
        chk("u.from_bytes(u2(x'CAFE').to_bytes())", "u[l=byte_array[cafe]]")
    }

    @Test fun testStructCreationText() {
        def("struct s { @size(5, 20) l: text; }")
        def("struct t { @min_size(3) l: text; }")
        def("struct u { @max_size(25) l: text; }")

        chk("s('Oops')", "rt_err:struct:s:attribute:l:validator:size:too_small")
        chk("s('This is long enough')", "s[l=text[This is long enough]]")
        chk("s('This is above the maximum I think')", "rt_err:struct:s:attribute:l:validator:size:too_large")

        chk("t(':(')", "rt_err:struct:t:attribute:l:validator:size:too_small")
        chk("t('easy breezy')", "t[l=text[easy breezy]]")

        chk("u('abcdefghijklmnopqrstuvwxyz')", "rt_err:struct:u:attribute:l:validator:size:too_large")
        chk("u('abcdefghijklmnopqrstuvwxy')", "u[l=text[abcdefghijklmnopqrstuvwxy]]")
    }

    @Test fun testStructUpdateText() {
        def("struct s { @size(1, 3) mutable l: text; }")
        def("struct t { @min_size(2) mutable l: text; }")
        def("struct u { @max_size(2) mutable l: text; }")

        chkEx("{ val x = s('ant'); x.l = ''; return x; }", "rt_err:struct:s:attribute:l:validator:size:too_small")
        chkEx("{ val x = s('ant'); x.l = 'monkey'; return x; }", "rt_err:struct:s:attribute:l:validator:size:too_large")
        chkEx("{ val x = s('ant'); x.l = 'dog'; return x; }", "s[l=text[dog]]")

        chkEx("{ val x = t('ant'); x.l = ''; return x; }", "rt_err:struct:t:attribute:l:validator:size:too_small")
        chkEx("{ val x = t('ant'); x.l = 'dogs'; return x; }", "t[l=text[dogs]]")

        chkEx("{ val x = u('ox'); x.l = 'monkey'; return x; }", "rt_err:struct:u:attribute:l:validator:size:too_large")
        chkEx("{ val x = u('ox'); x.l = 'tc'; return x; }", "u[l=text[tc]]")
    }

    @Test fun testStructFromGtvText() {
        def("struct s { @size(1, 3) l: text; }")
        def("struct t { @min_size(2) l: text; }")
        def("struct u { @max_size(2) l: text; }")

        chk("s.from_gtv(gtv.from_json('[\"\"]'))", "rt_err:struct:s:attribute:l:validator:size:too_small")
        chk("s.from_gtv(gtv.from_json('[\"hippopotamus\"]'))", "rt_err:struct:s:attribute:l:validator:size:too_large")
        chk("s.from_gtv(gtv.from_json('[\"fox\"]'))", "s[l=text[fox]]")

        chk("t.from_gtv(gtv.from_json('[\"\"]'))", "rt_err:struct:t:attribute:l:validator:size:too_small")
        chk("t.from_gtv(gtv.from_json('[\"fox\"]'))", "t[l=text[fox]]")

        chk("u.from_gtv(gtv.from_json('[\"hippopotamus\"]'))", "rt_err:struct:u:attribute:l:validator:size:too_large")
        chk("u.from_gtv(gtv.from_json('[\"ox\"]'))", "u[l=text[ox]]")
    }

    @Test fun testStructFromBytesText() {
        def("struct s { @size(1, 3) l: text; }")
        def("struct t { @min_size(2) l: text; }")
        def("struct u { @max_size(2) l: text; }")

        def("struct s2 { l: text; }")
        def("struct t2 { l: text; }")
        def("struct u2 { l: text; }")

        chk("s.from_bytes(s2('').to_bytes())", "rt_err:struct:s:attribute:l:validator:size:too_small")
        chk("s.from_bytes(s2('hippopotamus').to_bytes())", "rt_err:struct:s:attribute:l:validator:size:too_large")
        chk("s.from_bytes(s2('fox').to_bytes())", "s[l=text[fox]]")

        chk("t.from_bytes(t2('').to_bytes())", "rt_err:struct:t:attribute:l:validator:size:too_small")
        chk("t.from_bytes(t2('fox').to_bytes())", "t[l=text[fox]]")

        chk("u.from_bytes(u2('hippopotamus').to_bytes())", "rt_err:struct:u:attribute:l:validator:size:too_large")
        chk("u.from_bytes(u2('ox').to_bytes())", "u[l=text[ox]]")
    }

    @Disabled
    @Test fun testStructCreationJson() {
        def("struct s { @size(5, 20) l: json; }")
        def("struct t { @min_size(3) l: json; }")
        def("struct u { @max_size(25) l: json; }")

        chk("s(json('[10]'))", "rt_err:struct:s:attribute:l:validator:size:too_small")
        chk("s(json('{\"x\": [1, 2, 3, 4]}'))", "s[l=json[{\"x\":[1,2,3,4]}]]")
        chk("s(json('{\"x\": [1, 2, 3, 4], \"y\": [9, 8, 7, 6]}'))",
            "rt_err:struct:s:attribute:l:validator:size:too_large")

        chk("t(json('[]'))", "rt_err:struct:t:attribute:l:validator:size:too_small")
        chk("t(json('\"easy breezy\"'))", "t[l=json[\"easy breezy\"]]")

        chk("u(json('{\"value\": \"New\", \"onclick\": \"CreateNewDoc()\"}'))",
            "rt_err:struct:u:attribute:l:validator:size:too_large")
        chk("u(json('[\"a\", \"b\", \"c\"]'))", "u[l=json[[\"a\",\"b\",\"c\"]]]")
    }

    @Disabled
    @Test fun testStructUpdateJson() {
        def("struct s { @size(3, 5) mutable l: json; }")
        def("struct t { @min_size(4) mutable l: json; }")
        def("struct u { @max_size(4) mutable l: json; }")

        chkEx("{ val x = s(json('\"ant\"')); x.l = json('\"\"'); return x; }",
            "rt_err:struct:s:attribute:l:validator:size:too_small")
        chkEx("{ val x = s(json('\"ant\"')); x.l = json('\"monkey\"'); return x; }",
            "rt_err:struct:s:attribute:l:validator:size:too_large")
        chkEx("{ val x = s(json('\"ant\"')); x.l = json('\"dog\"'); return x; }", "s[l=json[\"dog\"]]")

        chkEx("{ val x = t(json('\"ant\"')); x.l = json('\"\"'); return x; }",
            "rt_err:struct:t:attribute:l:validator:size:too_small")
        chkEx("{ val x = t(json('\"ant\"')); x.l = json('\"dogs\"'); return x; }", "t[l=json[\"dogs\"]]")

        chkEx("{ val x = u(json('\"ox\"')); x.l = json('\"monkey\"'); return x; }",
            "rt_err:struct:u:attribute:l:validator:size:too_large")
        chkEx("{ val x = u(json('\"ox\"')); x.l = json('\"tc\"'); return x; }", "u[l=json[\"tc\"]]")
    }

    @Disabled
    @Test fun testStructFromGtvJson() {
        def("struct s { @size(3, 5) l: json; }")
        def("struct t { @min_size(4) l: json; }")
        def("struct u { @max_size(4) l: json; }")

        def("struct s2 { l: json; }")
        def("struct t2 { l: json; }")
        def("struct u2 { l: json; }")

        chk("s.from_gtv(s2(json('\"\"')).to_gtv())", "rt_err:struct:s:attribute:l:validator:size:too_small")
        chk("s.from_gtv(s2(json('\"hippopotamus\"')).to_gtv())", "rt_err:struct:s:attribute:l:validator:size:too_large")
        chk("s.from_gtv(s2(json('\"fox\"')).to_gtv())", "s[l=json[\"fox\"]]")

        chk("t.from_gtv(t2(json('\"\"')).to_gtv())", "rt_err:struct:t:attribute:l:validator:size:too_small")
        chk("t.from_gtv(t2(json('\"fox\"')).to_gtv())", "t[l=json[\"fox\"]]")

        chk("u.from_gtv(u2(json('\"hippopotamus\"')).to_gtv())", "rt_err:struct:u:attribute:l:validator:size:too_large")
        chk("u.from_gtv(u2(json('\"ox\"')).to_gtv())", "u[l=json[\"ox\"]]")
    }

    @Disabled
    @Test fun testStructFromBytesJson() {
        def("struct s { @size(3, 5) l: json; }")
        def("struct t { @min_size(4) l: json; }")
        def("struct u { @max_size(4) l: json; }")

        def("struct s2 { l: json; }")
        def("struct t2 { l: json; }")
        def("struct u2 { l: json; }")

        chk("s.from_bytes(s2(json('\"\"')).to_bytes())", "rt_err:struct:s:attribute:l:validator:size:too_small")
        chk("s.from_bytes(s2(json('\"hippopotamus\"')).to_bytes())",
            "rt_err:struct:s:attribute:l:validator:size:too_large")
        chk("s.from_bytes(s2(json('\"fox\"')).to_bytes())", "s[l=json[\"fox\"]]")

        chk("t.from_bytes(t2(json('\"\"')).to_bytes())", "rt_err:struct:t:attribute:l:validator:size:too_small")
        chk("t.from_bytes(t2(json('\"fox\"')).to_bytes())", "t[l=json[\"fox\"]]")

        chk("u.from_bytes(u2(json('\"hippopotamus\"')).to_bytes())",
            "rt_err:struct:u:attribute:l:validator:size:too_large")
        chk("u.from_bytes(u2(json('\"ox\"')).to_bytes())", "u[l=json[\"ox\"]]")
    }
}
