/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.doc

import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.utils.doc.DocDefinition
import net.postchain.rell.base.utils.doc.DocUtils
import net.postchain.rell.base.utils.immListOf
import kotlin.test.assertEquals

abstract class BaseCodeDocTest: BaseRellTest() {
    protected fun initTst() {
        tst.ide = true
        tst.ideDocSymbolsEnabled = true
        tst.moduleSelection(null, immListOf(ModuleName.EMPTY))
    }

    protected fun getDocDef(code: String, name: String): DocDefinition {
        initTst()
        var result: DocDefinition? = null
        val s = tst.processApp(code) { app ->
            val rApp = checkNotNull(app.rApp) { "R_App not available" }
            val def = DocUtils.getDocDefinitionByName(rApp, name)
            result = checkNotNull(def) { "Definition not found: '$name'" }
            "OK"
        }
        assertEquals("OK", s)
        return result!!
    }

    protected fun processDocDef(code: String, name: String, block: (DocDefinition) -> String): String {
        initTst()
        return tst.processApp(code) { app ->
            val rApp = checkNotNull(app.rApp) { "R_App not available" }
            val def = DocUtils.getDocDefinitionByName(rApp, name)
            checkNotNull(def) { "Definition not found: '$name'" }
            block(def)
        }
    }
}
