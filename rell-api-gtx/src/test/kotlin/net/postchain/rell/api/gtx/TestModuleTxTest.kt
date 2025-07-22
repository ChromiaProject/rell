/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.api.gtx

import net.postchain.rell.api.gtx.testutils.PostchainRellTestProjExt
import net.postchain.rell.base.testutils.BaseRellTest
import kotlin.test.Test
import net.postchain.rell.base.utils.immListOf

internal class TestModuleTxTest: BaseRellTest() {
    override fun getProjExt() = PostchainRellTestProjExt

    @Test fun testTransactions() {
        tstCtx.useSql = true

        file("prod.rell", """
            module;
            entity user { name; }
            operation add_user(name) { create user(name); }
            operation remove_user(name) { delete user @* { name }; }
        """)

        file("tests.rell", """
            @test module;
            import prod;

            function test_add_user() {
                assert_equals(prod.user@*{}(.name), list<text>());
                val tx = rell.test.tx(prod.add_user('Bob'));
                assert_equals(prod.user@*{}(.name), list<text>());
                tx.run();
                assert_equals(prod.user@*{}(.name), ['Bob']);
            }

            function test_remove_user() {
                rell.test.tx([prod.add_user('Bob'), prod.add_user('Alice')]).run();
                assert_equals(prod.user@*{}(.name), ['Bob', 'Alice']);
                val tx = rell.test.tx(prod.remove_user('Bob'));
                assert_equals(prod.user@*{}(.name), ['Bob', 'Alice']);
                tx.run();
                assert_equals(prod.user@*{}(.name), ['Alice']);
            }
        """)

        chkTests("tests", "test_add_user=OK,test_remove_user=OK")
    }

    @Test fun testMultipleBlocks() {
        tstCtx.useSql = true

        file("prod.rell", """
            module;
            entity user { name; }
            operation add_user(name) { create user(name); }
        """)

        file("tests.rell", """
            @test module;
            import prod;

            function rowid_to_int(r: rowid) = integer.from_gtv(r.to_gtv());

            function test_add_user() {
                assert_equals(prod.user@*{}(.name), list<text>());
                assert_equals(block@*{}(.block_height), list<integer>());
                assert_equals(transaction@*{}(rowid_to_int(.rowid)), list<integer>());

                prod.add_user('Bob').run();
                assert_equals(prod.user@*{}(.name), ['Bob']);
                assert_equals(block@*{}(.block_height), [0]);
                assert_equals(transaction@*{}(rowid_to_int(.rowid)), [1]);

                prod.add_user('Alice').run();
                assert_equals(prod.user@*{}(.name), ['Bob', 'Alice']);
                assert_equals(block@*{}(.block_height), [0, 1]);
                assert_equals(transaction@*{}(rowid_to_int(.rowid)), [1, 2]);

                prod.add_user('Trudy').run();
                assert_equals(prod.user@*{}(.name), ['Bob', 'Alice', 'Trudy']);
                assert_equals(block@*{}(.block_height), [0, 1, 2]);
                assert_equals(transaction@*{}(rowid_to_int(.rowid)), [1, 2, 3]);
            }
        """)

        chkTests("tests", "test_add_user=OK")
    }

    @Test fun testModuleArgs() {
        file("app.rell", """
            module;
            struct module_args { x: integer; }
            function f() = chain_context.args;
            query q() = chain_context.args;
            operation op() { print('o:' + chain_context.args); }
        """)

        file("test.rell", """
            @test module;
            import app;
            function test() {
                print('f:' + app.f());
                print('q:' + app.q());
                app.op().run();
            }
        """)

        tstCtx.useSql = true
        tst.moduleArgs("app" to "{'x':123}")

        chkTests("test", "test=OK")
        chkOut("f:app:module_args{x=123}", "q:app:module_args{x=123}", "o:app:module_args{x=123}")
    }

    @Test fun testModuleArgsDefaultValues() {
        file("app.rell", """
            module;
            struct module_args { x: text; y: integer = 123; }
            function f() = chain_context.args;
        """)
        file("test.rell", "@test module; import app; function test() { print(app.f()); }")

        tst.moduleArgs("app" to "{}")
        chkTests("test", "RTE:gtv_err:struct_noattr:app:module_args:x")
        chkOut()

        tst.moduleArgs("app" to "{'x':'Hello'}")
        chkTests("test", "test=OK")
        chkOut("app:module_args{x=Hello,y=123}")

        tst.moduleArgs("app" to "{'x':'Hello',y:456}")
        chkTests("test", "test=OK")
        chkOut("app:module_args{x=Hello,y=456}")
    }

    @Test fun testCreateExternalEntity() {
        tstCtx.useSql = true

        file("ext.rell", "@external module; @log entity user { name; }")

        file("prod.rell", """
            module;
            import ext;
            operation add_user(name) { create ext.user(name); }
        """)

        file("tests.rell", """
            @test module;
            import prod;
            import ext;

            function test_add_user() {
                assert_equals(ext.user@*{}(.name), list<text>());
                rell.test.tx(prod.add_user('Bob')).run();
                assert_equals(ext.user@*{}(.name), ['Bob']);
                rell.test.tx(prod.add_user('Alice')).run();
                assert_equals(ext.user@*{}(.name), ['Bob', 'Alice']);
            }
        """)

        chkTests("tests", "test_add_user=OK")
    }

    @Test fun testCompoundSingularOperations() {
        tstCtx.useSql = true

        file("op_mods.rell", """
            module;
            entity person { name; }
            operation add_person(name) { create person(name); }
            @singular operation add_person_singular(name) { create person(name); }
            @compound operation add_person_compound(name) { create person(name); }
            @compound operation add_person_compound_2(name) { create person(name); }
            @singular @compound operation add_person_singular_compound(name) { create person(name); }
        """)

        file("op_mods_test.rell", """
            @test module;
            import op_mods;

            @test function test__singular__once_per_tx__succeeds() {
                rell.test.tx([op_mods.add_person_singular('Alice')]).run();
                rell.test.tx([op_mods.add_person_singular('Bob')]).run();
                rell.test.tx([op_mods.add_person_singular('Charlie')]).run();
                assert_equals(op_mods.person@*{}(.name), ['Alice', 'Bob', 'Charlie']);
            }

            @test function test__compound__two_different_no_normal__fails() {
                rell.test.tx([
                    op_mods.add_person_compound('Bob'),
                    op_mods.add_person_compound_2('Alice')
                ]).run_must_fail("contains no normal operation");
            }

            @test function test__singular_compound__after_normal__succeeds() {
                rell.test.tx([
                    op_mods.add_person('Alice'),
                    op_mods.add_person_singular_compound('Bob')
                ]).run();
                assert_equals(op_mods.person@*{}(.name), ['Alice', 'Bob']);
            }
        """)

        val output = immListOf(
            "test__singular__once_per_tx__succeeds=OK",
            "test__compound__two_different_no_normal__fails=OK",
            "test__singular_compound__after_normal__succeeds=OK",
        ).joinToString(",")
        chkTests("op_mods_test", output)
    }
}
