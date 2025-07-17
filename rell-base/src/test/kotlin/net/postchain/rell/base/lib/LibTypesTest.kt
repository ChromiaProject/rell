/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.mtype.M_Types
import net.postchain.rell.base.testutils.BaseRellTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibTypesTest: BaseRellTest() {
    @Test fun testComparable() {
        val comparable = Lib_Rell.MODULE.lModule.getType("comparable").mType

        assertTrue(comparable.isSuperTypeOf(Lib_Rell.INTEGER_TYPE.mType))
        assertTrue(comparable.isSuperTypeOf(Lib_Rell.BIG_INTEGER_TYPE.mType))
        assertTrue(comparable.isSuperTypeOf(Lib_Rell.DECIMAL_TYPE.mType))
        assertTrue(comparable.isSuperTypeOf(Lib_Rell.BOOLEAN_TYPE.mType))
        assertTrue(comparable.isSuperTypeOf(Lib_Rell.TEXT_TYPE.mType))
        assertTrue(comparable.isSuperTypeOf(Lib_Rell.BYTE_ARRAY_TYPE.mType))
        assertTrue(comparable.isSuperTypeOf(Lib_Rell.RANGE_TYPE.mType))
        assertTrue(comparable.isSuperTypeOf(M_Types.NULL))

        assertFalse(comparable.isSuperTypeOf(Lib_Rell.UNIT_TYPE.mType))
        assertFalse(comparable.isSuperTypeOf(Lib_Rell.SET_TYPE.lTypeDef.getType(Lib_Rell.INTEGER_TYPE.mType).mType))

        assertFalse(comparable.isSuperTypeOf(M_Types.ANYTHING))
        assertFalse(comparable.isSuperTypeOf(M_Types.ANY))
        assertTrue(comparable.isSuperTypeOf(M_Types.NOTHING))

        assertTrue(M_Types.ANYTHING.isSuperTypeOf(comparable))
        assertTrue(M_Types.ANY.isSuperTypeOf(comparable))
        assertFalse(M_Types.NOTHING.isSuperTypeOf(comparable))
    }

    @Test fun testJsonConstructor() {
        chk("""json('[]')""", "json[[]]")
        chk("""json('{}')""", "json[{}]")
        chk("""json('0')""", "json[0]")
        chk("""json('"A"')""", """json["A"]""")
        chk("""json('[1,2,3]')""", "json[[1,2,3]]")
    }

    @Test fun testJsonStr() {
        chkEx("""{ val s = json('{  "x":5, "y" : 10  }'); return s.str(); }""", """text[{"x":5,"y":10}]""")
    }

    @Test fun testStruct() {
        def("struct foo { a: integer; b: text; }")
        def("struct bar { a: (x: integer, text); }")
        def("struct qaz { m: map<integer,text>; }")

        chk("foo(123,'Hello').to_gtv()", """gtv[[123, "Hello"]]""")
        chk("foo(123,'Hello').to_gtv_pretty()", """gtv[["a": 123, "b": "Hello"]]""")
        chk("foo(123,'Hello').to_bytes()", "byte_array[a510300ea30302017ba2070c0548656c6c6f]")
        chk("foo.from_gtv(gtv.from_bytes(x'a510300ea30302017ba2070c0548656c6c6f'))", "foo[a=int[123],b=text[Hello]]")
        chk("foo.from_gtv_pretty(gtv.from_bytes(x'a41a301830080c0161a30302017b300c0c0162a2070c0548656c6c6f'))",
                "foo[a=int[123],b=text[Hello]]")
        chk("foo.from_bytes(x'a510300ea30302017ba2070c0548656c6c6f')", "foo[a=int[123],b=text[Hello]]")

        chk("bar((x=123,'Hello')).to_gtv()", """gtv[[[123, "Hello"]]]""")
        chk("bar((x=123,'Hello')).to_gtv_pretty()", """gtv[["a": [123, "Hello"]]]""")
        chk("bar((x=123,'Hello')).to_bytes()", "byte_array[a5143012a510300ea30302017ba2070c0548656c6c6f]")
        chk("bar.from_gtv(gtv.from_bytes(x'a5143012a510300ea30302017ba2070c0548656c6c6f'))",
                "bar[a=(x=int[123],text[Hello])]")
        chk("bar((x=123,'Hello')).to_gtv_pretty().to_bytes()", "byte_array[a419301730150c0161a510300ea30302017ba2070c0548656c6c6f]")
        chk("bar.from_gtv_pretty(gtv.from_bytes(x'a419301730150c0161a510300ea30302017ba2070c0548656c6c6f'))",
                "bar[a=(x=int[123],text[Hello])]")
        chk("bar.from_bytes(x'a5143012a510300ea30302017ba2070c0548656c6c6f')", "bar[a=(x=int[123],text[Hello])]")

        chk("qaz([123:'Hello']).to_gtv()", """gtv[[[[123, "Hello"]]]]""")
        chk("qaz([123:'Hello']).to_gtv_pretty()", """gtv[["m": [[123, "Hello"]]]]""")
        chk("qaz([123:'Hello']).to_bytes()", "byte_array[a5183016a5143012a510300ea30302017ba2070c0548656c6c6f]")
        chk("qaz([123:'Hello']).to_gtv_pretty().to_bytes()",
                "byte_array[a41d301b30190c016da5143012a510300ea30302017ba2070c0548656c6c6f]")
        chk("qaz.from_gtv(gtv.from_bytes(x'a5183016a5143012a510300ea30302017ba2070c0548656c6c6f'))",
                "qaz[m=map<integer,text>[int[123]=text[Hello]]]")
        chk("qaz.from_gtv_pretty(gtv.from_bytes(x'a41d301b30190c016da5143012a510300ea30302017ba2070c0548656c6c6f'))",
                "qaz[m=map<integer,text>[int[123]=text[Hello]]]")
        chk("qaz.from_bytes(x'a5183016a5143012a510300ea30302017ba2070c0548656c6c6f')",
                "qaz[m=map<integer,text>[int[123]=text[Hello]]]")
    }

    @Test fun testIterableCollection() {
        chkCompile("function f(i: iterable<integer>) {}", "ct_err:unknown_name:iterable")
        chkCompile("function f(): iterable<integer>? = null;", "ct_err:unknown_name:iterable")
        chkCompile("function f() = iterable<integer>();", "ct_err:unknown_name:iterable")

        chkCompile("function f(i: collection<integer>) {}", "ct_err:unknown_name:collection")
        chkCompile("function f(): collection<integer>? = null;", "ct_err:unknown_name:collection")
        chkCompile("function f() = collection<integer>();", "ct_err:unknown_name:collection")
    }
}
