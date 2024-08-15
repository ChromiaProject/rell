/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.doc

import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.utils.doc.DocDefinition
import net.postchain.rell.base.utils.doc.DocUtils
import net.postchain.rell.base.utils.immListOf

abstract class BaseCodeDocTest: BaseRellTest() {
    protected fun initTst() {
        tst.ide = true
        tst.ideDocSymbolsEnabled = true
        tst.moduleSelection(null, immListOf(R_ModuleName.EMPTY))
    }

    protected fun getDocDef(code: String, name: String): DocDefinition {
        initTst()
        val rApp = tst.compileAppEx(code)
        val def = DocUtils.getDocDefinitionByName(rApp, name)
        return checkNotNull(def) { "Definition not found: '$name'" }
    }

    protected fun processDocDef(code: String, name: String, block: (DocDefinition) -> String): String {
        initTst()
        return tst.processApp(code) { app ->
            val def = DocUtils.getDocDefinitionByName(app.rApp, name)
            checkNotNull(def) { "Definition not found: '$name'" }
            block(def)
        }
    }
}
