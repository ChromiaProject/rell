/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.lmodel.L_ParamArity
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_StackPos
import net.postchain.rell.base.lib.type.Rt_UnitValue

object Lib_Print {
    val NAMESPACE = Ld_NamespaceDsl.make {
        function("print", result = "unit") {
            param("values", type = "anything", arity = L_ParamArity.ZERO_MANY)
            bodyContextN { ctx, args ->
                val str = args.joinToString(" ") { it.str() }
                ctx.globalCtx.outPrinter.print(str)
                Rt_UnitValue
            }
        }

        function("log", result = "unit") {
            param("values", type = "anything", arity = L_ParamArity.ZERO_MANY)

            bodyMeta {
                val filePos = fnBodyMeta.callPos.toFilePos()

                bodyContextN { ctx, args ->
                    val str = args.joinToString(" ") { arg -> arg.str() }

                    val pos = R_StackPos(ctx.defCtx.defId, filePos)
                    val posStr = "[$pos]"
                    val fullStr = if (str.isEmpty()) posStr else "$posStr $str"
                    ctx.globalCtx.logPrinter.print(fullStr)

                    Rt_UnitValue
                }
            }
        }
    }
}
