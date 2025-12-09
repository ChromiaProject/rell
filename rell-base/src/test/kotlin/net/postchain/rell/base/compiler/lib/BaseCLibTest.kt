/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.lib

import net.postchain.rell.base.compiler.base.lib.C_LibModule
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceBodyDsl
import net.postchain.rell.base.testutils.BaseRellTest

abstract class BaseCLibTest: BaseRellTest() {
    internal fun makeModule(block: Ld_NamespaceBodyDsl.() -> Unit): C_LibModule {
        return C_LibModule.make("test", Lib_Rell.MODULE, requireSince = false) {
            block(this)
        }
    }
}
