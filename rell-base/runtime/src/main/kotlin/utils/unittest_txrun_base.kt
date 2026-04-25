/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils

import net.postchain.rell.base.lib.test.Rt_TestBlockValue
import net.postchain.rell.base.runtime.Rt_CallContext
import net.postchain.rell.base.runtime.utils.Rt_Utils

interface Rt_UnitTestBlockRunner {
    fun runBlock(ctx: Rt_CallContext, block: Rt_TestBlockValue)
}

object Rt_NullUnitTestBlockRunner: Rt_UnitTestBlockRunner {
    override fun runBlock(ctx: Rt_CallContext, block: Rt_TestBlockValue): Unit =
        throw Rt_Utils.errNotSupported("Block execution not supported")
}
