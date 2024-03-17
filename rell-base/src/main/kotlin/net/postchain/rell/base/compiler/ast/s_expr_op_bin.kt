/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.ast

import net.postchain.rell.base.compiler.base.core.C_MessageContext
import net.postchain.rell.base.compiler.base.core.C_Statement
import net.postchain.rell.base.compiler.base.core.C_Types
import net.postchain.rell.base.compiler.base.expr.*
import net.postchain.rell.base.compiler.base.utils.C_Errors
import net.postchain.rell.base.compiler.base.utils.C_Utils
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.compiler.vexpr.*
import net.postchain.rell.base.lib.type.*
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.*
import net.postchain.rell.base.model.stmt.R_UpdateStatementWhat
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.toImmList
import net.postchain.rell.base.utils.toImmMap
import java.util.*

enum class S_BinaryOp(val code: String, val op: C_BinOp) {
    EQ("==", C_BinOp_Eq),
    NE("!=", C_BinOp_Ne),
    LE("<=", C_BinOp_Le),
    GE(">=", C_BinOp_Ge),
    LT("<", C_BinOp_Lt),
    GT(">", C_BinOp_Gt),
    EQ_REF("===", C_BinOp_EqRef),
    NE_REF("!==", C_BinOp_NeRef),
    PLUS("+", C_BinOp_Plus),
    MINUS("-", C_BinOp_Minus),
    MUL("*", C_BinOp_Mul),
    DIV("/", C_BinOp_Div),
    MOD("%", C_BinOp_Mod),
    AND("and", C_BinOp_And),
    OR("or", C_BinOp_Or),
    IN("in", C_BinOp_In(false)),
    NOT_IN("in", C_BinOp_In(true)),
    ELVIS("?:", C_BinOp_Elvis),
    ;

    companion object {
        private val PRECEDENCE_LEVELS = listOf(
                listOf(OR),
                listOf(AND),
                listOf(EQ, NE, LE, GE, LT, GT, EQ_REF, NE_REF),
                listOf(IN, NOT_IN),
                listOf(ELVIS),
                listOf(PLUS, MINUS),
                listOf(MUL, DIV, MOD)
        )

        private val PRECEDENCE_MAP: Map<S_BinaryOp, Int> = let {
            val m = mutableMapOf<S_BinaryOp, Int>()

            for ((level, ops) in PRECEDENCE_LEVELS.withIndex()) {
                for (op in ops) {
                    check(op !in m)
                    m[op] = level
                }
            }

            check(m.keys.containsAll(values().toSet())) {
                "Forgot to add new operator to the precedence table?"
            }

            m.toImmMap()
        }
    }

    fun precedence(): Int = PRECEDENCE_MAP.getValue(this)
}

enum class S_AssignOpCode(val op: S_AssignOp) {
    EQ(S_AssignOp_Eq),
    PLUS(S_AssignOp_Op("+=", C_BinOp_Plus)),
    MINUS(S_AssignOp_Op("-=", C_BinOp_Minus)),
    MUL(S_AssignOp_Op("*=", C_BinOp_Mul)),
    DIV(S_AssignOp_Op("/=", C_BinOp_Div)),
    MOD(S_AssignOp_Op("%=", C_BinOp_Mod)),
    ;
}

sealed class S_AssignOp {
    abstract fun compile(ctx: C_ExprContext, pos: S_Pos, dstValue: V_Expr, srcValue: V_Expr): C_Statement

    abstract fun compileDbUpdate(
        ctx: C_ExprContext,
        pos: S_Pos,
        entity: R_DbAtEntity,
        attr: R_Attribute,
        srcValue: V_Expr,
    ): R_UpdateStatementWhat?

    protected open fun compileVarFacts(
            dstValue: V_Expr,
            dstExpr: C_Destination,
            srcValue: V_Expr,
            srcType: R_Type
    ): C_VarFacts {
        return dstValue.varFacts.postFacts.and(srcValue.varFacts.postFacts)
    }
}

object S_AssignOp_Eq: S_AssignOp() {
    override fun compile(ctx: C_ExprContext, pos: S_Pos, dstValue: V_Expr, srcExpr: V_Expr): C_Statement {
        val destination = dstValue.destination()
        val dstType = destination.type()
        val srcType = srcExpr.type

        val adapter = C_Types.adapt(dstType, srcType, pos) { "stmt_assign_type" toCodeMsg "Assignment type mismatch" }
        val srcAdapterExpr = adapter.adaptExpr(ctx, srcExpr)
        val rSrcAdapterExpr = srcAdapterExpr.toRExpr()

        val rStmt = destination.compileAssignStatement(ctx, rSrcAdapterExpr, null)

        val varFacts = compileVarFacts(dstValue, destination, srcExpr, srcType)
        return C_Statement(rStmt, false, varFacts)
    }

    override fun compileVarFacts(dstValue: V_Expr, dstExpr: C_Destination, srcValue: V_Expr, srcType: R_Type): C_VarFacts {
        var varFacts = super.compileVarFacts(dstValue, dstExpr, srcValue, srcType)
        val cVarId = dstValue.varId()
        if (cVarId != null) {
            val inited = mapOf(cVarId to C_VarFact.YES)
            val dstType = dstExpr.type()
            val nulled = C_VarFacts.varTypeToNulled(cVarId, dstType, srcType)
            varFacts = varFacts.put(C_VarFacts.of(inited = inited, nulled = nulled))
        }
        return varFacts
    }

    override fun compileDbUpdate(
        ctx: C_ExprContext,
        pos: S_Pos,
        entity: R_DbAtEntity,
        attr: R_Attribute,
        srcValue: V_Expr,
    ): R_UpdateStatementWhat {
        val expr = srcValue.toDbExpr()
        val adapter = attr.type.getTypeAdapter(expr.type)
        val expr2 = if (adapter == null) {
            val name = attr.name
            C_Errors.errTypeMismatch(ctx.msgCtx, pos, expr.type, attr.type) {
                "stmt_assign_type" toCodeMsg "Type mismatch for '$name'"
            }
            C_ExprUtils.errorDbExpr(attr.type)
        } else {
            adapter.adaptExprDb(expr)
        }
        return R_UpdateStatementWhat(attr, expr2)
    }
}

class S_AssignOp_Op(val code: String, val op: C_BinOp_Common): S_AssignOp() {
    override fun compile(ctx: C_ExprContext, pos: S_Pos, dstValue: V_Expr, srcValue: V_Expr): C_Statement {
        val dstExpr = dstValue.destination()
        val dstType = dstExpr.effectiveType()

        val srcValue2 = op.adaptRight(ctx, dstType, srcValue)
        val rSrcExpr = srcValue2.toRExpr()
        val srcType = rSrcExpr.type

        val binOp = compileBinOp(ctx, pos, dstType, srcType)
        val cOp = C_AssignOp(pos, code, binOp.rOp, binOp.dbOp)
        val rStmt = dstExpr.compileAssignStatement(ctx, rSrcExpr, cOp)

        val varFacts = compileVarFacts(dstValue, dstExpr, srcValue, rSrcExpr.type)
        return C_Statement(rStmt, false, varFacts)
    }

    override fun compileDbUpdate(
        ctx: C_ExprContext,
        pos: S_Pos,
        entity: R_DbAtEntity,
        attr: R_Attribute,
        srcValue: V_Expr,
    ): R_UpdateStatementWhat {
        val srcValue2 = op.adaptRight(ctx, attr.type, srcValue)
        val dbSrcExpr = srcValue2.toDbExpr()
        val srcType = dbSrcExpr.type

        val binOp = compileBinOp(ctx, pos, attr.type, srcType)
        if (binOp.dbOp == null) {
            C_BinOp.errTypeMismatch(ctx.msgCtx, pos, code, attr.type, srcType)
        }

        return S_UpdateWhat.makeRWhat(entity, attr, dbSrcExpr, binOp.dbOp)
    }

    private fun compileBinOp(ctx: C_ExprContext, pos: S_Pos, left: R_Type, right: R_Type): V_BinaryOp {
        val binOp = op.compileOp(left, right)
        return if (binOp != null && binOp.resType == left) binOp else {
            C_BinOp.errTypeMismatch(ctx.msgCtx, pos, code, left, right)
            V_BinaryOp.of(left, R_BinaryOp_Add_Integer, Db_BinaryOp_Add_Integer) // Using fake ops for error recovery.
        }
    }
}

sealed class C_BinOp {
    abstract fun compile(ctx: C_ExprContext, left: V_Expr, right: V_Expr): V_Expr?
    open fun compileRight(ctx: C_ExprContext, sExpr: S_Expr): V_Expr = sExpr.compile(ctx).value()
    open fun rightVarFacts(left: V_Expr): C_VarFacts = C_VarFacts.EMPTY

    protected open fun compileExprVarFacts(left: V_Expr, right: V_Expr): C_ExprVarFacts {
        return C_ExprVarFacts.forSubExpressions(left, right)
    }

    companion object {
        fun errTypeMismatch(msgCtx: C_MessageContext, pos: S_Pos, op: String, leftType: R_Type, rightType: R_Type) {
            if (leftType.isNotError() && rightType.isNotError()) {
                msgCtx.error(pos, "binop_operand_type:$op:[${leftType.strCode()}]:[${rightType.strCode()}]",
                        "Wrong operand types for '$op': ${leftType.str()}, ${rightType.str()}")
            }
        }

        fun errTypeMismatchDb(msgCtx: C_MessageContext, pos: S_Pos, op: String, leftType: R_Type, rightType: R_Type) {
            if (leftType.isNotError() && rightType.isNotError()) {
                msgCtx.error(pos, "binop_nosql:$op:[${leftType.strCode()}]:[${rightType.strCode()}]",
                    "Operator '$op' cannot be converted to SQL with operands: ${leftType.str()}, ${rightType.str()}")
            }
        }
    }
}

sealed class C_BinOp_Common: C_BinOp() {
    abstract fun compileOp(left: R_Type, right: R_Type): V_BinaryOp?

    override fun compile(ctx: C_ExprContext, left: V_Expr, right: V_Expr): V_Expr? {
        val (left2, right2) = adaptLeftRight(ctx, left, right)
        return compile0(ctx, left2, right2)
    }

    private fun compile0(ctx: C_ExprContext, left: V_Expr, right: V_Expr): V_Expr? {
        val op = compileOp(left.type, right.type)
        if (op == null) {
            return null
        }

        val exprVarFacts = compileExprVarFacts(left, right)
        return V_BinaryExpr(ctx, left.pos, op, left, right, exprVarFacts)
    }

    protected open fun adaptOperands0(ctx: C_ExprContext, left: V_Expr, right: V_Expr): Pair<V_Expr, V_Expr> {
        val res = promoteNumeric(ctx, listOf(left, right))
        checkEquals(res.size, 2)
        return res[0] to res[1]
    }

    private fun adaptLeftRight(ctx: C_ExprContext, left: V_Expr, right: V_Expr): Pair<V_Expr, V_Expr> {
        return adaptOperands0(ctx, left, right)
    }

    open fun adaptRight(ctx: C_ExprContext, leftType: R_Type, right: V_Expr): V_Expr {
        val rightType = right.type
        if (rightType == leftType) {
            return right
        }

        if (isNumericType(leftType) && isNumericType(rightType)) {
            val resExpr = promoteNumericExpr(ctx, right, leftType)
            if (resExpr != null) {
                return resExpr
            }
        }

        return right
    }

    companion object {
        fun promoteNumeric(ctx: C_ExprContext, left: V_Expr, right: V_Expr): Pair<V_Expr, V_Expr> {
            val res = promoteNumeric(ctx, listOf(left, right))
            return Pair(res[0], res[1])
        }

        fun promoteNumeric(ctx: C_ExprContext, values: List<V_Expr>): List<V_Expr> {
            val resType = values.fold(values.firstOrNull()?.type) { type, value ->
                if (type == null) null else commonNumericType(type, value.type)
            }

            resType ?: return values

            val resValues = values.mapNotNull { promoteNumericExpr(ctx, it, resType) }
            return if (resValues.size == values.size) resValues.toImmList() else values
        }

        private fun promoteNumericExpr(ctx: C_ExprContext, value: V_Expr, resType: R_Type): V_Expr? {
            val adapter = resType.getTypeAdapter(value.type)
            return adapter?.adaptExpr(ctx, value)
        }

        private fun commonNumericType(type1: R_Type, type2: R_Type): R_Type? {
            return when {
                !isNumericType(type1) || !isNumericType(type2) -> null
                type1 == type2 -> type1
                type1 == R_DecimalType || type2 == R_DecimalType -> R_DecimalType
                type1 == R_BigIntegerType || type2 == R_BigIntegerType -> R_BigIntegerType
                else -> null // Must not happen
            }
        }

        fun isNumericType(type: R_Type): Boolean {
            return type == R_IntegerType || type == R_BigIntegerType || type == R_DecimalType
        }
    }
}

sealed class C_BinOp_EqNe(private val eq: Boolean, private val rOp: R_BinaryOp): C_BinOp_Common() {
    protected open fun isTypeSupported(type: R_Type) = true

    final override fun compile(ctx: C_ExprContext, left: V_Expr, right: V_Expr): V_Expr? {
        return if (left.type == R_NullType) {
            compileIsNull(ctx, right)
        } else if (right.type == R_NullType) {
            compileIsNull(ctx, left)
        } else {
            super.compile(ctx, left, right)
        }
    }

    private fun compileIsNull(ctx: C_ExprContext, expr: V_Expr): V_Expr? {
        val realExpr = expr.asNullable().unwrap()
        return compileIsNull0(ctx, realExpr)
    }

    private fun compileIsNull0(ctx: C_ExprContext, expr: V_Expr): V_Expr? {
        val type = expr.type
        if (type !is R_NullableType && type != R_NullType) {
            return null
        }
        if (!isTypeSupported(type)) {
            return null
        }

        val baseFacts = C_ExprVarFacts.forSubExpressions(expr)
        val nullFacts = C_ExprVarFacts.forNullCheck(expr, eq)
        val exprVarFacts = baseFacts.update(trueFacts = nullFacts.trueFacts, falseFacts = nullFacts.falseFacts)

        val op = V_UnaryOp_IsNull(!eq)
        return V_UnaryExpr(ctx, expr.pos, op, expr, exprVarFacts)
    }

    final override fun compileOp(left: R_Type, right: R_Type): V_BinaryOp? {
        val type = calcCommonType(left, right) ?: calcCommonType(right, left)
        return if (type == null || type is R_ObjectType || !isTypeSupported(type)) null else {
            createVOp(type)
        }
    }

    private fun calcCommonType(left: R_Type, right: R_Type): R_Type? {
        return if (left.isAssignableFrom(right)) left else null
    }

    fun createVOp(type: R_Type): V_BinaryOp {
        val dbOp: Db_BinaryOp = when (type) {
            is R_NullableType -> if (eq) Db_BinaryOp_NotDistinct else Db_BinaryOp_Distinct
            else -> if (eq) Db_BinaryOp_Eq else Db_BinaryOp_Ne
        }
        val actDbOp = if (dbOpSupported(type)) dbOp else null
        return V_BinaryOp.of(R_BooleanType, rOp, actDbOp)
    }

    private fun dbOpSupported(type: R_Type): Boolean {
        return type == R_BooleanType
                || type == R_IntegerType
                || type == R_BigIntegerType
                || type == R_DecimalType
                || type == R_TextType
                || type == R_ByteArrayType
                || type == R_RowidType
                || type is R_EntityType
                || type is R_EnumType
                || type is R_NullableType && dbOpSupported(type.valueType)
    }

    companion object {
        fun checkTypes(left: R_Type, right: R_Type): Boolean {
            val op = C_BinOp_Eq.compileOp(left, right)
            return op != null
        }

        fun checkTypesDb(left: R_Type, right: R_Type): Boolean {
            val op = C_BinOp_Eq.compileOp(left, right)
            return op?.dbOp != null
        }
    }
}

object C_BinOp_Eq: C_BinOp_EqNe(true, R_BinaryOp_Eq)
object C_BinOp_Ne: C_BinOp_EqNe(false, R_BinaryOp_Ne)

sealed class C_BinOp_EqNeRef(eq: Boolean, rOp: R_BinaryOp): C_BinOp_EqNe(eq, rOp) {
    final override fun isTypeSupported(type: R_Type) = type.isReference()
}

object C_BinOp_EqRef: C_BinOp_EqNeRef(true, R_BinaryOp_EqRef)
object C_BinOp_NeRef: C_BinOp_EqNeRef(false, R_BinaryOp_NeRef)

sealed class C_BinOp_Cmp(val cmpOp: R_CmpOp, val dbOp: Db_BinaryOp): C_BinOp_Common() {
    final override fun compileOp(left: R_Type, right: R_Type): V_BinaryOp? {
        if (left != right) {
            return null
        }

        val rCmpType = R_CmpType.forCmpOpType(left)
        if (rCmpType == null) {
            return null
        }

        return V_BinaryOp.of(R_BooleanType, R_BinaryOp_Cmp(cmpOp, rCmpType), dbOp)
    }
}

object C_BinOp_Lt: C_BinOp_Cmp(R_CmpOp_Lt, Db_BinaryOp_Lt)
object C_BinOp_Gt: C_BinOp_Cmp(R_CmpOp_Gt, Db_BinaryOp_Gt)
object C_BinOp_Le: C_BinOp_Cmp(R_CmpOp_Le, Db_BinaryOp_Le)
object C_BinOp_Ge: C_BinOp_Cmp(R_CmpOp_Ge, Db_BinaryOp_Ge)

object C_BinOp_Plus: C_BinOp_Common() {
    override fun compileOp(left: R_Type, right: R_Type): V_BinaryOp? {
        if (left != right) {
            return null
        }

        return when (left) {
            R_IntegerType -> V_BinaryOp.of(R_IntegerType, R_BinaryOp_Add_Integer, Db_BinaryOp_Add_Integer)
            R_BigIntegerType -> V_BinaryOp.of(R_BigIntegerType, R_BinaryOp_Add_BigInteger, Db_BinaryOp_Add_BigInteger)
            R_DecimalType -> V_BinaryOp.of(R_DecimalType, R_BinaryOp_Add_Decimal, Db_BinaryOp_Add_Decimal)
            R_TextType -> V_BinaryOp.of(R_TextType, R_BinaryOp_Concat_Text, Db_BinaryOp_Concat)
            R_ByteArrayType -> V_BinaryOp.of(R_ByteArrayType, R_BinaryOp_Concat_ByteArray, Db_BinaryOp_Concat)
            else -> null
        }
    }

    override fun adaptOperands0(ctx: C_ExprContext, left: V_Expr, right: V_Expr): Pair<V_Expr, V_Expr> {
        val leftType = left.type
        val rightType = right.type
        val hasText = leftType == R_TextType || rightType == R_TextType
        val hasNotText = leftType != R_TextType || rightType != R_TextType

        return if (hasText && hasNotText) {
            val resLeft = if (leftType == R_TextType) left else adaptToText(ctx, left)
            val resRight = if (rightType == R_TextType) right else adaptToText(ctx, right)
            resLeft to resRight
        } else {
            super.adaptOperands0(ctx, left, right)
        }
    }

    override fun adaptRight(ctx: C_ExprContext, leftType: R_Type, right: V_Expr): V_Expr {
        val rightType = right.type
        return if (leftType == R_TextType && rightType != R_TextType) {
            adaptToText(ctx, right)
        } else {
            super.adaptRight(ctx, leftType, right)
        }
    }

    private fun adaptToText(ctx: C_ExprContext, vExpr: V_Expr): V_Expr {
        val type = vExpr.type
        val rFn: R_SysFunction = Lib_Type_Any.ToText_R
        val dbFn = getDbToStringFunction(type)

        val resType: R_Type = R_TextType
        val desc = V_SysFunctionTargetDescriptor(resType, rFn, dbFn, type.toTextFunctionLazy, pure = true)
        val vCallTarget: V_FunctionCallTarget = V_FunctionCallTarget_SysGlobalFunction(desc)

        val vCallArgs = V_FunctionCallArgs.positional(immListOf(vExpr))
        val vCall = V_CommonFunctionCall_Full(vExpr.pos, vExpr.pos, resType, vCallTarget, vCallArgs)
        return V_FunctionCallExpr(ctx, vExpr.pos, null, vCall, false)
    }

    private val DB_TO_TEXT_CAST_TYPES =
        immListOf(R_BooleanType, R_IntegerType, R_BigIntegerType, R_RowidType, R_JsonType)

    private val Db_ToText_Cast = Db_SysFunction.cast("to_text", "TEXT")
    private val Db_ToText_Cast_NullableText = Db_SysFunction.template("to_text(text?)", 1, "COALESCE(#0, 'null')")
    private val Db_ToText_Cast_NullableAny = Db_SysFunction.template("to_text(any?)", 1, "COALESCE((#0)::TEXT, 'null')")

    private fun getDbToStringFunction(type: R_Type): Db_SysFunction? {
        return when (type) {
            in DB_TO_TEXT_CAST_TYPES -> Db_ToText_Cast
            R_DecimalType -> Lib_Type_Decimal.ToText_Db
            //is R_EntityType -> Lib_Type_Any.ToText_Db
            is R_NullableType -> {
                when (type.valueType) {
                    R_TextType -> Db_ToText_Cast_NullableText
                    in DB_TO_TEXT_CAST_TYPES -> Db_ToText_Cast_NullableAny
                    else -> null
                }
            }
            else -> null
        }
    }
}

sealed class C_BinOp_Arith(
    private val rOpInt: R_BinaryOp,
    private val dbOpInt: Db_BinaryOp,
    private val rOpBigInt: R_BinaryOp,
    private val dbOpBigInt: Db_BinaryOp,
    private val rOpDec: R_BinaryOp,
    private val dbOpDec: Db_BinaryOp,
): C_BinOp_Common() {
    override fun compileOp(left: R_Type, right: R_Type): V_BinaryOp? {
        if (left != right) {
            return null
        }

        return when (left) {
            R_IntegerType -> V_BinaryOp.of(R_IntegerType, rOpInt, dbOpInt)
            R_BigIntegerType -> V_BinaryOp.of(R_BigIntegerType, rOpBigInt, dbOpBigInt)
            R_DecimalType -> V_BinaryOp.of(R_DecimalType, rOpDec, dbOpDec)
            else -> null
        }
    }
}

object C_BinOp_Minus: C_BinOp_Arith(
    R_BinaryOp_Sub_Integer,
    Db_BinaryOp_Sub_Integer,
    R_BinaryOp_Sub_BigInteger,
    Db_BinaryOp_Sub_BigInteger,
    R_BinaryOp_Sub_Decimal,
    Db_BinaryOp_Sub_Decimal,
)

object C_BinOp_Mul: C_BinOp_Arith(
    R_BinaryOp_Mul_Integer,
    Db_BinaryOp_Mul_Integer,
    R_BinaryOp_Mul_BigInteger,
    Db_BinaryOp_Mul_BigInteger,
    R_BinaryOp_Mul_Decimal,
    Db_BinaryOp_Mul_Decimal,
)

object C_BinOp_Div: C_BinOp_Arith(
    R_BinaryOp_Div_Integer,
    Db_BinaryOp_Div_Integer,
    R_BinaryOp_Div_BigInteger,
    Db_BinaryOp_Div_BigInteger,
    R_BinaryOp_Div_Decimal,
    Db_BinaryOp_Div_Decimal,
)

object C_BinOp_Mod: C_BinOp_Arith(
    R_BinaryOp_Mod_Integer,
    Db_BinaryOp_Mod_Integer,
    R_BinaryOp_Mod_BigInteger,
    Db_BinaryOp_Mod_BigInteger,
    R_BinaryOp_Mod_Decimal,
    Db_BinaryOp_Mod_Decimal,
)

sealed class C_BinOp_Logic(val rOp: R_BinaryOp, val dbOp: Db_BinaryOp): C_BinOp_Common() {
    override fun compileOp(left: R_Type, right: R_Type): V_BinaryOp? {
        if (left != R_BooleanType || right != R_BooleanType) {
            return null
        }
        return V_BinaryOp.of(R_BooleanType, rOp, dbOp)
    }
}

object C_BinOp_And: C_BinOp_Logic(R_BinaryOp_And, Db_BinaryOp_And) {
    override fun compileExprVarFacts(left: V_Expr, right: V_Expr): C_ExprVarFacts {
        val leftFacts = left.varFacts
        val rightFacts = right.varFacts

        val trueFacts = leftFacts.trueFacts.and(rightFacts.trueFacts)
                .and(leftFacts.postFacts).and(rightFacts.postFacts)
        val falseFacts = leftFacts.postFacts

        return C_ExprVarFacts.of(trueFacts = trueFacts, falseFacts = falseFacts, postFacts = falseFacts)
    }

    override fun rightVarFacts(left: V_Expr): C_VarFacts {
        return left.varFacts.trueFacts
    }
}

object C_BinOp_Or: C_BinOp_Logic(R_BinaryOp_Or, Db_BinaryOp_Or) {
    override fun compileExprVarFacts(left: V_Expr, right: V_Expr): C_ExprVarFacts {
        val leftFacts = left.varFacts
        val rightFacts = right.varFacts

        val trueFacts = leftFacts.postFacts
        val falseFacts = leftFacts.falseFacts.and(rightFacts.falseFacts)
                .and(leftFacts.postFacts).and(rightFacts.postFacts)

        return C_ExprVarFacts.of(trueFacts = trueFacts, falseFacts = falseFacts)
    }

    override fun rightVarFacts(left: V_Expr): C_VarFacts {
        return left.varFacts.falseFacts
    }
}

class C_BinOp_In(private val not: Boolean): C_BinOp() {
    override fun compile(ctx: C_ExprContext, left: V_Expr, right: V_Expr): V_Expr? {
        val leftType = left.type
        val rightType = right.type

        val op = matchOp(rightType)
        op ?: return null

        val adapter = op.elemType.getTypeAdapter(leftType)
        adapter ?: return null

        val left2 = adapter.adaptExpr(ctx, left)
        return op.compile(ctx, left2, right)
    }

    override fun compileRight(ctx: C_ExprContext, sExpr: S_Expr): V_Expr {
        val atCtx = ctx.atCtx
        val cExpr = if (atCtx != null) {
            sExpr.compileNestedAt(ctx, atCtx)
        } else {
            sExpr.compile(ctx)
        }
        return cExpr.value()
    }

    private fun matchOp(right: R_Type): OpMatch? {
        return when (right) {
            is R_CollectionType -> CollectionOpMatch(right)
            is R_VirtualListType -> BasicOpMatch(R_IntegerType, R_BinaryOp_In_VirtualList)
            is R_VirtualSetType -> BasicOpMatch(S_VirtualType.virtualMemberType(right.innerType.elementType), R_BinaryOp_In_VirtualSet)
            is R_MapType -> BasicOpMatch(right.keyType, R_BinaryOp_In_Map)
            is R_VirtualMapType -> BasicOpMatch(right.innerType.keyType, R_BinaryOp_In_Map)
            is R_RangeType -> BasicOpMatch(R_IntegerType, R_BinaryOp_In_Range)
            else -> null
        }
    }

    private abstract class OpMatch(val elemType: R_Type) {
        abstract fun compile(ctx: C_ExprContext, left: V_Expr, right: V_Expr): V_Expr
    }

    private inner class BasicOpMatch(elemType: R_Type, val rOp: R_BinaryOp): OpMatch(elemType) {
        override fun compile(ctx: C_ExprContext, left: V_Expr, right: V_Expr): V_Expr {
            val vOp = V_BinaryOp.of(R_BooleanType, rOp, null)
            val varFacts = compileExprVarFacts(left, right)

            var vExpr: V_Expr = V_BinaryExpr(ctx, left.pos, vOp, left, right, varFacts)
            if (not) {
                vExpr = V_UnaryExpr(ctx, vExpr.pos, V_UnaryOp_Not(), vExpr, varFacts)
            }
            return vExpr
        }
    }

    private inner class CollectionOpMatch(type: R_CollectionType): OpMatch(type.elementType) {
        override fun compile(ctx: C_ExprContext, left: V_Expr, right: V_Expr): V_Expr {
            return V_InCollectionExpr(ctx, elemType, left, right, not)
        }
    }
}

object C_BinOp_Elvis: C_BinOp() {
    override fun compile(ctx: C_ExprContext, left: V_Expr, right: V_Expr): V_Expr? {
        val left2 = left.asNullable().unwrap()
        return compile0(ctx, left2, right)
    }

    private fun compile0(ctx: C_ExprContext, left: V_Expr, right: V_Expr): V_Expr? {
        val leftType = left.type
        if (leftType == R_NullType) {
            return right
        }

        if (leftType !is R_NullableType) {
            return null
        }

        val resType = R_Type.commonTypeOpt(leftType.valueType, right.type)
        if (resType == null) {
            return null
        }

        return V_ElvisExpr(ctx, left.pos, resType, left, right)
    }
}

class S_BinaryExprTail(val op: S_PosValue<S_BinaryOp>, val expr: S_Expr)

class S_BinaryExpr(val head: S_Expr, val tail: List<S_BinaryExprTail>): S_Expr(head.startPos) {
    override fun compile(ctx: C_ExprContext, hint: C_ExprHint): C_Expr {
        val queue = LinkedList(tail)
        val tree = buildTree(head, queue, 0)
        val value = tree.compile(ctx)
        return C_ValueExpr(value)
    }

    private fun buildTree(left: S_Expr, tail: Queue<S_BinaryExprTail>, level: Int): BinExprNode {
        if (tail.isEmpty() || tail.peek().op.value.precedence() < level) {
            return TermBinExprNode(left)
        }

        var res = buildTree(left, tail, level + 1)

        while (!tail.isEmpty() && tail.peek().op.value.precedence() == level) {
            val next = tail.remove()
            val right = buildTree(next.expr, tail, level + 1)
            res = OpBinExprNode(next.op, res, right)
        }

        return res
    }

    private abstract class BinExprNode {
        abstract fun compile(ctx: C_ExprContext): V_Expr
        open fun compileRight(ctx: C_ExprContext, op: C_BinOp): V_Expr = compile(ctx)
    }

    private class TermBinExprNode(private val expr: S_Expr): BinExprNode() {
        override fun compile(ctx: C_ExprContext): V_Expr {
            val cExpr = expr.compile(ctx)
            val vExpr = cExpr.value()
            C_Utils.checkUnitType(expr.startPos, vExpr.type) {
                "expr_operand_unit" toCodeMsg "Operand expression returns nothing"
            }
            return vExpr
        }

        override fun compileRight(ctx: C_ExprContext, op: C_BinOp): V_Expr {
            return op.compileRight(ctx, expr)
        }
    }

    private class OpBinExprNode(
            private val sOp: S_PosValue<S_BinaryOp>,
            private val left: BinExprNode,
            private val right: BinExprNode
    ): BinExprNode() {
        override fun compile(ctx: C_ExprContext): V_Expr {
            val leftValue = left.compile(ctx)

            val op = sOp.value.op
            val rightFacts = op.rightVarFacts(leftValue)
            val rightCtx = ctx.updateFacts(rightFacts)
            val rightValue = right.compileRight(rightCtx, op)

            val value = op.compile(ctx, leftValue, rightValue)
            return if (value != null) {
                value
            } else {
                val pos = sOp.pos
                C_BinOp.errTypeMismatch(ctx.msgCtx, pos, sOp.value.code, leftValue.type, rightValue.type)
                C_ExprUtils.errorVExpr(ctx, pos)
            }
        }
    }
}
