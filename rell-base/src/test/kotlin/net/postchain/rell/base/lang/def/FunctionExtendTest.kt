/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.def

import net.postchain.rell.base.testutils.BaseRellTest
import kotlin.test.Test

class FunctionExtendTest: BaseRellTest() {
    @Test fun testSimplest() {
        file("lib.rell", "module; @extendable function f(x: integer) { print('lib.f:' + x); }")
        def("import lib;")
        def("@extend(lib.f) function f(x: integer) { print('main.f:' + x); }")
        chkFullOut("query q() { lib.f(123); return 0; }", "int[0]", "main.f:123", "lib.f:123")
    }

    @Test fun testTypeUnit() {
        file("lib.rell", "module; @extendable function f(x: integer) { print('lib.f:'+x); }")
        def("import lib;")

        chk("_type_of(lib.f(123))", "text[unit]")

        val query = "query q() { lib.f(123); return 0; }"

        chkFullOut(query, "int[0]", "lib.f:123")
        chkFullOut("@extend(lib.f) function g(x: integer) { print('main.g:'+x); } $query", "int[0]", "main.g:123", "lib.f:123")

        chkFullOut("""
            @extend(lib.f) function g(x: integer) { print('main.g:'+x); }
            @extend(lib.f) function h(x: integer) { print('main.h:'+x); }
            $query
        """, "int[0]", "main.g:123", "main.h:123", "lib.f:123")
    }

    @Test fun testTypeUnitNoImplicit() {
        file("lib.rell", "module; @extendable function f();")
        file("ext.rell", "module; import lib; @extend(lib.f) function g() { print('g'); }")
        def("import lib;")

        chk("_type_of(lib.f())", "text[unit]")

        chkFullOut("query q() { lib.f(); return 123; }", "int[123]")
        chkFullOut("import ext; query q() { lib.f(); return 123; }", "int[123]", "g")
    }

    @Test fun testTypeBoolean() {
        file("lib.rell", """
            module;
            @extendable function f(x: integer): boolean { print('lib.f:'+x); return true; }
            @extendable function g(x: integer): boolean { print('lib.g:'+x); return false; }
            @extendable function h(x: integer): boolean;
        """)
        def("import lib;")

        chk("_type_of(lib.f(123))", "text[boolean]")
        chk("_type_of(lib.g(123))", "text[boolean]")
        chk("_type_of(lib.h(123))", "text[boolean]")

        val type = "boolean"

        chkType(type, "lib.f", listOf(), "boolean[true]", "lib.f:123")
        chkType(type, "lib.g", listOf(), "boolean[false]", "lib.g:123")
        chkType(type, "lib.h", listOf(), "boolean[false]")

        chkType(type, "lib.f", listOf("false"), "boolean[true]", "e1:123", "lib.f:123")
        chkType(type, "lib.f", listOf("true"), "boolean[true]", "e1:123")
        chkType(type, "lib.g", listOf("false"), "boolean[false]", "e1:123", "lib.g:123")
        chkType(type, "lib.g", listOf("true"), "boolean[true]", "e1:123")
        chkType(type, "lib.h", listOf("false"), "boolean[false]", "e1:123")
        chkType(type, "lib.h", listOf("true"), "boolean[true]", "e1:123")

        chkType(type, "lib.f", listOf("false", "false"), "boolean[true]", "e1:123", "e2:123", "lib.f:123")
        chkType(type, "lib.f", listOf("false", "true"), "boolean[true]", "e1:123", "e2:123")
        chkType(type, "lib.f", listOf("true", "false"), "boolean[true]", "e1:123")
        chkType(type, "lib.f", listOf("true", "true"), "boolean[true]", "e1:123")

        chkType(type, "lib.g", listOf("false", "false"), "boolean[false]", "e1:123", "e2:123", "lib.g:123")
        chkType(type, "lib.g", listOf("false", "true"), "boolean[true]", "e1:123", "e2:123")
        chkType(type, "lib.g", listOf("true", "false"), "boolean[true]", "e1:123")
        chkType(type, "lib.g", listOf("true", "true"), "boolean[true]", "e1:123")
    }

    @Test fun testTypeNullable() {
        file("lib.rell", """
            module;
            @extendable function f(x: integer): integer? { print('lib.f:'+x); return 11; }
            @extendable function g(x: integer): integer? { print('lib.g:'+x); return null; }
            @extendable function h(x: integer): integer?;
        """)
        def("import lib;")

        chk("_type_of(lib.f(123))", "text[integer?]")
        chk("_type_of(lib.g(123))", "text[integer?]")
        chk("_type_of(lib.h(123))", "text[integer?]")

        val type = "integer?"

        chkType(type, "lib.f", listOf(), "int[11]", "lib.f:123")
        chkType(type, "lib.g", listOf(), "null", "lib.g:123")
        chkType(type, "lib.h", listOf(), "null")

        chkType(type, "lib.f", listOf("null"), "int[11]", "e1:123", "lib.f:123")
        chkType(type, "lib.f", listOf("22"), "int[22]", "e1:123")
        chkType(type, "lib.g", listOf("null"), "null", "e1:123", "lib.g:123")
        chkType(type, "lib.g", listOf("22"), "int[22]", "e1:123")
        chkType(type, "lib.h", listOf("null"), "null", "e1:123")
        chkType(type, "lib.h", listOf("22"), "int[22]", "e1:123")

        chkType(type, "lib.f", listOf("null", "null"), "int[11]", "e1:123", "e2:123", "lib.f:123")
        chkType(type, "lib.f", listOf("null", "33"), "int[33]", "e1:123", "e2:123")
        chkType(type, "lib.f", listOf("22", "null"), "int[22]", "e1:123")
        chkType(type, "lib.f", listOf("22", "33"), "int[22]", "e1:123")

        chkType(type, "lib.g", listOf("null", "null"), "null", "e1:123", "e2:123", "lib.g:123")
        chkType(type, "lib.g", listOf("null", "33"), "int[33]", "e1:123", "e2:123")
        chkType(type, "lib.g", listOf("22", "null"), "int[22]", "e1:123")
        chkType(type, "lib.g", listOf("22", "33"), "int[22]", "e1:123")
    }

    @Test fun testTypeList() {
        tst.strictToString = false
        file("lib.rell", """
            module;
            @extendable function f(x: integer): list<integer> { print('lib.f:'+x); return [11]; }
            @extendable function h(x: integer): list<integer>;
        """)
        def("import lib;")

        chk("_type_of(lib.f(123))", "list<integer>")
        chk("_type_of(lib.h(123))", "list<integer>")

        val type = "list<integer>"

        chkType(type, "lib.f", listOf(), "[11]", "lib.f:123")
        chkType(type, "lib.h", listOf(), "[]")

        chkType(type, "lib.f", listOf("[]"), "[11]", "e1:123", "lib.f:123")
        chkType(type, "lib.f", listOf("[22]"), "[22, 11]", "e1:123", "lib.f:123")
        chkType(type, "lib.f", listOf("[22,33]"), "[22, 33, 11]", "e1:123", "lib.f:123")

        chkType(type, "lib.h", listOf("[]"), "[]", "e1:123")
        chkType(type, "lib.h", listOf("[22]"), "[22]", "e1:123")
        chkType(type, "lib.h", listOf("[22,33]"), "[22, 33]", "e1:123")

        chkType(type, "lib.f", listOf("[]", "[]"), "[11]", "e1:123", "e2:123", "lib.f:123")
        chkType(type, "lib.f", listOf("[]", "[33]"), "[33, 11]", "e1:123", "e2:123", "lib.f:123")
        chkType(type, "lib.f", listOf("[22]", "[]"), "[22, 11]", "e1:123", "e2:123", "lib.f:123")
        chkType(type, "lib.f", listOf("[22]", "[33]"), "[22, 33, 11]", "e1:123", "e2:123", "lib.f:123")
        chkType(type, "lib.f", listOf("[22,33]", "[44,55]"), "[22, 33, 44, 55, 11]", "e1:123", "e2:123", "lib.f:123")

        chkType(type, "lib.h", listOf("[]", "[]"), "[]", "e1:123", "e2:123")
        chkType(type, "lib.h", listOf("[]", "[33]"), "[33]", "e1:123", "e2:123")
        chkType(type, "lib.h", listOf("[22]", "[]"), "[22]", "e1:123", "e2:123")
        chkType(type, "lib.h", listOf("[22]", "[33]"), "[22, 33]", "e1:123", "e2:123")
        chkType(type, "lib.h", listOf("[22,33]", "[44,55]"), "[22, 33, 44, 55]", "e1:123", "e2:123")
    }

    @Test fun testTypeMap() {
        tst.strictToString = false
        file("lib.rell", """
            module;
            @extendable function f(x: integer): map<integer,text> { print('lib.f:'+x); return [1:'A']; }
            @extendable function h(x: integer): map<integer,text>;
        """)
        def("import lib;")

        chk("_type_of(lib.f(123))", "map<integer,text>")
        chk("_type_of(lib.h(123))", "map<integer,text>")

        val type = "map<integer,text>"

        chkType(type, "lib.f", listOf(), "{1=A}", "lib.f:123")
        chkType(type, "lib.h", listOf(), "{}")

        chkType(type, "lib.f", listOf("[:]"), "{1=A}", "e1:123", "lib.f:123")
        chkType(type, "lib.f", listOf("[2:'B']"), "{2=B, 1=A}", "e1:123", "lib.f:123")
        chkType(type, "lib.f", listOf("[2:'B',3:'C']"), "{2=B, 3=C, 1=A}", "e1:123", "lib.f:123")

        chkType(type, "lib.h", listOf("[:]"), "{}", "e1:123")
        chkType(type, "lib.h", listOf("[2:'B']"), "{2=B}", "e1:123")
        chkType(type, "lib.h", listOf("[2:'B',3:'C']"), "{2=B, 3=C}", "e1:123")

        chkType(type, "lib.f", listOf("[:]", "[:]"), "{1=A}", "e1:123", "e2:123", "lib.f:123")
        chkType(type, "lib.f", listOf("[:]", "[3:'C']"), "{3=C, 1=A}", "e1:123", "e2:123", "lib.f:123")
        chkType(type, "lib.f", listOf("[2:'B']", "[:]"), "{2=B, 1=A}", "e1:123", "e2:123", "lib.f:123")
        chkType(type, "lib.f", listOf("[2:'B']", "[3:'C']"), "{2=B, 3=C, 1=A}", "e1:123", "e2:123", "lib.f:123")
        chkType(type, "lib.f", listOf("[2:'B',3:'C']", "[4:'D',5:'E']"), "{2=B, 3=C, 4=D, 5=E, 1=A}",
                "e1:123", "e2:123", "lib.f:123")

        chkType(type, "lib.h", listOf("[:]", "[:]"), "{}", "e1:123", "e2:123")
        chkType(type, "lib.h", listOf("[:]", "[3:'C']"), "{3=C}", "e1:123", "e2:123")
        chkType(type, "lib.h", listOf("[2:'B']", "[:]"), "{2=B}", "e1:123", "e2:123")
        chkType(type, "lib.h", listOf("[2:'B']", "[3:'C']"), "{2=B, 3=C}", "e1:123", "e2:123")
        chkType(type, "lib.h", listOf("[2:'B',3:'C']", "[4:'D',5:'E']"), "{2=B, 3=C, 4=D, 5=E}", "e1:123", "e2:123")
    }

    @Test fun testTypeNullableBoolean() {
        file("lib.rell", """
            module;
            @extendable function f(x: integer): boolean? { print('lib.f:'+x); return true; }
            @extendable function h(x: integer): boolean?;
        """)
        def("import lib;")

        chk("_type_of(lib.f(123))", "text[boolean?]")
        chk("_type_of(lib.h(123))", "text[boolean?]")

        val type = "boolean?"

        chkType(type, "lib.f", listOf(), "boolean[true]", "lib.f:123")
        chkType(type, "lib.h", listOf(), "null")

        chkType(type, "lib.f", listOf("null", "null"), "boolean[true]", "e1:123", "e2:123", "lib.f:123")
        chkType(type, "lib.f", listOf("null", "false"), "boolean[false]", "e1:123", "e2:123")
        chkType(type, "lib.f", listOf("null", "true"), "boolean[true]", "e1:123", "e2:123")

        chkType(type, "lib.f", listOf("false", "null"), "boolean[false]", "e1:123")
        chkType(type, "lib.f", listOf("false", "false"), "boolean[false]", "e1:123")
        chkType(type, "lib.f", listOf("false", "true"), "boolean[false]", "e1:123")

        chkType(type, "lib.f", listOf("true", "null"), "boolean[true]", "e1:123")
        chkType(type, "lib.f", listOf("true", "false"), "boolean[true]", "e1:123")
        chkType(type, "lib.f", listOf("true", "true"), "boolean[true]", "e1:123")
    }

    @Test fun testTypeNullableList() {
        tst.strictToString = false
        file("lib.rell", """
            module;
            @extendable function f(x: integer): list<integer>? { print('lib.f:'+x); return [11]; }
            @extendable function h(x: integer): list<integer>?;
        """)
        def("import lib;")

        chk("_type_of(lib.f(123))", "list<integer>?")
        chk("_type_of(lib.h(123))", "list<integer>?")

        val type = "list<integer>?"

        chkType(type, "lib.f", listOf(), "[11]", "lib.f:123")
        chkType(type, "lib.h", listOf(), "null")

        chkType(type, "lib.f", listOf("null", "null"), "[11]", "e1:123", "e2:123", "lib.f:123")
        chkType(type, "lib.f", listOf("null", "[]"), "[]", "e1:123", "e2:123")
        chkType(type, "lib.f", listOf("null", "[22]"), "[22]", "e1:123", "e2:123")
        chkType(type, "lib.f", listOf("null", "[22,33]"), "[22, 33]", "e1:123", "e2:123")

        chkType(type, "lib.f", listOf("[]", "null"), "[]", "e1:123")
        chkType(type, "lib.f", listOf("[22]", "null"), "[22]", "e1:123")
        chkType(type, "lib.f", listOf("[22]", "[]"), "[22]", "e1:123")
        chkType(type, "lib.f", listOf("[22]", "[33]"), "[22]", "e1:123")
    }

    private fun chkType(type: String, baseFn: String, retValues: List<String>, expValue: String, vararg expOut: String) {
        val fnDefs = retValues.withIndex().joinToString("\n") { (i, v) ->
            val name = "e${i+1}"
            "@extend($baseFn) function $name(x: integer): $type { print('$name:'+x); return $v; }"
        }

        val code = "query q() = $baseFn(123); $fnDefs"
        chkFullOut(code, expValue, *expOut)
    }

    @Test fun testTypeInvalid() {
        def("enum color { red, green, blue }")
        def("struct s { x: integer; }")
        def("entity user { name; }")

        chkCompile("@extendable function f(): integer;", "ct_err:fn:extendable:type:integer")
        chkCompile("@extendable function f(): set<integer>;", "ct_err:fn:extendable:type:set<integer>")
        chkCompile("@extendable function f(): color;", "ct_err:fn:extendable:type:color")
        chkCompile("@extendable function f(): s;", "ct_err:fn:extendable:type:s")
        chkCompile("@extendable function f(): user;", "ct_err:fn:extendable:type:user")
        chkCompile("@extendable function f(): virtual<list<integer>>;", "ct_err:fn:extendable:type:virtual<list<integer>>")
    }

    @Test fun testMapKeyConflict() {
        tst.strictToString = false
        file("lib.rell", "module; @extendable function f(): map<integer,text> = [1:'A'];")
        def("import lib;")

        chkFull("@extend(lib.f) function f() = [2:'B']; query q() = lib.f();", "{2=B, 1=A}")
        chkFull("@extend(lib.f) function f() = [1:'B']; query q() = lib.f();",
                "rt_err:extendable_fn:map:key_conflict:int[1]:text[B]:text[A]")
        chkFull("@extend(lib.f) function f() = [1:'A']; query q() = lib.f();",
                "rt_err:extendable_fn:map:key_conflict:int[1]:text[A]:text[A]")
    }

    @Test fun testMapKeyConflictRecursiveStruct() {
        tst.strictToString = false
        file("lib.rell", """
            module;
            struct s { mutable next: s?; }
            @extendable function f(): map<integer,s> = [1:s(null)];
        """)
        def("import lib;")

        chkFull("""
            @extend(lib.f) function f() {
                val s1 = lib.s(null);
                val s2 = lib.s(s1);
                s1.next = s2;
                return [1:s1];
            }
            query q() = lib.f();
        """, "rt_err:extendable_fn:map:key_conflict:int[1]:lib:s[next=lib:s[next=lib:s[...]]]:lib:s[next=null]")
    }

    @Test fun testAnnotations() {
        chkCompile("@extendable('foo') function f() {}", "ct_err:ann:extendable:args:1")
        chkCompile("function f() {} @extendable(f) function g() {}", "ct_err:ann:extendable:args:1")

        chkCompile("@extendable function f(); @extend function g() {}", "ct_err:ann:extend:arg_count:0")
        chkCompile("@extendable function f(); @extend() function g() {}", "ct_err:ann:extend:arg_count:0")
        chkCompile("@extendable function f(); @extend('f') function g() {}", "ct_err:ann:arg:value_not_name:text[f]")
        chkCompile("@extendable function f(); @extend(123) function g() {}", "ct_err:ann:arg:value_not_name:int[123]")
        chkCompile("@extendable function f(); @extend(f, 123) function g() {}", "ct_err:ann:extend:arg_count:2")

        chkCompile("@extendable function f(); @extend(f) @extend(f) function g() {}", "ct_err:modifier:dup:ann:extend")
    }

    @Test fun testAnnotationsBadDef() {
        file("lib.rell", "module; @extendable function f() {}")
        chkAnnotationsBadDef("extendable", "")
        chkAnnotationsBadDef("extend", "(lib.f)")
    }

    private fun chkAnnotationsBadDef(name: String, args: String) {
        val ann = "@$name$args"
        chkCompile("$ann entity user { name; }", "ct_err:modifier:invalid:ann:$name")
        chkCompile("$ann object state { v: integer = 5; }", "ct_err:modifier:invalid:ann:$name")
        chkCompile("$ann enum color { red }", "ct_err:modifier:invalid:ann:$name")
        chkCompile("$ann struct rec { name; }", "ct_err:modifier:invalid:ann:$name")
        chkCompile("$ann query q() = 0;", "ct_err:modifier:invalid:ann:$name")
        chkCompile("$ann operation op(){}", "ct_err:modifier:invalid:ann:$name")
        chkCompile("$ann namespace ns {}", "ct_err:modifier:invalid:ann:$name")
        chkCompile("$ann import lib;", "ct_err:modifier:invalid:ann:$name")
        chkCompile("$ann val X = 5;", "ct_err:modifier:invalid:ann:$name")
    }

    @Test fun testAnnotationsConflict() {
        file("lib.rell", "module; @extendable function f() {}")
        def("import lib;")

        chkCompile("@extendable @extend(lib.f) function g() {}", "ct_err:modifier:bad_combination:ann:extendable,ann:extend")

        chkCompile("@extendable override function g() {}", "ct_err:[modifier:bad_combination:ann:extendable,kw:override][unknown_name:g]")
        chkCompile("@extendable abstract function g() {}",
                "ct_err:[modifier:bad_combination:ann:extendable,kw:abstract][fn:abstract:non_abstract_module::g]")

        chkCompile("@extend(lib.f) @extendable function g() {}", "ct_err:modifier:bad_combination:ann:extend,ann:extendable")

        chkCompile("@extend(lib.f) abstract function g() {}",
                "ct_err:[modifier:bad_combination:ann:extend,kw:abstract][fn:abstract:non_abstract_module::g]")
        chkCompile("@extend(lib.f) override function g() {}", "ct_err:[modifier:bad_combination:ann:extend,kw:override][unknown_name:g]")

        chkCompile("@extend(lib.f) @extendable abstract override function g() {}",
                "ct_err:[modifier:bad_combination:ann:extend,ann:extendable,kw:abstract,kw:override][fn:abstract:non_abstract_module::g]")
    }

    @Test fun testExtendTypeUnit() {
        chkExtendType("{}", "{}", "OK")
        chkExtendType("{}", "=true;", "ct_err:fn:extend:ret_type:g:[unit]:[boolean]")
        chkExtendType("{}", ":boolean=true;", "ct_err:fn:extend:ret_type:g:[unit]:[boolean]")
        chkExtendType("{}", "=123;", "ct_err:fn:extend:ret_type:g:[unit]:[integer]")
        chkExtendType("{}", ":integer=123;", "ct_err:fn:extend:ret_type:g:[unit]:[integer]")
        chkExtendType("{}", ":integer?=123;", "ct_err:fn:extend:ret_type:g:[unit]:[integer?]")
        chkExtendType("{}", "=[123];", "ct_err:fn:extend:ret_type:g:[unit]:[list<integer>]")
        chkExtendType("{}", ":list<integer> =[123];", "ct_err:fn:extend:ret_type:g:[unit]:[list<integer>]")

        chkExtendType(";", "{}", "OK")
        chkExtendType(";", "=true;", "ct_err:fn:extend:ret_type:g:[unit]:[boolean]")
        chkExtendType(";", ":boolean=true;", "ct_err:fn:extend:ret_type:g:[unit]:[boolean]")
    }

    @Test fun testExtendTypeBoolean() {
        chkExtendType("=true;", "=false;", "OK")
        chkExtendType("=true;", ":boolean=false;", "OK")
        chkExtendType("=true;", "{}", "ct_err:fn:extend:ret_type:g:[boolean]:[unit]")
        chkExtendType("=true;", ":integer?=123;", "ct_err:fn:extend:ret_type:g:[boolean]:[integer?]")
        chkExtendType("=true;", "=[123];", "ct_err:fn:extend:ret_type:g:[boolean]:[list<integer>]")
        chkExtendType("=true;", ":list<integer> =[123];", "ct_err:fn:extend:ret_type:g:[boolean]:[list<integer>]")

        chkExtendType(":boolean;", "=false;", "OK")
        chkExtendType(":boolean;", ":boolean=false;", "OK")
        chkExtendType(":boolean;", ":boolean?=true;", "ct_err:fn:extend:ret_type:g:[boolean]:[boolean?]")
        chkExtendType(":boolean;", ":integer?=123;", "ct_err:fn:extend:ret_type:g:[boolean]:[integer?]")
        chkExtendType(":boolean;", "=[123];", "ct_err:fn:extend:ret_type:g:[boolean]:[list<integer>]")
        chkExtendType(":boolean;", ":list<integer> =[123];", "ct_err:fn:extend:ret_type:g:[boolean]:[list<integer>]")

        chkExtendType(":boolean=true;", "=false;", "OK")
        chkExtendType(":boolean=true;", ":boolean=false;", "OK")
        chkExtendType(":boolean=true;", ":boolean?=true;", "ct_err:fn:extend:ret_type:g:[boolean]:[boolean?]")
        chkExtendType(":boolean=true;", ":integer?=123;", "ct_err:fn:extend:ret_type:g:[boolean]:[integer?]")
    }

    @Test fun testExtendTypeNullable() {
        chkExtendType("=_nullable_int(123);", "=123;", "OK")
        chkExtendType("=_nullable_int(123);", ":integer=123;", "OK")
        chkExtendType("=_nullable_int(123);", "=_nullable_int(123);", "OK")
        chkExtendType("=_nullable_int(123);", "=true;", "ct_err:fn:extend:ret_type:g:[integer?]:[boolean]")
        chkExtendType("=_nullable_int(123);", ":boolean=true;", "ct_err:fn:extend:ret_type:g:[integer?]:[boolean]")

        chkExtendType(":integer?;", "=123;", "OK")
        chkExtendType(":integer?;", ":integer=123;", "OK")
        chkExtendType(":integer?;", "=_nullable_int(123);", "OK")
        chkExtendType(":integer?;", "=true;", "ct_err:fn:extend:ret_type:g:[integer?]:[boolean]")
        chkExtendType(":integer?;", ":boolean=true;", "ct_err:fn:extend:ret_type:g:[integer?]:[boolean]")
    }

    @Test fun testExtendTypeList() {
        chkExtendType("=[123];", "=[123];", "OK")
        chkExtendType("=[123];", ":list<integer> =[123];", "OK")
        chkExtendType("=[123];", "=123;", "ct_err:fn:extend:ret_type:g:[list<integer>]:[integer]")
        chkExtendType("=[123];", ":integer=123;", "ct_err:fn:extend:ret_type:g:[list<integer>]:[integer]")
        chkExtendType("=[123];", "=true;", "ct_err:fn:extend:ret_type:g:[list<integer>]:[boolean]")
        chkExtendType("=[123];", ":boolean=true;", "ct_err:fn:extend:ret_type:g:[list<integer>]:[boolean]")
        chkExtendType("=[123];", ":list<integer>? =[123];", "ct_err:fn:extend:ret_type:g:[list<integer>]:[list<integer>?]")

        chkExtendType(":list<integer>;", "=[123];", "OK")
        chkExtendType(":list<integer>;", ":list<integer> =[123];", "OK")
        chkExtendType(":list<integer>;", "=123;", "ct_err:fn:extend:ret_type:g:[list<integer>]:[integer]")
        chkExtendType(":list<integer>;", ":integer=123;", "ct_err:fn:extend:ret_type:g:[list<integer>]:[integer]")
        chkExtendType(":list<integer>;", "=true;", "ct_err:fn:extend:ret_type:g:[list<integer>]:[boolean]")
        chkExtendType(":list<integer>;", ":boolean=true;", "ct_err:fn:extend:ret_type:g:[list<integer>]:[boolean]")
        chkExtendType(":list<integer>;", ":list<integer>? =[123];", "ct_err:fn:extend:ret_type:g:[list<integer>]:[list<integer>?]")
        chkExtendType(":list<integer?> =[];", ":list<integer> =[];", "ct_err:fn:extend:ret_type:g:[list<integer?>]:[list<integer>]")
    }

    @Test fun testExtendTypeMap() {
        chkExtendType("=[1:'A'];", "=[2:'B'];", "OK")
        chkExtendType("=[1:'A'];", ":map<integer,text> =[2:'B'];", "OK")
        chkExtendType("=[1:'A'];", "=123;", "ct_err:fn:extend:ret_type:g:[map<integer,text>]:[integer]")
        chkExtendType("=[1:'A'];", ":integer=123;", "ct_err:fn:extend:ret_type:g:[map<integer,text>]:[integer]")
        chkExtendType("=[1:'A'];", "=true;", "ct_err:fn:extend:ret_type:g:[map<integer,text>]:[boolean]")
        chkExtendType("=[1:'A'];", ":boolean=true;", "ct_err:fn:extend:ret_type:g:[map<integer,text>]:[boolean]")
        chkExtendType("=[1:'A'];", ":map<integer,text>? =[2:'B'];",
                "ct_err:fn:extend:ret_type:g:[map<integer,text>]:[map<integer,text>?]")

        chkExtendType(":map<integer,text>;", "=[2:'B'];", "OK")
        chkExtendType(":map<integer,text>;", ":map<integer,text> =[2:'B'];", "OK")
        chkExtendType(":map<integer,text>;", "=123;", "ct_err:fn:extend:ret_type:g:[map<integer,text>]:[integer]")
        chkExtendType(":map<integer,text>;", ":integer=123;", "ct_err:fn:extend:ret_type:g:[map<integer,text>]:[integer]")
        chkExtendType(":map<integer,text>;", "=true;", "ct_err:fn:extend:ret_type:g:[map<integer,text>]:[boolean]")
        chkExtendType(":map<integer,text>;", ":boolean=true;", "ct_err:fn:extend:ret_type:g:[map<integer,text>]:[boolean]")
        chkExtendType(":map<integer,text>;", ":map<integer,text>? =[:];",
                "ct_err:fn:extend:ret_type:g:[map<integer,text>]:[map<integer,text>?]")

        chkExtendType(":map<integer?,text> =[1:'A'];", ":map<integer,text> =[2:'B'];",
                "ct_err:fn:extend:ret_type:g:[map<integer?,text>]:[map<integer,text>]")
        chkExtendType(":map<integer,text?> =[1:'A'];", ":map<integer,text> =[2:'B'];",
                "ct_err:fn:extend:ret_type:g:[map<integer,text?>]:[map<integer,text>]")
    }

    private fun chkExtendType(base: String, ext: String, expected: String) {
        val baseDef = "@extendable function f()$base"
        val extDef = "@extend(f) function g()$ext"
        chkCompile("$baseDef $extDef", expected)
    }

    @Test fun testExtendFormalType() {
        tst.strictToString = false

        chkFull("@extendable function f() {} query q() = _type_of(f());", "unit")
        chkFull("@extendable function f() = true; query q() = _type_of(f());", "boolean")
        chkFull("@extendable function f() { return true; } query q() = _type_of(f());", "boolean")
        chkFull("@extendable function f() = [123]; query q() = _type_of(f());", "list<integer>")
        chkFull("@extendable function f() { return [123]; } query q() = _type_of(f());", "list<integer>")

        chkFull("@extendable function f(); query q() = _type_of(f());", "unit")
        chkFull("@extendable function f(): boolean; query q() = _type_of(f());", "boolean")
        chkFull("@extendable function f(): integer?; query q() = _type_of(f());", "integer?")

        chkFull("@extendable function f(): integer?; @extend(f) function g() = 123; query q() = _type_of(g());", "integer")
        chkFull("@extendable function f(): integer?; @extend(f) function g(): integer? = 123; query q() = _type_of(g());",
                "integer?")
    }

    @Test fun testExtendArguments() {
        def("@extendable function f0() {}")
        def("@extendable function f1(x: integer) {}")
        def("@extendable function f2(x: integer?) {}")
        def("@extendable function f3(x: decimal) {}")
        def("@extendable function f4(x: text, y: integer) {}")

        chkCompile("@extend(f0) function g() {}", "OK")
        chkCompile("@extend(f0) function g(x: integer) {}", "ct_err:fn:extend:param_cnt:g:0:1")

        chkCompile("@extend(f1) function g(x: integer) {}", "OK")
        chkCompile("@extend(f1) function g(y: integer) {}", "OK")
        chkCompile("@extend(f1) function g(x: integer?) {}", "ct_err:fn:extend:param_type:g:0:x:[integer]:[integer?]")
        chkCompile("@extend(f1) function g(x: text) {}", "ct_err:fn:extend:param_type:g:0:x:[integer]:[text]")
        chkCompile("@extend(f1) function g(x: decimal) {}", "ct_err:fn:extend:param_type:g:0:x:[integer]:[decimal]")
        chkCompile("@extend(f1) function g() {}", "ct_err:fn:extend:param_cnt:g:1:0")
        chkCompile("@extend(f1) function g(x: integer, y: text) {}", "ct_err:fn:extend:param_cnt:g:1:2")

        chkCompile("@extend(f2) function g(x: integer?) {}", "OK")
        chkCompile("@extend(f2) function g(x: integer) {}", "ct_err:fn:extend:param_type:g:0:x:[integer?]:[integer]")
        chkCompile("@extend(f2) function g() {}", "ct_err:fn:extend:param_cnt:g:1:0")
        chkCompile("@extend(f2) function g(x: integer?, y: text) {}", "ct_err:fn:extend:param_cnt:g:1:2")

        chkCompile("@extend(f3) function g(x: decimal) {}", "OK")
        chkCompile("@extend(f3) function g(x: integer) {}", "ct_err:fn:extend:param_type:g:0:x:[decimal]:[integer]")

        chkCompile("@extend(f4) function g(x: text, y: integer) {}", "OK")
        chkCompile("@extend(f4) function g(y: text, x: integer) {}", "OK")
        chkCompile("@extend(f4) function g(x: text?, y: integer) {}", "ct_err:fn:extend:param_type:g:0:x:[text]:[text?]")
        chkCompile("@extend(f4) function g(x: text, y: integer?) {}", "ct_err:fn:extend:param_type:g:1:y:[integer]:[integer?]")
        chkCompile("@extend(f4) function g(x: text?, y: integer?) {}",
                "ct_err:[fn:extend:param_type:g:0:x:[text]:[text?]][fn:extend:param_type:g:1:y:[integer]:[integer?]]")
        chkCompile("@extend(f4) function g(x: integer, y: text) {}",
                "ct_err:[fn:extend:param_type:g:0:x:[text]:[integer]][fn:extend:param_type:g:1:y:[integer]:[text]]")
        chkCompile("@extend(f4) function g(y: integer, x: text) {}",
                "ct_err:[fn:extend:param_type:g:0:x:[text]:[integer]][fn:extend:param_type:g:1:y:[integer]:[text]]")
        chkCompile("@extend(f4) function g(y: integer) {}",
                "ct_err:[fn:extend:param_cnt:g:2:1][fn:extend:param_type:g:0:x:[text]:[integer]]")
        chkCompile("@extend(f4) function g(x: text) {}", "ct_err:fn:extend:param_cnt:g:2:1")
        chkCompile("@extend(f4) function g() {}", "ct_err:fn:extend:param_cnt:g:2:0")
    }

    @Test fun testExtendSameModule() {
        def("@extendable function f() = [1];")
        def("@extend(f) function g() = [2];")

        chk("f()", "list<integer>[int[2],int[1]]")
        chk("g()", "list<integer>[int[2]]")
    }

    @Test fun testExtendBadFunction() {
        def("query q() = true;")
        def("operation op() {}")
        def("struct rec { x: integer; }")
        def("function g() {}")

        chkCompile("@extend(q) function f() = true;", "ct_err:wrong_name:function:query:q")
        chkCompile("@extend(op) function f() {}", "ct_err:wrong_name:function:operation:op")
        chkCompile("@extend(rec) function f(x: integer) {}", "ct_err:wrong_name:function:struct:rec")
        chkCompile("@extend(g) function f(x: integer) {}", "ct_err:fn:extend:not_extendable:g")

        chkCompile("@extend(abs) function f(x: integer) = x;", "ct_err:fn:extend:not_extendable:abs")
        chkCompile("@extend(op_context.is_signer) function f(b: byte_array) = true;",
                "ct_err:fn:extend:not_extendable:op_context.is_signer")
        chkCompile("@extend(exists) function f() = true;", "ct_err:fn:extend:not_extendable:exists")
        chkCompile("@extend(_type_of) function f() = true;", "ct_err:fn:extend:not_extendable:_type_of")
    }

    @Test fun testExtendAbstractFunction() {
        file("lib.rell", "abstract module; abstract function f();")
        chkCompile("import lib; @extend(lib.f) function g() {}",
                "ct_err:[override:missing:[lib:f]:[lib.rell:1]][fn:extend:not_extendable:lib.f]")
    }

    @Test fun testExtendIndirectImport() {
        file("lib.rell", "module; @extendable function f() = ['f'];")
        file("mid.rell", "module; import lib.{f};")
        def("import lib;")
        def("import mid;")
        def("@extend(mid.f) function g() = ['g'];")
        chk("lib.f()", "list<text>[text[g],text[f]]")
    }

    @Test fun testExtendSpecificImport() {
        tst.strictToString = false
        file("lib.rell", "module; @extendable function f() = ['f'];")
        file("ext.rell", "module; import lib; @extend(lib.f) function g() = ['g']; function h() = ['h'];")

        chkFull("import lib; query q() = lib.f();", "[f]")
        chkFull("import lib; import ext; query q() = lib.f();", "[g, f]")
        chkFull("import lib; import ext.*; query q() = lib.f();", "[g, f]")
        chkFull("import lib; import ext.{g}; query q() = lib.f();", "[g, f]")
        chkFull("import lib; import ext.{h}; query q() = lib.f();", "[g, f]")
    }

    @Test fun testRecursion() {
        tst.strictToString = false
        def("@extendable function f(x: integer): list<integer> = [];")
        def("function concat(a: list<integer>, b: list<integer>): list<integer> { a.add_all(b); return a; }")
        def("@extend(f) function g(x: integer): list<integer> = if (x == 0) [] else concat([x*x], f(x-1));")
        def("@extend(f) function h(x: integer): list<integer> = if (x == 0) [] else concat([x*x*x], f(x-1));")

        chk("f(0)", "[]")
        chk("f(1)", "[1, 1]")
        chk("f(2)", "[4, 1, 1, 8, 1, 1]")
        chk("f(3)", "[9, 4, 1, 1, 8, 1, 1, 27, 4, 1, 1, 8, 1, 1]")
        chk("f(4)", "[16, 9, 4, 1, 1, 8, 1, 1, 27, 4, 1, 1, 8, 1, 1, 64, 9, 4, 1, 1, 8, 1, 1, 27, 4, 1, 1, 8, 1, 1]")
    }

    @Test fun testFunctionForm() {
        def("@extendable function f() {}")
        chkCompile("@extendable function a.b() {}", "ct_err:fn:qname_no_override:a.b")
        chkCompile("@extend(f) function a.b() {}", "ct_err:fn:qname_no_override:a.b")
        chkCompile("@extendable function a();", "OK")
        chkCompile("@extend(f) function a();", "ct_err:fn:no_body:a")
    }

    @Test fun testImplicitReturnType() {
        tst.strictToString = false
        def("@extendable function f1();")
        def("@extendable function f2() {}")
        def("@extendable function f3(): list<integer>;")
        def("@extendable function f4() = [123];")
        def("@extendable function f5() { return [123]; }")

        chk("_type_of(f1())", "unit")
        chk("_type_of(f2())", "unit")
        chk("_type_of(f3())", "list<integer>")
        chk("_type_of(f4())", "list<integer>")
        chk("_type_of(f5())", "list<integer>")
    }

    @Test fun testReplExtend() {
        file("lib.rell", "module; @extendable function f() = ['f'];")

        repl.chk("import lib;")
        repl.chk("lib.f()", "RES:list<text>[text[f]]")

        repl.chk("@extend(lib.f) function g() = ['g'];")
        repl.chk("lib.f()", "RES:list<text>[text[g],text[f]]")

        repl.chk("@extend(lib.f) function h() = ['h'];")
        repl.chk("lib.f()", "RES:list<text>[text[g],text[h],text[f]]")
    }

    @Test fun testReplExtendable() {
        repl.chk("@extendable function f() = ['f'];")
        repl.chk("f()", "RES:list<text>[text[f]]")

        repl.chk("@extend(f) function g() = ['g'];")
        repl.chk("f()", "RES:list<text>[text[g],text[f]]")

        repl.chk("@extend(f) function h() = ['h'];")
        repl.chk("f()", "RES:list<text>[text[g],text[h],text[f]]")
    }

    @Test fun testReplMainModule() {
        file("lib.rell", "module; @extendable function f() { print('f'); }")
        file("foo.rell", "module; import lib; @extend(lib.f) function g() { print('g'); }")
        file("bar.rell", "module; import lib; @extend(lib.f) function h() { print('h'); }")
        file("app.rell", "module; import foo;")

        repl.chk("import lib;")
        repl.chk("lib.f()", "OUT:f", "RES:unit")

        tst.mainModule("app")
        repl.chk("lib.f()", "OUT:f", "RES:unit")

        repl.chk("import bar;")
        repl.chk("lib.f()", "OUT:h", "OUT:f", "RES:unit")

        repl.chk("import foo;")
        repl.chk("lib.f()", "OUT:h", "OUT:g", "OUT:f", "RES:unit")
    }

    @Test fun testNoExtend() {
        def("@extendable function a1(x: integer) { print('a1:'+x); }")
        def("@extendable function a2(x: integer);")
        def("@extendable function b1() = true;")
        def("@extendable function b2(): boolean;")
        def("@extendable function c1(): integer? = 123;")
        def("@extendable function c2(): integer?;")
        def("@extendable function d1(): list<integer> = [123];")
        def("@extendable function d2(): list<integer>;")

        chkFn("{ a1(123); }", "unit")
        chkOut("a1:123")
        chkFn("{ a2(123); }", "unit")
        chkOut()

        chk("b1()", "boolean[true]")
        chk("b2()", "boolean[false]")
        chk("c1()", "int[123]")
        chk("c2()", "null")
        chk("d1()", "list<integer>[int[123]]")
        chk("d2()", "list<integer>[]")
    }

    @Test fun testCallExtendDirectly() {
        file("lib.rell", "module; @extendable function f() = [123];")
        def("import lib;")
        def("@extend(lib.f) function g() = [456];")

        chk("lib.f()", "list<integer>[int[456],int[123]]")
        chk("g()", "list<integer>[int[456]]")
    }

    @Test fun testCallExtendableFromOtherModule() {
        tst.strictToString = false
        file("lib.rell", "module; @extendable function f() = [123];")
        file("sub.rell", "module; import lib; function g() = lib.f();")
        file("ext.rell", "module; import lib; @extend(lib.f) function h() = [456];")

        chkFull("import sub; query q() = sub.g();", "[123]")
        chkFull("import sub; import ext; query q() = sub.g();", "[456, 123]")
        chkFull("import ext; import sub; query q() = sub.g();", "[456, 123]")
    }

    @Test fun testCallArguments() {
        file("lib.rell", """
            module;
            @extendable function f() = [123];
            @extendable function g(x: integer) = [123];
            @extendable function h(x: text, y: boolean) = [123];
        """)
        def("import lib;")

        chk("lib.f()", "list<integer>[int[123]]")
        chk("lib.f(123)", "ct_err:expr:call:too_many_args:[f]:0:1")
        chk("lib.f(true)", "ct_err:expr:call:too_many_args:[f]:0:1")
        chk("lib.f('A', false)", "ct_err:expr:call:too_many_args:[f]:0:2")

        chk("lib.g(1)", "list<integer>[int[123]]")
        chk("lib.g(x = 1)", "list<integer>[int[123]]")
        chk("lib.g()", "ct_err:expr:call:missing_args:[g]:[0:x]")
        chk("lib.g('A')", "ct_err:expr_call_argtype:[g]:0:x:integer:text")
        chk("lib.g(x = 'A')", "ct_err:expr_call_argtype:[g]:0:x:integer:text")
        chk("lib.g(true)", "ct_err:expr_call_argtype:[g]:0:x:integer:boolean")
        chk("lib.g(1, 'A')", "ct_err:expr:call:too_many_args:[g]:1:2")

        chk("lib.h('A', true)", "list<integer>[int[123]]")
        chk("lib.h(x = 'A', y = true)", "list<integer>[int[123]]")
        chk("lib.h(y = true, x = 'A')", "list<integer>[int[123]]")
        chk("lib.h()", "ct_err:expr:call:missing_args:[h]:[0:x,1:y]")
        chk("lib.h('A')", "ct_err:expr:call:missing_args:[h]:[1:y]")
        chk("lib.h(x = 'A')", "ct_err:expr:call:missing_args:[h]:[1:y]")
        chk("lib.h(true)", "ct_err:[expr:call:missing_args:[h]:[1:y]][expr_call_argtype:[h]:0:x:text:boolean]")
        chk("lib.h(x = true)", "ct_err:[expr:call:missing_args:[h]:[1:y]][expr_call_argtype:[h]:0:x:text:boolean]")
        chk("lib.h(y = true)", "ct_err:expr:call:missing_args:[h]:[0:x]")
        chk("lib.h('A', 1)", "ct_err:expr_call_argtype:[h]:1:y:boolean:integer")
        chk("lib.h(true, 'A')", "ct_err:[expr_call_argtype:[h]:0:x:text:boolean][expr_call_argtype:[h]:1:y:boolean:text]")
        chk("lib.h('A', true, 1)", "ct_err:expr:call:too_many_args:[h]:2:3")
    }

    @Test fun testInvocationOrder() {
        tst.strictToString = false
        file("lib.rell", "module; @extendable function z() = ['z'];")
        file("a.rell", "module; import lib; @extend(lib.z) function a() = ['a'];")
        file("b.rell", "module; import lib; @extend(lib.z) function b() = ['b'];")
        file("c.rell", "module; import lib; import a; @extend(lib.z) function c() = ['c'];")
        file("d.rell", "module; import lib; @extend(lib.z) function d() = ['d']; import b;")
        file("e.rell", "module; import lib; import a; import f; @extend(lib.z) function e() = ['e'];")
        file("f.rell", "module; import lib; import b; @extend(lib.z) function f() = ['f']; import e;")

        chkFull("import lib; query q() = lib.z();", "[z]")

        chkInvOrder("a", "[a, z]")
        chkInvOrder("b", "[b, z]")
        chkInvOrder("c", "[c, a, z]")
        chkInvOrder("d", "[d, b, z]")
        chkInvOrder("e", "[e, a, f, b, z]")
        chkInvOrder("f", "[f, b, e, a, z]")

        chkInvOrder("a,b", "[a, b, z]")
        chkInvOrder("b,a", "[b, a, z]")
        chkInvOrder("a,c", "[a, c, z]")
        chkInvOrder("c,a", "[c, a, z]")
        chkInvOrder("a,d", "[a, d, b, z]")
        chkInvOrder("d,a", "[d, a, b, z]")
        chkInvOrder("a,e", "[a, e, f, b, z]")
        chkInvOrder("e,a", "[e, a, f, b, z]")
        chkInvOrder("a,f", "[a, f, b, e, z]")
        chkInvOrder("f,a", "[f, a, b, e, z]")
    }

    private fun chkInvOrder(imports: String, expected: String) {
        val importsCode = imports.split(",").joinToString(" ") { "import $it;" }
        chkFull("import lib; $importsCode query q() = lib.z();", expected)
    }

    @Test fun testTestModule() {
        file("lib.rell", "module; @extendable function f() {}")
        tst.mainModule("lib")
        tst.testModules("")

        chkCompile("@test module; @extendable function f() {}", "ct_err:def_test:fn_extendable:main:f")
        chkCompile("@test module; import lib; @extend(lib.f) function g() {}", "ct_err:def_test:fn_extend:main:g")
        chkCompile("@test module; @extendable function f() {} @extend(f) function g() {}",
                "ct_err:[def_test:fn_extendable:main:f][def_test:fn_extend:main:g]")
    }

    @Test fun testTestModuleMainModule() {
        file("lib.rell", "module; @extendable function f() { print('f'); }")
        file("foo.rell", "module; import lib; @extend(lib.f) function g() { print('g'); }")
        file("bar.rell", "module; import lib; @extend(lib.f) function h() { print('h'); }")
        file("app.rell", "module; import foo;")
        file("test1.rell", "@test module; import lib; function test_1() { lib.f(); }")
        file("test2.rell", "@test module; import lib; import bar; function test_2() { lib.f(); }")

        tst.mainModule("lib")
        tst.chkTests("test1", "test_1=OK")
        chkOut("f")
        tst.chkTests("test2", "test_2=OK")
        chkOut("f")

        tst.mainModule("app")
        tst.chkTests("test1", "test_1=OK")
        chkOut("g", "f")
        tst.chkTests("test2", "test_2=OK")
        chkOut("g", "f")
    }

    @Test fun testStackTrace() {
        file("lib.rell", "module; @extendable function f(x: integer) { require(x > 0); }")
        file("ext.rell", "module; import lib; @extend(lib.f) function g(x: integer) { require(x > 1); }")

        chkFull("import lib; query q() { lib.f(0); return 0; }", "req_err:null")
        chkStack("lib:f(lib.rell:1)", ":q(main.rell:1)")

        chkFull("import lib; import ext; query q() { lib.f(1); return 0; }", "req_err:null")
        chkStack("ext:g(ext.rell:1)", ":q(main.rell:1)")
    }

    @Test fun testNamelessFunction() {
        tst.strictToString = false
        file("lib.rell", "module; @extendable function f() = ['lib.f'];")
        file("foo.rell", "module; import lib; @extend(lib.f) function() = ['foo'];")
        file("bar.rell", "module; import lib; @extend(lib.f) function() = ['bar'];")
        def("import lib; import foo; import bar;")
        chk("lib.f()", "[foo, bar, lib.f]")
    }

    @Test fun testNamelessFunctionStackTrace() {
        file("lib.rell", "module; @extendable function f(x: integer) = false;")

        file("foo.rell", """
            module;
            import lib;
            @extend(lib.f) function(x: integer) { require(x != 1); return false; }
        """)

        file("bar.rell", """
            module;
            import lib;
            @extend(lib.f) function(x: integer) { require(x != 2); return false; }
            namespace a {
                @extend(lib.f) function(x: integer) { require(x != 3); return false; }
                namespace b {
                    @extend(lib.f) function(x: integer) { require(x != 4); return false; }
                }
            }
            @extend(lib.f) function g(x: integer) { require(x != 5); return false; }
            namespace {
                @extend(lib.f) function(x: integer) { require(x != 6); return false; }
            }
            namespace a {
                @extend(lib.f) function(x: integer) { require(x != 7); return false; }
            }
        """)

        def("import lib; import foo; import bar;")

        chk("lib.f(0)", "boolean[false]")

        chkNamelessStack("lib.f(1)", "foo:function#0(foo.rell:4)")
        chkNamelessStack("lib.f(2)", "bar:function#0(bar.rell:4)")
        chkNamelessStack("lib.f(3)", "bar:a.function#0(bar.rell:6)")
        chkNamelessStack("lib.f(4)", "bar:a.b.function#0(bar.rell:8)")
        chkNamelessStack("lib.f(5)", "bar:g(bar.rell:11)")
        chkNamelessStack("lib.f(6)", "bar:function#1(bar.rell:13)")
        chkNamelessStack("lib.f(7)", "bar:a.function#1(bar.rell:16)")
    }

    private fun chkNamelessStack(expr: String, expectedStack: String) {
        chk(expr, "req_err:null")
        chkStack(expectedStack, ":q(main.rell:2)")
    }

    @Test fun testExternalModule() {
        file("lib.rell", "@external module; @extendable function f() {}")
        file("ext.rell", "@external module; @extend(f) function g() {}")
        chkCompile("import lib;", "ct_err:lib.rell:def_external:module:FUNCTION")
        chkCompile("import ext;", "ct_err:ext.rell:def_external:module:FUNCTION")
    }

    @Test fun testDefaultParameterValues() {
        tst.strictToString = false
        file("lib.rell", """
            module;
            @extendable function f(x: integer = 123) = [x];
            @extendable function g(x: integer) = [x];
        """)
        def("import lib;")

        chkFull("@extend(lib.f) function h(x: integer) = [x + 1]; query q() = lib.f(456);", "[457, 456]")
        chkFull("@extend(lib.f) function h(x: integer) = [x + 1]; query q() = lib.f();", "[124, 123]")
        chkFull("@extend(lib.f) function h(x: integer = 456) = [x + 1]; query q() = lib.f(789);", "[790, 789]")
        chkFull("@extend(lib.f) function h(x: integer = 456) = [x + 1]; query q() = lib.f();", "[124, 123]")
        chkFull("@extend(lib.f) function h(x: integer = 456) = [x + 1]; query q() = h();", "[457]")
        chkFull("@extend(lib.f) function h() = [123]; query q() = lib.f();", "ct_err:fn:extend:param_cnt:h:1:0")

        chkFull("@extend(lib.g) function h(x: integer = 456) = [x + 1]; query q() = lib.g(123);", "[124, 123]")
        chkFull("@extend(lib.g) function h(x: integer = 456) = [x + 1]; query q() = lib.g();",
            "ct_err:expr:call:missing_args:[g]:[0:x]")
        chkFull("@extend(lib.g) function h(x: integer = 456) = [x + 1]; query q() = h();", "[457]")
    }

    @Test fun testBugNoBodyGetAppStructure() {
        tst.strictToString = false
        file("lib.rell", "module; @extendable function f(x: integer): boolean;")
        def("import lib;")
        chk("_type_of(lib.f(123))", "boolean")
        chkFull("", "rell.get_app_structure", listOf(),
            """{"modules":{"":{"name":""},"lib":{"functions":{"f":{"parameters":[{"name":"x","type":"integer"}],"type":"boolean"}},"name":"lib"}}}"""
        )
    }

    @Test fun testUnknownFunction() {
        chkFull("@extend(foo) function g() {}", "ct_err:unknown_name:foo")
        chkFull("@extend(foo.bar) function g() {}", "ct_err:unknown_name:foo")
    }

    private fun chkFullOut(code: String, expectedRes: String, vararg expectedOut: String) {
        chkOut()
        chkFull(code, expectedRes)
        chkOut(*expectedOut)
    }
}
