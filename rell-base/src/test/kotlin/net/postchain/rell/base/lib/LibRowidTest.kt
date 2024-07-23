/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.testutils.BaseRellTest
import org.junit.Test

class LibRowidTest: BaseRellTest() {
    @Test fun testConstructor() {
        chk("_type_of(rowid(0))", "text[rowid]")
        chk("rowid(0)", "rowid[0]")
        chk("rowid(12345)", "rowid[12345]")
        chk("rowid()", "ct_err:expr:call:missing_args:[rowid]:[0:value]")
        chk("rowid(1, 2)", "ct_err:expr:call:too_many_args:[rowid]:1:2")
        chk("rowid('a')", "ct_err:expr_call_badargs:[rowid]:[text]")
        chk("rowid(false)", "ct_err:expr_call_badargs:[rowid]:[boolean]")
        chk("rowid(rowid(0))", "ct_err:expr_call_badargs:[rowid]:[rowid]")
        chk("rowid(-1)", "rt_err:rowid(integer):negative:-1")
    }

    @Test fun testConstructorDb() {
        tstCtx.useSql = true
        def("entity user { name; value: integer; }")
        insert("c0.user", "name,value", "1,'Bob',123", "2,'Alice',456", "3,'Trudy',789")
        chk("user @* {} ( rowid(.value) )", "list<rowid>[rowid[123],rowid[456],rowid[789]]")
        chk("user @* {} ( rowid(.name.size()) )", "list<rowid>[rowid[3],rowid[5],rowid[5]]")
        chk("user @* { rowid(.value) == rowid(456) } ( .name )", "ct_err:expr_call_nosql:rowid")
    }

    @Test fun testValue() {
        chk("_type_of(rowid(0).to_integer())", "text[integer]")
        chk("rowid(0).to_integer()", "int[0]")
        chk("rowid(12345).to_integer()", "int[12345]")
    }

    @Test fun testValueDb() {
        tstCtx.useSql = true
        def("entity user { name; }")
        insert("c0.user", "name", "1,'Bob'", "2,'Alice'", "3,'Trudy'")
        chk("user @* {} ( .rowid )", "list<rowid>[rowid[1],rowid[2],rowid[3]]")
        chk("user @* {} ( .rowid.to_integer() )", "list<integer>[int[1],int[2],int[3]]")
        chk("user @* { .rowid.to_integer() == 2 } ( .name )", "list<text>[text[Alice]]")
    }
}
