/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.api.base

import net.postchain.gtv.GtvFactory
import net.postchain.rell.api.base.testutils.TestRellCliEnv
import net.postchain.rell.base.compiler.base.utils.C_CommonError
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.base.testutils.unwrap
import net.postchain.rell.base.utils.PostchainGtvUtils
import net.postchain.rell.base.utils.immListOf
import org.junit.Test
import kotlin.test.assertEquals

class RellApiCompileTest: BaseRellApiTest() {
    @Test fun testCompileAppBasic() {
        val sourceDir = C_SourceDir.mapDirOf("main.rell" to "module; function main() {}")
        chkCompileAppMods(defaultConfig, sourceDir, listOf("main"), listOf(), "main")
    }

    @Test fun testCompileAppNoAppModules() {
        val config = configBuilder().includeTestSubModules(true).build()
        chkCompileAppMods(config, generalSourceDir, listOf(), listOf())
    }

    @Test fun testCompileAppAllAppModules() {
        val config = configBuilder().includeTestSubModules(true).build()
        chkCompileAppMods(config, generalSourceDir, null, listOf(), "a", "b1", "b1.b2", "c", "e1")
    }

    @Test fun testCompileAppSpecificAppModule() {
        val config = defaultConfig
        val sourceDir = generalSourceDir
        chkCompileAppMods(config, sourceDir, listOf("a"), listOf(), "a")
        chkCompileAppMods(config, sourceDir, listOf("b1"), listOf(), "b1")
        chkCompileAppMods(config, sourceDir, listOf("b1.b2"), listOf(), "b1", "b1.b2")
        chkCompileAppMods(config, sourceDir, listOf("c"), listOf(), "c")
        chkCompileAppMods(config, sourceDir, listOf("e1"), listOf(), "e1")
        chkCompileAppMods(config, sourceDir, listOf("a", "b1.b2"), listOf(), "a", "b1", "b1.b2")
    }

    @Test fun testCompileAppSpecificTestModule() {
        val config = configBuilder().includeTestSubModules(true).build()
        chkCompileAppMods(config, generalSourceDir, listOf("a"), listOf("d"), "a", "c", "d")
        chkCompileAppMods(config, generalSourceDir, listOf("a"), listOf("e1.e2"), "a", "e1.e2", "e1.e2.e3")
        chkCompileAppMods(config, generalSourceDir, listOf("b1"), listOf("d"), "b1", "c", "d")
        chkCompileAppMods(config, generalSourceDir, listOf(), listOf("d"), "c", "d")
        chkCompileAppMods(config, generalSourceDir, null, listOf("d"), "a", "b1", "b1.b2", "c", "d", "e1")
    }

    @Test fun testCompileAppAllTestModules() {
        val config = configBuilder().includeTestSubModules(true).build()
        chkCompileAppMods(config, generalSourceDir, listOf(), listOf(""), "c", "d", "e1.e2", "e1.e2.e3")
    }

    @Test fun testCompileAppTestSubModules() {
        val config1 = configBuilder().includeTestSubModules(true).build()
        val config2 = configBuilder().includeTestSubModules(false).build()
        chkCompileAppMods(config1, generalSourceDir, listOf(), listOf("d"), "c", "d")
        chkCompileAppMods(config2, generalSourceDir, listOf(), listOf("d"), "c", "d")
        chkCompileAppMods(config1, generalSourceDir, listOf(), listOf("e1.e2"), "e1.e2", "e1.e2.e3")
        chkCompileAppMods(config2, generalSourceDir, listOf(), listOf("e1.e2"), "e1.e2")
        chkCompileAppMods(config1, generalSourceDir, listOf(), listOf("e1"), "e1.e2", "e1.e2.e3")
        chkCompileAppMods(config2, generalSourceDir, listOf(), listOf("e1"), "CME:module:not_test:e1")
        chkCompileAppMods(config1, generalSourceDir, listOf(), listOf("c"), "CME:module:not_test:c")
        chkCompileAppMods(config2, generalSourceDir, listOf(), listOf("c"), "CME:module:not_test:c")
    }

    @Test fun testCompileAppTestSubModulesNotFound() {
        val config1 = configBuilder().includeTestSubModules(true).build()
        val config2 = configBuilder().includeTestSubModules(false).build()
        val sourceDir = C_SourceDir.mapDirOf("a/b/module.rell" to "@test module;")
        chkCompileAppMods(config1, sourceDir, listOf(), listOf("a"), "a.b")
        chkCompileAppMods(config2, sourceDir, listOf(), listOf("a"), "CME:import:not_found:a")
        chkCompileAppMods(config1, sourceDir, listOf(), listOf("x"), "CME:import:not_found:x")
        chkCompileAppMods(config2, sourceDir, listOf(), listOf("x"), "CME:import:not_found:x")
    }

    @Test fun testCompileAppTestModuleAsAppModule() {
        chkCompileAppMods(defaultConfig, generalSourceDir, listOf("d"), listOf(), "CME:module:main_test:d")
    }

    @Test fun testCompileAppModuleNotFound() {
        val config = defaultConfig
        chkCompileAppMods(config, generalSourceDir, listOf("foo"), listOf(), "CME:import:not_found:foo")
        chkCompileAppMods(config, generalSourceDir, listOf(), listOf("foo"), "CME:import:not_found:foo")

        val config2 = config.toBuilder().includeTestSubModules(false).build()
        chkCompileAppMods(config2, generalSourceDir, listOf(), listOf("foo"), "CME:import:not_found:foo")
    }

    @Test fun testCompileAppCompilationError() {
        val sourceDir = C_SourceDir.mapDirOf(
            "good.rell" to "module;",
            "bad.rell" to "module; import lib;",
            "lib.rell" to "module; val x: integer = true;",
        )
        chkCompileAppMods(defaultConfig, sourceDir, listOf("good"), listOf(), "good")
        chkCompileAppMods(defaultConfig, sourceDir, listOf("bad"), listOf(), "CTE:lib.rell:def:const_expr_type:[integer]:[boolean]")
    }

    @Test fun testCompileAppMountConflict() {
        val config1 = configBuilder().mountConflictError(true).build()
        val config2 = configBuilder().mountConflictError(false).build()

        val sourceDir = C_SourceDir.mapDirOf(
            "foo.rell" to "module; entity user {}",
            "bar.rell" to "module; entity user {}",
        )

        val err1 = "bar.rell:mnt_conflict:user:[bar:user]:user:ENTITY:[foo:user]:foo.rell(1:16)"
        val err2 = "foo.rell:mnt_conflict:user:[foo:user]:user:ENTITY:[bar:user]:bar.rell(1:16)"
        chkCompileAppMods(config1, sourceDir, null, listOf(), "CTE:[$err1][$err2]")
        chkCompileAppMods(config2, sourceDir, null, listOf(), "bar", "foo")

        chkCompileAppMods(config1, sourceDir, listOf(), listOf())
        chkCompileAppMods(config2, sourceDir, listOf(), listOf())
        chkCompileAppMods(config1, sourceDir, listOf("foo"), listOf(), "foo")
        chkCompileAppMods(config2, sourceDir, listOf("foo"), listOf(), "foo")
        chkCompileAppMods(config1, sourceDir, listOf("bar"), listOf(), "bar")
        chkCompileAppMods(config2, sourceDir, listOf("bar"), listOf(), "bar")
    }

    @Test fun testCompileAppMountConflictSys() {
        val config1 = configBuilder().mountConflictError(true).build()
        val config2 = configBuilder().mountConflictError(false).build()
        val sourceDir = C_SourceDir.mapDirOf("sys.rell" to "module; @mount('blocks') entity user {}")
        val err = "sys.rell:mnt_conflict:sys:[sys:user]:blocks"
        chkCompileAppMods(config1, sourceDir, null, listOf(), "CTE:$err")
        chkCompileAppMods(config2, sourceDir, null, listOf(), "sys")
        chkCompileAppMods(config1, sourceDir, listOf("sys"), listOf(), "CTE:$err")
        chkCompileAppMods(config2, sourceDir, listOf("sys"), listOf(), "sys")
    }

    @Test fun testCompileAppModuleArgs() {
        val sourceDir = C_SourceDir.mapDirOf(
            "foo.rell" to "module; struct module_args { x: integer; }",
            "bar.rell" to "module; struct module_args { y: text; }",
        )

        var config = defaultConfig
        chkCompileAppMods(config, sourceDir, listOf(), listOf())
        chkCompileAppMods(config, sourceDir, listOf("foo"), listOf(), "CME:module_args_missing:foo")
        chkCompileAppMods(config, sourceDir, listOf("bar"), listOf(), "CME:module_args_missing:bar")
        chkCompileAppMods(config, sourceDir, null, listOf(), "CME:module_args_missing:bar,foo")

        config = configBuilder().moduleArgsMissingError(false).build()
        chkCompileAppMods(config, sourceDir, listOf(), listOf())
        chkCompileAppMods(config, sourceDir, listOf("foo"), listOf(), "foo")
        chkCompileAppMods(config, sourceDir, listOf("bar"), listOf(), "bar")
        chkCompileAppMods(config, sourceDir, null, listOf(), "bar", "foo")

        val fooArgs = mapOf("x" to GtvFactory.gtv(123))
        val barArgs = mapOf("y" to GtvFactory.gtv("Hello"))

        config = configBuilder().moduleArgs("foo" to fooArgs).build()
        chkCompileAppMods(config, sourceDir, listOf("foo"), listOf(), "foo")
        chkCompileAppMods(config, sourceDir, listOf("bar"), listOf(), "CME:module_args_missing:bar")
        chkCompileAppMods(config, sourceDir, null, listOf(), "CME:module_args_missing:bar")

        config = configBuilder().moduleArgs("bar" to barArgs).build()
        chkCompileAppMods(config, sourceDir, listOf("foo"), listOf(), "CME:module_args_missing:foo")
        chkCompileAppMods(config, sourceDir, listOf("bar"), listOf(), "bar")
        chkCompileAppMods(config, sourceDir, null, listOf(), "CME:module_args_missing:foo")

        config = configBuilder().moduleArgs("foo" to barArgs).build()
        chkCompileAppMods(config, sourceDir, listOf(), listOf())
        chkCompileAppMods(config, sourceDir, listOf("foo"), listOf(),
            "CME:module_args_bad:foo:gtv_err:struct_nokey:foo:module_args:x")
        chkCompileAppMods(config, sourceDir, listOf("bar"), listOf(), "CME:module_args_missing:bar")
        chkCompileAppMods(config, sourceDir, null, listOf(), "CME:module_args_missing:bar")
    }

    @Test fun testCompileAppModuleArgsDefaultValue() {
        val sourceDir = C_SourceDir.mapDirOf(
            "foo.rell" to "module; struct module_args { p: text; q: integer = 123; }",
            "bar.rell" to "module; struct module_args { r: text = 'Hello'; s: integer = 456; }",
        )

        var config = defaultConfig
        chkCompileAppMods(config, sourceDir, listOf(), listOf())
        chkCompileAppMods(config, sourceDir, listOf("foo"), listOf(), "CME:module_args_missing:foo")
        chkCompileAppMods(config, sourceDir, listOf("bar"), listOf(), "bar")
        chkCompileAppMods(config, sourceDir, null, listOf(), "CME:module_args_missing:foo")

        config = configBuilder().moduleArgs("foo" to mapOf("p" to GtvFactory.gtv("A"))).build()
        chkCompileAppMods(config, sourceDir, listOf("foo"), listOf(), "foo")
        chkCompileAppMods(config, sourceDir, listOf("bar"), listOf(), "bar")
        chkCompileAppMods(config, sourceDir, null, listOf(), "bar", "foo")

        config = configBuilder().moduleArgs("bar" to mapOf()).build()
        chkCompileAppMods(config, sourceDir, listOf("foo"), listOf(), "CME:module_args_missing:foo")
        chkCompileAppMods(config, sourceDir, listOf("bar"), listOf(), "bar")
        chkCompileAppMods(config, sourceDir, null, listOf(), "CME:module_args_missing:foo")
    }

    private fun chkCompileAppMods(
        config: RellApiCompile.Config,
        sourceDir: C_SourceDir,
        appModules: List<String>?,
        testModules: List<String>,
        vararg expected: String,
    ) {
        val env = TestRellCliEnv()
        val config2 = config.toBuilder().cliEnv(env).build()

        val appMods = appModules?.map { R_ModuleName.of(it) }
        val testMods = testModules.map { R_ModuleName.of(it) }
        val actualList = compileApp(config2, sourceDir, appMods, testMods)

        assertEquals(expected.toList(), actualList)
    }

    private fun compileApp(
        config: RellApiCompile.Config,
        sourceDir: C_SourceDir,
        appModules: List<R_ModuleName>?,
        testModules: List<R_ModuleName> = immListOf(),
    ): List<String> {
        val options = RellApiBaseInternal.makeCompilerOptions(config)

        val apiRes = try {
            compileApp0(config, options, sourceDir, appModules, testModules)
        } catch (e: C_CommonError) {
            return listOf("CME:${e.code}")
        }

        val res = apiRes.cRes
        val ctErr = handleCompilationError(res)
        return if (ctErr != null) listOf(ctErr) else res.app!!.moduleMap.keys.map { it.str() }.sorted()
    }

    @Test fun testCompileGtv() {
        val rellVer = RellTestUtils.RELL_VER

        chkCompileGtv(defaultConfig, generalSourceDir, "a",
            """{"compilerVersion":"$rellVer","modules":["a"],"sources":{"a.rell":"module;"},"version":"$rellVer"}"""
        )

        chkCompileGtv(defaultConfig, generalSourceDir, "b1.b2", """{
            "compilerVersion":"$rellVer",
            "modules":["b1.b2"],
            "sources":{"b1/b2.rell":"module;","b1/module.rell":"module;"},
            "version":"$rellVer"
        }""")

        chkCompileGtv(defaultConfig, generalSourceDir, "d", "CME:module:main_test:d")

        val config = defaultConfig.toBuilder().version("0.13.0").build()
        chkCompileGtv(config, generalSourceDir, "a",
            """{"compilerVersion":"$rellVer","modules":["a"],"sources":{"a.rell":"module;"},"version":"0.13.0"}"""
        )
    }

    @Test fun testCompileGtvModuleArgs() {
        val sourceDir = C_SourceDir.mapDirOf(
            "foo.rell" to "module; struct module_args { x: integer; }",
            "bar.rell" to "module;",
            "ref.rell" to "module; import foo;",
        )

        val fooEntry = """"foo.rell":"module; struct module_args { x: integer; }""""
        val barEntry = """"bar.rell":"module;""""
        val refEntry = """"ref.rell":"module; import foo;""""
        val compVerEntry = """"compilerVersion":"${RellTestUtils.RELL_VER}""""
        val verEntry = """"version":"${RellTestUtils.RELL_VER}""""

        var config = defaultConfig
        chkCompileGtv(config, sourceDir, "foo", "CME:module_args_missing:foo")
        chkCompileGtv(config, sourceDir, "ref", "CME:module_args_missing:foo")
        chkCompileGtv(config, sourceDir, "bar", """{$compVerEntry,"modules":["bar"],"sources":{$barEntry},$verEntry}""")

        config = configBuilder().moduleArgsMissingError(false).build()
        chkCompileGtv(config, sourceDir, "foo", """{$compVerEntry,"modules":["foo"],"sources":{$fooEntry},$verEntry}""")
        chkCompileGtv(config, sourceDir, "bar", """{$compVerEntry,"modules":["bar"],"sources":{$barEntry},$verEntry}""")
        chkCompileGtv(config, sourceDir, "ref",
            """{$compVerEntry,"modules":["ref"],"sources":{$fooEntry,$refEntry},$verEntry}""")

        val fooArgs = mapOf("x" to GtvFactory.gtv(123))
        val argsEntry = """"moduleArgs":{"foo":{"x":123}}"""
        config = configBuilder().moduleArgs("foo" to fooArgs).build()
        chkCompileGtv(config, sourceDir, "foo",
            """{$compVerEntry,$argsEntry,"modules":["foo"],"sources":{$fooEntry},$verEntry}""")
        chkCompileGtv(config, sourceDir, "bar",
            """{$compVerEntry,$argsEntry,"modules":["bar"],"sources":{$barEntry},$verEntry}""")
        chkCompileGtv(config, sourceDir, "ref",
            """{$compVerEntry,$argsEntry,"modules":["ref"],"sources":{$fooEntry,$refEntry},$verEntry}""")

        val wrongArgs = mapOf("x" to GtvFactory.gtv("Hello"))
        val wrongArgsEntry = """"moduleArgs":{"foo":{"x":"Hello"}}"""
        config = configBuilder().moduleArgs("foo" to wrongArgs).build()
        chkCompileGtv(config, sourceDir, "foo",
            "CME:module_args_bad:foo:gtv_err:type:[integer]:INTEGER:STRING:attr:[foo:module_args]:x")
        chkCompileGtv(config, sourceDir, "ref",
            "CME:module_args_bad:foo:gtv_err:type:[integer]:INTEGER:STRING:attr:[foo:module_args]:x")
        chkCompileGtv(config, sourceDir, "bar",
            """{$compVerEntry,$wrongArgsEntry,"modules":["bar"],"sources":{$barEntry},$verEntry}""")
    }

    @Test fun testCompileGtvModuleArgsDefaultValue() {
        val sourceDir = C_SourceDir.mapDirOf(
            "foo.rell" to "module; struct module_args { x: integer; y: integer = 123; }",
            "bar.rell" to "module; struct module_args { p: integer = 456; q: integer = 789; }",
        )

        val fooEntry = file(sourceDir, "foo.rell")
        val barEntry = file(sourceDir, "bar.rell")
        val compVerEntry = """"compilerVersion":"${RellTestUtils.RELL_VER}""""
        val verEntry = """"version":"${RellTestUtils.RELL_VER}""""

        var config = defaultConfig
        chkCompileGtv(config, sourceDir, "foo", "CME:module_args_missing:foo")
        chkCompileGtv(config, sourceDir, "bar", """{$compVerEntry,"modules":["bar"],"sources":{$barEntry},$verEntry}""")

        config = configBuilder().moduleArgs("foo" to mapOf("x" to GtvFactory.gtv(321))).build()
        val argsEntry = """"moduleArgs":{"foo":{"x":321}}"""
        chkCompileGtv(config, sourceDir, "foo",
            """{$compVerEntry,$argsEntry,"modules":["foo"],"sources":{$fooEntry},$verEntry}""")
        chkCompileGtv(config, sourceDir, "bar",
            """{$compVerEntry,$argsEntry,"modules":["bar"],"sources":{$barEntry},$verEntry}""")
    }

    private fun file(sourceDir: C_SourceDir, path: String): String {
        val f = sourceDir.file(C_SourcePath.parse(path))
        checkNotNull(f)
        val text = f.readText().replace("=", "\\u003d")
        return "\"$path\":\"$text\""
    }

    private fun chkCompileGtv(
        config: RellApiCompile.Config,
        sourceDir: C_SourceDir,
        mainModule: String,
        expected: String,
    ) {
        val actual = compileGtv(config, sourceDir, mainModule)
        assertEquals(expected.unwrap(), actual)
    }

    private fun compileGtv(
        config: RellApiCompile.Config,
        sourceDir: C_SourceDir,
        mainModule: String,
    ): String {
        return try {
            compileGtv0(config, sourceDir, mainModule)
        } catch (e: C_CommonError) {
            "CME:${e.code}"
        }
    }

    private fun compileGtv0(
        config: RellApiCompile.Config,
        sourceDir: C_SourceDir,
        mainModule: String,
    ): String {
        val options = RellApiBaseInternal.makeCompilerOptions(config)
        val rModuleName = R_ModuleName.of(mainModule)
        val apiRes = compileApp0(config, options, sourceDir, listOf(rModuleName))

        val cRes = apiRes.cRes
        val ctErr = handleCompilationError(cRes)
        if (ctErr != null) return ctErr

        val gtv = RellApiBaseInternal.compileGtv0(config, sourceDir, immListOf(rModuleName), cRes.files)
        return PostchainGtvUtils.gtvToJson(gtv)
    }
}
