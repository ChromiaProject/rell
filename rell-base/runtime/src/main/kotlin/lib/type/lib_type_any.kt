/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.rell.base.compiler.base.lib.C_SysFunction
import net.postchain.rell.base.compiler.base.lib.C_SysFunctionBody
import net.postchain.rell.base.runtime.Rt_TextValue
import net.postchain.rell.base.runtime.rSimple

object Lib_Type_Any {
    val ToText_R = C_SysFunction.rSimple { a ->
        val s = a.str()
        Rt_TextValue.get(s)
    }

    // No DB-operation, as most types do not support it.
    val ToText_NoDb = C_SysFunctionBody.direct(ToText_R, pure = true)
}
