/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.expr.atexpr

import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.testutils.RellCodeTester
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.toImmMap

abstract class AtExprBaseTest: BaseRellTest(useSql = true) {
    protected abstract fun impKind(): AtExprTestKind
    protected var impKind = impKind()

    protected fun impNew(name: String) = impKind.impNew(name)
    protected fun impFrom(name: String) = impKind.impFrom(name)
    protected fun impRtErr(code: String) = impKind.impRtErr(code)
    protected fun impDefType(name: String, vararg attrs: String) = impKind.impDefType(tst, name, *attrs)
    protected fun impCreateObjs(t: RellCodeTester, name: String, vararg objs: String) = impKind.impCreateObjs(t, name, *objs)
    protected fun impCreateObjs(name: String, vararg objs: String) = impCreateObjs(tst, name, *objs)

    protected abstract class AtExprTestKind {
        abstract fun impNew(name: String): String
        abstract fun impFrom(name: String): String
        abstract fun impRtErr(code: String): String
        abstract fun impDefType(t: RellCodeTester, name: String, vararg attrs: String)
        abstract fun impCreateObjs(t: RellCodeTester, name: String, vararg objs: String)
    }

    protected class AtExprTestKind_Col_Struct: AtExprTestKind() {
        override fun impNew(name: String) = name
        override fun impFrom(name: String) = "get_$name()"
        override fun impRtErr(code: String) = "rt_err:$code"

        override fun impDefType(t: RellCodeTester, name: String, vararg attrs: String) {
            val attrsStr = attrs.joinToString(" ") { "$it;" }
            val code = "struct $name { $attrsStr }"
            t.def(code)
        }

        override fun impCreateObjs(t: RellCodeTester, name: String, vararg objs: String) {
            val values = objs.joinToString(", ") { "$name($it)" }
            t.def("function get_$name(): list<$name> = [$values];")
        }
    }

    // May be be useful in the future.
    protected class AtExprTestKind_Col_Tuple: AtExprTestKind() {
        private val types = mutableMapOf<String, Map<String, String>>()
        private val objs = mutableMapOf<String, List<Map<String, String>>>()

        override fun impNew(name: String) = ""
        override fun impFrom(name: String) = "get_$name()"
        override fun impRtErr(code: String) = "rt_err:$code"

        override fun impDefType(t: RellCodeTester, name: String, vararg attrs: String) {
            check(name !in types)
            val type = attrs.map {
                val parts = it.split(":")
                if (parts.size == 1) parts[0] to parts[0] else parts[0] to parts[1]
            }.toMap().toImmMap()
            types[name] = type
        }

        private fun typeExpr(name: String): String {
            val type = types[name]
            type ?: return name
            return type.entries.joinToString(", ", prefix = "(", postfix = ")") { "${it.key}: ${typeExpr(it.value)}" }
        }

        override fun impCreateObjs(t: RellCodeTester, name: String, vararg objs: String) {
            check(name !in objs)

            val type = types.getValue(name)

            val paramsStr = type.entries.joinToString(", ") { "${it.key}: ${typeExpr(it.value)}" }
            val exprStr = type.entries.joinToString(", ") { "${it.key} = ${it.key}" }
            t.def("function new_$name($paramsStr) = ($exprStr);")

            val values = objs.map { obj ->
                val map = obj.split(",")
                        .map { part ->
                            val sub = part.split(" = ").map { it.trim() }
                            checkEquals(2, sub.size)
                            check(sub[0] in type)
                            sub[0] to sub[1]
                        }
                        .toMap().toImmMap()
                type.keys.joinToString(", ", prefix = "new_$name(", postfix = ")") { "$it = ${map.getValue(it)}" }
            }

            val typeStr = typeExpr(name)
            val valuesStr = values.joinToString(", ")
            t.def("function get_$name(): list<$typeStr> = [$valuesStr];")
        }
    }

    protected class AtExprTestKind_Col_Entity: AtExprTestKind() {
        override fun impNew(name: String) = "create $name"
        override fun impFrom(name: String) = "get_$name()"
        override fun impRtErr(code: String) = "rt_err:$code"

        override fun impDefType(t: RellCodeTester, name: String, vararg attrs: String) {
            val attrsStr = attrs.joinToString(" ") { "$it;" }
            val code = "entity $name { $attrsStr }"
            t.def(code)
            t.def("function get_$name(): list<$name> = list($name @* {});")
        }

        override fun impCreateObjs(t: RellCodeTester, name: String, vararg objs: String) {
            if (objs.isNotEmpty()) {
                val code = objs.joinToString(" ") { "create $name($it);" }
                t.postInitOp(code)
            }
        }
    }

    protected class AtExprTestKind_Db: AtExprTestKind() {
        override fun impNew(name: String) = "create $name"
        override fun impFrom(name: String) = name
        override fun impRtErr(code: String) = "rt_err:sqlerr:0"

        override fun impDefType(t: RellCodeTester, name: String, vararg attrs: String) {
            val attrsStr = attrs.joinToString(" ") { "$it;" }
            val code = "entity $name { $attrsStr }"
            t.def(code)
        }

        override fun impCreateObjs(t: RellCodeTester, name: String, vararg objs: String) {
            if (objs.isNotEmpty()) {
                val code = objs.joinToString(" ") { "create $name($it);" }
                t.chkOp(code)
            }
        }
    }
}
