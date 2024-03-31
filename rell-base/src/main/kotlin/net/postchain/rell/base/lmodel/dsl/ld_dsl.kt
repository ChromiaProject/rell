/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.lmodel.L_Module

@RellLibDsl
interface Ld_ModuleDsl: Ld_NamespaceBodyDsl {
    fun imports(module: L_Module)

    companion object {
        fun make(name: String, modCfg: Ld_ModuleConfig, block: Ld_ModuleDsl.() -> Unit): L_Module {
            return Ld_ModuleDslImpl.make(name, modCfg, block)
        }
    }
}

@RellLibDsl
interface Ld_MemberDsl {
    fun since(version: String)
    fun comment(text: String)
}
