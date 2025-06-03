/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.expr

import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.toImmList
import java.util.regex.Pattern

sealed class Db_SysFunction(val name: String) {
    abstract fun toSql(ctx: SqlGenContext, bld: SqlBuilder, args: List<RedDb_Expr>)

    companion object {
        val COALESCE = simple("coalesce", "COALESCE")

        fun simple(name: String, sql: String): Db_SysFunction {
            return Db_SysFn_Simple(name, sql)
        }

        fun template(name: String, arity: Int, template: String): Db_SysFunction {
            return Db_SysFn_Template(name, arity, template)
        }

        fun cast(name: String, type: String): Db_SysFunction {
            return Db_SysFn_Template(name, 1, "(#0)::$type")
        }
    }
}

private class Db_SysFn_Simple(name: String, val sql: String): Db_SysFunction(name) {
    override fun toSql(ctx: SqlGenContext, bld: SqlBuilder, args: List<RedDb_Expr>) {
        bld.append(sql)
        bld.append("(")
        bld.append(args, ", ") {
            it.toSql(ctx, bld, false)
        }
        bld.append(")")
    }
}

private class Db_SysFn_Template(name: String, private val arity: Int, template: String): Db_SysFunction(name) {
    private val fragments: ImmList<Pair<String?, Int?>> = let {
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

    override fun toSql(ctx: SqlGenContext, bld: SqlBuilder, args: List<RedDb_Expr>) {
        checkEquals(args.size, arity)
        for ((first, second) in fragments) {
            if (first != null) bld.append(first)
            if (second != null) args[second].toSql(ctx, bld, false)
        }
    }
}

object Db_SysFn_Aggregation {
    val Sum = Db_SysFunction.template("sum", 1, "COALESCE(SUM(#0),0)")
    val Min = Db_SysFunction.simple("min", "MIN")
    val Max = Db_SysFunction.simple("max", "MAX")
}
