/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.expr.expr

import net.postchain.rell.base.testutils.BaseRellTest
import org.junit.Test

class TupleMatchingTest: BaseRellTest() {
    @Test fun testSimple() {
        chkEx("{ val (i, s) = (123, 'Hello'); return i; }", "int[123]")
        chkEx("{ val (i, s) = (123, 'Hello'); return s; }", "text[Hello]")
        chkEx("{ val (i: integer, s: text) = (123, 'Hello'); return i; }", "int[123]")
        chkEx("{ val (i: integer, s: text) = (123, 'Hello'); return s; }", "text[Hello]")
        chkEx("{ val (i: integer, s) = (123, 'Hello'); return i; }", "int[123]")
        chkEx("{ val (i, s: text) = (123, 'Hello'); return s; }", "text[Hello]")
        chkEx("{ val (x, y) = [123, 456]; return 0; }", "ct_err:var_notuple:list<integer>")
    }

    @Test fun testSingleVariable() {
        chkEx("{ val (i) = (123,); return i; }", "int[123]")
        chkEx("{ val (i) = (123); return 0; }", "ct_err:var_notuple:integer")
        chkEx("{ val (i) = 123; return 0; }", "ct_err:var_notuple:integer")
        chkEx("{ val (i: integer) = (123); return 0; }", "ct_err:var_notuple:integer")
        chkEx("{ val (i: integer) = (123,); return i; }", "int[123]")
        chkEx("{ val (i: integer) = 123; return 0; }", "ct_err:var_notuple:integer")
        chkEx("{ val i: integer = (123,); return 0; }", "ct_err:stmt_var_type:i:[integer]:[(integer,)]")
        chkEx("{ val i: integer = 123; return i; }", "int[123]")
    }

    @Test fun testTypeMismatch() {
        chkEx("{ val (x, y) = (123, 'Hello', true); return 0; }", "ct_err:var_tuple_wrongsize:2:3:(integer,text,boolean)")
        chkEx("{ val (x, y, z) = (123, 'Hello'); return 0; }", "ct_err:var_tuple_wrongsize:3:2:(integer,text)")
        chkEx("{ val (x: integer, y: text) = ('Hello', 123); return 0; }",
                "ct_err:[stmt_var_type:x:[integer]:[text]][stmt_var_type:y:[text]:[integer]]")
        chkEx("{ val (x, y: text) = ('Hello', 123); return 0; }", "ct_err:stmt_var_type:y:[text]:[integer]")
        chkEx("{ val (x: integer, y) = ('Hello', 123); return 0; }", "ct_err:stmt_var_type:x:[integer]:[text]")
        chkEx("{ val (x, y) = ('Hello', 123); return x; }", "text[Hello]")
    }

    @Test fun testNestedTuples() {
        chkEx("{ val (x, (y, z)) = ('Hello', (123, true)); return x; }", "text[Hello]")
        chkEx("{ val (x, (y, z)) = ('Hello', (123, true)); return y; }", "int[123]")
        chkEx("{ val (x, (y, z)) = ('Hello', (123, true)); return z; }", "boolean[true]")
        chkEx("{ val (x, (y, z)) = ('Hello', 123, true); return 0; }",
                "ct_err:[var_tuple_wrongsize:2:3:(text,integer,boolean)][var_notuple:integer]")
        chkEx("{ val (x, (y, z)) = (('Hello', 123), true); return 0; }", "ct_err:var_notuple:boolean")
        chkEx("{ val (x, (y, z)) = ('Hello', (123,), true); return 0; }",
                "ct_err:[var_tuple_wrongsize:2:3:(text,(integer,),boolean)][var_tuple_wrongsize:2:1:(integer,)]")
        chkEx("{ val (x, (y, z)) = ('Hello', 123); return 0; }", "ct_err:var_notuple:integer")
        chkEx("{ val (x: text, (y, z)) = ('Hello', (123, true)); return x; }", "text[Hello]")
        chkEx("{ val (x: text, (y: integer, z)) = ('Hello', (123, true)); return x; }", "text[Hello]")
        chkEx("{ val (x: text, (y: integer, z: boolean)) = ('Hello', (123, true)); return x; }", "text[Hello]")
    }

    @Test fun testNullable() {
        chkEx("{ val t: integer? = _nullable(123); var (x, y) = (t, 'Hello'); return x; }", "int[123]")
        chkEx("{ val t: integer? = _nullable(123); var (x: integer, y: text) = (t, 'Hello'); return 0; }",
                "ct_err:stmt_var_type:x:[integer]:[integer?]")
        chkEx("{ val t: integer? = _nullable(123); var (x: integer?, y) = (t, 'Hello'); return x; }", "int[123]")
        chkEx("{ val (x: integer, y) = (123, 'Hello'); return x; }", "int[123]")
    }

    @Test fun testWildcard() {
        chkEx("{ val (x, _, y) = (123, 456, 789); return x; }", "int[123]")
        chkEx("{ val (x, _, y) = (123, 456, 789); return y; }", "int[789]")
        chkEx("{ val (x, _, y) = (123, 456, 789); return _; }", "ct_err:unknown_name:_")
        chkEx("{ val (x, _, y) = (123, 456); return 0; }", "ct_err:var_tuple_wrongsize:3:2:(integer,integer)")
    }

    @Test fun testForListSet() {
        val list = "[(123,'Hello'),(456,'Bye')]"
        chkEx("{ for ((x, y) in $list) { print(x,y); } return 7; }", "int[7]")
        chkOut("123 Hello", "456 Bye")

        chkEx("{ for (x in $list) { print(x); } return 7; }", "int[7]")
        chkOut("(123,Hello)", "(456,Bye)")

        chkEx("{ for ((x) in $list) { print(x); } return 7; }", "ct_err:var_tuple_wrongsize:1:2:(integer,text)")
        chkEx("{ for ((x, y, z) in $list) { print(x); } return 7; }", "ct_err:var_tuple_wrongsize:3:2:(integer,text)")
        chkEx("{ for (x, y in $list) { print(x,y); } return 7; }", "ct_err:syntax")

        chkEx("{ for ((x, y) in set($list)) { print(x,y); } return 7; }", "int[7]")
        chkOut("123 Hello", "456 Bye")
    }

    @Test fun testForMap() {
        val map = "[123:'Hello',456:'Bye']"
        chkEx("{ for ((x, y) in $map) { print(x,y); } return 7; }", "int[7]")
        chkOut("123 Hello", "456 Bye")

        chkEx("{ for (x in $map) { print(x); } return 7; }", "int[7]")
        chkOut("(123,Hello)", "(456,Bye)")

        chkEx("{ for ((x) in $map) { print(x); } return 7; }", "ct_err:var_tuple_wrongsize:1:2:(integer,text)")
    }
}
