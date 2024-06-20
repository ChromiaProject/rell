/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.core

import com.google.common.collect.Multimap
import net.postchain.rell.base.compiler.ast.S_Comment
import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.utils.C_IdeCompletionsContext
import net.postchain.rell.base.compiler.base.utils.C_IdeCompletionsManager
import net.postchain.rell.base.compiler.base.utils.C_MessageManager
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.model.R_DefinitionName
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.utils.ErrorTracker
import net.postchain.rell.base.utils.doc.*
import net.postchain.rell.base.utils.ide.IdeCompletion
import net.postchain.rell.base.utils.ide.IdeSymbolInfo
import net.postchain.rell.base.utils.mapValuesNotNull

class C_SymbolContext(
    val nameCtx: C_NameContext,
    val ideCompletCtx: C_IdeCompletionsContext,
    val docSymbolFactory: C_DocSymbolFactory,
) {
    fun makeDocSymbol(
        kind: DocSymbolKind,
        symbolName: DocSymbolName,
        declaration: DocDeclaration,
        mountName: String? = null,
        comment: S_Comment?,
    ): DocSymbol {
        return docSymbolFactory.makeDocSymbol(kind, symbolName, declaration, mountName, comment)
    }
}

sealed class C_DocSymbolFactory(
    val isEnabled: Boolean,
) {
    abstract fun makeDocSymbol(
        kind: DocSymbolKind,
        symbolName: DocSymbolName,
        declaration: DocDeclaration,
        mountName: String? = null,
        comment: DocComment?,
    ): DocSymbol

    fun makeDocSymbol(
        kind: DocSymbolKind,
        symbolName: DocSymbolName,
        declaration: DocDeclaration,
        mountName: String? = null,
        comment: S_Comment?,
    ): DocSymbol {
        val docComment = comment?.compile(this)
        return makeDocSymbol(kind, symbolName, declaration, mountName, docComment)
    }

    open fun compileComment(pos: S_Pos, text: String): DocComment? = null

    open fun compileFunctionParamComments(
        pos: S_Pos,
        funName: R_DefinitionName,
        funComment: S_Comment?,
        paramNames: List<R_Name>,
        paramComments: Map<R_Name, S_Comment>,
    ): DocFunctionParamComments = DocFunctionParamComments.NULL

    companion object {
        val NONE: C_DocSymbolFactory = C_DocSymbolFactory_None

        fun get(msgMgr: C_MessageManager, opts: C_CompilerOptions): C_DocSymbolFactory {
            return if (opts.ideDocSymbolsEnabled) C_DocSymbolFactory_Normal(msgMgr) else C_DocSymbolFactory_None
        }
    }
}

@Suppress("ConvertObjectToDataObject")
private object C_DocSymbolFactory_None: C_DocSymbolFactory(false) {
    override fun makeDocSymbol(
        kind: DocSymbolKind,
        symbolName: DocSymbolName,
        declaration: DocDeclaration,
        mountName: String?,
        comment: DocComment?,
    ): DocSymbol {
        return DocSymbol.NONE
    }
}

private class C_DocSymbolFactory_Normal(private val msgMgr: C_MessageManager): C_DocSymbolFactory(true) {
    override fun compileComment(pos: S_Pos, text: String): DocComment {
        val resText = tansformCommentText(text)
        return DocCommentParser.parse(resText, errorTracker(pos))
    }

    private fun tansformCommentText(text: String): String {
        val lines = text.trim().lines()
        val resLines = lines.map { transformLine(it) }
        return resLines.joinToString("\n")
    }

    private fun transformLine(line: String): String {
        val res = line.trim()
        return when {
            res.startsWith("* ") -> res.substring(2)
            res == "*" -> ""
            else -> res
        }
    }

    override fun makeDocSymbol(
        kind: DocSymbolKind,
        symbolName: DocSymbolName,
        declaration: DocDeclaration,
        mountName: String?,
        comment: DocComment?,
    ): DocSymbol {
        return DocSymbol(
            kind = kind,
            symbolName = symbolName,
            declaration = declaration,
            mountName = mountName,
            comment = comment,
        )
    }

    override fun compileFunctionParamComments(
        pos: S_Pos,
        funName: R_DefinitionName,
        funComment: S_Comment?,
        paramNames: List<R_Name>,
        paramComments: Map<R_Name, S_Comment>
    ): DocFunctionParamComments {
        val docFunComment = funComment?.compile(this)
        val docParamComments = paramComments.mapValuesNotNull {
            it.value.compile(this)
        }

        val errTracker = errorTracker(funComment?.pos ?: pos)
        return DocFunctionParamComments.make(funName, docFunComment, paramNames, docParamComments, errTracker)
    }

    private fun errorTracker(pos: S_Pos): ErrorTracker {
        return ErrorTracker { code, msg ->
            // TODO Specify actual error position within a comment, not the linked token position
            msgMgr.warning(pos, code, msg)
        }
    }
}

interface C_SymbolContextProvider {
    fun getNopSymbolContext(): C_SymbolContext
    fun getFileSymbolContext(path: C_SourcePath): C_SymbolContext
    fun getDocSymbolFactory(): C_DocSymbolFactory
}

class C_SymbolContextManager(
    msgMgr: C_MessageManager,
    opts: C_CompilerOptions,
) {
    private val mainFile = opts.symbolInfoFile

    val provider: C_SymbolContextProvider = C_SymbolContextProviderImpl()

    private val nameCtxMgr = C_NameContextManager(msgMgr, opts)
    private val ideCompletMgr = C_IdeCompletionsManager(opts)
    private val docSymbolFactory = C_DocSymbolFactory.get(msgMgr, opts)

    private val nopSymCtx = C_SymbolContext(nameCtxMgr.nopNameCtx, ideCompletMgr.nopCtx, docSymbolFactory)

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
        override fun getDocSymbolFactory() = docSymbolFactory
        override fun getNopSymbolContext() = nopSymCtx

        override fun getFileSymbolContext(path: C_SourcePath): C_SymbolContext {
            val nameCtx = if (path == mainFile) nameCtxMgr.activeNameCtx else nameCtxMgr.nopNameCtx
            val ideCompletCtx = ideCompletMgr.getFileContext(path)
            return C_SymbolContext(nameCtx, ideCompletCtx, docSymbolFactory)
        }
    }
}
