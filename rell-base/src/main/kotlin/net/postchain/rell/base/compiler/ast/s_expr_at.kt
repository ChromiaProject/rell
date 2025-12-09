/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.ast

import net.postchain.rell.base.compiler.base.core.*
import net.postchain.rell.base.compiler.base.expr.*
import net.postchain.rell.base.compiler.base.lib.C_LibUtils
import net.postchain.rell.base.compiler.base.modifier.*
import net.postchain.rell.base.compiler.base.utils.*
import net.postchain.rell.base.compiler.vexpr.V_AtWhatFieldFlags
import net.postchain.rell.base.compiler.vexpr.V_ConstantValueEvalContext
import net.postchain.rell.base.compiler.vexpr.V_DbAtWhatField
import net.postchain.rell.base.compiler.vexpr.V_Expr
import net.postchain.rell.base.lib.type.*
import net.postchain.rell.base.lmodel.L_TypeUtils
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.*
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.DocDeclarationProto_AtVariable
import net.postchain.rell.base.utils.doc.DocSymbolKind
import net.postchain.rell.base.utils.doc.DocSymbolName
import net.postchain.rell.base.utils.ide.IdeLocalSymbolLink
import net.postchain.rell.base.utils.ide.IdeSymbolId
import net.postchain.rell.base.utils.ide.IdeSymbolKind

internal sealed class S_AtExprFrom(val startPos: S_Pos) {
    internal abstract fun compile(ctx: C_ExprContext, fromCtx: C_AtFromContext): C_AtFrom
    internal abstract fun compileJoin(ctx: C_ExprContext, fromCtx: C_AtFromContext, alias: C_Name?): C_AtFrom
}

internal class S_AtExprFrom_Simple(val expr: S_Expr): S_AtExprFrom(expr.startPos) {
    override fun compile(ctx: C_ExprContext, fromCtx: C_AtFromContext): C_AtFrom {
        return compileJoin(ctx, fromCtx, null)
    }

    override fun compileJoin(ctx: C_ExprContext, fromCtx: C_AtFromContext, alias: C_Name?): C_AtFrom {
        val itemCtx = C_AtFromItemContext(
            fromCtx,
            isJoin = false,
            outerJoinPos = null,
            atExprAllowed = true,
            comment = null,
        )
        val item = expr.compileFromItem(ctx, itemCtx, alias)
        return compile0(ctx, fromCtx, item)
    }

    private fun compile0(ctx: C_ExprContext, fromCtx: C_AtFromContext, item: C_AtFromItem): C_AtFrom {
        return when (item) {
            is C_AtFromItem_Entity -> C_AtFrom_Entities(ctx, fromCtx, null, immListOf(item))
            is C_AtFromItem_Iterable -> C_AtFrom_Iterable(ctx, fromCtx, null, item)
        }
    }
}

internal class S_AtExprFrom_Complex(
    startPos: S_Pos,
    private val items: ImmList<S_AtExprFromItem>,
): S_AtExprFrom(startPos) {
    init {
        require(items.isNotEmpty())
    }

    override fun compile(ctx: C_ExprContext, fromCtx: C_AtFromContext): C_AtFrom {
        val (cItems, rFromBlock) = compileItems(ctx, fromCtx)

        val entities = mutableListOf<C_AtFromItem_Entity>()
        val iterables = mutableListOf<C_AtFromItem_Iterable>()

        for (item in cItems) {
            when (item) {
                is C_AtFromItem_Entity -> processFromItem(ctx, item, entities, iterables)
                is C_AtFromItem_Iterable -> processFromItem(ctx, item, iterables, entities)
            }
        }

        if (entities.isNotEmpty()) {
            return C_AtFrom_Entities(ctx, fromCtx, rFromBlock, entities.toImmList())
        }

        if (iterables.size > 1 && iterables.any { it.vExpr.type.isNotError() }) {
            ctx.msgCtx.error(iterables[1].pos, "at:from:many_iterables:${iterables.size}",
                    "Only one collection is allowed in at-expression")
        }

        val iterable = iterables.first()
        return C_AtFrom_Iterable(ctx, fromCtx, rFromBlock, iterable)
    }

    override fun compileJoin(ctx: C_ExprContext, fromCtx: C_AtFromContext, alias: C_Name?): C_AtFrom {
        ctx.msgCtx.error(startPos, "expr:at:join:complex_from", "Join at-expression must use a simple entity")
        return compile(ctx, fromCtx)
    }

    private fun compileItems(ctx: C_ExprContext, fromCtx: C_AtFromContext): Pair<List<C_AtFromItem>, R_FrameBlock> {
        val res = mutableListOf<C_AtFromItem>()

        var isDb: Boolean? = null
        val fromBlkCtx = ctx.blkCtx.createSubContext("@from")
        val fromExprCtx = ctx.copy(blkCtx = fromBlkCtx)

        for (item in items) {
            val cItem = item.compile(fromExprCtx, fromCtx, isDb)
            res.add(cItem)

            val itemIsDb = when (cItem) {
                is C_AtFromItem_Iterable -> false
                is C_AtFromItem_Entity -> {
                    val entity = cItem.atEntity
                    val entry = C_BlockEntry_AtEntity(entity, entity.aliasIdeDef.refInfo, cItem.isOuter())
                    fromBlkCtx.addEntry(
                        entity.declPos,
                        entity.alias,
                        entity.explicitAlias,
                        entry,
                        errOnNameConflict = false,
                    )
                    true
                }
            }

            if (isDb == null) {
                isDb = itemIsDb
            }
        }

        val rBlock = fromBlkCtx.buildBlock().rBlock
        return res.toImmList() to rBlock
    }

    private fun <T: C_AtFromItem> processFromItem(
        ctx: C_ExprContext,
        item: T,
        targets: MutableList<T>,
        opposites: List<*>,
    ) {
        if (opposites.isNotEmpty()) {
            ctx.msgCtx.error(item.pos,
                "at:from:mix_entity_iterable", "Cannot mix entities and collections in at-expression")
        }
        targets.add(item)
    }
}

internal class S_AtExprFromItem(
    private val modifiers: S_Modifiers,
    private val alias: S_Name?,
    private val expr: S_Expr,
    private val comment: S_Comment?,
) {
    fun compile(ctx: C_ExprContext, fromCtx: C_AtFromContext, isDb: Boolean?): C_AtFromItem {
        if (modifiers.pos != null) {
            RESTRICTIONS.access(ctx.msgCtx, modifiers.pos)
        }

        val mods = C_ModifierValues(C_ModifierTargetType.EXPRESSION, null)
        val modOuter = mods.field(C_ModifierFields.OUTER)
        modifiers.compile(ctx.nsCtx, mods)

        val isJoin = isDb == true
        val outerPos = modOuter.pos()
        if (outerPos != null && !isJoin) {
            ctx.msgCtx.error(outerPos, "expr:at:from:bad_outer_join", "Invalid outer join expression")
        }

        val outerJoinPos = if (isJoin) outerPos else null
        val itemCtx = C_AtFromItemContext(fromCtx, isJoin, outerJoinPos, isDb != null, comment = comment)

        val aliasNameHand = alias?.compile(ctx, def = true)

        val item = expr.compileFromItem(ctx, itemCtx, aliasNameHand?.name)
        aliasNameHand?.setIdeInfo(item.aliasIdeDef.defInfo)
        return item
    }

    companion object {
        private val RESTRICTIONS = C_FeatureRestrictions.make("0.13.10",
            "at_expr_from_annotation", "At-expression-from annotations are",
        )
    }
}

internal sealed class S_AtExprWhat {
    internal abstract fun compile(ctx: C_ExprContext, from: C_AtFrom, subValues: MutableList<V_Expr>): C_AtWhat
}

internal class S_AtExprWhat_Default: S_AtExprWhat() {
    override fun compile(ctx: C_ExprContext, from: C_AtFrom, subValues: MutableList<V_Expr>): C_AtWhat {
        val fields = from.makeDefaultWhatFields(ctx)
        return C_AtWhat(fields, null)
    }
}

internal class S_AtExprWhat_Simple(val startPos: S_Pos, val path: ImmList<S_Name>): S_AtExprWhat() {
    override fun compile(ctx: C_ExprContext, from: C_AtFrom, subValues: MutableList<V_Expr>): C_AtWhat {
        var expr = S_AttrExpr.compileAttr(ctx, C_ExprHint.DEFAULT, path[0])

        for (i in 1 until path.size) {
            val step = path[i]
            val stepHand = step.compile(ctx)
            expr = expr.member(ctx, stepHand, C_ExprHint.DEFAULT)
        }

        val vExpr = expr.vExpr()
        val field = V_DbAtWhatField(ctx.appCtx, null, vExpr.type, vExpr, V_AtWhatFieldFlags.DEFAULT, null)
        return C_AtWhat(immListOf(field), startPos)
    }
}

internal class S_AtExprWhatComplexField(
    val attr: S_Name?,
    val expr: S_Expr,
    val modifiers: S_Modifiers,
    val sort: S_PosValue<R_AtWhatSort>?,
    val comment: S_Comment?,
)

internal class S_AtExprWhat_Complex(
    private val posRange: S_PosRange,
    private val fields: ImmList<S_AtExprWhatComplexField>,
): S_AtExprWhat() {
    override fun compile(ctx: C_ExprContext, from: C_AtFrom, subValues: MutableList<V_Expr>): C_AtWhat {
        ctx.blkCtx.frameCtx.ideCompCtx.trackScope(posRange, ctx)

        val lazyTupleIdeId = lazy { ctx.defCtx.tupleIdeId() }

        val procFields = processFields(ctx)
        subValues.addAll(procFields.map { it.vExpr })

        val selFields = procFields.filter { !it.flags.omit }
        if (selFields.isEmpty()) {
            ctx.msgCtx.error(fields[0].expr.startPos, "at:no_fields", "All fields are excluded from the result")
        }

        val hasGroup = procFields.any { it.summarization?.isGroup() ?: false }

        val vFields = procFields.mapToImmList { field ->
            val resultType = field.summarization?.getResultType(hasGroup) ?: field.vExpr.type
            val rIdeName = compileFieldName(ctx, lazyTupleIdeId, field, resultType, selFields.size > 1)
            V_DbAtWhatField(ctx.appCtx, rIdeName, resultType, field.vExpr, field.flags, field.summarization)
        }

        return C_AtWhat(vFields, posRange.start)
    }

    private fun processFields(ctx: C_ExprContext): List<WhatField> {
        val procFields = fields.map { processField(ctx, it) }

        val (aggrFields, noAggrFields) = procFields.withIndex().partition {
            it.value.summarization != null || it.value.flags.aggregate != null
        }

        if (aggrFields.isNotEmpty()) {
            for ((i, field) in noAggrFields) {
                val code = "at:what:no_aggr:$i"
                val anns = C_AtSummarizationKind.entries.joinToString(", ") { "@${it.annotation}" }
                val msg = "Either none or all what-expressions must be annotated with one of: $anns"
                ctx.msgCtx.error(field.vExpr.pos, code, msg)
            }
        }

        val res = processNameConflicts(ctx, procFields)
        return res
    }

    private fun processField(
        ctx: C_ExprContext,
        field: S_AtExprWhatComplexField,
    ): WhatField {
        val mods = C_ModifierValues(C_ModifierTargetType.EXPRESSION, null)
        val modOmit = mods.field(C_ModifierFields.OMIT)
        val modSort = mods.field(C_ModifierFields.SORT)
        val modSumm = mods.field(C_ModifierFields.SUMMARIZATION)
        field.modifiers.compile(ctx.nsCtx, mods)

        val omit = modOmit.hasValue()
        val sort = modSort.posValue() ?: field.sort
        val summ = modSumm.posValue()

        summ?.value?.restrictions?.access(ctx.msgCtx, summ.pos)

        if (field.sort != null) {
            val ann = if (field.sort.value.asc) C_Annotations.SORT else C_Annotations.SORT_DESC
            ctx.msgCtx.warning(field.sort.pos, "at:what:sort:deprecated:$ann",
                    "Deprecated sort syntax; use @$ann annotation instead")

            if (modSort.value() != null) {
                ctx.msgCtx.error(field.sort.pos, "at:what:sort:specified_by_kw_and_ann",
                        "Sorting is specified by annotation and keyword at the same time")
            }
        }

        val flags = V_AtWhatFieldFlags(
            omit = omit,
            sort = sort,
            group = if (summ?.value == C_AtSummarizationKind.GROUP) summ.pos else null,
            aggregate = if (summ != null && summ.value != C_AtSummarizationKind.GROUP) summ.pos else null,
        )

        val vExpr = field.expr.compileSafe(ctx).vExpr()
        val cSummarization = compileSummarization(ctx, vExpr, summ?.value)

        var explicitNameHand: C_NameHandle? = null
        var effectiveName: C_Name? = null

        val attr = field.attr

        if (attr != null) {
            val attrNameHand = attr.compile(ctx, def = true)
            val cAttr = attrNameHand.name
            if (cAttr.str != "_") {
                explicitNameHand = attrNameHand
                effectiveName = cAttr
            } else {
                attrNameHand.setIdeInfo(C_IdeSymbolInfo.UNKNOWN)
            }
        } else if (!omit && (cSummarization == null || cSummarization.isGroup())) {
            val impName = vExpr.implicitAtWhatAttrName()
            if (impName != null) {
                effectiveName = impName
            }
        }

        return WhatField(vExpr, explicitNameHand, effectiveName, flags, cSummarization, field.comment)
    }

    private fun compileFieldName(
        ctx: C_ExprContext,
        lazyTupleIdeId: Lazy<IdeSymbolId>,
        field: WhatField,
        resultType: R_Type,
        manyFields: Boolean,
    ): R_IdeName? {
        field.effectiveName ?: return null

        val ideDef = S_TupleType.makeFieldIdeDef(
            ctx.symCtx,
            lazyTupleIdeId.value,
            field.effectiveName,
            resultType,
            field.comment,
        )
        val ideDefId = ideDef.defInfo.defId

        if (field.explicitNameHand != null) {
            var defIdeInfo = ideDef.defInfo
            if (field.flags.omit) defIdeInfo = defIdeInfo.update(defId = null)
            field.explicitNameHand.setIdeInfo(defIdeInfo)
        } else if (ideDefId != null) {
            ctx.nameCtx.setDefId(field.effectiveName.pos, ideDefId)
        }

        val hasActualName = !field.flags.omit && (field.explicitNameHand != null || manyFields)
        return if (hasActualName) R_IdeName(field.effectiveName.rName, ideDef.refInfo) else null
    }

    private fun compileSummarization(
        ctx: C_ExprContext,
        vExpr: V_Expr,
        ann: C_AtSummarizationKind?,
    ): C_AtSummarization? {
        ann ?: return null

        val type = vExpr.type
        val pos = C_AtSummarizationPos(vExpr.pos, ann)

        val cSummarization = when (ann) {
            C_AtSummarizationKind.GROUP -> C_AtSummarization_Group(pos, type)
            C_AtSummarizationKind.SUM -> compileSummarizationSum(pos, type)
            C_AtSummarizationKind.MIN -> compileSummarizationMinMax(pos, type, R_CmpOp_Le, Db_SysFn_Aggregation.Min)
            C_AtSummarizationKind.MAX -> compileSummarizationMinMax(pos, type, R_CmpOp_Ge, Db_SysFn_Aggregation.Max)
            C_AtSummarizationKind.LIST -> C_AtSummarization_Aggregate_List(pos, type)
            C_AtSummarizationKind.SET -> compileSummarizationSet(ctx.msgCtx, pos, type)
            C_AtSummarizationKind.MAP -> compileSummarizationMap(ctx.msgCtx, pos, type)
        }

        if (cSummarization == null) {
            C_AtSummarization.typeError(ctx.msgCtx, type, pos)
        }

        return cSummarization
    }

    private fun compileSummarizationSum(pos: C_AtSummarizationPos, type: R_Type): C_AtSummarization? {
        return when (type) {
            R_IntegerType -> C_AtSummarization_Aggregate_Sum(pos, type, R_BinaryOp_Add_Integer, Rt_IntValue.ZERO)
            R_BigIntegerType -> C_AtSummarization_Aggregate_Sum(pos, type, R_BinaryOp_Add_BigInteger, Rt_BigIntegerValue.ZERO)
            R_DecimalType -> C_AtSummarization_Aggregate_Sum(pos, type, R_BinaryOp_Add_Decimal, Rt_DecimalValue.ZERO)
            else -> null
        }
    }

    private fun compileSummarizationMinMax(
            pos: C_AtSummarizationPos,
            type: R_Type,
            cmpOp: R_CmpOp,
            dbFn: Db_SysFunction
    ): C_AtSummarization? {
        val rCmpType = R_CmpType.forAtMinMaxType(type)
        val rComparator = if (type is R_NullableType) null else type.comparator()
        return if (rCmpType == null && rComparator == null) null else {
            C_AtSummarization_Aggregate_MinMax(pos, type, cmpOp, rCmpType, rComparator, dbFn)
        }
    }

    private fun compileSummarizationSet(
        msgCtx: C_MessageContext,
        pos: C_AtSummarizationPos,
        type: R_Type
    ): C_AtSummarization {
        if (!C_LibUtils.isImmutableType(type.mType)) {
            C_AtSummarization.typeError(msgCtx, type, pos)
        }
        return C_AtSummarization_Aggregate_Set(pos, type)
    }

    private fun compileSummarizationMap(
        msgCtx: C_MessageContext,
        pos: C_AtSummarizationPos,
        type: R_Type
    ): C_AtSummarization? {
        val mTypes = C_LibUtils.asMapEntryOrNull(type.mType)
        mTypes ?: return null

        val (mKeyType, mValueType) = mTypes
        val rKeyType = L_TypeUtils.getRTypeOrNull(mKeyType) ?: return null
        val rValueType = L_TypeUtils.getRTypeOrNull(mValueType) ?: return null

        if (!C_LibUtils.isImmutableType(mTypes.first)) {
            C_AtSummarization.typeError(msgCtx, type, pos)
        }

        val rMapType = R_MapType(rKeyType, rValueType)
        return C_AtSummarization_Aggregate_Map(pos, type, rMapType)
    }

    private fun processNameConflicts(ctx: C_ExprContext, procFields: List<WhatField>): List<WhatField> {
        val res = mutableListOf<WhatField>()
        val names = mutableSetOf<R_Name>()

        for (f in procFields) {
            val name = f.effectiveName
            var field = f
            if (name != null && !names.add(name.rName)) {
                ctx.msgCtx.error(f.namePos, "at:dup_field_name:$name", "Duplicate field name: '$name'")
                f.explicitNameHand?.setIdeInfo(C_IdeSymbolInfo.UNKNOWN)
                field = f.updateName(null)
            }
            res.add(field)
        }

        return res
    }

    private class WhatField(
        val vExpr: V_Expr,
        val explicitNameHand: C_NameHandle?,
        val effectiveName: C_Name?,
        val flags: V_AtWhatFieldFlags,
        val summarization: C_AtSummarization?,
        val comment: S_Comment?,
    ) {
        val namePos: S_Pos = explicitNameHand?.pos ?: vExpr.pos

        fun updateName(newName: C_Name?): WhatField {
            return WhatField(
                vExpr = vExpr,
                effectiveName = newName,
                explicitNameHand = explicitNameHand,
                flags = flags,
                summarization = summarization,
                comment = comment,
            )
        }
    }
}

internal class S_AtExprWhere(
    private val exprs: ImmList<S_Expr>,
    private val posRange: S_PosRange,
) {
    internal fun compile(ctx: C_ExprContext, atExprId: R_AtExprId, subValues: MutableList<V_Expr>): V_Expr? {
        ctx.blkCtx.frameCtx.ideCompCtx.trackScope(posRange, ctx)

        var whereCtx = ctx
        val whereExprs0 = mutableListOf<V_Expr>()

        for ((idx, expr) in exprs.withIndex()) {
            val vExpr = compileWhereExpr(whereCtx, atExprId, idx, expr, subValues)
            if (S_AtExpr.WHERE_VAR_STATES_SWITCH.isActive(ctx)) {
                whereCtx = whereCtx.updateVarStates(vExpr.varStatesDelta.whenTrue)
            }
            whereExprs0.add(vExpr)
        }

        val whereExprs = whereExprs0.toImmList()
        return makeWhere(ctx, whereExprs)
    }

    private fun compileWhereExpr(
        ctx: C_ExprContext,
        atExprId: R_AtExprId,
        idx: Int,
        expr: S_Expr,
        subValues: MutableList<V_Expr>,
    ): V_Expr {
        val cExpr = expr.compileSafe(ctx)
        val vExpr = cExpr.vExpr()
        subValues.add(vExpr)

        val type = vExpr.type
        if (type.isError()) {
            return vExpr
        }

        val dependsOnThisAtExpr = vExpr.info.dependsOnAtExprs == immSetOf(atExprId)
        val attrName = vExpr.implicitAtWhereAttrName()

        return if (!dependsOnThisAtExpr && attrName != null) {
            val cAttrName = C_Name.make(vExpr.pos, attrName)
            compileWhereExprName(ctx, idx, vExpr, cAttrName, type)
        } else {
            compileWhereExprNoName(ctx, idx, vExpr, dependsOnThisAtExpr)
        }
    }

    private fun compileWhereExprNoName(ctx: C_ExprContext, idx: Int, vExpr: V_Expr, dependsOnThisAtExpr: Boolean): V_Expr {
        val type = vExpr.type
        if (type == R_BooleanType || type == R_CtErrorType) {
            return vExpr
        }

        if (dependsOnThisAtExpr) {
            val msg = "Wrong type of ${whereExprMsg(idx)}"
            C_Errors.errTypeMismatch(ctx.msgCtx, vExpr.pos, type, R_BooleanType, "at_where:type:$idx", msg)
            return C_ExprUtils.errorVExpr(ctx, vExpr.pos, R_BooleanType)
        }

        val attrs = S_AtExpr.findWhereContextAttrsByType(ctx, vExpr.pos, type)
        if (attrs.isEmpty()) {
            ctx.msgCtx.error(vExpr.pos, "at_where_type:$idx:${type.strCode()}",
                    "No attribute matches type of ${whereExprMsg(idx)} (${type.str()})")
            return C_ExprUtils.errorVExpr(ctx, vExpr.pos)
        } else if (attrs.size > 1) {
            throw C_Errors.errMultipleAttrs(vExpr.pos, attrs, "at_attr_type_ambig:$idx:${type.strCode()}",
                    "Multiple attributes match type of ${whereExprMsg(idx)} (${type.str()})")
        }

        val attr = attrs[0]
        val attrExpr = attr.compile(ctx, vExpr.pos)
        return C_ExprUtils.makeVBinaryExprEq(ctx, vExpr.pos, attrExpr, vExpr)
    }

    private fun compileWhereExprName(ctx: C_ExprContext, idx: Int, vExpr: V_Expr, name: C_Name, type: R_Type): V_Expr {
        val entityAttrs = ctx.findWhereAttributesByName(name)
        if (entityAttrs.isEmpty() && type == R_BooleanType) {
            val msg = "No context attribute matches name '$name', but the expression is accepted" +
                    ", because its type is ${R_BooleanType.name}" +
                    " (suggestion: write <expression> == true for clarity)"
            ctx.msgCtx.warning(vExpr.pos, "at:where:name_boolean_no_attr:$name", msg)
            return vExpr
        }

        val entityAttr = ctx.msgCtx.consumeError {
            matchWhereAttribute(ctx, idx, vExpr.pos, name.rName, entityAttrs, type)
        }
        entityAttr ?: return C_ExprUtils.errorVExpr(ctx, vExpr.pos)

        val entityAttrExpr = entityAttr.compile(ctx, vExpr.pos)
        return C_ExprUtils.makeVBinaryExprEq(ctx, vExpr.pos, entityAttrExpr, vExpr)
    }

    private fun matchWhereAttribute(
        ctx: C_ExprContext,
        idx: Int,
        exprPos: S_Pos,
        name: R_Name,
        entityAttrsByName: List<C_AtFromImplicitAttr>,
        varType: R_Type
    ): C_AtFromImplicitAttr {
        val entityAttrsByType = if (entityAttrsByName.isNotEmpty()) {
            entityAttrsByName.filter { it.type == varType }
        } else {
            S_AtExpr.findWhereContextAttrsByType(ctx, exprPos, varType)
        }

        if (entityAttrsByType.isEmpty()) {
            throw C_Error.more(exprPos, "at_where:var_noattrs:$idx:$name:${varType.strCode()}",
                    "No attribute matches name '$name' or type ${varType.str()}")
        } else if (entityAttrsByType.size > 1) {
            if (entityAttrsByName.isEmpty()) {
                throw C_Errors.errMultipleAttrs(
                    exprPos,
                    entityAttrsByType,
                    "at_where:var_manyattrs_type:$idx:$name:${varType.strCode()}",
                    "Multiple attributes match expression type ${varType.str()}",
                )
            } else {
                throw C_Errors.errMultipleAttrs(
                    exprPos,
                    entityAttrsByType,
                    "at_where:var_manyattrs_nametype:$idx:$name:${varType.strCode()}",
                    "Multiple attributes match name '$name' and type ${varType.str()}",
                )
            }
        }

        return entityAttrsByType[0]
    }

    private fun makeWhere(ctx: C_ExprContext, compiledExprs: List<V_Expr>): V_Expr? {
        if (compiledExprs.isEmpty()) {
            return null
        }

        val vExpr = compiledExprs.foldSimple { left, right ->
            val opCtx = C_BinOpContext(ctx, right.pos)
            C_BinOp_And.compile(opCtx, left, right) ?: C_ExprUtils.errorVExpr(ctx, left.pos)
        }

        val value = vExpr.constantValue(V_ConstantValueEvalContext())
        return if (value == Rt_BooleanValue.TRUE) null else vExpr
    }

    private fun whereExprMsg(idx: Int): String {
        val idxMsg = if (exprs.size == 1) "" else " #${idx + 1}"
        return "where-expression$idxMsg"
    }
}

internal class S_AtExpr(
    val from: S_AtExprFrom,
    val cardinality: S_PosValue<R_AtCardinality>,
    val where: S_AtExprWhere,
    val what: S_AtExprWhat,
    val limit: S_Expr?,
    val offset: S_Expr?,
): S_Expr(from.startPos) {
    override fun compile(ctx: C_ExprContext, hint: C_ExprHint): C_Expr {
        return compile0(ctx, null)
    }

    override fun compileNestedAt(ctx: C_ExprContext, parentAtCtx: C_AtContext): C_Expr {
        return compile0(ctx, parentAtCtx)
    }

    override fun compileFromItem(ctx: C_ExprContext, itemCtx: C_AtFromItemContext, alias: C_Name?): C_AtFromItem {
        if (!itemCtx.atExprAllowed) {
            ctx.msgCtx.error(startPos, "expr:at:from:nested_at", "First from-expression must be a simple entity")
        }

        if (!itemCtx.isJoin) {
            return super.compileFromItem(ctx, itemCtx, alias)
        }

        JOIN_RESTRICTIONS.access(ctx.msgCtx, startPos)

        val fromCtx = itemCtx.fromCtx
        val atExprId = fromCtx.atExprId
        val subFromCtx = C_AtFromContext(cardinality.pos, atExprId, fromCtx.parentAtCtx)
        val cFrom = from.compileJoin(ctx, subFromCtx, alias)
        val cDetails = compileDetails(ctx, atExprId, cFrom)

        return cFrom.compileJoin(cDetails, itemCtx.isOuterJoin)
    }

    private fun compile0(ctx: C_ExprContext, parentAtCtx: C_AtContext?): C_Expr {
        val atExprId = ctx.appCtx.nextAtExprId()

        val fromCtx = C_AtFromContext(cardinality.pos, atExprId, parentAtCtx)
        val cFrom = from.compile(ctx, fromCtx)
        val cDetails = compileDetails(ctx, atExprId, cFrom)

        val vExpr = cFrom.compile(cDetails)
        return C_ValueExpr(vExpr)
    }

    private fun compileDetails(
        ctx: C_ExprContext,
        atExprId: R_AtExprId,
        cFrom: C_AtFrom,
    ): C_AtDetails {
        val subValues = cFrom.getAllExprs().toMutableList()

        val whereCtx = cFrom.innerExprCtx()
        val vWhere = where.compile(whereCtx, atExprId, subValues)

        val whatCtx = if (!WHERE_VAR_STATES_SWITCH.isActive(ctx)) whereCtx else {
            whereCtx.updateVarStates(vWhere?.varStatesDelta?.whenTrue ?: C_VarStatesDelta.EMPTY)
        }
        val cWhat = what.compile(whatCtx, cFrom, subValues)
        val cResult = compileAtResult(cWhat.allFields)

        val vLimit = compileLimitOffset(limit, "limit", ctx, subValues)
        val vOffset = compileLimitOffset(offset, "offset", ctx, subValues)

        val base = C_AtExprBase(cWhat, vWhere)
        val varStatesDelta = C_ExprVarStatesDelta.forExpressions(subValues)

        return C_AtDetails(startPos, cardinality, base, vLimit, vOffset, cResult, varStatesDelta)
    }

    private fun compileAtResult(whatFields: List<V_DbAtWhatField>): C_AtExprResult {
        val selFieldsIndexes = whatFields.withIndex().filter { !it.value.flags.omit }.mapToImmList { it.index }
        val selFields = selFieldsIndexes.map { whatFields[it] }

        val groupFieldsIndexes = whatFields.withIndex()
                .filter { it.value.summarization?.isGroup() ?: false }
                .mapToImmList { it.index }

        val hasAggregateFields = whatFields.any { !(it.summarization?.isGroup() ?: true) }

        val (rowDecoder, recordType) = compileRowDecoder(selFields)
        val resultType = C_AtExprResult.calcResultType(recordType, cardinality.value)

        return C_AtExprResult(
            recordType,
            resultType,
            rowDecoder,
            selFieldsIndexes,
            groupFieldsIndexes,
            hasAggregateFields,
        )
    }

    private fun compileRowDecoder(selFields: List<V_DbAtWhatField>): Pair<R_AtExprRowDecoder, R_Type> {
        return if (selFields.size == 1 && selFields[0].name == null) {
            R_AtExprRowDecoder_Simple to selFields[0].resultType
        } else if (selFields.isNotEmpty()) {
            val tupleFields = selFields.mapIndexedToImmList { i, field -> R_TupleField(i, field.name, field.resultType) }
            val type = R_TupleType(tupleFields)
            R_AtExprRowDecoder_Tuple(type) to type
        } else {
            R_AtExprRowDecoder_Simple to R_CtErrorType
        }
    }

    private fun compileLimitOffset(sExpr: S_Expr?, msg: String, ctx: C_ExprContext, subValues: MutableList<V_Expr>): V_Expr? {
        if (sExpr == null) {
            return null
        }

        val subCtx = ctx.copy(atCtx = null)
        val vExpr = sExpr.compile(subCtx).vExpr()
        subValues.add(vExpr)

        C_Types.match(R_IntegerType, vExpr.type, sExpr.startPos) { "expr_at_${msg}_type" toCodeMsg "Wrong $msg type" }
        return vExpr
    }

    internal companion object {
        private val JOIN_RESTRICTIONS = C_FeatureRestrictions.make("0.13.10", "at_expr_join", "Join syntax is")

        val WHERE_VAR_STATES_SWITCH = C_FeatureSwitch("0.14.0", false)

        fun findWhereContextAttrsByType(ctx: C_ExprContext, pos: S_Pos, type: R_Type): ImmList<C_AtFromImplicitAttr> {
            return if (type == R_BooleanType) {
                immListOf()
            } else {
                ctx.findWhereAttributesByType(pos, type)
            }
        }

        fun makeDbAtEntity(
            symCtx: C_SymbolContext,
            entity: R_EntityDefinition,
            alias: C_Name,
            explicitAlias: C_Name?,
            atEntityId: R_AtEntityId,
            comment: S_Comment?,
        ): C_AtEntity {
            val resComment = if (explicitAlias != null) comment else null
            val ideDef = makeDbAtIdeDef(symCtx, alias.str, alias.pos, entity, resComment)
            val dollarIdeDef = makeDbAtIdeDef(symCtx, C_Constants.AT_PLACEHOLDER, alias.pos, entity, resComment)

            return C_AtEntity(
                alias.pos,
                entity,
                alias.rName,
                explicitAlias != null,
                atEntityId,
                ideDef,
                dollarIdeDef.refInfo,
            )
        }

        private fun makeDbAtIdeDef(
            symCtx: C_SymbolContext,
            name: String,
            pos: S_Pos,
            rEntity: R_EntityDefinition,
            comment: S_Comment?,
        ): C_IdeSymbolDef {
            val docType = rEntity.type.docType()
            val docSymbol = symCtx.makeDocSymbol(
                DocSymbolKind.AT_VAR_DB,
                DocSymbolName.local(name),
                DocDeclarationProto_AtVariable(name, docType).toLazyDeclaration(),
                comment = comment,
            )
            return C_IdeSymbolDef.make(IdeSymbolKind.LOC_AT_ALIAS, link = IdeLocalSymbolLink(pos), doc = docSymbol)
        }

        fun makeColAtIdeDef(
            symCtx: C_SymbolContext,
            explicitAlias: R_Name?,
            pos: S_Pos,
            itemType: R_Type,
            comment: S_Comment?,
        ): C_IdeSymbolDef {
            val docName = explicitAlias?.str ?: C_Constants.AT_PLACEHOLDER
            val docType = itemType.docType()
            val docSymbol = symCtx.makeDocSymbol(
                DocSymbolKind.AT_VAR_COL,
                DocSymbolName.local(docName),
                DocDeclarationProto_AtVariable(docName, docType).toLazyDeclaration(),
                comment = comment,
            )
            return C_IdeSymbolDef.make(IdeSymbolKind.LOC_AT_ALIAS, link = IdeLocalSymbolLink(pos), doc = docSymbol)
        }
    }
}
