/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.lib

import net.postchain.rell.base.lmodel.L_Module
import net.postchain.rell.base.lmodel.dsl.Ld_ModuleConfig
import net.postchain.rell.base.lmodel.dsl.Ld_ModuleDsl
import net.postchain.rell.base.utils.toImmMap

class C_LibModule(
    val lModule: L_Module,
    typeDefs: List<C_LibTypeDef>,
    val namespace: C_LibNamespace,
    val extensionTypes: List<C_LibTypeExtension>,
) {
    private val typeDefsByName = typeDefs.associateBy { it.typeName }.toImmMap()

    fun getTypeDef(name: String): C_LibTypeDef {
        return typeDefsByName.getValue(name)
    }

    companion object {
        fun make(
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
    }
}
