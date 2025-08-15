/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.module

import net.postchain.rell.base.lang.def.ExternalTest
import net.postchain.rell.base.lib.LibBlockTransactionTest
import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.testutils.RellCodeTester
import net.postchain.rell.base.testutils.RellTestContext
import net.postchain.rell.base.utils.formatEx
import kotlin.test.Test

class ExternalModuleTest: BaseRellTest(useSql = true) {
    @Test fun testImportMainToExternalAsRegular() {
        initBasic()
        file("ext.rell", "@external module; @log entity user { name; }")
        insert(LibBlockTransactionTest.BLOCK_INSERTS_0)
        insert("c0.user", "transaction,name", "100,720,'Bob'")

        def("import ext;")
        chk("ext.user @* {} ( user, .name )", "[(ext:user[100],name=Bob)]")
        chkDataRaw("c0.user(100,Bob,720)")
    }

    @Test fun testImportMainToExternalAsRegularDir() {
        initBasic()
        file("ext/module.rell", "@external module;")
        file("ext/part.rell", "@log entity user { name; }")
        insert(LibBlockTransactionTest.BLOCK_INSERTS_0)
        insert("c0.user", "transaction,name", "100,720,'Bob'")

        def("import ext;")
        chk("ext.user @* {} ( user, .name )", "[(ext:user[100],name=Bob)]")
    }

    @Test fun testImportMainToExternalAsExternal() {
        file("ext.rell", "@external module; @log entity user { name; }")
        initExternalModule(1, "ext", "user", "Bob")
        initChainDependency(1, "other")

        def("@external('other') import ext;")
        chk("ext.user @* {} ( user, .name )", "[(ext[other]:user[1],name=Bob)]")
        chkDataRaw("c1101.user(1,Bob,330100)")
    }

    @Test fun testImportMainToExternalAsExternalNamespace() {
        file("ext.rell", "@external module; @log entity user { name; }")
        initExternalModule(1, "ext", "user", "Bob")
        initChainDependency(1, "other")

        def("@external('other') namespace { import ext; }")
        chk("ext.user @* {} ( user, .name )", "[(ext[other]:user[1],name=Bob)]")
    }

    @Test fun testImportExternalToRegular() {
        file("lib.rell", "module;")
        file("ext.rell", "@external module; import lib;")
        chkCompile("import ext;", "ct_err:ext.rell:import:module_not_external:lib")
        chkCompile("@external('foo') import ext;", "ct_err:ext.rell:import:module_not_external:lib")
    }

    @Test fun testImportMainToRegularAsExternal() {
        file("lib.rell", "module;")
        chkCompile("@external('foo') namespace { import lib; }", "ct_err:import:module_not_external:lib")
        chkCompile("@external('foo') import lib;", "ct_err:import:module_not_external:lib")
    }

    @Test fun testImportExternalToExternalAsRegular() {
        file("companies.rell", "@external module; @log entity company { name; }")
        file("users.rell", "@external module; import companies; @log entity user { name; companies.company; }")

        initExternalModule(1, "companies", "company", "Amazon")
        initExternalModule(1, "users", "user", "Jeff", "company", "1")
        initChainDependency(1, "A")

        def("@external('A') import users;")
        def("@external('A') import companies;")
        def("entity data { users.user; }")
        insert("c0.data", "user", "100,1")

        chkDataRaw("c0.data(100,1)", "c1101.company(1,Amazon,330100)", "c1101.user(1,1,Jeff,330100)")
        chk("users.user @* {} ( user, .name )", "[(users[A]:user[1],name=Jeff)]")
        chk("companies.company @* {} ( company, .name )", "[(companies[A]:company[1],name=Amazon)]")
        chk("data @ {} (_=.user, .user.name, .user.company, .user.company.name)",
                "(users[A]:user[1],Jeff,companies[A]:company[1],Amazon)")
    }

    @Test fun testImportExternalToExternalAsExternal() {
        file("companies.rell", "@external module; @log entity company { name; }")
        file("users.rell", "@external module; @external('A') import companies; @log entity user { name; companies.company; }")

        initExternalModule(1, "companies", "company", "Amazon")
        initChainDependency(1, "A")
        initExternalModule(2, "users", "user", "Jeff", "company", "1")
        initChainDependency(2, "B")

        def("@external('B') import users;")
        def("entity data { users.user; }")
        insert("c0.data", "user", "100,1")

        chkDataRaw("c0.data(100,1)", "c1101.company(1,Amazon,330100)", "c1102.user(1,1,Jeff,330200)")
        chk("users.user @* {} ( user, .name )", "[(users[B]:user[1],name=Jeff)]")
        chk("data @ {} (_=.user, .user.name, .user.company, .user.company.name)",
                "(users[B]:user[1],Jeff,companies[A]:company[1],Amazon)")
    }

    @Test fun testImportMainToExternalAsRegularAndExternal() {
        file("users.rell", "@external module; @log entity user { name; }")
        initExternalModule(1, "users", "user", "Bob")
        initChainDependency(1, "A")

        def("import reg_users: users;")
        def("@external('A') import ext_users: users;")
        insert(LibBlockTransactionTest.BLOCK_INSERTS_0)
        insert("c0.user", "name,transaction", "2,'Alice',720")

        chkDataRaw("c0.user(2,Alice,720)", "c1101.user(1,Bob,330100)")
        chk("reg_users.user @ {} ( _=user, _=.name )", "(users:user[2],Alice)")
        chk("ext_users.user @ {} ( _=user, _=.name )", "(users[A]:user[1],Bob)")
        chkEx("{ val u: ext_users.user = reg_users.user @ {}; return 0; }", "ct_err:stmt_var_type:u:[users[A]:user]:[users:user]")
        chkEx("{ val u: reg_users.user = ext_users.user @ {}; return 0; }", "ct_err:stmt_var_type:u:[users:user]:[users[A]:user]")
    }

    @Test fun testImportMainToExternalAsExternalMultipleTimesSameChain() {
        file("users.rell", "@external module; @log entity user { name; }")
        initExternalModule(1, "users", "user", "Bob")
        initChainDependency(1, "A")

        def("@external('A') import users_1: users;")
        def("@external('A') import users_2: users;")
        def("@external('A') import users_3: users;")

        chkDataRaw("c1101.user(1,Bob,330100)")
        chk("users_1.user @ {} ( _=user, _=.name )", "(users[A]:user[1],Bob)")
        chk("users_2.user @ {} ( _=user, _=.name )", "(users[A]:user[1],Bob)")
        chk("users_3.user @ {} ( _=user, _=.name )", "(users[A]:user[1],Bob)")
        chkEx("{ val u: users_1.user = users_2.user @ {}; return u; }", "users[A]:user[1]")
        chkEx("{ val u: users_2.user = users_3.user @ {}; return u; }", "users[A]:user[1]")
        chkEx("{ val u: users_3.user = users_1.user @ {}; return u; }", "users[A]:user[1]")
    }

    @Test fun testImportMainToExternalAsExternalMultipleTimesDifferentChains() {
        file("users.rell", "@external module; @log entity user { name; }")
        initExternalModule(1, "users", "user", "Bob")
        initChainDependency(1, "A")
        initExternalModule(2, "users", "user", "Alice")
        initChainDependency(2, "B")
        initExternalModule(3, "users", "user", "Trudy")
        initChainDependency(3, "C")

        def("@external('A') import users_1: users;")
        def("@external('B') import users_2: users;")
        def("@external('C') import users_3: users;")

        chkDataRaw("c1101.user(1,Bob,330100)", "c1102.user(1,Alice,330200)", "c1103.user(1,Trudy,330300)")
        chk("users_1.user @ {} ( _=user, _=.name )", "(users[A]:user[1],Bob)")
        chk("users_2.user @ {} ( _=user, _=.name )", "(users[B]:user[1],Alice)")
        chk("users_3.user @ {} ( _=user, _=.name )", "(users[C]:user[1],Trudy)")
        chkEx("{ val u: users_1.user = users_2.user @ {}; return 0; }", "ct_err:stmt_var_type:u:[users[A]:user]:[users[B]:user]")
        chkEx("{ val u: users_2.user = users_3.user @ {}; return 0; }", "ct_err:stmt_var_type:u:[users[B]:user]:[users[C]:user]")
        chkEx("{ val u: users_3.user = users_1.user @ {}; return 0; }", "ct_err:stmt_var_type:u:[users[C]:user]:[users[A]:user]")
    }

    @Test fun testExternalNamespaceInExternalModule() {
        chkExternalDefInExternalModule("namespace { @log entity company { name; } }")
    }

    @Test fun testExternalEntityInExternalModule() {
        chkExternalDefInExternalModule("@log entity company { name; }")
    }

    private fun chkExternalDefInExternalModule(def: String) {
        file("companies.rell", "module; @log entity company { name; }")
        file("users.rell", "@external module; @external('A') $def @log entity user { name; company; }")

        initExternalModule(1, "companies", "company", "Amazon")
        initChainDependency(1, "A")
        initExternalModule(2, "users", "user", "Jeff", "company", "1")
        initChainDependency(2, "B")

        def("@external('B') import users;")
        def("entity data { users.user; }")
        insert("c0.data", "user", "100,1")

        chkDataRaw("c0.data(100,1)", "c1101.company(1,Amazon,330100)", "c1102.user(1,1,Jeff,330200)")
        chk("users.user @* {} ( user, .name )", "[(users[B]:user[1],name=Jeff)]")
        chk("data @ {} (_=.user, .user.name, .user.company, .user.company.name)",
                "(users[B]:user[1],Jeff,users[A]:company[1],Amazon)")
    }

    @Test fun testTxBlkInExternalModuleImplicit() {
        file("ext.rell", "@external module; @log entity data { name; some_blk: block; some_tx: transaction; }")
        chkTxBlkInExternalModule()
    }

    @Test fun testTxBlkInExternalModuleExplicit() {
        file("ext.rell", """
            @external module;
            namespace ns { entity transaction; entity block; }
            @log entity data { name; some_blk: ns.block; some_tx: ns.transaction; }
        """)
        chkTxBlkInExternalModule()

        chk("_type_of(ext.ns.transaction@{})", "[A]:transaction")
        chk("_type_of(ext.ns.block@{})", "[A]:block")

        chkEx("{ val t: transaction = ext.ns.transaction@{}; return 0; }", "ct_err:stmt_var_type:t:[transaction]:[[A]:transaction]")
        chkEx("{ val b: block = ext.ns.block@{}; return 0; }", "ct_err:stmt_var_type:b:[block]:[[A]:block]")
        chkEx("{ val t: ext.ns.transaction = ext.data@{}(.transaction); return t; }", "[A]:transaction[330100]")
        chkEx("{ val b: ext.ns.block = ext.data@{}(.transaction.block); return b; }", "[A]:block[220100]")
        chkEx("{ val t: ext.ns.transaction = ext.data@{}(.some_tx); return t; }", "[A]:transaction[330101]")
        chkEx("{ val b: ext.ns.block = ext.data@{}(.some_blk); return b; }", "[A]:block[220102]")
    }

    private fun chkTxBlkInExternalModule() {
        initExternalModule(1, "ext", "data", "Bob", "some_tx,some_blk", "330101,220102")
        initChainDependency(1, "A")

        def("@external('A') import ext;")
        def("@external('A') namespace e { entity transaction; entity block; }")
        chkDataRaw("c1101.data(1,Bob,220102,330101,330100)")

        chk("_type_of(ext.data@{}(.transaction))", "[A]:transaction")
        chk("_type_of(ext.data@{}(.transaction.block))", "[A]:block")
        chk("_type_of(ext.data@{}(.some_tx))", "[A]:transaction")
        chk("_type_of(ext.data@{}(.some_blk))", "[A]:block")
        chk("_type_of(e.transaction@{})", "[A]:transaction")
        chk("_type_of(e.transaction@{}(.block))", "[A]:block")
        chk("_type_of(e.block@{})", "[A]:block")
        chk("_type_of(ext.transaction@{})", "[A]:transaction")
        chk("_type_of(ext.transaction@{}(.block))", "[A]:block")
        chk("_type_of(ext.block@{})", "[A]:block")
        chk("_type_of(transaction@{})", "transaction")
        chk("_type_of(block@{})", "block")

        chkEx("{ val t: transaction = ext.data@{}(.transaction); return 0; }", "ct_err:stmt_var_type:t:[transaction]:[[A]:transaction]")
        chkEx("{ val b: block = ext.data@{}(.transaction.block); return 0; }", "ct_err:stmt_var_type:b:[block]:[[A]:block]")
        chkEx("{ val t: transaction = ext.data@{}(.some_tx); return 0; }", "ct_err:stmt_var_type:t:[transaction]:[[A]:transaction]")
        chkEx("{ val b: block = ext.data@{}(.some_blk); return 0; }", "ct_err:stmt_var_type:b:[block]:[[A]:block]")

        chkEx("{ val t: e.transaction = ext.data@{}(.transaction); return t; }", "[A]:transaction[330100]")
        chkEx("{ val b: e.block = ext.data@{}(.transaction.block); return b; }", "[A]:block[220100]")
        chkEx("{ val t: e.transaction = ext.data@{}(.some_tx); return t; }", "[A]:transaction[330101]")
        chkEx("{ val b: e.block = ext.data@{}(.some_blk); return b; }", "[A]:block[220102]")

        chkEx("{ val t: ext.transaction = ext.data@{}(.transaction); return t; }", "[A]:transaction[330100]")
        chkEx("{ val b: ext.block = ext.data@{}(.transaction.block); return b; }", "[A]:block[220100]")
        chkEx("{ val t: ext.transaction = ext.data@{}(.some_tx); return t; }", "[A]:transaction[330101]")
        chkEx("{ val b: ext.block = ext.data@{}(.some_blk); return b; }", "[A]:block[220102]")

        chk("ext.data @ {} ( _=.name, _=.transaction, _=.transaction.block, _=.some_tx, _=.some_blk )",
                "(Bob,[A]:transaction[330100],[A]:block[220100],[A]:transaction[330101],[A]:block[220102])")
    }

    @Test fun testTxBlkInExternalModuleAsRegular() {
        file("ext.rell", """
            @external module;
            namespace ns { entity transaction; entity block; }
            @log entity data {
                name;
                imp_blk: block;
                imp_tx: transaction;
                exp_blk: ns.block;
                exp_tx: ns.transaction;
            }
        """)

        def("import ext;")
        tst.strictToString = false

        chk("_type_of(ext.ns.transaction@{})", "transaction")
        chk("_type_of(ext.ns.transaction@{}(.block))", "block")
        chk("_type_of(ext.ns.block@{})", "block")

        chk("_type_of(ext.transaction@{})", "transaction")
        chk("_type_of(ext.transaction@{}(.block))", "block")
        chk("_type_of(ext.block@{})", "block")

        chk("_type_of(ext.data@{}(.transaction))", "transaction")
        chk("_type_of(ext.data@{}(.transaction.block))", "block")

        chk("_type_of(ext.data@{}(.imp_tx))", "transaction")
        chk("_type_of(ext.data@{}(.imp_tx.block))", "block")
        chk("_type_of(ext.data@{}(.imp_blk))", "block")

        chk("_type_of(ext.data@{}(.exp_tx))", "transaction")
        chk("_type_of(ext.data@{}(.exp_tx.block))", "block")
        chk("_type_of(ext.data@{}(.exp_blk))", "block")

        chk("_type_of(transaction@{})", "transaction")
        chk("_type_of(transaction@{}(.block))", "block")
        chk("_type_of(block@{})", "block")
    }

    @Test fun testTxBlkImport() {
        file("ext.rell", "@external module; @log entity user { name; }")
        initExternalModule(1, "ext", "user", "Bob")
        initChainDependency(1, "A")

        def("@external('A') import exact: ext.{transaction, block};")
        def("@external('A') import wildcard: ext.*;")

        chk("_type_of(exact.transaction@{})", "[A]:transaction")
        chk("_type_of(exact.block@{})", "[A]:block")
        chk("_type_of(wildcard.transaction@{})", "[A]:transaction")
        chk("_type_of(wildcard.block@{})", "[A]:block")
        chk("_type_of(transaction@{})", "transaction")
        chk("_type_of(block@{})", "block")
    }

    @Test fun testNoLog() {
        file("ext.rell", "@external module; entity user { name; }")
        chkCompile("import ext;", "ct_err:ext.rell:def_entity_external_nolog:user")
    }

    @Test fun testUnallowedDefs() {
        file("lib.rell", "module;")
        chkModule("object state { mutable x: integer = 123; }", "ct_err:ext.rell:def_external:module:OBJECT")
        chkModule("struct r { x: integer; }", "ct_err:ext.rell:def_external:module:STRUCT")
        chkModule("function f(){}", "ct_err:ext.rell:def_external:module:FUNCTION")
        chkModule("operation o(){}", "ct_err:ext.rell:def_external:module:OPERATION")
        chkModule("query q() = 123;", "ct_err:ext.rell:def_external:module:QUERY")
        chkModule("@log entity user { name; }", "OK")
        chkModule("enum e { A, B, C }", "OK")
    }

    @Test fun testWrongAnnotation() {
        file("ext.rell", "@external module;")
        chkModuleFull("@external('foo') module;", "ct_err:ext.rell:ann:external:args:1")
        chkCompile("@external import ext;", "ct_err:ann:external:arg_count:0")
        chkCompile("@external @log entity user { name; }", "ct_err:ann:external:arg_count:0")
        chkCompile("@external namespace {}", "ct_err:ann:external:arg_count:0")
        chkCompile("@external namespace bar {}", "ct_err:ann:external:arg_count:0")
    }

    @Test fun testSystemDefsAccess() {
        file("ext.rell", "@external module; @log entity user { name; }")
        initExternalModule(1, "ext", "user", "Bob")
        initChainDependency(1, "A")
        def("@external('A') import ext;")

        chk("_type_of(ext.transaction@{})", "[A]:transaction")
        chk("_type_of(ext.block@{})", "[A]:block")
        chkEx("{ val x: ext.integer = 123; return 0; }", "ct_err:unknown_name:ext.integer")
        chk("ext.integer('123')", "ct_err:unknown_name:[ext]:integer")
        chk("ext.max(123, 456)", "ct_err:unknown_name:[ext]:max")
    }

    @Test fun testEnum() {
        file("users.rell", "@external module; enum role { admin, guest } @log entity user { name; role; }")
        initExternalModule(1, "users", "user", "Bob", "role", "0")
        initChainDependency(1, "A")
        initExternalModule(2, "users", "user", "Alice", "role", "1")
        initChainDependency(2, "B")

        def("import users;")
        def("@external('A') import users_a: users;")
        def("@external('B') import users_b: users;")

        chkDataRaw("c1101.user(1,Bob,0,330100)", "c1102.user(1,Alice,1,330200)")
        chk("_type_of(users.user @{} (.role))", "users:role")
        chk("_type_of(users_a.user @{} (.role))", "users:role")
        chk("_type_of(users_b.user @{} (.role))", "users:role")
        chkEx("{ val x: users.role? = users_a.user @? {} (.role); return x; }", "admin")
        chkEx("{ val x: users_a.role? = users_b.user @? {} (.role); return x; }", "guest")
        chkEx("{ val x: users_b.role? = users.user @? {} (.role); return x; }", "null")
    }

    @Test fun testGtvExternalBlockTransaction() {
        file("ext.rell", "@external module;")

        tstCtx.blockchain(123, "B123")
        tst.dropTables = false
        tst.chainDependency("dep", "B123", 3)

        run {
            val t = RellCodeTester(tst.tstCtx)
            t.chainId = 123
            t.dropTables = true
            t.insert(ExternalTest.BLOCK_INSERTS_123)
            t.init()
        }
        tst.dropTables = false

        def("@external('dep') import ext;")

        chk("ext.block.from_gtv((1001).to_gtv())", "[dep]:block[1001]")
        chk("ext.block.from_gtv((1002).to_gtv())", "[dep]:block[1002]")
        chk("ext.block.from_gtv((1003).to_gtv())", "[dep]:block[1003]")
        chk("ext.block.from_gtv((1004).to_gtv())", "gtv_err:obj_missing:[[dep]:block]:1004")
        chk("ext.block.from_gtv((1005).to_gtv())", "gtv_err:obj_missing:[[dep]:block]:1005")
        chk("ext.block.from_gtv((1000).to_gtv())", "gtv_err:obj_missing:[[dep]:block]:1000")
        chk("ext.block.from_gtv((1999).to_gtv())", "gtv_err:obj_missing:[[dep]:block]:1999")

        chk("ext.transaction.from_gtv((2001).to_gtv())", "[dep]:transaction[2001]")
        chk("ext.transaction.from_gtv((2002).to_gtv())", "[dep]:transaction[2002]")
        chk("ext.transaction.from_gtv((2003).to_gtv())", "[dep]:transaction[2003]")
        chk("ext.transaction.from_gtv((2004).to_gtv())", "gtv_err:obj_missing:[[dep]:transaction]:2004")
        chk("ext.transaction.from_gtv((2005).to_gtv())", "gtv_err:obj_missing:[[dep]:transaction]:2005")
        chk("ext.transaction.from_gtv((2000).to_gtv())", "gtv_err:obj_missing:[[dep]:transaction]:2000")
        chk("ext.transaction.from_gtv((2999).to_gtv())", "gtv_err:obj_missing:[[dep]:transaction]:2999")
    }

    @Test fun testStructOfExternalEntity() {
        tst.strictToString = false
        file("ext.rell", "@external module; @log entity user { name; }")
        def("import ext;")
        def("function f(): struct<ext.user>? = null;")
        chk("_type_of(f()!!)", "struct<ext:user>")
    }

    @Test fun testCreateExternalEntityAsRegular() {
        initBasic()
        file("ext.rell", "@external module; @log entity user { name; }")
        insert(LibBlockTransactionTest.BLOCK_INSERTS_0)
        insert("c0.user", "transaction,name", "100,720,'Bob'")

        def("import ext;")
        chk("ext.user @* {} ( user, .name )", "[(ext:user[100],name=Bob)]")
        chkDataRaw("c0.user(100,Bob,720)")

        chkOp("{ create ext.user(name = 'Alice'); }", "rt_err:op_context:noop")
        chkOp("{ delete ext.user @ {}; }", "ct_err:stmt_delete_cant:user")
    }

    @Test fun testCreateExternalEntityAsExternal() {
        file("ext.rell", "@external module; @log entity user { name; }")
        initExternalModule(1, "ext", "user", "Bob")
        initChainDependency(1, "other")

        def("@external('other') import ext;")
        chk("ext.user @* {} ( user, .name )", "[(ext[other]:user[1],name=Bob)]")
        chkDataRaw("c1101.user(1,Bob,330100)")

        chkOp("{ create ext.user(name = 'Alice'); }", "ct_err:expr_create_cant:ext.user")
        chkOp("{ delete ext.user @ {}; }", "ct_err:stmt_delete_cant:user")
    }

    private fun chkModule(code: String, expected: String) {
        chkModuleFull("@external module; $code", expected)
    }

    private fun chkModuleFull(code: String, expected: String) {
        val t = RellCodeTester(tstCtx)
        t.file("ext.rell", code)
        t.chkCompile("import ext;", expected)
    }

    private fun initChainDependency(iChain: Int, alias: String) {
        val chainRid = calcChainRid(iChain)
        tst.chainDependency(alias, chainRid, 0)
    }

    private var initBasicDone = false

    private fun initBasic(dropTables: Boolean = false) {
        if (initBasicDone) return
        initBasicDone = true
        tst.strictToString = false

        val nChains = 4

        for (iChain in 0 until nChains) {
            val chainId = calcChainId(iChain)
            val rid = calcChainRid(iChain)
            tstCtx.blockchain(chainId, rid)
        }

        if (dropTables) {
            val t = RellCodeTester(tst.tstCtx)
            t.dropTables = true
            t.init()
        }

        for (iChain in 0 until nChains) initChain(iChain)
    }

    private fun initExternalModule(
            iChain: Int,
            moduleName: String,
            entityName: String,
            value: String,
            extraColumns: String? = null,
            extraValues: String? = null
    ) {
        val chainId = calcChainId(iChain)
        val txId = calcTxId(iChain, 0)

        initBasic(tst.dropTables)
        tst.dropTables = false

        val t = RellCodeTester(tst.tstCtx)
        for ((path, code) in tst.files()) t.file(path, code)
        for ((alias, chain) in tst.chainDependencies()) t.chainDependency(alias, chain.first.toHex(), chain.second)
        t.def("import $moduleName;")
        t.chainId = calcChainId(iChain)
        t.dropTables = false

        var columns = "name,transaction"
        var values = "1,'$value',$txId"
        if (extraColumns != null) {
            columns += ",$extraColumns"
            values += ",$extraValues"
        }

        t.insert("c$chainId.$entityName", columns, values)
        t.chk("$moduleName.$entityName @ {} ( _=$entityName, _=.name )", "($moduleName:$entityName[1],text[$value])")

        tst.dropTables = false
    }

    private fun initChain(iChain: Int) {
        val chainId = calcChainId(iChain)
        val inserts = chainInserts(iChain, chainId)
        initExternalChain(tst, chainId, inserts)
    }

    private fun chainInserts(iChain: Int, chainId: Long): List<String> {
        val nBlocks = 4
        val nTxPerBlock = 4
        val b = RellTestContext.BlockBuilder(chainId)
        var iTx = 0
        for (iBlock in 0 until nBlocks) {
            val blockIid = calcBlockId(iChain, iBlock)
            b.block(blockIid, iBlock.toLong(), "$blockIid", 1500000000000 + 1000000 * iBlock)
            for (k in 0 until nTxPerBlock) {
                val txIid = calcTxId(iChain, iTx++)
                val sTx = "%02d".formatEx(iTx)
                b.tx(txIid, blockIid, "$txIid", "DEAF${chainId}0$iBlock$sTx", "BEEF${chainId}0$iBlock$sTx")
            }
        }
        return b.list()
    }

    companion object {
        fun initExternalChain(tst: RellCodeTester, chainId: Long, inserts: List<String>, dropTables: Boolean = false) {
            val t = RellCodeTester(tst.tstCtx)
            for ((path, code) in tst.files()) t.file(path, code)
            for ((alias, chain) in tst.chainDependencies()) {
                t.chainDependency(alias, chain.first.toHex(), chain.second)
            }
            t.chainId = chainId
            t.dropTables = dropTables
            t.insert(inserts)
            t.init()
        }

        private fun calcChainId(iChain: Int) = 1100L + iChain
        private fun calcChainRid(iChain: Int) = "1D${calcChainId(iChain)}"
        private fun calcBlockId(iChain: Int, iBlock: Int) = 220000L + iChain * 100 + iBlock
        private fun calcTxId(iChain: Int, iTx: Int) = 330000L + iChain * 100 + iTx
    }
}
