/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.gtx

import net.postchain.gtv.GtvBigInteger
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.rell.base.lang.type.DecimalTest
import net.postchain.rell.base.lib.LibBlockTransactionTest
import net.postchain.rell.gtx.testutils.BaseGtxTest
import kotlin.test.Test
import java.math.BigInteger

class GtxTest: BaseGtxTest() {
    @Test fun testObject() {
        def("object foo { x: integer = 123; s: text = 'Hello'; }")
        chk("foo.x", "123")
        chk("foo.s", "'Hello'")
    }

    @Test fun testImport() {
        file("lib/foo.rell", "module; function f(): integer = 123;")
        def("import lib.foo;")
        chk("foo.f()", "123")
    }

    @Test fun testNamespaceOperation() {
        def("namespace foo { operation bar() {print('Hello');} }")
        chkCallOperation("foo.bar", listOf())
        chkOut("Hello")
    }

    @Test fun testNamespaceQuery() {
        def("namespace foo { query bar() = 123; }")
        chkCallQuery("foo.bar", "", "123")
    }

    @Test fun testModules() {
        file("lib/foo.rell", "module; function f(): integer = 123;")
        def("import lib.foo;")
        tst.modules = null
        chk("foo.f()", "123")
    }

    @Test fun testBlockTransactionOut() {
        tst.chainId = 333
        tst.inserts = LibBlockTransactionTest.BLOCK_INSERTS_CURRENT
        tst.gtv = true

        chkCompile("query q(): block? = null;", "OK")
        chkCompile("query q(): transaction? = null;", "OK")
        chkCompile("query q(v: block) = 0;", "OK")
        chkCompile("query q(v: transaction) = 0;", "OK")

        chk("block @ {.block_height == 10}", "101")
        chk("block @ {.block_height == 20}", "102")
        chk("transaction @ {.block.block_height == 10}", "201")
        chk("transaction @ {.block.block_height == 20}", "202")
    }

    @Test fun testBlockTransactionIn() {
        tst.chainId = 333
        tst.inserts = LibBlockTransactionTest.BLOCK_INSERTS_CURRENT
        tst.gtv = true
        tst.wrapRtErrors = false

        def("query blk(v: block) = (v.rowid, v.block_height);")
        def("query tx(v: transaction) = (v.rowid, v.block.rowid, v.block.block_height);")

        chkCallQuery("blk", "v:101", "[101,10]")
        chkCallQuery("blk", "v:102", "[102,20]")
        chkCallQuery("tx", "v:201", "[201,101,10]")
        chkCallQuery("tx", "v:202", "[202,102,20]")

        chkCallQuery("blk", "v:100", "gtv_err:obj_missing:[block]:100")
        chkCallQuery("blk", "v:103", "gtv_err:obj_missing:[block]:103")
        chkCallQuery("blk", "v:201", "gtv_err:obj_missing:[block]:201")
        chkCallQuery("blk", "v:202", "gtv_err:obj_missing:[block]:202")

        chkCallQuery("tx", "v:200", "gtv_err:obj_missing:[transaction]:200")
        chkCallQuery("tx", "v:203", "gtv_err:obj_missing:[transaction]:203")
        chkCallQuery("tx", "v:101", "gtv_err:obj_missing:[transaction]:101")
        chkCallQuery("tx", "v:102", "gtv_err:obj_missing:[transaction]:102")
    }

    @Test fun testArgumentErrorsQuery() {
        tst.gtv = true
        tst.wrapRtErrors = false
        def("query qint(x: integer) = 123;")
        def("query qtext(x: text) = 123;")

        chkCallQuery("qint", "x:321", "123")
        chkCallQuery("qint", "a:321", "rt_err:query:invalid_args:qint:a")
        chkCallQuery("qint", "x:321,a:654", "rt_err:query:invalid_args:qint:a")
        chkCallQuery("qint", "x:'abc'", "gtv_err:type:[integer]:INTEGER:STRING:param:x")
        chkCallQuery("qint", "x:[]", "gtv_err:type:[integer]:INTEGER:ARRAY:param:x")
        chkCallQuery("qint", "x:{}", "gtv_err:type:[integer]:INTEGER:DICT:param:x")

        chkCallQuery("qtext", "x:'abc'", "123")
        chkCallQuery("qtext", "a:'abc'", "rt_err:query:invalid_args:qtext:a")
        chkCallQuery("qtext", "x:'abc',a:'def'", "rt_err:query:invalid_args:qtext:a")
        chkCallQuery("qtext", "x:123", "gtv_err:type:[text]:STRING:INTEGER:param:x")
        chkCallQuery("qtext", "x:[]", "gtv_err:type:[text]:STRING:ARRAY:param:x")
        chkCallQuery("qtext", "x:{}", "gtv_err:type:[text]:STRING:DICT:param:x")
    }

    @Test fun testArgumentErrorsOperation() {
        tst.gtv = true
        tst.wrapRtErrors = false
        def("operation oint(x: integer) {}")
        def("operation otext(x: text) {}")

        chkCallOperation("oint", listOf("321"), "OK")
        chkCallOperation("oint", listOf("321","654"), "rt_err:operation:[oint]:arg_count:2:1")
        chkCallOperation("oint", listOf("'abc'"), "gtv_err:type:[integer]:INTEGER:STRING:param:x")
        chkCallOperation("oint", listOf("[]"), "gtv_err:type:[integer]:INTEGER:ARRAY:param:x")
        chkCallOperation("oint", listOf("{}"), "gtv_err:type:[integer]:INTEGER:DICT:param:x")

        chkCallOperation("otext", listOf("'abc'"), "OK")
        chkCallOperation("otext", listOf("'abc'","'def'"), "rt_err:operation:[otext]:arg_count:2:1")
        chkCallOperation("otext", listOf("123"), "gtv_err:type:[text]:STRING:INTEGER:param:x")
        chkCallOperation("otext", listOf("[]"), "gtv_err:type:[text]:STRING:ARRAY:param:x")
        chkCallOperation("otext", listOf("{}"), "gtv_err:type:[text]:STRING:DICT:param:x")
    }

    @Test fun testBigInteger() {
        tst.wrapRtErrors = false
        def("query qint(x: integer) = x;")
        def("query qdec(x: decimal) = x;")

        val two63 = "9223372036854775808"
        val ten25 = "10000000000000000000000000"

        chkCallQuery("qint", mapOf("x" to GtvBigInteger(BigInteger.valueOf(123))), "123")
        chkCallQuery("qint", mapOf("x" to GtvBigInteger(BigInteger.valueOf(Long.MAX_VALUE))), "9223372036854775807")
        chkCallQuery("qint", mapOf("x" to GtvBigInteger(BigInteger(two63))), "gtv_err:type:[integer]:out_of_range:$two63:param:x")
        chkCallQuery("qint", mapOf("x" to GtvBigInteger(BigInteger(ten25))), "gtv_err:type:[integer]:out_of_range:$ten25:param:x")

        chkCallQuery("qdec", mapOf("x" to GtvBigInteger(BigInteger.valueOf(123))), "'123'")
        chkCallQuery("qdec", mapOf("x" to GtvBigInteger(BigInteger.valueOf(Long.MAX_VALUE))), "'9223372036854775807'")
        chkCallQuery("qdec", mapOf("x" to GtvBigInteger(BigInteger(two63))), "'$two63'")
        chkCallQuery("qdec", mapOf("x" to GtvBigInteger(BigInteger(ten25))), "'$ten25'")

        val decMax = DecimalTest.LIMIT.subtract(BigInteger.ONE)
        chkCallQuery("qdec", mapOf("x" to GtvBigInteger(decMax)), "'$decMax'")
        chkCallQuery("qdec", mapOf("x" to GtvBigInteger(DecimalTest.LIMIT)), "rt_err:decimal:overflow")
    }

    @Test fun testOptionalParamsOperation() {
        def("operation op(x: integer = 123, y: text = 'hello', z: decimal = 45.67) {}")
        chkCallOperation("op", listOf(), "OK")
        chkCallOperation("op", listOf("321"), "OK")
        chkCallOperation("op", listOf("321", "bye"), "OK")
        chkCallOperation("op", listOf("321", "bye", "'76.54'"), "OK")
    }

    @Test fun testOptionalParamsQuery() {
        def("query q(x: integer = 123, y: text = 'hello', z: decimal = 45.67) = (x, y, z);")
        chkCallQuery("q", mapOf("x" to gtv(321), "y" to gtv("bye"), "z" to gtv("76.54")), "[321,'bye','76.54']")
        chkCallQuery("q", mapOf("y" to gtv("bye"), "z" to gtv("76.54")), "[123,'bye','76.54']")
        chkCallQuery("q", mapOf("x" to gtv(321), "z" to gtv("76.54")), "[321,'hello','76.54']")
        chkCallQuery("q", mapOf("x" to gtv(321), "y" to gtv("bye")), "[321,'bye','45.67']")
        chkCallQuery("q", mapOf("x" to gtv(321)), "[321,'hello','45.67']")
        chkCallQuery("q", mapOf("y" to gtv("bye")), "[123,'bye','45.67']")
        chkCallQuery("q", mapOf("z" to gtv("76.54")), "[123,'hello','76.54']")
    }

    @Test fun testVersionControl() {
        chkVer("function f() = crypto.eth_privkey_to_address(x'');", "0.13.5",
            "VER:lib:FUNCTION:[rell:crypto.eth_privkey_to_address]")
        chkVer("function f(m: rell.meta) {}", "0.13.5", "VER:lib:TYPE:[rell:rell.meta]")
        chkVer("function f(x: integer?) = x == 123;", "0.13.10", "VER:feature:binop_nullable_eq_value")
    }

    private fun chkVer(code: String, v1: String, err: String) {
        chkVerRt("$code query q() = 0;", v1, err, "0")
    }
}
