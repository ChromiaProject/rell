/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.expr.expr

import net.postchain.rell.base.testutils.BaseRellTest
import org.junit.Test

class CollectionTypeInferenceTest: BaseRellTest() {
    @Test fun testLocalVariableAssignment() {
        chkEx("{ val x: list<integer>; x = []; return x; }", "list<integer>[]")
        chkEx("{ val x: list<integer>; x = list(); return x; }", "list<integer>[]")
        chkEx("{ var x: list<integer>; x = []; return x; }", "list<integer>[]")
        chkEx("{ var x: list<integer>; x = list(); return x; }", "list<integer>[]")

        chkEx("{ val x: set<integer>; x = set(); return x; }", "set<integer>[]")
        chkEx("{ var x: set<integer>; x = set(); return x; }", "set<integer>[]")

        chkEx("{ val x: map<integer,text>; x = [:]; return x; }", "map<integer,text>[]")
        chkEx("{ val x: map<integer,text>; x = map(); return x; }", "map<integer,text>[]")
        chkEx("{ var x: map<integer,text>; x = [:]; return x; }", "map<integer,text>[]")
        chkEx("{ var x: map<integer,text>; x = map(); return x; }", "map<integer,text>[]")
    }

    @Test fun testLocalVariableInitialization() {
        chkEx("{ val x: list<integer> = []; return x; }", "list<integer>[]")
        chkEx("{ val x: list<integer> = list(); return x; }", "list<integer>[]")
        chkEx("{ var x: list<integer> = []; return x; }", "list<integer>[]")
        chkEx("{ var x: list<integer> = list(); return x; }", "list<integer>[]")

        chkEx("{ val x: set<integer> = set(); return x; }", "set<integer>[]")
        chkEx("{ var x: set<integer> = set(); return x; }", "set<integer>[]")

        chkEx("{ val x: map<integer,text> = [:]; return x; }", "map<integer,text>[]")
        chkEx("{ val x: map<integer,text> = map(); return x; }", "map<integer,text>[]")
        chkEx("{ var x: map<integer,text> = [:]; return x; }", "map<integer,text>[]")
        chkEx("{ var x: map<integer,text> = map(); return x; }", "map<integer,text>[]")
    }

    @Test fun testLocalVariableInitializationComplex() {
        chkEx("{ val (x: list<integer>, y: set<text>) = ([], set()); return x; }", "list<integer>[]")
        chkEx("{ val (x: list<integer>, y: set<text>) = ([], set()); return y; }", "set<text>[]")

        chkEx("{ val (x: list<integer>, y) = ([], 0); return x; }", "list<integer>[]")
        chkEx("{ val (x, y: set<text>) = (0, set()); return y; }", "set<text>[]")

        val init = "val (x: list<integer>, (y: set<text>, z: map<decimal,boolean>)) = ([], (set(), [:]));"
        chkEx("{ $init return x; }", "list<integer>[]")
        chkEx("{ $init return y; }", "set<text>[]")
        chkEx("{ $init return z; }", "map<decimal,boolean>[]")

        chkEx("{ val (x: list<integer>, (y, z)) = ([], (0, 1)); return x; }", "list<integer>[]")
        chkEx("{ val (x, (y: set<text>, z)) = (0, (set(), 1)); return y; }", "set<text>[]")
        chkEx("{ val (x, (y, z: map<decimal,boolean>)) = (0, (1, [:])); return z; }", "map<decimal,boolean>[]")
    }

    @Test fun testStructInitializers() {
        chkFull("struct s { x: list<integer> = []; } query q() = s();", "s[x=list<integer>[]]")
        chkFull("struct s { x: list<integer> = list(); } query q() = s();", "s[x=list<integer>[]]")
        chkFull("struct s { x: set<integer> = set(); } query q() = s();", "s[x=set<integer>[]]")
        chkFull("struct s { x: map<integer,text> = [:]; } query q() = s();", "s[x=map<integer,text>[]]")
        chkFull("struct s { x: map<integer,text> = map(); } query q() = s();", "s[x=map<integer,text>[]]")

        chkFull("struct s { x: list<integer>? = []; } query q() = s();", "s[x=list<integer>[]]")
        chkFull("struct s { x: list<integer>? = list(); } query q() = s();", "s[x=list<integer>[]]")
        chkFull("struct s { x: set<integer>? = set(); } query q() = s();", "s[x=set<integer>[]]")
        chkFull("struct s { x: map<integer,text>? = [:]; } query q() = s();", "s[x=map<integer,text>[]]")
        chkFull("struct s { x: map<integer,text>? = map(); } query q() = s();", "s[x=map<integer,text>[]]")
    }

    @Test fun testStructConstrutor() {
        chkFull("struct s { x: list<integer>; } query q() = s(x = []);", "s[x=list<integer>[]]")
        chkFull("struct s { x: list<integer>; } query q() = s(x = list());", "s[x=list<integer>[]]")
        chkFull("struct s { x: list<integer>; } query q() = s([]);", "s[x=list<integer>[]]")
        chkFull("struct s { x: list<integer>; } query q() = s(list());", "s[x=list<integer>[]]")

        chkFull("struct s { x: set<integer>; } query q() = s(x = set());", "s[x=set<integer>[]]")
        chkFull("struct s { x: set<integer>; } query q() = s(set());", "s[x=set<integer>[]]")

        chkFull("struct s { x: map<integer,text>; } query q() = s(x = [:]);", "s[x=map<integer,text>[]]")
        chkFull("struct s { x: map<integer,text>; } query q() = s(x = map());", "s[x=map<integer,text>[]]")
        chkFull("struct s { x: map<integer,text>; } query q() = s([:]);", "s[x=map<integer,text>[]]")
        chkFull("struct s { x: map<integer,text>; } query q() = s(map());", "s[x=map<integer,text>[]]")

        chkFull("struct s { x: list<integer>?; } query q() = s(x = []);", "s[x=list<integer>[]]")
        chkFull("struct s { x: list<integer>?; } query q() = s(x = list());", "s[x=list<integer>[]]")
        chkFull("struct s { x: list<integer>?; } query q() = s([]);", "s[x=list<integer>[]]")
        chkFull("struct s { x: list<integer>?; } query q() = s(list());", "s[x=list<integer>[]]")
    }

    @Test fun testStructAssignment() {
        chkStructAssignment("list<integer>", "[0]", "[]", "s[x=list<integer>[]]")
        chkStructAssignment("list<integer>", "[0]", "list()", "s[x=list<integer>[]]")
        chkStructAssignment("set<integer>", "set([0])", "set()", "s[x=set<integer>[]]")
        chkStructAssignment("map<integer,text>", "[0:'A']", "[:]", "s[x=map<integer,text>[]]")
        chkStructAssignment("map<integer,text>", "[0:'A']", "map()", "s[x=map<integer,text>[]]")

        chkStructAssignment("list<integer>?", "null", "[]", "s[x=list<integer>[]]")
        chkStructAssignment("list<integer>?", "null", "list()", "s[x=list<integer>[]]")
        chkStructAssignment("set<integer>?", "null", "set()", "s[x=set<integer>[]]")
        chkStructAssignment("map<integer,text>?", "null", "[:]", "s[x=map<integer,text>[]]")
        chkStructAssignment("map<integer,text>?", "null", "map()", "s[x=map<integer,text>[]]")
    }

    private fun chkStructAssignment(type: String, init: String, value: String, exp: String) {
        chkFull("struct s { mutable x: $type = $init; } query q() { val v = s(); v.x = $value; return v; }", exp)
    }

    @Test fun testFunctionArgumentsCall() {
        chkFunctionArgumentsCall("list<integer>", "[]", "list<integer>[]")
        chkFunctionArgumentsCall("list<integer>", "list()", "list<integer>[]")
        chkFunctionArgumentsCall("set<integer>", "set()", "set<integer>[]")
        chkFunctionArgumentsCall("map<integer,text>", "[:]", "map<integer,text>[]")
        chkFunctionArgumentsCall("map<integer,text>", "map()", "map<integer,text>[]")
    }

    private fun chkFunctionArgumentsCall(type: String, value: String, exp: String) {
        chkFull("function f(x: $type) = $type(x); query q() = f($value);", exp)
        chkFull("function f(x: $type) = $type(x); query q() = f(x = $value);", exp)
        chkFull("function f(x: $type?) = $type(x!!); query q() = f($value);", exp)
        chkFull("function f(x: $type?) = $type(x!!); query q() = f(x = $value);", exp)
    }

    @Test fun testFunctionArgumentsDefaults() {
        chkFull("function f(x: list<integer> = []) = list(x); query q() = f();", "list<integer>[]")
        chkFull("function f(x: list<integer> = list()) = list(x); query q() = f();", "list<integer>[]")
        chkFull("function f(x: set<integer> = set()) = set(x); query q() = f();", "set<integer>[]")
        chkFull("function f(x: map<integer,text> = [:]) = map(x); query q() = f();", "map<integer,text>[]")
        chkFull("function f(x: map<integer,text> = map()) = map(x); query q() = f();", "map<integer,text>[]")

        chkFull("function f(x: list<integer>? = []) = list(x!!); query q() = f();", "list<integer>[]")
        chkFull("function f(x: list<integer>? = list()) = list(x!!); query q() = f();", "list<integer>[]")
        chkFull("function f(x: set<integer>? = set()) = set(x!!); query q() = f();", "set<integer>[]")
        chkFull("function f(x: map<integer,text>? = [:]) = map(x!!); query q() = f();", "map<integer,text>[]")
        chkFull("function f(x: map<integer,text>? = map()) = map(x!!); query q() = f();", "map<integer,text>[]")
    }

    @Test fun testFunctionReturnType() {
        chkFunctionReturnType("list<integer>", "[]", "list<integer>[]")
        chkFunctionReturnType("list<integer>", "list()", "list<integer>[]")
        chkFunctionReturnType("set<integer>", "set()", "set<integer>[]")
        chkFunctionReturnType("map<integer,text>", "[:]", "map<integer,text>[]")
        chkFunctionReturnType("map<integer,text>", "map()", "map<integer,text>[]")
    }

    private fun chkFunctionReturnType(type: String, value: String, exp: String) {
        chkFull("function f(): $type = $value; query q() = f();", exp)
        chkFull("function f(): $type? = $value; query q() = f();", exp)
        chkFull("function f(): $type { return $value; } query q() = f();", exp)
        chkFull("function f(): $type? { return $value; } query q() = f();", exp)
    }

    @Test fun testQueryReturnType() {
        chkQueryReturnType("list<integer>", "[]", "list<integer>[]")
        chkQueryReturnType("list<integer>", "list()", "list<integer>[]")
        chkQueryReturnType("set<integer>", "set()", "set<integer>[]")
        chkQueryReturnType("map<integer,text>", "[:]", "map<integer,text>[]")
        chkQueryReturnType("map<integer,text>", "map()", "map<integer,text>[]")
    }

    private fun chkQueryReturnType(type: String, value: String, exp: String) {
        chkFull("query q(): $type = $value;", exp)
        chkFull("query q(): $type? = $value;", exp)
        chkFull("query q(): $type { return $value; }", exp)
        chkFull("query q(): $type? { return $value; }", exp)
    }

    @Test fun testListElementAssignment() {
        chkEx("{ val l = [[123]]; l[0] = []; return l; }", "list<list<integer>>[list<integer>[]]")
        chkEx("{ val l = [[123]]; l[0] = list(); return l; }", "list<list<integer>>[list<integer>[]]")
        chkEx("{ val l = [set([123])]; l[0] = set(); return l; }", "list<set<integer>>[set<integer>[]]")
        chkEx("{ val l = [[123:'abc']]; l[0] = [:]; return l; }", "list<map<integer,text>>[map<integer,text>[]]")
        chkEx("{ val l = [[123:'abc']]; l[0] = map(); return l; }", "list<map<integer,text>>[map<integer,text>[]]")
    }

    @Test fun testMapElementAssignment() {
        chkEx("{ val m = [33:[123]]; m[33] = []; return m; }", "map<integer,list<integer>>[int[33]=list<integer>[]]")
        chkEx("{ val m = [33:[123]]; m[33] = list(); return m; }", "map<integer,list<integer>>[int[33]=list<integer>[]]")
        chkEx("{ val m = [33:set([123])]; m[33] = set(); return m; }", "map<integer,set<integer>>[int[33]=set<integer>[]]")
        chkEx("{ val m = [33:[123:'abc']]; m[33] = [:]; return m; }",
            "map<integer,map<integer,text>>[int[33]=map<integer,text>[]]")
        chkEx("{ val m = [33:[123:'abc']]; m[33] = map(); return m; }",
            "map<integer,map<integer,text>>[int[33]=map<integer,text>[]]")
    }

    @Test fun testListMethods() {
        chkEx("{ val l = list<list<integer>>(); l.add([]); return l; }", "list<list<integer>>[list<integer>[]]")
        chkEx("{ val l = list<list<integer>>(); l.add(list()); return l; }", "list<list<integer>>[list<integer>[]]")
        chkEx("{ val l = list<set<integer>>(); l.add(set()); return l; }", "list<set<integer>>[set<integer>[]]")
        chkEx("{ val l = list<map<integer,text>>(); l.add([:]); return l; }",
            "list<map<integer,text>>[map<integer,text>[]]")
        chkEx("{ val l = list<map<integer,text>>(); l.add(map()); return l; }",
            "list<map<integer,text>>[map<integer,text>[]]")
    }

    @Test fun testMapMethods() {
        chkEx("{ val m = map<integer,list<integer>>(); m.put(33, []); return m; }",
            "map<integer,list<integer>>[int[33]=list<integer>[]]")
        chkEx("{ val m = map<integer,list<integer>>(); m.put(33, list()); return m; }",
            "map<integer,list<integer>>[int[33]=list<integer>[]]")
        chkEx("{ val m = map<integer,set<integer>>(); m.put(33, set()); return m; }",
            "map<integer,set<integer>>[int[33]=set<integer>[]]")
        chkEx("{ val m = map<integer,map<integer,text>>(); m.put(33, [:]); return m; }",
            "map<integer,map<integer,text>>[int[33]=map<integer,text>[]]")
        chkEx("{ val m = map<integer,map<integer,text>>(); m.put(33, map()); return m; }",
            "map<integer,map<integer,text>>[int[33]=map<integer,text>[]]")
    }
}
