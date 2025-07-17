/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.api.gtx.lib

import net.postchain.rell.api.gtx.testutils.PostchainRellTestProjExt
import net.postchain.rell.base.testutils.BaseRellTest
import kotlin.test.Test

internal class LibRellTestEventsTest: BaseRellTest() {
    override fun getProjExt() = PostchainRellTestProjExt

    @Test fun testGetEvents() {
        file("module.rell", "operation foo(x: integer) { op_context.emit_event('foo', x.to_gtv()); }")
        LibRellTestTxTest.initTxChain(tstCtx, tst)

        repl.chk("get_events()", "CTE:<console>:unknown_name:get_events")
        repl.chk("rell.test.get_events()", "RES:list<(text,gtv)>[]")
        repl.chk("foo(123).run();", "RES:unit")
        repl.chk("rell.test.get_events()", "RES:list<(text,gtv)>[(text[foo],gtv[123])]")
        repl.chk("foo(456).run();", "RES:unit")
        repl.chk("rell.test.get_events()", "RES:list<(text,gtv)>[(text[foo],gtv[456])]")
        repl.chk("rell.test.tx(foo(123),foo(456)).run();", "RES:unit")
        repl.chk("rell.test.get_events()", "RES:list<(text,gtv)>[(text[foo],gtv[123]),(text[foo],gtv[456])]")
    }

    @Test fun testGetEventsSideEffects() {
        file("module.rell", "operation foo(x: integer) { op_context.emit_event('foo', x.to_gtv()); }")
        LibRellTestTxTest.initTxChain(tstCtx, tst)

        repl.chk("rell.test.get_events()", "RES:list<(text,gtv)>[]")
        repl.chk("foo(123).run();", "RES:unit")
        repl.chk("rell.test.get_events()", "RES:list<(text,gtv)>[(text[foo],gtv[123])]")
        repl.chk("rell.test.get_events()", "RES:list<(text,gtv)>[(text[foo],gtv[123])]")
        repl.chk("rell.test.get_events()", "RES:list<(text,gtv)>[(text[foo],gtv[123])]")

        repl.chk("rell.test.get_events().clear()", "RES:unit")
        repl.chk("rell.test.get_events()", "RES:list<(text,gtv)>[(text[foo],gtv[123])]")

        repl.chk("rell.test.get_events().add(('bar',(789).to_gtv()))", "RES:boolean[true]")
        repl.chk("rell.test.get_events()", "RES:list<(text,gtv)>[(text[foo],gtv[123])]")
    }

    @Test fun testGetEventsNonTestMode() {
        tst.testLib = false
        file("op.rell", "module; operation foo() { rell.test.get_events(); }")
        file("fn.rell", "module; function foo() { rell.test.get_events(); }")
        chkCompile("", "OK")
        chkCompile("import op;", "ct_err:op.rell:unknown_name:[rell:rell]:test")
        chkCompile("import fn;", "ct_err:fn.rell:unknown_name:[rell:rell]:test")
    }

    @Test fun testAssertEvents() {
        file("module.rell", """
            operation foo(x: integer) { op_context.emit_event('foo', x.to_gtv()); }
            function event(type: text, data: integer) = (type, data.to_gtv());
        """)
        LibRellTestTxTest.initTxChain(tstCtx, tst)

        repl.chk("assert_events()", "RES:unit")
        repl.chk("assert_events(event('foo',123))",
            "asrt_err:assert_events:list<(text,gtv)>[]:list<(text,gtv)>[(text[foo],gtv[123])]")

        repl.chk("foo(123).run();", "RES:unit")
        repl.chk("assert_events()", "asrt_err:assert_events:list<(text,gtv)>[(text[foo],gtv[123])]:list<(text,gtv)>[]")
        repl.chk("assert_events(event('foo',123))", "RES:unit")
        repl.chk("assert_events(event('foo',123))", "RES:unit")
        repl.chk("assert_events(event('bar',123))",
            "asrt_err:assert_events:list<(text,gtv)>[(text[foo],gtv[123])]:list<(text,gtv)>[(text[bar],gtv[123])]")
        repl.chk("assert_events(event('foo',124))",
            "asrt_err:assert_events:list<(text,gtv)>[(text[foo],gtv[123])]:list<(text,gtv)>[(text[foo],gtv[124])]")

        repl.chk("foo(456).run();", "RES:unit")
        repl.chk("rell.test.assert_events()",
            "asrt_err:assert_events:list<(text,gtv)>[(text[foo],gtv[456])]:list<(text,gtv)>[]")
        repl.chk("rell.test.assert_events(event('foo',456))", "RES:unit")
        repl.chk("rell.test.get_events()", "RES:list<(text,gtv)>[(text[foo],gtv[456])]")
    }
}
