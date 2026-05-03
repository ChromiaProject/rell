/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.expr

import net.postchain.rell.base.model.*
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.immListOf

class Db_AtFromItem(
    val atEntity: R_DbAtEntity,
    val isOuter: Boolean,
    val where: Db_Expr?,
    val rBlock: R_FrameBlock?,
)

sealed interface Db_AtWhatValue

class Db_AtWhatValue_RExpr(val expr: R_Expr): Db_AtWhatValue

class Db_AtWhatValue_DbExpr(val expr: Db_Expr, val resultType: R_Type): Db_AtWhatValue

abstract class Db_ComplexAtWhatEvaluator {
    /** Returns structured metadata for RR_ resolution. Override in each evaluator implementation. */
    open fun combinerInfo(): Db_AtWhatCombinerInfo? = null
}

/**
 * Structured metadata describing what kind of computation a [Db_ComplexAtWhatEvaluator] performs.
 * Used by the RR_ resolver to construct proper combiner variants instead of
 * falling back to the legacy RCalc escape hatch.
 */
sealed class Db_AtWhatCombinerInfo {
    class Tuple(val type: R_TupleType) : Db_AtWhatCombinerInfo()
    class Struct(val struct: R_Struct, val attrMapping: ImmList<Int> = immListOf()) : Db_AtWhatCombinerInfo()
    class ListLiteral(val type: R_Type) : Db_AtWhatCombinerInfo()
    class MapLiteral(val type: R_Type) : Db_AtWhatCombinerInfo()
    class FunctionCall(
            val returnType: R_Type,
            val target: R_FunctionCallTarget,
            val callFilePos: FilePos,
            val paramsToExprs: ImmList<Int>,
    ) : Db_AtWhatCombinerInfo()
    class PartialFunctionCall(
        val returnType: R_Type,
        val target: R_FunctionCallTarget,
        val mapping: R_PartialCallMapping,
    ) : Db_AtWhatCombinerInfo()
    class MemberAccess(val calculator: R_MemberCalculator, val safe: Boolean) : Db_AtWhatCombinerInfo()
    class Lazy(val type: R_Type) : Db_AtWhatCombinerInfo()
}

class Db_AtWhatValue_Complex(
    val subWhatValues: ImmList<Db_AtWhatValue>,
    val rExprs: ImmList<R_Expr>,
    val items: ImmList<Pair<Boolean, Int>>,
    val evaluator: Db_ComplexAtWhatEvaluator,
): Db_AtWhatValue {
    init {
        items.forEach { (db, i) ->
            require(i >= 0 && i < (if (db) subWhatValues else rExprs).size) { "$db $i" }
        }
    }
}

class Db_AtWhatValue_ToStruct(
    val rStruct: R_Struct,
    val exprs: ImmList<Db_Expr>,
): Db_AtWhatValue

class Db_AtWhatField(
    val flags: R_AtWhatFieldFlags,
    val value: Db_AtWhatValue,
)

class Db_AtExprFrom(
    val from: ImmList<Db_AtFromItem>,
    val block: R_FrameBlock? = null,
) {
    init {
        val fromEntities = this.from.map { it.atEntity }
        R_DbAtEntity.checkList(fromEntities)
    }
}

class Db_AtExprBase(
    val from: Db_AtExprFrom,
    val what: ImmList<Db_AtWhatField>,
    val where: Db_Expr?,
    val isMany: Boolean,
)

class Db_NestedAtExpr(
    type: R_Type,
    val base: Db_AtExprBase,
    val extras: R_AtExprExtras,
    val block: R_FrameBlock,
): Db_Expr(type)
