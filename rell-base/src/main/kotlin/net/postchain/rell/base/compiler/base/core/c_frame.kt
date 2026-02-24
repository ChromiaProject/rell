/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.core

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.expr.*
import net.postchain.rell.base.compiler.base.utils.*
import net.postchain.rell.base.compiler.vexpr.V_Expr
import net.postchain.rell.base.compiler.vexpr.V_LocalVarExpr
import net.postchain.rell.base.compiler.vexpr.V_SmartNullableExpr
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.*
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.ide.IdeCompletion
import kotlin.math.max

internal class C_LocalVarRef(
    val target: C_LocalVar,
    val ptr: R_VarPtr,
) {
    fun toRExpr(): R_DestinationExpr = R_VarExpr(target.type, ptr, target.metaName)
    fun toDbExpr(): Db_Expr = Db_InterpretedExpr(toRExpr())

    fun compile(ctx: C_ExprContext, pos: S_Pos): V_Expr {
        val vExpr = V_LocalVarExpr(ctx, pos, this)
        return V_SmartNullableExpr.wrap(ctx, vExpr, SMART_KIND)
    }

    companion object {
        private val SMART_KIND = C_CodeMsg("var", "Variable")
    }
}

internal class C_LocalVar(
    val metaName: String,
    val rName: R_Name?,
    val type: R_Type,
    val mutable: Boolean,
    val offset: Int,
    val uid: C_LocalVarUid,
    val atExprId: R_AtExprId?,
) {
    val varKey = C_VarStateKey(uid)

    fun toRef(blockUid: R_FrameBlockUid): C_LocalVarRef {
        val ptr = R_VarPtr(blockUid, offset)
        return C_LocalVarRef(this, ptr)
    }
}

internal class C_FrameContext private constructor(
    internal val fnCtx: C_FunctionContext,
    proto: C_CallFrameProto,
) {
    val msgCtx = fnCtx.msgCtx
    val appCtx = fnCtx.appCtx

    val ideCompCtx = C_IdeCompletionsContext(fnCtx.defCtx.mntCtx.fileCtx.path, fnCtx.globalCtx.compilerOptions)

    private val ownerRootBlkCtx = C_OwnerBlockContext.createRoot(this, proto.rootBlockScope)
    val rootBlkCtx: C_BlockContext = ownerRootBlkCtx

    private var callFrameSize = proto.size

    fun adjustCallFrameSize(size: Int) {
        check(size >= 0)
        callFrameSize = max(callFrameSize, size)
    }

    fun makeCallFrame(hasGuardBlock: Boolean): C_CallFrame {
        val rootBlock = ownerRootBlkCtx.buildBlock()
        val rFrame = R_CallFrame(fnCtx.defCtx.defId, callFrameSize, rootBlock.rBlock, hasGuardBlock)
        val proto = C_CallFrameProto(rFrame.size, rootBlock.scope)
        return C_CallFrame(rFrame, proto)
    }

    companion object {
        fun create(fnCtx: C_FunctionContext, proto: C_CallFrameProto = C_CallFrameProto.EMPTY): C_FrameContext {
            return C_FrameContext(fnCtx, proto)
        }
    }
}

internal class C_CallFrame(val rFrame: R_CallFrame, val proto: C_CallFrameProto)

internal class C_CallFrameProto(val size: Int, val rootBlockScope: C_BlockScope) {
    companion object { val EMPTY = C_CallFrameProto(0, C_BlockScope.EMPTY) }
}

internal class C_BlockScopeVar(val localVar: C_LocalVar, val ideInfo: C_IdeSymbolInfo)

internal class C_BlockScope(val localVars: ImmMap<R_Name, C_BlockScopeVar>) {
    companion object { val EMPTY = C_BlockScope(immMapOf()) }
}

internal sealed class C_BlockEntry {
    abstract fun toLocalVarOpt(): C_LocalVar?
    abstract fun ideSymbolInfo(): C_IdeSymbolInfo
    abstract fun compile(ctx: C_ExprContext, pos: S_Pos, ambiguous: Boolean): V_Expr

    fun ideCompletion(): IdeCompletion? {
        val ideInfo = ideSymbolInfo().getIdeInfo()
        val doc = ideInfo.doc
        doc ?: return null
        return C_IdeCompletionsUtils.makeIdeCompletion(doc)
    }

    companion object {
        fun ideCompletions(entries: List<Pair<String, C_BlockEntry>>): ImmMultimap<String, IdeCompletion> {
            return entries
                .mapNotNull { (name, entry) ->
                    val completion = entry.ideCompletion()
                    if (completion == null) null else (name to completion)
                }
                .toImmMultimap()
        }
    }
}

internal class C_BlockEntry_Var(
    private val localVar: C_LocalVar,
    private val ideInfo: C_IdeSymbolInfo,
): C_BlockEntry() {
    override fun toLocalVarOpt() = localVar
    override fun ideSymbolInfo() = ideInfo

    override fun compile(ctx: C_ExprContext, pos: S_Pos, ambiguous: Boolean): V_Expr {
        return compile0(ctx, pos, localVar)
    }

    companion object {
        fun compile0(ctx: C_ExprContext, pos: S_Pos, localVar: C_LocalVar): V_Expr {
            val varRef = localVar.toRef(ctx.blkCtx.blockUid)
            return varRef.compile(ctx, pos)
        }
    }
}

internal class C_BlockEntry_AtEntity(
    private val atEntity: C_AtEntity,
    private val ideInfo: C_IdeSymbolInfo,
    private val isOuter: Boolean,
): C_BlockEntry() {
    override fun toLocalVarOpt() = null
    override fun ideSymbolInfo() = ideInfo

    override fun compile(ctx: C_ExprContext, pos: S_Pos, ambiguous: Boolean): V_Expr {
        return atEntity.toVExpr(ctx, pos, isOuter, ambiguous)
    }
}

internal class C_BlockScopeBuilder(
    private val fnCtx: C_FunctionContext,
    startOffset: Int,
    private val ideParentCompletionsScopeProvider: C_IdeCompletionsScopeProvider,
    proto: C_BlockScope,
) {
    private val explicitEntries = mutableMapOf<R_Name, C_BlockEntry>()
    private val implicitEntries = mutableMultimapOf<R_Name, C_BlockEntry>()
    private var done = false

    private var endVarOffset: Int = let {
        var resOfs = startOffset
        for (entry in proto.localVars.values) {
            val offset = entry.localVar.offset
            check(offset >= startOffset)
            resOfs = max(resOfs, offset + 1)
        }
        resOfs
    }

    private var ideCompletionsScope: C_IdeCompletionsScope? = null
    private val ideCompletionsList = mutableListOf<Pair<String, C_BlockEntry>>()

    init {
        for ((name, scopeVar) in proto.localVars) {
            val entry = C_BlockEntry_Var(scopeVar.localVar, scopeVar.ideInfo)
            explicitEntries[name] = entry
            ideAddCompletion(name, entry)
        }
    }

    fun endVarOffset() = endVarOffset

    fun lookupVar(name: R_Name): C_LocalVar? {
        val entry = explicitEntries[name]
        return entry?.toLocalVarOpt()
    }

    fun lookupExplicit(name: R_Name): C_BlockEntry? {
        return explicitEntries[name]
    }

    fun lookupImplicit(name: R_Name): ImmList<C_BlockEntry> {
        return implicitEntries.get(name).toImmList()
    }

    fun newVar(
        metaName: String,
        name: R_Name?,
        type: R_Type,
        mutable: Boolean,
        atExprId: R_AtExprId?,
    ): C_LocalVar {
        check(!done)
        val ofs = endVarOffset++
        val varUid = fnCtx.nextVarUid(metaName)
        return C_LocalVar(metaName, name, type, mutable, ofs, varUid, atExprId)
    }

    fun addEntry(name: R_Name, explicit: Boolean, entry: C_BlockEntry) {
        check(!done)

        if (explicit) {
            if (name !in explicitEntries) {
                explicitEntries[name] = entry
            }
        } else {
            implicitEntries.put(name, entry)
        }

        ideAddCompletion(name, entry)
    }

    private fun ideAddCompletion(name: R_Name, entry: C_BlockEntry) {
        ideCompletionsList.add(name.str to entry)
        ideCompletionsScope = null
    }

    fun ideCompletionsScope(): C_IdeCompletionsScope {
        var scope = ideCompletionsScope
        if (scope == null) {
            val parentScope = ideParentCompletionsScopeProvider.ideCompletionsScope()
            val list = ideCompletionsList.toImmList()

            val late = C_LateInit(C_CompilerPass.COMPLETIONS, immMultimapOf<String, IdeCompletion>())
            fnCtx.executor.onPass(C_CompilerPass.COMPLETIONS) {
                late.set(C_BlockEntry.ideCompletions(list))
            }

            scope = C_IdeCompletionsScope(parentScope, late.getter)
            ideCompletionsScope = scope
        }
        return scope
    }

    fun build(): C_BlockScope {
        check(!done)
        done = true
        val variables = explicitEntries
                .mapNotNull {
                    val localVar = it.value.toLocalVarOpt()
                    val value = if (localVar == null) null else C_BlockScopeVar(localVar, it.value.ideSymbolInfo())
                    it.key to value
                }
                .filter { it.second != null }
                .associateToImmMap { it.first to it.second!! }
        return C_BlockScope(variables)
    }
}

internal sealed class C_BlockContext(
    val frameCtx: C_FrameContext,
    val blockUid: R_FrameBlockUid,
): C_IdeCompletionsScopeProvider {
    val appCtx = frameCtx.appCtx
    val fnCtx = frameCtx.fnCtx
    val defCtx = fnCtx.defCtx
    val nsCtx = defCtx.nsCtx

    abstract fun isTopLevelBlock(): Boolean
    abstract fun createSubContext(location: String, atFrom: C_AtFrom? = null): C_OwnerBlockContext

    abstract fun lookupEntry(name: R_Name): C_BlockEntryResolution?
    abstract fun lookupLocalVar(name: R_Name): C_LocalVarRef?
    abstract fun lookupAtPlaceholder(): C_BlockEntryResolution?
    abstract fun lookupAtMembers(ctx: C_ExprContext, name: C_Name): ImmList<C_AtContextMember>
    abstract fun lookupAtImplicitAttributesByName(ctx: C_ExprContext, name: C_Name): ImmList<C_AtFromImplicitAttr>
    abstract fun lookupAtImplicitAttributesByType(ctx: C_ExprContext, pos: S_Pos, type: R_Type): ImmList<C_AtFromImplicitAttr>

    abstract fun addEntry(
        pos: S_Pos,
        name: R_Name,
        explicit: Boolean,
        entry: C_BlockEntry,
        errOnNameConflict: Boolean = true,
    )

    abstract fun addAtPlaceholder(entry: C_BlockEntry)

    abstract fun addLocalVar(
        name: C_Name,
        type: R_Type,
        mutable: Boolean,
        atExprId: R_AtExprId?,
        ideInfo: C_IdeSymbolInfo,
    ): C_LocalVarRef

    abstract fun newLocalVar(
        metaName: String,
        name: R_Name?,
        type: R_Type,
        mutable: Boolean,
        atExprId: R_AtExprId?,
    ): C_LocalVar
}

internal class C_FrameBlock(val rBlock: R_FrameBlock, val scope: C_BlockScope)

internal sealed class C_BlockEntryResolution {
    abstract fun ideSymbolInfo(): C_IdeSymbolInfo
    abstract fun compile(ctx: C_ExprContext, pos: S_Pos): V_Expr
}

private class C_BlockEntryResolution_Normal(private val entry: C_BlockEntry): C_BlockEntryResolution() {
    override fun ideSymbolInfo() = entry.ideSymbolInfo()

    override fun compile(ctx: C_ExprContext, pos: S_Pos): V_Expr {
        return entry.compile(ctx, pos, false)
    }
}

private class C_BlockEntryResolution_OuterPlaceholder(private val entry: C_BlockEntry): C_BlockEntryResolution() {
    override fun ideSymbolInfo() = entry.ideSymbolInfo()

    override fun compile(ctx: C_ExprContext, pos: S_Pos): V_Expr {
        ctx.msgCtx.error(pos, "at_expr:placeholder:belongs_to_outer",
                "Cannot use a placeholder to access an outer at-expression; use explicit alias")
        return entry.compile(ctx, pos, false)
    }
}

private class C_BlockEntryResolution_Ambiguous(
        private val symbol: C_Symbol,
        private val entry: C_BlockEntry
): C_BlockEntryResolution() {
    override fun ideSymbolInfo() = entry.ideSymbolInfo()

    override fun compile(ctx: C_ExprContext, pos: S_Pos): V_Expr {
        ctx.msgCtx.error(pos, "name:ambiguous:${symbol.code}", "${symbol.msgCapital()} is ambiguous")
        return entry.compile(ctx, pos, true)
    }
}

internal class C_OwnerBlockContext(
    frameCtx: C_FrameContext,
    blockUid: R_FrameBlockUid,
    private val parent: C_OwnerBlockContext?,
    atFrom: C_AtFrom?,
    protoBlockScope: C_BlockScope,
): C_BlockContext(frameCtx, blockUid) {
    private val startVarOffset: Int = parent?.scopeBuilder?.endVarOffset() ?: 0

    private val scopeBuilder: C_BlockScopeBuilder = C_BlockScopeBuilder(
        fnCtx,
        startVarOffset,
        parent ?: frameCtx.fnCtx.nsCtx,
        protoBlockScope,
    )

    private val atFromBlock: C_AtFromBlock? = let {
        val parentBlock = parent?.atFromBlock
        if (atFrom == null) parentBlock else C_AtFromBlock(parentBlock, atFrom)
    }

    private val atPlaceholders = mutableListOf<C_BlockEntry>()
    private var build = false

    override fun isTopLevelBlock() = parent?.parent == null

    override fun createSubContext(location: String, atFrom: C_AtFrom?): C_OwnerBlockContext {
        check(!build) { "Block has been built: $blockUid" }
        val blockUid = fnCtx.nextBlockUid()
        return C_OwnerBlockContext(frameCtx, blockUid, this, atFrom, C_BlockScope.EMPTY)
    }

    override fun lookupLocalVar(name: R_Name): C_LocalVarRef? {
        val localVar = findValue { it.scopeBuilder.lookupVar(name) }
        return localVar?.toRef(blockUid)
    }

    override fun lookupEntry(name: R_Name): C_BlockEntryResolution? {
        val explicit = findValue { it.scopeBuilder.lookupExplicit(name) }
        val implicit = findAllValues { it.scopeBuilder.lookupImplicit(name) }
        val entries = listOfNotNull(explicit) + implicit

        return if (entries.isEmpty()) null else {
            val entry = entries.first()
            return if (entries.size == 1) {
                C_BlockEntryResolution_Normal(entry)
            } else {
                val sym = C_Symbol_Name(name)
                C_BlockEntryResolution_Ambiguous(sym, entry)
            }
        }
    }

    override fun lookupAtPlaceholder(): C_BlockEntryResolution? {
        val entries = findAllValues { blkCtx -> blkCtx.atPlaceholders.map { it to blkCtx } }
        if (entries.isEmpty()) {
            return null
        }

        val thisBlockEntries = entries.filter { (_, blkCtx) -> blkCtx.atFromBlock == atFromBlock }

        return if (thisBlockEntries.size == 1) {
            val (entry, _) = thisBlockEntries.first()
            C_BlockEntryResolution_Normal(entry)
        } else if (thisBlockEntries.size > 1) {
            val (entry, _) = thisBlockEntries.first()
            C_BlockEntryResolution_Ambiguous(C_Symbol_Placeholder, entry)
        } else {
            val (entry, _) = entries.first()
            C_BlockEntryResolution_OuterPlaceholder(entry)
        }
    }

    override fun lookupAtMembers(ctx: C_ExprContext, name: C_Name): ImmList<C_AtContextMember> {
        var block = atFromBlock

        val mems = mutableListOf<C_AtContextMember>()

        while (block != null) {
            val blockMems = block.from.findMembers(ctx, name)
            mems.addAll(blockMems.map { C_AtContextMember(it, block !== atFromBlock) })

            block = when (appCtx.globalCtx.compilerOptions.atAttrShadowing) {
                C_AtAttrShadowing.NONE -> block.parent
                C_AtAttrShadowing.FULL -> if (mems.isNotEmpty()) null else block.parent
                C_AtAttrShadowing.PARTIAL -> if (mems.isNotEmpty() && block.from.innerAtCtx.parent == null) null else block.parent
            }
        }

        return mems.toImmList()
    }

    override fun lookupAtImplicitAttributesByName(ctx: C_ExprContext, name: C_Name): ImmList<C_AtFromImplicitAttr> {
        // Not looking in outer contexts, because for implicit matching only the direct at-expr is considered.
        return atFromBlock?.from?.findImplicitAttributesByName(ctx, name).orEmpty()
    }

    override fun lookupAtImplicitAttributesByType(ctx: C_ExprContext, pos: S_Pos, type: R_Type): ImmList<C_AtFromImplicitAttr> {
        // Not looking in outer contexts, because for implicit matching only the direct at-expr is considered.
        return atFromBlock?.from?.findImplicitAttributesByType(ctx, pos, type).orEmpty()
    }

    override fun addEntry(
        pos: S_Pos,
        name: R_Name,
        explicit: Boolean,
        entry: C_BlockEntry,
        errOnNameConflict: Boolean,
    ) {
        if (explicit && !checkNameConflict(pos, name, errOnNameConflict)) {
            return
        }
        scopeBuilder.addEntry(name, explicit, entry)
    }

    override fun addLocalVar(
        name: C_Name,
        type: R_Type,
        mutable: Boolean,
        atExprId: R_AtExprId?,
        ideInfo: C_IdeSymbolInfo,
    ): C_LocalVarRef {
        val localVar = scopeBuilder.newVar(name.str, name.rName, type, mutable, atExprId)
        if (checkNameConflict(name.pos, name.rName)) {
            scopeBuilder.addEntry(name.rName, true, C_BlockEntry_Var(localVar, ideInfo))
        }
        return localVar.toRef(blockUid)
    }

    private fun checkNameConflict(pos: S_Pos, name: R_Name, errOnNameConflict: Boolean = true): Boolean {
        val entry = findValue { it.scopeBuilder.lookupExplicit(name) }
        if (entry != null && errOnNameConflict) {
            frameCtx.msgCtx.error(pos, "block:name_conflict:$name", "Name conflict: '$name' already exists")
        }
        return entry == null
    }

    override fun addAtPlaceholder(entry: C_BlockEntry) {
        atPlaceholders.add(entry)
    }

    override fun newLocalVar(
        metaName: String,
        name: R_Name?,
        type: R_Type,
        mutable: Boolean,
        atExprId: R_AtExprId?,
    ): C_LocalVar {
        return scopeBuilder.newVar(metaName, name, type, mutable, atExprId)
    }

    private fun <T> findValue(getter: (C_OwnerBlockContext) -> T?): T? {
        var ctx: C_OwnerBlockContext? = this
        while (ctx != null) {
            val value = getter(ctx)
            if (value != null) {
                return value
            }
            ctx = ctx.parent
        }
        return null
    }

    private fun <T> findAllValues(getter: (C_OwnerBlockContext) -> List<T>): ImmList<T> {
        var ctx: C_OwnerBlockContext? = this
        val res = mutableListOf<T>()
        while (ctx != null) {
            val values = getter(ctx)
            res.addAll(values)
            ctx = ctx.parent
        }
        return res.toImmList()
    }

    override fun ideCompletionsScope(): C_IdeCompletionsScope {
        val baseScope = scopeBuilder.ideCompletionsScope()

        val late = C_LateInit(C_CompilerPass.COMPLETIONS, immMultimapOf<String, IdeCompletion>())

        frameCtx.appCtx.executor.onPass(C_CompilerPass.COMPLETIONS) {
            val entries = atPlaceholders.map { C_Constants.AT_PLACEHOLDER to it }
            val entryMap = C_BlockEntry.ideCompletions(entries)
            val memberMap = ideCompletionsAtMembers()

            val res = entryMap.toMutableMultimap()
            res.putAll(memberMap)
            late.set(res.toImmMultimap())
        }

        return C_IdeCompletionsScope(baseScope, late.getter)
    }

    private fun ideCompletionsAtMembers(): ImmMultimap<String, IdeCompletion> {
        val res = mutableMultimapOf<String, IdeCompletion>()

        var block = atFromBlock
        while (block != null) {
            res.putAll(block.from.ideCompletions())
            block = block.parent
        }

        return res.toImmMultimap()
    }

    fun buildBlock(): C_FrameBlock {
        check(!build)
        build = true
        val scope = scopeBuilder.build()
        val endVarOffset = scopeBuilder.endVarOffset()
        frameCtx.adjustCallFrameSize(endVarOffset + 1)
        val size = endVarOffset - startVarOffset
        val rBlock = R_FrameBlock(parent?.blockUid, blockUid, startVarOffset, size)
        return C_FrameBlock(rBlock, scope)
    }

    private class C_AtFromBlock(val parent: C_AtFromBlock?, val from: C_AtFrom)

    companion object {
        fun createRoot(frameCtx: C_FrameContext, protoScope: C_BlockScope): C_OwnerBlockContext {
            val fnCtx = frameCtx.fnCtx
            val blockUid = fnCtx.nextBlockUid()
            return C_OwnerBlockContext(frameCtx, blockUid, null, null, protoScope)
        }
    }
}

internal class C_LambdaBlock(
    val rLambda: R_LambdaBlock,
    private val exprCtx: C_ExprContext,
    private val localVar: C_LocalVar,
    private val blockUid: R_FrameBlockUid,
) {
    fun compileVarRExpr(blockUid: R_FrameBlockUid = this.blockUid): R_Expr {
        val varRef = localVar.toRef(blockUid)
        return varRef.toRExpr()
    }

    fun compileVarDbExpr(blockUid: R_FrameBlockUid = this.blockUid): Db_Expr {
        val rVarExpr = compileVarRExpr(blockUid)
        return Db_InterpretedExpr(rVarExpr)
    }

    fun compileVarExpr(pos: S_Pos, blockUid: R_FrameBlockUid = this.blockUid): V_Expr {
        val varRef = localVar.toRef(blockUid)
        return V_LocalVarExpr(exprCtx, pos, varRef)
    }

    companion object {
        fun builder(ctx: C_ExprContext, varType: R_Type) = C_LambdaBlockBuilder(ctx, varType)
    }
}

internal class C_LambdaBlockBuilder(ctx: C_ExprContext, private val varType: R_Type) {
    val innerBlkCtx = ctx.blkCtx.createSubContext("<lambda>")
    val innerExprCtx = ctx.copy(blkCtx = innerBlkCtx)

    private val localVar = innerBlkCtx.newLocalVar("<lambda>", null, varType, false, null)

    fun build(): C_LambdaBlock {
        val cBlock = innerBlkCtx.buildBlock()
        val varRef = localVar.toRef(innerBlkCtx.blockUid)
        val rLambda = R_LambdaBlock(cBlock.rBlock, varRef.ptr, varType)
        return C_LambdaBlock(rLambda, innerExprCtx, localVar, innerBlkCtx.blockUid)
    }
}
