/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.def

import net.postchain.rell.base.lib.LibBlockTransactionTest
import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.testutils.RellCodeTester
import net.postchain.rell.base.testutils.RellTestContext
import net.postchain.rell.base.testutils.RellTestUtils
import kotlin.test.Test

class ExternalTest: BaseRellTest(useSql = true) {
    @Test fun testSimple() {
        tstCtx.blockchain(333, "deadbeef")

        initExternalChain(inserts = LibBlockTransactionTest.BLOCK_INSERTS_333)
        def("@external('foo') namespace { @log entity user { name; } }")
        tst.chainDependency("foo", "deadbeef", 1000)
        chk("user @ {} ( _=user, _=.name )", "([foo]:user[1],text[Bob])")
    }

    @Test fun testExternalEntity() {
        tstCtx.blockchain(333, "deadbeef")

        initExternalChain(inserts = LibBlockTransactionTest.BLOCK_INSERTS_333)
        def("@external('foo') @log entity user { name; }")
        tst.chainDependency("foo", "deadbeef", 1000)
        chk("user @ {} ( _=user, _=.name )", "([foo]:user[1],text[Bob])")
    }

    @Test fun testExternalNamespace() {
        tstCtx.blockchain(333, "deadbeef")

        initExternalChain(inserts = LibBlockTransactionTest.BLOCK_INSERTS_333)
        def("@external('foo') @mount('') namespace ns { @log entity user { name; } }")
        tst.chainDependency("foo", "deadbeef", 1000)
        chk("ns.user @ {} ( _=user, _=.name )", "([foo]:ns.user[1],text[Bob])")
    }

    @Test fun testExternalInsideNamespace() {
        tstCtx.blockchain(333, "deadbeef")

        initExternalChain(inserts = LibBlockTransactionTest.BLOCK_INSERTS_333)
        def("@external('foo') @mount('') namespace bar { @log entity user { name; } }")
        tst.chainDependency("foo", "deadbeef", 1000)
        chk("bar.user @ {} ( _=user, _=.name )", "([foo]:bar.user[1],text[Bob])")
        chk("user @ {} ( _=user, _=.name )", "ct_err:[unknown_name:user][unknown_name:user][expr_attr_unknown:name]")
    }

    @Test fun testNamespaceInsideExternal() {
        tstCtx.blockchain(333, "deadbeef")

        initExternalChain(333, "abc.foo.bar.user", "namespace abc { namespace foo { namespace bar { @log entity user { name; } } } }",
                inserts = LibBlockTransactionTest.BLOCK_INSERTS_333)
        def("namespace abc { @external('ext') namespace foo { namespace bar { @log entity user { name; } } } }")
        tst.chainDependency("ext", "deadbeef", 1000)
        chk("abc.foo.bar.user @ {} ( _=user, _=.name )", "([ext]:abc.foo.bar.user[1],text[Bob])")
        chk("foo.bar.user @ {} ( _=user, _=.name )", "ct_err:[unknown_name:foo][unknown_name:user][expr_attr_unknown:name]")
    }

    @Test fun testUnallowedDefs() {
        tstCtx.blockchain(333, "deadbeef")
        tstCtx.blockchain(444, "cafebabe")

        initExternalChain(inserts = LibBlockTransactionTest.BLOCK_INSERTS_333)
        tst.chainDependency("foo", "deadbeef", 1000)
        tst.chainDependency("bar", "cafebabe", 1000)

        chkCompile("@external('foo') namespace { @external('bar') namespace {} }", "OK")
        chkCompile("@external('foo') namespace { @external('bar') @log entity user { name; } }", "OK")
        chkCompile("@external('foo') namespace { object state { mutable x: integer = 123; } }", "ct_err:def_external:namespace:OBJECT")
        chkCompile("@external('foo') namespace { struct r { x: integer; } }", "ct_err:def_external:namespace:STRUCT")
        chkCompile("@external('foo') namespace { function f(){} }", "ct_err:def_external:namespace:FUNCTION")
        chkCompile("@external('foo') namespace { operation o(){} }", "ct_err:def_external:namespace:OPERATION")
        chkCompile("@external('foo') namespace { query q() = 123; }", "ct_err:def_external:namespace:QUERY")

        chkCompile("@external('foo') object state { mutable x: integer = 123; }", "ct_err:modifier:invalid:ann:external")
        chkCompile("@external('foo') struct r { x: integer; }", "ct_err:modifier:invalid:ann:external")
        chkCompile("@external('foo') enum e { A, B, C }", "ct_err:modifier:invalid:ann:external")
        chkCompile("@external('foo') function f(){}", "ct_err:modifier:invalid:ann:external")
        chkCompile("@external('foo') operation o(){}", "ct_err:modifier:invalid:ann:external")
        chkCompile("@external('foo') query q() = 123;", "ct_err:modifier:invalid:ann:external")

        chkCompile("@external('foo') namespace { enum e { A, B, C } }", "OK")
    }

    @Test fun testNoLog() {
        tstCtx.blockchain(333, "deadbeef")

        initExternalChain(inserts = LibBlockTransactionTest.BLOCK_INSERTS_333)
        tst.chainDependency("foo", "deadbeef", 1000)

        chkCompile("@external('foo') namespace { entity user { name; } }", "ct_err:def_entity_external_nolog:user")
        chkCompile("@external('foo') entity user { name; }", "ct_err:def_entity_external_nolog:user")
    }

    @Test fun testDuplicateChainRID() {
        tstCtx.blockchain(333, "deadbeef")

        initExternalChain(inserts = LibBlockTransactionTest.BLOCK_INSERTS_333)
        tst.chainDependency("foo", "deadbeef", 1000)
        tst.chainDependency("bar", "deadbeef", 2000)

        val exp = RellTestUtils.strToRidHex("deadbeef")
        chk("123", "rt_err:external_chain_dup_rid:bar:$exp")
    }

    @Test fun testUnknownChain() {
        tstCtx.blockchain(333, "deadbeef")

        initExternalChain(inserts = LibBlockTransactionTest.BLOCK_INSERTS_333)
        tst.chainDependency("foo", "deadbeef", 1000)
        chkFull("@external('bar') namespace { @log entity user { name; } } query q() = 123;", "rt_err:external_chain_unknown:bar")
    }

    @Test fun testReferenceInternalToExternal() {
        tstCtx.blockchain(333, "deadbeef")

        initExternalChain(inserts = LibBlockTransactionTest.BLOCK_INSERTS_333)
        def("@external('foo') namespace { @log entity user { name; } }")
        def("entity local { user; }")
        insert("c0.local", "user", "1,1")
        tst.chainDependency("foo", "deadbeef", 1000)
        chk("local @ {} ( _=local, _=.user, _=.user.name )", "(local[1],[foo]:user[1],text[Bob])")
    }

    @Test fun testReferenceExternalToInternal() {
        tstCtx.blockchain(333, "deadbeef")
        tstCtx.blockchain(555, "cafebabe")

        // Init chain "bar" (create meta info)
        run {
            val t = RellCodeTester(tstCtx)
            t.def("@log entity company { name; }")
            t.chainId = 555
            t.insert(LibBlockTransactionTest.BLOCK_INSERTS_555)
            t.init()
        }

        // Init chain "foo" (create meta info)
        run {
            val t = RellCodeTester(tstCtx)
            t.def("@external('bar') namespace { @log entity company { name; } }")
            t.def("@log entity user { name; company; }")
            t.chainId = 333
            t.dropTables = false
            t.insert(LibBlockTransactionTest.BLOCK_INSERTS_333)
            t.chainDependency("bar", "cafebabe", 1000)
            t.init()
        }

        def("@log entity company { name; }")
        def("@external('foo') namespace { @log entity user { name; company; } }")
        tst.chainId = 555
        tst.createTables = false
        tst.dropTables = false
        tst.chainDependency("foo", "deadbeef", 1000)
        insert("c555.company", "name,transaction", "33,'Google',2")
        insert("c333.user", "name,transaction,company", "17,'Bob',444,33")

        chk("user @ {} ( _=user, _=.name, _=.company, _=.company.name )", "([foo]:user[17],text[Bob],company[33],text[Google])")
    }

    @Test fun testAccessTransaction() {
        tstCtx.blockchain(333, "deadbeef")

        initExternalChain(inserts = LibBlockTransactionTest.BLOCK_INSERTS_333)
        def("@external('foo') namespace { @log entity user { name; } }")
        tst.chainDependency("foo", "deadbeef", 1000)

        chk("user@{} ( .name )", "text[Bob]")
        chk("user@{} ( .transaction )", "[foo]:transaction[444]")
        chk("user@{} ( .transaction.tx_rid )", "byte_array[fade]")
        chk("user@{} ( .transaction.tx_hash )", "byte_array[1234]")
        chk("user@{} ( .transaction.tx_data )", "byte_array[edaf]")
        chk("user@{} ( .transaction.block )", "[foo]:block[111]")
        chk("user@{} ( .transaction.block.block_height )", "int[222]")
        chk("user@{} ( .transaction.block.block_rid )", "byte_array[deadbeef]")
        chk("user@{} ( .transaction.block.timestamp )", "int[1500000000000]")
    }

    @Test fun testAccessReference() {
        tstCtx.blockchain(333, "deadbeef")

        run {
            val t = RellCodeTester(tstCtx)
            t.def("@log entity company { name; }")
            t.def("@log entity user { name; company; }")
            t.chainId = 333
            t.insert(LibBlockTransactionTest.BLOCK_INSERTS_333)
            t.insert("c333.company", "name,transaction", "1,'Google',444")
            t.insert("c333.user", "name,company,transaction", "1,'Bob',1,444")
            t.chk("user @ {} ( _=user, _=.name, _=.company, _=.company.name, _=.transaction )",
                    "(user[1],text[Bob],company[1],text[Google],transaction[444])")
        }
        tst.dropTables = false

        def("@external('foo') namespace { @log entity company { name; } @log entity user { name; company; } }")
        tst.chainDependency("foo", "deadbeef", 1000)
        chk("user @ {} ( _=user, _=.name, _=.company, _=.company.name )", "([foo]:user[1],text[Bob],[foo]:company[1],text[Google])")
        chk("company @ {} ( _=company, _=.name )", "([foo]:company[1],text[Google])")
    }

    @Test fun testHeightCheck() {
        tstCtx.blockchain(333, "deadbeef")

        chkHeight(10, "user @? {}", "null")
        chkHeight(221, "user @? {}", "null")
        chkHeight(222, "user @? {}", "[foo]:user[1]")
        chkHeight(223, "user @? {}", "[foo]:user[1]")
        chkHeight(1000, "user @? {}", "[foo]:user[1]")

        chkHeight(10, "local @? {} ( .user )", "[foo]:user[1]")
        chkHeight(222, "local @? {} ( .user )", "[foo]:user[1]")
        chkHeight(223, "local @? {} ( .user )", "[foo]:user[1]")
        chkHeight(1000, "local @? {} ( .user )", "[foo]:user[1]")
    }

    private fun chkHeight(height: Long, code: String, expected: String) {
        initExternalChain(inserts = LibBlockTransactionTest.BLOCK_INSERTS_333)
        run {
            val t = RellCodeTester(tstCtx)
            t.dropTables = false
            t.def("@external('foo') namespace { @log entity user { name; } }")
            t.def("entity local { user; }")
            t.insert("c0.local", "user", "1,1")
            t.chainDependency("foo", "deadbeef", height)
            t.chk(code, expected)
        }
    }

    @Test fun testMisc() {
        tstCtx.blockchain(333, "deadbeef")

        initExternalChain(inserts = LibBlockTransactionTest.BLOCK_INSERTS_333)
        tst.chainDependency("foo", "deadbeef", 1000)
        chkFull("@external('foo') namespace {} query q() = 123;", "int[123]") // Empty external block
        chkCompile("@external('') namespace {}", "ct_err:ann:external:invalid:")

        chkCompile("@external(0) namespace {}", "ct_err:ann:external:arg_type:integer")
        chkCompile("@external(foo) namespace {}", "ct_err:ann:arg:name_not_value:foo")
        chkCompile("@external(foo.bar) namespace {}", "ct_err:ann:arg:name_not_value:foo.bar")
    }

    @Test fun testTxImplicitTransactionBlockTypes() {
        tstCtx.blockchain(333, "deadbeef")
        tstCtx.blockchain(555, "beefdead")

        initExternalChain(chainId = 333, inserts = LibBlockTransactionTest.BLOCK_INSERTS_333)
        initExternalChain(chainId = 555, inserts = LibBlockTransactionTest.BLOCK_INSERTS_555, dropTables = false, txId = 2)
        def("@mount('') namespace foo { @external('foo') @log entity user { name; } }")
        def("@mount('') namespace bar { @external('bar') @log entity user { name; } }")
        def("@log entity local_user { name; }")
        tst.chainDependency("foo", "deadbeef", 1000)
        tst.chainDependency("bar", "beefdead", 1000)
        insert(LibBlockTransactionTest.BLOCK_INSERTS_0)
        insert("c0.local_user", "name,transaction", "1,'Bob',720")

        chk("_type_of((foo.user @ {}).transaction)", "text[[foo]:transaction]")
        chk("_type_of((foo.user @ {}).transaction.block)", "text[[foo]:block]")
        chk("_type_of((bar.user @ {}).transaction)", "text[[bar]:transaction]")
        chk("_type_of((bar.user @ {}).transaction.block)", "text[[bar]:block]")
        chk("_type_of((local_user @ {}).transaction)", "text[transaction]")
        chk("_type_of((local_user @ {}).transaction.block)", "text[block]")

        chkEx("{ val t: transaction = (foo.user @ {}).transaction; return 0; }",
                "ct_err:stmt_var_type:t:[transaction]:[[foo]:transaction]")
        chkEx("{ val b: block = (foo.user @ {}).transaction.block; return 0; }",
                "ct_err:stmt_var_type:b:[block]:[[foo]:block]")

        val txFn = "function f(t: transaction, u: foo.user)"
        chkCompile("$txFn = (t == u.transaction);", "ct_err:binop_operand_type:==:[transaction]:[[foo]:transaction]")
        chkCompile("$txFn = (t != u.transaction);", "ct_err:binop_operand_type:!=:[transaction]:[[foo]:transaction]")
        chkCompile("$txFn = (t < u.transaction);", "ct_err:binop_operand_type:<:[transaction]:[[foo]:transaction]")
        chkCompile("$txFn = (t > u.transaction);", "ct_err:binop_operand_type:>:[transaction]:[[foo]:transaction]")

        val blkFn = "function f(b: block, u: foo.user)"
        chkCompile("$blkFn = (b == u.transaction.block);", "ct_err:binop_operand_type:==:[block]:[[foo]:block]")
        chkCompile("$blkFn = (b != u.transaction.block);", "ct_err:binop_operand_type:!=:[block]:[[foo]:block]")
        chkCompile("$blkFn = (b < u.transaction.block);", "ct_err:binop_operand_type:<:[block]:[[foo]:block]")
        chkCompile("$blkFn = (b > u.transaction.block);", "ct_err:binop_operand_type:>:[block]:[[foo]:block]")
    }

    @Test fun testTxExplicitTypeDeclaration() {
        chkCompile("entity transaction;", "ct_err:def_entity_hdr_noexternal:transaction")
        chkCompile("entity block;", "ct_err:def_entity_hdr_noexternal:block")
        chkCompile("entity foo;", "ct_err:[def_entity_hdr_name:foo][def_entity_hdr_noexternal:foo]")
        chkCompile("@external('foo') namespace { entity transaction {} }", "ct_err:def_entity_external_unallowed:transaction")
        chkCompile("@external('foo') namespace { entity block {} }", "ct_err:def_entity_external_unallowed:block")

        chkCompile("namespace abc { @external('foo') namespace { entity transaction; } }", "OK")
        chkCompile("namespace abc { @external('foo') namespace { entity block; } }", "OK")
        chkCompile("namespace abc { @external('foo') namespace { entity transaction; entity block; } }", "OK")

        chkCompile("@external('foo') namespace { entity foo; }", "ct_err:def_entity_hdr_name:foo")
        chkCompile("namespace abc { @external('foo') namespace { entity foo; } }", "ct_err:def_entity_hdr_name:foo")

        chkCompile("namespace abc { @external('foo') namespace { @log entity transaction; } }", "ct_err:def_entity_hdr:modifier:ann:log")
        chkCompile("namespace abc { @external('foo') namespace { @log entity block; } }", "ct_err:def_entity_hdr:modifier:ann:log")
        chkCompile("namespace abc { @external('foo') namespace { @aaa entity block; } }", "ct_err:modifier:invalid:ann:aaa")
        chkCompile("namespace abc { @external('foo') namespace { entity transaction(log); } }", "ct_err:def_entity_hdr_annotations:transaction")
        chkCompile("namespace abc { @external('foo') namespace { entity block(log); } }", "ct_err:def_entity_hdr_annotations:block")
        chkCompile("namespace abc { @external('foo') namespace { entity block(aaa); } }", "ct_err:def_entity_hdr_annotations:block")

        chkCompile("@mount('') namespace abc { @external('foo') namespace { @log entity transaction {} } }",
                "ct_err:def_entity_external_unallowed:transaction")
        chkCompile("@mount('') namespace abc { @external('foo') namespace { @log entity block {} } }",
                "ct_err:def_entity_external_unallowed:block")
        chkCompile("namespace abc { @external('foo') namespace { @log entity transaction {} } }", "OK")
        chkCompile("namespace abc { @external('foo') namespace { @log entity block {} } }", "OK")

        chkCompile("@mount('') namespace abc { @external('foo') @log entity transaction {} }",
                "ct_err:def_entity_external_unallowed:transaction")
        chkCompile("@mount('') namespace abc { @external('foo') @log entity block {} }",
                "ct_err:def_entity_external_unallowed:block")
        chkCompile("namespace abc { @external('foo') @log entity transaction {} }", "OK")
        chkCompile("namespace abc { @external('foo') @log entity block {} }", "OK")

        chkCompile("@external('foo') namespace { namespace xyz { entity transaction; } }", "OK")
        chkCompile("@external('foo') namespace { namespace xyz { entity block; } }", "OK")
        chkCompile("@external('foo') namespace xyz { entity transaction; }", "OK")
        chkCompile("@external('foo') namespace xyz { entity block; }", "OK")
        chkCompile("namespace abc { @external('foo') namespace { entity transaction; } }", "OK")
        chkCompile("namespace abc { @external('foo') namespace { entity block; } }", "OK")

        chkCompile("@external('foo') namespace xyz { entity transaction; }", "OK")
        chkCompile("@external('foo') namespace xyz { entity block; }", "OK")
        chkCompile("namespace abc { @external('foo') entity transaction; }", "OK")
        chkCompile("namespace abc { @external('foo') entity block; }", "OK")
    }

    @Test fun testTxExplicitTypeDeclarationNameConflict() {
        tst.ideDefIdConflictError = false

        chkCompile("namespace abc { @external('foo') namespace { entity transaction; entity transaction; } }", """ct_err:
            [name_conflict:user:transaction:ENTITY:main.rell(1:73)]
            [name_conflict:user:transaction:ENTITY:main.rell(1:53)]
        """)

        chkCompile("namespace abc { @external('foo') namespace { entity block; entity block; } }", """ct_err:
            [name_conflict:user:block:ENTITY:main.rell(1:67)]
            [name_conflict:user:block:ENTITY:main.rell(1:53)]
        """)
    }

    @Test fun testTxExplicitTypeMount() {
        tstCtx.blockchain(333, "deadbeef")
        tst.chainDependency("foo", "deadbeef", 1000)
        initExternalChain(inserts = LibBlockTransactionTest.BLOCK_INSERTS_333)

        chkCompile("@external('foo') namespace ns { @mount('') entity transaction; }", "ct_err:def_entity_hdr:modifier:ann:mount")
        chkCompile("@external('foo') namespace ns { @mount('') entity block; }", "ct_err:def_entity_hdr:modifier:ann:mount")
        chkCompile("@external('foo') namespace ns { @mount('bar') entity transaction; }", "ct_err:def_entity_hdr:modifier:ann:mount")
        chkCompile("@external('foo') namespace ns { @mount('bar') entity block; }", "ct_err:def_entity_hdr:modifier:ann:mount")

        chkCompile("namespace ns { @external('foo') @mount('') entity transaction; }", "ct_err:def_entity_hdr:modifier:ann:mount")
        chkCompile("namespace ns { @external('foo') @mount('') entity block; }", "ct_err:def_entity_hdr:modifier:ann:mount")
        chkCompile("namespace ns { @external('foo') @mount('bar') entity transaction; }", "ct_err:def_entity_hdr:modifier:ann:mount")
        chkCompile("namespace ns { @external('foo') @mount('bar') entity block; }", "ct_err:def_entity_hdr:modifier:ann:mount")

        def("namespace ns1 { @mount('bar') @external('foo') namespace { entity transaction; entity block; } }")
        def("@mount('bar') namespace ns2 { @external('foo') namespace { entity transaction; entity block; } }")

        chk("ns1.transaction @ {}", "[foo]:transaction[444]")
        chk("ns2.transaction @ {}", "[foo]:transaction[444]")
        chk("ns1.block @ {}", "[foo]:block[111]")
        chk("ns2.block @ {}", "[foo]:block[111]")
    }

    @Test fun testTxExplicitTypeCompatibility() {
        tstCtx.blockchain(333, "deadbeef")
        tstCtx.blockchain(555, "beefdead")
        def("@log entity user{ name; }")
        def("namespace foo { @external('foo') namespace { entity transaction; entity block; @log entity user {name;} } }")
        def("namespace bar { @external('bar') namespace { entity transaction; entity block; @log entity user {name;} } }")
        tst.chainDependency("foo", "deadbeef", 1000)
        tst.chainDependency("bar", "beefdead", 1000)

        chkCompile("function f(u: foo.user): transaction = u.transaction;",
            "ct_err:fn_rettype:[transaction]:[[foo]:transaction]")
        chkCompile("function f(u: user): foo.transaction = u.transaction;",
            "ct_err:fn_rettype:[[foo]:transaction]:[transaction]")
        chkCompile("function f(u: foo.user): block = u.transaction.block;", "ct_err:fn_rettype:[block]:[[foo]:block]")
        chkCompile("function f(u: user): foo.block = u.transaction.block;", "ct_err:fn_rettype:[[foo]:block]:[block]")

        chkCompile("function f(u: bar.user): foo.transaction = u.transaction;",
            "ct_err:fn_rettype:[[foo]:transaction]:[[bar]:transaction]")
        chkCompile("function f(u: foo.user): bar.transaction = u.transaction;",
            "ct_err:fn_rettype:[[bar]:transaction]:[[foo]:transaction]")
        chkCompile("function f(u: bar.user): foo.block = u.transaction.block;",
            "ct_err:fn_rettype:[[foo]:block]:[[bar]:block]")
        chkCompile("function f(u: foo.user): bar.block = u.transaction.block;",
            "ct_err:fn_rettype:[[bar]:block]:[[foo]:block]")
    }

    @Test fun testTxExplicitTypeCompatibility2() {
        tstCtx.blockchain(333, "deadbeef")
        tstCtx.blockchain(555, "beefdead")
        def("@log entity user { name; }")
        def("namespace foo { @external('foo') namespace { entity transaction; entity block; @log entity user {name;} } }")
        def("namespace bar { @external('bar') namespace { entity transaction; entity block; @log entity user {name;} } }")
        tst.chainDependency("foo", "deadbeef", 1000)
        tst.chainDependency("bar", "beefdead", 1000)

        fun chkOpErr(type1: String, typeStr1: String, type2: String, typeStr2: String, op: String) {
            chkCompile("function f(x: $type1, y: $type2): boolean = (x $op y);",
                    "ct_err:binop_operand_type:$op:[$typeStr1]:[$typeStr2]")
        }

        fun chkTypes(type1: String, typeStr1: String, type2: String, typeStr2: String) {
            chkOpErr(type1, typeStr1, type2, typeStr2, "==")
            chkOpErr(type1, typeStr1, type2, typeStr2, "!=")
            chkOpErr(type1, typeStr1, type2, typeStr2, "<")
            chkOpErr(type1, typeStr1, type2, typeStr2, ">")
        }

        chkTypes("transaction", "transaction", "foo.transaction", "[foo]:transaction")
        chkTypes("foo.transaction", "[foo]:transaction", "transaction", "transaction")
        chkTypes("foo.transaction", "[foo]:transaction", "bar.transaction", "[bar]:transaction")
        chkTypes("block", "block", "foo.block", "[foo]:block")
        chkTypes("foo.block", "[foo]:block", "block", "block")
        chkTypes("foo.block", "[foo]:block", "bar.block", "[bar]:block")
    }

    @Test fun testTxExplicitTypeCompatibility3() {
        tstCtx.blockchain(333, "deadbeef")
        tst.chainDependency("foo", "deadbeef", 1000)
        initExternalChain(inserts = LibBlockTransactionTest.BLOCK_INSERTS_333)

        def("namespace ns1 { @mount('bar') @external('foo') namespace { entity transaction; entity block; } }")
        def("@mount('bar') namespace ns2 { @external('foo') namespace { entity transaction; entity block; } }")

        chkEx("{ val tx: ns1.transaction = ns2.transaction @ {}; return tx; }", "[foo]:transaction[444]")
        chkEx("{ val tx: ns2.transaction = ns1.transaction @ {}; return tx; }", "[foo]:transaction[444]")
        chkEx("{ val b: ns1.block = ns2.block @ {}; return b; }", "[foo]:block[111]")
        chkEx("{ val b: ns2.block = ns1.block @ {}; return b; }", "[foo]:block[111]")
    }

    @Test fun testTxExplicitTypeLocalVar() {
        tstCtx.blockchain(333, "deadbeef")

        initExternalChain(inserts = LibBlockTransactionTest.BLOCK_INSERTS_333)
        def("@external('foo') @mount('') namespace foo { entity transaction; entity block; @log entity user { name; } }")
        tst.chainDependency("foo", "deadbeef", 1000)

        val tx = "val t: foo.transaction = foo.user @ {} (.transaction);"
        val block = "$tx; val b: foo.block = t.block;"
        chkEx("{ $tx; return t; }", "[foo]:transaction[444]")
        chkEx("{ $tx; return t.tx_rid; }", "byte_array[fade]")
        chkEx("{ $tx; return t.tx_hash; }", "byte_array[1234]")
        chkEx("{ $tx; return t.tx_data; }", "byte_array[edaf]")
        chkEx("{ $block; return b; }", "[foo]:block[111]")
        chkEx("{ $block; return b.block_height; }", "int[222]")
        chkEx("{ $block; return b.block_rid; }", "byte_array[deadbeef]")
        chkEx("{ $block; return b.timestamp; }", "int[1500000000000]")
    }

    @Test fun testTxExplicitTypeAttribute() {
        tstCtx.blockchain(333, "deadbeef")

        initExternalChain(inserts = LibBlockTransactionTest.BLOCK_INSERTS_333)
        def("@external('foo') @mount('') namespace foo { entity transaction; entity block; @log entity user { name; } }")
        def("entity local { tx: foo.transaction; blk: foo.block; }")
        tst.chainDependency("foo", "deadbeef", 1000)

        chkOp("val u = foo.user @ {}; create local(u.transaction, u.transaction.block);")
        chkData("local(1,444,111)")

        chk("(local @ {}).tx", "[foo]:transaction[444]")
        chk("(local @ {}).blk", "[foo]:block[111]")
    }

    @Test fun testTxExplicitTypeSelect() {
        tstCtx.blockchain(333, "deadbeef")

        chkTxExplicitTypeSelect(10, "[]", "[]")
        chkTxExplicitTypeSelect(221, "[]", "[]")
        chkTxExplicitTypeSelect(222, "[[foo]:transaction[444]]", "[[foo]:block[111]]")
        chkTxExplicitTypeSelect(1000, "[[foo]:transaction[444]]", "[[foo]:block[111]]")
        chkTxExplicitTypeSelect(1000000, "[[foo]:transaction[444]]", "[[foo]:block[111]]")
    }

    private fun chkTxExplicitTypeSelect(height: Long, expectedTx: String, expectedBlock: String) {
        initExternalChain(inserts = LibBlockTransactionTest.BLOCK_INSERTS_333)

        run {
            val t = RellCodeTester(tstCtx)
            t.dropTables = false
            t.def("namespace foo { @external('foo') namespace { entity block; entity transaction; } }")
            t.strictToString = false
            t.chainDependency("foo", "deadbeef", height)
            t.chk("transaction @* {}", "[]")
            t.chk("block @* {}", "[]")
            t.chk("foo.transaction @* {}", expectedTx)
            t.chk("foo.block @* {}", expectedBlock)
        }
    }

    @Test fun testGtvExternalEntity() {
        tstCtx.blockchain(123, "deadbeef")

        run {
            val t = RellCodeTester(tstCtx)
            t.def("@log entity user { name; }")
            t.chainId = 123
            t.insert(BLOCK_INSERTS_123)
            t.insert("c123.user", "name,transaction", "1,'Alice',2001")
            t.insert("c123.user", "name,transaction", "2,'Bob',2002")
            t.insert("c123.user", "name,transaction", "3,'Calvin',2003")
            t.insert("c123.user", "name,transaction", "4,'Donald',2004")
            t.insert("c123.user", "name,transaction", "5,'Evan',2005")
            t.chk("(user @* {}).size()", "int[5]")
        }

        tst.dropTables = false
        def("@external('foo') namespace { @log entity user { name; } }")
        def("struct rec { u: user; }")
        tst.chainDependency("foo", "deadbeef", 3)

        fun code(id: Long) = """rec.from_gtv_pretty(gtv.from_json('{"u":$id}'))"""
        chk(code(1), "rec[u=[foo]:user[1]]")
        chk(code(2), "rec[u=[foo]:user[2]]")
        chk(code(3), "rec[u=[foo]:user[3]]")
        chk(code(4), "gtv_err:obj_missing:[[foo]:user]:4")
        chk(code(5), "gtv_err:obj_missing:[[foo]:user]:5")
        chk(code(321), "gtv_err:obj_missing:[[foo]:user]:321")
    }

    @Test fun testGtvExternalTransaction() {
        def("namespace foo { @external('foo') namespace { entity block; entity transaction; } }")
        def("struct r_tx { t: transaction; }")
        def("struct r_block { b: block; }")
        def("struct r_foo_tx { t: foo.transaction; }")
        def("struct r_foo_block { b: foo.block; }")

        tstCtx.blockchain(333, "deadbeef")
        tst.chainDependency("foo", "deadbeef", 1000)
        tst.gtv = true
        initExternalChain(inserts = LibBlockTransactionTest.BLOCK_INSERTS_333)

        fun chkType(type: String) {
            chkCompile("function nop(x: $type?): $type? = x; query q(): $type { var t: $type? = nop(null); return t!!; }", "OK")
            chkCompile("query q(x: $type) = 0;", "OK")
            chkCompile("operation o(x: $type) {}", "OK")
        }

        fun chkStructType(type: String) {
            chkType(type)

            chkCompile("function f(x: $type) { x.to_bytes(); }", "OK")
            chkCompile("function f(x: $type) { x.to_gtv(); }", "OK")
            chkCompile("function f(x: $type) { x.to_gtv_pretty(); }", "OK")

            chkCompile("function f() { $type.from_bytes(x''); }", "OK")
            chkCompile("function f() { $type.from_gtv(gtv.from_bytes(x'')); }", "OK")
            chkCompile("function f() { $type.from_gtv_pretty(gtv.from_bytes(x'')); }", "OK")
        }

        chkType("transaction")
        chkType("block")
        chkType("foo.transaction")
        chkType("foo.block")

        chkStructType("r_tx")
        chkStructType("r_block")
        chkStructType("r_foo_tx")
        chkStructType("r_foo_block")
    }

    @Test fun testCreateExternalEntity() {
        tstCtx.blockchain(333, "deadbeef")

        initExternalChain(inserts = LibBlockTransactionTest.BLOCK_INSERTS_333)
        def("@external('foo') namespace { @log entity user { name; } }")
        tst.chainDependency("foo", "deadbeef", 1000)
        chkOp("create user (name = 'Alice');", "ct_err:expr_create_cant:user")
    }

    @Test fun testDeleteExternalEntity() {
        tstCtx.blockchain(333, "deadbeef")

        initExternalChain(inserts = LibBlockTransactionTest.BLOCK_INSERTS_333)
        def("@external('foo') namespace { @log entity user { name; } }")
        tst.chainDependency("foo", "deadbeef", 1000)
        chkOp("delete user @* {};", "ct_err:stmt_delete_cant:user")
    }

    @Test fun testMetaEntityNotFound() {
        tstCtx.blockchain(333, "deadbeef")

        chkMetaEntity(
                "@log entity company {}",
                "@external('foo') namespace { @log entity user {} }",
                "rt_err:external_meta_no_entity:foo:user"
        )
    }

    @Test fun testMetaEntityObject() {
        tstCtx.blockchain(333, "deadbeef")

        chkMetaEntity(
                "object user { name: text = 'Bob'; }",
                "@external('foo') namespace { @log entity user {} }",
                "rt_err:external_meta_no_entity:foo:user"
        )
    }

    @Test fun testMetaEntityNoLog() {
        tstCtx.blockchain(333, "deadbeef")

        chkMetaEntity(
                "entity user {}",
                "@external('foo') namespace { @log entity user {} }",
                "rt_err:external_meta_nolog:foo:user"
        )
    }

    @Test fun testMetaAttributeNotFound() {
        tstCtx.blockchain(333, "deadbeef")

        chkMetaEntity(
                "@log entity user { name: text; }",
                "@external('foo') namespace { @log entity user { fullName: text; } }",
                "rt_err:external_meta_noattrs:foo:[[foo]:user]:fullName"
        )
    }

    @Test fun testMetaAttributeWrongType() {
        tstCtx.blockchain(333, "deadbeef")

        chkMetaEntity(
                "@log entity user { attr: integer; }",
                "@external('foo') namespace { @log entity user { attr: text; } }",
                "rt_err:external_meta_attrtype:foo:[[foo]:user]:attr:[sys:text]:[sys:integer]"
        )

        chkMetaEntity(
                "@log entity user { attr: text; }",
                "@external('foo') namespace { @log entity user { attr: byte_array; } }",
                "rt_err:external_meta_attrtype:foo:[[foo]:user]:attr:[sys:byte_array]:[sys:text]"
        )

        chkMetaEntity(
                "@log entity user { name: text; }",
                "@external('foo') namespace { @log entity user { name; } }",
                "OK"
        )

        chkMetaEntity(
                "@log entity user { name; }",
                "@external('foo') namespace { @log entity user { name: text; } }",
                "OK"
        )
    }

    @Test fun testMetaAttributeWrongChain() {
        tstCtx.blockchain(333, "deadbeef")

        chkMetaEntity(
                "@log entity company {} @log entity user { company; }",
                "@log entity company {} @external('foo') namespace { @log entity user { company; } }",
                "rt_err:external_meta_attrtype:foo:[[foo]:user]:company:[class:0:company]:[class:333:company]"
        )
    }

    @Test fun testMetaAttributeSubSet() {
        tstCtx.blockchain(333, "deadbeef")

        chkMetaEntity(
                "@log entity user { x: integer; y: text; z: boolean; }",
                "@external('foo') namespace { @log entity user { x: integer; z: boolean; } }",
                "OK"
        )

        chkMetaEntity(
                "@log entity user { x: integer; y: text; z: boolean; }",
                "@external('foo') namespace { @log entity user { y: text; } }",
                "OK"
        )
    }

    @Test fun testMetaEntityNamespace() {
        tstCtx.blockchain(333, "deadbeef")

        chkMetaEntity(
                "namespace x { @log entity user {} }",
                "@external('foo') namespace { @log entity user {} }",
                "rt_err:external_meta_no_entity:foo:user"
        )

        chkMetaEntity(
                "namespace x { @log entity user {} }",
                "@external('foo') namespace { namespace x { @log entity user {} } }",
                "OK"
        )

        chkMetaEntity(
                "namespace x { @log entity user {} }",
                "namespace y { @external('foo') namespace x { @log entity user {} } }",
                "rt_err:external_meta_no_entity:foo:y.x.user"
        )

        chkMetaEntity(
                "namespace x { @log entity user {} }",
                "@mount('') namespace y { @external('foo') namespace x { @log entity user {} } }",
                "OK"
        )
    }

    @Test fun testMetaAttributeNamespace() {
        tstCtx.blockchain(333, "deadbeef")

        val extDefs = "namespace x { @log entity company {} } namespace y { @log entity user { c: x.company; } }"

        chkMetaEntity(
                extDefs,
                "@external('foo') namespace z { namespace x { @log entity company {} } namespace y { @log entity user { c: z.x.company; } } }",
                "rt_err:external_meta_no_entity:foo:z.x.company,z.y.user"
        )

        chkMetaEntity(
                extDefs,
                "@mount('') namespace z { @external('foo') namespace x { @log entity company {} @log entity user { c: company; } } }",
                "rt_err:external_meta_no_entity:foo:x.user"
        )

        chkMetaEntity(
                extDefs,
                "@mount('') namespace z { @external('foo') namespace { namespace x { @log entity company {} } @log entity user { c: x.company; } } }",
                "rt_err:external_meta_no_entity:foo:user"
        )

        chkMetaEntity(
                extDefs,
                "@mount('') namespace z { @external('foo') namespace { @log entity company {} namespace y { @log entity user { c: company; } } } }",
                "rt_err:external_meta_no_entity:foo:company"
        )
    }

    @Test fun testMetaData() {
        tstCtx.blockchain(333, "deadbeef")

        run {
            val t = RellCodeTester(tstCtx)
            t.def("@log entity ext_a { value: integer; }")
            t.def("namespace y { @log entity ext_b {} }")
            t.chainId = 333
            t.insert(LibBlockTransactionTest.BLOCK_INSERTS_333)
            t.init()
        }

        def("@log entity helper { id: integer; data: byte_array; }")
        def("@external('foo') @mount('') namespace x { @log entity ext_a { value: integer; } namespace y { @log entity ext_b {} } }")
        def("entity my_entity { b: boolean; i: integer; t: text; n: name; h: helper; ea: x.ext_a; eb: x.y.ext_b; }")
        tst.dropTables = false
        tst.chainDependency("foo", "deadbeef", 1000)
        tst.init()

        tst.chkDataSql("""SELECT C.name, C.log FROM "c0.sys.classes" C ORDER BY C.name;""",
                "helper,true",
                "my_entity,false"
        )

        val sql = """SELECT C.name, A.name, A.type
            | FROM "c0.sys.attributes" A JOIN "c0.sys.classes" C ON A.class_id = C.id
            | ORDER BY C.name, A.name;""".trimMargin()

        tst.chkDataSql(sql,
                "helper,data,sys:byte_array",
                "helper,id,sys:integer",
                "helper,transaction,class:0:transaction",
                "my_entity,b,sys:boolean",
                "my_entity,ea,class:333:ext_a",
                "my_entity,eb,class:333:y.ext_b",
                "my_entity,h,class:0:helper",
                "my_entity,i,sys:integer",
                "my_entity,n,sys:text",
                "my_entity,t,sys:text"
        )
    }

    private fun chkMetaEntity(externalDefs: String, localDefs: String, expected: String) {
        run {
            val t = RellCodeTester(tstCtx)
            t.def(externalDefs)
            t.chainId = 333
            t.insert(LibBlockTransactionTest.BLOCK_INSERTS_333)
            t.chk("123", "int[123]") // Initializes database
        }

        run {
            val t = RellCodeTester(tstCtx)
            t.def(localDefs)
            t.dropTables = false
            t.strictToString = false
            t.chainDependency("foo", "deadbeef", 1000)
            t.chk("'OK'", expected)
        }
    }

    @Test fun testMountEntity() {
        tstCtx.blockchain(333, "deadbeef")

        initExternalChain(333, "foo.bar.user", "namespace foo { namespace bar { @log entity user { name; } } }",
                inserts = LibBlockTransactionTest.BLOCK_INSERTS_333)

        tst.chainDependency("foo", "deadbeef", 1000)

        chkFull("@external('foo') @log entity user { name; } query q() = user @ {} ( _=user, _=.name );",
                "rt_err:external_meta_no_entity:foo:user")

        chkFull("@external('foo') namespace foo { namespace bar { @log entity user { name; } } } " +
                "query q() = foo.bar.user @ {} ( _=user, _=.name );",
                "([foo]:foo.bar.user[1],text[Bob])")

        chkFull("@external('foo') @mount('foo.bar.user') @log entity admin { name; } " +
                "query q() = admin @ {} ( _=admin, _=.name );",
                "([foo]:admin[1],text[Bob])")

        chkFull("@external('foo') namespace { @mount('foo.bar.user') @log entity admin { name; } }" +
                "query q() = admin @ {} ( _=admin, _=.name );",
                "([foo]:admin[1],text[Bob])")

        chkFull("@mount('foo.bar.') @external('foo') @log entity user { name; } " +
                "query q() = user @ {} ( _=user, _=.name );",
                "([foo]:user[1],text[Bob])")

        chkFull("@mount('foo.bar') @external('foo') namespace { @log entity user { name; } }" +
                "query q() = user @ {} ( _=user, _=.name );",
                "([foo]:user[1],text[Bob])")

        chkFull("@external('foo') @mount('foo.bar') namespace ns { @log entity user { name; } } " +
                "query q() = ns.user @ {} ( _=user, _=.name );",
                "([foo]:ns.user[1],text[Bob])")

        chkFull("@mount('junk') @external('foo') namespace { @mount('foo.bar') namespace ns { @log entity user { name; } } } " +
                "query q() = ns.user @ {} ( _=user, _=.name );",
                "([foo]:ns.user[1],text[Bob])")

        chkFull("@mount('junk') @external('foo') namespace { @mount('trash') namespace ns { @mount('foo.bar.user') @log entity user { name; } } } " +
                "query q() = ns.user @ {} ( _=user, _=.name );",
                "([foo]:ns.user[1],text[Bob])")
    }

    @Test fun testDuplicateExternalBlock() {
        chkCompile("namespace ns1 { @external('foo') namespace { entity transaction; } } namespace ns2 { @external('foo') namespace { entity transaction; } }", "OK")
        chkCompile("namespace ns1 { @external('foo') namespace { entity block; } } namespace ns2 { @external('foo') namespace { entity block; } }", "OK")
        chkCompile("namespace ns1 { @external('foo') namespace { @log entity user {} } } namespace ns2 { @external('foo') namespace { @log entity company {} } }", "OK")

        val code = """
            @mount('') namespace ns1 { @external('foo') @log entity user {} }
            @mount('') namespace ns2 { @external('foo') @log entity user {} }
        """
        chkCompile(code, """ct_err:
            [mnt_conflict:user:[[foo]:ns1.user]:user:ENTITY:[[foo]:ns2.user]:main.rell(3:69)]
            [mnt_conflict:user:[[foo]:ns2.user]:user:ENTITY:[[foo]:ns1.user]:main.rell(2:69)]
        """)
    }

    @Test fun testEnumInExternalBlock() {
        tstCtx.blockchain(333, "deadbeef")
        initExternalChain(333, "foo.bar.user", "namespace foo { namespace bar { @log entity user { name; } } }",
            inserts = LibBlockTransactionTest.BLOCK_INSERTS_333)
        tst.chainDependency("foo", "deadbeef", 1000)
        chkFull("@external('foo') namespace { enum color { red, green, blue } } query q() = color.red;", "color[red]")
    }

    @Test fun testEnumWithExternalAnnotation() {
        chkFull("@external('foo') enum color { red, green, blue }", "ct_err:modifier:invalid:ann:external")
    }

    private fun initExternalChain(
        chainId: Long = 333,
        entityName: String = "user",
        def: String = "@log entity user { name; }",
        dropTables: Boolean = true,
        inserts: List<String> = listOf(),
        txId: Int = 444
    ) {
        run {
            val t = RellCodeTester(tst.tstCtx)
            t.def(def)
            t.chainId = chainId
            t.dropTables = dropTables
            t.insert(inserts)
            t.insert("c$chainId.$entityName", "name,transaction", "1,'Bob',$txId")
            t.chk("$entityName @ {} ( _=user, _=.name )", "($entityName[1],text[Bob])")
        }
        tst.dropTables = false
    }

    companion object {
        val BLOCK_INSERTS_123 = RellTestContext.BlockBuilder(123)
            .block(1001, 1, "DEAD01", 1510000000000)
            .block(1002, 2, "DEAD02", 1520000000000)
            .block(1003, 3, "DEAD03", 1530000000000)
            .block(1004, 4, "DEAD04", 1540000000000)
            .block(1005, 5, "DEAD05", 1550000000000)
            .tx(2001, 1001, "BEEF01", "2001", "1234")
            .tx(2002, 1002, "BEEF02", "2002", "1234")
            .tx(2003, 1003, "BEEF03", "2003", "1234")
            .tx(2004, 1004, "BEEF04", "2004", "1234")
            .tx(2005, 1005, "BEEF05", "2005", "1234")
            .list()

        fun initExternalChain(
            tst: RellCodeTester,
            chainName: String,
            def: String,
            dropTables: Boolean = true,
        ) {
            tst.tstCtx.useSql = true
            tst.tstCtx.blockchain(333, "deadbeef")

            run {
                val t = RellCodeTester(tst.tstCtx)
                t.def(def)
                t.chainId = 333
                t.dropTables = dropTables
                t.init()
            }

            tst.chainDependency(chainName, "deadbeef", 0)
            tst.dropTables = false
        }
    }
}
