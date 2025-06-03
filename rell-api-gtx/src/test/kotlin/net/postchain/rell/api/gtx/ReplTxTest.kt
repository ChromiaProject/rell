/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.api.gtx

import net.postchain.rell.api.gtx.testutils.PostchainRellTestProjExt
import net.postchain.rell.base.testutils.BaseRellTest
import org.junit.Test

class ReplTxTest: BaseRellTest(useSql = true) {
    override fun getProjExt() = PostchainRellTestProjExt

    private fun initStuff() {
        val chainId = 0L
        val chainRid = "DeadBeef".repeat(8)
        tst.replModule = ""
        tst.chainId = chainId
        tst.blockchainRid = chainRid
        tstCtx.blockchain(chainId, chainRid)
    }

    @Test fun testSysEntities() {
        repl.chk("_type_of(transaction@{})", "RES:text[transaction]")
        repl.chk("_type_of(block@{})", "RES:text[block]")
        repl.chk("transaction @* {}", "RES:list<transaction>[]")
        repl.chk("block @* {}", "RES:list<block>[]")
    }

    @Test fun testSysEntitiesCompatibility() {
        repl.chk("var b = block @? {};")
        repl.chk("b = block @? {};")
        repl.chk("_type_of(b)", "RES:text[block?]")
        repl.chk("b", "RES:null")
    }

    @Test fun testTxRun() {
        file("module.rell", "operation foo(x: integer) { print('x='+x); }")
        initStuff()

        repl.chk("val op = foo(123);")
        repl.chk("op", "RES:op[foo(123)]")
        repl.chk("val tx = rell.test.tx(op);")
        repl.chk("tx.run();", "OUT:x=123", "RES:unit")
    }

    @Test fun testBlockRun() {
        file("module.rell", "operation foo(x: integer) { print('x='+x); }")
        initStuff()

        repl.chk("val tx1 = rell.test.tx(foo(123));")
        repl.chk("val tx2 = rell.test.tx(foo(456));")
        repl.chk("val blk = rell.test.block([tx1, tx2]);")
        repl.chk("blk.run();", "OUT:x=123", "OUT:x=456", "RES:unit")
    }

    @Test fun testCallOpByDynamicName() {
        file("module.rell", "operation foo(x: integer) { print('x='+x); }")
        initStuff()

        repl.chk("val op = rell.test.op('foo', [(123).to_gtv()]);")
        repl.chk("val tx = rell.test.tx(op);")
        repl.chk("tx.run();", "OUT:x=123", "RES:unit")
    }

    @Test fun testMultipleBlocks() {
        file("module.rell", """
            operation foo(x: integer) { print('foo('+x+')'); }
            operation bar(x: integer) { print('bar('+x+')'); }
        """)
        initStuff()

        repl.chk("rell.test.tx(foo(123)).run();", "OUT:foo(123)", "RES:unit")
        repl.chk("block@*{}", "RES:list<block>[block[1]]")
        repl.chk("transaction@*{}", "RES:list<transaction>[transaction[1]]")

        repl.chk("rell.test.tx(bar(456)).run();", "OUT:bar(456)", "RES:unit")
        repl.chk("block@*{}", "RES:list<block>[block[1],block[2]]")
        repl.chk("transaction@*{}", "RES:list<transaction>[transaction[1],transaction[2]]")

        repl.chk("rell.test.tx(foo(789)).run();", "OUT:foo(789)", "RES:unit")
        repl.chk("block@*{}", "RES:list<block>[block[1],block[2],block[3]]")
        repl.chk("transaction@*{}", "RES:list<transaction>[transaction[1],transaction[2],transaction[3]]")
    }

    @Test fun testNoRepl() {
        tst.testLib = true
        file("module.rell", "module;")
        def("operation foo(x: integer){}")
        chkEx("{ rell.test.tx(foo(123)).run(); return 0; }", "rt_err:fn:rell.test.tx.run:no_repl_test")
    }

    @Test fun testImportedOperations() {
        file("module.rell", "module;")
        file("a.rell", "module; operation foo(x: integer){print('foo:'+x);}")
        file("b.rell", "module; operation bar(y: integer){print('bar:'+y);}")
        initStuff()

        repl.chk("import a;")
        repl.chk("rell.test.tx(a.foo(123)).run();", "OUT:foo:123", "RES:unit")
        repl.chk("import b;")
        repl.chk("rell.test.tx(b.bar(456)).run();", "OUT:bar:456", "RES:unit")
    }
}
