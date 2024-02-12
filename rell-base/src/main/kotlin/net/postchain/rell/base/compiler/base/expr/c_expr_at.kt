/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.expr

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.ast.S_PosValue
import net.postchain.rell.base.compiler.base.core.*
import net.postchain.rell.base.compiler.base.modifier.C_AtSummarizationKind
import net.postchain.rell.base.compiler.base.utils.C_CodeMsg
import net.postchain.rell.base.compiler.base.utils.C_Utils
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.compiler.vexpr.*
import net.postchain.rell.base.lib.type.*
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.*
import net.postchain.rell.base.model.stmt.R_IterableAdapter
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.utils.toImmList

class C_AtContext(
        val parent: C_AtContext?,
        val atExprId: R_AtExprId,
        val dbAt: Boolean
)

class C_AtFromContext(val pos: S_Pos, val atExprId: R_AtExprId, val parentAtCtx: C_AtContext?)

abstract class C_AtFromBase {
    abstract fun nameMsg(): C_CodeMsg
    abstract fun compile(pos: S_Pos): V_Expr
}

class C_AtFromMember(
    private val base: C_AtFromBase,
    private val selfType: R_Type,
    private val member: C_TypeValueMember,
) {
    fun nameMsg(): String = member.nameMsg().msg
    fun ownerMsg(): C_CodeMsg = base.nameMsg()
    fun isValue() = member.isValue()
    fun isCallable() = member.isCallable()

    fun compile(ctx: C_ExprContext, cNameHand: C_NameHandle): C_Expr {
        val baseExpr = base.compile(cNameHand.pos)
        val link = C_MemberLink(baseExpr, selfType, cNameHand.pos, cNameHand.name, false)
        return member.compile(ctx, link, cNameHand)
    }
}

abstract class C_AtFrom(
    protected val outerExprCtx: C_ExprContext,
    fromCtx: C_AtFromContext,
) {
    val atExprId = fromCtx.atExprId

    protected val parentAtCtx = fromCtx.parentAtCtx
    protected val innerBlkCtx = outerExprCtx.blkCtx.createSubContext("@", atFrom = this)

    val innerAtCtx = C_AtContext(fromCtx.parentAtCtx, atExprId, this is C_AtFrom_Entities)

    abstract fun innerExprCtx(): C_ExprContext
    abstract fun makeDefaultWhat(): V_DbAtWhat
    abstract fun findMembers(name: R_Name): List<C_AtFromMember>
    abstract fun findImplicitAttributesByName(name: R_Name): List<C_AtFromImplicitAttr>
    abstract fun findImplicitAttributesByType(type: R_Type): List<C_AtFromImplicitAttr>

    abstract fun compile(details: C_AtDetails): V_Expr

    protected fun compileColWhat(details: C_AtDetails, what: List<V_DbAtWhatField>): V_ColAtWhat {
        val colFields = what.map { it.toColField() }
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

    private fun compileColSorting(cFields: List<V_DbAtWhatField>): List<IndexedValue<Comparator<Rt_Value>>> {
        val sorting = cFields
                .withIndex()
                .mapNotNull { (i, f) ->
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
                .toImmList()
        return sorting
    }
}

sealed class C_AtFromItem(val pos: S_Pos)

class C_AtFromItem_Entity(pos: S_Pos, val alias: C_Name, val entity: R_EntityDefinition): C_AtFromItem(pos)

class C_AtFromItem_Iterable(
    pos: S_Pos,
    val vExpr: V_Expr,
    val elemType: R_Type,
    val ideInfo: C_IdeSymbolInfo,
    private val rIterableAdapter: R_IterableAdapter,
): C_AtFromItem(pos) {
    fun compile(): V_ColAtFrom {
        return V_ColAtFrom(rIterableAdapter, vExpr)
    }
}

class C_AtExprBase(
    val what: V_DbAtWhat,
    val where: V_Expr?,
)

class C_AtExprResult(
    val recordType: R_Type,
    val resultType: R_Type,
    val rowDecoder: R_AtExprRowDecoder,
    selectedFields: List<Int>,
    groupFields: List<Int>,
    val hasAggregateFields: Boolean,
) {
    val selectedFields = selectedFields.toImmList()
    val groupFields = groupFields.toImmList()

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

class C_AtDetails(
    val startPos: S_Pos,
    val cardinality: S_PosValue<R_AtCardinality>,
    val base: C_AtExprBase,
    val limit: V_Expr?,
    val offset: V_Expr?,
    val res: C_AtExprResult,
    val exprFacts: C_ExprVarFacts,
)

class C_AtSummarizationPos(val exprPos: S_Pos, val ann: C_AtSummarizationKind)

sealed class C_AtSummarization(
    protected val pos: C_AtSummarizationPos,
    protected val valueType: R_Type,
) {
    abstract fun isGroup(): Boolean
    open fun isCollectionAggregation(): Boolean = false
    abstract fun getResultType(hasGroup: Boolean): R_Type
    abstract fun compileR(appCtx: C_AppContext): R_ColAtFieldSummarization
    abstract fun compileDb(appCtx: C_AppContext, dbExpr: Db_Expr): Db_Expr

    companion object {
        fun typeError(msgCtx: C_MessageContext, type: R_Type, pos: C_AtSummarizationPos) {
            val code = "at:what:aggr:bad_type:${pos.ann}:${type.strCode()}"
            val msg = "Invalid type of @${pos.ann.annotation} expression: ${type.strCode()}"
            msgCtx.error(pos.exprPos, code, msg)
        }
    }
}

class C_AtSummarization_Group(pos: C_AtSummarizationPos, valueType: R_Type): C_AtSummarization(pos, valueType) {
    override fun isGroup() = true
    override fun getResultType(hasGroup: Boolean) = valueType

    override fun compileR(appCtx: C_AppContext): R_ColAtFieldSummarization {
        C_Utils.checkGroupValueType(appCtx, pos.exprPos, valueType)
        return R_ColAtFieldSummarization_Group()
    }

    override fun compileDb(appCtx: C_AppContext, dbExpr: Db_Expr) = dbExpr
}

sealed class C_AtSummarization_Aggregate(
    pos: C_AtSummarizationPos,
    valueType: R_Type,
): C_AtSummarization(pos, valueType) {
    protected abstract fun compileDb0(msgCtx: C_MessageContext): Db_SysFunction?

    final override fun isGroup() = false

    final override fun compileDb(appCtx: C_AppContext, dbExpr: Db_Expr): Db_Expr {
        val dbFn = compileDb0(appCtx.msgCtx)
        dbFn ?: return dbExpr
        return Db_CallExpr(dbExpr.type, dbFn, listOf(dbExpr))
    }
}

class C_AtSummarization_Aggregate_Sum(
    pos: C_AtSummarizationPos,
    valueType: R_Type,
    private val rOp: R_BinaryOp,
    private val zeroValue: Rt_Value
): C_AtSummarization_Aggregate(pos, valueType) {
    override fun getResultType(hasGroup: Boolean) = valueType
    override fun compileR(appCtx: C_AppContext) = R_ColAtFieldSummarization_Aggregate_Sum(rOp, zeroValue)
    override fun compileDb0(msgCtx: C_MessageContext) = Db_SysFn_Aggregation.Sum
}

class C_AtSummarization_Aggregate_MinMax(
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

abstract class C_AtSummarization_Aggregate_Collection(
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

class C_AtSummarization_Aggregate_List(
    pos: C_AtSummarizationPos,
    valueType: R_Type,
): C_AtSummarization_Aggregate_Collection(pos, valueType) {
    private val listType = R_ListType(valueType)

    override fun getResultType(hasGroup: Boolean): R_Type = listType
    override fun compileR(appCtx: C_AppContext) = R_ColAtFieldSummarization_Aggregate_List(listType)
}

class C_AtSummarization_Aggregate_Set(
    pos: C_AtSummarizationPos,
    valueType: R_Type,
): C_AtSummarization_Aggregate_Collection(pos, valueType) {
    private val setType = R_SetType(valueType)

    override fun getResultType(hasGroup: Boolean): R_Type = setType
    override fun compileR(appCtx: C_AppContext) = R_ColAtFieldSummarization_Aggregate_Set(setType)
}

class C_AtSummarization_Aggregate_Map(
    pos: C_AtSummarizationPos,
    valueType: R_Type,
    private val mapType: R_MapType,
): C_AtSummarization_Aggregate_Collection(pos, valueType) {
    override fun getResultType(hasGroup: Boolean): R_Type = mapType
    override fun compileR(appCtx: C_AppContext) = R_ColAtFieldSummarization_Aggregate_Map(mapType)
}

class C_AtContextMember(private val member: C_AtFromMember, private val outerAtExpr: Boolean) {
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

class C_AtFromImplicitAttr(
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
        val vBase = base.compile(pos)
        val link = C_MemberLink(vBase, selfType, pos, null, false)
        val cExpr = attr.member.compile(ctx, link, C_IdeSymbolInfoHandle.NOP_HANDLE)
        return cExpr.value()
    }
}

class C_AtTypeImplicitAttr(val member: C_TypeValueMember, val type: R_Type)
