/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.api.shell

import net.postchain.gtv.GtvFactory
import net.postchain.rell.api.base.BaseRellApiTest
import net.postchain.rell.api.base.RellApiCompile
import net.postchain.rell.base.compiler.base.utils.C_CommonError
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.sql.SqlUtils
import net.postchain.rell.base.testutils.RellReplTester
import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.base.testutils.SqlTestUtils
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.plus
import kotlin.test.Test
import kotlin.test.assertEquals

internal class RellApiRunShellTest: BaseRellApiTest() {
    @Test fun testRunShell() {
        val sourceDir = C_SourceDir.mapDirOf(
            "start.rell" to "module; function f() = 123;",
            "lib.rell" to "module; function g() = 456;",
        )

        val input = immListOf(
            "2+2",
            "f()",
            "g()",
            "import lib;",
            "lib.g()",
        )

        chkShell(sourceDir, input,
            "RES:int[4]", "RES:int[123]", "CTE:<console>:unknown_name:g", "RES:int[456]",
            module = "start",
        )
    }

    @Test fun testRunShellBugEnum() {
        val sourceDir = C_SourceDir.mapDirOf("lib.rell" to "module; enum color { red }")
        chkShell(sourceDir, immListOf("color.red"), "RES:lib:color[red]", module = "lib")
    }

    @Test fun testModuleArgs() {
        val sourceDir = C_SourceDir.mapDirOf(
            "lib.rell" to "module; struct module_args { x: integer; } function f() = chain_context.args;",
        )

        val input = immListOf("import lib;", "lib.f()")
        chkShell(sourceDir, input, "rt_err:chain_context.args:no_module_args:lib")

        val compileConfig = configBuilder().moduleArgs("lib" to mapOf("x" to GtvFactory.gtv(123))).build()
        chkShell(sourceDir, input, "RES:lib:module_args[x=int[123]]", compileConfig = compileConfig)
    }

    @Test fun testModuleArgsDefaultValue() {
        val sourceDir = C_SourceDir.mapDirOf(
            "foo.rell" to "module; struct module_args { x: text; y: integer = 123; } function f() = chain_context.args;",
            "bar.rell" to "module; struct module_args { p: text = 'Hello'; q: integer = 456; } function g() = chain_context.args;",
        )

        chkShell(sourceDir, immListOf("import foo; foo.f()"), "rt_err:chain_context.args:no_module_args:foo")
        chkShell(sourceDir, immListOf("import bar; bar.g()"), "RES:bar:module_args[p=text[Hello],q=int[456]]")

        var compileConfig = configBuilder().moduleArgs("foo" to mapOf("x" to GtvFactory.gtv("ABC"))).build()
        chkShell(sourceDir, immListOf("import foo; foo.f()"),
            "RES:foo:module_args[x=text[ABC],y=int[123]]",
            compileConfig = compileConfig,
        )

        compileConfig = configBuilder().moduleArgs("bar" to mapOf()).build()
        chkShell(sourceDir, immListOf("import bar; bar.g()"),
            "RES:bar:module_args[p=text[Hello],q=int[456]]",
            compileConfig = compileConfig,
        )

        compileConfig = configBuilder().moduleArgs("bar" to mapOf("q" to GtvFactory.gtv(789))).build()
        chkShell(sourceDir, immListOf("import bar; bar.g()"),
            "RES:bar:module_args[p=text[Hello],q=int[789]]",
            compileConfig = compileConfig,
        )
    }

    @Test fun testMerkleHash() {
        val input = immListOf(
            "gtv.from_json('[[]]').hash()",
            "gtv.from_json('[{}]').hash()",
        )

        chkShell(C_SourceDir.EMPTY, input,
            "RES:byte_array[b27d13915e478770d8cbaaf72d2c92f67a17250b2c40c9a7b36c3e996ae5fad7]",
            "RES:byte_array[5ac6c92dffe0a0defa0581023e84c3d344a42d4ff90fc2a3af0d40dbf8d7a622]",
        )
    }

    private fun chkShell(
        sourceDir: C_SourceDir,
        input: ImmList<String>,
        vararg expected: String,
        module: String? = null,
        useDatabase: Boolean = false,
        compileConfig: RellApiCompile.Config = defaultConfig,
    ) {
        val inChannelFactory = RellReplTester.TestReplInputChannelFactory(input + "\\q")
        val outChannelFactory = RellReplTester.TestReplOutputChannelFactory()

        val runConfig = RellApiRunShell.Config.Builder()
            .compileConfig(compileConfig)
            .inputChannelFactory(inChannelFactory)
            .outputChannelFactory(outChannelFactory)

        if (useDatabase) {
            val (handle, url) = SqlTestUtils.createTempDbUrl()
            resource(handle)
            runConfig.databaseUrl(url)
            SqlTestUtils.createIsolatedSchemaConnection().use { con ->
                SqlUtils.dropAll(SqlTestUtils.createSqlExecutor(con), true)
            }
        }

        val moduleName = if (module == null) null else ModuleName.of(module)

        try {
            RellApiShellInternal.runShell(runConfig.build(), sourceDir, moduleName)
        } catch (e: C_CommonError) {
            val actual = listOf("CME:${e.code}")
            assertEquals(expected.toList(), actual)
            return
        }

        outChannelFactory.chk(*expected)
    }

    @Test fun testRellVersion() {
        chkRellVersion("0.0.1", "CME:config:version:unknown")
        chkRellVersion("100.100.100", "CME:config:version:unknown")
        chkRellVersion("0.10.8", "CME:config:version:unsupported")
        chkRellVersion(RellTestUtils.NEXT_VER, "CME:config:version:unknown")
    }

    private fun chkRellVersion(version: String, err: String) {
        val config = configBuilder().version(version).build()
        val sourceDir = C_SourceDir.mapDirOf("lib.rell" to "module; enum color { red }")
        chkShell(sourceDir, immListOf(), "$err:$version", compileConfig = config)
    }

    private val OP_MODS_DIR = C_SourceDir.mapDirOf("op_mods.rell" to """
        module;
        entity person { name; }
        operation add_person(name) { create person(name); }
        @singular operation add_person_singular(name) { create person(name); }
        @singular operation add_person_singular_2(name) { create person(name); }
        @compound operation add_person_compound(name) { create person(name); }
        @singular @compound operation add_person_singular_compound(name) { create person(name); }
    """)

    @Test fun testSingularOpDifferentOperationsSucceeds() {
        val shellInput = immListOf(
            "import op_mods;",
            "\\db-update",
            "rell.test.tx([op_mods.add_person_singular('Alice'), op_mods.add_person_singular_2('Bob')]).run();",
            "op_mods.person@*{}(.name);"
        )
        val expected = "RES:unit, RES:list<text>[text[Alice],text[Bob]]"
        chkShell(OP_MODS_DIR, shellInput, expected, useDatabase = true)
    }

    @Test fun testCompoundOpAfterNormalSucceeds() {
        val shellInput = immListOf(
            "import op_mods;",
            "\\db-update",
            "rell.test.tx([op_mods.add_person('Alice'), op_mods.add_person_compound('Bob') ]).run();",
            "op_mods.person@*{}(.name);"
        )
        val expected = "RES:unit, RES:list<text>[text[Alice],text[Bob]]"
        chkShell(OP_MODS_DIR, shellInput, expected, useDatabase = true)
    }

    @Test fun testSingularCompoundOpAloneFails() {
        val shellInput = immListOf(
            "import op_mods;",
            "\\db-update",
            "rell.test.tx([op_mods.add_person_singular_compound('Bob')]).run();",
        )
        val expected = "rt_err:fn:rell.test.tx.run:fail:net.postchain.common.exception.TransactionIncorrect"
        chkShell(OP_MODS_DIR, shellInput, expected, useDatabase = true)
    }

    @Test fun testFunctionHiddenParamAnnotationInvalid() {
        val expected = "CTE:<console>:modifier:invalid:ann:dummy_annotation"
        chkSingleNoCtx("function foo(@dummy_annotation x: integer): integer { return x + 17; }", expected)
        chkSingleNoCtx("function bar(x: integer, @dummy_annotation y: text) = y.repeat(x);", expected)
        chkSingleNoCtx("function baz(@dummy_annotation a: gtv, z: big_integer) { return 10L; }", expected)
        chkSingleNoCtx("function quix(@dummy_annotation name) = \"We're here.\";", expected)
        chkSingleNoCtx("function quam(@dummy_annotation dec: decimal) = dec * 3.1415;", expected)
    }

    @Test fun testOperationHiddenParamAnnotationInvalid() {
        val expected = "CTE:<console>:def_repl:OPERATION, CTE:<console>:modifier:invalid:ann:dummy_annotation"
        chkSingleNoCtx("operation foo(@dummy_annotation x: integer) {}", expected)
        chkSingleNoCtx("operation bar(x: integer, @dummy_annotation y: text) {}", expected)
        chkSingleNoCtx("operation baz(@dummy_annotation a: gtv, z: big_integer) {}", expected)
        chkSingleNoCtx("operation quix(@dummy_annotation name) {}", expected)
        chkSingleNoCtx("operation quam(@dummy_annotation arr: byte_array) {}", expected)
    }

    @Test fun testQueryHiddenParamAnnotationInvalid() {
        val expected = "CTE:<console>:def_repl:QUERY, CTE:<console>:modifier:invalid:ann:dummy_annotation"
        chkSingleNoCtx("query foo(@dummy_annotation x: integer): integer = x + 1;", expected)
        chkSingleNoCtx("query bar(x: integer, @dummy_annotation y: text): text { return (x).to_text() + y; }", expected)
        chkSingleNoCtx("query baz(@dummy_annotation a: gtv, z: big_integer): boolean { return z.to_gtv() == a; }", expected)
        chkSingleNoCtx("query quix(@dummy_annotation name): text = name.reversed();", expected)
        chkSingleNoCtx("query quam(@dummy_annotation dec: decimal) = dec * 3.1415;", expected)
    }

    @Test fun testEntityHiddenAttrAnnotationInvalid() {
        val expected = "CTE:<console>:def_repl:ENTITY, CTE:<console>:modifier:invalid:ann:dummy_annotation"
        chkSingleNoCtx("entity foo { @dummy_annotation x: integer; }", expected)
        chkSingleNoCtx("entity bar { x: integer = 10; @dummy_annotation y: text; }", expected)
        chkSingleNoCtx("entity baz { @dummy_annotation a: boolean; z: big_integer = 987654321L; }", expected)
        chkSingleNoCtx("entity quix { @dummy_annotation name; }", expected)
        chkSingleNoCtx("entity quam { @dummy_annotation dec: decimal; }", expected)
    }

    @Test fun testObjectHiddenAttrAnnotationInvalid() {
        val expected = "CTE:<console>:def_repl:OBJECT, CTE:<console>:modifier:invalid:ann:dummy_annotation"
        chkSingleNoCtx("object foo { @dummy_annotation x: integer = 0; }", expected)
        chkSingleNoCtx("object bar { x: integer = -2; @dummy_annotation y: text = \"Old Tom Paine\"; }", expected)
        chkSingleNoCtx("object baz { @dummy_annotation a: boolean = false; z: big_integer = 0L; }", expected)
        chkSingleNoCtx("object quix { @dummy_annotation name = 'He ran so fast!'; }", expected)
        chkSingleNoCtx("object quam { @dummy_annotation arr: byte_array = x\"acce55ed\"; }", expected)
    }

    @Test fun testStructHiddenAttrAnnotationInvalid() {
        val expected = "CTE:<console>:modifier:invalid:ann:dummy_annotation"
        chkSingleNoCtx("struct foo { @dummy_annotation x: integer; }", expected)
        chkSingleNoCtx("struct bar { x: integer = 10; @dummy_annotation y: text; }", expected)
        chkSingleNoCtx("struct baz { @dummy_annotation a: gtv; z: big_integer = -12L; }", expected)
        chkSingleNoCtx("struct quix { @dummy_annotation name; }", expected)
        chkSingleNoCtx("struct quam { @dummy_annotation dec: decimal; }", expected)
    }

    @Test fun testModuleArgsArePassed() {
        val sourceDir = C_SourceDir.mapDirOf("op_mods.rell" to """
            module;
            struct module_args { default_name: text = 'Iaroslav'; }
            entity person { name; }
            operation add_person_d() { create person(chain_context.args.default_name); }
        """)

        val shellInput = immListOf(
            "import op_mods;",
            "\\db-update",
            "rell.test.tx([op_mods.add_person_d()]).run();",
            "op_mods.person @ {}(.name)",
        )

        chkShell(sourceDir, shellInput, "RES:unit, RES:text[Iaroslav]", useDatabase = true)

        chkShell(
            sourceDir,
            shellInput,
            "RES:unit, RES:text[Alex]",
            useDatabase = true,
            compileConfig = configBuilder()
                .moduleArgs("op_mods" to mapOf("default_name" to GtvFactory.gtv("Alex")))
                .build(),
        )
    }

    private fun chkSingleNoCtx(code: String, expected: String) {
        chkShell(C_SourceDir.EMPTY, immListOf(code), expected)
    }
}
