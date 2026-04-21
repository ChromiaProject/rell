/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.rell.base.compiler.base.lib.C_SysFunction
import net.postchain.rell.base.compiler.base.lib.C_SysFunctionBody
import net.postchain.rell.base.model.expr.Db_SysFunction
import net.postchain.rell.base.runtime.utils.Rt_Utils

/** Typed factory: creates an R_SysFunction from a single-arg lambda. */
fun C_SysFunction.Companion.rSimple(rCode: (Rt_Value) -> Rt_Value): R_SysFunction = R_SysFunction { _, args ->
    Rt_Utils.checkEquals(args.size, 1)
    rCode(args[0])
}

/** Typed factory: creates a C_SysFunctionBody from a single-arg lambda. */
fun C_SysFunctionBody.Companion.simple(
    dbFn: Db_SysFunction? = null,
    pure: Boolean = false,
    rCode: (Rt_Value) -> Rt_Value,
): C_SysFunctionBody {
    val rFn = C_SysFunction.rSimple(rCode)
    return C_SysFunctionBody.direct(rFn, dbFn, pure = pure)
}

/** Typed factory: creates a C_SysFunctionBody from a two-arg lambda. */
fun C_SysFunctionBody.Companion.simple(
    dbFn: Db_SysFunction? = null,
    pure: Boolean = false,
    rCode: (Rt_Value, Rt_Value) -> Rt_Value,
): C_SysFunctionBody {
    val rFn = R_SysFunction { _, args ->
        Rt_Utils.checkEquals(args.size, 2)
        rCode(args[0], args[1])
    }
    return C_SysFunctionBody.direct(rFn, dbFn, pure = pure)
}
