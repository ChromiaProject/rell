/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.api.gtx.lib

import net.postchain.common.exception.TransactionIncorrect
import net.postchain.rell.api.gtx.testutils.PostchainRellTestProjExt
import net.postchain.rell.base.testutils.chkFromGtv
import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.testutils.RellCodeTester
import net.postchain.rell.base.testutils.RellTestContext
import kotlin.test.Test

internal class LibRellTestTxTest: BaseRellTest() {
    override fun getProjExt() = PostchainRellTestProjExt

    init {
        tst.testLib = true
    }

    @Test fun testBlockConstructor() {
        def("operation foo(x: integer){}")

        chk("_type_of(rell.test.block())", "text[rell.test.block]")

        chk("rell.test.block()", "rell.test.block[]")
        chk("rell.test.block(rell.test.tx())", """rell.test.block[rell.test.tx[]]""")
        chk("rell.test.block(rell.test.tx(),rell.test.tx())", """rell.test.block[rell.test.tx[],rell.test.tx[]]""")

        chk("rell.test.block(list<rell.test.tx>())", "rell.test.block[]")
        chk("rell.test.block([rell.test.tx()])", """rell.test.block[rell.test.tx[]]""")
        chk("rell.test.block([rell.test.tx(),rell.test.tx()])", """rell.test.block[rell.test.tx[],rell.test.tx[]]""")

        chk("rell.test.block(foo(123))", "rell.test.block[rell.test.tx[op[foo(123)]]]")
        chk("rell.test.block(foo(123),foo(456))", "rell.test.block[rell.test.tx[op[foo(123)],op[foo(456)]]]")
        chk("rell.test.block(list<rell.test.op>())", "rell.test.block[rell.test.tx[]]")
        chk("rell.test.block([foo(123)])", "rell.test.block[rell.test.tx[op[foo(123)]]]")
        chk("rell.test.block([foo(123),foo(456)])", "rell.test.block[rell.test.tx[op[foo(123)],op[foo(456)]]]")

        chk("rell.test.block(struct<foo>(123))", "ct_err:expr_call_badargs:[rell.test.block]:[struct<foo>]")
    }

    @Test fun testBlockRun() {
        file("module.rell", "operation foo(x: integer) { print(x); }")
        initTxChain()
        repl.chk("val b = rell.test.block().tx(foo(123));")
        repl.chk("b.run();", "OUT:123", "null")
        repl.chk("b.run_must_fail().message != '';", "true")
        repl.chk("b.run_must_fail('Transaction already in database').message != '';", "true")
    }

    @Test fun testBlockTx() {
        def("operation foo(x: integer){}")

        chk("_type_of(rell.test.block().tx(foo(123)))", "text[rell.test.block]")
        chk("_type_of(rell.test.block().tx(foo(123)).tx(foo(456)))", "text[rell.test.block]")

        chk("rell.test.block()", "rell.test.block[]")
        chk("rell.test.block().tx()", "ct_err:expr_call_badargs:[rell.test.block.tx]:[]")
        chk("rell.test.block().tx(rell.test.tx(foo(123)))", "rell.test.block[rell.test.tx[op[foo(123)]]]")
        chk("rell.test.block().tx(rell.test.tx(foo(123)),rell.test.tx(foo(456)))",
                "rell.test.block[rell.test.tx[op[foo(123)]],rell.test.tx[op[foo(456)]]]")
        chk("rell.test.block().tx([rell.test.tx(foo(123))])", "rell.test.block[rell.test.tx[op[foo(123)]]]")
        chk("rell.test.block().tx([rell.test.tx(foo(123)),rell.test.tx(foo(456))])",
                "rell.test.block[rell.test.tx[op[foo(123)]],rell.test.tx[op[foo(456)]]]")
        chk("rell.test.block().tx(list<rell.test.tx>())", "rell.test.block[]")

        chk("rell.test.block().tx(foo(123))", "rell.test.block[rell.test.tx[op[foo(123)]]]")
        chk("rell.test.block().tx(foo(123),foo(456))", "rell.test.block[rell.test.tx[op[foo(123)],op[foo(456)]]]")
        chk("rell.test.block().tx(list<rell.test.op>())", "rell.test.block[rell.test.tx[]]")
        chk("rell.test.block().tx([foo(123)])", "rell.test.block[rell.test.tx[op[foo(123)]]]")
        chk("rell.test.block().tx([foo(123),foo(456)])", "rell.test.block[rell.test.tx[op[foo(123)],op[foo(456)]]]")
        chk("rell.test.block().tx(list<rell.test.op>())", "rell.test.block[rell.test.tx[]]")

        chk("rell.test.block().tx(foo(123)).tx(foo(456))",
                "rell.test.block[rell.test.tx[op[foo(123)]],rell.test.tx[op[foo(456)]]]")
        chk("rell.test.block(foo(123)).tx(foo(456)).tx(foo(789))",
                "rell.test.block[rell.test.tx[op[foo(123)]],rell.test.tx[op[foo(456)]],rell.test.tx[op[foo(789)]]]")

        chkEx("{ val b = rell.test.block(); return b === b.tx(foo(123)); }", "boolean[true]")
        chkEx("{ val b = rell.test.block(); return b === b.tx(foo(123)).tx(foo(456)); }", "boolean[true]")
    }

    @Test fun testBlockCopy() {
        def("operation foo(x: integer){}")

        chk("_type_of(rell.test.block().copy())", "text[rell.test.block]")

        chk("rell.test.block()", "rell.test.block[]")
        chk("rell.test.block().copy()", "rell.test.block[]")
        chk("rell.test.block(foo(123)).copy()", "rell.test.block[rell.test.tx[op[foo(123)]]]")
        chk("rell.test.block(foo(123)).copy().tx(foo(456))",
                "rell.test.block[rell.test.tx[op[foo(123)]],rell.test.tx[op[foo(456)]]]")

        var code = "val b = rell.test.block(); val b2 = b.copy(); b.tx(foo(123)); b2.tx(foo(456));"
        chkEx("{ $code return b; }", "rell.test.block[rell.test.tx[op[foo(123)]]]")
        chkEx("{ $code return b2; }", "rell.test.block[rell.test.tx[op[foo(456)]]]")

        code = "val b = rell.test.block(foo(123)); val b2 = b.copy(); b.tx(foo(456)); b2.tx(foo(789));"
        chkEx("{ $code return b; }", "rell.test.block[rell.test.tx[op[foo(123)]],rell.test.tx[op[foo(456)]]]")
        chkEx("{ $code return b2; }", "rell.test.block[rell.test.tx[op[foo(123)]],rell.test.tx[op[foo(789)]]]")

        code = "val b1 = rell.test.block(foo(123)); val b2 = b1.copy();"
        chkEx("{ $code return b1 === b2; }", "boolean[false]")
        chkEx("{ $code return b1 !== b2; }", "boolean[true]")
        chkEx("{ $code return b1 == b2; }", "boolean[true]")
        chkEx("{ $code return b1 != b2; }", "boolean[false]")
    }

    @Test fun testBlockAddTxThenChangeTx() {
        def("operation foo(x: integer){}")
        chkEx("{ val t = rell.test.tx(foo(123)); val b = rell.test.block(t); t.op(foo(456)); return (t, b); }",
                "(rell.test.tx[op[foo(123)],op[foo(456)]],rell.test.block[rell.test.tx[op[foo(123)]]])")
    }

    @Test fun testTxConstructor() {
        def("operation foo(x: integer, y: text){}")
        def("operation bar(p: text, q: integer){}")

        chk("_type_of(rell.test.tx())", "text[rell.test.tx]")

        chk("rell.test.tx()", "rell.test.tx[]")
        chk("rell.test.tx(foo(123,'Hello'))", """rell.test.tx[op[foo(123,"Hello")]]""")
        chk("rell.test.tx(foo(123,'Hello'),bar('Bye',456))", """rell.test.tx[op[foo(123,"Hello")],op[bar("Bye",456)]]""")

        chk("rell.test.tx(list<rell.test.op>())", "rell.test.tx[]")
        chk("rell.test.tx([foo(123,'Hello')])", """rell.test.tx[op[foo(123,"Hello")]]""")
        chk("rell.test.tx([foo(123,'Hello'),bar('Bye',456)])", """rell.test.tx[op[foo(123,"Hello")],op[bar("Bye",456)]]""")

        chk("rell.test.tx(struct<foo>(123,'Hello'))", """rell.test.tx[op[foo(123,"Hello")]]""")
        chk("rell.test.tx(struct<foo>(123,'Hello'),struct<bar>(456,'Bye'))",
                """rell.test.tx[op[foo(123,"Hello")],op[bar("Bye",456)]]""")

        chk("rell.test.tx(list<struct<foo>>())", """rell.test.tx[]""")
        chk("rell.test.tx([struct<foo>(123,'Hello')])", """rell.test.tx[op[foo(123,"Hello")]]""")
        chk("rell.test.tx([struct<foo>(123,'Hello'),struct<foo>(456,'Bye')])",
                """rell.test.tx[op[foo(123,"Hello")],op[foo(456,"Bye")]]""")
        chk("rell.test.tx([struct<foo>(123,'Hello'),struct<bar>(456,'Bye')])",
                "ct_err:expr_list_itemtype:[struct<foo>]:[struct<bar>]")
    }

    @Test fun testTxOp() {
        def("operation foo(x: integer){}")

        chk("_type_of(rell.test.tx().op(foo(123)))", "text[rell.test.tx]")

        chk("rell.test.tx().op(foo(123))", "rell.test.tx[op[foo(123)]]")
        chk("rell.test.tx().op(foo(123),foo(456))", "rell.test.tx[op[foo(123)],op[foo(456)]]")
        chk("rell.test.tx().op(foo(123)).op(foo(456))", "rell.test.tx[op[foo(123)],op[foo(456)]]")

        chk("rell.test.tx().op(list<rell.test.op>())", "rell.test.tx[]")
        chk("rell.test.tx().op([foo(123)])", "rell.test.tx[op[foo(123)]]")
        chk("rell.test.tx().op([foo(123),foo(456)])", "rell.test.tx[op[foo(123)],op[foo(456)]]")

        chk("rell.test.tx().op(struct<foo>(123))", "rell.test.tx[op[foo(123)]]")
        chk("rell.test.tx().op(struct<foo>(123), struct<foo>(456))", "rell.test.tx[op[foo(123)],op[foo(456)]]")
        chk("rell.test.tx().op(list<struct<foo>>())", "rell.test.tx[]")
        chk("rell.test.tx().op([struct<foo>(123)])", "rell.test.tx[op[foo(123)]]")
        chk("rell.test.tx().op([struct<foo>(123), struct<foo>(456)])", "rell.test.tx[op[foo(123)],op[foo(456)]]")

        chkEx("{ val t = rell.test.tx(); return t === t.op(foo(123)); }", "boolean[true]")
        chkEx("{ val t = rell.test.tx(); return t === t.op(foo(123)).op(foo(456)); }", "boolean[true]")
    }

    @Test fun testTxSign() {
        def("operation foo(x: integer){}")

        chkEx("{ val t = rell.test.tx(); return t === t.sign(rell.test.keypairs.bob); }", "boolean[true]")
        chkEx("{ val t = rell.test.tx(); return t === t.sign(rell.test.keypairs.bob).sign(rell.test.keypairs.alice); }",
                "boolean[true]")

        chkSignCommon("rell.test.tx", "rell.test.tx(foo(123))")
    }

    private fun chkSignCommon(exprType: String, expr: String) {
        chk("_type_of($expr.sign(rell.test.keypairs.bob))", "text[rell.test.tx]")

        chk("$expr.sign()", "ct_err:expr_call_badargs:[$exprType.sign]:[]")
        chk("$expr.sign(rell.test.keypairs.bob)", "rell.test.tx[op[foo(123)],034f35]")
        chk("$expr.sign(rell.test.keypairs.bob,rell.test.keypairs.alice)", "rell.test.tx[op[foo(123)],034f35,02466d]")
        chk("$expr.sign(rell.test.keypairs.bob).sign(rell.test.keypairs.alice)", "rell.test.tx[op[foo(123)],034f35,02466d]")

        chk("$expr.sign(list<rell.test.keypair>())", "rell.test.tx[op[foo(123)]]")
        chk("$expr.sign([rell.test.keypairs.bob])", "rell.test.tx[op[foo(123)],034f35]")
        chk("$expr.sign([rell.test.keypairs.bob,rell.test.keypairs.alice])", "rell.test.tx[op[foo(123)],034f35,02466d]")

        chk("$expr.sign(rell.test.privkeys.bob)", "rell.test.tx[op[foo(123)],034f35]")
        chk("$expr.sign(rell.test.privkeys.bob,rell.test.privkeys.alice)", "rell.test.tx[op[foo(123)],034f35,02466d]")
        chk("$expr.sign(rell.test.privkeys.bob).sign(rell.test.privkeys.alice)", "rell.test.tx[op[foo(123)],034f35,02466d]")

        chk("$expr.sign(list<byte_array>())", "rell.test.tx[op[foo(123)]]")
        chk("$expr.sign([rell.test.privkeys.bob])", "rell.test.tx[op[foo(123)],034f35]")
        chk("$expr.sign([rell.test.privkeys.bob,rell.test.privkeys.alice])", "rell.test.tx[op[foo(123)],034f35,02466d]")

        chk("$expr.sign(rell.test.keypair(priv = rell.test.privkeys.bob, pub = rell.test.pubkeys.bob))",
                "rell.test.tx[op[foo(123)],034f35]")

        chk("$expr.sign(rell.test.pubkeys.bob)", "rt_err:tx.sign:priv_key_size:32:33")
        chk("$expr.sign(x'')", "rt_err:tx.sign:priv_key_size:32:0")
        chk("$expr.sign(x'00')", "rt_err:tx.sign:priv_key_size:32:1")
        chk("$expr.sign(123)", "ct_err:expr_call_badargs:[$exprType.sign]:[integer]")
        chk("$expr.sign('bob')", "ct_err:expr_call_badargs:[$exprType.sign]:[text]")
        chk("$expr.sign(rell.test.keypair(priv=x'12', pub=x'34'))", "rt_err:keypair:wrong_byte_array_size:33:1")
    }

    @Test fun testTxCopy() {
        def("operation foo(x: integer){}")

        chk("_type_of(rell.test.tx().copy())", "text[rell.test.tx]")

        chk("rell.test.tx()", "rell.test.tx[]")
        chk("rell.test.tx().copy()", "rell.test.tx[]")
        chk("rell.test.tx().op(foo(123)).copy().op(foo(456))", "rell.test.tx[op[foo(123)],op[foo(456)]]")

        var code = "val t1 = rell.test.tx(); val t2 = t1.copy(); t1.op(foo(123)); t2.op(foo(456));"
        chkEx("{ $code return t1; }", "rell.test.tx[op[foo(123)]]")
        chkEx("{ $code return t2; }", "rell.test.tx[op[foo(456)]]")

        code = "val t1 = rell.test.tx(foo(123)); val t2 = t1.copy(); t1.op(foo(456)); t2.op(foo(789));"
        chkEx("{ $code return t1; }", "rell.test.tx[op[foo(123)],op[foo(456)]]")
        chkEx("{ $code return t2; }", "rell.test.tx[op[foo(123)],op[foo(789)]]")

        code = "val t1 = rell.test.tx(foo(123)); val t2 = t1.copy();"
        chkEx("{ $code return t1 === t2; }", "boolean[false]")
        chkEx("{ $code return t1 !== t2; }", "boolean[true]")
        chkEx("{ $code return t1 == t2; }", "boolean[true]")
        chkEx("{ $code return t1 != t2; }", "boolean[false]")
    }

    @Test fun testTxNop() {
        chk("rell.test.tx().nop()", "rell.test.tx[op[nop(0)]]")
        chk("rell.test.tx().nop()", "rell.test.tx[op[nop(0)]]")
        chk("rell.test.tx().nop().nop()", "rell.test.tx[op[nop(0)],op[nop(1)]]")
        chk("rell.test.tx().nop(123)", "rell.test.tx[op[nop(123)]]")
        chk("rell.test.tx().nop('Bob')", "rell.test.tx[op[nop(\"Bob\")]]")
        chk("rell.test.tx().nop(x'Beef')", "rell.test.tx[op[nop(x\"BEEF\")]]")
    }

    @Test fun testTxRun() {
        file("module.rell", "operation foo(x: integer) { print(x); }")
        initTxChain()
        repl.chk("val tx = rell.test.tx().op(foo(123));")
        repl.chk("tx.run();", "OUT:123", "null")
        repl.chk("tx.run_must_fail().message != '';", "true")
        repl.chk("tx.run_must_fail('Transaction already in database').message != '';", "true")
    }

    @Test fun testOpConstructor() {
        chk("rell.test.op()", "ct_err:expr_call_badargs:[rell.test.op]:[]")
        chk("rell.test.op('foo')", "op[foo()]")
        chk("rell.test.op('foo', (123).to_gtv())", "op[foo(123)]")
        chk("rell.test.op('foo', (123).to_gtv(), 'Hello'.to_gtv())", """op[foo(123,"Hello")]""")

        chk("rell.test.op('foo', list<gtv>())", "op[foo()]")
        //chk("rell.test.op('foo', [])", "op[foo()]")
        chk("rell.test.op('foo', [(123).to_gtv()])", "op[foo(123)]")
        chk("rell.test.op('foo', [(123).to_gtv(), 'Hello'.to_gtv()])", """op[foo(123,"Hello")]""")

        chk("rell.test.op('foo', 123)", "ct_err:expr_call_badargs:[rell.test.op]:[text,integer]")
        chk("rell.test.op('foo', 'Hello')", "ct_err:expr_call_badargs:[rell.test.op]:[text,text]")

        chk("rell.test.op('', list<gtv>())", "rt_err:rell.test.op:bad_name:")
        chk("rell.test.op('123', list<gtv>())", "rt_err:rell.test.op:bad_name:123")
        chk("rell.test.op('.foo', list<gtv>())", "rt_err:rell.test.op:bad_name:.foo")
        chk("rell.test.op('foo.', list<gtv>())", "rt_err:rell.test.op:bad_name:foo.")
        chk("rell.test.op('foo.bar', list<gtv>())", "op[foo.bar()]")
    }

    @Test fun testOpTx() {
        def("operation foo(x: integer){}")

        chk("_type_of(foo(123))", "text[rell.test.op]")
        chk("_type_of(foo(123).tx())", "text[rell.test.tx]")

        chk("foo(123)", "op[foo(123)]")
        chk("foo(123).tx()", "rell.test.tx[op[foo(123)]]")
    }

    @Test fun testOpSign() {
        def("operation foo(x: integer){}")
        chkSignCommon("rell.test.op", "foo(123)")
    }

    @Test fun testOpRun() {
        file("module.rell", "operation foo(x: integer) { print(x); }")
        initTxChain()

        repl.chk("val op = foo(123);")
        repl.chk("op.run();", "OUT:123", "null")
        repl.chk("op.run_must_fail().message != '';", "true")
        repl.chk("op.run_must_fail('Transaction already in database').message != '';", "true")
    }

    @Test fun testOpTypeCompatibility() {
        def("operation bob(x: integer){}")
        def("operation alice(x: integer){}")
        def("operation trudy(t: text){}")
        chkEx("{ var v = bob(123); v = alice(456); return v; }", """op[alice(456)]""")
        chkEx("{ var v = bob(123); v = trudy('Hello'); return v; }", """op[trudy("Hello")]""")
    }

    @Test fun testOpTypeExplicit() {
        def("operation bob(x: integer){}")
        chkEx("{ var v: rell.test.op; v = bob(123); return v; }", "op[bob(123)]")
        chkEx("{ var v: struct<bob>; v = bob(123); return 0; }", "ct_err:stmt_assign_type:[struct<bob>]:[rell.test.op]")
        chkEx("{ var v: rell.test.op; v = struct<bob>(123).to_test_op(); return v; }", "op[bob(123)]")
    }

    @Test fun testOpTypeOps() {
        def("operation foo(x: integer, y: text){}")
        def("operation bar(x: integer, y: text){}")

        chk("foo(123,'Hello') == foo(456,'Bye')", "boolean[false]")
        chk("foo(123,'Hello') == foo(123,'Hello')", "boolean[true]")
        chk("foo(123,'Hello') == foo(579-456,'Hello')", "boolean[true]")
        chk("foo(123,'Hello') != foo(456,'Bye')", "boolean[true]")
        chk("foo(123,'Hello') != foo(123,'Hello')", "boolean[false]")

        chk("foo(123,'Hello') == bar(123,'Hello')", "boolean[false]")
        chk("foo(123,'Hello') != bar(123,'Hello')", "boolean[true]")

        chk("foo(123,'Hello') < foo(123,'Hello')", "ct_err:binop_operand_type:<:[rell.test.op]:[rell.test.op]")
        chk("foo(123,'Hello') > foo(123,'Hello')", "ct_err:binop_operand_type:>:[rell.test.op]:[rell.test.op]")
        chk("foo(123,'Hello') <= foo(123,'Hello')", "ct_err:binop_operand_type:<=:[rell.test.op]:[rell.test.op]")
        chk("foo(123,'Hello') >= foo(123,'Hello')", "ct_err:binop_operand_type:>=:[rell.test.op]:[rell.test.op]")
    }

    @Test fun testOpTypeMemberName() {
        def("@mount('a.b.my_op') operation foo(x: integer, y: text){}")
        chk("foo(123,'Hello').name", "text[a.b.my_op]")
        chkEx("{ val op = foo(123,'Hello'); op.name = 'bar'; return 0; }", "ct_err:attr_not_mutable:rell.test.op.name")
    }

    @Test fun testOpTypeMemberArgs() {
        def("@mount('a.b.my_op') operation foo(x: integer, y: text){}")

        chk("foo(123,'Hello').args", "list<gtv>[gtv[123],gtv[\"Hello\"]]")
        chkEx("{ val op = foo(123,'Hello'); op.args.clear(); return op.args; }", "list<gtv>[gtv[123],gtv[\"Hello\"]]")
        chkEx("{ val op = foo(123,'Hello'); op.args = list<gtv>(); return 0; }",
            "ct_err:attr_not_mutable:rell.test.op.args")

        chkEx("{ val op = foo(123,'Hello'); val args = op.args; args.clear(); return (args, op.args); }",
            "(list<gtv>[],list<gtv>[gtv[123],gtv[\"Hello\"]])")
    }

    @Test fun testOpTypeMemberToGtxOperation() {
        def("@mount('a.b.my_op') operation foo(x: integer, y: text){}")
        chk("_type_of(foo(123,'Hello').to_gtx_operation())", "text[gtx_operation]")
        chk("foo(123,'Hello').to_gtx_operation()",
            "gtx_operation[name=text[a.b.my_op],args=list<gtv>[gtv[123],gtv[\"Hello\"]]]")
    }

    @Test fun testOpTypeAsMapKey() {
        def("operation foo(x: integer, y: text){}")
        def("operation bar(p: text, q: integer){}")
        chk("[ foo(123,'Hello') : 'Bob', bar('Bye', 456) : 'Alice' ]",
                """map<rell.test.op,text>[op[foo(123,"Hello")]=text[Bob],op[bar("Bye",456)]=text[Alice]]""")
    }

    @Test fun testGtxOperationToTestOp() {
        chk("_type_of(gtx_operation('foo', list<gtv>()).to_test_op())", "text[rell.test.op]")
        chk("gtx_operation('foo', list<gtv>()).to_test_op()", "op[foo()]")
        chk("gtx_operation('foo', [(123).to_gtv(),'Hello'.to_gtv()]).to_test_op()", "op[foo(123,\"Hello\")]")
    }

    @Test fun testSignAndRun() {
        initSignAndRun()

        val expr = "data @? {} ( @sort_desc _=.rowid, _=.x, _=.signers ) limit 1"

        repl.chk(expr, "null")
        repl.chk("foo(100).run();", "null")
        repl.chk(expr, "(1,100,[])")

        repl.chk("foo(101).sign(rell.test.keypairs.bob).run();", "null")
        repl.chk(expr, "(2,101,[034f35])")

        repl.chk("foo(102).sign(rell.test.keypairs.alice).run();", "null")
        repl.chk(expr, "(3,102,[02466d])")

        repl.chk("foo(103).sign(rell.test.keypairs.bob,rell.test.keypairs.alice).run();", "null")
        repl.chk(expr, "(4,103,[034f35,02466d])")

        repl.chk("foo(104).sign(rell.test.keypairs.bob).sign(rell.test.keypairs.alice).run();", "null")
        repl.chk(expr, "(5,104,[034f35,02466d])")

        repl.chk("foo(105).sign(rell.test.privkeys.bob).run();", "null")
        repl.chk(expr, "(6,105,[034f35])")

        repl.chk("foo(106).sign(rell.test.privkeys.alice).run();", "null")
        repl.chk(expr, "(7,106,[02466d])")
    }

    @Test fun testSignAndRunError() {
        initSignAndRun()
        val expr = "data @? {} ( @sort_desc _=.rowid, _=.x, _=.signers ) limit 1"
        repl.chk(expr, "null")
        repl.chk("val kp = rell.test.keypair(priv = rell.test.privkeys.bob, pub = rell.test.pubkeys.alice);")
        repl.chk("foo(100).sign(kp).run();", "rt_err:fn:rell.test.tx.run:fail:${TransactionIncorrect::class.qualifiedName}")
        repl.chk(expr, "null")
    }

    @Test fun testNop() {
        chk("rell.test.nop()", "op[nop(0)]")
        chk("rell.test.nop()", "op[nop(0)]")
        chk("[rell.test.nop(), rell.test.nop(), rell.test.nop()]", "list<rell.test.op>[op[nop(0)],op[nop(1)],op[nop(2)]]")
        chk("rell.test.nop(123)", """op[nop(123)]""")
        chk("rell.test.nop('Bob')", """op[nop("Bob")]""")
        chk("rell.test.nop(x'beef')", """op[nop(x"BEEF")]""")

        repl.chk("rell.test.nop()", "RES:op[nop(0)]")
        repl.chk("rell.test.nop()", "RES:op[nop(1)]")
    }

    @Test fun testNopRun() {
        file("module.rell", "operation foo(x: integer) { print(x); }")
        initTxChain()

        val err = "rt_err:fn:rell.test.tx.run:fail:${TransactionIncorrect::class.qualifiedName}"

        repl.chk("rell.test.tx(foo(123)).run();", "OUT:123", "null")
        repl.chk("rell.test.tx(rell.test.op('nop')).run();", err)
        repl.chk("rell.test.tx(rell.test.op('nop'), foo(123)).run();", "OUT:123", "null")
        repl.chk("rell.test.tx(foo(123), rell.test.op('nop')).run();", "OUT:123", "null")
        repl.chk("rell.test.tx(rell.test.op('nop'), foo(123), rell.test.op('nop')).run();", err)

        repl.chk("rell.test.tx(foo(321), rell.test.op('nop')).run();", "OUT:321", "null")
        repl.chk("rell.test.tx(foo(321), rell.test.op('nop'), rell.test.op('nop')).run();", err)
        repl.chk("rell.test.tx(foo(321), rell.test.op('nop', (456).to_gtv())).run();", "OUT:321", "null")
        repl.chk("rell.test.tx(foo(321), rell.test.op('nop', 'Hello'.to_gtv())).run();", "OUT:321", "null")
        repl.chk("rell.test.tx(foo(321), rell.test.op('nop', x'beef'.to_gtv())).run();", "OUT:321", "null")
        repl.chk("rell.test.tx(foo(321), rell.test.op('nop', (5).to_gtv(), (7).to_gtv())).run();", err)
    }

    @Test fun testNopRunRepeat() {
        file("module.rell", "operation foo(x: integer) { print(x); }")
        initTxChain()
        repl.chk("rell.test.tx(rell.test.nop(), foo(123)).run()", "OUT:123", "null")
        repl.chk("rell.test.tx(rell.test.nop(), foo(123)).run()", "OUT:123", "null")
        repl.chk("rell.test.tx(rell.test.nop(), foo(123)).run()", "OUT:123", "null")
    }

    @Test fun testRunMustFail() {
        file("module.rell", "operation foo(x: integer) { require(x > 0); print(x); }")
        initTxChain()

        repl.chk("block @? {} ( @sort_desc .block_height ) limit 1", "null")

        repl.chk("foo(123).run();", "OUT:123", "null")
        repl.chk("block @? {} ( @sort_desc .block_height ) limit 1", "0")

        repl.chk("foo(-1).run();", "req_err:null")
        repl.chk("block @? {} ( @sort_desc .block_height ) limit 1", "0")

        repl.chk("foo(456).run_must_fail();", "OUT:456", "rt_err:fn:rell.test.op.run_must_fail:nofail")
        repl.chk("block @? {} ( @sort_desc .block_height ) limit 1", "1")

        repl.chk("foo(-1).run_must_fail();", "Requirement error")
        repl.chk("block @? {} ( @sort_desc .block_height ) limit 1", "1")
    }

    @Test fun testRunMustFailExpected() {
        file("module.rell", "operation foo(x: integer) { require(x > 0, 'x is negative: ' + x); }")
        initTxChain()
        repl.chk("foo(123).run_must_fail('x is negative: -1');", "rt_err:fn:rell.test.op.run_must_fail:nofail")
        repl.chk("foo(-1).run_must_fail('x is negative: -1');", "x is negative: -1")
        repl.chk("foo(-1).run_must_fail('x is negative: -2');", "asrt_err:run_must_fail:mismatch:[x is negative: -2]:[x is negative: -1]")
        repl.chk("foo(-1).run_must_fail('x is negative');", "x is negative: -1")
        repl.chk("foo(-1).run_must_fail('negative');", "x is negative: -1")
        repl.chk("foo(-1).run_must_fail('positive');", "asrt_err:run_must_fail:mismatch:[positive]:[x is negative: -1]")
    }

    @Test fun testRunMustFailResult() {
        file("module.rell", "operation foo(x: integer) { require(x > 0, 'x is negative: ' + x); }")
        initTxChain()
        repl.outPlainValues = false
        repl.chk("_type_of(foo(-1).run_must_fail());", "RES:text[rell.test.failure]")
        repl.chk("_type_of(foo(-1).run_must_fail('x is negative: -1'));", "RES:text[rell.test.failure]")
        repl.chk("foo(-1).run_must_fail();", "RES:rell.test.failure[x is negative: -1]")
        repl.chk("foo(-1).run_must_fail('x is negative: -1');", "RES:rell.test.failure[x is negative: -1]")
    }

    @Test fun testDuplicateTx() {
        file("module.rell", "operation foo(x: integer) { print(x); }")
        initTxChain()
        repl.chk("foo(123).run();", "OUT:123", "null")
        repl.chk("foo(123).run();", "rt_err:block_runner:tx_duplicate")
        repl.chk("foo(456).run();", "OUT:456", "null")
        repl.chk("block @* {} ( .block_height )", "[0, 1]")
    }

    @Test fun testDuplicateTxInSingleBlock() {
        file("module.rell", "operation foo(x: integer) { print(x); }")
        initTxChain()
        repl.chk(
            "rell.test.block(rell.test.tx(foo(123)), rell.test.tx(foo(123))).run();",
            "OUT:123",
            "rt_err:block_runner:tx_duplicate"
        )
    }

    private fun initSignAndRun() {
        file("module.rell", """
            entity data { x: integer; signers: text; }
            operation foo(x: integer) {
                var sigs = '';
                for (s in op_context.get_signers()) {
                    if (not sigs.empty()) sigs += ',';
                    sigs += s.to_hex().sub(0,6);
                }
                create data(x, '[' + sigs + ']');
            }
        """)

        initTxChain()
    }

    private fun initTxChain() {
        initTxChain(tstCtx, tst)
        repl.outPlainValues = true
    }

    /*@Test*/ fun testToFromGtvBlock() {
        def("operation foo(x: integer, y: text) {}")

        val brid = "00".repeat(32)
        chk("rell.test.block().to_gtv()", """gtv[[]]""")
        chk("rell.test.block(rell.test.tx(foo(123,'Hello'))).to_gtv()",
                """gtv[[[["$brid",[["foo",[123,"Hello"]]],[]],[]]]]""")

        chkFromGtv("[]", "rell.test.block.from_gtv(g)", """rell.test.block[]""")
        chkFromGtv("[[['$brid',[['foo',[123,'Hello']]],[]],[]]]", "rell.test.block.from_gtv(g)",
                """rell.test.block[rell.test.tx[op[foo(123,"Hello")]]]""")
    }

    /*@Test*/ fun testToFromGtvTx() {
        def("operation foo(x: integer, y: text) {}")

        val brid = "00".repeat(32)
        chk("rell.test.tx().to_gtv()", """gtv[[["$brid",[],[]],[]]]""")
        chk("rell.test.tx(foo(123,'Hello')).to_gtv()", """gtv[[["$brid",[["foo",[123,"Hello"]]],[]],[]]]""")

        chkFromGtv("[['$brid',[],[]],[]]", "rell.test.tx.from_gtv(g)", """rell.test.tx[]""")
        chkFromGtv("[['$brid',[['foo',[123,'Hello']]],[]],[]]", "rell.test.tx.from_gtv(g)",
                """rell.test.tx[op[foo(123,"Hello")]]""")
    }

    /*@Test*/ fun testToFromGtvOp() {
        def("operation foo(x: integer, y: text) {}")

        chk("foo(123,'Hello').to_gtv()", """gtv[["foo",[123,"Hello"]]]""")
        chk("foo(123,'Hello').to_gtv_pretty()", """gtv[["foo",[123,"Hello"]]]""")

        chkFromGtv("['foo',[123,'Hello']]", "rell.test.op.from_gtv(g)", """op[foo(123,"Hello")]""")
        chkFromGtv("[123,'Hello']", "rell.test.op.from_gtv(g)", "rt_err:from_gtv")
        chkFromGtv("['foo',123,'Hello']", "rell.test.op.from_gtv(g)", "rt_err:from_gtv")
        chkFromGtv("['',[123,'Hello']]", "rell.test.op.from_gtv(g)", "rt_err:from_gtv")
        chkFromGtv("['987',[123,'Hello']]", "rell.test.op.from_gtv(g)", "rt_err:from_gtv")
    }

    private fun chkFromGtv(gtv: String, expr: String, expected: String) = chkFromGtv(tst, gtv, expr, expected)

    companion object {
        fun initTxChain(tstCtx: RellTestContext, tst: RellCodeTester) {
            tstCtx.useSql = true
            val chainId = 0L
            val chainRid = "DeadBeef".repeat(8)
            tst.replModule = ""
            tst.chainId = chainId
            tst.blockchainRid = chainRid
            tstCtx.blockchain(chainId, chainRid)
        }
    }
}
