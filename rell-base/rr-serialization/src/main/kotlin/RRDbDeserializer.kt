/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.serialization

import net.postchain.rell.base.model.rr.*
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.mapToImmList
import net.postchain.rell.base.utils.toImmList
import rell.ir.*

// --- DbExpr deserialization ---

fun deserializeDbExpr(fb: DbExpr?): RR_DbExpr = when (fb?.exprType) {
    null -> RR_DbExpr.Interpreted(RR_Expr.Error(RR_Type.Error, "null db expr"))
    DbExprUnion.DbInterpretedExpr -> {
        val e = DbInterpretedExpr().also { fb.expr(it) }
        RR_DbExpr.Interpreted(deserializeExpr(e.expr))
    }

    DbExprUnion.DbBinaryExpr -> {
        val e = DbBinaryExpr().also { fb.expr(it) }
        RR_DbExpr.Binary(
            deserializeType(e.type),
            deserializeDbBinaryOp(e.op),
            deserializeDbExpr(e.left),
            deserializeDbExpr(e.right),
            e.nullableEq,
        )
    }

    DbExprUnion.DbUnaryExpr -> {
        val e = DbUnaryExpr().also { fb.expr(it) }
        RR_DbExpr.Unary(deserializeType(e.type), deserializeDbUnaryOp(e.op), deserializeDbExpr(e.expr))
    }

    DbExprUnion.DbEntityExpr -> {
        val e = DbEntityExpr().also { fb.expr(it) }
        RR_DbExpr.Entity(e.entityDefIndex.toInt(), e.entityId.toInt())
    }

    DbExprUnion.DbRelExpr -> {
        val e = DbRelExpr().also { fb.expr(it) }
        RR_DbExpr.Rel(deserializeDbExpr(e.base), e.attrName, e.targetEntityDefIndex.toInt())
    }

    DbExprUnion.DbAttrExpr -> {
        val e = DbAttrExpr().also { fb.expr(it) }
        RR_DbExpr.Attr(deserializeDbExpr(e.base), e.attrName, deserializeType(e.type))
    }

    DbExprUnion.DbRowidExpr -> {
        val e = DbRowidExpr().also { fb.expr(it) }
        RR_DbExpr.Rowid(deserializeDbExpr(e.base))
    }

    DbExprUnion.DbCollectionInterpretedExpr -> {
        val e = DbCollectionInterpretedExpr().also { fb.expr(it) }
        RR_DbExpr.CollectionInterpreted(deserializeExpr(e.expr))
    }

    DbExprUnion.DbInExpr -> {
        val e = DbInExpr().also { fb.expr(it) }
        val exprs = (0 until e.exprsLength).mapToImmList { deserializeDbExpr(e.exprs(it)) }
        RR_DbExpr.In(deserializeDbExpr(e.keyExpr), exprs, e.not)
    }

    DbExprUnion.DbElvisExpr -> {
        val e = DbElvisExpr().also { fb.expr(it) }
        RR_DbExpr.Elvis(deserializeType(e.type), deserializeDbExpr(e.left), deserializeDbExpr(e.right))
    }

    DbExprUnion.DbCallExpr -> {
        val e = DbCallExpr().also { fb.expr(it) }
        val args = (0 until e.argsLength).mapToImmList { deserializeDbExpr(e.args(it)) }
        val fn = deserializeDbSysFn(e.fnName)
        RR_DbExpr.Call(deserializeType(e.type), fn, args)
    }

    DbExprUnion.DbExistsExpr -> {
        val e = DbExistsExpr().also { fb.expr(it) }
        RR_DbExpr.Exists(deserializeDbExpr(e.subExpr), e.not)
    }

    DbExprUnion.DbInCollectionExpr -> {
        val e = DbInCollectionExpr().also { fb.expr(it) }
        RR_DbExpr.InCollection(deserializeDbExpr(e.left), deserializeExpr(e.right), e.not)
    }

    DbExprUnion.DbWhenExpr -> {
        val e = DbWhenExpr().also { fb.expr(it) }
        val keyExpr = e.keyExpr?.let { deserializeDbExpr(it) }
        val cases = (0 until e.casesLength).mapToImmList { i ->
            val c = e.cases(i)!!
            val conds = (0 until c.condsLength).mapToImmList { j -> deserializeDbExpr(c.conds(j)) }
            RR_DbWhenCase(conds, deserializeDbExpr(c.expr))
        }
        val elseExpr = e.elseExpr?.let { deserializeDbExpr(it) }
        RR_DbExpr.When(deserializeType(e.type), keyExpr, cases, elseExpr, e.hasElse)
    }

    DbExprUnion.DbNestedAtExpr -> {
        val e = DbNestedAtExpr().also { fb.expr(it) }
        RR_DbExpr.NestedAt(deserializeType(e.type), deserializeDbExpr(e.inner))
    }

    DbExprUnion.DbSubQueryExpr -> {
        val e = DbSubQueryExpr().also { fb.expr(it) }
        val from = deserializeDbAtFrom(e.from)
        val what = deserializeDbAtWhatFields(e.whatLength) { e.what(it) }
        val where = e.where?.let { deserializeDbExpr(it) }
        val extras = e.extras?.let { deserializeAtExtras(it) }
        val internals = deserializeDbAtInternals(e.internals)
        RR_DbExpr.SubQuery(from, what, where, extras, e.isMany, internals)
    }

    else -> RR_DbExpr.Interpreted(RR_Expr.Error(RR_Type.Error, "unknown db expr union: ${fb.exprType}"))
}

// --- At-expression support ---

fun deserializeDbAtEntity(fb: DbAtEntity?): RR_DbAtEntity {
    if (fb == null) error("Null DbAtEntity")
    return RR_DbAtEntity(
        entityDefIndex = fb.entityDefIndex.toInt(),
        entityId = fb.id.toInt(),
        joinWhere = fb.joinWhere?.let { deserializeDbExpr(it) },
        isOuter = fb.isOuter,
        joinBlock = fb.joinBlock?.let { deserializeFrameBlock(it) },
    )
}

fun deserializeDbAtFrom(fb: DbAtExprFrom?): RR_DbAtFrom {
    if (fb == null) error("Null DbAtExprFrom")
    val entities = (0 until fb.entitiesLength).mapToImmList { deserializeDbAtEntity(fb.entities(it)) }
    return RR_DbAtFrom(entities, fb.block?.let { deserializeFrameBlock(it) })
}

fun deserializeDbAtWhatFields(count: Int, accessor: (Int) -> DbAtWhatField?): ImmList<RR_DbAtWhatField> =
    (0 until count).mapToImmList { i ->
        val f = accessor(i)!!
        val flags = f.flags.let { RR_AtWhatFieldFlags(it.omit, it.sort, it.group, it.aggregate) }
        RR_DbAtWhatField(flags, deserializeDbExpr(f.expr), f.resultType?.let { deserializeType(it) } ?: RR_Type.Error)
    }

fun deserializeDbAtInternals(fb: DbAtExprInternals?): RR_DbAtInternals =
    RR_DbAtInternals(fb?.block?.let { deserializeFrameBlock(it) })

fun deserializeAtExtras(fb: AtExprExtras?): RR_AtExtras? {
    if (fb == null) return null
    return RR_AtExtras(
        limit = fb.limit?.let { deserializeExpr(it) },
        offset = fb.offset?.let { deserializeExpr(it) },
    )
}

fun deserializeAtCardinality(fb: UByte): RR_AtCardinality = when (fb) {
    AtCardinality.ZERO_ONE -> RR_AtCardinality.ZERO_ONE
    AtCardinality.ONE -> RR_AtCardinality.ONE
    AtCardinality.ZERO_MANY -> RR_AtCardinality.ZERO_MANY
    AtCardinality.ONE_MANY -> RR_AtCardinality.ONE_MANY
    else -> RR_AtCardinality.ZERO_MANY
}

// --- ColAt support ---

fun deserializeColAtParam(fb: ColAtParam?): RR_ColAtParam {
    if (fb == null) error("Null ColAtParam")
    val ptr = fb.ptr?.let { RR_VarPtr(it.blockUid.toLong(), it.offset) } ?: RR_VarPtr(0, 0)
    return RR_ColAtParam(deserializeType(fb.type), ptr)
}

fun deserializeColAtFrom(fb: ColAtFrom?): RR_ColAtFrom {
    if (fb == null) error("Null ColAtFrom")
    val adapter = when (fb.iterableAdapter) {
        IterableAdapterKind.LEGACY_MAP -> RR_IterableAdapterKind.LEGACY_MAP
        else -> RR_IterableAdapterKind.DIRECT
    }
    return RR_ColAtFrom(deserializeExpr(fb.expr), fb.block?.let { deserializeFrameBlock(it) }, adapter)
}

fun deserializeColAtWhat(fb: ColAtWhat?): RR_ColAtWhat {
    if (fb == null) error("Null ColAtWhat")
    val fields = (0 until fb.fieldsLength).mapToImmList { i ->
        val f = fb.fields(i)
        val flags = f.flags.let { RR_AtWhatFieldFlags(it.omit, it.sort, it.group, it.aggregate) }
        RR_ColAtWhatField(deserializeExpr(f.expr), flags)
    }
    val selectedFields = (0 until fb.selectedFieldsLength).mapToImmList { fb.selectedFields(it) }
    val groupFields =
        if (fb.groupFieldsLength > 0) (0 until fb.groupFieldsLength).mapToImmList { fb.groupFields(it) } else null
    return RR_ColAtWhat(fields, fb.fieldCount, selectedFields, groupFields)
}

fun deserializeColAtSummarizationKind(fb: UByte): RR_ColAtSummarizationKind = when (fb) {
    ColAtSummarizationKind.GROUP -> RR_ColAtSummarizationKind.GROUP
    ColAtSummarizationKind.ALL -> RR_ColAtSummarizationKind.ALL
    else -> RR_ColAtSummarizationKind.NONE
}

fun deserializeColAtFieldSummarizationKind(fb: UByte): RR_ColAtFieldSummarizationKind = when (fb) {
    ColAtFieldSummarizationKind.GROUP -> RR_ColAtFieldSummarizationKind.GROUP
    ColAtFieldSummarizationKind.SUM -> RR_ColAtFieldSummarizationKind.SUM
    ColAtFieldSummarizationKind.MIN -> RR_ColAtFieldSummarizationKind.MIN
    ColAtFieldSummarizationKind.MAX -> RR_ColAtFieldSummarizationKind.MAX
    ColAtFieldSummarizationKind.LIST -> RR_ColAtFieldSummarizationKind.LIST
    ColAtFieldSummarizationKind.SET -> RR_ColAtFieldSummarizationKind.SET
    ColAtFieldSummarizationKind.MAP -> RR_ColAtFieldSummarizationKind.MAP
    else -> RR_ColAtFieldSummarizationKind.NONE
}

// --- WhatFieldGroup deserialization ---

fun deserializeWhatFieldGroup(fb: rell.ir.DbAtWhatFieldGroup): RR_DbAtWhatFieldGroup {
    val combiner = deserializeDbAtFieldCombiner(fb.combiner!!)
    val rExprs =
        if (fb.rExprsLength > 0) (0 until fb.rExprsLength).mapToImmList { deserializeExpr(fb.rExprs(it)!!) } else null
    val itemOrder = if (fb.itemOrderLength > 0) {
        (0 until fb.itemOrderLength).mapToImmList { i ->
            val entry = fb.itemOrder(i)!!
            entry.isDb to entry.index
        }
    } else null
    val subGroups = if (fb.subGroupsLength > 0) (0 until fb.subGroupsLength).mapToImmList {
        deserializeWhatFieldGroup(
            fb.subGroups(it)!!,
        )
    } else null
    return RR_DbAtWhatFieldGroup(fb.columnCount, combiner, rExprs, itemOrder, subGroups)
}

private fun deserializeDbAtFieldCombiner(fb: rell.ir.DbAtFieldCombiner): RR_DbAtFieldCombiner = when (fb.combinerType) {
    DbAtFieldCombinerUnion.DbAtFieldCombiner_Single -> RR_DbAtFieldCombiner.Single
    DbAtFieldCombinerUnion.DbAtFieldCombiner_Tuple -> {
        val c = DbAtFieldCombiner_Tuple().also { fb.combiner(it) }
        RR_DbAtFieldCombiner.Tuple(deserializeType(c.tupleType))
    }

    DbAtFieldCombinerUnion.DbAtFieldCombiner_Struct -> {
        val c = DbAtFieldCombiner_Struct().also { fb.combiner(it) }
        val mapping = (0 until c.attrMappingLength).mapToImmList { c.attrMapping(it) }
        RR_DbAtFieldCombiner.Struct(c.structDefIndex, mapping)
    }

    DbAtFieldCombinerUnion.DbAtFieldCombiner_FunctionCall -> {
        val c = DbAtFieldCombiner_FunctionCall().also { fb.combiner(it) }
        RR_DbAtFieldCombiner.FunctionCall(deserializeFunctionCall(c.call))
    }

    DbAtFieldCombinerUnion.DbAtFieldCombiner_ListLiteral -> {
        val c = DbAtFieldCombiner_ListLiteral().also { fb.combiner(it) }
        RR_DbAtFieldCombiner.ListLiteral(deserializeType(c.listType))
    }

    DbAtFieldCombinerUnion.DbAtFieldCombiner_MapLiteral -> {
        val c = DbAtFieldCombiner_MapLiteral().also { fb.combiner(it) }
        RR_DbAtFieldCombiner.MapLiteral(deserializeType(c.mapType))
    }

    DbAtFieldCombinerUnion.DbAtFieldCombiner_MemberAccess -> {
        val c = DbAtFieldCombiner_MemberAccess().also { fb.combiner(it) }
        RR_DbAtFieldCombiner.MemberAccess(deserializeMemberCalculator(c.calculator), c.safe)
    }

    DbAtFieldCombinerUnion.DbAtFieldCombiner_Lazy -> {
        val c = DbAtFieldCombiner_Lazy().also { fb.combiner(it) }
        RR_DbAtFieldCombiner.Lazy(deserializeType(c.type))
    }

    else -> RR_DbAtFieldCombiner.Single
}

// --- DbSysFn deserialization ---

private fun deserializeDbSysFn(repr: String): RR_DbSysFn {
    if (!repr.contains("#{")) return RR_DbSysFn.Simple(repr)
    val fragments = mutableListOf<RR_DbSysFnFragment>()
    var i = 0
    while (i < repr.length) {
        val idx = repr.indexOf("#{", i)
        if (idx < 0) {
            fragments.add(RR_DbSysFnFragment.Text(repr.substring(i)))
            break
        }
        if (idx > i) fragments.add(RR_DbSysFnFragment.Text(repr.substring(i, idx)))
        val end = repr.indexOf("}", idx)
        val argIdx = repr.substring(idx + 2, end).toInt()
        fragments.add(RR_DbSysFnFragment.Arg(argIdx))
        i = end + 1
    }
    return RR_DbSysFn.Template(fragments.toImmList())
}
