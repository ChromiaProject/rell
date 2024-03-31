/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.testutils

abstract class BaseTesterTest(useSql: Boolean): BaseContextTest(useSql) {
    protected abstract val tst: RellBaseTester

    fun file(path: String, text: String) = tst.file(path, text)
    fun mainModule(vararg modules: String) = tst.mainModule(*modules)
    fun def(defs: List<String>) = tst.def(defs)
    fun def(def: String) = tst.def(def)
    fun insert(table: String, columns: String, vararg rows: String) = tst.insert(table, columns, *rows)
    fun insert(insert: String) = tst.insert(listOf(insert))
    fun insert(inserts: List<String>) = tst.insert(inserts)

    fun chk(expr: String, expected: String) = tst.chk(expr, expected)
    fun chkEx(code: String, expected: String) = tst.chkEx(code, expected)
    fun chkExOut(code: String, expected: String, vararg expectedOut: String) = tst.chkExOut(code, expected, *expectedOut)
    fun chkCompile(code: String, expected: String) = tst.chkCompile(code, expected)

    fun chkOut(vararg expected: String) = tst.chkOut(*expected)
    fun chkLog(vararg expected: String) = tst.chkLog(*expected)

    fun chkData(vararg expected: String) = tst.chkData(*expected)
    fun chkDataNew(vararg expected: String) = tst.chkDataNew(*expected)
    fun chkDataRaw(vararg expected: String) = tst.chkDataRaw(*expected)

    fun chkVerRt(code: String, version: String, expOld: String, expNew: String) {
        tst.chkVerRt(code, version, expOld, expNew)
    }

    fun chkVerRtExpr(expr: String, version: String, expOld: String, expNew: String) {
        tst.chkVerRt("query q() = $expr;", version, expOld, expNew)
    }
}
