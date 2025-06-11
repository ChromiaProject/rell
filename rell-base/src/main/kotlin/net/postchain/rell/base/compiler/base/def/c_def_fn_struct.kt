/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.def

import net.postchain.rell.base.compiler.ast.S_CallArgument
import net.postchain.rell.base.compiler.base.core.C_CompilerPass
import net.postchain.rell.base.compiler.base.core.C_TypeHint
import net.postchain.rell.base.compiler.base.expr.C_AttributeResolver
import net.postchain.rell.base.compiler.base.expr.C_CallArgument
import net.postchain.rell.base.compiler.base.expr.C_CreateContext
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.compiler.base.utils.C_CodeMsg
import net.postchain.rell.base.compiler.base.utils.C_IdeCompletionsUtils
import net.postchain.rell.base.compiler.vexpr.V_GlobalFunctionCall
import net.postchain.rell.base.compiler.vexpr.V_StructExpr
import net.postchain.rell.base.model.R_CallFrame
import net.postchain.rell.base.model.R_Struct
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.ide.IdeCompletion

class C_StructGlobalFunction(private val struct: R_Struct): C_GlobalFunction() {
    override fun compileCall0(
        ctx: C_ExprContext,
        name: LazyPosString,
        args: ImmList<S_CallArgument>,
        resTypeHint: C_TypeHint,
    ): V_GlobalFunctionCall {
        val fnPos = name.pos

        val initFrameGetter = struct.rDefBase?.initFrameGetter ?: R_CallFrame.NONE_INIT_FRAME_GETTER
        val createCtx = C_CreateContext(ctx, initFrameGetter, fnPos.toFilePos())

        val callArgs = C_CallArgument.compileAttributes(ctx, args, struct.attributes)
        val attrArgs = C_CallArgument.toAttrArguments(ctx, callArgs, C_CodeMsg("struct", "struct expression"))

        val attrs = C_AttributeResolver.resolveCreate(createCtx, struct.name, struct.attributes, attrArgs, fnPos)

        val dbModRes = ctx.getDbModificationRestriction()
        if (dbModRes != null) {
            ctx.executor.onPass(C_CompilerPass.VALIDATION) {
                val dbModAttr = attrs.implicitAttrs.firstOrNull { it.attr.isExprDbModification }
                if (dbModAttr != null) {
                    val code = "${dbModRes.code}:attr:${dbModAttr.attr.name}"
                    val msg = "${dbModRes.msg} (default value of attribute '${dbModAttr.attr.name}')"
                    ctx.msgCtx.error(fnPos, code, msg)
                }
            }
        }

        val vExpr = V_StructExpr(ctx, fnPos, struct, attrs.explicitAttrs, attrs.implicitAttrs)
        return V_GlobalFunctionCall(vExpr, null, immMapOf())
    }

    override fun ideGetParameterCompletions(): ImmMultimap<String, IdeCompletion> {
        return struct.attributes
            .map { (rName, rAttr) ->
                rName.str to C_IdeCompletionsUtils.makeIdeCompletion(rAttr.docSymbol, struct.name)
            }
            .toImmMultimap()
    }
}
