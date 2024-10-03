/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.ide

import com.google.common.collect.Multimap
import net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.lib.C_LibModule
import net.postchain.rell.base.compiler.base.utils.C_IdeCompletionsUtils
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lmodel.dsl.Ld_ModuleDsl
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.base.utils.ide.IdeCompletion
import net.postchain.rell.base.utils.toImmMultimap
import kotlin.test.assertEquals

abstract class BaseIdeCompletionTest: BaseRellTest() {
    protected var fullCompStr = true
    protected var defaultLib = false

    protected fun chkCompKeys(vararg expected: String) {
        val map = calcComps0("", null)
        val actual = map.keySet().sorted()
        assertEquals(expected.toList(), actual)
    }

    protected fun chkComps(code: String, vararg expected: String) {
        chkComps(code, -1, *expected)
    }

    protected fun chkComps(code: String, pos: Int, vararg expected: String, err: String? = null) {
        val (realCode, realPos) = prepareCode(code, pos)
        val actual = calcComps(realCode, realPos, err = err)
        assertEquals(expected.toList(), actual)
    }

    private fun prepareCode(code: String, pos: Int): Pair<String, Int?> {
        val re = Regex("\\^([0-9]+)")

        val fullCode = tst.moduleCode(code)
        val posMap = mutableMapOf<Int, Int>()
        var resCode = code

        while (true) {
            val m = re.find(resCode)
            m ?: break

            val i = m.groupValues[1].toInt()
            posMap[i] = m.range.first + (fullCode.length - code.length)

            val s = " ".repeat(m.range.last - m.range.first + 1)
            resCode = resCode.substring(0, m.range.first) + s + resCode.substring(m.range.last + 1)
        }

        val resPos = if (pos == -1) null else posMap.getValue(pos)
        return resCode to resPos
    }

    protected fun calcComps(
        code: String,
        pos: Int?,
        defaultOptions: C_CompilerOptions? = null,
        err: String? = null,
    ): List<String> {
        val map = calcComps0(code, pos, defaultOptions, err)
        return map.entries().sortedBy { it.key }.map { it.value }
    }

    protected fun calcComps0(
        code: String,
        pos: Int?,
        defaultOptions: C_CompilerOptions? = null,
        err: String? = null,
    ): Multimap<String, String> {
        val sourceDir = tst.createSourceDir(code)
        val path = C_SourcePath.parse("main.rell")

        val baseOptions = defaultOptions ?: baseCompilerOptions()
        val options = C_IdeCompletionsUtils.getCompilerOptions(sourceDir, path, pos, baseOptions)
        checkNotNull(options)

        val modSel = C_CompilerModuleSelection(listOf(R_ModuleName.EMPTY))
        val cRes = RellTestUtils.compileApp(sourceDir, modSel, options, tst.extraMod)

        val actualErr = if (cRes.errors.isEmpty()) "n/a" else RellTestUtils.msgsToString(cRes.errors)
        assertEquals(err ?: "n/a", actualErr)

        return cRes.ideCompletions.entries()
            .filterNot { (_, comp) -> defaultLib && isDefaultLib(comp) }
            .filterNot { (name, _) -> name.startsWith("_") }
            .map { (name, comp) ->
                name to completionToStr(name, comp)
            }
            .toImmMultimap()
    }

    private fun isDefaultLib(comp: IdeCompletion): Boolean {
        val name = comp.symbolName.strCode()
        return name.startsWith("rell:") || name == ":block" || name == ":transaction"
    }

    private fun completionToStr(name: String, comp: IdeCompletion): String {
        val parts = mutableListOf(name, comp.kind.name, comp.symbolName.strCode())
        if (fullCompStr) {
            parts.add(comp.params?.joinToString(", ", "(", ")") { it.code } ?: "")
            parts.add(comp.result ?: "-")
            parts.add(comp.location ?: "-")
        }
        return parts.joinToString("|")
    }

    protected fun libModule(block: Ld_ModuleDsl.() -> Unit) {
        tst.extraMod = C_LibModule.make("test", requireSince = false) {
            imports(Lib_Rell.MODULE.lModule)
            block(this)
        }
    }

    protected fun baseCompilerOptions(): C_CompilerOptions {
        return tst.compilerOptions().toBuilder()
            .defaultLib(defaultLib)
            .hiddenLib(false)
            .symbolInfoFile(null)
            .ideDocSymbolsEnabled(true)
            .build()
    }
}
