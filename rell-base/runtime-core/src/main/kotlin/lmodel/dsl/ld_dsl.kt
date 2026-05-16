/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.lmodel.L_Module

@RellLibDsl
interface Ld_ModuleDsl: Ld_NamespaceBodyDsl {
    fun imports(module: L_Module)

    companion object {
        fun make(name: String, modCfg: Ld_ModuleConfig, block: Ld_ModuleDsl.() -> Unit): L_Module =
            Ld_ModuleDslImpl.make(name, modCfg, block)
    }
}

@RellLibDsl
interface Ld_MemberDsl {
    fun since(version: String)
    fun comment(text: String)

    /**
     * Alias for [comment] with the text as the receiver: `"""...""".comment()`. Lets a
     * multi-line `"""..."""` block stay as a leading expression instead of a wrapped call
     * argument, which the code formatter handles far more gracefully. The receiver is
     * [CharSequence] rather than [String] so the JVM signature does not clash with [comment].
     */
    fun CharSequence.comment() {
        comment(toString())
    }
}
