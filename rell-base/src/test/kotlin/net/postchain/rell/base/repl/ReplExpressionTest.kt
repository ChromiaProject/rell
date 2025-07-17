/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.repl

import net.postchain.rell.base.testutils.BaseRellTest
import kotlin.test.Test

class ReplExpressionTest: BaseRellTest() {
    @Test fun testExpr() {
        repl.chk("2 + 2", "RES:int[4]")
        repl.chk("1*2*3*4*5*6*7*8*9*10*11*12*13", "RES:int[6227020800]")
        repl.chk("integer.MAX_VALUE", "RES:int[9223372036854775807]")
    }

    @Test fun testOperation() {
        file("root.rell", "operation foo(x: integer, y: text) {}")
        file("lib.rell", "module; operation bar(p: text, q: integer) {}")
        tst.replModule = ""
        repl.chk("foo(123, 'Hello')", """RES:op[foo(123,"Hello")]""")
        repl.chk("import lib;")
        repl.chk("lib.bar('Bye', 456)", """RES:op[bar("Bye",456)]""")
    }

    @Test fun testQuery() {
        file("root.rell", "query info() = 123;")
        file("lib.rell", "module; query help() = 'Hello';")
        tst.replModule = ""
        repl.chk("info()", "RES:int[123]")
        repl.chk("import lib;")
        repl.chk("lib.help()", "RES:text[Hello]")
    }

    @Test fun testSyntaxError() {
        repl.chk("")
        repl.chk("***", "CTE:<console>:syntax")
        repl.chk(",,,", "CTE:<console>:syntax")
    }

    @Test fun testRuntimeError() {
        repl.chk("123 / 0", "rt_err:expr:/:div0:123")
    }

    @Test fun testPrint() {
        repl.chk("print(123)", "OUT:123", "RES:unit")
    }

    @Test fun testLiterals() {
        repl.chk("null", "RES:null")
        repl.chk("true", "RES:boolean[true]")
        repl.chk("123", "RES:int[123]")
        repl.chk("123.456", "RES:dec[123.456]")
        repl.chk("'Hello'", "RES:text[Hello]")
        repl.chk("x'1234BEEF'", "RES:byte_array[1234beef]")
    }

    @Test fun testSimpleExprs() {
        repl.chk("val x = 123;")
        repl.chk("x", "RES:int[123]")
        repl.chk(".y", "CTE:<console>:expr_attr_unknown:y")
        repl.chk("(x + 5) * 6", "RES:int[768]")
    }

    @Test fun testCollectionExprs() {
        repl.chk("[1,2,3]", "RES:list<integer>[int[1],int[2],int[3]]")
        repl.chk("[123:'Hello']", "RES:map<integer,text>[int[123]=text[Hello]]")
        repl.chk("list<integer>()", "RES:list<integer>[]")
        repl.chk("set<integer>()", "RES:set<integer>[]")
        repl.chk("map<integer,text>()", "RES:map<integer,text>[]")
    }
}
