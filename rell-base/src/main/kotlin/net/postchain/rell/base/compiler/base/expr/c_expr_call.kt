/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.expr

import net.postchain.rell.base.compiler.ast.S_CallArgument
import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.core.*
import net.postchain.rell.base.compiler.base.utils.C_CodeMsg
import net.postchain.rell.base.compiler.base.utils.C_MessageManager
import net.postchain.rell.base.compiler.base.utils.C_Utils
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.compiler.vexpr.V_Expr
import net.postchain.rell.base.model.R_Attribute
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.ImmMap
import net.postchain.rell.base.utils.toImmList

interface C_CallTypeHints {
    fun getTypeHint(index: Int?, name: R_Name?): C_TypeHint
}

object C_CallTypeHints_None: C_CallTypeHints {
    override fun getTypeHint(index: Int?, name: R_Name?) = C_TypeHint.NONE
}

internal sealed class C_CallArgumentValue(val pos: S_Pos)
internal class C_CallArgumentValue_Expr(pos: S_Pos, val vExpr: V_Expr): C_CallArgumentValue(pos)
internal class C_CallArgumentValue_Wildcard(pos: S_Pos): C_CallArgumentValue(pos)

internal class C_CallArgumentHandle(
    val index: Int,
    val nameHand: C_NameHandle?,
    val value: C_CallArgumentValue,
) {
    fun toCallArgument() = C_CallArgument(index, nameHand?.name, value)
}

internal class C_CallArgument(val index: Int, val name: C_Name?, val value: C_CallArgumentValue) {
    companion object {
        fun compileAttributes(
            ctx: C_ExprContext,
            args: List<S_CallArgument>,
            attrs: ImmMap<R_Name, R_Attribute>,
        ): List<C_CallArgument> {
            val typeHints = C_AttrsTypeHints(attrs)

            val rawArgs = compileArguments(ctx, args, typeHints)

            for (arg in rawArgs) {
                if (arg.nameHand != null) {
                    val ideInfo = attrs[arg.nameHand.rName]?.ideInfo ?: C_IdeSymbolInfo.UNKNOWN
                    arg.nameHand.setIdeInfo(ideInfo)
                }
            }

            return rawArgs.map { it.toCallArgument() }
        }

        fun compileArguments(
            ctx: C_ExprContext,
            args: List<S_CallArgument>,
            typeHints: C_CallTypeHints,
        ): ImmList<C_CallArgumentHandle> {
            val res = mutableListOf<C_CallArgumentHandle>()
            var positional = true

            for ((index, arg) in args.withIndex()) {
                val cArg = arg.compile(ctx, index, positional, typeHints)
                if (cArg.nameHand != null) positional = false
                res.add(cArg)
            }

            return res.toImmList()
        }

        fun toAttrArguments(ctx: C_ExprContext, callArgs: List<C_CallArgument>, place: C_CodeMsg): List<C_AttrArgument> {
            var wildcardErr = false
            return callArgs.mapNotNull {
                when (it.value) {
                    is C_CallArgumentValue_Expr -> {
                        C_AttrArgument(it.index, it.name, it.value.vExpr)
                    }
                    is C_CallArgumentValue_Wildcard -> {
                        if (!wildcardErr) {
                            wildcardErr = true
                            val code = "expr:call:wildcard:${place.code}"
                            val msg = "Wildcards not allowed in ${place.msg}"
                            ctx.msgCtx.error(it.value.pos, code, msg)
                        }
                        if (it.name == null) null else {
                            val vExpr = C_ExprUtils.errorVExpr(ctx, it.value.pos)
                            C_AttrArgument(it.index, it.name, vExpr)
                        }
                    }
                }
            }
        }
    }

    private class C_AttrsTypeHints(private val attrs: ImmMap<R_Name, R_Attribute>): C_CallTypeHints {
        override fun getTypeHint(index: Int?, name: R_Name?): C_TypeHint {
            val attr = if (name != null) {
                attrs[name]
            } else if (attrs.size == 1) {
                attrs.values.iterator().next()
            } else {
                null
            }
            return C_TypeHint.ofType(attr?.type)
        }
    }
}

internal class C_CallArguments(
    val all: ImmList<C_CallArgument>,
    val positional: ImmList<C_CallArgument>,
    val named: ImmList<C_NameValue<C_CallArgument>>,
) {
    fun validate(msgMgr: C_MessageManager): Boolean {
        var res = true

        for (arg in all) {
            when (arg.value) {
                is C_CallArgumentValue_Expr -> {
                    val ok = C_Utils.checkUnitType(msgMgr, arg.value.pos, arg.value.vExpr.type) {
                        "expr_arg_unit" toCodeMsg "Argument expression returns nothing"
                    }
                    res = res && ok
                }
                is C_CallArgumentValue_Wildcard -> {}
            }
        }

        return res
    }
}
