/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.lmodel.L_ParamArity
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_StackPos
import net.postchain.rell.base.runtime.Rt_UnitValue

internal object Lib_Print {
    val NAMESPACE = Ld_NamespaceDsl.make {
        function("print", result = "unit", since = "0.6.0") {
            comment("""
                Prints the given value(s) to STDOUT, and then terminates the line.
                When multiple values are provided, each is separated by a single space character.
            """)
            param("values", type = "anything", arity = L_ParamArity.ZERO_MANY, comment = "the value(s) to print")
            bodyContextN { ctx, args ->
                val str = args.joinToString(" ") { it.str() }
                ctx.globalCtx.outPrinter.print(str)
                Rt_UnitValue
            }
        }

        function("log", result = "unit", since = "0.6.0") {
            comment("""
                Prints the given value(s) to the log, with a timestamp, and then terminates the line.
                When multiple values are provided, each is separated by a single space character.
            """)
            param("values", type = "anything", arity = L_ParamArity.ZERO_MANY, comment = "the value(s) to log")

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
