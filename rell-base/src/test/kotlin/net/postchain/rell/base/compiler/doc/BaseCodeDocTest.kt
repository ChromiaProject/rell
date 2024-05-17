/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.doc

import net.postchain.rell.base.compiler.base.core.C_CompilationResult
import net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.doc.DocDefinition
import net.postchain.rell.base.utils.doc.DocUtils
import net.postchain.rell.base.utils.immListOf

abstract class BaseCodeDocTest: BaseRellTest(useSql = false) {
    protected fun getDocDef(code: String, name: String): DocDefinition {
        val sourceDir = tst.createSourceDir(code)
        val modSel = C_CompilerModuleSelection(null, immListOf(R_ModuleName.EMPTY))
        val options = C_CompilerOptions.builder().ide(true).ideDocSymbolsEnabled(true).hiddenLib(true).build()
        val cRes = RellTestUtils.compileApp(sourceDir, modSel, options)
        checkEquals(cRes.errors, listOf())

        val def = getDocDef0(cRes, name)
        return checkNotNull(def) { "Definition not found: '$name'" }
    }

    private fun getDocDef0(cRes: C_CompilationResult, name: String): DocDefinition? {
        val moduleName = R_ModuleName.of(name.substringBefore(":"))
        val path = if (":" !in name) listOf() else name.substringAfter(":").split(".")
        val rApp = checkNotNull(cRes.app)
        val rModule = rApp.moduleMap.getValue(moduleName)
        return DocUtils.getDocDefinitionByPath(rModule, path)
    }
}
