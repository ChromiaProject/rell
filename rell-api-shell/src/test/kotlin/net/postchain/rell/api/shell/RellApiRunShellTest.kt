/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.api.shell

import net.postchain.gtv.GtvFactory
import net.postchain.rell.api.base.BaseRellApiTest
import net.postchain.rell.api.base.RellApiCompile
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.testutils.RellReplTester
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.plus
import org.junit.Test

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
        compileConfig: RellApiCompile.Config = defaultConfig,
    ) {
        val inChannelFactory = RellReplTester.TestReplInputChannelFactory(input + "\\q")
        val outChannelFactory = RellReplTester.TestReplOutputChannelFactory()

        val runConfig = RellApiRunShell.Config.Builder()
            .compileConfig(compileConfig)
            .inputChannelFactory(inChannelFactory)
            .outputChannelFactory(outChannelFactory)
            .build()

        val moduleName = if (module == null) null else R_ModuleName.of(module)
        RellApiShellInternal.runShell(runConfig, sourceDir, moduleName)
        outChannelFactory.chk(*expected)
    }
}
