/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.expr

import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.rr.RR_ConstantValue
import net.postchain.rell.base.model.stmt.R_IterableAdapter
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.checkEquals

class R_ColAtParam(val type: R_Type, val ptr: R_VarPtr)

sealed class R_ColAtFieldSummarization

object R_ColAtFieldSummarization_None: R_ColAtFieldSummarization()

class R_ColAtFieldSummarization_Group: R_ColAtFieldSummarization()

sealed class R_ColAtFieldSummarization_Aggregate: R_ColAtFieldSummarization()

class R_ColAtFieldSummarization_Aggregate_Sum(
    val op: R_BinaryOp,
    val zeroValue: RR_ConstantValue,
): R_ColAtFieldSummarization_Aggregate()

class R_ColAtFieldSummarization_Aggregate_MinMax(
    val rCmpOp: R_CmpOp,
): R_ColAtFieldSummarization_Aggregate()

class R_ColAtFieldSummarization_Aggregate_List(
    val listType: R_ListType,
): R_ColAtFieldSummarization_Aggregate()

class R_ColAtFieldSummarization_Aggregate_Set(
    val setType: R_SetType,
): R_ColAtFieldSummarization_Aggregate()

class R_ColAtFieldSummarization_Aggregate_Map(
    val mapType: R_MapType,
): R_ColAtFieldSummarization_Aggregate()

class R_ColAtWhatField(val expr: R_Expr, val flags: R_AtWhatFieldFlags, val summarization: R_ColAtFieldSummarization)

data class R_ColAtSortEntry(val fieldIndex: Int, val ascending: Boolean)

class R_ColAtWhatExtras(
    val fieldCount: Int,
    val selectedFields: ImmList<Int>,
    val groupFields: ImmList<Int>,
    val sorting: ImmList<R_ColAtSortEntry>,
) {
    init {
        val fieldIndices = 0 until fieldCount
        selectedFields.forEach { check(it in fieldIndices) }
        groupFields.forEach { check(it in fieldIndices) }
        sorting.forEach { check(it.fieldIndex in fieldIndices) }
    }
}

class R_ColAtWhat(
        val fields: ImmList<R_ColAtWhatField>,
        val extras: R_ColAtWhatExtras
) {
    init {
        checkEquals(extras.fieldCount, fields.size)
    }
}

sealed class R_ColAtSummarization

class R_ColAtSummarization_None: R_ColAtSummarization()
class R_ColAtSummarization_Group: R_ColAtSummarization()
class R_ColAtSummarization_All: R_ColAtSummarization()

class R_ColAtFrom(
    val iterableAdapter: R_IterableAdapter,
    val expr: R_Expr,
    val block: R_FrameBlock?,
)

class R_ColAtExpr(
    type: R_Type,
    val block: R_FrameBlock,
    val param: R_ColAtParam,
    val from: R_ColAtFrom,
    val what: R_ColAtWhat,
    val where: R_Expr,
    val summarization: R_ColAtSummarization,
    val errPos: ErrorPos,
    cardinality: AtCardinality,
    extras: R_AtExprExtras,
): R_AtExpr(type, cardinality, extras)
