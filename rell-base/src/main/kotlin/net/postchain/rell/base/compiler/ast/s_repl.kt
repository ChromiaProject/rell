/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.ast

import net.postchain.rell.base.compiler.base.core.*
import net.postchain.rell.base.compiler.base.module.C_MidModuleCompiler
import net.postchain.rell.base.compiler.base.module.C_ModuleLoader
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.compiler.base.utils.IdeSourcePathFilePath
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.utils.associateToImmMap
import net.postchain.rell.base.utils.mapNotNullToImmList
import net.postchain.rell.base.utils.plus

internal class S_ReplCommand(steps: List<S_ReplStep>, expr: S_Expr?) {
    private val defs = steps.mapNotNullToImmList { it.definition() }

    private val stmts = let {
        (steps.mapNotNullToImmList { it.statement() } + listOfNotNull(expr).map { S_ExprStatement(it, it.startPos) })
    }

    internal fun compile(
        msgCtx: C_MessageContext,
        symCtxProvider: C_SymbolContextProvider,
        executor: C_CompilerExecutor,
        sourceDir: C_SourceDir,
        currentModuleName: R_ModuleName?,
        appState: C_ReplAppState,
    ): C_ExtReplCommand {
        val modLdr = C_ModuleLoader(msgCtx, symCtxProvider, executor, sourceDir, appState.moduleHeaders)

        if (currentModuleName != null) {
            modLdr.loadModule(currentModuleName)
        }

        val moduleName = currentModuleName ?: R_ModuleName.EMPTY
        val midMembers = modLdr.readerCtx.appCtx.withModuleContext(moduleName) { modCtx ->
            val sourcePath = C_SourcePath.EMPTY
            val idePath = IdeSourcePathFilePath(sourcePath)
            val fileCtx = modCtx.createFileContext(sourcePath, idePath)
            val defCtx = fileCtx.createDefinitionContext()
            defs.mapNotNull { it.compile(defCtx) }
        }

        val midModules = modLdr.finish()

        val midCompiler = C_MidModuleCompiler(msgCtx, symCtxProvider, midModules)
        if (currentModuleName != null) {
            midCompiler.compileModule(currentModuleName, null)
        }

        val extMembers = midCompiler.compileReplMembers(moduleName, midMembers)
        val extModules = midCompiler.getExtModules()

        val newModuleHeaders = midModules.associateToImmMap { it.moduleName to it.compiledHeader }

        return C_ExtReplCommand(extModules, extMembers, currentModuleName, stmts, appState.modules, newModuleHeaders)
    }
}

internal sealed class S_ReplStep {
    abstract fun definition(): S_Definition?
    abstract fun statement(): S_Statement?
}

internal class S_DefinitionReplStep(val def: S_Definition): S_ReplStep() {
    override fun definition() = def
    override fun statement() = null
}

internal class S_StatementReplStep(val stmt: S_Statement): S_ReplStep() {
    override fun definition() = null
    override fun statement() = stmt
}
