/*
 * Copyright (C) 2023 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.rell.base.compiler.base.lib.C_SysFunction
import net.postchain.rell.base.compiler.base.lib.C_SysFunctionBody
import net.postchain.rell.base.model.expr.Db_SysFunction

object Lib_Type_Any {
    val ToText_R = C_SysFunction.rSimple { a ->
        val s = a.str()
        Rt_TextValue.get(s)
    }

    val ToText_Db = Db_SysFunction.cast("to_text", "TEXT")

    // No DB-operation, as most types do not support it.
    val ToText_NoDb = C_SysFunctionBody.direct(ToText_R, pure = true)
}
