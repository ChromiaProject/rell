/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.def

import net.postchain.rell.base.compiler.ast.S_Expr
import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.core.*
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.compiler.base.expr.C_ExprHint
import net.postchain.rell.base.compiler.base.expr.C_ExprUtils
import net.postchain.rell.base.compiler.base.expr.C_VarStateKey
import net.postchain.rell.base.compiler.base.namespace.C_DeclarationType
import net.postchain.rell.base.compiler.base.utils.C_CodeMsg
import net.postchain.rell.base.compiler.base.utils.C_GraphUtils
import net.postchain.rell.base.compiler.base.utils.C_LateGetter
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.compiler.vexpr.V_ConstantValueEvalContext
import net.postchain.rell.base.compiler.vexpr.V_Expr
import net.postchain.rell.base.compiler.vexpr.V_GlobalConstantExpr
import net.postchain.rell.base.compiler.vexpr.V_SmartNullableExpr
import net.postchain.rell.base.model.R_CtErrorType
import net.postchain.rell.base.model.R_GlobalConstantDefinition
import net.postchain.rell.base.model.R_GlobalConstantId
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.runtime.Rt_CommonError
import net.postchain.rell.base.runtime.Rt_Exception
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.utils.LazyPosString
import net.postchain.rell.base.utils.One
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.toImmMap

class C_GlobalConstantDefinition(
    val rDef: R_GlobalConstantDefinition,
    private val typePos: S_Pos,
    varId: C_VarId,
    private val headerGetter: C_LateGetter<C_GlobalConstantHeader>,
    private val exprGetter: C_LateGetter<V_Expr>,
) {
    private val varKey: C_VarStateKey = C_VarStateKey(varId)

    fun compileRead(exprCtx: C_ExprContext, name: C_Name): V_Expr {
        val header = headerGetter.get()
        val lazyName = LazyPosString.of(name.pos, name.str)
        val type = header.deepHeader.compileReturnType(exprCtx, lazyName) ?: R_CtErrorType
        val vExpr = V_GlobalConstantExpr(exprCtx, name.pos, name.rName, type, varKey, rDef.constId, header)
        return V_SmartNullableExpr.wrap(exprCtx, vExpr, SMART_KIND)
    }

    companion object {
        private val SMART_KIND = C_CodeMsg("const", "Global constant")

        fun validateConstants(msgCtx: C_MessageContext, constants: List<C_GlobalConstantDefinition>) {
            validateTypes(msgCtx, constants)
            validateExpressions(msgCtx, constants)
            validateRecursion(msgCtx, constants)
        }

        private fun validateTypes(msgCtx: C_MessageContext, constants: List<C_GlobalConstantDefinition>) {
            for (c in constants) {
                val header = c.headerGetter.get()
                val type = header.deepHeader.returnType()
                if (type.isError()) continue

                val flags = type.completeFlags()
                if (flags.mutable) {
                    val code = "def:const:bad_type:mutable:[${c.rDef.constId.strCode()}]:[${type.strCode()}]"
                    var msg = "Global constant cannot have a mutable type"
                    if (!header.isExplicitType) msg = "$msg: ${type.str()}"
                    msgCtx.error(c.typePos, code, msg)
                } else if (!flags.pure) {
                    val code = "def:const:bad_type:not_pure:[${c.rDef.constId.strCode()}]:[${type.strCode()}]"
                    var msg = "Bad type for a global constant"
                    if (!header.isExplicitType) msg = "$msg: ${type.str()}"
                    msgCtx.error(c.typePos, code, msg)
                }
            }
        }

        private fun validateExpressions(msgCtx: C_MessageContext, constants: List<C_GlobalConstantDefinition>) {
            for (c in constants) {
                c.exprGetter.get().traverse {
                    val r = it.globalConstantRestriction()
                    if (r == null) true else {
                        var msg = "Bad expression for a global constant"
                        if (r.msg != null) msg = "$msg: ${r.msg}"
                        msgCtx.error(it.pos, "def:const:bad_expr:[${c.rDef.constId.strCode()}]:${r.code}", msg)
                        true
                    }
                }
            }
        }

        private fun validateRecursion(msgCtx: C_MessageContext, constants: List<C_GlobalConstantDefinition>) {
            class Edge(val srcId: R_GlobalConstantId, val tgtExpr: V_Expr)

            val graph = constants
                    .associate { c ->
                        val deps = c.exprGetter.get().traverseToSet {
                            val depId = it.globalConstantId()
                            if (depId == null) immListOf() else immListOf(Edge(c.rDef.constId, it) to depId)
                        }
                        c.rDef.constId to deps
                    }
                    .toImmMap()

            val cycles = C_GraphUtils.findCyclesEx(graph)

            for (cycle in cycles) {
                for ((edge, tgtId) in cycle) {
                    val code = "def:const:cycle:${edge.srcId.strCode()}:${tgtId.strCode()}"
                    val msg = "Global constant expression is recursive"
                    msgCtx.error(edge.tgtExpr.pos, code, msg)
                }
            }
        }
    }
}

class C_GlobalConstantHeader(
    explicitType: R_Type?,
    val constBody: C_GlobalConstantDeepDefinitionBody?,
) {
    val isExplicitType = explicitType != null
    val deepHeader = C_DeepDefinitionHeader(C_DeclarationType.CONSTANT, explicitType, constBody)

    companion object {
        val ERROR = C_GlobalConstantHeader(null, null)
    }
}

class C_GlobalConstantDeepDefinitionBody(
    private val defCtx: C_DefinitionContext,
    private val sExpr: S_Expr,
    private val constId: R_GlobalConstantId,
    private val explicitRetType: R_Type?,
): C_CommonDeepDefinitionBody<V_Expr>(defCtx.appCtx) {
    private var constantValue0: One<Rt_Value?>? = null

    override fun returnsValue() = true
    override fun getErrorBody() = C_ExprUtils.errorVExpr(defCtx.initExprCtx, sExpr.startPos)
    override fun getReturnType(body: V_Expr) = body.type

    override fun compileBody(): V_Expr {
        val exprCtx = defCtx.initExprCtx

        val exprHint = C_ExprHint.ofType(explicitRetType)
        val vExpr = sExpr.compile(exprCtx, exprHint).vExpr()

        val actualType = vExpr.type

        return if (explicitRetType == null) {
            C_Types.checkNotUnit(defCtx.msgCtx, sExpr.startPos, actualType, defCtx.defName.simpleName) {
                "def:const" toCodeMsg "global constant"
            }
            vExpr
        } else {
            val adapter = C_Types.adapt(explicitRetType, actualType, sExpr.startPos) {
                "def:const_expr_type" toCodeMsg "Expression type mismatch"
            }
            adapter.adaptExpr(exprCtx, vExpr)
        }
    }

    fun constantValue(ctx: V_ConstantValueEvalContext): Rt_Value? {
        val o = constantValue0
        if (o != null) {
            return o.value
        }

        var value: Rt_Value? = null

        try {
            value = ctx.addConstId(constId) {
                calcConstantValue(ctx)
            }
        } finally {
            constantValue0 = One(value)
        }

        return value
    }

    private fun calcConstantValue(ctx: V_ConstantValueEvalContext): Rt_Value? {
        val vExpr = compile()
        return try {
            vExpr.constantValue(ctx)
        } catch (e: Rt_Exception) {
            when (e.err) {
                is Rt_CommonError -> defCtx.msgCtx.error(vExpr.pos, e.err.code, e.fullMessage())
            }
            null
        } catch (e: Throwable) {
            // ignore
            null
        }
    }
}
