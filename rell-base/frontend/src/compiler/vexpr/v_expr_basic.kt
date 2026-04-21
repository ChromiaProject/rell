/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.vexpr

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.core.C_QualifiedName
import net.postchain.rell.base.compiler.base.core.C_TypeAdapter
import net.postchain.rell.base.compiler.base.def.C_GlobalConstantHeader
import net.postchain.rell.base.compiler.base.expr.*
import net.postchain.rell.base.compiler.base.utils.C_LateGetter
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.*
import net.postchain.rell.base.model.rr.RR_ConstantValue
import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_TupleField
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.utils.*

private fun RR_ConstantValue.rType(): R_Type = when (this) {
    is RR_ConstantValue.Null -> R_NullType
    is RR_ConstantValue.Unit -> R_UnitType
    is RR_ConstantValue.Bool -> R_BooleanType
    is RR_ConstantValue.Int -> R_IntegerType
    is RR_ConstantValue.Text -> R_TextType
    is RR_ConstantValue.ByteArray -> R_ByteArrayType
    is RR_ConstantValue.Decimal -> R_DecimalType
    is RR_ConstantValue.BigInteger -> R_BigIntegerType
    is RR_ConstantValue.Rowid -> R_RowidType
    is RR_ConstantValue.Gtv -> R_GtvType
    is RR_ConstantValue.Enum,
    is RR_ConstantValue.Struct,
    is RR_ConstantValue.Collection,
    is RR_ConstantValue.MapConstant,
    is RR_ConstantValue.TupleConstant,
    is RR_ConstantValue.Meta,
    -> throw IllegalStateException("Cannot infer R_Type from ${this::class.simpleName}; provide explicit type")
}

class V_ErrorExpr(
    exprCtx: C_ExprContext,
    pos: S_Pos,
    private val resType: R_Type,
    private val message: String,
): V_Expr(exprCtx, pos) {
    override fun exprInfo0() = V_ExprInfo.simple(resType)
    override fun toRExpr(): R_Expr = R_ErrorExpr(type, message)
}

class V_ConstantValueExpr(
    exprCtx: C_ExprContext,
    pos: S_Pos,
    private val value: RR_ConstantValue,
    private val valueType: R_Type? = null,
    private val dependsOnAtExprs: ImmSet<R_AtExprId> = immSetOf(),
): V_Expr(exprCtx, pos) {
    override fun exprInfo0(): V_ExprInfo {
        val rType = valueType ?: value.rType()
        return V_ExprInfo.simple(rType, dependsOnAtExprs = dependsOnAtExprs)
    }

    override fun toRExpr(): R_Expr = R_RRConstantValueExpr(type, value)
    override fun constantValue(ctx: V_ConstantValueEvalContext) = value
}

class V_IfExpr(
    exprCtx: C_ExprContext,
    pos: S_Pos,
    private val resType: R_Type,
    private val condExpr: V_Expr,
    private val trueExpr: V_Expr,
    private val falseExpr: V_Expr,
    private val resVarStates: C_ExprVarStatesDelta,
): V_Expr(exprCtx, pos) {
    override fun exprInfo0() = V_ExprInfo.simple(resType, condExpr, trueExpr, falseExpr)
    override fun varStatesDelta0() = resVarStates

    override fun toRExpr(): R_Expr {
        val rCond = condExpr.toRExpr()
        val rTrue = trueExpr.toRExpr()
        val rFalse = falseExpr.toRExpr()
        return R_IfExpr(resType, rCond, rTrue, rFalse)
    }

    override fun toDbExpr0(): Db_Expr {
        val dbCond = condExpr.toDbExpr()
        val dbTrue = trueExpr.toDbExpr()
        val dbFalse = falseExpr.toDbExpr()
        val cases = immListOf(Db_WhenCase(immListOf(dbCond), dbTrue))
        return Db_WhenExpr(resType, null, cases, dbFalse)
    }
}

class V_TupleExpr(
    exprCtx: C_ExprContext,
    pos: S_Pos,
    private val tupleType: R_TupleType,
    private val exprs: ImmList<V_Expr>,
): V_Expr(exprCtx, pos) {
    override fun exprInfo0() = V_ExprInfo.simple(tupleType, exprs, canBeDbExpr = false)

    override fun toRExpr(): R_Expr {
        val rExprs = exprs.mapToImmList { it.toRExpr() }
        return R_TupleExpr(tupleType, rExprs)
    }

    override fun toDbExprWhat0(): C_DbAtWhatValue {
        val evaluator = object: Db_ComplexAtWhatEvaluator() {
            override fun combinerInfo() = Db_AtWhatCombinerInfo.Tuple(tupleType)
        }
        return C_DbAtWhatValue_Complex(exprs, evaluator)
    }

    override fun constantValue(ctx: V_ConstantValueEvalContext): RR_ConstantValue? {
        val fieldValues = exprs.mapToImmList { it.constantValue(ctx) ?: return@constantValue null }
        val rrType = simpleTupleType(tupleType) ?: return null
        return RR_ConstantValue.TupleConstant(rrType, fieldValues)
    }

    companion object {
        /** Simplified R_TupleType -> RR_Type conversion for compile-time constant detection. */
        private fun simpleTupleType(type: R_TupleType): RR_Type? {
            val fields = type.fields.mapToImmList { field ->
                val rrFieldType = simpleRrType(field.type) ?: return@simpleTupleType null
                RR_TupleField(field.name?.str, rrFieldType)
            }
            return RR_Type.Tuple(fields)
        }

        private fun simpleRrType(rType: R_Type): RR_Type? = when (rType) {
            is R_BooleanType -> RR_Type.Primitive(RR_PrimitiveKind.BOOLEAN)
            is R_IntegerType -> RR_Type.Primitive(RR_PrimitiveKind.INTEGER)
            is R_BigIntegerType -> RR_Type.Primitive(RR_PrimitiveKind.BIG_INTEGER)
            is R_DecimalType -> RR_Type.Primitive(RR_PrimitiveKind.DECIMAL)
            is R_TextType -> RR_Type.Primitive(RR_PrimitiveKind.TEXT)
            is R_ByteArrayType -> RR_Type.Primitive(RR_PrimitiveKind.BYTE_ARRAY)
            is R_NullType -> RR_Type.Null
            is R_TupleType -> simpleTupleType(rType)
            else -> null
        }
    }
}

class V_TypeAdapterExpr(
    exprCtx: C_ExprContext,
    private val resType: R_Type,
    private val expr: V_Expr,
    private val adapter: C_TypeAdapter,
): V_Expr(exprCtx, expr.pos) {
    override fun exprInfo0() = V_ExprInfo.simple(resType, expr)

    override fun toRExpr(): R_Expr {
        val rExpr = expr.toRExpr()
        return adapter.adaptExprR(rExpr)
    }

    override fun toDbExpr0(): Db_Expr {
        val dbExpr = expr.toDbExpr()
        return adapter.adaptExprDb(dbExpr)
    }

    override fun constantValue(ctx: V_ConstantValueEvalContext): RR_ConstantValue? = null
}

class V_CreateExprAttr(val attr: R_Attribute, val expr: V_Expr) {
    fun toRAttr(): R_CreateExprAttr {
        val rExpr = expr.toRExpr()
        return R_CreateExprAttr(attr, rExpr)
    }
}

class V_StructExpr(
    exprCtx: C_ExprContext,
    pos: S_Pos,
    private val struct: R_Struct,
    explicitAttrs: ImmList<V_CreateExprAttr>,
    implicitAttrs: ImmList<V_CreateExprAttr>,
): V_Expr(exprCtx, pos) {
    private val allAttrs = let {
        val impIdxs = implicitAttrs.map { it.attr.index }.toSet()
        val expIdxs = explicitAttrs.map { it.attr.index }.toSet()
        val dupIdxs = impIdxs.intersect(expIdxs)
        require(dupIdxs.isEmpty()) { dupIdxs }

        val allIdxs = impIdxs + expIdxs
        for (attr in struct.attributesList) {
            check(attr.index in allIdxs) { attr }
        }

        implicitAttrs.forEach {
            require(it.attr.hasExpr) { it.attr }
        }

        explicitAttrs + implicitAttrs
    }

    override fun exprInfo0() = V_ExprInfo.simple(
        struct.type,
        allAttrs.map { it.expr },
        canBeDbExpr = false,
    )

    override fun toRExpr(): R_Expr {
        val rAttrs = allAttrs.mapToImmList { it.toRAttr() }
        return R_StructExpr(struct, rAttrs)
    }

    override fun toDbExprWhat0(): C_DbAtWhatValue {
        val exprs = allAttrs.mapToImmList { it.expr }

        val evaluator = object: Db_ComplexAtWhatEvaluator() {
            override fun combinerInfo() = Db_AtWhatCombinerInfo.Struct(struct, allAttrs.mapToImmList { it.attr.index })
        }

        return C_DbAtWhatValue_Complex(exprs, evaluator)
    }
}

class V_GlobalConstantExpr(
        exprCtx: C_ExprContext,
        pos: S_Pos,
        private val name: Name,
        private val resType: R_Type,
        private val varKey: C_VarStateKey,
        private val constId: GlobalConstantId,
        private val header: C_GlobalConstantHeader,
): V_Expr(exprCtx, pos) {
    override fun exprInfo0() = V_ExprInfo.simple(resType)

    override fun toRExpr(): R_Expr = R_GlobalConstantExpr(resType, constId)

    override fun constantValue(ctx: V_ConstantValueEvalContext): RR_ConstantValue? {
        val cBody = header.constBody
        return cBody?.constantValue(ctx)
    }

    override fun varKey() = varKey
    override fun globalConstantId() = constId

    override fun implicitTargetAttrName() = name
}

class V_ParameterDefaultValueExpr constructor(
        exprCtx: C_ExprContext,
        pos: S_Pos,
        private val resType: R_Type,
        private val callFilePos: FilePos,
        private val initFrameGetter: C_LateGetter<R_CallFrame>,
        private val exprGetter: C_LateGetter<R_Expr>,
): V_Expr(exprCtx, pos) {
    override fun exprInfo0() = V_ExprInfo.simple(resType)

    override fun globalConstantRestriction() = V_GlobalConstantRestriction("param_default_value", null)

    override fun toRExpr(): R_Expr {
        return R_ParameterDefaultValueExpr(resType, callFilePos, initFrameGetter, exprGetter)
    }
}

class V_AttributeDefaultValueExpr(
        exprCtx: C_ExprContext,
        pos: S_Pos,
        private val attr: R_Attribute,
        private val createFilePos: FilePos?,
        private val initFrameGetter: C_LateGetter<R_CallFrame>,
): V_Expr(exprCtx, pos) {
    override fun exprInfo0() = V_ExprInfo.simple(attr.type)

    override fun globalConstantRestriction() = V_GlobalConstantRestriction("attr_default_value:${attr.name}",
            "using default value for attribute '${attr.name}' (not supported yet)")

    override fun toRExpr(): R_Expr {
        return R_AttributeDefaultValueExpr(attr, createFilePos, initFrameGetter)
    }
}

class V_ObjectExpr(
    exprCtx: C_ExprContext,
    qName: C_QualifiedName,
    private val rObject: R_ObjectDefinition,
): V_Expr(exprCtx, qName.pos) {
    override fun exprInfo0() = V_ExprInfo.simple(rObject.type)
    override fun globalConstantRestriction() = V_GlobalConstantRestriction("object", null)
    override fun toRExpr(): R_Expr = R_ObjectExpr(rObject.type)

    override fun getDefMeta(): R_DefinitionMeta {
        return R_DefinitionMeta("object", rObject.defName, mountName = rObject.rEntity.mountName)
    }
}
