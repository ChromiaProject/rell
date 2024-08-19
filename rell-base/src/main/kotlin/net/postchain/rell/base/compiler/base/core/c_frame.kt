/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.core

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.expr.*
import net.postchain.rell.base.compiler.base.utils.C_CodeMsg
import net.postchain.rell.base.compiler.base.utils.C_Symbol
import net.postchain.rell.base.compiler.base.utils.C_Symbol_Name
import net.postchain.rell.base.compiler.base.utils.C_Symbol_Placeholder
import net.postchain.rell.base.compiler.vexpr.V_Expr
import net.postchain.rell.base.compiler.vexpr.V_LocalVarExpr
import net.postchain.rell.base.compiler.vexpr.V_SmartNullableExpr
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.*
import net.postchain.rell.base.utils.*
import kotlin.math.max

class C_LocalVarRef(
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

class C_LocalVar(
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
        val ptr = R_VarPtr(metaName, blockUid, offset)
        return C_LocalVarRef(this, ptr)
    }
}

class C_FrameContext private constructor(val fnCtx: C_FunctionContext, proto: C_CallFrameProto) {
    val msgCtx = fnCtx.msgCtx
    val appCtx = fnCtx.appCtx

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

class C_CallFrame(val rFrame: R_CallFrame, val proto: C_CallFrameProto)

class C_CallFrameProto(val size: Int, val rootBlockScope: C_BlockScope) {
    companion object { val EMPTY = C_CallFrameProto(0, C_BlockScope.EMPTY) }
}

class C_BlockScopeVar(val localVar: C_LocalVar, val ideInfo: C_IdeSymbolInfo)

class C_BlockScope(variables: Map<R_Name, C_BlockScopeVar>) {
    val localVars = variables.toImmMap()

    companion object { val EMPTY = C_BlockScope(immMapOf()) }
}

sealed class C_BlockEntry {
    abstract fun toLocalVarOpt(): C_LocalVar?
    abstract fun ideSymbolInfo(): C_IdeSymbolInfo
    abstract fun compile(ctx: C_ExprContext, pos: S_Pos, ambiguous: Boolean): V_Expr
}

class C_BlockEntry_Var(
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

class C_BlockEntry_AtEntity(
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

class C_BlockScopeBuilder(
    private val fnCtx: C_FunctionContext,
    startOffset: Int,
    proto: C_BlockScope,
) {
    private val explicitEntries = mutableMapOf<R_Name, C_BlockEntry>()
    private val implicitEntries = mutableMultimapOf<R_Name, C_BlockEntry>()
    private var done = false

    private var endOffset: Int = let {
        var resOfs = startOffset
        for (entry in proto.localVars.values) {
            val offset = entry.localVar.offset
            check(offset >= startOffset)
            resOfs = max(resOfs, offset + 1)
        }
        resOfs
    }

    init {
        explicitEntries.putAll(proto.localVars.mapValues { C_BlockEntry_Var(it.value.localVar, it.value.ideInfo) })
    }

    fun endOffset() = endOffset

    fun lookupVar(name: R_Name): C_LocalVar? {
        val entry = explicitEntries[name]
        return entry?.toLocalVarOpt()
    }

    fun lookupExplicit(name: R_Name): C_BlockEntry? {
        return explicitEntries[name]
    }

    fun lookupImplicit(name: R_Name): List<C_BlockEntry> {
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
        val ofs = endOffset++
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
                .associate { it.first to it.second!! }
        return C_BlockScope(variables)
    }
}

sealed class C_BlockContext(val frameCtx: C_FrameContext, val blockUid: R_FrameBlockUid) {
    val appCtx = frameCtx.appCtx
    val fnCtx = frameCtx.fnCtx
    val defCtx = fnCtx.defCtx
    val nsCtx = defCtx.nsCtx

    abstract fun isTopLevelBlock(): Boolean
    abstract fun createSubContext(location: String, atFrom: C_AtFrom? = null): C_OwnerBlockContext

    abstract fun lookupEntry(name: R_Name): C_BlockEntryResolution?
    abstract fun lookupLocalVar(name: R_Name): C_LocalVarRef?
    abstract fun lookupAtPlaceholder(): C_BlockEntryResolution?
    abstract fun lookupAtMembers(ctx: C_ExprContext, name: C_Name): List<C_AtContextMember>
    abstract fun lookupAtImplicitAttributesByName(ctx: C_ExprContext, name: C_Name): List<C_AtFromImplicitAttr>
    abstract fun lookupAtImplicitAttributesByType(ctx: C_ExprContext, pos: S_Pos, type: R_Type): List<C_AtFromImplicitAttr>

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
            atExprId: R_AtExprId?
    ): C_LocalVar
}

class C_FrameBlock(val rBlock: R_FrameBlock, val scope: C_BlockScope)

sealed class C_BlockEntryResolution {
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

class C_OwnerBlockContext(
    frameCtx: C_FrameContext,
    blockUid: R_FrameBlockUid,
    private val parent: C_OwnerBlockContext?,
    atFrom: C_AtFrom?,
    protoBlockScope: C_BlockScope,
): C_BlockContext(frameCtx, blockUid) {
    private val startOffset: Int = parent?.scopeBuilder?.endOffset() ?: 0
    private val scopeBuilder: C_BlockScopeBuilder = C_BlockScopeBuilder(fnCtx, startOffset, protoBlockScope)
    private val atFromBlock: C_AtFromBlock? = if (atFrom == null) parent?.atFromBlock else C_AtFromBlock(parent?.atFromBlock, atFrom)
    private val atPlaceholders = mutableListOf<C_BlockEntry>()
    private var build = false

    override fun isTopLevelBlock() = parent?.parent == null

    override fun createSubContext(location: String, atFrom: C_AtFrom?): C_OwnerBlockContext {
        check(!build) { "Block has been built: $blockUid" }
        val blockUid = fnCtx.nextBlockUid(location)
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

    override fun lookupAtMembers(ctx: C_ExprContext, name: C_Name): List<C_AtContextMember> {
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

    override fun lookupAtImplicitAttributesByName(ctx: C_ExprContext, name: C_Name): List<C_AtFromImplicitAttr> {
        // Not looking in outer contexts, because for implicit matching only the direct at-expr is considered.
        return atFromBlock?.from?.findImplicitAttributesByName(ctx, name) ?: immListOf()
    }

    override fun lookupAtImplicitAttributesByType(ctx: C_ExprContext, pos: S_Pos, type: R_Type): List<C_AtFromImplicitAttr> {
        // Not looking in outer contexts, because for implicit matching only the direct at-expr is considered.
        return atFromBlock?.from?.findImplicitAttributesByType(ctx, pos, type) ?: immListOf()
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

    private fun <T: Any> findAllValues(getter: (C_OwnerBlockContext) -> List<T>): List<T> {
        var ctx: C_OwnerBlockContext? = this
        val res = mutableListOf<T>()
        while (ctx != null) {
            val values = getter(ctx)
            res.addAll(values)
            ctx = ctx.parent
        }
        return res.toImmList()
    }

    fun buildBlock(): C_FrameBlock {
        check(!build)
        build = true
        val scope = scopeBuilder.build()
        val endOffset = scopeBuilder.endOffset()
        frameCtx.adjustCallFrameSize(endOffset + 1)
        val size = endOffset - startOffset
        val rBlock = R_FrameBlock(parent?.blockUid, blockUid, startOffset, size)
        return C_FrameBlock(rBlock, scope)
    }

    private class C_AtFromBlock(val parent: C_AtFromBlock?, val from: C_AtFrom)

    companion object {
        fun createRoot(frameCtx: C_FrameContext, protoScope: C_BlockScope): C_OwnerBlockContext {
            val fnCtx = frameCtx.fnCtx
            val name = fnCtx.fnUid.name
            val blockUid = fnCtx.nextBlockUid("root:$name")
            return C_OwnerBlockContext(frameCtx, blockUid, null, null, protoScope)
        }
    }
}

class C_LambdaBlock(
        val rLambda: R_LambdaBlock,
        private val exprCtx: C_ExprContext,
        private val localVar: C_LocalVar,
        private val blockUid: R_FrameBlockUid
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

class C_LambdaBlockBuilder(ctx: C_ExprContext, private val varType: R_Type) {
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
