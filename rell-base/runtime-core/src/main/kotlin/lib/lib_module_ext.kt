/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.compiler.base.lib.C_LibAdapter
import net.postchain.rell.base.compiler.base.lib.C_LibModule
import net.postchain.rell.base.lmodel.dsl.Ld_ModuleConfig
import net.postchain.rell.base.lmodel.dsl.Ld_ModuleDsl

/** Typed factory for C_LibModule — bridges compiler (frontend) and lmodel DSL (runtime). */
fun C_LibModule.Companion.make(
    name: String,
    vararg imports: C_LibModule,
    requireSince: Boolean = true,
    versionControl: Boolean = true,
    block: Ld_ModuleDsl.() -> Unit,
): C_LibModule {
    val modCfg = Ld_ModuleConfig(requireSince = requireSince, versionControl = versionControl)

    val lModule = Ld_ModuleDsl.make(name, modCfg) {
        for (imp in imports) {
            this.imports(imp.lModule)
        }
        block(this)
    }

    return C_LibAdapter.makeModule(lModule)
}
