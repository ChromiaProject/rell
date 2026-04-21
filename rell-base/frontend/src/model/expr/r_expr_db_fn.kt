/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.expr

import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.toImmList
import java.util.regex.Pattern

sealed class Db_SysFunction(val name: String) {
    companion object {
        fun simple(name: String, sql: String): Db_SysFunction = Db_SysFn_Simple(name, sql)

        fun template(name: String, arity: Int, template: String): Db_SysFunction =
            Db_SysFn_Template(name, arity, template)

        fun cast(name: String, type: String): Db_SysFunction = Db_SysFn_Template(name, 1, "(#0)::$type")
    }
}

class Db_SysFn_Simple(name: String, val sql: String): Db_SysFunction(name)

class Db_SysFn_Template(name: String, val arity: Int, template: String): Db_SysFunction(name) {
    val fragments: ImmList<Pair<String?, Int?>> = let {
        val pat = Pattern.compile("#\\d")
        val m = pat.matcher(template)

        val list = mutableListOf<Pair<String?, Int?>>()
        var i = 0

        while (m.find()) {
            val start = m.start()
            val end = m.end()
            if (i < start) list.add(Pair(template.substring(i, start), null))
            val v = m.group().substring(1).toInt()
            list.add(Pair(null, v))
            i = end
        }

        if (i < template.length) list.add(Pair(template.substring(i), null))

        list.toImmList()
    }
}

object Db_SysFn_Aggregation {
    val Sum = Db_SysFunction.template("sum", 1, "COALESCE(SUM(#0),0)")
    val Min = Db_SysFunction.simple("min", "MIN")
    val Max = Db_SysFunction.simple("max", "MAX")
}
