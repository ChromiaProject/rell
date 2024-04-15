/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.lib.type.Rt_UnitValue
import net.postchain.rell.base.lmodel.L_ParamArity
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_StackPos

object Lib_Print {
    val NAMESPACE = Ld_NamespaceDsl.make {
        function("print", result = "unit", since = "0.6.0") {
            comment("Prints the given message to the node log")
            param("values", type = "anything", arity = L_ParamArity.ZERO_MANY, comment = "Any string or value")
            bodyContextN { ctx, args ->
                val str = args.joinToString(" ") { it.str() }
                ctx.globalCtx.outPrinter.print(str)
                Rt_UnitValue
            }
        }

        function("log", result = "unit", since = "0.6.0") {
            comment("Prints the given message to the node log with timestamp")
            param("values", type = "anything", arity = L_ParamArity.ZERO_MANY, comment = "Any string or value")

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
