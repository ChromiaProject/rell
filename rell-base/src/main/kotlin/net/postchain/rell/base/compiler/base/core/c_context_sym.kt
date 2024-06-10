/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.core

import com.google.common.collect.Multimap
import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.utils.C_IdeCompletionsContext
import net.postchain.rell.base.compiler.base.utils.C_IdeCompletionsManager
import net.postchain.rell.base.compiler.base.utils.C_MessageManager
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.utils.ide.IdeCompletion
import net.postchain.rell.base.utils.ide.IdeSymbolInfo

class C_SymbolContext(
    val nameCtx: C_NameContext,
    val ideCompletCtx: C_IdeCompletionsContext,
)

interface C_SymbolContextProvider {
    fun getNopSymbolContext(): C_SymbolContext
    fun getFileSymbolContext(path: C_SourcePath): C_SymbolContext
}

class C_SymbolContextManager(
    msgMgr: C_MessageManager,
    opts: C_CompilerOptions,
) {
    private val mainFile = opts.symbolInfoFile

    val provider: C_SymbolContextProvider = C_SymbolContextProviderImpl()

    private val nameCtxMgr = C_NameContextManager(msgMgr, opts)
    private val ideCompletMgr = C_IdeCompletionsManager(opts)

    private val nopSymCtx = C_SymbolContext(nameCtxMgr.nopNameCtx, ideCompletMgr.nopCtx)

    fun finish(): Finish {
        val symInfos = nameCtxMgr.finish()
        val completions = ideCompletMgr.finish()
        return Finish(symInfos, completions)
    }

    class Finish(
        val symbolInfos: Map<S_Pos, IdeSymbolInfo>,
        val completions: Multimap<String, IdeCompletion>,
    )

    private inner class C_SymbolContextProviderImpl: C_SymbolContextProvider {
        override fun getNopSymbolContext(): C_SymbolContext = nopSymCtx

        override fun getFileSymbolContext(path: C_SourcePath): C_SymbolContext {
            val nameCtx = if (path == mainFile) nameCtxMgr.activeNameCtx else nameCtxMgr.nopNameCtx
            val ideCompletCtx = ideCompletMgr.getFileContext(path)
            return C_SymbolContext(nameCtx, ideCompletCtx)
        }
    }
}
