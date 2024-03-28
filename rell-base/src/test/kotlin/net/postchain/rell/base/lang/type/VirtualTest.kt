/*
 * Copyright (C) 2023 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.type

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvNull
import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.testutils.GtvTestUtils
import net.postchain.rell.base.testutils.VirtualTestUtils
import org.junit.Test

class VirtualTest: BaseRellTest(false) {
    init {
        tst.strictToString = false
        tst.gtvResult = true
    }

    @Test fun testAllowedTypes() {
        def("struct rec { x: integer; }")
        def("struct vir { y: virtual<rec>; }")
        def("struct nogtv { z: range; }")
        def("enum E {A,B,C}")
        def("entity user { name; }")
        def("struct novir { m: map<integer,text>; }")
        def("struct cycle { mutable next: cycle?; m: map<integer,text>; }")

        chkAllowedType("virtual<list<integer>>", "OK")
        chkAllowedType("virtual<set<integer>>", "OK")
        chkAllowedType("virtual<map<text,integer>>", "OK")
        chkAllowedType("virtual<map<integer,text>>", "ct_err:type:virtual:bad_inner_type:map<integer,text>")
        chkAllowedType("virtual<rec>", "OK")
        chkAllowedType("virtual<rec?>", "ct_err:type:virtual:bad_inner_type:rec?")
        chkAllowedType("virtual<(integer,text)>", "OK")
        chkAllowedType("virtual<(a:integer,b:text)>", "OK")
        chkAllowedType("virtual<(a:integer,text)>", "OK")
        chkAllowedType("virtual<(integer,b:text)>", "OK")
        chkAllowedType("virtual<(integer,)>", "OK")
        chkAllowedType("virtual<list<map<text,(rec,integer)>>>", "OK")
        chkAllowedType("virtual<list<integer?>>", "OK")
        chkAllowedType("virtual<list<gtv>>", "OK")
        chkAllowedType("virtual<list<virtual<rec>>>", "OK")
        chkAllowedType("virtual<set<virtual<rec>>>", "OK")
        chkAllowedType("virtual<list<vir>>", "OK")
        chkAllowedType("virtual<set<vir>>", "OK")
        chkAllowedType("virtual<vir>", "OK")
        chkAllowedType("virtual<(set<integer>)>", "OK")
        chkAllowedType("virtual<list<set<integer>>>", "OK")
        chkAllowedType("virtual<list<map<text,set<integer>>>>", "OK")

        chkAllowedType("virtual<integer>", "ct_err:type:virtual:bad_inner_type:integer")
        chkAllowedType("virtual<boolean>", "ct_err:type:virtual:bad_inner_type:boolean")
        chkAllowedType("virtual<range>", "ct_err:type:virtual:bad_inner_type:range")
        chkAllowedType("virtual<list<range>>", "ct_err:type:virtual:bad_inner_type:list<range>")
        chkAllowedType("virtual<set<range>>", "ct_err:type:virtual:bad_inner_type:set<range>")
        chkAllowedType("virtual<integer?>", "ct_err:type:virtual:bad_inner_type:integer?")
        chkAllowedType("virtual<list<integer>?>", "ct_err:type:virtual:bad_inner_type:list<integer>?")
        chkAllowedType("virtual<gtv>", "ct_err:type:virtual:bad_inner_type:gtv")
        chkAllowedType("virtual<E>", "ct_err:type:virtual:bad_inner_type:E")
        chkAllowedType("virtual<user>", "ct_err:type:virtual:bad_inner_type:user")
        chkAllowedType("virtual<nogtv>", "ct_err:type:virtual:bad_inner_type:nogtv")
        chkAllowedType("virtual<list<nogtv>>", "ct_err:type:virtual:bad_inner_type:list<nogtv>")
        chkAllowedType("virtual<set<nogtv>>", "ct_err:type:virtual:bad_inner_type:set<nogtv>")
        chkAllowedType("virtual<virtual<rec>>", "ct_err:type:virtual:bad_inner_type:virtual<rec>")
        chkAllowedType("virtual<novir>", "ct_err:type:virtual:bad_inner_type:novir")
        chkAllowedType("virtual<cycle>", "ct_err:type:virtual:bad_inner_type:cycle")

        chkAllowedType("virtual<list<map<integer,text>>>", "ct_err:type:virtual:bad_inner_type:list<map<integer,text>>")
        chkAllowedType("virtual<list<map<integer,(rec,integer)>>>",
                "ct_err:type:virtual:bad_inner_type:list<map<integer,(rec,integer)>>")

        chkAllowedType("virtual<(integer)->text>", "ct_err:type:virtual:bad_inner_type:(integer)->text")
        chkAllowedType("virtual<list<(integer)->text>>", "ct_err:type:virtual:bad_inner_type:list<(integer)->text>")
    }

    private fun chkAllowedType(type: String, expected: String) {
        chkCompile("operation o(a: $type){}", expected)
    }

    @Test fun testStructType() {
        def("struct rec { i: integer; t: text; s: sub; }")
        def("struct sub { q: integer; p: sub2; }")
        def("struct sub2 { r: integer; }")

        val args = GtvTestUtils.decodeGtvStr("[123,'Hello',[456,[789]]]")
        chkVirtual("rec", "x", args, "{'i':123,'s':{'p':{'r':789},'q':456},'t':'Hello'}")
        chkVirtual("rec", "x.i", args, "123")
        chkVirtual("rec", "x.t", args, "'Hello'")
        chkVirtual("rec", "x.s", args, "{'p':{'r':789},'q':456}")
        chkVirtual("rec", "x.s.q", args, "456")
        chkVirtual("rec", "x.s.p", args, "{'r':789}")
        chkVirtual("rec", "x.s.p.r", args, "789")

        val virArgs = argToGtv("[123,'Hello',[456,[789]]]", "[[0],[1],[2]]")
        chkVirtual("virtual<rec>", "_type_of(x)", virArgs, "'virtual<rec>'")
        chkVirtual("virtual<rec>", "_type_of(x.to_full())", virArgs, "'rec'")
        chkVirtual("virtual<rec>", "_type_of(x.i)", virArgs, "'integer'")
        chkVirtual("virtual<rec>", "_type_of(x.t)", virArgs, "'text'")
        chkVirtual("virtual<rec>", "_type_of(x.s)", virArgs, "'virtual<sub>'")
        chkVirtual("virtual<rec>", "_type_of(x.s.to_full())", virArgs, "'sub'")
        chkVirtual("virtual<rec>", "_type_of(x.s.q)", virArgs, "'integer'")
        chkVirtual("virtual<rec>", "_type_of(x.s.p)", virArgs, "'virtual<sub2>'")
        chkVirtual("virtual<rec>", "_type_of(x.s.p.to_full())", virArgs, "'sub2'")
        chkVirtual("virtual<rec>", "_type_of(x.s.p.r)", virArgs, "'integer'")

        chkVirtual("virtual<rec>", "x.to_gtv()", virArgs, "ct_err:fn:invalid:virtual<rec>:to_gtv")
        chkVirtual("virtual<rec>", "x.s.to_gtv()", virArgs, "ct_err:fn:invalid:virtual<sub>:to_gtv")
        chkVirtual("virtual<rec>", "x.s.to_full().to_gtv()", virArgs, "[456,[789]]")

        chkVirtual("virtual<rec>?", "_type_of(x)", virArgs, "'virtual<rec>?'")
        chkVirtual("virtual<rec>?", "_type_of(x?.s)", virArgs, "'virtual<sub>?'")
        chkVirtual("virtual<rec>?", "_type_of(x!!.s)", virArgs, "'virtual<sub>'")
    }

    @Test fun testStructAttrsFull() {
        def("struct rec { i: integer; t: text; s: sub; }")
        def("struct sub { q: integer; p: sub2; }")
        def("struct sub2 { r: integer; }")

        val args = argToGtv("[123,'Hello',[456,[789]]]", "[[0],[1],[2]]")
        chkVirtual("virtual<rec>", "_strict_str(x)", args,
                "'virtual<rec>[i=int[123],t=text[Hello],s=virtual<sub>[q=int[456],p=virtual<sub2>[r=int[789]]]]'")
        chkVirtual("virtual<rec>", "_strict_str(x.i)", args, "'int[123]'")
        chkVirtual("virtual<rec>", "_strict_str(x.t)", args, "'text[Hello]'")
        chkVirtual("virtual<rec>", "_strict_str(x.s)", args, "'virtual<sub>[q=int[456],p=virtual<sub2>[r=int[789]]]'")
        chkVirtual("virtual<rec>", "_strict_str(x.s.q)", args, "'int[456]'")
        chkVirtual("virtual<rec>", "_strict_str(x.s.p)", args, "'virtual<sub2>[r=int[789]]'")
        chkVirtual("virtual<rec>", "_strict_str(x.s.p.r)", args, "'int[789]'")
    }

    @Test fun testStructAttrsPart() {
        def("struct rec { i: integer; t: text; s: sub; }")
        def("struct sub { q: integer; p: sub2; }")
        def("struct sub2 { r: integer; }")

        var args = argToGtv("[123,'Hello',[456,[789]]]", "[[0]]")
        chkVirtual("virtual<rec>", "_strict_str(x)", args, "'virtual<rec>[i=int[123],t=null,s=null]'")
        chkVirtual("virtual<rec>", "_strict_str(x.i)", args, "'int[123]'")
        chkVirtual("virtual<rec>", "_strict_str(x.t)", args, "rt_err:virtual_struct:get:novalue:rec:t")
        chkVirtual("virtual<rec>", "_strict_str(x.s)", args, "rt_err:virtual_struct:get:novalue:rec:s")

        args = argToGtv("[123,'Hello',[456,[789]]]", "[[1]]")
        chkVirtual("virtual<rec>", "_strict_str(x)", args, "'virtual<rec>[i=null,t=text[Hello],s=null]'")
        chkVirtual("virtual<rec>", "_strict_str(x.i)", args, "rt_err:virtual_struct:get:novalue:rec:i")
        chkVirtual("virtual<rec>", "_strict_str(x.t)", args, "'text[Hello]'")
        chkVirtual("virtual<rec>", "_strict_str(x.s)", args, "rt_err:virtual_struct:get:novalue:rec:s")

        args = argToGtv("[123,'Hello',[456,[789]]]", "[[2]]")
        chkVirtual("virtual<rec>", "_strict_str(x)", args,
                "'virtual<rec>[i=null,t=null,s=virtual<sub>[q=int[456],p=virtual<sub2>[r=int[789]]]]'")
        chkVirtual("virtual<rec>", "_strict_str(x.i)", args, "rt_err:virtual_struct:get:novalue:rec:i")
        chkVirtual("virtual<rec>", "_strict_str(x.t)", args, "rt_err:virtual_struct:get:novalue:rec:t")
        chkVirtual("virtual<rec>", "_strict_str(x.s)", args, "'virtual<sub>[q=int[456],p=virtual<sub2>[r=int[789]]]'")

        args = argToGtv("[123,'Hello',[456,[789]]]", "[[0],[1]]")
        chkVirtual("virtual<rec>", "_strict_str(x)", args, "'virtual<rec>[i=int[123],t=text[Hello],s=null]'")
        chkVirtual("virtual<rec>", "_strict_str(x.i)", args, "'int[123]'")
        chkVirtual("virtual<rec>", "_strict_str(x.t)", args, "'text[Hello]'")
        chkVirtual("virtual<rec>", "_strict_str(x.s)", args, "rt_err:virtual_struct:get:novalue:rec:s")

        args = argToGtv("[123,'Hello',[456,[789]]]", "[[0],[2]]")
        chkVirtual("virtual<rec>", "_strict_str(x)", args,
                "'virtual<rec>[i=int[123],t=null,s=virtual<sub>[q=int[456],p=virtual<sub2>[r=int[789]]]]'")
        chkVirtual("virtual<rec>", "_strict_str(x.i)", args, "'int[123]'")
        chkVirtual("virtual<rec>", "_strict_str(x.t)", args, "rt_err:virtual_struct:get:novalue:rec:t")
        chkVirtual("virtual<rec>", "_strict_str(x.s)", args, "'virtual<sub>[q=int[456],p=virtual<sub2>[r=int[789]]]'")

        args = argToGtv("[123,'Hello',[456,[789]]]", "[[1],[2]]")
        chkVirtual("virtual<rec>", "_strict_str(x)", args,
                "'virtual<rec>[i=null,t=text[Hello],s=virtual<sub>[q=int[456],p=virtual<sub2>[r=int[789]]]]'")
        chkVirtual("virtual<rec>", "_strict_str(x.i)", args, "rt_err:virtual_struct:get:novalue:rec:i")
        chkVirtual("virtual<rec>", "_strict_str(x.t)", args, "'text[Hello]'")
        chkVirtual("virtual<rec>", "_strict_str(x.s)", args, "'virtual<sub>[q=int[456],p=virtual<sub2>[r=int[789]]]'")
    }

    @Test fun testStructAttrsPartNested() {
        def("struct rec { i: integer; t: text; s: sub; }")
        def("struct sub { q: integer; p: sub2; }")
        def("struct sub2 { r: integer; }")

        var args = argToGtv("[123,'Hello',[456,[789]]]", "[[2,0]]")
        chkVirtual("virtual<rec>", "_strict_str(x)", args,
                "'virtual<rec>[i=null,t=null,s=virtual<sub>[q=int[456],p=null]]'")
        chkVirtual("virtual<rec>", "_strict_str(x.i)", args, "rt_err:virtual_struct:get:novalue:rec:i")
        chkVirtual("virtual<rec>", "_strict_str(x.t)", args, "rt_err:virtual_struct:get:novalue:rec:t")
        chkVirtual("virtual<rec>", "_strict_str(x.s)", args, "'virtual<sub>[q=int[456],p=null]'")
        chkVirtual("virtual<rec>", "_strict_str(x.s.q)", args, "'int[456]'")
        chkVirtual("virtual<rec>", "_strict_str(x.s.p)", args, "rt_err:virtual_struct:get:novalue:sub:p")

        args = argToGtv("[123,'Hello',[456,[789]]]", "[[2,1]]")
        chkVirtual("virtual<rec>", "_strict_str(x)", args,
                "'virtual<rec>[i=null,t=null,s=virtual<sub>[q=null,p=virtual<sub2>[r=int[789]]]]'")
        chkVirtual("virtual<rec>", "_strict_str(x.s)", args, "'virtual<sub>[q=null,p=virtual<sub2>[r=int[789]]]'")
        chkVirtual("virtual<rec>", "_strict_str(x.s.q)", args, "rt_err:virtual_struct:get:novalue:sub:q")
        chkVirtual("virtual<rec>", "_strict_str(x.s.p)", args, "'virtual<sub2>[r=int[789]]'")
        chkVirtual("virtual<rec>", "_strict_str(x.s.p.r)", args, "'int[789]'")

        args = argToGtv("[123,'Hello',[456,[789]]]", "[[2,0],[2,1,0]]")
        chkVirtual("virtual<rec>", "_strict_str(x)", args,
                "'virtual<rec>[i=null,t=null,s=virtual<sub>[q=int[456],p=virtual<sub2>[r=int[789]]]]'")
        chkVirtual("virtual<rec>", "_strict_str(x.s)", args, "'virtual<sub>[q=int[456],p=virtual<sub2>[r=int[789]]]'")
        chkVirtual("virtual<rec>", "_strict_str(x.s.q)", args, "'int[456]'")
        chkVirtual("virtual<rec>", "_strict_str(x.s.p)", args, "'virtual<sub2>[r=int[789]]'")
        chkVirtual("virtual<rec>", "_strict_str(x.s.p.r)", args, "'int[789]'")
    }

    @Test fun testStructToFull() {
        def("struct rec { i: integer; t: text; s: sub; }")
        def("struct sub { q: integer; p: sub2; }")
        def("struct sub2 { r: integer; }")

        var args = argToGtv("[123,'Hello',[456,[789]]]", "[[0],[1],[2]]")
        chkVirtual("virtual<rec>", "_strict_str(x.to_full())", args, "rt_err:virtual:to_full:notfull:virtual<rec>")
        chkVirtual("virtual<rec>", "_strict_str(x.s.to_full())", args, "'sub[q=int[456],p=sub2[r=int[789]]]'")
        chkVirtual("virtual<rec>", "_strict_str(x.s.p.to_full())", args, "'sub2[r=int[789]]'")

        args = argToGtv("[123,'Hello',[456,[789]]]", "[[2]]")
        chkVirtual("virtual<rec>", "_strict_str(x.to_full())", args, "rt_err:virtual:to_full:notfull:virtual<rec>")
        chkVirtual("virtual<rec>", "_strict_str(x.s.to_full())", args, "'sub[q=int[456],p=sub2[r=int[789]]]'")
        chkVirtual("virtual<rec>", "_strict_str(x.s.p.to_full())", args, "'sub2[r=int[789]]'")

        args = argToGtv("[123,'Hello',[456,[789]]]", "[[2,0]]")
        chkVirtual("virtual<rec>", "_strict_str(x.to_full())", args, "rt_err:virtual:to_full:notfull:virtual<rec>")
        chkVirtual("virtual<rec>", "_strict_str(x.s.to_full())", args, "rt_err:virtual:to_full:notfull:virtual<sub>")
        chkVirtual("virtual<rec>", "_strict_str(x.s.p.to_full())", args, "rt_err:virtual_struct:get:novalue:sub:p")

        args = argToGtv("[123,'Hello',[456,[789]]]", "[[2,1]]")
        chkVirtual("virtual<rec>", "_strict_str(x.to_full())", args, "rt_err:virtual:to_full:notfull:virtual<rec>")
        chkVirtual("virtual<rec>", "_strict_str(x.s.to_full())", args, "rt_err:virtual:to_full:notfull:virtual<sub>")
        chkVirtual("virtual<rec>", "_strict_str(x.s.p.to_full())", args, "'sub2[r=int[789]]'")

        args = argToGtv("[123,'Hello',[456,[789]]]", "[[2,0],[2,1]]")
        chkVirtual("virtual<rec>", "_strict_str(x.to_full())", args, "rt_err:virtual:to_full:notfull:virtual<rec>")
        chkVirtual("virtual<rec>", "_strict_str(x.s.to_full())", args, "rt_err:virtual:to_full:notfull:virtual<sub>")
        chkVirtual("virtual<rec>", "_strict_str(x.s.p.to_full())", args, "'sub2[r=int[789]]'")

        args = argToGtv("[123,'Hello',[456,[789]]]", "[[2,1,0]]")
        chkVirtual("virtual<rec>", "_strict_str(x.to_full())", args, "rt_err:virtual:to_full:notfull:virtual<rec>")
        chkVirtual("virtual<rec>", "_strict_str(x.s.to_full())", args, "rt_err:virtual:to_full:notfull:virtual<sub>")
        chkVirtual("virtual<rec>", "_strict_str(x.s.p.to_full())", args, "rt_err:virtual:to_full:notfull:virtual<sub2>")
    }

    @Test fun testStructNullableType() {
        def("struct rec { i: integer; t: text; s: sub?; }")
        def("struct sub { q: integer; p: sub2; }")
        def("struct sub2 { r: integer; }")

        val args = argToGtv("[123,'Hello',[456,[789]]]", "[[0],[1],[2]]")

        chkVirtual("virtual<rec>?", "_type_of(x)", args, "'virtual<rec>?'")
        chkVirtual("virtual<rec>?", "_type_of(x.to_full())", args, "ct_err:expr_mem_null:virtual<rec>?:to_full")
        chkVirtual("virtual<rec>?", "_type_of(x?.to_full())", args, "'rec?'")
        chkVirtual("virtual<rec>?", "_type_of(x.i)", args, "ct_err:expr_mem_null:virtual<rec>?:i")
        chkVirtual("virtual<rec>?", "_type_of(x.t)", args, "ct_err:expr_mem_null:virtual<rec>?:t")
        chkVirtual("virtual<rec>?", "_type_of(x.s)", args, "ct_err:expr_mem_null:virtual<rec>?:s")
        chkVirtual("virtual<rec>?", "_type_of(x?.i)", args, "'integer?'")
        chkVirtual("virtual<rec>?", "_type_of(x?.t)", args, "'text?'")
        chkVirtual("virtual<rec>?", "_type_of(x?.s)", args, "'virtual<sub>?'")
        chkVirtual("virtual<rec>?", "_type_of(x?.s.to_full())", args, "ct_err:expr_mem_null:virtual<sub>?:to_full")
        chkVirtual("virtual<rec>?", "_type_of(x?.s?.to_full())", args, "'sub?'")
        chkVirtual("virtual<rec>?", "_type_of(x?.s.q)", args, "ct_err:expr_mem_null:virtual<sub>?:q")
        chkVirtual("virtual<rec>?", "_type_of(x?.s.p)", args, "ct_err:expr_mem_null:virtual<sub>?:p")
        chkVirtual("virtual<rec>?", "_type_of(x?.s?.q)", args, "'integer?'")
        chkVirtual("virtual<rec>?", "_type_of(x?.s?.p)", args, "'virtual<sub2>?'")
        chkVirtual("virtual<rec>?", "_type_of(x?.s?.p?.to_full())", args, "'sub2?'")
        chkVirtual("virtual<rec>?", "_type_of(x?.s?.p?.r)", args, "'integer?'")

        chkVirtual("virtual<rec>", "_type_of(x.s)", args, "'virtual<sub>?'")
        chkVirtual("virtual<rec>", "_type_of(x.i)", args, "'integer'")
        chkVirtual("virtual<rec>", "_type_of(x.t)", args, "'text'")
        chkVirtual("virtual<rec>", "_type_of(x.s)", args, "'virtual<sub>?'")
        chkVirtual("virtual<rec>", "_type_of(x.s.to_full())", args, "ct_err:expr_mem_null:virtual<sub>?:to_full")
        chkVirtual("virtual<rec>", "_type_of(x.s?.to_full())", args, "'sub?'")
        chkVirtual("virtual<rec>", "_type_of(x.s.q)", args, "ct_err:expr_mem_null:virtual<sub>?:q")
        chkVirtual("virtual<rec>", "_type_of(x.s.p)", args, "ct_err:expr_mem_null:virtual<sub>?:p")
        chkVirtual("virtual<rec>", "_type_of(x.s?.q)", args, "'integer?'")
        chkVirtual("virtual<rec>", "_type_of(x.s?.p)", args, "'virtual<sub2>?'")
        chkVirtual("virtual<rec>", "_type_of(x.s?.p?.to_full())", args, "'sub2?'")
        chkVirtual("virtual<rec>", "_type_of(x.s?.p?.r)", args, "'integer?'")
    }

    @Test fun testStructNullableValue() {
        def("struct rec { i: integer; t: text; s: sub?; }")
        def("struct sub { q: integer; p: sub2; }")
        def("struct sub2 { r: integer; }")

        var args: Gtv = GtvNull
        chkVirtual("virtual<rec>?", "_strict_str(x)", args, "'null'")
        chkVirtual("virtual<rec>?", "x.i", args, "ct_err:expr_mem_null:virtual<rec>?:i")
        chkVirtual("virtual<rec>?", "x.to_full()", args, "ct_err:expr_mem_null:virtual<rec>?:to_full")

        args = argToGtv("[123,'Hello',[456,[789]]]", "[[2]]")
        chkVirtual("virtual<rec>?", "_strict_str(x)", args,
                "'virtual<rec>[i=null,t=null,s=virtual<sub>[q=int[456],p=virtual<sub2>[r=int[789]]]]'")
        chkVirtual("virtual<rec>?", "x?.i", args, "rt_err:virtual_struct:get:novalue:rec:i")
        chkVirtual("virtual<rec>?", "x?.t", args, "rt_err:virtual_struct:get:novalue:rec:t")
        chkVirtual("virtual<rec>?", "_strict_str(x?.s)", args, "'virtual<sub>[q=int[456],p=virtual<sub2>[r=int[789]]]'")
        chkVirtual("virtual<rec>?", "_strict_str(x?.s.q)", args, "ct_err:expr_mem_null:virtual<sub>?:q")
        chkVirtual("virtual<rec>?", "_strict_str(x?.s.p)", args, "ct_err:expr_mem_null:virtual<sub>?:p")
        chkVirtual("virtual<rec>?", "_strict_str(x?.s?.q)", args, "'int[456]'")
        chkVirtual("virtual<rec>?", "_strict_str(x?.s?.p)", args, "'virtual<sub2>[r=int[789]]'")

        args = argToGtv("[123,'Hello',[456,[789]]]", "[[0],[1]]")
        chkVirtual("virtual<rec>?", "_strict_str(x)", args, "'virtual<rec>[i=int[123],t=text[Hello],s=null]'")
        chkVirtual("virtual<rec>?", "_strict_str(x?.i)", args, "'int[123]'")
        chkVirtual("virtual<rec>?", "_strict_str(x?.t)", args, "'text[Hello]'")
        chkVirtual("virtual<rec>?", "_strict_str(x?.s)", args, "rt_err:virtual_struct:get:novalue:rec:s")
        chkVirtual("virtual<rec>?", "_strict_str(x?.s?.q)", args, "rt_err:virtual_struct:get:novalue:rec:s")
        chkVirtual("virtual<rec>?", "_strict_str(x?.s?.p)", args, "rt_err:virtual_struct:get:novalue:rec:s")
        chkVirtual("virtual<rec>?", "_strict_str(x?.s?.p?.r)", args, "rt_err:virtual_struct:get:novalue:rec:s")
    }

    @Test fun testStructAttrType() {
        def("struct rec { i: integer; l: list<integer>; }")

        var args = argToGtv("[123,[456,789]]", "[[0],[1]]")
        chkVirtual("virtual<rec>", "_type_of(x)", args, "'virtual<rec>'")
        chkVirtual("virtual<rec>", "_type_of(x.i)", args, "'integer'")
        chkVirtual("virtual<rec>", "_type_of(x.l)", args, "'virtual<list<integer>>'")
        chkVirtual("virtual<rec>", "_strict_str(x)", args,
                "'virtual<rec>[i=int[123],l=virtual<list<integer>>[int[456],int[789]]]'")

        args = argToGtv("[123,[456,789]]", "[[0]]")
        chkVirtual("virtual<rec>", "_strict_str(x.i)", args, "'int[123]'")
        chkVirtual("virtual<rec>", "_strict_str(x.l)", args, "rt_err:virtual_struct:get:novalue:rec:l")

        args = argToGtv("[123,[456,789]]", "[[1]]")
        chkVirtual("virtual<rec>", "_strict_str(x.i)", args, "rt_err:virtual_struct:get:novalue:rec:i")
        chkVirtual("virtual<rec>", "_strict_str(x.l)", args, "'virtual<list<integer>>[int[456],int[789]]'")
    }

    @Test fun testStructAttrMutable() {
        def("struct rec { mutable i: integer; mutable t: text; }")

        val args = argToGtv("[123,'Hello']", "[[0],[1]]")
        chkVirtualEx("virtual<rec>", "{ x.i = 456; return 0; }", args, "ct_err:attr_not_mutable:virtual<rec>.i")
        chkVirtualEx("virtual<rec>", "{ x.t = 'Bye'; return 0; }", args, "ct_err:attr_not_mutable:virtual<rec>.t")

        chkVirtualEx("virtual<rec>", " = x.foo;", args, "ct_err:unknown_member:[virtual<rec>]:foo")
        chkVirtualEx("virtual<rec>", " = x.foo();", args, "ct_err:unknown_member:[virtual<rec>]:foo")
    }

    @Test fun testListType() {
        val type = "virtual<list<integer>>"
        val args = argToGtv("[123,456]", "[[0],[1]]")
        chkVirtual(type, "_type_of(x)", args, "'virtual<list<integer>>'")
        chkVirtual(type, "_type_of(x[0])", args, "'integer'")
        chkVirtual(type, "_type_of(x[1])", args, "'integer'")
        chkVirtual(type, "_type_of(x.empty())", args, "'boolean'")
        chkVirtual(type, "_type_of(x.size())", args, "'integer'")
        chkVirtual(type, "_type_of(0 in x)", args, "'boolean'")
    }

    @Test fun testListValue() {
        val type = "virtual<list<integer>>"

        var args = argToGtv("[123,456]", "[[0],[1]]")
        chkVirtual(type, "_strict_str(x)", args, "'virtual<list<integer>>[int[123],int[456]]'")
        chkVirtual(type, "_strict_str(x[0])", args, "'int[123]'")
        chkVirtual(type, "_strict_str(x[1])", args, "'int[456]'")
        chkVirtual(type, "_strict_str(x.empty())", args, "'boolean[false]'")
        chkVirtual(type, "_strict_str(x.size())", args, "'int[2]'")
        chkVirtual(type, "_strict_str(0 in x)", args, "'boolean[true]'")
        chkVirtual(type, "_strict_str(1 in x)", args, "'boolean[true]'")
        chkVirtual(type, "_strict_str(2 in x)", args, "'boolean[false]'")
        chkVirtual(type, "_strict_str(-1 in x)", args, "'boolean[false]'")

        args = argToGtv("[123,456]", "[[0]]")
        chkVirtual(type, "_strict_str(x)", args, "'virtual<list<integer>>[int[123],null]'")
        chkVirtual(type, "_strict_str(x[0])", args, "'int[123]'")
        chkVirtual(type, "_strict_str(x[1])", args, "rt_err:virtual_list:get:novalue:1")
        chkVirtual(type, "_strict_str(x.empty())", args, "'boolean[false]'")
        chkVirtual(type, "_strict_str(x.size())", args, "'int[2]'")
        chkVirtual(type, "_strict_str(0 in x)", args, "'boolean[true]'")
        chkVirtual(type, "_strict_str(1 in x)", args, "'boolean[false]'")

        args = argToGtv("[123,456]", "[[1]]")
        chkVirtual(type, "_strict_str(x)", args, "'virtual<list<integer>>[null,int[456]]'")
        chkVirtual(type, "_strict_str(x[0])", args, "rt_err:virtual_list:get:novalue:0")
        chkVirtual(type, "_strict_str(x[1])", args, "'int[456]'")
        chkVirtual(type, "_strict_str(x.empty())", args, "'boolean[false]'")
        chkVirtual(type, "_strict_str(x.size())", args, "'int[2]'")
        chkVirtual(type, "_strict_str(0 in x)", args, "'boolean[false]'")
        chkVirtual(type, "_strict_str(1 in x)", args, "'boolean[true]'")
    }

    @Test fun testListToFull() {
        val type = "virtual<list<integer>>"

        var args = argToGtv("[123,456]", "[[0],[1]]")
        chkVirtual(type, "_type_of(x.to_full())", args, "'list<integer>'")
        chkVirtual(type, "_strict_str(x.to_full())", args, "rt_err:virtual:to_full:notfull:virtual<list<integer>>")

        args = argToGtv("[123,456]", "[[0]]")
        chkVirtual(type, "_strict_str(x.to_full())", args, "rt_err:virtual:to_full:notfull:virtual<list<integer>>")

        args = argToGtv("[123,456]", "[[1]]")
        chkVirtual(type, "_strict_str(x.to_full())", args, "rt_err:virtual:to_full:notfull:virtual<list<integer>>")
    }

    @Test fun testListOperations() {
        val type = "virtual<list<integer>>"
        val args = argToGtv("[123,456]", "[[0],[1]]")

        chkVirtualEx(type, "{ x[0] = 123; return 0; }", args, "ct_err:expr_immutable:virtual<list<integer>>")
        chkVirtualEx(type, "{ x[0] += 123; return 0; }", args, "ct_err:expr_immutable:virtual<list<integer>>")

        chkVirtualEx(type, " = _strict_str(x.str());", args, "'text[[123, 456]]'")
        chkVirtualEx(type, " = _strict_str(x.to_text());", args, "'text[[123, 456]]'")
        chkVirtualEx(type, " = _strict_str(x.empty());", args, "'boolean[false]'")
        chkVirtualEx(type, " = _strict_str(x.size());", args, "'int[2]'")
        chkVirtualEx(type, " = _strict_str(x[0]);", args, "'int[123]'")
        chkVirtualEx(type, " = _strict_str(x.get(0));", args, "'int[123]'")

        chkVirtualEx(type, " = x.contains(123);", args, "ct_err:unknown_member:[virtual<list<integer>>]:contains")
        chkVirtualEx(type, " = x.index_of(123);", args, "ct_err:unknown_member:[virtual<list<integer>>]:index_of")
        chkVirtualEx(type, " = x.contains_all([123]);", args, "ct_err:unknown_member:[virtual<list<integer>>]:contains_all")
        chkVirtualEx(type, " = x.sub(0, 1);", args, "ct_err:unknown_member:[virtual<list<integer>>]:sub")
        chkVirtualEx(type, " = x.sorted();", args, "ct_err:unknown_member:[virtual<list<integer>>]:sorted")

        chkVirtualEx(type, "{ x.clear(); return 0; }", args, "ct_err:unknown_member:[virtual<list<integer>>]:clear")
        chkVirtualEx(type, "{ x.remove(123); return 0; }", args, "ct_err:unknown_member:[virtual<list<integer>>]:remove")
        chkVirtualEx(type, "{ x.remove_at(0); return 0; }", args, "ct_err:unknown_member:[virtual<list<integer>>]:remove_at")
        chkVirtualEx(type, "{ x.remove_all([123, 456]); return 0; }", args, "ct_err:unknown_member:[virtual<list<integer>>]:remove_all")
        chkVirtualEx(type, "{ x.add(789); return 0; }", args, "ct_err:unknown_member:[virtual<list<integer>>]:add")
        chkVirtualEx(type, "{ x.add_all([789]); return 0; }", args, "ct_err:unknown_member:[virtual<list<integer>>]:add_all")
        chkVirtualEx(type, "{ x._sort(); return 0; }", args, "ct_err:unknown_member:[virtual<list<integer>>]:_sort")
    }

    @Test fun testListIterator() {
        def("struct rec { i: integer; }")

        val type = "virtual<list<rec>>"

        var args = argToGtv("[[123],[456]]", "[[0],[1]]")
        chkVirtualEx(type, "{ for (v in x) return _type_of(v); return ''; }", args, "'virtual<rec>'")
        chkVirtualEx(type, "{ for (v in x) return _strict_str(v); return ''; }", args, "'virtual<rec>[i=int[123]]'")

        args = argToGtv("[[123],[456]]", "[[0]]")
        chkVirtualEx(type, "{ for (v in x) return _strict_str(v); return ''; }", args, "'virtual<rec>[i=int[123]]'")

        args = argToGtv("[[123],[456]]", "[[1]]")
        chkVirtualEx(type, "{ for (v in x) return _strict_str(v); return ''; }", args, "'virtual<rec>[i=int[456]]'")
    }

    @Test fun testListOfStruct() {
        def("struct rec { i: integer; t: text; }")

        val type = "virtual<list<rec>>"

        var args = argToGtv("[[123,'Hello'],[456,'Bye']]", "[[0],[1]]")
        chkVirtual(type, "_type_of(x)", args, "'virtual<list<rec>>'")
        chkVirtual(type, "_type_of(x[0])", args, "'virtual<rec>'")
        chkVirtual(type, "_type_of(x.get(0))", args, "'virtual<rec>'")

        args = argToGtv("[[123,'Hello'],[456,'Bye']]", "[[0],[1]]")
        chkVirtual(type, "_strict_str(x)", args,
                "'virtual<list<rec>>[virtual<rec>[i=int[123],t=text[Hello]],virtual<rec>[i=int[456],t=text[Bye]]]'")
        chkVirtual(type, "_strict_str(x.to_full())", args, "rt_err:virtual:to_full:notfull:virtual<list<rec>>")
        chkVirtual(type, "_strict_str(x[0])", args, "'virtual<rec>[i=int[123],t=text[Hello]]'")
        chkVirtual(type, "_strict_str(x[1])", args, "'virtual<rec>[i=int[456],t=text[Bye]]'")
        chkVirtual(type, "_strict_str(x[0].i)", args, "'int[123]'")
        chkVirtual(type, "_strict_str(x[0].t)", args, "'text[Hello]'")
        chkVirtual(type, "_strict_str(x[0].to_full())", args, "'rec[i=int[123],t=text[Hello]]'")
        chkVirtual(type, "_strict_str(x[1].to_full())", args, "'rec[i=int[456],t=text[Bye]]'")

        args = argToGtv("[[123,'Hello'],[456,'Bye']]", "[[1,0]]")
        chkVirtual(type, "_strict_str(x)", args, "'virtual<list<rec>>[null,virtual<rec>[i=int[456],t=null]]'")
        chkVirtual(type, "_strict_str(x.to_full())", args, "rt_err:virtual:to_full:notfull:virtual<list<rec>>")
        chkVirtual(type, "_strict_str(x[0])", args, "rt_err:virtual_list:get:novalue:0")
        chkVirtual(type, "_strict_str(x[1])", args, "'virtual<rec>[i=int[456],t=null]'")
        chkVirtual(type, "_strict_str(x[1].to_full())", args, "rt_err:virtual:to_full:notfull:virtual<rec>")

        args = argToGtv("[[123,'Hello'],[456,'Bye']]", "[[1,0],[1,1]]")
        chkVirtual(type, "_strict_str(x)", args, "'virtual<list<rec>>[null,virtual<rec>[i=int[456],t=text[Bye]]]'")
        chkVirtual(type, "_strict_str(x[0])", args, "rt_err:virtual_list:get:novalue:0")
        chkVirtual(type, "_strict_str(x[1])", args, "'virtual<rec>[i=int[456],t=text[Bye]]'")
        chkVirtual(type, "_strict_str(x[1].to_full())", args, "rt_err:virtual:to_full:notfull:virtual<rec>")
    }

    @Test fun testListOfList() {
        val type = "virtual<list<list<integer>>>"

        var args = argToGtv("[[123,456],[654,321]]", "[[0],[1]]")
        chkVirtual(type, "_type_of(x)", args, "'virtual<list<list<integer>>>'")
        chkVirtual(type, "_type_of(x[0])", args, "'virtual<list<integer>>'")
        chkVirtual(type, "_type_of(x.get(0))", args, "'virtual<list<integer>>'")

        args = argToGtv("[[123,456],[654,321]]", "[[0],[1]]")
        chkVirtual(type, "_strict_str(x)", args,
                "'virtual<list<list<integer>>>[virtual<list<integer>>[int[123],int[456]],virtual<list<integer>>[int[654],int[321]]]'")
        chkVirtual(type, "_strict_str(x.to_full())", args, "rt_err:virtual:to_full:notfull:virtual<list<list<integer>>>")
        chkVirtual(type, "_strict_str(x[0])", args, "'virtual<list<integer>>[int[123],int[456]]'")

        args = argToGtv("[[123,456],[654,321]]", "[[1,0]]")
        chkVirtual(type, "_strict_str(x)", args, "'virtual<list<list<integer>>>[null,virtual<list<integer>>[int[654],null]]'")
        chkVirtual(type, "_strict_str(x.to_full())", args, "rt_err:virtual:to_full:notfull:virtual<list<list<integer>>>")
        chkVirtual(type, "_strict_str(x[0])", args, "rt_err:virtual_list:get:novalue:0")
        chkVirtual(type, "_strict_str(x[1])", args, "'virtual<list<integer>>[int[654],null]'")

        args = argToGtv("[[123,456],[654,321]]", "[[1,0],[1,1]]")
        chkVirtual(type, "_strict_str(x)", args, "'virtual<list<list<integer>>>[null,virtual<list<integer>>[int[654],int[321]]]'")
        chkVirtual(type, "_strict_str(x.to_full())", args, "rt_err:virtual:to_full:notfull:virtual<list<list<integer>>>")
        chkVirtual(type, "_strict_str(x[0])", args, "rt_err:virtual_list:get:novalue:0")
        chkVirtual(type, "_strict_str(x[1])", args, "'virtual<list<integer>>[int[654],int[321]]'")
        chkVirtual(type, "_strict_str(x[1].to_full())", args, "rt_err:virtual:to_full:notfull:virtual<list<integer>>")

        args = argToGtv("[[123,456],[]]", "[[1]]")
        chkVirtual(type, "_strict_str(x)", args, "'virtual<list<list<integer>>>[null,virtual<list<integer>>[]]'")
        chkVirtual(type, "_strict_str(x.to_full())", args, "rt_err:virtual:to_full:notfull:virtual<list<list<integer>>>")
        chkVirtual(type, "_strict_str(x[0])", args, "rt_err:virtual_list:get:novalue:0")
        chkVirtual(type, "_strict_str(x[1])", args, "'virtual<list<integer>>[]'")
        chkVirtual(type, "_strict_str(x[1].to_full())", args, "'list<integer>[]'")
    }

    @Test fun testListOfSet() {
        val type = "virtual<list<set<integer>>>"

        var args = argToGtv("[[123,456],[654,321]]", "[[0],[1]]")
        chkVirtual(type, "_type_of(x)", args, "'virtual<list<set<integer>>>'")
        chkVirtual(type, "_type_of(x[0])", args, "'virtual<set<integer>>'")
        chkVirtual(type, "_type_of(x.get(0))", args, "'virtual<set<integer>>'")

        args = argToGtv("[[123,456],[654,321]]", "[[0],[1]]")
        chkVirtual(type, "_strict_str(x)", args,
                "'virtual<list<set<integer>>>[virtual<set<integer>>[int[123],int[456]],virtual<set<integer>>[int[654],int[321]]]'")
        chkVirtual(type, "_strict_str(x.to_full())", args, "rt_err:virtual:to_full:notfull:virtual<list<set<integer>>>")
        chkVirtual(type, "_strict_str(x[0])", args, "'virtual<set<integer>>[int[123],int[456]]'")

        args = argToGtv("[[123,456],[654,321]]", "[[1,0]]")
        chkVirtual(type, "_strict_str(x)", args, "'virtual<list<set<integer>>>[null,virtual<set<integer>>[int[654]]]'")
        chkVirtual(type, "_strict_str(x.to_full())", args, "rt_err:virtual:to_full:notfull:virtual<list<set<integer>>>")
        chkVirtual(type, "_strict_str(x[0])", args, "rt_err:virtual_list:get:novalue:0")
        chkVirtual(type, "_strict_str(x[1])", args, "'virtual<set<integer>>[int[654]]'")

        args = argToGtv("[[123,456],[654,321]]", "[[1,0],[1,1]]")
        chkVirtual(type, "_strict_str(x)", args, "'virtual<list<set<integer>>>[null,virtual<set<integer>>[int[654],int[321]]]'")
        chkVirtual(type, "_strict_str(x.to_full())", args, "rt_err:virtual:to_full:notfull:virtual<list<set<integer>>>")
        chkVirtual(type, "_strict_str(x[0])", args, "rt_err:virtual_list:get:novalue:0")
        chkVirtual(type, "_strict_str(x[1])", args, "'virtual<set<integer>>[int[654],int[321]]'")
        chkVirtual(type, "_strict_str(x[1].to_full())", args, "rt_err:virtual:to_full:notfull:virtual<set<integer>>")

        args = argToGtv("[[123,456],[]]", "[[1]]")
        chkVirtual(type, "_strict_str(x)", args, "'virtual<list<set<integer>>>[null,virtual<set<integer>>[]]'")
        chkVirtual(type, "_strict_str(x.to_full())", args, "rt_err:virtual:to_full:notfull:virtual<list<set<integer>>>")
        chkVirtual(type, "_strict_str(x[0])", args, "rt_err:virtual_list:get:novalue:0")
        chkVirtual(type, "_strict_str(x[1])", args, "'virtual<set<integer>>[]'")
        chkVirtual(type, "_strict_str(x[1].to_full())", args, "'set<integer>[]'")
    }

    @Test fun testListOfMap() {
        val type = "virtual<list<map<text,integer>>>"

        var args = argToGtv("[{'Hello':123},{'Bye':456}]", "[[0],[1]]")
        chkVirtual(type, "_type_of(x)", args, "'virtual<list<map<text,integer>>>'")
        chkVirtual(type, "_type_of(x[0])", args, "'virtual<map<text,integer>>'")
        chkVirtual(type, "_type_of(x.get(0))", args, "'virtual<map<text,integer>>'")

        args = argToGtv("[{'Hello':123},{'Bye':456}]", "[[0],[1]]")
        chkVirtual(type, "_strict_str(x)", args,
                "'virtual<list<map<text,integer>>>[virtual<map<text,integer>>[text[Hello]=int[123]],virtual<map<text,integer>>[text[Bye]=int[456]]]'")
        chkVirtual(type, "_strict_str(x.to_full())", args, "rt_err:virtual:to_full:notfull:virtual<list<map<text,integer>>>")
        chkVirtual(type, "_strict_str(x[0])", args, "'virtual<map<text,integer>>[text[Hello]=int[123]]'")
        chkVirtual(type, "_strict_str(x[0].to_full())", args, "'map<text,integer>[text[Hello]=int[123]]'")

        args = argToGtv("[{'Hello':123},{'Bye':456}]", "[[0,'Hello'],[1,'Bye']]")
        chkVirtual(type, "_strict_str(x)", args,
                "'virtual<list<map<text,integer>>>[virtual<map<text,integer>>[text[Hello]=int[123]],virtual<map<text,integer>>[text[Bye]=int[456]]]'")
        chkVirtual(type, "_strict_str(x.to_full())", args, "rt_err:virtual:to_full:notfull:virtual<list<map<text,integer>>>")
        chkVirtual(type, "_strict_str(x[0])", args, "'virtual<map<text,integer>>[text[Hello]=int[123]]'")
        chkVirtual(type, "_strict_str(x[1])", args, "'virtual<map<text,integer>>[text[Bye]=int[456]]'")
        chkVirtual(type, "_strict_str(x[0].to_full())", args, "rt_err:virtual:to_full:notfull:virtual<map<text,integer>>")
        chkVirtual(type, "_strict_str(x[1].to_full())", args, "rt_err:virtual:to_full:notfull:virtual<map<text,integer>>")
    }

    @Test fun testListOfTuple() {
        val type = "virtual<list<(a:integer,b:text)>>"

        var args = argToGtv("[[123,'Hello'],[456,'Bye']]", "[[0],[1]]")
        chkVirtual(type, "_type_of(x)", args, "'virtual<list<(a:integer,b:text)>>'")
        chkVirtual(type, "_type_of(x[0])", args, "'virtual<(a:integer,b:text)>'")
        chkVirtual(type, "_type_of(x.get(0))", args, "'virtual<(a:integer,b:text)>'")

        args = argToGtv("[[123,'Hello'],[456,'Bye']]", "[[0],[1]]")
        chkVirtual(type, "_strict_str(x)", args,
                "'virtual<list<(a:integer,b:text)>>[virtual(int[123],text[Hello]),virtual(int[456],text[Bye])]'")
        chkVirtual(type, "_strict_str(x.to_full())", args,
                "rt_err:virtual:to_full:notfull:virtual<list<(a:integer,b:text)>>")
        chkVirtual(type, "_strict_str(x[0])", args, "'virtual(a=int[123],b=text[Hello])'")
        chkVirtual(type, "_strict_str(x[0].to_full())", args, "'(a=int[123],b=text[Hello])'")
        chkVirtual(type, "_strict_str(x[1].to_full())", args, "'(a=int[456],b=text[Bye])'")

        args = argToGtv("[[123,'Hello'],[456,'Bye']]", "[[0],[1,0],[1,1]]")
        chkVirtual(type, "_strict_str(x)", args,
                "'virtual<list<(a:integer,b:text)>>[virtual(int[123],text[Hello]),virtual(int[456],text[Bye])]'")
        chkVirtual(type, "_strict_str(x.to_full())", args,
                "rt_err:virtual:to_full:notfull:virtual<list<(a:integer,b:text)>>")
        chkVirtual(type, "_strict_str(x[0])", args, "'virtual(a=int[123],b=text[Hello])'")
        chkVirtual(type, "_strict_str(x[0].to_full())", args, "'(a=int[123],b=text[Hello])'")
        chkVirtual(type, "_strict_str(x[1].to_full())", args, "rt_err:virtual:to_full:notfull:virtual<(a:integer,b:text)>")
    }

    @Test fun testListOfNullable() {
        val type = "virtual<list<integer?>>"

        var args = argToGtv("[123,null,456]", "[[0],[1],[2]]")
        chkVirtual(type, "_type_of(x)", args, "'virtual<list<integer?>>'")
        chkVirtual(type, "_type_of(x[0])", args, "'integer?'")

        chkVirtual(type, "_strict_str(x[0])", args, "'int[123]'")
        chkVirtual(type, "_strict_str(x[1])", args, "'null'")
        chkVirtual(type, "_strict_str(x[2])", args, "'int[456]'")
        chkVirtual(type, "_strict_str(0 in x)", args, "'boolean[true]'")
        chkVirtual(type, "_strict_str(1 in x)", args, "'boolean[true]'")
        chkVirtual(type, "_strict_str(2 in x)", args, "'boolean[true]'")

        args = argToGtv("[123,null,456]", "[[0],[2]]")
        chkVirtual(type, "_strict_str(x[0])", args, "'int[123]'")
        chkVirtual(type, "_strict_str(x[1])", args, "rt_err:virtual_list:get:novalue:1")
        chkVirtual(type, "_strict_str(x[2])", args, "'int[456]'")
        chkVirtual(type, "_strict_str(0 in x)", args, "'boolean[true]'")
        chkVirtual(type, "_strict_str(1 in x)", args, "'boolean[false]'")
        chkVirtual(type, "_strict_str(2 in x)", args, "'boolean[true]'")

        args = argToGtv("[123,null,456]", "[[1],[2]]")
        chkVirtual(type, "_strict_str(x[0])", args, "rt_err:virtual_list:get:novalue:0")
        chkVirtual(type, "_strict_str(x[1])", args, "'null'")
        chkVirtual(type, "_strict_str(x[2])", args, "'int[456]'")
        chkVirtual(type, "_strict_str(0 in x)", args, "'boolean[false]'")
        chkVirtual(type, "_strict_str(1 in x)", args, "'boolean[true]'")
        chkVirtual(type, "_strict_str(2 in x)", args, "'boolean[true]'")
    }

    @Test fun testListElementType() {
        tstCtx.useSql = true
        tst.chainId = 0
        def("entity user { name; }")
        def("struct rec { i: integer; t: text; }")
        def("enum myenum { A, B, C }")
        insert("c0.user", "name", "101,'Bob'")
        insert("c0.user", "name", "102,'Alice'")

        chkListElementType("boolean", "[0,1]", "boolean[false],boolean[true]")
        chkListElementType("integer", "[123,456]", "int[123],int[456]")
        chkListElementType("text", "['A','B']", "text[A],text[B]")
        chkListElementType("byte_array", "['0123','ABCD']", "byte_array[0123],byte_array[abcd]")
        chkListElementType("json", "['{}','[]']", "json[{}],json[[]]")
        chkListElementType("user", "[101,102]", "user[101],user[102]")
        chkListElementType("myenum", "[0,1]", "myenum[A],myenum[B]")
        chkListElementType("integer?", "[123,456]", "int[123],int[456]")
        chkListElementType("gtv", "[123,456]", "gtv[123],gtv[456]")

        chkListElementType("rec", "[[123,'A'],[456,'B']]",
                "virtual<rec>[i=int[123],t=text[A]],virtual<rec>[i=int[456],t=text[B]]")
        chkListElementType("(integer,text)", "[[123,'A'],[456,'B']]",
                "virtual(int[123],text[A]),virtual(int[456],text[B])")
        chkListElementType("list<integer>", "[[123],[456]]",
                "virtual<list<integer>>[int[123]],virtual<list<integer>>[int[456]]")
    }

    private fun chkListElementType(type: String, arg: String, expected: String) {
        val gtvArg = argToGtv(arg, "[[0],[1]]")
        chkVirtual("virtual<list<$type>>", "_strict_str(x)", gtvArg, "'virtual<list<$type>>[$expected]'")
    }

    @Test fun testSetType() {
        val type = "virtual<set<integer>>"
        val args = argToGtv("[123,456]", "[[0],[1]]")
        chkVirtual(type, "_type_of(x)", args, "'virtual<set<integer>>'")
        chkVirtual(type, "_type_of(x.empty())", args, "'boolean'")
        chkVirtual(type, "_type_of(x.size())", args, "'integer'")
        chkVirtual(type, "_type_of(0 in x)", args, "'boolean'")
    }

    @Test fun testSetValue() {
        val type = "virtual<set<integer>>"

        var args = argToGtv("[123,456]", "[[0],[1]]")
        chkVirtual(type, "_strict_str(x)", args, "'virtual<set<integer>>[int[123],int[456]]'")
        chkVirtual(type, "_strict_str(x.empty())", args, "'boolean[false]'")
        chkVirtual(type, "_strict_str(x.size())", args, "'int[2]'")
        chkVirtual(type, "_strict_str(123 in x)", args, "'boolean[true]'")
        chkVirtual(type, "_strict_str(456 in x)", args, "'boolean[true]'")
        chkVirtual(type, "_strict_str(789 in x)", args, "'boolean[false]'")
        chkVirtual(type, "_strict_str('A' in x)", args, "ct_err:binop_operand_type:in:[text]:[virtual<set<integer>>]")

        args = argToGtv("[123,456]", "[[0]]")
        chkVirtual(type, "_strict_str(x)", args, "'virtual<set<integer>>[int[123]]'")
        chkVirtual(type, "_strict_str(x.empty())", args, "'boolean[false]'")
        chkVirtual(type, "_strict_str(x.size())", args, "'int[1]'")
        chkVirtual(type, "_strict_str(123 in x)", args, "'boolean[true]'")
        chkVirtual(type, "_strict_str(456 in x)", args, "'boolean[false]'")

        args = argToGtv("[123,456]", "[[1]]")
        chkVirtual(type, "_strict_str(x)", args, "'virtual<set<integer>>[int[456]]'")
        chkVirtual(type, "_strict_str(x.empty())", args, "'boolean[false]'")
        chkVirtual(type, "_strict_str(x.size())", args, "'int[1]'")
        chkVirtual(type, "_strict_str(123 in x)", args, "'boolean[false]'")
        chkVirtual(type, "_strict_str(456 in x)", args, "'boolean[true]'")
    }

    @Test fun testSetToFull() {
        val type = "virtual<set<integer>>"

        var args = argToGtv("[123,456]", "[[0],[1]]")
        chkVirtual(type, "_type_of(x.to_full())", args, "'set<integer>'")
        chkVirtual(type, "_strict_str(x.to_full())", args, "rt_err:virtual:to_full:notfull:virtual<set<integer>>")

        args = argToGtv("[123,456]", "[[0]]")
        chkVirtual(type, "_strict_str(x.to_full())", args, "rt_err:virtual:to_full:notfull:virtual<set<integer>>")

        args = argToGtv("[123,456]", "[[1]]")
        chkVirtual(type, "_strict_str(x.to_full())", args, "rt_err:virtual:to_full:notfull:virtual<set<integer>>")
    }

    @Test fun testSetOperations() {
        val type = "virtual<set<integer>>"
        val args = argToGtv("[123,456]", "[[0],[1]]")

        chkVirtualEx(type, " = _strict_str(x.str());", args, "'text[[123, 456]]'")
        chkVirtualEx(type, " = _strict_str(x.to_text());", args, "'text[[123, 456]]'")
        chkVirtualEx(type, " = _strict_str(x.empty());", args, "'boolean[false]'")
        chkVirtualEx(type, " = _strict_str(x.size());", args, "'int[2]'")
        chkVirtualEx(type, " = _strict_str(123 in x);", args, "'boolean[true]'")
        chkVirtualEx(type, " = _strict_str(456 in x);", args, "'boolean[true]'")
        chkVirtualEx(type, " = _strict_str(789 in x);", args, "'boolean[false]'")

        chkVirtualEx(type, " = x.contains(123);", args, "ct_err:unknown_member:[virtual<set<integer>>]:contains")
        chkVirtualEx(type, " = x.contains_all([123]);", args, "ct_err:unknown_member:[virtual<set<integer>>]:contains_all")
        chkVirtualEx(type, " = x.sorted();", args, "ct_err:unknown_member:[virtual<set<integer>>]:sorted")

        chkVirtualEx(type, "{ x.clear(); return 0; }", args, "ct_err:unknown_member:[virtual<set<integer>>]:clear")
        chkVirtualEx(type, "{ x.remove(123); return 0; }", args, "ct_err:unknown_member:[virtual<set<integer>>]:remove")
        chkVirtualEx(type, "{ x.remove_all([123, 456]); return 0; }", args, "ct_err:unknown_member:[virtual<set<integer>>]:remove_all")
        chkVirtualEx(type, "{ x.add(789); return 0; }", args, "ct_err:unknown_member:[virtual<set<integer>>]:add")
        chkVirtualEx(type, "{ x.add_all([789]); return 0; }", args, "ct_err:unknown_member:[virtual<set<integer>>]:add_all")
    }

    @Test fun testSetOperatorIn() {
        val innerType = "(integer,text)"
        val type = "virtual<set<$innerType>>"

        var args = listOf(argToGtv("[[123,'A'],[456,'B']]", "[[0],[1]]"), argToGtv("[123,'A']", "[[0],[1]]"))
        chkFullGtv("query q(x: $type, y: $innerType) = _strict_str(y in x);", args,
                "ct_err:binop_operand_type:in:[(integer,text)]:[virtual<set<(integer,text)>>]")

        val code = "query q(x: $type, y: virtual<$innerType>) = _strict_str(y in x);"
        chkFullGtv(code, args, "'boolean[true]'")

        args = listOf(argToGtv("[[123,'A'],[456,'B']]", "[[0],[1]]"), argToGtv("[456,'B']", "[[0],[1]]"))
        chkFullGtv(code, args, "'boolean[true]'")

        args = listOf(argToGtv("[[123,'A'],[456,'B']]", "[[0]]"), argToGtv("[123,'A']", "[[0],[1]]"))
        chkFullGtv(code, args, "'boolean[true]'")
        args = listOf(argToGtv("[[123,'A'],[456,'B']]", "[[0]]"), argToGtv("[456,'B']", "[[0],[1]]"))
        chkFullGtv(code, args, "'boolean[false]'")

        args = listOf(argToGtv("[[123,'A'],[456,'B']]", "[[0],[1]]"), argToGtv("[123,'A']", "[[0]]"))
        chkFullGtv(code, args, "'boolean[false]'")
        args = listOf(argToGtv("[[123,'A'],[456,'B']]", "[[0,0],[1]]"), argToGtv("[123,'A']", "[[0]]"))
        chkFullGtv(code, args, "'boolean[true]'")
    }

    @Test fun testSetIterator() {
        def("struct rec { i: integer; }")

        val type = "virtual<set<rec>>"

        var args = argToGtv("[[123],[456]]", "[[0],[1]]")
        chkVirtualEx(type, "{ for (v in x) return _type_of(v); return ''; }", args, "'virtual<rec>'")
        chkVirtualEx(type, "{ for (v in x) return _strict_str(v); return ''; }", args, "'virtual<rec>[i=int[123]]'")

        args = argToGtv("[[123],[456]]", "[[0]]")
        chkVirtualEx(type, "{ for (v in x) return _strict_str(v); return ''; }", args, "'virtual<rec>[i=int[123]]'")

        args = argToGtv("[[123],[456]]", "[[1]]")
        chkVirtualEx(type, "{ for (v in x) return _strict_str(v); return ''; }", args, "'virtual<rec>[i=int[456]]'")
    }

    @Test fun testSetOfTuple() {
        val type = "virtual<set<(a:integer,b:text)>>"

        var args = argToGtv("[[123,'Hello'],[456,'Bye']]", "[[0],[1]]")
        chkVirtual(type, "_type_of(x)", args, "'virtual<set<(a:integer,b:text)>>'")

        args = argToGtv("[[123,'Hello'],[456,'Bye']]", "[[0],[1]]")
        chkVirtual(type, "_strict_str(x)", args,
                "'virtual<set<(a:integer,b:text)>>[virtual(int[123],text[Hello]),virtual(int[456],text[Bye])]'")
        chkVirtual(type, "_strict_str(x.to_full())", args,
                "rt_err:virtual:to_full:notfull:virtual<set<(a:integer,b:text)>>")

        args = argToGtv("[[123,'Hello'],[456,'Bye']]", "[[0],[1,0],[1,1]]")
        chkVirtual(type, "_strict_str(x)", args,
                "'virtual<set<(a:integer,b:text)>>[virtual(int[123],text[Hello]),virtual(int[456],text[Bye])]'")
        chkVirtual(type, "_strict_str(x.to_full())", args,
                "rt_err:virtual:to_full:notfull:virtual<set<(a:integer,b:text)>>")

        args = argToGtv("[[123,'Hello'],[456,'Bye']]", "[[0,1],[1,0]]")
        chkVirtual(type, "_strict_str(x)", args,
                "'virtual<set<(a:integer,b:text)>>[virtual(null,text[Hello]),virtual(int[456],null)]'")

        args = argToGtv("[[123,'Hello'],[456,'Bye']]", "[[0],[1]]")
        chkVirtual(type, "_strict_str((a=123,b='Hello') in x)", args,
                "ct_err:binop_operand_type:in:[(a:integer,b:text)]:[virtual<set<(a:integer,b:text)>>]")
    }

    @Test fun testMapType() {
        val type = "virtual<map<text, integer>>"
        val args = argToGtv("{'Hello':123,'Bye':456}", "[['Hello'],['Bye']]")
        chkVirtual(type, "_type_of(x)", args, "'virtual<map<text,integer>>'")
        chkVirtual(type, "_type_of(x.to_full())", args, "'map<text,integer>'")
        chkVirtual(type, "_type_of(x['X'])", args, "'integer'")
        chkVirtual(type, "_type_of(x.empty())", args, "'boolean'")
        chkVirtual(type, "_type_of(x.size())", args, "'integer'")
        chkVirtual(type, "_type_of('A' in x)", args, "'boolean'")
        chkVirtual(type, "_type_of(x.keys())", args, "'set<text>'")
        chkVirtual(type, "_type_of(x.values())", args, "'list<integer>'")
    }

    @Test fun testMapValue() {
        val type = "virtual<map<text, integer>>"

        var args = argToGtv("{'Hello':123,'Bye':456}", "[['Hello'],['Bye']]")
        chkVirtual(type, "_strict_str(x)", args, "'virtual<map<text,integer>>[text[Bye]=int[456],text[Hello]=int[123]]'")
        chkVirtual(type, "_strict_str(x['Hello'])", args, "'int[123]'")
        chkVirtual(type, "_strict_str(x['Bye'])", args, "'int[456]'")
        chkVirtual(type, "_strict_str(x.empty())", args, "'boolean[false]'")
        chkVirtual(type, "_strict_str(x.size())", args, "'int[2]'")
        chkVirtual(type, "_strict_str('Hello' in x)", args, "'boolean[true]'")
        chkVirtual(type, "_strict_str('Bye' in x)", args, "'boolean[true]'")
        chkVirtual(type, "_strict_str('Hi' in x)", args, "'boolean[false]'")

        args = argToGtv("{'Hello':123,'Bye':456}", "[['Hello']]")
        chkVirtual(type, "_strict_str(x)", args, "'virtual<map<text,integer>>[text[Hello]=int[123]]'")
        chkVirtual(type, "_strict_str(x['Hello'])", args, "'int[123]'")
        chkVirtual(type, "_strict_str(x['Bye'])", args, "rt_err:fn_map_get_novalue:text[Bye]")
        chkVirtual(type, "_strict_str(x.empty())", args, "'boolean[false]'")
        chkVirtual(type, "_strict_str(x.size())", args, "'int[1]'")
        chkVirtual(type, "_strict_str('Hello' in x)", args, "'boolean[true]'")
        chkVirtual(type, "_strict_str('Bye' in x)", args, "'boolean[false]'")

        args = argToGtv("{'Hello':123,'Bye':456}", "[['Bye']]")
        chkVirtual(type, "_strict_str(x)", args, "'virtual<map<text,integer>>[text[Bye]=int[456]]'")
        chkVirtual(type, "_strict_str(x['Hello'])", args, "rt_err:fn_map_get_novalue:text[Hello]")
        chkVirtual(type, "_strict_str(x['Bye'])", args, "'int[456]'")
        chkVirtual(type, "_strict_str(x.empty())", args, "'boolean[false]'")
        chkVirtual(type, "_strict_str(x.size())", args, "'int[1]'")
        chkVirtual(type, "_strict_str('Hello' in x)", args, "'boolean[false]'")
        chkVirtual(type, "_strict_str('Bye' in x)", args, "'boolean[true]'")
    }

    @Test fun testMapToFull() {
        val type = "virtual<map<text, integer>>"

        var args = argToGtv("{'Hello':123,'Bye':456}", "[['Hello'],['Bye']]")
        chkVirtual(type, "_type_of(x.to_full())", args, "'map<text,integer>'")
        chkVirtual(type, "_strict_str(x.to_full())", args, "rt_err:virtual:to_full:notfull:virtual<map<text,integer>>")

        args = argToGtv("{'Hello':123,'Bye':456}", "[['Hello']]")
        chkVirtual(type, "_strict_str(x.to_full())", args, "rt_err:virtual:to_full:notfull:virtual<map<text,integer>>")

        args = argToGtv("{'Hello':123,'Bye':456}", "[['Bye']]")
        chkVirtual(type, "_strict_str(x.to_full())", args, "rt_err:virtual:to_full:notfull:virtual<map<text,integer>>")
    }

    @Test fun testMapOperations() {
        val type = "virtual<map<text, integer>>"

        val args = argToGtv("{'Hello':123,'Bye':456}", "[['Hello'],['Bye']]")
        chkVirtualEx(type, "{ x['Hello'] = 123; return 0; }", args, "ct_err:expr_immutable:virtual<map<text,integer>>")
        chkVirtualEx(type, "{ x['Hello'] += 123; return 0; }", args, "ct_err:expr_immutable:virtual<map<text,integer>>")

        chkVirtualEx(type, "= _strict_str(x.str());", args, "'text[{Bye=456, Hello=123}]'")
        chkVirtualEx(type, "= _strict_str(x.to_text());", args, "'text[{Bye=456, Hello=123}]'")
        chkVirtualEx(type, "= _strict_str(x.empty());", args, "'boolean[false]'")
        chkVirtualEx(type, "= _strict_str(x.size());", args, "'int[2]'")
        chkVirtualEx(type, "= _strict_str(x.get('Hello'));", args, "'int[123]'")
        chkVirtualEx(type, "= _strict_str(x.get('World'));", args, "rt_err:fn:map.get:novalue:text[World]")
        chkVirtualEx(type, "= _strict_str(x.get_or_null('Hello'));", args, "'int[123]'")
        chkVirtualEx(type, "= _strict_str(x.get_or_null('World'));", args, "'null'")
        chkVirtualEx(type, "= _strict_str(x.get_or_default('Hello', null));", args, "'int[123]'")
        chkVirtualEx(type, "= _strict_str(x.get_or_default('World', null));", args, "'null'")
        chkVirtualEx(type, "= _strict_str(x.contains('Hello'));", args, "'boolean[true]'")
        chkVirtualEx(type, "= _strict_str(x.keys());", args, "'set<text>[text[Bye],text[Hello]]'")
        chkVirtualEx(type, "= _strict_str(x.values());", args, "'list<integer>[int[456],int[123]]'")

        chkVirtualEx(type, "{ x.clear(); return 0; }", args,
                "ct_err:unknown_member:[virtual<map<text,integer>>]:clear")
        chkVirtualEx(type, "{ x.put('Hi', 789); return 0; }", args,
                "ct_err:unknown_member:[virtual<map<text,integer>>]:put")
        chkVirtualEx(type, "{ x.put_all(['Hi':789]); return 0; }", args,
                "ct_err:unknown_member:[virtual<map<text,integer>>]:put_all")
        chkVirtualEx(type, "{ x.remove('Hello'); return 0; }", args,
                "ct_err:unknown_member:[virtual<map<text,integer>>]:remove")
    }

    @Test fun testMapIterator() {
        def("struct rec { i: integer; j: integer; }")

        val type = "virtual<map<text, rec>>"
        var args = argToGtv("{'Hello':[123,321],'Bye':[456,654]}", "[['Hello'],['Bye']]")
        chkVirtualEx(type, "{ for (e in x) return _type_of(e); return ''; }", args, "'(text,virtual<rec>)'")
        chkVirtualEx(type, "= x @ {} ( _type_of($) ) limit 1;", args, "'(text,virtual<rec>)'")
        chkVirtualEx(type, "{ for ((k, v) in x) return _type_of(k); return ''; }", args, "'text'")
        chkVirtualEx(type, "{ for ((k, v) in x) return _type_of(v); return ''; }", args, "'virtual<rec>'")
        chkVirtualEx(type, "{ for (e in x) return _strict_str(e); return ''; }", args,
            "'(text[Bye],virtual<rec>[i=int[456],j=int[654]])'")
        chkVirtualEx(type, "= x @ {} ( _strict_str($) ) limit 1;", args,
            "'(text[Bye],virtual<rec>[i=int[456],j=int[654]])'")
        chkVirtualEx(type, "{ for ((k, v) in x) return _strict_str(k); return ''; }", args, "'text[Bye]'")
        chkVirtualEx(type, "{ for ((k, v) in x) return _strict_str(v); return ''; }", args,
            "'virtual<rec>[i=int[456],j=int[654]]'")
        chkVirtualEx(type, "{ for (v in x.values()) return _strict_str(v); return ''; }", args,
            "'virtual<rec>[i=int[456],j=int[654]]'")

        args = argToGtv("{'Hello':[123,321],'Bye':[456,654]}", "[['Hello']]")
        chkVirtualEx(type, "{ for ((k, v) in x) return _strict_str(k); return ''; }", args, "'text[Hello]'")
        chkVirtualEx(type, "{ for ((k, v) in x) return _strict_str(v); return ''; }", args,
            "'virtual<rec>[i=int[123],j=int[321]]'")

        args = argToGtv("{'Hello':[123,321],'Bye':[456,654]}", "[['Bye']]")
        chkVirtualEx(type, "{ for ((k, v) in x) return _strict_str(k); return ''; }", args, "'text[Bye]'")
        chkVirtualEx(type, "{ for ((k, v) in x) return _strict_str(v); return ''; }", args,
            "'virtual<rec>[i=int[456],j=int[654]]'")

        args = argToGtv("{'Hello':[123,321],'Bye':[456,654]}", "[['Hello',1]]")
        chkVirtualEx(type, "{ for ((k, v) in x) return _strict_str(k); return ''; }", args, "'text[Hello]'")
        chkVirtualEx(type, "{ for ((k, v) in x) return _strict_str(v); return ''; }", args,
            "'virtual<rec>[i=null,j=int[321]]'")
    }

    @Test fun testMapOfStruct() {
        def("struct rec { i: integer; t: text; }")

        val type = "virtual<map<text,rec>>"
        var args = argToGtv("{'A':[123,'Hello'],'B':[456,'Bye']}", "[['A'],['B']]")
        chkVirtual(type, "_type_of(x)", args, "'virtual<map<text,rec>>'")
        chkVirtual(type, "_type_of(x['A'])", args, "'virtual<rec>'")
        chkVirtual(type, "_strict_str(x['A'])", args, "'virtual<rec>[i=int[123],t=text[Hello]]'")
        chkVirtual(type, "_type_of(x.keys())", args, "'set<text>'")
        chkVirtual(type, "_type_of(x.values())", args, "'list<virtual<rec>>'")
        chkVirtual(type, "_type_of(x.get('A'))", args, "'virtual<rec>'")
        chkVirtual(type, "_type_of(x.get_or_null('A'))", args, "'virtual<rec>?'")
        chkVirtual(type, "_type_of(x.get_or_default('A', null))", args, "'virtual<rec>?'")

        args = argToGtv("{'A':[123,'Hello'],'B':[456,'Bye']}", "[['B',0]]")
        chkVirtual(type, "_strict_str(x)", args, "'virtual<map<text,rec>>[text[B]=virtual<rec>[i=int[456],t=null]]'")
        chkVirtual(type, "_strict_str(x['A'])", args, "rt_err:fn_map_get_novalue:text[A]")
        chkVirtual(type, "_strict_str(x['B'])", args, "'virtual<rec>[i=int[456],t=null]'")
        chkVirtual(type, "_strict_str(x['B'].i)", args, "'int[456]'")
        chkVirtual(type, "_strict_str(x['B'].t)", args, "rt_err:virtual_struct:get:novalue:rec:t")
        chkVirtual(type, "_strict_str(x.get('B'))", args, "'virtual<rec>[i=int[456],t=null]'")
        chkVirtual(type, "_strict_str(x.get_or_null('A'))", args, "'null'")
        chkVirtual(type, "_strict_str(x.get_or_null('B'))", args, "'virtual<rec>[i=int[456],t=null]'")
        chkVirtual(type, "_strict_str(x.get_or_default('A', null))", args, "'null'")
        chkVirtual(type, "_strict_str(x.get_or_default('B', null))", args, "'virtual<rec>[i=int[456],t=null]'")
        chkVirtual(type, "_strict_str(x.keys())", args, "'set<text>[text[B]]'")
        chkVirtual(type, "_strict_str(x.values())", args, "'list<virtual<rec>>[virtual<rec>[i=int[456],t=null]]'")
    }

    @Test fun testTupleType() {
        val type = "virtual<(a:integer,b:text)>"
        var args = argToGtv("[123,'Hello']", "[[0],[1]]")

        chkVirtual(type, "_type_of(x)", args, "'virtual<(a:integer,b:text)>'")
        chkVirtual(type, "_type_of(x.to_full())", args, "'(a:integer,b:text)'")
        chkVirtual(type, "_type_of(x.a)", args, "'integer'")
        chkVirtual(type, "_type_of(x.b)", args, "'text'")

        chkVirtual(type, "_type_of(x[0])", args, "'integer'")
        chkVirtual(type, "_type_of(x[1])", args, "'text'")
        chkVirtual(type, "_type_of(x[-1])", args, "ct_err:expr_subscript:tuple:index:-1:2")
        chkVirtual(type, "_type_of(x[2])", args, "ct_err:expr_subscript:tuple:index:2:2")
    }

    @Test fun testTupleValue() {
        val type = "virtual<(a:integer,b:text)>"

        var args = argToGtv("[123,'Hello']", "[[0],[1]]")
        chkVirtual(type, "_strict_str(x)", args, "'virtual(a=int[123],b=text[Hello])'")
        chkVirtual(type, "_strict_str(x.a)", args, "'int[123]'")
        chkVirtual(type, "_strict_str(x.b)", args, "'text[Hello]'")
        chkVirtual(type, "_strict_str(x[0])", args, "'int[123]'")
        chkVirtual(type, "_strict_str(x[1])", args, "'text[Hello]'")
        chkVirtual(type, "_strict_str(x[-1])", args, "ct_err:expr_subscript:tuple:index:-1:2")
        chkVirtual(type, "_strict_str(x[2])", args, "ct_err:expr_subscript:tuple:index:2:2")

        args = argToGtv("[123,'Hello']", "[[0]]")
        chkVirtual(type, "_strict_str(x)", args, "'virtual(a=int[123],b=null)'")
        chkVirtual(type, "_strict_str(x.a)", args, "'int[123]'")
        chkVirtual(type, "_strict_str(x.b)", args, "rt_err:virtual_tuple:get:novalue:b")
        chkVirtual(type, "_strict_str(x[0])", args, "'int[123]'")
        chkVirtual(type, "_strict_str(x[1])", args, "rt_err:virtual_tuple:get:novalue:b")

        args = argToGtv("[123,'Hello']", "[[1]]")
        chkVirtual(type, "_strict_str(x)", args, "'virtual(a=null,b=text[Hello])'")
        chkVirtual(type, "_strict_str(x.a)", args, "rt_err:virtual_tuple:get:novalue:a")
        chkVirtual(type, "_strict_str(x.b)", args, "'text[Hello]'")
        chkVirtual(type, "_strict_str(x[0])", args, "rt_err:virtual_tuple:get:novalue:a")
        chkVirtual(type, "_strict_str(x[1])", args, "'text[Hello]'")
    }

    @Test fun testTupleToFull() {
        val type = "virtual<(a:integer,b:text)>"

        var args = argToGtv("[123,'Hello']", "[[0],[1]]")
        chkVirtual(type, "_type_of(x.to_full())", args, "'(a:integer,b:text)'")
        chkVirtual(type, "_strict_str(x.to_full())", args, "rt_err:virtual:to_full:notfull:virtual<(a:integer,b:text)>")

        args = argToGtv("[123,'Hello']", "[[0]]")
        chkVirtual(type, "_strict_str(x.to_full())", args, "rt_err:virtual:to_full:notfull:virtual<(a:integer,b:text)>")

        args = argToGtv("[123,'Hello']", "[[1]]")
        chkVirtual(type, "_strict_str(x.to_full())", args, "rt_err:virtual:to_full:notfull:virtual<(a:integer,b:text)>")
    }

    @Test fun testTupleOfStruct() {
        def("struct rec { i: integer; t: text; }")

        val type = "virtual<(a:integer,b:rec)>"

        var args = argToGtv("[456,[123,'Hello']]", "[[0],[1]]")
        chkVirtual(type, "_type_of(x)", args, "'virtual<(a:integer,b:rec)>'")
        chkVirtual(type, "_type_of(x.a)", args, "'integer'")
        chkVirtual(type, "_type_of(x.b)", args, "'virtual<rec>'")
        chkVirtual(type, "_strict_str(x)", args, "'virtual(a=int[456],b=virtual<rec>[i=int[123],t=text[Hello]])'")
        chkVirtual(type, "_strict_str(x.a)", args, "'int[456]'")
        chkVirtual(type, "_strict_str(x.b)", args, "'virtual<rec>[i=int[123],t=text[Hello]]'")
        chkVirtual(type, "_strict_str(x[0])", args, "'int[456]'")
        chkVirtual(type, "_strict_str(x[1])", args, "'virtual<rec>[i=int[123],t=text[Hello]]'")

        args = argToGtv("[456,[123,'Hello']]", "[[1,0]]")
        chkVirtual(type, "_type_of(x)", args, "'virtual<(a:integer,b:rec)>'")
        chkVirtual(type, "_strict_str(x)", args, "'virtual(a=null,b=virtual<rec>[i=int[123],t=null])'")
        chkVirtual(type, "_strict_str(x.a)", args, "rt_err:virtual_tuple:get:novalue:a")
        chkVirtual(type, "_strict_str(x.b)", args, "'virtual<rec>[i=int[123],t=null]'")
        chkVirtual(type, "_strict_str(x.b.i)", args, "'int[123]'")
        chkVirtual(type, "_strict_str(x.b.t)", args, "rt_err:virtual_struct:get:novalue:rec:t")
        chkVirtual(type, "_strict_str(x[0])", args, "rt_err:virtual_tuple:get:novalue:a")
        chkVirtual(type, "_strict_str(x[1])", args, "'virtual<rec>[i=int[123],t=null]'")
        chkVirtual(type, "_strict_str(x[1].i)", args, "'int[123]'")
        chkVirtual(type, "_strict_str(x[1].t)", args, "rt_err:virtual_struct:get:novalue:rec:t")
    }

    @Test fun testVirtual() {
        def("struct sub { i: integer; t: text; }")
        def("struct top { k: integer; s: virtual<sub>; }")

        val type = "virtual<top>"
        val sub = GtvTestUtils.decodeGtvStr("[123,'Hello']")
        var args = GtvFactory.gtv(GtvFactory.gtv(456), argToGtv(sub, "[[0],[1]]"))
        var virArgs = argToGtv(args, "[[0],[1]]")

        chkVirtual(type, "_type_of(x.to_full())", virArgs, "'top'")
        chkVirtual(type, "_type_of(x.k)", virArgs, "'integer'")
        chkVirtual(type, "_type_of(x.s)", virArgs, "'virtual<sub>'")
        chkVirtual(type, "_type_of(x.s.to_full())", virArgs, "'sub'")
        chkVirtual(type, "_type_of(x.s.i)", virArgs, "'integer'")
        chkVirtual(type, "_type_of(x.s.t)", virArgs, "'text'")

        chkVirtual(type, "_strict_str(x.to_full())", virArgs, "rt_err:virtual:to_full:notfull:virtual<top>")
        chkVirtual(type, "_strict_str(x.s.to_full())", virArgs, "rt_err:virtual:to_full:notfull:virtual<sub>")
        chkVirtual(type, "_strict_str(x.k)", virArgs, "'int[456]'")
        chkVirtual(type, "_strict_str(x.s)", virArgs, "'virtual<sub>[i=int[123],t=text[Hello]]'")
        chkVirtual(type, "_strict_str(x.s.i)", virArgs, "'int[123]'")
        chkVirtual(type, "_strict_str(x.s.t)", virArgs, "'text[Hello]'")

        args = GtvFactory.gtv(GtvFactory.gtv(456), argToGtv(sub, "[[1]]"))
        virArgs = argToGtv(args, "[[1]]")

        chkVirtual(type, "_strict_str(x.to_full())", virArgs, "rt_err:virtual:to_full:notfull:virtual<top>")
        chkVirtual(type, "_strict_str(x.s.to_full())", virArgs, "rt_err:virtual:to_full:notfull:virtual<sub>")
        chkVirtual(type, "_strict_str(x.k)", virArgs, "rt_err:virtual_struct:get:novalue:top:k")
        chkVirtual(type, "_strict_str(x.s)", virArgs, "'virtual<sub>[i=null,t=text[Hello]]'")
        chkVirtual(type, "_strict_str(x.s.i)", virArgs, "rt_err:virtual_struct:get:novalue:sub:i")
        chkVirtual(type, "_strict_str(x.s.t)", virArgs, "'text[Hello]'")

        args = GtvFactory.gtv(GtvFactory.gtv(456), argToGtv(sub, "[[1]]"))
        virArgs = argToGtv(args, "[[0],[1]]")

        chkVirtual(type, "_strict_str(x.to_full())", virArgs, "rt_err:virtual:to_full:notfull:virtual<top>")
        chkVirtual(type, "_strict_str(x.s.to_full())", virArgs, "rt_err:virtual:to_full:notfull:virtual<sub>")
        chkVirtual(type, "_strict_str(x.k)", virArgs, "'int[456]'")
        chkVirtual(type, "_strict_str(x.s)", virArgs, "'virtual<sub>[i=null,t=text[Hello]]'")
        chkVirtual(type, "_strict_str(x.s.i)", virArgs, "rt_err:virtual_struct:get:novalue:sub:i")
        chkVirtual(type, "_strict_str(x.s.t)", virArgs, "'text[Hello]'")

        args = GtvFactory.gtv(GtvFactory.gtv(456), argToGtv(sub, "[[0],[1]]"))
        virArgs = argToGtv(args, "[[1]]")

        chkVirtual(type, "_strict_str(x.to_full())", virArgs, "rt_err:virtual:to_full:notfull:virtual<top>")
        chkVirtual(type, "_strict_str(x.s.to_full())", virArgs, "rt_err:virtual:to_full:notfull:virtual<sub>")
        chkVirtual(type, "_strict_str(x.k)", virArgs, "rt_err:virtual_struct:get:novalue:top:k")
        chkVirtual(type, "_strict_str(x.s)", virArgs, "'virtual<sub>[i=int[123],t=text[Hello]]'")
        chkVirtual(type, "_strict_str(x.s.i)", virArgs, "'int[123]'")
        chkVirtual(type, "_strict_str(x.s.t)", virArgs, "'text[Hello]'")
    }

    @Test fun testToGtv() {
        tst.gtv = true
        def("struct rec { x: integer; }")
        def("struct vir { v: virtual<rec>; }")
        def("struct ind1 { r: vir?; }")
        def("struct ind2 { q: list<ind1>; }")
        def("struct ind3 { p: map<text,ind2>; }")
        def("struct cycle { mutable next: cycle?; v: ind3; }")

        chkCompile("function f(a: virtual<rec>) { a.to_gtv(); }", "ct_err:fn:invalid:virtual<rec>:to_gtv")
        chkCompile("function f(a: virtual<list<integer>>) { a.to_gtv(); }", "ct_err:fn:invalid:virtual<list<integer>>:to_gtv")
        chkCompile("function f(a: virtual<map<text,integer>>) { a.to_gtv(); }", "ct_err:fn:invalid:virtual<map<text,integer>>:to_gtv")
        chkCompile("function f(a: virtual<(integer,text)>) { a.to_gtv(); }", "ct_err:fn:invalid:virtual<(integer,text)>:to_gtv")
        chkCompile("function f(a: virtual<rec>) { a.to_gtv_pretty(); }", "ct_err:fn:invalid:virtual<rec>:to_gtv_pretty")

        chkCompile("function f(a: vir) { a.to_gtv(); }", "ct_err:fn:invalid:vir:to_gtv")
        chkCompile("function f(a: ind1) { a.to_gtv(); }", "ct_err:fn:invalid:ind1:to_gtv")
        chkCompile("function f(a: ind2) { a.to_gtv(); }", "ct_err:fn:invalid:ind2:to_gtv")
        chkCompile("function f(a: ind3) { a.to_gtv(); }", "ct_err:fn:invalid:ind3:to_gtv")
        chkCompile("function f(a: (integer,map<text,list<ind3>>)) { a.to_gtv(); }",
                "ct_err:fn:invalid:(integer,map<text,list<ind3>>):to_gtv")

        chkCompile("query q() = list<virtual<rec>>();", "ct_err:result_nogtv:q:list<virtual<rec>>")
        chkCompile("query q() = list<virtual<list<integer>>>();", "ct_err:result_nogtv:q:list<virtual<list<integer>>>")
        chkCompile("query q() = list<virtual<map<text,integer>>>();", "ct_err:result_nogtv:q:list<virtual<map<text,integer>>>")
        chkCompile("query q() = list<virtual<(integer,text)>>();", "ct_err:result_nogtv:q:list<virtual<(integer,text)>>")

        chkCompile("query q() = list<vir>();", "ct_err:result_nogtv:q:list<vir>")
        chkCompile("query q() = list<ind1>();", "ct_err:result_nogtv:q:list<ind1>")
        chkCompile("query q() = list<ind2>();", "ct_err:result_nogtv:q:list<ind2>")
        chkCompile("query q() = list<ind3>();", "ct_err:result_nogtv:q:list<ind3>")
        chkCompile("query q() = list<(integer,map<text,list<ind3>>)>();",
                "ct_err:result_nogtv:q:list<(integer,map<text,list<ind3>>)>")
        chkCompile("query q() = list<vir?>();", "ct_err:result_nogtv:q:list<vir?>")
        chkCompile("query q() = set<vir>();", "ct_err:result_nogtv:q:set<vir>")
        chkCompile("query q() = map<text,vir>();", "ct_err:result_nogtv:q:map<text,vir>")
        chkCompile("query q() = map<vir,text>();", "ct_err:result_nogtv:q:map<vir,text>")
        chkCompile("query q() = list<cycle>();", "ct_err:result_nogtv:q:list<cycle>")
    }

    @Test fun testFromGtv() {
        def("struct rec { i: integer; t: text; }")

        chkVirtual("gtv", "_strict_str(virtual<rec>.from_gtv_pretty(x))", argToGtv("[123,'Hello']", "[[0],[1]]"),
                "ct_err:fn:invalid:virtual<rec>:from_gtv_pretty")

        chkFromGtv("rec", "[123,'Hello']", "[[0],[1]]", "'virtual<rec>[i=int[123],t=text[Hello]]'")
        chkFromGtv("rec", "[123,'Hello']", "[[0]]", "'virtual<rec>[i=int[123],t=null]'")
        chkFromGtv("rec", "[123,'Hello']", "[[1]]", "'virtual<rec>[i=null,t=text[Hello]]'")

        chkFromGtv("(integer,text)", "[123,'Hello']", "[[0],[1]]", "'virtual(int[123],text[Hello])'")
        chkFromGtv("(integer,text)", "[123,'Hello']", "[[0]]", "'virtual(int[123],null)'")
        chkFromGtv("(integer,text)", "[123,'Hello']", "[[1]]", "'virtual(null,text[Hello])'")

        chkFromGtv("list<integer>", "[123,456]", "[[0],[1]]", "'virtual<list<integer>>[int[123],int[456]]'")
        chkFromGtv("list<integer>", "[123,456]", "[[0]]", "'virtual<list<integer>>[int[123],null]'")
        chkFromGtv("list<integer>", "[123,456]", "[[1]]", "'virtual<list<integer>>[null,int[456]]'")

        chkFromGtv("set<integer>", "[123,456]", "[[0],[1]]", "'virtual<set<integer>>[int[123],int[456]]'")
        chkFromGtv("set<integer>", "[123,456]", "[[0]]", "'virtual<set<integer>>[int[123]]'")
        chkFromGtv("set<integer>", "[123,456]", "[[1]]", "'virtual<set<integer>>[int[456]]'")

        chkFromGtv("map<text,integer>", "{'A':123,'B':456}", "[['A'],['B']]",
                "'virtual<map<text,integer>>[text[A]=int[123],text[B]=int[456]]'")
        chkFromGtv("map<text,integer>", "{'A':123,'B':456}", "[['A']]", "'virtual<map<text,integer>>[text[A]=int[123]]'")
        chkFromGtv("map<text,integer>", "{'A':123,'B':456}", "[['B']]", "'virtual<map<text,integer>>[text[B]=int[456]]'")

        chkVirtual("gtv", "_strict_str(virtual<map<integer,text>>.from_gtv(x))",
                argToGtv("{'A':123,'B':456}", "[['A'],['B']]"), "ct_err:type:virtual:bad_inner_type:map<integer,text>")
    }

    private fun chkFromGtv(type: String, args: String, paths: String, expected: String) {
        val virArgs = argToGtv(args, paths)
        chkVirtual("gtv", "_type_of(virtual<$type>.from_gtv(x))", virArgs, "'virtual<$type>'")
        chkVirtual("gtv", "_strict_str(virtual<$type>.from_gtv(x))", virArgs, expected)
    }

    @Test fun testBadValue() {
        val expr = "_strict_str(virtual<list<integer>>.from_gtv(x))"
        chkVirtual("gtv", expr, argToGtv("[123,456]"), "gtv_err:virtual:deserialize:java.lang.IllegalStateException")
        chkVirtual("gtv", expr, argToGtv("{'A':123}"), "gtv_err:virtual:type:GtvDictionary")
        chkVirtual("gtv", expr, argToGtv("['A','B']", "[[0]]"), "gtv_err:type:[integer]:INTEGER:STRING")

        chkVirtual("virtual<list<integer>>", "_strict_str(x)", argToGtv("[123,456]"),
                "gtv_err:virtual:deserialize:java.lang.IllegalStateException")
        chkVirtual("virtual<list<integer>>", "_strict_str(x)", argToGtv("{'A':123}"),
                "gtv_err:virtual:type:GtvDictionary")
        chkVirtual("virtual<list<integer>>", "_strict_str(x)", argToGtv("['A','B']", "[[0]]"),
                "gtv_err:type:[integer]:INTEGER:STRING")
    }

    @Test fun testOperatorsErr() {
        def("struct rec { i: integer; t: text; }")

        chkOperatorErr("virtual<list<integer>>", "virtual<list<text>>", "==", "!=", "===", "!==")
        chkOperatorErr("virtual<list<integer>>", "virtual<rec>", "==", "!=", "===", "!==")
        chkOperatorErr("virtual<rec>", "virtual<(integer,text)>", "==", "!=", "===", "!==")
        chkOperatorErr("virtual<rec>", "virtual<(i:integer,t:text)>", "==", "!=", "===", "!==")
        chkOperatorErr("virtual<map<text,integer>>", "virtual<rec>", "==", "!=", "===", "!==")
        chkOperatorErr("virtual<map<text,integer>>", "virtual<list<integer>>", "==", "!=", "===", "!==")
        chkOperatorErr("virtual<map<text,integer>>", "virtual<list<text>>", "==", "!=", "===", "!==")

        chkOperatorErr("virtual<list<integer>>", "list<integer>", "==", "!=", "===", "!==")
        chkOperatorErr("virtual<map<text,integer>>", "map<text,integer>", "==", "!=", "===", "!==")
        chkOperatorErr("virtual<rec>", "rec", "==", "!=", "===", "!==")
        chkOperatorErr("virtual<(integer,text)>", "(integer,text)", "==", "!=", "===", "!==")
    }

    private fun chkOperatorErr(type1: String, type2: String, vararg ops: String) {
        for (op in ops) {
            chkCompile("query q(x: $type1, y: $type2) = x $op y;", "ct_err:binop_operand_type:$op:[$type1]:[$type2]")
        }
    }

    @Test fun testOperatorsEq() {
        def("struct rec { i: integer; t: text; }")

        chkOperatorEq("virtual<list<integer>>", "[123,456]", "[[0],[1]]", "[123,456]", "[[0],[1]]", true)
        chkOperatorEq("virtual<list<integer>>", "[123,456]", "[[0],[1]]", "[123,457]", "[[0],[1]]", false)

        chkOperatorEq("virtual<list<integer>>", "[123,456]", "[[0]]", "[123,456]", "[[0],[1]]", false)
        chkOperatorEq("virtual<list<integer>>", "[123,456]", "[[0],[1]]", "[123,456]", "[[0]]", false)
        chkOperatorEq("virtual<list<integer>>", "[123,456]", "[[0]]", "[123,456]", "[[1]]", false)
        chkOperatorEq("virtual<list<integer>>", "[123,456]", "[[0]]", "[123,456]", "[[0]]", true)
        chkOperatorEq("virtual<list<integer>>", "[123,456]", "[[1]]", "[123,456]", "[[1]]", true)

        val type = "virtual<map<text,integer>>"
        chkOperatorEq(type, "{'A':123,'B':456}", "[['A'],['B']]", "{'A':123,'B':456}", "[['A'],['B']]", true)
        chkOperatorEq(type, "{'A':123,'B':456}", "[['A'],['B']]", "{'A':123,'B':457}", "[['A'],['B']]", false)
        chkOperatorEq(type, "{'A':123,'B':456}", "[['A']]", "{'A':123,'B':456}", "[['A'],['B']]", false)
        chkOperatorEq(type, "{'A':123,'B':456}", "[['A']]", "{'A':123,'B':456}", "[['B']]", false)
        chkOperatorEq(type, "{'A':123,'B':456}", "[['B']]", "{'A':123,'B':456}", "[['A']]", false)
        chkOperatorEq(type, "{'A':123,'B':456}", "[['A']]", "{'A':123,'B':456}", "[['A']]", true)
        chkOperatorEq(type, "{'A':123,'B':456}", "[['B']]", "{'A':123,'B':456}", "[['B']]", true)

        chkOperatorEq("virtual<rec>", "[123,'Hello']", "[[0],[1]]", "[123,'Hello']", "[[0],[1]]", true)
        chkOperatorEq("virtual<rec>", "[123,'Hello']", "[[0],[1]]", "[123,'Bye']", "[[0],[1]]", false)
        chkOperatorEq("virtual<rec>", "[123,'Hello']", "[[0]]", "[123,'Hello']", "[[0],[1]]", false)
        chkOperatorEq("virtual<rec>", "[123,'Hello']", "[[0]]", "[123,'Hello']", "[[1]]", false)
        chkOperatorEq("virtual<rec>", "[123,'Hello']", "[[1]]", "[123,'Hello']", "[[0]]", false)
        chkOperatorEq("virtual<rec>", "[123,'Hello']", "[[0]]", "[123,'Hello']", "[[0]]", true)
        chkOperatorEq("virtual<rec>", "[123,'Hello']", "[[1]]", "[123,'Hello']", "[[1]]", true)

        chkOperatorEq("virtual<(integer,text)>", "[123,'Hello']", "[[0],[1]]", "[123,'Hello']", "[[0],[1]]", true)
        chkOperatorEq("virtual<(integer,text)>", "[123,'Hello']", "[[0],[1]]", "[123,'Bye']", "[[0],[1]]", false)
        chkOperatorEq("virtual<(integer,text)>", "[123,'Hello']", "[[0]]", "[123,'Hello']", "[[0],[1]]", false)
        chkOperatorEq("virtual<(integer,text)>", "[123,'Hello']", "[[0],[1]]", "[123,'Hello']", "[[0]]", false)
        chkOperatorEq("virtual<(integer,text)>", "[123,'Hello']", "[[0]]", "[123,'Hello']", "[[1]]", false)
        chkOperatorEq("virtual<(integer,text)>", "[123,'Hello']", "[[0]]", "[123,'Hello']", "[[0]]", true)
        chkOperatorEq("virtual<(integer,text)>", "[123,'Hello']", "[[1]]", "[123,'Hello']", "[[1]]", true)
    }

    private fun chkOperatorEq(type: String, arg1: String, paths1: String, arg2: String, paths2: String, eq: Boolean) {
        chkOperator(type, arg1, paths1, arg2, paths2, "x == y", if (eq) "1" else "0")
        chkOperator(type, arg1, paths1, arg2, paths2, "x != y", if (eq) "0" else "1")
    }

    @Test fun testOperatorsRefEq() {
        def("struct rec { i: integer; t: text; }")
        chkOperatorRefEq("virtual<list<integer>>", "[123,456]", "[[0],[1]]")
        chkOperatorRefEq("virtual<map<text,integer>>", "{'A':123,'B':456}", "[['A'],['B']]")
        chkOperatorRefEq("virtual<rec>", "[123,'Hello']", "[[0],[1]]")
        chkOperatorRefEq("virtual<(integer,text)>", "[123,'Hello']", "[[0],[1]]")
    }

    private fun chkOperatorRefEq(type: String, arg: String, paths: String) {
        chkOperator(type, arg, paths, arg, paths, "x === y", "0")
        chkOperator(type, arg, paths, arg, paths, "x !== y", "1")
        chkOperator(type, arg, paths, arg, paths, "x === _nop(x)", "1")
        chkOperator(type, arg, paths, arg, paths, "x !== _nop(x)", "0")
        chkOperator(type, arg, paths, arg, paths, "y === _nop(y)", "1")
        chkOperator(type, arg, paths, arg, paths, "y !== _nop(y)", "0")
    }

    private fun chkOperator(type: String, arg1: String, paths1: String, arg2: String, paths2: String, expr: String,
                            expected: String
    ) {
        val args = listOf(argToGtv(arg1, paths1), argToGtv(arg2, paths2))
        chkFullGtv("query q(x: $type, y: $type) = $expr;", args, expected)
    }

    @Test fun testOperation() {
        tstCtx.useSql = true

        val code = "operation o(x: virtual<list<integer>>) { print(_strict_str(x)); }"

        chkOpFullGtv(code, listOf(argToGtv("[123,456]", "[[0],[1]]")))
        chkOut("virtual<list<integer>>[int[123],int[456]]")

        chkOpFullGtv(code, listOf(argToGtv("[123,456]", "[[0]]")))
        chkOut("virtual<list<integer>>[int[123],null]")

        chkOpFullGtv(code, listOf(argToGtv("[123,456]", "[[1]]")))
        chkOut("virtual<list<integer>>[null,int[456]]")
    }

    @Test fun testFunctionParameterType() {
        def("struct rec { i: integer; }")
        chkCompile("function f(x: virtual<rec>) {}", "OK")
        chkCompile("function f(x: virtual<set<rec>>) {}", "OK")
        chkCompile("function f(x: virtual<list<rec>>) {}", "OK")
        chkCompile("function f(x: virtual<map<text,rec>>) {}", "OK")
    }

    @Test fun testHash() {
        def("struct rec { i: integer; t: text; }")

        var t = "virtual<list<integer>>"
        chkHash(t, "[123,456]", "[[0],[1]]", "B3E8532E5A7DAD8C8B159CC47A894263D45975527AFCF72746CCB600716B34A1")
        chkHash(t, "[123,457]", "[[0],[1]]", "E5E06932D6FFECE097CF0124CD2F321959BAF3A654A1888675CD8ED2AF249204")
        chkHash(t, "[123,456]", "[[0]]", "B3E8532E5A7DAD8C8B159CC47A894263D45975527AFCF72746CCB600716B34A1")
        chkHash(t, "[123,456]", "[[1]]", "B3E8532E5A7DAD8C8B159CC47A894263D45975527AFCF72746CCB600716B34A1")

        t = "virtual<set<integer>>"
        chkHash(t, "[123,456]", "[[0],[1]]", "B3E8532E5A7DAD8C8B159CC47A894263D45975527AFCF72746CCB600716B34A1")
        chkHash(t, "[123,457]", "[[0],[1]]", "E5E06932D6FFECE097CF0124CD2F321959BAF3A654A1888675CD8ED2AF249204")
        chkHash(t, "[123,456]", "[[0]]", "B3E8532E5A7DAD8C8B159CC47A894263D45975527AFCF72746CCB600716B34A1")
        chkHash(t, "[123,456]", "[[1]]", "B3E8532E5A7DAD8C8B159CC47A894263D45975527AFCF72746CCB600716B34A1")

        t = "virtual<map<text,integer>>"
        chkHash(t, "{'A':123,'B':456}", "[['A'],['B']]", "0C97DF55C690A501B81DFF34C8D2FCB9C804759D7715522683B85D300064613E")
        chkHash(t, "{'A':123,'B':457}", "[['A'],['B']]", "A06E058690528CB06091866CF5D58EFCF89D3C2C7123EB2B36D03FC6A2C6B0C2")
        chkHash(t, "{'A':123,'B':456}", "[['A']]", "0C97DF55C690A501B81DFF34C8D2FCB9C804759D7715522683B85D300064613E")
        chkHash(t, "{'A':123,'B':456}", "[['B']]", "0C97DF55C690A501B81DFF34C8D2FCB9C804759D7715522683B85D300064613E")

        t = "virtual<rec>"
        chkHash(t, "[123,'Hello']", "[[0],[1]]", "74443C7DE4D4FEE6F6F4D9B0AA5D4749DBFB0965B422E578802701B9AC2E063A")
        chkHash(t, "[124,'Hello']", "[[0],[1]]", "0EDB5FB062209FABEAB581E76AC5D9ADE6C415B3CE15DAE92B0E4432429661BD")
        chkHash(t, "[123,'Hello']", "[[0]]", "74443C7DE4D4FEE6F6F4D9B0AA5D4749DBFB0965B422E578802701B9AC2E063A")
        chkHash(t, "[123,'Hello']", "[[1]]", "74443C7DE4D4FEE6F6F4D9B0AA5D4749DBFB0965B422E578802701B9AC2E063A")

        t = "virtual<(integer,text)>"
        chkHash(t, "[123,'Hello']", "[[0],[1]]", "74443C7DE4D4FEE6F6F4D9B0AA5D4749DBFB0965B422E578802701B9AC2E063A")
        chkHash(t, "[124,'Hello']", "[[0],[1]]", "0EDB5FB062209FABEAB581E76AC5D9ADE6C415B3CE15DAE92B0E4432429661BD")
        chkHash(t, "[123,'Hello']", "[[0]]", "74443C7DE4D4FEE6F6F4D9B0AA5D4749DBFB0965B422E578802701B9AC2E063A")
        chkHash(t, "[123,'Hello']", "[[1]]", "74443C7DE4D4FEE6F6F4D9B0AA5D4749DBFB0965B422E578802701B9AC2E063A")
    }

    @Test fun testHashNested() {
        val type = "virtual<list<list<integer>>>"

        chkHash(type, "x.hash()", "[[123,456],[654,321]]", "[[0],[1]]",
                "1AE6EE6282E24BDBF4590DD24FE3EB92F1A37DE551995F7AF518B13188610F99")
        chkHash(type, "x[0].hash()", "[[123,456],[654,321]]", "[[0],[1]]",
                "B3E8532E5A7DAD8C8B159CC47A894263D45975527AFCF72746CCB600716B34A1")
        chkHash(type, "x[1].hash()", "[[123,456],[654,321]]", "[[0],[1]]",
                "0F655912BCF53057732DFAB9CD35895F471053FF214C5661C7DB279A2984CB41")
        chkHash(type, "x.get(1).hash()", "[[123,456],[654,321]]", "[[0],[1]]",
                "0F655912BCF53057732DFAB9CD35895F471053FF214C5661C7DB279A2984CB41")

        chkHash(type, "x.hash()", "[[654,321],[123,456]]", "[[0],[1]]",
                "D5AA2D0C5F50DFA8580A24AEB7987971B25959E099B2422D4D98BEE7029E6DC3")
        chkHash(type, "x[0].hash()", "[[654,321],[123,456]]", "[[0],[1]]",
                "0F655912BCF53057732DFAB9CD35895F471053FF214C5661C7DB279A2984CB41")
        chkHash(type, "x[1].hash()", "[[654,321],[123,456]]", "[[0],[1]]",
                "B3E8532E5A7DAD8C8B159CC47A894263D45975527AFCF72746CCB600716B34A1")

        chkHash(type, "x.hash()", "[[123,456],[654,321]]", "[[0]]",
                "1AE6EE6282E24BDBF4590DD24FE3EB92F1A37DE551995F7AF518B13188610F99")
        chkHash(type, "x[0].hash()", "[[123,456],[654,321]]", "[[0]]",
                "B3E8532E5A7DAD8C8B159CC47A894263D45975527AFCF72746CCB600716B34A1")
        chkHash(type, "x.hash()", "[[123,456],[654,321]]", "[[1]]",
                "1AE6EE6282E24BDBF4590DD24FE3EB92F1A37DE551995F7AF518B13188610F99")
        chkHash(type, "x[1].hash()", "[[123,456],[654,321]]", "[[1]]",
                "0F655912BCF53057732DFAB9CD35895F471053FF214C5661C7DB279A2984CB41")

        chkHash(type, "x.hash()", "[[123,456],[654,321]]", "[[0,0]]",
                "1AE6EE6282E24BDBF4590DD24FE3EB92F1A37DE551995F7AF518B13188610F99")
        chkHash(type, "x[0].hash()", "[[123,456],[654,321]]", "[[0,0]]",
                "B3E8532E5A7DAD8C8B159CC47A894263D45975527AFCF72746CCB600716B34A1")
        chkHash(type, "x.hash()", "[[123,456],[654,321]]", "[[0,1]]",
                "1AE6EE6282E24BDBF4590DD24FE3EB92F1A37DE551995F7AF518B13188610F99")
        chkHash(type, "x[0].hash()", "[[123,456],[654,321]]", "[[0,1]]",
                "B3E8532E5A7DAD8C8B159CC47A894263D45975527AFCF72746CCB600716B34A1")
    }

    private fun chkHash(type: String, arg: String, paths: String, expected: String) {
        chkHash(type, "x.hash()", arg, paths, expected)
    }

    private fun chkHash(type: String, expr: String, arg: String, paths: String, expected: String) {
        val gtvArg = argToGtv(arg, paths)
        chkVirtual(type, expr, gtvArg, "'$expected'")
    }

    companion object {
        fun argToGtv(args: String) = VirtualTestUtils.argToGtv(args)
        fun argToGtv(args: String, paths: String) = VirtualTestUtils.argToGtv(args, paths)
        fun argToGtv(gtv: Gtv, paths: String) = VirtualTestUtils.argToGtv(gtv, paths)
    }
}
