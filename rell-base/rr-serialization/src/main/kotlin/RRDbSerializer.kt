/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.serialization

import net.postchain.rell.base.model.rr.*
import rell.ir.*

// --- DbExpr serialization ---

fun SerializerContext.serializeDbExpr(expr: RR_DbExpr): Int {
    val (unionType, unionOffset) = serializeDbExprUnion(expr)
    DbExpr.startDbExpr(builder)
    DbExpr.addExprType(builder, unionType)
    DbExpr.addExpr(builder, unionOffset)
    return DbExpr.endDbExpr(builder)
}

private fun SerializerContext.serializeDbExprUnion(expr: RR_DbExpr): Pair<UByte, Int> = when (expr) {
    is RR_DbExpr.Interpreted -> {
        val e = serializeExpr(expr.expr)
        DbExprUnion.DbInterpretedExpr to DbInterpretedExpr.createDbInterpretedExpr(builder, e)
    }

    is RR_DbExpr.Binary -> {
        val t = serializeType(expr.type)
        val left = serializeDbExpr(expr.left)
        val right = serializeDbExpr(expr.right)
        DbBinaryExpr.startDbBinaryExpr(builder)
        DbBinaryExpr.addType(builder, t)
        DbBinaryExpr.addOp(builder, serializeDbBinaryOp(expr.op))
        DbBinaryExpr.addLeft(builder, left)
        DbBinaryExpr.addRight(builder, right)
        DbBinaryExpr.addNullableEq(builder, expr.nullableEq)
        DbExprUnion.DbBinaryExpr to DbBinaryExpr.endDbBinaryExpr(builder)
    }

    is RR_DbExpr.Unary -> {
        val t = serializeType(expr.type)
        val inner = serializeDbExpr(expr.expr)
        DbUnaryExpr.startDbUnaryExpr(builder)
        DbUnaryExpr.addType(builder, t)
        DbUnaryExpr.addOp(builder, serializeDbUnaryOp(expr.op))
        DbUnaryExpr.addExpr(builder, inner)
        DbExprUnion.DbUnaryExpr to DbUnaryExpr.endDbUnaryExpr(builder)
    }

    is RR_DbExpr.Entity -> {
        DbExprUnion.DbEntityExpr to DbEntityExpr.createDbEntityExpr(
            builder,
            expr.entityDefIndex.toUInt(),
            expr.entityId.toUInt(),
        )
    }

    is RR_DbExpr.Rel -> {
        val base = serializeDbExpr(expr.base)
        val name = createString(expr.attrName)
        DbRelExpr.startDbRelExpr(builder)
        DbRelExpr.addBase(builder, base)
        DbRelExpr.addAttrName(builder, name)
        DbRelExpr.addTargetEntityDefIndex(builder, expr.targetEntityDefIndex.toUInt())
        DbExprUnion.DbRelExpr to DbRelExpr.endDbRelExpr(builder)
    }

    is RR_DbExpr.Attr -> {
        val base = serializeDbExpr(expr.base)
        val name = createString(expr.attrName)
        val t = serializeType(expr.type)
        DbAttrExpr.startDbAttrExpr(builder)
        DbAttrExpr.addBase(builder, base)
        DbAttrExpr.addAttrName(builder, name)
        DbAttrExpr.addType(builder, t)
        DbExprUnion.DbAttrExpr to DbAttrExpr.endDbAttrExpr(builder)
    }

    is RR_DbExpr.Rowid -> {
        val base = serializeDbExpr(expr.base)
        DbExprUnion.DbRowidExpr to DbRowidExpr.createDbRowidExpr(builder, base)
    }

    is RR_DbExpr.CollectionInterpreted -> {
        val e = serializeExpr(expr.expr)
        DbExprUnion.DbCollectionInterpretedExpr to DbCollectionInterpretedExpr.createDbCollectionInterpretedExpr(
            builder,
            e,
        )
    }

    is RR_DbExpr.In -> {
        val key = serializeDbExpr(expr.keyExpr)
        val exprs = expr.exprs.map { serializeDbExpr(it) }.toIntArray()
        val exprsVec = builder.createVectorOfTables(exprs)
        DbInExpr.startDbInExpr(builder)
        DbInExpr.addKeyExpr(builder, key)
        DbInExpr.addExprs(builder, exprsVec)
        DbInExpr.addNot(builder, expr.not)
        DbExprUnion.DbInExpr to DbInExpr.endDbInExpr(builder)
    }

    is RR_DbExpr.Elvis -> {
        val t = serializeType(expr.type)
        val left = serializeDbExpr(expr.left)
        val right = serializeDbExpr(expr.right)
        DbExprUnion.DbElvisExpr to DbElvisExpr.createDbElvisExpr(builder, t, left, right)
    }

    is RR_DbExpr.Call -> {
        val t = serializeType(expr.type)
        val fn = serializeDbSysFn(expr.fn)
        val args = expr.args.map { serializeDbExpr(it) }.toIntArray()
        val argsVec = builder.createVectorOfTables(args)
        DbCallExpr.startDbCallExpr(builder)
        DbCallExpr.addType(builder, t)
        DbCallExpr.addFnName(builder, fn)
        DbCallExpr.addArgs(builder, argsVec)
        DbExprUnion.DbCallExpr to DbCallExpr.endDbCallExpr(builder)
    }

    is RR_DbExpr.Exists -> {
        val sub = serializeDbExpr(expr.subExpr)
        DbExistsExpr.startDbExistsExpr(builder)
        DbExistsExpr.addSubExpr(builder, sub)
        DbExistsExpr.addNot(builder, expr.not)
        DbExprUnion.DbExistsExpr to DbExistsExpr.endDbExistsExpr(builder)
    }

    is RR_DbExpr.InCollection -> {
        val left = serializeDbExpr(expr.left)
        val right = serializeExpr(expr.right)
        DbInCollectionExpr.startDbInCollectionExpr(builder)
        DbInCollectionExpr.addLeft(builder, left)
        DbInCollectionExpr.addRight(builder, right)
        DbInCollectionExpr.addNot(builder, expr.not)
        DbExprUnion.DbInCollectionExpr to DbInCollectionExpr.endDbInCollectionExpr(builder)
    }

    is RR_DbExpr.When -> {
        val t = serializeType(expr.type)
        val keyExpr = expr.keyExpr?.let { serializeDbExpr(it) }
        val cases = expr.cases.map { case ->
            val conds = case.conds.map { serializeDbExpr(it) }.toIntArray()
            val condsVec = builder.createVectorOfTables(conds)
            val caseExpr = serializeDbExpr(case.expr)
            DbWhenCase.createDbWhenCase(builder, condsVec, caseExpr)
        }.toIntArray()
        val casesVec = builder.createVectorOfTables(cases)
        val elseExpr = expr.elseExpr?.let { serializeDbExpr(it) }
        DbWhenExpr.startDbWhenExpr(builder)
        DbWhenExpr.addType(builder, t)
        if (keyExpr != null) DbWhenExpr.addKeyExpr(builder, keyExpr)
        DbWhenExpr.addCases(builder, casesVec)
        if (elseExpr != null) DbWhenExpr.addElseExpr(builder, elseExpr)
        DbExprUnion.DbWhenExpr to DbWhenExpr.endDbWhenExpr(builder)
    }

    is RR_DbExpr.NestedAt -> {
        val t = serializeType(expr.type)
        val inner = serializeDbExpr(expr.inner)
        DbExprUnion.DbNestedAtExpr to DbNestedAtExpr.createDbNestedAtExpr(builder, t, inner)
    }

    is RR_DbExpr.SubQuery -> {
        val from = serializeDbAtFrom(expr.from)
        val what = serializeDbAtWhatFields(expr.what)
        val where = expr.where?.let { serializeDbExpr(it) }
        val extras = expr.extras?.let { serializeAtExtras(it) }
        val internals = serializeDbAtInternals(expr.internals)
        DbSubQueryExpr.startDbSubQueryExpr(builder)
        DbSubQueryExpr.addFrom(builder, from)
        DbSubQueryExpr.addWhat(builder, what)
        if (where != null) DbSubQueryExpr.addWhere(builder, where)
        if (extras != null) DbSubQueryExpr.addExtras(builder, extras)
        DbSubQueryExpr.addIsMany(builder, expr.isMany)
        DbSubQueryExpr.addInternals(builder, internals)
        DbExprUnion.DbSubQueryExpr to DbSubQueryExpr.endDbSubQueryExpr(builder)
    }
}

// --- At-expression support ---

fun SerializerContext.serializeDbAtEntity(entity: RR_DbAtEntity): Int {
    val joinWhere = entity.joinWhere?.let { serializeDbExpr(it) }
    val joinBlock = entity.joinBlock?.let { serializeFrameBlock(it) }
    DbAtEntity.startDbAtEntity(builder)
    DbAtEntity.addEntityDefIndex(builder, entity.entityDefIndex.toUInt())
    DbAtEntity.addId(builder, entity.entityId.toUInt())
    if (joinWhere != null) DbAtEntity.addJoinWhere(builder, joinWhere)
    DbAtEntity.addIsOuter(builder, entity.isOuter)
    if (joinBlock != null) DbAtEntity.addJoinBlock(builder, joinBlock)
    return DbAtEntity.endDbAtEntity(builder)
}

fun SerializerContext.serializeDbAtFrom(from: RR_DbAtFrom): Int {
    val entities = from.entities.map { serializeDbAtEntity(it) }.toIntArray()
    val entitiesVec = builder.createVectorOfTables(entities)
    val block = from.block?.let { serializeFrameBlock(it) }
    DbAtExprFrom.startDbAtExprFrom(builder)
    DbAtExprFrom.addEntities(builder, entitiesVec)
    if (block != null) DbAtExprFrom.addBlock(builder, block)
    return DbAtExprFrom.endDbAtExprFrom(builder)
}

fun SerializerContext.serializeDbAtWhatFields(fields: List<RR_DbAtWhatField>): Int {
    val offsets = fields.map { field ->
        val flags = serializeAtWhatFieldFlags(field.flags)
        val expr = serializeDbExpr(field.expr)
        val resultType = serializeType(field.resultType)
        DbAtWhatField.startDbAtWhatField(builder)
        DbAtWhatField.addFlags(builder, flags)
        DbAtWhatField.addExpr(builder, expr)
        DbAtWhatField.addResultType(builder, resultType)
        DbAtWhatField.endDbAtWhatField(builder)
    }.toIntArray()
    return builder.createVectorOfTables(offsets)
}

private fun SerializerContext.serializeAtWhatFieldFlags(flags: RR_AtWhatFieldFlags): Int {
    AtWhatFieldFlags.startAtWhatFieldFlags(builder)
    AtWhatFieldFlags.addOmit(builder, flags.omit)
    AtWhatFieldFlags.addSort(builder, flags.sort)
    AtWhatFieldFlags.addGroup(builder, flags.group)
    AtWhatFieldFlags.addAggregate(builder, flags.aggregate)
    return AtWhatFieldFlags.endAtWhatFieldFlags(builder)
}

fun SerializerContext.serializeDbAtInternals(internals: RR_DbAtInternals): Int {
    val block = internals.block?.let { serializeFrameBlock(it) }
    DbAtExprInternals.startDbAtExprInternals(builder)
    if (block != null) DbAtExprInternals.addBlock(builder, block)
    return DbAtExprInternals.endDbAtExprInternals(builder)
}

fun SerializerContext.serializeWhatFieldGroups(groups: List<RR_DbAtWhatFieldGroup>): Int {
    val offsets = groups.map { serializeWhatFieldGroup(it) }.toIntArray()
    return builder.createVectorOfTables(offsets)
}

private fun SerializerContext.serializeWhatFieldGroup(group: RR_DbAtWhatFieldGroup): Int {
    val combiner = serializeDbAtFieldCombiner(group.combiner)
    val rExprs = group.rExprs?.let { exprs ->
        val offs = exprs.map { serializeExpr(it) }.toIntArray()
        builder.createVectorOfTables(offs)
    }
    val itemOrder = group.itemOrder?.let { items ->
        val offs = items.map { (isDb, idx) ->
            ItemOrderEntry.createItemOrderEntry(builder, isDb, idx)
        }.toIntArray()
        builder.createVectorOfTables(offs)
    }
    val subGroups = group.subGroups?.let { subs ->
        val offs = subs.map { serializeWhatFieldGroup(it) }.toIntArray()
        builder.createVectorOfTables(offs)
    }
    DbAtWhatFieldGroup.startDbAtWhatFieldGroup(builder)
    DbAtWhatFieldGroup.addColumnCount(builder, group.columnCount)
    DbAtWhatFieldGroup.addCombiner(builder, combiner)
    if (rExprs != null) DbAtWhatFieldGroup.addRExprs(builder, rExprs)
    if (itemOrder != null) DbAtWhatFieldGroup.addItemOrder(builder, itemOrder)
    if (subGroups != null) DbAtWhatFieldGroup.addSubGroups(builder, subGroups)
    return DbAtWhatFieldGroup.endDbAtWhatFieldGroup(builder)
}

private fun SerializerContext.serializeDbAtFieldCombiner(combiner: RR_DbAtFieldCombiner): Int {
    val (unionType, unionOffset) = when (combiner) {
        is RR_DbAtFieldCombiner.Single -> {
            DbAtFieldCombiner_Single.startDbAtFieldCombiner_Single(builder)
            DbAtFieldCombinerUnion.DbAtFieldCombiner_Single to DbAtFieldCombiner_Single.endDbAtFieldCombiner_Single(
                builder,
            )
        }

        is RR_DbAtFieldCombiner.Tuple -> {
            val t = serializeType(combiner.tupleType)
            DbAtFieldCombinerUnion.DbAtFieldCombiner_Tuple to DbAtFieldCombiner_Tuple.createDbAtFieldCombiner_Tuple(
                builder,
                t,
            )
        }

        is RR_DbAtFieldCombiner.Struct -> {
            val mapping = if (combiner.attrMapping.isNotEmpty()) {
                DbAtFieldCombiner_Struct.createAttrMappingVector(builder, combiner.attrMapping.toIntArray())
            } else 0
            DbAtFieldCombiner_Struct.startDbAtFieldCombiner_Struct(builder)
            DbAtFieldCombiner_Struct.addStructDefIndex(builder, combiner.structDefIndex)
            if (mapping != 0) DbAtFieldCombiner_Struct.addAttrMapping(builder, mapping)
            DbAtFieldCombinerUnion.DbAtFieldCombiner_Struct to DbAtFieldCombiner_Struct.endDbAtFieldCombiner_Struct(
                builder,
            )
        }

        is RR_DbAtFieldCombiner.FunctionCall -> {
            val call = serializeRRFunctionCall(combiner.call)
            DbAtFieldCombinerUnion.DbAtFieldCombiner_FunctionCall to DbAtFieldCombiner_FunctionCall.createDbAtFieldCombiner_FunctionCall(
                builder,
                call,
            )
        }

        is RR_DbAtFieldCombiner.ListLiteral -> {
            val t = serializeType(combiner.listType)
            DbAtFieldCombinerUnion.DbAtFieldCombiner_ListLiteral to DbAtFieldCombiner_ListLiteral.createDbAtFieldCombiner_ListLiteral(
                builder,
                t,
            )
        }

        is RR_DbAtFieldCombiner.MapLiteral -> {
            val t = serializeType(combiner.mapType)
            DbAtFieldCombinerUnion.DbAtFieldCombiner_MapLiteral to DbAtFieldCombiner_MapLiteral.createDbAtFieldCombiner_MapLiteral(
                builder,
                t,
            )
        }

        is RR_DbAtFieldCombiner.MemberAccess -> {
            val calc = serializeRRMemberCalculator(combiner.calculator)
            DbAtFieldCombiner_MemberAccess.startDbAtFieldCombiner_MemberAccess(builder)
            DbAtFieldCombiner_MemberAccess.addCalculator(builder, calc)
            DbAtFieldCombiner_MemberAccess.addSafe(builder, combiner.safe)
            DbAtFieldCombinerUnion.DbAtFieldCombiner_MemberAccess to DbAtFieldCombiner_MemberAccess.endDbAtFieldCombiner_MemberAccess(
                builder,
            )
        }

        is RR_DbAtFieldCombiner.Lazy -> {
            val t = serializeType(combiner.type)
            DbAtFieldCombinerUnion.DbAtFieldCombiner_Lazy to DbAtFieldCombiner_Lazy.createDbAtFieldCombiner_Lazy(
                builder,
                t,
            )
        }
    }
    DbAtFieldCombiner.startDbAtFieldCombiner(builder)
    DbAtFieldCombiner.addCombinerType(builder, unionType)
    DbAtFieldCombiner.addCombiner(builder, unionOffset)
    return DbAtFieldCombiner.endDbAtFieldCombiner(builder)
}

fun SerializerContext.serializeAtExtras(extras: RR_AtExtras): Int {
    val limit = extras.limit?.let { serializeExpr(it) }
    val offset = extras.offset?.let { serializeExpr(it) }
    AtExprExtras.startAtExprExtras(builder)
    if (limit != null) AtExprExtras.addLimit(builder, limit)
    if (offset != null) AtExprExtras.addOffset(builder, offset)
    return AtExprExtras.endAtExprExtras(builder)
}

fun serializeAtCardinality(cardinality: RR_AtCardinality): UByte = when (cardinality) {
    RR_AtCardinality.ZERO_ONE -> AtCardinality.ZERO_ONE
    RR_AtCardinality.ONE -> AtCardinality.ONE
    RR_AtCardinality.ZERO_MANY -> AtCardinality.ZERO_MANY
    RR_AtCardinality.ONE_MANY -> AtCardinality.ONE_MANY
}

// --- ColAt support ---

fun SerializerContext.serializeColAtParam(param: RR_ColAtParam): Int {
    val t = serializeType(param.type)
    ColAtParam.startColAtParam(builder)
    ColAtParam.addType(builder, t)
    ColAtParam.addPtr(builder, VarPtr.createVarPtr(builder, param.ptr.blockUid.toUInt(), param.ptr.offset))
    return ColAtParam.endColAtParam(builder)
}

fun SerializerContext.serializeColAtFrom(from: RR_ColAtFrom): Int {
    val expr = serializeExpr(from.expr)
    val block = from.block?.let { serializeFrameBlock(it) }
    val adapter = when (from.iterableAdapter) {
        RR_IterableAdapterKind.DIRECT -> IterableAdapterKind.DIRECT
        RR_IterableAdapterKind.LEGACY_MAP -> IterableAdapterKind.LEGACY_MAP
    }
    ColAtFrom.startColAtFrom(builder)
    ColAtFrom.addExpr(builder, expr)
    if (block != null) ColAtFrom.addBlock(builder, block)
    ColAtFrom.addIterableAdapter(builder, adapter)
    return ColAtFrom.endColAtFrom(builder)
}

fun SerializerContext.serializeColAtWhat(what: RR_ColAtWhat): Int {
    val fields = what.fields.map { field ->
        val expr = serializeExpr(field.expr)
        val flags = serializeAtWhatFieldFlags(field.flags)
        ColAtWhatField.startColAtWhatField(builder)
        ColAtWhatField.addExpr(builder, expr)
        ColAtWhatField.addFlags(builder, flags)
        ColAtWhatField.endColAtWhatField(builder)
    }.toIntArray()
    val fieldsVec = builder.createVectorOfTables(fields)
    val selectedVec = ColAtWhat.createSelectedFieldsVector(builder, what.selectedFields.toIntArray())
    val groupVec = what.groupFields?.let { ColAtWhat.createGroupFieldsVector(builder, it.toIntArray()) }
    ColAtWhat.startColAtWhat(builder)
    ColAtWhat.addFields(builder, fieldsVec)
    ColAtWhat.addFieldCount(builder, what.fieldCount)
    ColAtWhat.addSelectedFields(builder, selectedVec)
    if (groupVec != null) ColAtWhat.addGroupFields(builder, groupVec)
    return ColAtWhat.endColAtWhat(builder)
}

fun serializeColAtSummarizationKind(kind: RR_ColAtSummarizationKind): UByte = when (kind) {
    RR_ColAtSummarizationKind.NONE -> ColAtSummarizationKind.NONE
    RR_ColAtSummarizationKind.GROUP -> ColAtSummarizationKind.GROUP
    RR_ColAtSummarizationKind.ALL -> ColAtSummarizationKind.ALL
}

fun serializeColAtFieldSummarizationKind(kind: RR_ColAtFieldSummarizationKind): UByte = when (kind) {
    RR_ColAtFieldSummarizationKind.NONE -> ColAtFieldSummarizationKind.NONE
    RR_ColAtFieldSummarizationKind.GROUP -> ColAtFieldSummarizationKind.GROUP
    RR_ColAtFieldSummarizationKind.SUM -> ColAtFieldSummarizationKind.SUM
    RR_ColAtFieldSummarizationKind.MIN -> ColAtFieldSummarizationKind.MIN
    RR_ColAtFieldSummarizationKind.MAX -> ColAtFieldSummarizationKind.MAX
    RR_ColAtFieldSummarizationKind.LIST -> ColAtFieldSummarizationKind.LIST
    RR_ColAtFieldSummarizationKind.SET -> ColAtFieldSummarizationKind.SET
    RR_ColAtFieldSummarizationKind.MAP -> ColAtFieldSummarizationKind.MAP
}

// --- DbSysFn serialization ---

private fun SerializerContext.serializeDbSysFn(fn: RR_DbSysFn): Int = when (fn) {
    is RR_DbSysFn.Simple -> createString(fn.sql)
    is RR_DbSysFn.Template -> {
        val repr = fn.fragments.joinToString("") { fragment ->
            when (fragment) {
                is RR_DbSysFnFragment.Text -> fragment.text
                is RR_DbSysFnFragment.Arg -> "#{${fragment.index}}"
            }
        }
        createString(repr)
    }
}
