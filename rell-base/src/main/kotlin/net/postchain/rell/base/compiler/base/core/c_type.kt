/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.core

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.compiler.base.utils.C_CodeMsgSupplier
import net.postchain.rell.base.compiler.base.utils.C_Error
import net.postchain.rell.base.compiler.base.utils.C_Errors
import net.postchain.rell.base.compiler.vexpr.V_Expr
import net.postchain.rell.base.compiler.vexpr.V_TypeAdapterExpr
import net.postchain.rell.base.lib.type.*
import net.postchain.rell.base.lmodel.L_TypeUtils
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.*
import net.postchain.rell.base.mtype.M_Type
import net.postchain.rell.base.utils.*

class C_TypeHint private constructor(val rTypes: ImmSet<R_Type>) {
    fun getFunctionType(): R_FunctionType? {
        return rTypes
            .mapNotNull { it as? R_FunctionType }
            .singleOrNull()
    }

    fun getTupleFieldHint(index: Int): C_TypeHint {
        check(index >= 0) { index }
        val rResTypes = rTypes
            .mapNotNull { it as? R_TupleType }
            .mapNotNull { it.fields.getOrNull(index)?.type }
            .filter { it != R_NullType }
        return ofTypes(rResTypes)
    }

    fun getSourceType(dstType: M_Type): R_Type? {
        val rDstType = L_TypeUtils.getRType(dstType)
        val resTypes = rTypes.mapNotNull {
            getSourceType0(rDstType, it)
        }
        val resType = resTypes.singleOrNull()
        return resType
    }

    companion object {
        val NONE: C_TypeHint = C_TypeHint(immSetOf())

        fun ofTypes(types: List<R_Type>): C_TypeHint {
            return if (types.isEmpty()) NONE else C_TypeHint(types.toImmSet())
        }

        fun ofType(type: R_Type?): C_TypeHint {
            type ?: return NONE
            return ofTypes(immListOf(type))
        }

        fun combined(hints: List<C_TypeHint>): C_TypeHint {
            val mTypes = hints.flatMap { it.rTypes }.toImmSet()
            return C_TypeHint(mTypes)
        }

        private fun getSourceType0(rDstType: R_Type, rHintType: R_Type): R_Type? {
            return when (rHintType) {
                is R_NullableType -> when (rDstType) {
                    is R_NullableType -> {
                        val valueType = getSourceType0(rDstType.valueType, rHintType.valueType)
                        valueType?.let { R_NullableType(valueType) }
                    }
                    else -> getSourceType0(rDstType, rHintType.valueType)
                }
                is R_GenericType -> getSourceTypeGeneric(rDstType, rHintType)
                is R_LibGenericType -> getSourceTypeLibGeneric(rDstType, rHintType)
                else -> null
            }
        }

        private fun getSourceTypeGeneric(rDstType: R_Type, rHintType: R_GenericType): R_Type? {
            if (rDstType !is R_LibGenericType) {
                return null
            }

            var curType: R_Type? = rDstType
            val transList = mutableListOf<R_TypeTransformer>()

            while (curType != null) {
                val parentType = curType.parentType
                parentType ?: break

                val trans = getTypeTransformer(parentType, curType)
                trans ?: break
                transList.add(trans)

                if (parentType is R_GenericType && parentType.typeName == rHintType.typeName) {
                    var res: R_Type = rHintType
                    for (curTrans in transList.reversed()) {
                        res = curTrans.transform(res) ?: return null
                    }
                    return res
                }
                curType = parentType
            }

            return null
        }

        private fun getSourceTypeLibGeneric(rDstType: R_Type, rHintType: R_BaseGenericType): R_Type? {
            return when (rDstType) {
                is R_LibGenericType -> when {
                    rDstType.typeName == rHintType.typeName && rDstType.args.size == rHintType.args.size -> {
                        val args = rDstType.args.withIndex().mapNotNullAllOrNull { (i, patArg) ->
                            val hintArg = rHintType.args[i]
                            matchTypeParam(patArg, hintArg)
                        }
                        args?.let { rHintType.typeMeta?.getTypeOrNull(args) }
                    }
                    else -> null
                }
                else -> null
            }
        }

        private fun interface R_TypeTransformer {
            fun transform(type: R_Type): R_Type?
        }

        private fun getTypeTransformer(srcType: R_Type, dstType: R_Type): R_TypeTransformer? {
            return when (dstType) {
                is R_BaseGenericType -> {
                    if (dstType.args.none { it is R_VariableType }) {
                        if (srcType == dstType) R_TypeTransformer { it } else null
                    } else {
                        val srcExtractors = srcType.typeExtractors
                        val dstExtractors = dstType.args.mapNotNullAllOrNull { arg ->
                            when (arg) {
                                is R_VariableType -> srcExtractors[arg.name]
                                else -> { _: R_Type -> arg }
                            }
                        }
                        if (dstExtractors == null) null else R_TypeTransformer { type ->
                            val transArgs = dstExtractors.mapNotNullAllOrNull { it(type) }
                            if (transArgs == null) null else dstType.typeMeta?.getTypeOrNull(transArgs)
                        }
                    }
                }
                else -> null
            }
        }

        private fun matchTypeParam(type1: R_Type, type2: R_Type): R_Type? {
            return when (type1) {
                type2 -> type1
                is R_VariableType -> type2
                is R_TupleType -> when (type2) {
                    is R_TupleType -> when {
                        type1.fields.size == type2.fields.size -> {
                            val resFields = type1.fields.withIndex().mapNotNullAllOrNull { (i, f1) ->
                                val f2 = type2.fields[i]
                                matchTypeParam(f1.type, f2.type)?.let { R_TupleField(i, null, it) }
                            }
                            resFields?.let { R_TupleType(resFields) }
                        }
                        else -> null
                    }
                    else -> null
                }
                else -> null
            }
        }
    }
}

internal sealed class C_TypeAdapter {
    abstract fun adaptExpr(ctx: C_ExprContext, expr: V_Expr): V_Expr
    abstract fun adaptExprR(expr: R_Expr): R_Expr
    abstract fun adaptExprDb(expr: Db_Expr): Db_Expr
    abstract fun toRAdapter(): R_TypeAdapter
}

internal data object C_TypeAdapter_Direct: C_TypeAdapter() {
    override fun adaptExpr(ctx: C_ExprContext, expr: V_Expr) = expr
    override fun adaptExprR(expr: R_Expr) = expr
    override fun adaptExprDb(expr: Db_Expr) = expr
    override fun toRAdapter(): R_TypeAdapter = R_TypeAdapter_Direct
}

internal data object C_TypeAdapter_IntegerToBigInteger: C_TypeAdapter() {
    override fun adaptExpr(ctx: C_ExprContext, expr: V_Expr): V_Expr {
        return V_TypeAdapterExpr(ctx, R_BigIntegerType, expr, this)
    }

    override fun adaptExprR(expr: R_Expr): R_Expr {
        return R_TypeAdapterExpr(R_BigIntegerType, expr, toRAdapter())
    }

    override fun adaptExprDb(expr: Db_Expr): Db_Expr {
        return Db_CallExpr(R_BigIntegerType, Lib_Type_BigInteger.FromInteger_Db, immListOf(expr))
    }

    override fun toRAdapter(): R_TypeAdapter = R_TypeAdapter_IntegerToBigInteger
}

internal data object C_TypeAdapter_IntegerToDecimal: C_TypeAdapter() {
    override fun adaptExpr(ctx: C_ExprContext, expr: V_Expr): V_Expr {
        return V_TypeAdapterExpr(ctx, R_DecimalType, expr, this)
    }

    override fun adaptExprR(expr: R_Expr): R_Expr {
        return R_TypeAdapterExpr(R_DecimalType, expr, toRAdapter())
    }

    override fun adaptExprDb(expr: Db_Expr): Db_Expr {
        return Db_CallExpr(R_DecimalType, Lib_Type_Decimal.FromInteger_Db, immListOf(expr))
    }

    override fun toRAdapter(): R_TypeAdapter = R_TypeAdapter_IntegerToDecimal
}

internal data object C_TypeAdapter_BigIntegerToDecimal: C_TypeAdapter() {
    override fun adaptExpr(ctx: C_ExprContext, expr: V_Expr): V_Expr {
        return V_TypeAdapterExpr(ctx, R_DecimalType, expr, this)
    }

    override fun adaptExprR(expr: R_Expr): R_Expr {
        return R_TypeAdapterExpr(R_DecimalType, expr, toRAdapter())
    }

    override fun adaptExprDb(expr: Db_Expr): Db_Expr {
        return Db_CallExpr(R_DecimalType, Lib_Type_Decimal.FromBigInteger_Db, immListOf(expr))
    }

    override fun toRAdapter(): R_TypeAdapter = R_TypeAdapter_BigIntegerToDecimal
}

internal class C_TypeAdapter_Nullable(
    private val dstType: R_Type,
    private val innerAdapter: C_TypeAdapter,
): C_TypeAdapter() {
    override fun adaptExpr(ctx: C_ExprContext, expr: V_Expr): V_Expr {
        return V_TypeAdapterExpr(ctx, dstType, expr, this)
    }

    override fun adaptExprR(expr: R_Expr): R_Expr {
        val rAdapter = toRAdapter()
        return R_TypeAdapterExpr(dstType, expr, rAdapter)
    }

    override fun adaptExprDb(expr: Db_Expr) = expr

    override fun toRAdapter(): R_TypeAdapter {
        val rInnerAdapter = innerAdapter.toRAdapter()
        return R_TypeAdapter_Nullable(rInnerAdapter)
    }
}

internal object C_Types {
    fun match(dstType: R_Type, srcType: R_Type, errPos: S_Pos, errSupplier: C_CodeMsgSupplier) {
        val err = match0(dstType, srcType, errPos, errSupplier)
        if (err != null) {
            throw err
        }
    }

    fun matchOpt(
        msgCtx: C_MessageContext,
        dstType: R_Type,
        srcType: R_Type,
        errPos: S_Pos,
        errSupplier: C_CodeMsgSupplier,
    ): Boolean {
        val err = match0(dstType, srcType, errPos, errSupplier)
        return if (err != null) {
            msgCtx.error(err)
            false
        } else {
            true
        }
    }

    private fun match0(dstType: R_Type, srcType: R_Type, errPos: S_Pos, errSupplier: C_CodeMsgSupplier): C_Error? {
        return if (dstType.isNotError() && srcType.isNotError() && !dstType.isAssignableFrom(srcType)) {
            C_Errors.errTypeMismatch(errPos, srcType, dstType, errSupplier)
        } else {
            null
        }
    }

    fun adapt(dstType: R_Type, srcType: R_Type, errPos: S_Pos, errSupplier: C_CodeMsgSupplier): C_TypeAdapter {
        val adapter = dstType.getTypeAdapter(srcType)
        if (adapter == null) {
            throw C_Errors.errTypeMismatch(errPos, srcType, dstType, errSupplier)
        }
        return adapter
    }

    fun adaptSafe(
        msgCtx: C_MessageContext,
        dstType: R_Type,
        srcType: R_Type,
        errPos: S_Pos,
        errSupplier: C_CodeMsgSupplier,
    ): C_TypeAdapter {
        val adapter = dstType.getTypeAdapter(srcType)
        return if (adapter != null) adapter else {
            C_Errors.errTypeMismatch(msgCtx, errPos, srcType, dstType, errSupplier)
            C_TypeAdapter_Direct
        }
    }

    fun checkNotUnit(
            msgCtx: C_MessageContext,
            pos: S_Pos,
            type: R_Type,
            name: String?,
            kindSupplier: C_CodeMsgSupplier
    ): R_Type {
        if (type != R_UnitType) return type
        val kind = kindSupplier()
        val nameCode = name ?: "?"
        msgCtx.error(pos, "type:${kind.code}:unit:$nameCode", "Type of ${kind.msg} cannot be ${type.str()}")
        return R_CtErrorType
    }

    fun commonType(a: R_Type, b: R_Type, errPos: S_Pos, errSupplier: C_CodeMsgSupplier): R_Type {
        val res = commonTypeOpt(a, b)
        return res ?: throw C_Errors.errTypeMismatch(errPos, b, a, errSupplier)
    }

    fun commonTypeOpt(a: R_Type, b: R_Type): R_Type? {
        return R_Type.commonTypeOpt(a, b)
    }

    fun commonTypesOpt(a: R_MapKeyValueTypes, b: R_MapKeyValueTypes): R_MapKeyValueTypes? {
        val key = commonTypeOpt(a.key, b.key)
        key ?: return null
        val value = commonTypeOpt(a.value, b.value)
        return if (value == null) null else R_MapKeyValueTypes(key, value)
    }

    fun toNullable(type: R_Type): R_Type {
        return if (type is R_NullableType || type == R_NullType || type.isError()) type else R_NullableType(type)
    }

    fun removeNullable(type: R_Type): R_Type {
        return if (type is R_NullableType) type.valueType else type
    }

    fun isNullOrNullable(type: R_Type): Boolean = type == R_NullType || type is R_NullableType
}
