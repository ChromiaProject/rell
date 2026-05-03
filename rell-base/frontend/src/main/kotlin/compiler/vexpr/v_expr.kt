/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.vexpr

import net.postchain.rell.base.compiler.ast.S_CallArgument
import net.postchain.rell.base.compiler.ast.S_CallArguments
import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.core.*
import net.postchain.rell.base.compiler.base.expr.*
import net.postchain.rell.base.compiler.base.fn.C_FunctionCallTargetBase
import net.postchain.rell.base.compiler.base.fn.C_FunctionCallTarget_FunctionType
import net.postchain.rell.base.compiler.base.fn.C_FunctionUtils
import net.postchain.rell.base.compiler.base.lib.C_TypeMember
import net.postchain.rell.base.compiler.base.utils.C_Error
import net.postchain.rell.base.compiler.base.utils.C_Errors
import net.postchain.rell.base.compiler.base.utils.C_PosCodeMsg
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.Db_Expr
import net.postchain.rell.base.model.expr.R_Expr
import net.postchain.rell.base.model.rr.RR_ConstantValue
import net.postchain.rell.base.utils.*

class V_ExprInfo(
    val type: R_Type,
    val subExprs: ImmList<V_Expr>,
    val hasDbModifications: Boolean = false,
    val canBeDbExpr: Boolean = true,
    val dependsOnDbAtEntity: Boolean = false,
    val dependsOnAtExprs: ImmSet<R_AtExprId> = immSetOf(),
) {
    companion object {
        fun simple(
            type: R_Type,
            vararg subExprs: V_Expr,
            hasDbModifications: Boolean = false,
            canBeDbExpr: Boolean = true,
            dependsOnDbAtEntity: Boolean = false,
            dependsOnAtExprs: ImmSet<R_AtExprId> = immSetOf(),
        ): V_ExprInfo {
            return simple(
                type,
                subExprs.toImmList(),
                hasDbModifications = hasDbModifications,
                canBeDbExpr = canBeDbExpr,
                dependsOnDbAtEntity = dependsOnDbAtEntity,
                dependsOnAtExprs = dependsOnAtExprs,
            )
        }

        fun simple(
            type: R_Type,
            subExprs: List<V_Expr>,
            hasDbModifications: Boolean = false,
            canBeDbExpr: Boolean = true,
            dependsOnDbAtEntity: Boolean = false,
            dependsOnAtExprs: ImmSet<R_AtExprId> = immSetOf(),
        ): V_ExprInfo {
            val depsOnDbAtEnt = dependsOnDbAtEntity || subExprs.any { it.info.dependsOnDbAtEntity }
            val canBeDb = !depsOnDbAtEnt || (canBeDbExpr && subExprs.all { it.info.canBeDbExpr })
            return V_ExprInfo(
                    type = type,
                    subExprs = subExprs.toImmList(),
                    hasDbModifications = hasDbModifications || subExprs.any { it.info.hasDbModifications },
                    canBeDbExpr = canBeDb,
                    dependsOnDbAtEntity = depsOnDbAtEnt,
                    dependsOnAtExprs = dependsOnAtExprs + subExprs.flatMap { it.info.dependsOnAtExprs },
            )
        }
    }
}

class V_ConstantValueEvalContext {
    private val constIds = mutableSetOf<GlobalConstantId>()

    fun <T> addConstId(constId: GlobalConstantId, code: () -> T): T? {
        if (!constIds.add(constId)) {
            return null
        }
        try {
            return code()
        } finally {
            constIds.remove(constId)
        }
    }
}

class V_GlobalConstantRestriction(val code: String, val msg: String?)

class V_ExprWrapper(
    private val msgCtx: C_MessageContext,
    private val expr: V_Expr,
    private val msgSupplier: () -> C_PosCodeMsg? = { null },
) {
    val pos = expr.pos
    val type: R_Type = expr.type

    fun unwrap(): V_Expr {
        val cm = msgSupplier()
        if (cm != null) {
            msgCtx.warning(cm.pos, cm.code, cm.msg)
        }
        return expr
    }
}

abstract class V_Expr(
    protected val exprCtx: C_ExprContext,
    val pos: S_Pos,
) {
    protected val msgCtx = exprCtx.msgCtx

    val info: V_ExprInfo by lazy {
        exprInfo0()
    }

    val type: R_Type by lazy {
        info.type
    }

    val varStatesDelta: C_ExprVarStatesDelta by lazy {
        varStatesDelta0()
    }

    protected abstract fun exprInfo0(): V_ExprInfo

    open fun varStatesDelta0(): C_ExprVarStatesDelta {
        return C_ExprVarStatesDelta.forExpressions(info.subExprs)
    }

    protected open fun toDbExpr0(): Db_Expr = throw C_Errors.errExprDbNotAllowed(pos)

    abstract fun toRExpr(): R_Expr

    fun toDbExpr(): Db_Expr {
        if (info.dependsOnDbAtEntity) {
            return toDbExpr0()
        }
        val rExpr = toRExpr()
        return C_ExprUtils.toDbExpr(exprCtx.msgCtx, pos, rExpr)
    }

    fun toDbExprWhat(): C_DbAtWhatValue {
        val compilerOptions = exprCtx.globalCtx.compilerOptions
        val direct = (info.canBeDbExpr && type.sqlInfo.isSqlCompatible(compilerOptions))
                || !compilerOptions.complexWhatEnabled
        return if (direct) {
            toDbExprWhatDirect()
        } else {
            toDbExprWhat0()
        }
    }

    protected open fun toDbExprWhat0(): C_DbAtWhatValue {
        return toDbExprWhatDirect()
    }

    private fun toDbExprWhatDirect(): C_DbAtWhatValue {
        val dbExpr = toDbExpr()
        return C_DbAtWhatValue_Simple(dbExpr)
    }

    open fun destination(): C_Destination {
        throw C_Errors.errBadDestination(pos)
    }

    fun member(ctx: C_ExprContext, memberNameHand: C_NameHandle, safe: Boolean, exprHint: C_ExprHint): C_Expr {
        val memberName = memberNameHand.name

        var self = this
        var selfType = type
        var baseNulled: C_VarNulled? = null

        if (safe) {
            if (selfType.isNotError()) {
                if (selfType !is R_NullableType) {
                    baseNulled = C_VarNulled.NO
                }
                self = self.asNullable().unwrap()
                selfType = self.type
                if (selfType !is R_NullableType) {
                    val typeStr = type.strCode()
                    val msg = "Wrong type for operator '?.': $typeStr"
                    ctx.msgCtx.error(memberName.pos, "expr_safemem_type:[$typeStr]:$memberName", msg)
                }
            }
        } else {
            if (selfType is R_NullableType) {
                val nameStr = memberName.str
                val msg = "Cannot access member '$nameStr' of nullable type ${type.str()}"
                ctx.msgCtx.error(memberName.pos, "expr_mem_null:${type.strCode()}:$nameStr", msg)
            }
        }

        selfType = C_Types.removeNullable(selfType)

        val members = ctx.typeMgr.getValueMembers(selfType, memberName.rName)
        val member = C_TypeMember.getMember(ctx.msgCtx, members, exprHint, memberName, selfType, "type_value_member")

        if (member == null) {
            memberNameHand.setIdeInfo(C_IdeSymbolInfo.UNKNOWN)
            return C_ExprUtils.errorExpr(ctx, memberName.pos)
        }

        val actualSafe = safe && (self.type is R_NullableType)
        return self.member0(ctx, selfType, memberNameHand, member, actualSafe, baseNulled, exprHint)
    }

    open fun member0(
        ctx: C_ExprContext,
        selfType: R_Type,
        memberNameHand: C_NameHandle,
        memberValue: C_TypeValueMember,
        safe: Boolean,
        baseNulled: C_VarNulled?,
        exprHint: C_ExprHint,
    ): C_Expr {
        val memberName = memberNameHand.name
        val link = C_MemberLink(this, selfType, memberName.pos, memberName, safe)
        return memberValue.compile(ctx, link, memberNameHand, baseNulled)
    }

    internal open fun call(ctx: C_ExprContext, pos: S_Pos, args: S_CallArguments, resTypeHint: C_TypeHint): V_Expr {
        return callCommon(ctx, pos, args.list, resTypeHint, type, false)
    }

    internal fun callCommon(
        ctx: C_ExprContext,
        pos: S_Pos,
        args: List<S_CallArgument>,
        resTypeHint: C_TypeHint,
        type: R_Type,
        safe: Boolean,
    ): V_Expr {
        if (type is R_FunctionType) {
            val callTargetBase = C_FunctionCallTargetBase.forFunctionType(ctx, pos, type)
            val callTarget = C_FunctionCallTarget_FunctionType(callTargetBase, this, type, safe)
            val vCall = C_FunctionUtils.compileRegularCall(callTargetBase, callTarget, args, resTypeHint)
            return vCall.vExpr()
        }

        // Validate args.
        args.forEachIndexed { index, arg ->
            ctx.msgCtx.consumeError {
                val cArg = arg.compile(ctx, index, true, C_CallTypeHints_None)
                cArg.nameHand?.setIdeInfo(C_IdeSymbolInfo.UNKNOWN)
            }
        }

        if (type == R_CtErrorType) {
            return C_ExprUtils.errorVExpr(ctx, pos)
        } else {
            val typeStr = type.strCode()
            throw C_Error.stop(pos, "expr_call_nofn:$typeStr", "Not a function: value of type $typeStr")
        }
    }

    fun traverse(code: (V_Expr) -> Boolean) {
        val more = code(this)
        if (more) {
            for (expr in info.subExprs) {
                expr.traverse(code)
            }
        }
    }

    fun <T: Any> traverseToSet(code: (V_Expr) -> Collection<T>): ImmSet<T> {
        val res = mutableListOf<T>()
        traverseToCollection(res, code)
        return res.toImmSet()
    }

    private fun <T> traverseToCollection(res: MutableCollection<T>, code: (V_Expr) -> Collection<T>) {
        traverse {
            val l = code(it)
            res.addAll(l)
            true
        }
    }

    open fun constantValue(ctx: V_ConstantValueEvalContext): RR_ConstantValue? = null

    open fun isAtExprItem(): Boolean = false
    open fun implicitTargetAttrName(): Name? = null
    open fun implicitAtWhereAttrName(): Name? = implicitTargetAttrName()
    open fun implicitAtWhatAttrName(): C_Name? = null
    open fun varKey(): C_VarStateKey? = null
    open fun globalConstantId(): GlobalConstantId? = null
    open fun globalConstantRestriction(): V_GlobalConstantRestriction? = null
    open fun asNullable(): V_ExprWrapper = asWrapper()
    open fun getDefMeta(): R_DefinitionMeta? = null

    fun asWrapper(): V_ExprWrapper = V_ExprWrapper(msgCtx, this)
}
