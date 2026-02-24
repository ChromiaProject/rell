/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.expr

import com.google.common.collect.Multimap
import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.core.*
import net.postchain.rell.base.compiler.base.utils.C_CodeMsg
import net.postchain.rell.base.compiler.base.utils.C_Constants
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.compiler.vexpr.*
import net.postchain.rell.base.lib.type.R_ListType
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.*
import net.postchain.rell.base.model.stmt.R_IterableAdapter_Direct
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.ide.IdeCompletion

internal class C_AtEntity(
    val declPos: S_Pos,
    val rEntity: R_EntityDefinition,
    val alias: R_Name,
    val explicitAlias: Boolean,
    atEntityId: R_AtEntityId,
    val aliasIdeDef: C_IdeSymbolDef,
    val dollarIdeInfo: C_IdeSymbolInfo,
) {
    val atExprId = atEntityId.exprId
    val varId: C_VarId = C_AtEntityVarId(this)

    private val rAtEntity = R_DbAtEntity(rEntity, atEntityId)

    fun toRAtEntity(): R_DbAtEntity {
        return rAtEntity
    }

    fun toVExpr(ctx: C_ExprContext, pos: S_Pos, isOuter: Boolean, isAmbiguous: Boolean): V_Expr {
        val vExpr = V_AtEntityExpr(ctx, pos, this, isOuter, isAmbiguous)
        return V_SmartNullableExpr.wrap(ctx, vExpr, "var" toCodeMsg "variable")
    }

    fun toRAtEntityValidated(ctx: C_ExprContext, pos: S_Pos, ambiguous: Boolean): R_DbAtEntity {
        if (!isValidAccess(ctx) && !ambiguous) {
            ctx.msgCtx.error(pos, "at:entity:outer:$alias",
                    "Cannot access entity '${rEntity.moduleLevelName}' as it belongs to an unrelated at-expression")
        }
        return rAtEntity
    }

    private fun isValidAccess(ctx: C_ExprContext): Boolean {
        return chainToIterable(ctx.atCtx) { it.parent }.any { it.atExprId == atExprId }
    }

    private data class C_AtEntityVarId(private val atEntity: C_AtEntity): C_VarId() {
        override fun nameMsg() = atEntity.alias.str
    }
}

internal class C_AtFrom_Entities(
    outerExprCtx: C_ExprContext,
    fromCtx: C_AtFromContext,
    fromBlock: R_FrameBlock?,
    private val items: ImmList<C_AtFromItem_Entity>,
): C_AtFrom(outerExprCtx, fromCtx, fromBlock) {
    private val entities = this.items.mapToImmList { it.atEntity }

    private val innerExprCtx = outerExprCtx.copy(blkCtx = innerBlkCtx, atCtx = innerAtCtx)

    init {
        check(entities.isNotEmpty())

        val atExprIds = entities.map { it.atExprId }.toSet()
        checkEquals(atExprIds, setOf(atExprId))

        val ph = entities.any { !it.explicitAlias }
        for (item in items) {
            val entity = item.atEntity
            val isOuter = item.isOuter()
            val entry = C_BlockEntry_AtEntity(entity, entity.aliasIdeDef.refInfo, isOuter)
            innerBlkCtx.addEntry(entity.declPos, entity.alias, entity.explicitAlias, entry)
            if (ph) {
                val phEntry = C_BlockEntry_AtEntity(entity, entity.dollarIdeInfo, isOuter)
                innerBlkCtx.addAtPlaceholder(phEntry)
            }
        }
    }

    override fun getAllExprs() = items.flatMapToImmList { it.getExprs() }
    override fun innerExprCtx() = innerExprCtx

    override fun makeDefaultWhatFields(ctx: C_ExprContext): ImmList<V_DbAtWhatField> {
        return items.mapToImmList {
            val atEntity = it.atEntity
            val name = if (entities.size == 1) null else R_IdeName(atEntity.alias, C_IdeSymbolInfo.MEM_TUPLE_ATTR)
            val vExpr = atEntity.toVExpr(ctx, atEntity.declPos, isOuter = it.isOuter(), isAmbiguous = false)
            V_DbAtWhatField(ctx.appCtx, name, vExpr.type, vExpr, V_AtWhatFieldFlags.DEFAULT, null)
        }
    }

    override fun findMembers(ctx: C_ExprContext, name: C_Name): ImmList<C_AtFromMember> {
        return items.flatMapToImmList { item ->
            val isOuter = item.isOuter()
            val base = C_AtFromBase_Entity(item.atEntity, isOuter)
            val selfType = item.atEntity.rEntity.type
            val members = ctx.typeMgr.getValueMembers(selfType, name.rName)
            members.map { C_AtFromMember(base, selfType, it, isOuter) }
        }
    }

    override fun findImplicitAttributesByName(ctx: C_ExprContext, name: C_Name): ImmList<C_AtFromImplicitAttr> {
        return findContextAttrs { rEntity ->
            ctx.typeMgr.getAtImplicitAttrsByName(rEntity.type, name.rName)
        }
    }

    override fun findImplicitAttributesByType(
        ctx: C_ExprContext,
        pos: S_Pos,
        type: R_Type,
    ): ImmList<C_AtFromImplicitAttr> {
        return findContextAttrs { rEntity ->
            ctx.typeMgr.getAtImplicitAttrsByType(rEntity.type, type)
        }
    }

    private fun findContextAttrs(getter: (R_EntityDefinition) -> List<C_AtTypeImplicitAttr>): ImmList<C_AtFromImplicitAttr> {
        return items.flatMapToImmList { item ->
            val atEntity = item.atEntity
            val base = C_AtFromBase_Entity(atEntity, item.isOuter())
            val members = getter(atEntity.rEntity)
            members.map { C_AtFromImplicitAttr(base, atEntity.rEntity.type, it) }
        }
    }

    override fun ideCompletions(): ImmMultimap<String, IdeCompletion> {
        val members = items.flatMap { item ->
            val selfType = item.atEntity.rEntity.type
            outerExprCtx.typeMgr.getValueMembers(selfType)
        }

        return members
            .mapNotNull {
                val name = it.optionalName?.str
                val completion = it.ideCompletion()
                if (name == null || completion == null) null else (".$name" to completion)
            }
            .toImmMultimap()
    }

    override fun compile(details: C_AtDetails): V_Expr {
        val cBase = compileBase(details)
        if (parentAtCtx?.dbAt != true) {
            return compileTop(details, cBase)
        }

        val dependent = isOuterDependent(cBase)
        return if (dependent || details.cardinality.value == R_AtCardinality.ZERO_MANY) {
            compileNested(details, cBase)
        } else {
            compileTop(details, cBase)
        }
    }

    override fun compileJoin(details: C_AtDetails, isOuter: Boolean): C_AtFromItem {
        if (items.size > 1) {
            msgCtx.error(items[1].pos, "expr:at:join:many_items", "Join at-expression must have only one entity")
        }

        val item = items.first()
        val where = details.compileJoin(msgCtx)
        val cBlock = innerBlkCtx.buildBlock()
        return item.compileJoin(msgCtx, isOuter, where, cBlock.rBlock)
    }

    private fun isOuterDependent(cBase: C_AtExprBase): Boolean {
        val exprIds = cBase.referencedAtExprIds()
        return chainToIterable(parentAtCtx) { it.parent }.any { exprIds.contains(it.atExprId) }
    }

    private fun compileTop(details: C_AtDetails, cBase: C_AtExprBase): V_Expr {
        val colAggr = cBase.what.any { it.summarization?.isCollectionAggregation() == true }
        return if (colAggr) {
            compileTopColAggr(details, cBase)
        } else {
            compileTopDefault(details, cBase)
        }
    }

    private fun compileTopDefault(details: C_AtDetails, cBase: C_AtExprBase): V_Expr {
        val extras = V_AtExprExtras(details.limit, details.offset)
        val cBlock = innerBlkCtx.buildBlock()
        val internals = R_DbAtExprInternals(cBlock.rBlock, details.res.rowDecoder)

        return V_TopDbAtExpr(
            outerExprCtx,
            details.startPos,
            details.res.resultType,
            cBase.toVBase(),
            extras,
            details.cardinality.value,
            internals,
            details.varStatesDelta,
        )
    }

    private fun compileTopColAggr(details: C_AtDetails, cBase: C_AtExprBase): V_Expr {
        val dbWhat = cBase.what.mapToImmList { field ->
            val flags = field.flags.update(omit = false, sort = null, group = null, aggregate = null)
            field.update(resultType = field.expr.type, flags = flags, summarization = null)
        }

        val itemType = R_TupleType.make(dbWhat.map { it.resultType })
        val innerAtExpr = createTopColAggrInner(details, cBase, dbWhat, itemType)

        val innerBlkCtx2 = outerExprCtx.blkCtx.createSubContext("@2", null)
        val itemVar = innerBlkCtx2.newLocalVar(C_Constants.AT_PLACEHOLDER, null, itemType, false, null)
        return createTopColAggrOuter(details, itemVar, innerAtExpr, innerBlkCtx2)
    }

    private fun createTopColAggrInner(
        details: C_AtDetails,
        cBase: C_AtExprBase,
        what: ImmList<V_DbAtWhatField>,
        itemType: R_TupleType,
    ): V_Expr {
        val cBlock = innerBlkCtx.buildBlock()
        val rowDecoder = R_AtExprRowDecoder_Tuple(itemType)

        return V_TopDbAtExpr(
            outerExprCtx,
            details.startPos,
            resultType = R_ListType(itemType),
            base = cBase.update(what = what, isMany = true).toVBase(),
            extras = V_AtExprExtras(null, null),
            cardinality = R_AtCardinality.ZERO_MANY,
            internals = R_DbAtExprInternals(cBlock.rBlock, rowDecoder),
            resVarStates = details.varStatesDelta,
        )
    }

    private fun createTopColAggrOuter(
        details: C_AtDetails,
        itemVar: C_LocalVar,
        innerAtExpr: V_Expr,
        innerBlkCtx2: C_OwnerBlockContext,
    ): V_Expr {
        val cBlock = innerBlkCtx2.buildBlock()
        val itemVarRef = itemVar.toRef(cBlock.rBlock.uid)
        val innerExprCtx2 = outerExprCtx
            .updateVarStates(C_VarStatesDelta.changed(itemVar.varKey))
            .copy(blkCtx = innerBlkCtx2, atCtx = innerAtCtx)
        val itemExpr: V_Expr = V_LocalVarExpr(innerExprCtx2, details.startPos, itemVarRef)

        val colWhat = details.base.what.allFields.mapIndexed { i, field ->
            val kind = V_TupleSubscriptKind_Simple
            val expr = V_TupleSubscriptExpr(innerExprCtx2, field.expr.pos, itemExpr, kind, field.expr.type, i, null)
            field.update(expr = expr)
        }

        return V_ColAtExpr(
            outerExprCtx,
            details.startPos,
            result = details.res,
            from = V_ColAtFrom(R_IterableAdapter_Direct, innerAtExpr, null),
            what = compileColWhat(details, colWhat),
            where = null,
            cardinality = details.cardinality.value,
            extras = V_AtExprExtras(details.limit, details.offset),
            block = cBlock.rBlock,
            param = R_ColAtParam(itemVar.type, itemVarRef.ptr),
            resVarStates = details.varStatesDelta,
        )
    }

    private fun compileNested(details: C_AtDetails, cBase: C_AtExprBase): V_Expr {
        var resultType = details.res.resultType

        if (details.cardinality.value != R_AtCardinality.ZERO_MANY) {
            msgCtx.error(details.cardinality.pos, "at_expr:nested:cardinality:${details.cardinality.value}",
                    "Only '@*' can be used in a nested at-expression")
            // Fix result type to prevent exists() also firing a "wrong argument type" CTE.
            resultType = C_AtExprResult.calcResultType(details.res.recordType, R_AtCardinality.ZERO_MANY)
        }

        val cBlock = innerBlkCtx.buildBlock()
        val extras = V_AtExprExtras(details.limit, details.offset)

        return V_NestedDbAtExpr(
            outerExprCtx,
            details.startPos,
            resultType,
            cBase.toVBase(),
            extras,
            cBlock.rBlock,
            details.varStatesDelta,
        )
    }

    private fun compileBase(details: C_AtDetails): C_AtExprBase {
        val vFromItems = items.mapToImmList { it.compile() }
        val vFrom = V_DbAtExprFrom(vFromItems, fromBlock)
        val whatFields = details.base.what.getMaterialFields()
        return C_AtExprBase(
            vFrom,
            whatFields,
            details.base.where,
            isMany = details.cardinality.value.many,
        )
    }

    fun compileUpdate(): R_FrameBlock {
        val cBlock = innerBlkCtx.buildBlock()
        return cBlock.rBlock
    }

    private inner class C_AtFromBase_Entity(
        private val atEntity: C_AtEntity,
        private val isOuter: Boolean,
    ): C_AtFromBase() {
        override fun nameMsg(): C_CodeMsg {
            return "${atEntity.alias}:${atEntity.rEntity.type.name}" toCodeMsg atEntity.alias.str
        }

        override fun compile(ctx: C_ExprContext, pos: S_Pos): V_Expr {
            return atEntity.toVExpr(ctx, pos, isOuter = isOuter, isAmbiguous = false)
        }
    }

    private class C_AtExprBase(
        private val from: V_DbAtExprFrom,
        val what: ImmList<V_DbAtWhatField>,
        private val where: V_Expr?,
        private val isMany: Boolean,
    ) {
        private val innerExprs = what.mapToImmList { it.expr } + listOfNotNull(where)

        private val refAtExprIds: ImmSet<R_AtExprId> by lazy {
            innerExprs.flatMap { it.info.dependsOnAtExprs }.toImmSet()
        }

        fun update(
            what: ImmList<V_DbAtWhatField> = this.what,
            where: V_Expr? = this.where,
            isMany: Boolean = this.isMany,
        ) = C_AtExprBase(
            from = from,
            what = what,
            where = where,
            isMany = isMany,
        )

        fun referencedAtExprIds(): Set<R_AtExprId> = refAtExprIds
        fun toVBase() = V_AtExprBase(from, what, where, isMany, innerExprs)
    }
}

sealed class C_DbAtWhatValue {
    internal abstract fun toDbWhatTop(appCtx: C_AppContext, field: V_DbAtWhatField): Db_AtWhatValue
    internal abstract fun toDbWhatSub(): Db_AtWhatValue
}

internal class C_DbAtWhatValue_Simple(private val dbExpr: Db_Expr): C_DbAtWhatValue() {
    override fun toDbWhatTop(appCtx: C_AppContext, field: V_DbAtWhatField): Db_AtWhatValue {
        var resExpr = dbExpr
        if (field.summarization != null) {
            resExpr = field.summarization.compileDb(appCtx, resExpr)
        }
        return Db_AtWhatValue_DbExpr(resExpr, field.resultType)
    }

    override fun toDbWhatSub(): Db_AtWhatValue {
        return Db_AtWhatValue_DbExpr(dbExpr, dbExpr.type)
    }
}

internal class C_DbAtWhatValue_Complex internal constructor(
    val vExprs: ImmList<V_Expr>,
    private val evaluator: Db_ComplexAtWhatEvaluator,
): C_DbAtWhatValue() {
    override fun toDbWhatTop(appCtx: C_AppContext, field: V_DbAtWhatField): Db_AtWhatValue {
        V_AtUtils.checkNoWhatModifiersDb(appCtx.msgCtx, field)
        return toDbWhatSub()
    }

    override fun toDbWhatSub(): Db_AtWhatValue {
        val items = mutableListOf<Pair<Boolean, Int>>()
        val dbExprs = mutableListOf<Db_AtWhatValue>()
        val rExprs = mutableListOf<R_Expr>()

        for (vExpr in vExprs) {
            if (vExpr.info.dependsOnDbAtEntity) {
                items.add(true to dbExprs.size)
                dbExprs.add(vExpr.toDbExprWhat().toDbWhatSub())
            } else {
                items.add(false to rExprs.size)
                rExprs.add(vExpr.toRExpr())
            }
        }

        return Db_AtWhatValue_Complex(dbExprs.toImmList(), rExprs.toImmList(), items.toImmList(), evaluator)
    }
}

internal class C_DbAtWhatValue_Other(private val dbWhatValue: Db_AtWhatValue): C_DbAtWhatValue() {
    override fun toDbWhatTop(appCtx: C_AppContext, field: V_DbAtWhatField): Db_AtWhatValue {
        V_AtUtils.checkNoWhatModifiersDb(appCtx.msgCtx, field)
        return dbWhatValue
    }

    override fun toDbWhatSub() = dbWhatValue
}
