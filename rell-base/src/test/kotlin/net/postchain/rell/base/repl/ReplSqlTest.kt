/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.repl

import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.testutils.RellCodeTester
import org.junit.Test

class ReplSqlTest: BaseRellTest(useSql = true) {
    @Test fun testEntityDeclare() {
        repl.chk("entity user { name; }", "CTE:<console>:def_repl:ENTITY")
        repl.chk("user @* {}", "CTE:<console>:unknown_name:user")
    }

    @Test fun testEntityModule() {
        file("module.rell", "entity user { name; }")
        tst.replModule = ""
        repl.chk("\\db-auto", "CMD:db-auto:true")
        repl.chk("user @* {}", "RES:list<user>[]")
        repl.chk("user @* {} (.name)", "RES:list<text>[]")
    }

    @Test fun testEntityImport() {
        initSql("entity user { name; }", "c0.user", "name", "0,'Bob'", "1,'Alice'")
        file("u.rell", "module; entity user { name; }")
        file("c.rell", "module; entity company { name; }")
        repl.chk("\\db-auto", "CMD:db-auto:true")
        repl.chk("import u;")
        repl.chk("u.user @* {}", "RES:list<u:user>[u:user[0],u:user[1]]")
        repl.chk("u.user @* {} (.name)", "RES:list<text>[text[Bob],text[Alice]]")
        repl.chk("import c;")
        repl.chk("c.company @* {}", "RES:list<c:company>[]")
    }

    @Test fun testEntityModify() {
        file("u.rell", "module; entity user { name; mutable value: integer; }")
        repl.chk("\\db-auto", "CMD:db-auto:true")
        repl.chk("import u;")
        repl.chk("u.user @* {} (.name)", "RES:list<text>[]")
        repl.chk("create u.user ('Bob', 123)", "CTE:<console>:no_db_update:repl")
        repl.chk("update u.user @* {} (value = 123);", "CTE:<console>:no_db_update:repl")
        repl.chk("delete u.user @* {};", "CTE:<console>:no_db_update:repl")
    }

    @Test fun testObjectDeclare() {
        repl.chk("object state { mutable x: integer = 123; }", "CTE:<console>:def_repl:OBJECT")
        repl.chk("state.x", "CTE:<console>:unknown_name:state")
    }

    @Test fun testObjectModule() {
        file("module.rell", "object state { mutable x: integer = 123; }")
        tst.replModule = ""
        repl.chk("\\db-auto", "CMD:db-auto:true")
        repl.chk("state.x", "RES:int[123]")
        repl.chk("state.x = 456;", "CTE:<console>:no_db_update:repl")
    }

    @Test fun testObjectImport() {
        initSql("object state { mutable x: integer = 123; }", "c0.state", "x")
        file("s.rell", "module; object state { mutable x: integer = 123; }")
        file("t.rell", "module; object etats { mutable y: integer = 456; }")
        repl.chk("\\db-auto", "CMD:db-auto:true")
        repl.chk("import s;")
        repl.chk("s.state.x", "RES:int[123]")
        repl.chk("s.state.x = 456;", "CTE:<console>:no_db_update:repl")
        repl.chk("import t;")
        repl.chk("t.etats.y", "RES:int[456]")
        repl.chk("t.etats.y = 789;", "CTE:<console>:no_db_update:repl")
    }

    @Test fun testSqlInitError() {
        initSql("entity user { name; }", "c0.user", "name", "0,'Bob'", "1,'Alice'")
        file("u1.rell", "module; @log entity user { name; }")
        file("u2.rell", "module; entity user { name; }")
        repl.chk("\\db-auto", "CMD:db-auto:true")
        repl.chk("import u1;", "rt_err:meta:entity:diff_log:user:false:true")
        repl.chk("import u2;")
        repl.chk("u2.user @* {} (.name)", "RES:list<text>[text[Bob],text[Alice]]")
    }

    @Test fun testDbUpdate() {
        file("u.rell", "module; entity user { name; }")
        repl.chk("import u;")
        repl.chk("u.user @* {}", "rt_err:sqlerr:0")
        repl.chk("\\db-update")
        repl.chk("u.user @* {}", "RES:list<u:user>[]")
    }

    @Test fun testDbUpdate2() {
        file("c.rell", "module; entity company { name; }")
        file("u.rell", "module; import c; entity user { name; c.company; }")

        repl.chk("import c;")
        repl.chk("\\db-update")
        repl.chk("c.company @* {}", "RES:list<c:company>[]")

        repl.chk("import u;")
        repl.chk("u.user @* {}", "rt_err:sqlerr:0")
        repl.chk("c.company @* {}", "RES:list<c:company>[]")
        repl.chk("\\db-update")
        repl.chk("u.user @* {}", "RES:list<u:user>[]")
        repl.chk("c.company @* {}", "RES:list<c:company>[]")
    }

    @Test fun testDbAuto() {
        file("c.rell", "module; entity company { name; }")
        file("u.rell", "module; import c; entity user { name; c.company; }")
        file("d.rell", "module; entity data { value: integer; }")

        repl.chk("import c;")
        repl.chk("c.company @* {}", "rt_err:sqlerr:0")
        repl.chk("\\db-auto", "CMD:db-auto:true")
        repl.chk("c.company @* {}", "RES:list<c:company>[]")

        repl.chk("import u;")
        repl.chk("u.user @* {}", "RES:list<u:user>[]")

        repl.chk("\\db-auto", "CMD:db-auto:false")
        repl.chk("import d;")
        repl.chk("d.data @* {}", "rt_err:sqlerr:0")
        repl.chk("\\db-update")
        repl.chk("d.data @* {}", "RES:list<d:data>[]")
    }

    private fun initSql(defs: String, insTable: String, insColumns: String, vararg insData: String) {
        val t = RellCodeTester(tstCtx)
        t.def(defs)
        for (data in insData) t.insert(insTable, insColumns, data)
        t.init()
    }
}
