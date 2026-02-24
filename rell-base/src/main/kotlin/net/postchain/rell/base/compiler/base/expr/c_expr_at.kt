/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.expr

import com.google.common.collect.Multimap
import net.postchain.rell.base.compiler.ast.S_Comment
import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.ast.S_PosValue
import net.postchain.rell.base.compiler.base.core.*
import net.postchain.rell.base.compiler.base.modifier.C_AtSummarizationKind
import net.postchain.rell.base.compiler.base.utils.C_CodeMsg
import net.postchain.rell.base.compiler.base.utils.C_Utils
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.compiler.vexpr.*
import net.postchain.rell.base.lib.type.*
import net.postchain.rell.base.model.R_AtExprId
import net.postchain.rell.base.model.R_FrameBlock
import net.postchain.rell.base.model.R_NullableType
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.expr.*
import net.postchain.rell.base.model.stmt.R_IterableAdapter
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.ide.IdeCompletion

class C_AtContext(
    val parent: C_AtContext?,
    val atExprId: R_AtExprId,
    val dbAt: Boolean,
)

class C_AtFromContext(val pos: S_Pos, val atExprId: R_AtExprId, val parentAtCtx: C_AtContext?)

class C_AtFromItemContext(
    val fromCtx: C_AtFromContext,
    val isJoin: Boolean,
    val outerJoinPos: S_Pos?,
    val atExprAllowed: Boolean,
    val comment: S_Comment?,
) {
    val isOuterJoin = outerJoinPos != null

    init {
        if (outerJoinPos != null) {
            require(isJoin)
        }
    }
}

internal abstract class C_AtFromBase {
    abstract fun nameMsg(): C_CodeMsg
    abstract fun compile(ctx: C_ExprContext, pos: S_Pos): V_Expr
}

internal class C_AtFromMember(
    private val base: C_AtFromBase,
    private val selfType: R_Type,
    private val member: C_TypeValueMember,
    private val safe: Boolean,
) {
    fun nameMsg(): String = member.nameMsg().msg
    fun ownerMsg(): C_CodeMsg = base.nameMsg()
    fun isValue() = member.isValue()
    fun isCallable() = member.isCallable()

    fun compile(ctx: C_ExprContext, cNameHand: C_NameHandle): C_Expr {
        val baseExpr = base.compile(ctx, cNameHand.pos)
        val actualSafe = safe && baseExpr.type is R_NullableType
        val link = C_MemberLink(baseExpr, selfType, cNameHand.pos, cNameHand.name, actualSafe)
        return member.compile(ctx, link, cNameHand, null)
    }
}

internal abstract class C_AtFrom(
    protected val outerExprCtx: C_ExprContext,
    fromCtx: C_AtFromContext,
    protected val fromBlock: R_FrameBlock?,
) {
    val atExprId = fromCtx.atExprId

    protected val parentAtCtx = fromCtx.parentAtCtx
    protected val innerBlkCtx = outerExprCtx.blkCtx.createSubContext("@", atFrom = this)

    protected val msgCtx = outerExprCtx.msgCtx

    val innerAtCtx = C_AtContext(fromCtx.parentAtCtx, atExprId, this is C_AtFrom_Entities)

    abstract fun getAllExprs(): ImmList<V_Expr>

    abstract fun innerExprCtx(): C_ExprContext
    abstract fun makeDefaultWhatFields(ctx: C_ExprContext): ImmList<V_DbAtWhatField>
    abstract fun findMembers(ctx: C_ExprContext, name: C_Name): ImmList<C_AtFromMember>
    abstract fun findImplicitAttributesByName(ctx: C_ExprContext, name: C_Name): ImmList<C_AtFromImplicitAttr>
    abstract fun findImplicitAttributesByType(ctx: C_ExprContext, pos: S_Pos, type: R_Type): ImmList<C_AtFromImplicitAttr>
    abstract fun ideCompletions(): ImmMultimap<String, IdeCompletion>

    abstract fun compile(details: C_AtDetails): V_Expr
    abstract fun compileJoin(details: C_AtDetails, isOuter: Boolean): C_AtFromItem

    protected fun compileColWhat(details: C_AtDetails, what: List<V_DbAtWhatField>): V_ColAtWhat {
        val colFields = what.mapToImmList { it.toColField() }
        val sorting = compileColSorting(what)

        val extras = R_ColAtWhatExtras(
            colFields.size,
            details.res.selectedFields,
            details.res.groupFields,
            sorting,
            details.res.rowDecoder,
        )

        return V_ColAtWhat(colFields, extras)
    }

    private fun compileColSorting(cFields: List<V_DbAtWhatField>): ImmList<IndexedValue<Comparator<Rt_Value>>> {
        val sorting = cFields
                .withIndex()
                .mapNotNullToImmList { (i, f) ->
                    val sort = f.flags.sort
                    if (sort == null) null else {
                        val type = f.resultType
                        var comparator = type.comparator()
                        if (comparator != null && !sort.value.asc) comparator = comparator.reversed()
                        if (comparator != null) IndexedValue(i, comparator) else {
                            outerExprCtx.msgCtx.error(sort.pos, "at:expr:sort:type:${type.strCode()}",
                                    "Type ${type.str()} is not sortable")
                            null
                        }
                    }
                }
        return sorting
    }
}

sealed class C_AtFromItem(
    val pos: S_Pos,
    val aliasIdeDef: C_IdeSymbolDef,
)

internal sealed class C_AtFromItem_Entity(
    pos: S_Pos,
    val atEntity: C_AtEntity,
): C_AtFromItem(pos, atEntity.aliasIdeDef) {
    abstract fun isOuter(): Boolean
    abstract fun getExprs(): List<V_Expr>
    internal abstract fun compile(): V_DbAtFromItem

    internal abstract fun compileJoin(
        msgCtx: C_MessageContext,
        isOuter: Boolean,
        where: V_Expr?,
        block: R_FrameBlock,
    ): C_AtFromItem_Entity
}

internal class C_AtFromItem_Entity_Simple(
    pos: S_Pos,
    atEntity: C_AtEntity,
): C_AtFromItem_Entity(pos, atEntity) {
    override fun isOuter() = false
    override fun getExprs() = immListOf<V_Expr>()

    override fun compile(): V_DbAtFromItem {
        val rAtEntity = atEntity.toRAtEntity()
        return V_DbAtFromItem(rAtEntity, false, null, null)
    }

    override fun compileJoin(
        msgCtx: C_MessageContext,
        isOuter: Boolean,
        where: V_Expr?,
        block: R_FrameBlock,
    ): C_AtFromItem_Entity {
        return C_AtFromItem_Entity_Join(atEntity.declPos, atEntity, isOuter, where, block)
    }
}

internal class C_AtFromItem_Entity_Join(
    pos: S_Pos,
    atEntity: C_AtEntity,
    private val isOuter: Boolean,
    private val where: V_Expr?,
    private val block: R_FrameBlock?,
): C_AtFromItem_Entity(pos, atEntity) {
    override fun isOuter() = isOuter
    override fun getExprs() = listOfNotNull(where)

    override fun compile(): V_DbAtFromItem {
        val rAtEntity = atEntity.toRAtEntity()
        return V_DbAtFromItem(rAtEntity, isOuter, where, block)
    }

    override fun compileJoin(
        msgCtx: C_MessageContext,
        isOuter: Boolean,
        where: V_Expr?,
        block: R_FrameBlock,
    ): C_AtFromItem_Entity {
        // Must not happen, but handling the error anyway.
        msgCtx.error(pos, "expr:at:from:join_as_join", "Cannot use this expression as a join")
        return this
    }
}

internal class C_AtFromItem_Iterable(
    pos: S_Pos,
    aliasIdeDef: C_IdeSymbolDef,
    val alias: C_Name?,
    val vExpr: V_Expr,
    val elemType: R_Type,
    private val rIterableAdapter: R_IterableAdapter,
): C_AtFromItem(pos, aliasIdeDef) {
    fun compile(fromBlock: R_FrameBlock?): V_ColAtFrom {
        return V_ColAtFrom(rIterableAdapter, vExpr, fromBlock)
    }
}

internal class C_AtWhat(
    val allFields: ImmList<V_DbAtWhatField>,
    private val explicitPos: S_Pos?,
) {
    fun getMaterialFields() = allFields.filterToImmList { !it.isIgnored() }

    fun compileJoin(msgCtx: C_MessageContext) {
        if (explicitPos != null) {
            msgCtx.error(explicitPos, "expr:at:join:explicit_what", "Join at-expression cannot have a what-part")
        }
    }
}

internal class C_AtExprBase(
    val what: C_AtWhat,
    val where: V_Expr?,
) {
    fun compileJoin(msgCtx: C_MessageContext): V_Expr? {
        what.compileJoin(msgCtx)
        return where
    }
}

class C_AtExprResult(
    val recordType: R_Type,
    val resultType: R_Type,
    val rowDecoder: R_AtExprRowDecoder,
    val selectedFields: ImmList<Int>,
    val groupFields: ImmList<Int>,
    val hasAggregateFields: Boolean,
) {
    companion object {
        fun calcResultType(recordType: R_Type, cardinality: R_AtCardinality): R_Type {
            return if (cardinality.many) {
                R_ListType(recordType)
            } else if (cardinality.zero) {
                C_Types.toNullable(recordType)
            } else {
                recordType
            }
        }
    }
}

internal class C_AtDetails(
    val startPos: S_Pos,
    val cardinality: S_PosValue<R_AtCardinality>,
    val base: C_AtExprBase,
    val limit: V_Expr?,
    val offset: V_Expr?,
    val res: C_AtExprResult,
    val varStatesDelta: C_ExprVarStatesDelta,
) {
    fun compileJoin(msgCtx: C_MessageContext): V_Expr? {
        val where = base.compileJoin(msgCtx)

        if (cardinality.value != R_AtCardinality.ZERO_MANY) {
            msgCtx.error(cardinality.pos, "expr:at:join:cardinality:${cardinality.value}",
                "Join at-expression must use operator '${R_AtCardinality.ZERO_MANY.code}'")
        }

        checkExtra(msgCtx, limit, "limit")
        checkExtra(msgCtx, offset, "offset")
        return where
    }

    private fun checkExtra(msgCtx: C_MessageContext, expr: V_Expr?, kind: String) {
        if (expr != null) {
            msgCtx.error(expr.pos, "expr:at:join:extra:$kind", "Join at-expression cannot have $kind")
        }
    }
}

internal class C_AtSummarizationPos(val exprPos: S_Pos, val ann: C_AtSummarizationKind)

internal sealed class C_AtSummarization(
    protected val pos: C_AtSummarizationPos,
    protected val valueType: R_Type,
) {
    abstract fun isGroup(): Boolean
    open fun isCollectionAggregation(): Boolean = false
    abstract fun getResultType(hasGroup: Boolean): R_Type
    internal abstract fun compileR(appCtx: C_AppContext): R_ColAtFieldSummarization
    internal abstract fun compileDb(appCtx: C_AppContext, dbExpr: Db_Expr): Db_Expr

    companion object {
        fun typeError(msgCtx: C_MessageContext, type: R_Type, pos: C_AtSummarizationPos) {
            val code = "at:what:aggr:bad_type:${pos.ann}:${type.strCode()}"
            val msg = "Invalid type of @${pos.ann.annotation} expression: ${type.strCode()}"
            msgCtx.error(pos.exprPos, code, msg)
        }
    }
}

internal class C_AtSummarization_Group(pos: C_AtSummarizationPos, valueType: R_Type): C_AtSummarization(pos, valueType) {
    override fun isGroup() = true
    override fun getResultType(hasGroup: Boolean) = valueType

    override fun compileR(appCtx: C_AppContext): R_ColAtFieldSummarization {
        C_Utils.checkGroupValueType(appCtx, pos.exprPos, valueType)
        return R_ColAtFieldSummarization_Group()
    }

    override fun compileDb(appCtx: C_AppContext, dbExpr: Db_Expr) = dbExpr
}

internal sealed class C_AtSummarization_Aggregate(
    pos: C_AtSummarizationPos,
    valueType: R_Type,
): C_AtSummarization(pos, valueType) {
    protected abstract fun compileDb0(msgCtx: C_MessageContext): Db_SysFunction?

    final override fun isGroup() = false

    final override fun compileDb(appCtx: C_AppContext, dbExpr: Db_Expr): Db_Expr {
        val dbFn = compileDb0(appCtx.msgCtx)
        dbFn ?: return dbExpr
        return Db_CallExpr(dbExpr.type, dbFn, immListOf(dbExpr))
    }
}

internal class C_AtSummarization_Aggregate_Sum(
    pos: C_AtSummarizationPos,
    valueType: R_Type,
    private val rOp: R_BinaryOp,
    private val zeroValue: Rt_Value
): C_AtSummarization_Aggregate(pos, valueType) {
    override fun getResultType(hasGroup: Boolean) = valueType
    override fun compileR(appCtx: C_AppContext) = R_ColAtFieldSummarization_Aggregate_Sum(rOp, zeroValue)
    override fun compileDb0(msgCtx: C_MessageContext) = Db_SysFn_Aggregation.Sum
}

internal class C_AtSummarization_Aggregate_MinMax(
    pos: C_AtSummarizationPos,
    valueType: R_Type,
    private val rCmpOp: R_CmpOp,
    private val rCmpType: R_CmpType?,
    private val rComparator: Comparator<Rt_Value>?,
    private val dbFn: Db_SysFunction,
): C_AtSummarization_Aggregate(pos, valueType) {
    override fun getResultType(hasGroup: Boolean): R_Type {
        return if (hasGroup) valueType else C_Types.toNullable(valueType)
    }

    override fun compileR(appCtx: C_AppContext): R_ColAtFieldSummarization {
        return if (rComparator == null) {
            typeError(appCtx.msgCtx, valueType, pos)
            R_ColAtFieldSummarization_None
        } else {
            R_ColAtFieldSummarization_Aggregate_MinMax(rCmpOp, rComparator)
        }
    }

    override fun compileDb0(msgCtx: C_MessageContext): Db_SysFunction? {
        // Postgres doesn't support MIN/MAX for BOOLEAN and BYTEA.
        return if (rCmpType == null || valueType == R_BooleanType || valueType == R_ByteArrayType) {
            typeError(msgCtx, valueType, pos)
            null
        } else {
            dbFn
        }
    }
}

internal abstract class C_AtSummarization_Aggregate_Collection(
    pos: C_AtSummarizationPos,
    valueType: R_Type,
): C_AtSummarization_Aggregate(pos, valueType) {
    final override fun isCollectionAggregation() = true

    final override fun compileDb0(msgCtx: C_MessageContext): Db_SysFunction? {
        val code = "at:what:aggr:collection_db:${pos.ann}:${valueType.strCode()}"
        val msg = "Annotation @${pos.ann.annotation} not supported in a database expression"
        msgCtx.error(pos.exprPos, code, msg)
        return null
    }
}

internal class C_AtSummarization_Aggregate_List(
    pos: C_AtSummarizationPos,
    valueType: R_Type,
): C_AtSummarization_Aggregate_Collection(pos, valueType) {
    private val listType = R_ListType(valueType)

    override fun getResultType(hasGroup: Boolean): R_Type = listType
    override fun compileR(appCtx: C_AppContext) = R_ColAtFieldSummarization_Aggregate_List(listType)
}

internal class C_AtSummarization_Aggregate_Set(
    pos: C_AtSummarizationPos,
    valueType: R_Type,
): C_AtSummarization_Aggregate_Collection(pos, valueType) {
    private val setType = R_SetType(valueType)

    override fun getResultType(hasGroup: Boolean): R_Type = setType
    override fun compileR(appCtx: C_AppContext) = R_ColAtFieldSummarization_Aggregate_Set(setType)
}

internal class C_AtSummarization_Aggregate_Map(
    pos: C_AtSummarizationPos,
    valueType: R_Type,
    private val mapType: R_MapType,
): C_AtSummarization_Aggregate_Collection(pos, valueType) {
    override fun getResultType(hasGroup: Boolean): R_Type = mapType
    override fun compileR(appCtx: C_AppContext) = R_ColAtFieldSummarization_Aggregate_Map(mapType)
}

internal class C_AtContextMember(private val member: C_AtFromMember, private val outerAtExpr: Boolean) {
    fun isValue() = member.isValue()
    fun isCallable() = member.isCallable()

    fun fullNameMsg(): C_CodeMsg {
        val name = member.nameMsg()
        val owner = member.ownerMsg()
        return "${owner.code}:$name" toCodeMsg "${owner.msg}.$name"
    }

    fun compile(ctx: C_ExprContext, cNameHand: C_NameHandle): C_Expr {
        if (outerAtExpr) {
            val name = member.nameMsg()
            val owner = member.ownerMsg()
            ctx.msgCtx.error(cNameHand.pos, "at_expr:attr:belongs_to_outer:$name:${owner.code}",
                "Name '$name' belongs to an outer at-expression, fully qualified name is required")
        }
        return member.compile(ctx, cNameHand)
    }
}

internal class C_AtFromImplicitAttr(
    private val base: C_AtFromBase,
    private val selfType: R_Type,
    private val attr: C_AtTypeImplicitAttr,
) {
    val type = attr.type

    override fun toString() = attrNameMsg().code

    fun attrNameMsg(): C_CodeMsg {
        val baseMsg = base.nameMsg()
        val name = attr.member.nameMsg()
        val code = "${baseMsg.code}.${name.code}"
        return C_CodeMsg(code, name.msg)
    }

    fun compile(ctx: C_ExprContext, pos: S_Pos): V_Expr {
        val vBase = base.compile(ctx, pos)
        val link = C_MemberLink(vBase, selfType, pos, null, false)
        val cExpr = attr.member.compile(ctx, link, C_IdeSymbolInfoHandle.NOP_HANDLE, null)
        return cExpr.vExpr()
    }
}

internal class C_AtTypeImplicitAttr(val member: C_TypeValueMember, val type: R_Type)
