/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.expr

import net.postchain.rell.base.compiler.base.utils.C_LateGetter
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.rr.RR_ConstantValue
import net.postchain.rell.base.model.stmt.R_Statement
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.ImmMap
import net.postchain.rell.base.utils.checkEquals

abstract class R_Expr(open val type: R_Type)

abstract class R_BaseExpr(type: R_Type): R_Expr(type)

class R_ErrorExpr(type: R_Type, val message: String): R_BaseExpr(type)

sealed class R_DestinationExpr(type: R_Type): R_BaseExpr(type)

class R_VarExpr(type: R_Type, val ptr: R_VarPtr, val name: String): R_DestinationExpr(type)

class R_StructMemberExpr(val base: R_Expr, val attr: R_Attribute): R_DestinationExpr(attr.type)

class R_RRConstantValueExpr(type: R_Type, val rrValue: RR_ConstantValue): R_BaseExpr(type)

class R_TupleExpr(
    override val type: R_TupleType,
    val exprs: ImmList<R_Expr>,
): R_BaseExpr(type)

class R_ListLiteralExpr(override val type: R_ListType, val exprs: ImmList<R_Expr>): R_BaseExpr(type)

class R_MapLiteralExpr(
    override val type: R_MapType,
    val entries: ImmList<Pair<R_Expr, R_Expr>>,
    val errPos: ErrorPos,
): R_BaseExpr(type)

class R_ListSubscriptExpr(
    type: R_Type,
    val base: R_Expr,
    val expr: R_Expr,
    val errPos: ErrorPos,
): R_DestinationExpr(type)

class R_VirtualListSubscriptExpr(
    type: R_Type,
    val base: R_Expr,
    val expr: R_Expr,
): R_BaseExpr(type)

class R_MapSubscriptExpr(
    type: R_Type,
    val base: R_Expr,
    val expr: R_Expr,
    val errPos: ErrorPos,
): R_DestinationExpr(type)

class R_VirtualMapSubscriptExpr(
    type: R_Type,
    val base: R_Expr,
    val expr: R_Expr,
    val errPos: ErrorPos,
): R_BaseExpr(type)

class R_TextSubscriptExpr(
    val base: R_Expr,
    val expr: R_Expr,
    val errPos: ErrorPos,
): R_BaseExpr(R_TextType)

sealed class R_JsonSubscriptExpr(
    val base: R_Expr,
    val expr: R_Expr,
    val errPos: ErrorPos,
): R_BaseExpr(R_JsonType)

class R_JsonArraySubscriptExpr(
    base: R_Expr,
    expr: R_Expr,
    errPos: ErrorPos,
): R_JsonSubscriptExpr(base, expr, errPos)

class R_JsonObjectSubscriptExpr(
    base: R_Expr,
    expr: R_Expr,
    errPos: ErrorPos,
): R_JsonSubscriptExpr(base, expr, errPos)

class R_ByteArraySubscriptExpr(
    val base: R_Expr,
    val expr: R_Expr,
    val errPos: ErrorPos,
): R_BaseExpr(R_IntegerType)

class R_ElvisExpr(
    type: R_Type,
    val left: R_Expr,
    val right: R_Expr,
): R_BaseExpr(type)

class R_NotNullExpr(
    type: R_Type,
    val expr: R_Expr,
    val errPos: ErrorPos,
): R_BaseExpr(type)

class R_IfExpr(
    type: R_Type,
    val cond: R_Expr,
    val trueExpr: R_Expr,
    val falseExpr: R_Expr,
): R_BaseExpr(type)

sealed class R_WhenChooser

class R_IterativeWhenChooser(
    val keyExpr: R_Expr,
    val exprs: ImmList<IndexedValue<R_Expr>>,
    val elseIdx: Int?,
): R_WhenChooser()

class R_LookupWhenChooser(
    val keyExpr: R_Expr,
    val map: ImmMap<RR_ConstantValue, Int>,
    val elseIdx: Int?,
): R_WhenChooser()

class R_WhenExpr(
    type: R_Type,
    val chooser: R_WhenChooser,
    val exprs: ImmList<R_Expr>,
): R_BaseExpr(type)

class R_CreateExprAttr(val attr: R_Attribute, val expr: R_Expr)

sealed class R_CreateExpr(
    type: R_Type,
    val rEntity: R_EntityDefinition,
    val errPos: ErrorPos,
): R_BaseExpr(type)

class R_RegularCreateExpr(
    rEntity: R_EntityDefinition,
    errPos: ErrorPos,
    val attrs: ImmList<R_CreateExprAttr>,
): R_CreateExpr(rEntity.type, rEntity, errPos)

class R_StructCreateExpr(
    rEntity: R_EntityDefinition,
    errPos: ErrorPos,
    val structType: R_StructType,
    val structExpr: R_Expr,
): R_CreateExpr(rEntity.type, rEntity, errPos)

class R_StructListCreateExpr(
    rEntity: R_EntityDefinition,
    errPos: ErrorPos,
    val structType: R_StructType,
    val resultListType: R_ListType,
    val listExpr: R_Expr,
): R_CreateExpr(resultListType, rEntity, errPos)

class R_StructExpr(
    val struct: R_Struct,
    val attrs: ImmList<R_CreateExprAttr>,
): R_BaseExpr(struct.type) {
    init {
        checkEquals(attrs.map { it.attr.index }.sorted(), struct.attributesList.indices.toList())
    }
}

class R_AssignExpr(
    type: R_Type,
    val op: R_BinaryOp,
    val dstExpr: R_DestinationExpr,
    val srcExpr: R_Expr,
    val post: Boolean,
): R_BaseExpr(type)

class R_StatementExpr(val stmt: R_Statement): R_BaseExpr(R_UnitType)

class R_ChainHeightExpr(val chain: R_ExternalChainRef): R_BaseExpr(R_IntegerType)

class R_TypeAdapterExpr(
    type: R_Type,
    val expr: R_Expr,
    val adapter: R_TypeAdapter,
): R_BaseExpr(type)

sealed class R_TypeAdapter

data object R_TypeAdapter_Direct: R_TypeAdapter()

data object R_TypeAdapter_IntegerToBigInteger: R_TypeAdapter()

data object R_TypeAdapter_IntegerToDecimal: R_TypeAdapter()

data object R_TypeAdapter_BigIntegerToDecimal: R_TypeAdapter()

class R_TypeAdapter_Nullable(val innerAdapter: R_TypeAdapter): R_TypeAdapter()

class R_ParameterDefaultValueExpr(
        type: R_Type,
        val callFilePos: FilePos,
        val initFrameGetter: C_LateGetter<R_CallFrame>,
        val exprGetter: C_LateGetter<R_Expr>,
): R_BaseExpr(type)

class R_AttributeDefaultValueExpr(
        val attr: R_Attribute,
        val createFilePos: FilePos?,
        val initFrameGetter: C_LateGetter<R_CallFrame>,
): R_BaseExpr(attr.type)

class R_GlobalConstantExpr(
        type: R_Type,
        val constId: GlobalConstantId,
): R_BaseExpr(type)
