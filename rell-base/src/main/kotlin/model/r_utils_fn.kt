/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.rell.base.runtime.Rt_CallContext
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.utils.checkEquals

fun interface R_SysFunction {
    fun call(ctx: Rt_CallContext, args: List<Rt_Value>): Rt_Value
}

abstract class R_SysFunction_N: R_SysFunction {
    protected abstract fun call(args: List<Rt_Value>): Rt_Value

    final override fun call(ctx: Rt_CallContext, args: List<Rt_Value>): Rt_Value {
        val res = call(args)
        return res
    }
}

abstract class R_SysFunction_1: R_SysFunction {
    protected abstract fun call(arg: Rt_Value): Rt_Value

    final override fun call(ctx: Rt_CallContext, args: List<Rt_Value>): Rt_Value {
        checkEquals(args.size, 1)
        val res = call(args[0])
        return res
    }
}

abstract class R_SysFunction_2: R_SysFunction {
    protected abstract fun call(arg1: Rt_Value, arg2: Rt_Value): Rt_Value

    final override fun call(ctx: Rt_CallContext, args: List<Rt_Value>): Rt_Value {
        checkEquals(args.size, 2)
        val res = call(args[0], args[1])
        return res
    }
}

abstract class R_SysFunctionEx_N: R_SysFunction

abstract class R_SysFunctionEx_0: R_SysFunction {
    protected abstract fun call(ctx: Rt_CallContext): Rt_Value

    final override fun call(ctx: Rt_CallContext, args: List<Rt_Value>): Rt_Value {
        checkEquals(args.size, 0)
        val res = call(ctx)
        return res
    }
}

abstract class R_SysFunctionEx_1: R_SysFunction {
    protected abstract fun call(ctx: Rt_CallContext, arg: Rt_Value): Rt_Value

    final override fun call(ctx: Rt_CallContext, args: List<Rt_Value>): Rt_Value {
        checkEquals(args.size, 1)
        val res = call(ctx, args[0])
        return res
    }
}

abstract class R_SysFunctionEx_2: R_SysFunction {
    protected abstract fun call(ctx: Rt_CallContext, arg1: Rt_Value, arg2: Rt_Value): Rt_Value

    final override fun call(ctx: Rt_CallContext, args: List<Rt_Value>): Rt_Value {
        checkEquals(args.size, 2)
        val res = call(ctx, args[0], args[1])
        return res
    }
}
