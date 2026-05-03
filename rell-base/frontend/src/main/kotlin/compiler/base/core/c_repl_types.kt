/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.core

import net.postchain.rell.base.compiler.ast.S_Statement
import net.postchain.rell.base.compiler.base.def.C_FunctionExtensionsTable
import net.postchain.rell.base.compiler.base.def.C_MountTables
import net.postchain.rell.base.compiler.base.module.*
import net.postchain.rell.base.compiler.base.namespace.C_NsAsm_ReplState
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.model.R_AppSqlDefs
import net.postchain.rell.base.model.R_GlobalConstantDefinition
import net.postchain.rell.base.model.R_StructDefinition
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.ImmMap
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.immMapOf

/**
 * REPL compilation state types. These are pure compiler data classes that both the frontend
 * and the REPL implementation (in the umbrella module) reference.
 */
class C_ReplAppState(
        val nsAsmState: C_NsAsm_ReplState,
        val moduleHeaders: ImmMap<ModuleName, C_ModuleHeader>,
        val modules: ImmMap<C_ModuleKey, C_PrecompiledModule>,
        val sysDefs: C_SystemDefs?,
        val sqlDefs: R_AppSqlDefs,
        val mntTables: C_MountTables,
        val constants: ImmList<R_GlobalConstantDefinition>,
        val moduleArgs: ImmMap<ModuleName, R_StructDefinition>,
        val functionExtensions: C_FunctionExtensionsTable,
) {
    companion object {
        val EMPTY = C_ReplAppState(
            C_NsAsm_ReplState.EMPTY,
            immMapOf(),
            immMapOf(),
            null,
            R_AppSqlDefs.EMPTY,
            C_MountTables.EMPTY,
            immListOf(),
            immMapOf(),
            C_FunctionExtensionsTable(immListOf()),
        )
    }
}

class C_ReplDefsState(val appState: C_ReplAppState) {
    companion object {
        val EMPTY = C_ReplDefsState(C_ReplAppState.EMPTY)
    }
}

/** Data holder for a parsed REPL command. compile() is an extension in the runtime module (c_repl.kt). */
class C_ExtReplCommand(
        val extModules: ImmList<C_ExtModule>,
        val extMembers: ImmList<C_ExtModuleMember>,
        val currentModuleName: ModuleName?,
        val statements: ImmList<S_Statement>,
        val preModules: ImmMap<C_ModuleKey, C_PrecompiledModule>,
        val newModuleHeaders: ImmMap<ModuleName, C_ModuleHeader>,
)
