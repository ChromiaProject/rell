/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.core

import net.postchain.rell.base.compiler.ast.S_BasicPos
import net.postchain.rell.base.compiler.ast.S_Comment
import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.utils.C_LateGetter
import net.postchain.rell.base.compiler.base.utils.C_MessageManager
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.compiler.parser.RellTokenizer
import net.postchain.rell.base.model.R_DefinitionName
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.utils.ImmMap
import net.postchain.rell.base.utils.doc.*
import net.postchain.rell.base.utils.ide.IdeSymbolInfo
import net.postchain.rell.base.utils.mapValuesNotNull

class C_SymbolContext(
    val nameCtx: C_NameContext,
    val docSymbolFactory: C_DocSymbolFactory,
) {
    fun makeDocSymbol(
        kind: DocSymbolKind,
        symbolName: DocSymbolName,
        declaration: Lazy<DocDeclaration>,
        mountName: String? = null,
        comment: S_Comment?,
    ): DocSymbol {
        return docSymbolFactory.makeDocSymbol(kind, symbolName, declaration, mountName, comment)
    }

    fun commentProvider(getter: C_LateGetter<DocComment?>): CommentProvider = CommentProvider_Getter(getter)
    fun commentProvider(comment: S_Comment?): CommentProvider = CommentProvider_SComment(docSymbolFactory, comment)

    sealed class CommentProvider {
        abstract fun getter(kind: DocSymbolKind): C_LateGetter<DocComment?>

        companion object {
            val NULL: CommentProvider = CommentProvider_Getter(C_LateGetter.const(null))
        }
    }

    private class CommentProvider_SComment(
        private val docSymFactory: C_DocSymbolFactory,
        private val sComment: S_Comment?,
    ): CommentProvider() {
        override fun getter(kind: DocSymbolKind): C_LateGetter<DocComment?> {
            val docComment = sComment?.compile(docSymFactory, kind)
            return C_LateGetter.const(docComment)
        }
    }

    private class CommentProvider_Getter(private val getter: C_LateGetter<DocComment?>): CommentProvider() {
        override fun getter(kind: DocSymbolKind) = getter
    }
}

sealed class C_DocSymbolFactory(
    val isEnabled: Boolean,
) {
    abstract fun makeDocSymbol(
        kind: DocSymbolKind,
        symbolName: DocSymbolName,
        declaration: Lazy<DocDeclaration>,
        mountName: String? = null,
        comment: DocComment?,
    ): DocSymbol

    fun makeDocSymbol(
        kind: DocSymbolKind,
        symbolName: DocSymbolName,
        declaration: Lazy<DocDeclaration>,
        mountName: String? = null,
        comment: S_Comment?,
    ): DocSymbol {
        val docComment = comment?.compile(this, kind)
        return makeDocSymbol(kind, symbolName, declaration, mountName, docComment)
    }

    open fun compileComment(pos: S_Pos, text: String, kind: DocSymbolKind): DocComment? = null

    open fun compileFunctionParamComments(
        pos: S_Pos,
        funName: R_DefinitionName,
        funComment: S_Comment?,
        funKind: DocSymbolKind,
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
        declaration: Lazy<DocDeclaration>,
        mountName: String?,
        comment: DocComment?,
    ): DocSymbol {
        return DocSymbol.NONE
    }
}

private class C_DocSymbolFactory_Normal(private val msgMgr: C_MessageManager): C_DocSymbolFactory(true) {
    override fun compileComment(pos: S_Pos, text: String, kind: DocSymbolKind): DocComment {
        val linePosMap = lazy { RellTokenizer.linePosMap(text, pos) }
        return DocCommentParser.parse(text, kind, errorTracker(pos)) { ofs ->
            val posEntry = linePosMap.value.floorEntry(ofs)
            val errPos = if (posEntry == null) pos else S_BasicPos.addColumn(posEntry.value, ofs - posEntry.key)
            DocCommentPos_SPos(errPos)
        }
    }

    override fun makeDocSymbol(
        kind: DocSymbolKind,
        symbolName: DocSymbolName,
        declaration: Lazy<DocDeclaration>,
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
        funKind: DocSymbolKind,
        paramNames: List<R_Name>,
        paramComments: Map<R_Name, S_Comment>,
    ): DocFunctionParamComments {
        val docFunComment = funComment?.compile(this, funKind)
        val docParamComments = paramComments.mapValuesNotNull {
            it.value.compile(this, DocSymbolKind.PARAMETER)
        }

        val errTracker = errorTracker(funComment?.pos ?: pos)
        return DocFunctionParamComments.make(funName, docFunComment, paramNames, docParamComments, errTracker)
    }

    private fun errorTracker(startPos: S_Pos): DocCommentErrorTracker {
        return DocCommentErrorTracker { pos, code, msg ->
            val errPos = (pos as? DocCommentPos_SPos)?.pos ?: startPos
            msgMgr.warning(errPos, code, msg)
        }
    }

    private class DocCommentPos_SPos(val pos: S_Pos): DocCommentPos() {
        override fun toString() = pos.toString()
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
    private val docSymbolFactory = C_DocSymbolFactory.get(msgMgr, opts)

    private val nopSymCtx = C_SymbolContext(nameCtxMgr.nopNameCtx, docSymbolFactory)

    fun finish(): Finish {
        val symInfos = nameCtxMgr.finish()
        return Finish(symInfos)
    }

    class Finish(
        val symbolInfos: ImmMap<S_Pos, IdeSymbolInfo>,
    )

    private inner class C_SymbolContextProviderImpl: C_SymbolContextProvider {
        override fun getDocSymbolFactory() = docSymbolFactory
        override fun getNopSymbolContext() = nopSymCtx

        override fun getFileSymbolContext(path: C_SourcePath): C_SymbolContext {
            val nameCtx = if (path == mainFile) nameCtxMgr.activeNameCtx else nameCtxMgr.nopNameCtx
            return C_SymbolContext(nameCtx, docSymbolFactory)
        }
    }
}
