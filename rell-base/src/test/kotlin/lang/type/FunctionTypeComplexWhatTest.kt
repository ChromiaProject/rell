/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.type

import net.postchain.rell.base.testutils.BaseRellTest
import kotlin.test.Test

class FunctionTypeComplexWhatTest: BaseRellTest() {
    @Test fun testInvokeFunctionValue() {
        def("function f(i: integer, t: text) = 'f:'+i+','+t;")
        initComplexWhat()

        chk("data @* {} ( f(.i, .t) )", "list<text>[text[f:11,A],text[f:22,B]]")

        chkEx("{ val p = f(*); return data @* {} ( p(.i, .t) ); }", "list<text>[text[f:11,A],text[f:22,B]]")
        chkEx("{ val p = f(*); return data @* {} ( p(77, .t) ); }", "list<text>[text[f:77,A],text[f:77,B]]")
        chkEx("{ val p = f(*); return data @* {} ( p(.i, 'Z') ); }", "list<text>[text[f:11,Z],text[f:22,Z]]")
        chkEx("{ val p = f(*); return data @* {} ( p(77, 'Z') ); }", "list<text>[text[f:77,Z],text[f:77,Z]]")

        chkEx("{ val p = f(t = *, i = *); return data @* {} ( p(.i, .t) ); }",
                "ct_err:[expr_call_argtype:[?]:0:text:integer][expr_call_argtype:[?]:1:integer:text]")
        chkEx("{ val p = f(t = *, i = *); return data @* {} ( p(.t, .i) ); }", "list<text>[text[f:11,A],text[f:22,B]]")
        chkEx("{ val p = f(t = *, i = *); return data @* {} ( p(.t, 77) ); }", "list<text>[text[f:77,A],text[f:77,B]]")
        chkEx("{ val p = f(t = *, i = *); return data @* {} ( p('Z', .i) ); }", "list<text>[text[f:11,Z],text[f:22,Z]]")
        chkEx("{ val p = f(t = *, i = *); return data @* {} ( p('Z', 77) ); }", "list<text>[text[f:77,Z],text[f:77,Z]]")

        chkEx("{ val p = f(i = *, t = *); return data @* {} ( p(.i, .t) ); }", "list<text>[text[f:11,A],text[f:22,B]]")
        chkEx("{ val p = f(i = 77, t = *); return data @* {} ( p(.t) ); }", "list<text>[text[f:77,A],text[f:77,B]]")
        chkEx("{ val p = f(i = 77, t = *); return data @* {} ( p('Z') ); }", "list<text>[text[f:77,Z],text[f:77,Z]]")
        chkEx("{ val p = f(i = *, t = 'Z'); return data @* {} ( p(.i) ); }", "list<text>[text[f:11,Z],text[f:22,Z]]")
        chkEx("{ val p = f(i = *, t = 'Z'); return data @* {} ( p(77) ); }", "list<text>[text[f:77,Z],text[f:77,Z]]")
        chkEx("{ val p = f(i = 77, t = 'Z', *); return data @* {} ( p() ); }", "list<text>[text[f:77,Z],text[f:77,Z]]")
    }

    @Test fun testInvokeFunctionValueDefaultParameters() {
        def("function f(i: integer = 55, t: text = 'R') = 'f:'+i+','+t;")
        initComplexWhat()

        chkEx("{ val p = f(*); return data @* {} ( p() ); }", "list<text>[text[f:55,R],text[f:55,R]]")

        chkEx("{ val p = f(i = *); return data @* {} ( p(.i) ); }", "list<text>[text[f:11,R],text[f:22,R]]")
        chkEx("{ val p = f(i = *); return data @* {} ( p(77) ); }", "list<text>[text[f:77,R],text[f:77,R]]")
        chkEx("{ val p = f(t = *); return data @* {} ( p(.t) ); }", "list<text>[text[f:55,A],text[f:55,B]]")
        chkEx("{ val p = f(t = *); return data @* {} ( p('Z') ); }", "list<text>[text[f:55,Z],text[f:55,Z]]")

        chkEx("{ val p = f(i = *, t = *); return data @* {} ( p(.i, .t) ); }", "list<text>[text[f:11,A],text[f:22,B]]")
        chkEx("{ val p = f(i = 77, t = *); return data @* {} ( p(.t) ); }", "list<text>[text[f:77,A],text[f:77,B]]")
        chkEx("{ val p = f(i = 77, t = *); return data @* {} ( p('Z') ); }", "list<text>[text[f:77,Z],text[f:77,Z]]")
        chkEx("{ val p = f(i = *, t = 'Z'); return data @* {} ( p(.i) ); }", "list<text>[text[f:11,Z],text[f:22,Z]]")
        chkEx("{ val p = f(i = *, t = 'Z'); return data @* {} ( p(77) ); }", "list<text>[text[f:77,Z],text[f:77,Z]]")
        chkEx("{ val p = f(i = 77, t = 'Z', *); return data @* {} ( p() ); }", "list<text>[text[f:77,Z],text[f:77,Z]]")
    }

    @Test fun testInvokeFunctionValueDependent() {
        def("function f(i: integer, t: text) = 'f:'+i+','+t;")
        def("function fi(i: integer) = f(i, *);")
        def("function ft(t: text) = f(*, t);")
        initComplexWhat()

        chkEx("{ val ps = data@*{}( fi(.i)(.t) ); return ps; }", "list<text>[text[f:11,A],text[f:22,B]]")

        chkEx("{ val ps = data@*{}( fi(.i)(.t, *) ); return ps; }",
                "list<()->text>[fn[f(int[11],text[A])],fn[f(int[22],text[B])]]")

        chkEx("{ val ps = data@*{}( fi(.i)(.t, *) ); return ps@*{}( $() ); }", "list<text>[text[f:11,A],text[f:22,B]]")

        chkEx("{ val ps = data@*{}( ft(.t)(.i) ); return ps; }", "list<text>[text[f:11,A],text[f:22,B]]")

        chkEx("{ val ps = data@*{}( ft(.t)(.i, *) ); return ps; }",
                "list<()->text>[fn[f(int[11],text[A])],fn[f(int[22],text[B])]]")

        chkEx("{ val ps = data@*{}( ft(.t)(.i, *) ); return ps@*{}( $() ); }", "list<text>[text[f:11,A],text[f:22,B]]")
    }

    @Test fun testPartialApplication() {
        def("function f(i: integer, t: text) = 'f:'+i+','+t;")
        initComplexWhat()

        chkEx("{ val ps = data@*{}( f(*) ); return ps; }", "list<(integer,text)->text>[fn[f(*,*)],fn[f(*,*)]]")
        chkEx("{ val ps = data@*{}( f(*) ); return ps@*{} ( $(99,'Z') ); }", "list<text>[text[f:99,Z],text[f:99,Z]]")

        chkEx("{ val ps = data@*{}( f(i=.i, *) ); return ps; }", "list<(text)->text>[fn[f(int[11],*)],fn[f(int[22],*)]]")
        chkEx("{ val ps = data@*{}( f(i=.i, *) ); return ps@*{}( $('Z') ); }", "list<text>[text[f:11,Z],text[f:22,Z]]")
        chkEx("{ val ps = data@*{}( f(i=99, *) ); return ps; }", "list<(text)->text>[fn[f(int[99],*)],fn[f(int[99],*)]]")
        chkEx("{ val ps = data@*{}( f(i=99, *) ); return ps@*{}( $('Z') ); }", "list<text>[text[f:99,Z],text[f:99,Z]]")

        chkEx("{ val ps = data@*{}( f(t=.t, *) ); return ps; }", "list<(integer)->text>[fn[f(*,text[A])],fn[f(*,text[B])]]")
        chkEx("{ val ps = data@*{}( f(t=.t, *) ); return ps@*{}( $(99) ); }", "list<text>[text[f:99,A],text[f:99,B]]")
        chkEx("{ val ps = data@*{}( f(t='Z', *) ); return ps; }", "list<(integer)->text>[fn[f(*,text[Z])],fn[f(*,text[Z])]]")
        chkEx("{ val ps = data@*{}( f(t='Z', *) ); return ps@*{}( $(99) ); }", "list<text>[text[f:99,Z],text[f:99,Z]]")

        chkEx("{ val ps = data@*{}( f(i=.i, t=.t, *) ); return ps; }", "list<()->text>[fn[f(int[11],text[A])],fn[f(int[22],text[B])]]")
        chkEx("{ val ps = data@*{}( f(i=.i, t=.t, *) ); return ps@*{}( $() ); }", "list<text>[text[f:11,A],text[f:22,B]]")
        chkEx("{ val ps = data@*{}( f(i=.i, t='Z', *) ); return ps; }", "list<()->text>[fn[f(int[11],text[Z])],fn[f(int[22],text[Z])]]")
        chkEx("{ val ps = data@*{}( f(i=.i, t='Z', *) ); return ps@*{}( $() ); }", "list<text>[text[f:11,Z],text[f:22,Z]]")

        chkEx("{ val ps = data@*{}( f(i=99, t=.t, *) ); return ps; }", "list<()->text>[fn[f(int[99],text[A])],fn[f(int[99],text[B])]]")
        chkEx("{ val ps = data@*{}( f(i=99, t=.t, *) ); return ps@*{}( $() ); }", "list<text>[text[f:99,A],text[f:99,B]]")
        chkEx("{ val ps = data@*{}( f(i=99, t='Z', *) ); return ps; }", "list<()->text>[fn[f(int[99],text[Z])],fn[f(int[99],text[Z])]]")
        chkEx("{ val ps = data@*{}( f(i=99, t='Z', *) ); return ps@*{}( $() ); }", "list<text>[text[f:99,Z],text[f:99,Z]]")
    }

    @Test fun testPartialApplicationDefaultValues() {
        def("function f(i: integer = 55, t: text = 'R') = 'f:'+i+','+t;")
        initComplexWhat()

        chkEx("{ val ps = data@*{}( f(*) ); return ps; }", "list<()->text>[fn[f(int[55],text[R])],fn[f(int[55],text[R])]]")
        chkEx("{ val ps = data@*{}( f(*) ); return ps@*{} ( $() ); }", "list<text>[text[f:55,R],text[f:55,R]]")

        chkEx("{ val ps = data@*{}( f(i=*) ); return ps; }", "list<(integer)->text>[fn[f(*,text[R])],fn[f(*,text[R])]]")
        chkEx("{ val ps = data@*{}( f(i=*) ); return ps@*{}( $(99) ); }", "list<text>[text[f:99,R],text[f:99,R]]")
        chkEx("{ val ps = data@*{}( f(i=.i, *) ); return ps; }", "list<()->text>[fn[f(int[11],text[R])],fn[f(int[22],text[R])]]")
        chkEx("{ val ps = data@*{}( f(i=.i, *) ); return ps@*{}( $() ); }", "list<text>[text[f:11,R],text[f:22,R]]")
        chkEx("{ val ps = data@*{}( f(i=99, *) ); return ps; }", "list<()->text>[fn[f(int[99],text[R])],fn[f(int[99],text[R])]]")
        chkEx("{ val ps = data@*{}( f(i=99, *) ); return ps@*{}( $() ); }", "list<text>[text[f:99,R],text[f:99,R]]")

        chkEx("{ val ps = data@*{}( f(t=*) ); return ps; }", "list<(text)->text>[fn[f(int[55],*)],fn[f(int[55],*)]]")
        chkEx("{ val ps = data@*{}( f(t=*) ); return ps@*{}( $('Z') ); }", "list<text>[text[f:55,Z],text[f:55,Z]]")
        chkEx("{ val ps = data@*{}( f(t=.t, *) ); return ps; }", "list<()->text>[fn[f(int[55],text[A])],fn[f(int[55],text[B])]]")
        chkEx("{ val ps = data@*{}( f(t=.t, *) ); return ps@*{}( $() ); }", "list<text>[text[f:55,A],text[f:55,B]]")
        chkEx("{ val ps = data@*{}( f(t='Z', *) ); return ps; }", "list<()->text>[fn[f(int[55],text[Z])],fn[f(int[55],text[Z])]]")
        chkEx("{ val ps = data@*{}( f(t='Z', *) ); return ps@*{}( $() ); }", "list<text>[text[f:55,Z],text[f:55,Z]]")

        chkEx("{ val ps = data@*{}( f(i=*, t=*) ); return ps; }", "list<(integer,text)->text>[fn[f(*,*)],fn[f(*,*)]]")
        chkEx("{ val ps = data@*{}( f(i=*, t=*) ); return ps@*{}( $(99,'Z') ); }", "list<text>[text[f:99,Z],text[f:99,Z]]")
        chkEx("{ val ps = data@*{}( f(i=.i, t=*) ); return ps; }", "list<(text)->text>[fn[f(int[11],*)],fn[f(int[22],*)]]")
        chkEx("{ val ps = data@*{}( f(i=.i, t=*) ); return ps@*{}( $('Z') ); }", "list<text>[text[f:11,Z],text[f:22,Z]]")
        chkEx("{ val ps = data@*{}( f(i=99, t=*) ); return ps; }", "list<(text)->text>[fn[f(int[99],*)],fn[f(int[99],*)]]")
        chkEx("{ val ps = data@*{}( f(i=99, t=*) ); return ps@*{}( $('Z') ); }", "list<text>[text[f:99,Z],text[f:99,Z]]")

        chkEx("{ val ps = data@*{}( f(t=*, i=*) ); return ps; }", "list<(text,integer)->text>[fn[f(*,*)],fn[f(*,*)]]")
        chkEx("{ val ps = data@*{}( f(t=*, i=*) ); return ps@*{}( $('Z',99) ); }", "list<text>[text[f:99,Z],text[f:99,Z]]")
        chkEx("{ val ps = data@*{}( f(t=.t, i=*) ); return ps; }", "list<(integer)->text>[fn[f(*,text[A])],fn[f(*,text[B])]]")
        chkEx("{ val ps = data@*{}( f(t=.t, i=*) ); return ps@*{}( $(99) ); }", "list<text>[text[f:99,A],text[f:99,B]]")
        chkEx("{ val ps = data@*{}( f(t='Z', i=*) ); return ps; }", "list<(integer)->text>[fn[f(*,text[Z])],fn[f(*,text[Z])]]")
        chkEx("{ val ps = data@*{}( f(t='Z', i=*) ); return ps@*{}( $(99) ); }", "list<text>[text[f:99,Z],text[f:99,Z]]")

        chkEx("{ val ps = data@*{}( f(i=.i, t=.t, *) ); return ps; }", "list<()->text>[fn[f(int[11],text[A])],fn[f(int[22],text[B])]]")
        chkEx("{ val ps = data@*{}( f(i=.i, t=.t, *) ); return ps@*{}( $() ); }", "list<text>[text[f:11,A],text[f:22,B]]")
        chkEx("{ val ps = data@*{}( f(t=.t, i=.i, *) ); return ps; }", "list<()->text>[fn[f(int[11],text[A])],fn[f(int[22],text[B])]]")
        chkEx("{ val ps = data@*{}( f(t=.t, i=.i, *) ); return ps@*{}( $() ); }", "list<text>[text[f:11,A],text[f:22,B]]")
    }

    @Test fun testPartialApplicationMulti() {
        def("function f(i: integer, t: text) = 'f:'+i+','+t;")
        initComplexWhat()

        chkEx("{ val ps = data@*{}( f(i=.i, *)(.t, *) ); return ps; }",
                "list<()->text>[fn[f(int[11],text[A])],fn[f(int[22],text[B])]]")

        chkEx("{ val ps = data@*{}( f(i=.i, *)(.t, *) ); return ps@*{}( $() ); }", "list<text>[text[f:11,A],text[f:22,B]]")

        chkEx("{ val ps = data@*{}( f(t=.t, *)(.i, *) ); return ps; }",
                "list<()->text>[fn[f(int[11],text[A])],fn[f(int[22],text[B])]]")

        chkEx("{ val ps = data@*{}( f(t=.t, *)(.i, *) ); return ps@*{}( $() ); }", "list<text>[text[f:11,A],text[f:22,B]]")
    }

    @Test fun testSysMemberFunction() {
        def("function f(i: integer) = 'f:'+i;")
        initComplexWhat()

        chk("data @* {} ( f(.i).replace('f',.t) )", "list<text>[text[A:11],text[B:22]]")
        chk("data @* {} ( f(.i).replace('f','Z') )", "list<text>[text[Z:11],text[Z:22]]")

        chk("data @* {} ( f(99).replace('f',.t) )", "list<text>[text[A:99],text[B:99]]")
        chk("data @* {} ( f(99).replace('f','Z') )", "list<text>[text[Z:99],text[Z:99]]")

        chk("data @* {} ( f(.i).replace('f',*) )", "list<(text)->text>[fn[text.replace(text[f],*)],fn[text.replace(text[f],*)]]")
        chk("data @* {} ( f(.i).replace('f',*)(.t) )", "list<text>[text[A:11],text[B:22]]")
        chk("data @* {} ( f(.i).replace('f',*)('Z') )", "list<text>[text[Z:11],text[Z:22]]")

        chk("data @* {} ( f(99).replace('f',*) )", "list<(text)->text>[fn[text.replace(text[f],*)],fn[text.replace(text[f],*)]]")
        chk("data @* {} ( f(99).replace('f',*)(.t) )", "list<text>[text[A:99],text[B:99]]")
        chk("data @* {} ( f(99).replace('f',*)('Z') )", "list<text>[text[Z:99],text[Z:99]]")

        chk("data @* {} ( f(.i, *)().replace('f',.t) )", "list<text>[text[A:11],text[B:22]]")
        chk("data @* {} ( f(.i, *)().replace('f','Z') )", "list<text>[text[Z:11],text[Z:22]]")

        chk("data @* {} ( f(.i, *)().replace('f',*)(.t) )", "list<text>[text[A:11],text[B:22]]")
        chk("data @* {} ( f(.i, *)().replace('f',*)('Z') )", "list<text>[text[Z:11],text[Z:22]]")
    }

    @Test fun testSysMemberFunctionSafeAccessCall() {
        def("function f(i: integer) = if (i < 20) 'f:'+i else null;")
        initComplexWhat()

        chk("data @* {} ( f(.i).replace('f',.t) )", "ct_err:expr_mem_null:text?:replace")
        chk("data @* {} ( f(.i).replace('f','Z') )", "ct_err:expr_mem_null:text?:replace")

        chk("data @* {} ( f(.i)?.replace('f',.t) )", "list<text?>[text[A:11],null]")
        chk("data @* {} ( f(.i)?.replace('f','Z') )", "list<text?>[text[Z:11],null]")

        chk("data @* {} ( f(10)?.replace('f',.t) )", "list<text?>[text[A:10],text[B:10]]")
        chk("data @* {} ( f(10)?.replace('f','Z') )", "list<text?>[text[Z:10],text[Z:10]]")
        chk("data @* {} ( f(99)?.replace('f',.t) )", "list<text?>[null,null]")
        chk("data @* {} ( f(99)?.replace('f','Z') )", "list<text?>[null,null]")
    }

    @Test fun testSysMemberFunctionSafeAccessPartial() {
        def("function f(i: integer) = if (i < 20) 'f:'+i else null;")
        initComplexWhat()

        chk("_type_of(data @ {} ( f(.i).replace('f',*) ))", "ct_err:expr_mem_null:text?:replace")
        chk("_type_of(data @ {} ( f(.i)?.replace('f',*) ))", "text[((text)->text)?]")

        chk("data @* {} ( f(.i)?.replace('f',*) )", "list<((text)->text)?>[fn[text.replace(text[f],*)],null]")
        chk("data @* {} ( f(.i)?.replace('f',*)(.t) )", "ct_err:expr_call_nofn:((text)->text)?")
        chk("data @* {} ( f(.i)?.replace('f',*)!!(.t) )", "ct_err:expr_sqlnotallowed")
    }

    private fun initComplexWhat() {
        tstCtx.useSql = true
        def("entity data { i: integer; t: text; }")
        insert("c0.data", "i,t", "100,11,'A'", "101,22,'B'")
    }
}
