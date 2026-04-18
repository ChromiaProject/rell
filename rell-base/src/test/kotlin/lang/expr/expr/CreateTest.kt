/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.expr.expr

import net.postchain.rell.base.testutils.BaseRellTest
import kotlin.test.Test

class CreateTest: BaseRellTest(useSql = true) {
    override fun entityDefs() = listOf(
        "entity city { name: text; }",
        "entity person { name: text; city; street: text; house: integer; score: integer; }",
    )

    @Test fun testCreateCity() {
        chkData()

        chkOp("create city ( 'New York' );")
        chkDataNew("city(1,New York)")

        chkOp("create city ( 'San Francisco' );")
        chkDataNew("city(2,San Francisco)")

        chkOp("create city ( 'Los Angeles' );")
        chkDataNew("city(3,Los Angeles)")
    }

    @Test fun testCreatePerson() {
        chkData()

        chkOp("""
            create city ( 'New York' );
            create city ( 'San Francisco' );
            create city ( 'Los Angeles' );
        """)
        chkDataNew("city(1,New York)", "city(2,San Francisco)", "city(3,Los Angeles)")

        chkOp("create person ( name = 'James', city @ { 'Los Angeles' }, street = 'Evergreen Ave', house = 5, score = 100 );")
        chkDataNew("person(4,James,3,Evergreen Ave,5,100)")

        chkOp("create person ( name = 'Mike', city @ { 'New York' }, street = 'Grand St', house = 7, score = 250 );")
        chkDataNew("person(5,Mike,1,Grand St,7,250)")
    }

    @Test fun testDefaultValues() {
        def("entity person { name: text; year: integer; score: integer = 777; status: text = 'Unknown'; }")
        chkData()

        chkOp("create person();", "ct_err:attr_missing:[person]:name,year")
        chkOp("create person(name = 'Bob');", "ct_err:attr_missing:[person]:year")
        chkOp("create person(year = 1980);", "ct_err:attr_missing:[person]:name")
        chkDataNew()

        chkOp("create person(name = 'Bob', year = 1980);")
        chkDataNew("person(1,Bob,1980,777,Unknown)")

        chkOp("create person(name = 'Alice', year = 1985, score = 55555);")
        chkDataNew("person(2,Alice,1985,55555,Unknown)")

        chkOp("create person(name = 'Trudy', year = 2000, status = 'Ready');")
        chkDataNew("person(3,Trudy,2000,777,Ready)")

        chkOp("create person(name = 'Will', year = 1990, status = 'Busy', score = 1000);")
        chkDataNew("person(4,Will,1990,1000,Busy)")
    }

    @Test fun testDefaultValueTypeErr() {
        tst.defs = listOf()
        chkCompile("entity person { name: text; year: integer = 'Hello'; }", "ct_err:attr_type:year:[integer]:[text]")
        chkCompile("entity person { name: text = 12345; year: integer; }", "ct_err:attr_type:name:[text]:[integer]")
    }

    @Test fun testDefaultValueVariable() {
        def("entity default_score { mutable value: integer; }")
        def("entity person { name: text; score: integer = default_score@{}.value; }")
        chkData()

        chkOp("create default_score( 100 );")
        chkDataNew("default_score(1,100)")

        chkOp("create person('Bob');")
        chkDataNew("person(2,Bob,100)")

        chkOp("update default_score @ {} ( value = 555 );")
        chkData("default_score(1,555)", "person(2,Bob,100)")

        chkOp("create person('Alice');")
        chkDataNew("person(3,Alice,555)")
    }

    @Test fun testErr() {
        chkOp("create foo(x = 123);", "ct_err:unknown_name:foo")

        chkOp("create city(foo = 123);", "ct_err:attr_unknown_name:foo")

        chkOp("create city(name = 'New York', name = 'New York');", "ct_err:attr_dup_name:name")
        chkOp("create city(name = 'New York', name = 'New Orlean');", "ct_err:attr_dup_name:name")
    }

    @Test fun testDotAttribute() {
        def("entity person { name: text; year: integer; score: integer = 777; status: text = 'Unknown'; }")
        chkData()

        chkOp("create person(.name = 'Bob', .year = 1980);")
        chkDataNew("person(1,Bob,1980,777,Unknown)")
    }

    @Test fun testNoAttributes() {
        def("entity person {}")
        chkData()

        chkOp("create person();")
        chkData("person(1)")
    }

    @Test fun testWrongAttributes() {
        def("entity person { name; rating: integer; }")
        chkOp("create person(true);", "ct_err:[attr_missing:[person]:name,rating][attr_implic_unknown:0:boolean]")
        chkOp("create person('Bob', true);", "ct_err:[attr_missing:[person]:rating][attr_implic_unknown:1:boolean]")
        chkOp("create person(true, 'Bob');", "ct_err:[attr_missing:[person]:rating][attr_implic_unknown:0:boolean]")
        chkOp("create person('Bob', 123, true);", "ct_err:attr_implic_unknown:2:boolean")
    }

    @Test fun testBugAttrExpr() {
        tst.def("operation o(){ create user(); }")
        tst.def("entity user { name = 'Bob'; }")
        chkOpFull("")
        chkData("user(1,Bob)")
    }

    @Test fun testAsDefaultValueEntity() {
        def("entity tag {}")
        def("entity data { t: tag = create tag(); }")

        chkCompile("", "OK")
        chk("create data()", "ct_err:no_db_update:query")

        chk("data @* {} ( data, _=.t)", "list<(data,tag)>[]")
        chkOp("create data();")
        chk("data @* {} ( data, _=.t)", "list<(data,tag)>[(data[2],tag[1])]")
    }

    @Test fun testAsDefaultValueStruct() {
        def("entity tag { x: integer; }")
        def("struct data { t: tag = create tag(123); }")
        def("struct data2 { t: tag = create tag(struct<tag>(456)); }")

        chkCompile("", "OK")
        chk("data()", "ct_err:no_db_update:query:attr:t")
        chk("data2()", "ct_err:no_db_update:query:attr:t")

        chk("tag @* {}", "list<tag>[]")
        chkOp("print(data());")
        chkOut("data{t=tag[1]}")
        chk("tag @* {}", "list<tag>[tag[1]]")

        chkOp("print(data2());")
        chkOut("data2{t=tag[2]}")
        chk("tag @* {}", "list<tag>[tag[1],tag[2]]")
    }

    @Test fun testAsDefaultValueFunction() {
        def("entity tag {}")
        def("function f(t: tag = create tag()) { return t; }")
        def("function g(t: tag = f()) { return t; }")

        chkCompile("", "OK")
        chk("f()", "ct_err:no_db_update:query:param:t")
        chk("f(*)", "ct_err:no_db_update:query:param:t")
        //chk("g()", "ct_err:no_db_update:query:param:t")
        //chk("g(*)", "ct_err:no_db_update:query:param:t")

        chk("tag @* {}", "list<tag>[]")
        chkOp("print(f());")
        chkOut("tag[1]")
        chk("tag @* {}", "list<tag>[tag[1]]")

        chkOp("print(g());")
        chkOut("tag[2]")
        chk("tag @* {}", "list<tag>[tag[1],tag[2]]")
    }

    @Test fun testAsDefaultValueQuery() {
        def("entity tag {}")
        chkCompile("query f(t: tag = create tag()) { return t; }", "ct_err:no_db_update:query")
    }

    @Test fun testAsDefaultValueMirrorEntity() {
        def("entity tag {}")
        def("entity data { t: tag = create tag(); }")

        chkCompile("", "OK")
        chk("struct<data>()", "ct_err:no_db_update:query:attr:t")

        chk("tag @* {}", "list<tag>[]")
        chkOp("struct<data>();")
        chk("tag @* {}", "list<tag>[tag[1]]")
    }

    @Test fun testAsDefaultValueMirrorObject() {
        def("entity tag {}")
        def("object data { t: tag = create tag(); }")

        chkCompile("", "OK")
        chk("struct<data>()", "ct_err:no_db_update:query:attr:t")

        chk("tag @* {}", "list<tag>[tag[1]]")
        chkOp("struct<data>();")
        chk("tag @* {}", "list<tag>[tag[1],tag[2]]")
    }

    @Test fun testAsDefaultValueMirrorOperation() {
        tst.testLib = true
        def("entity tag {}")
        def("operation f(t: tag = create tag()) {}")

        chkCompile("", "OK")
        chk("f()", "ct_err:no_db_update:query:param:t")

        chk("tag @* {}", "list<tag>[]")
        chkOp("print(f());")
        chkOut("f(1)")
        chk("tag @* {}", "list<tag>[tag[1]]")
    }

    @Test fun testAttributeDefaultValueTypePromotion() {
        def("entity data { x: decimal = 123; }")

        chk("data @* {} ( _=.x )", "list<decimal>[]")

        chkOp("create data();")
        chk("data @* {} ( _=.x )", "list<decimal>[dec[123]]")

        chkOp("create data(456);", "ct_err:attr_implic_unknown:0:integer")
        chkOp("create data(x = 456);")
        chk("data @* {} ( _=.x )", "list<decimal>[dec[123],dec[456]]")

        chkOp("create data(789.0);")
        chk("data @* {} ( _=.x )", "list<decimal>[dec[123],dec[456],dec[789]]")

        chkOp("create data(x = 987.0);")
        chk("data @* {} ( _=.x )", "list<decimal>[dec[123],dec[456],dec[789],dec[987]]")
    }

    @Test fun testListOfStruct() {
        chkListOfStruct("struct<data>")
    }

    @Test fun testListOfStructMutable() {
        chkListOfStruct("struct<mutable data>")
    }

    private fun chkListOfStruct(type: String) {
        def("entity data { x: integer; }")

        chkFn("= _type_of(create data(list<$type>()));", "text[list<data>]")
        chkFn("= _type_of(create data([$type(10)]));", "text[list<data>]")
        chkFn("= _type_of(create data([$type(10), $type(20), $type(30)]));", "text[list<data>]")

        chkDataNew()

        chkOp("val c = create data(list<$type>()); print(_strict_str(c));", "OK")
        chkOut("list<data>[]")
        chkDataNew()

        chkOp("val c = create data([$type(10)]); print(_strict_str(c));", "OK")
        chkOut("list<data>[data[1]]")
        chkDataNew("data(1,10)")

        chkOp("val c = create data([$type(20), $type(30)]); print(_strict_str(c));", "OK")
        chkOut("list<data>[data[2],data[3]]")
        chkDataNew("data(2,20)", "data(3,30)")

        chkOp("val c = create data( range(40,90,10) @* {} ($type($)) ); print(_strict_str(c));", "OK")
        chkOut("list<data>[data[4],data[5],data[6],data[7],data[8]]")
        chkDataNew("data(4,40)", "data(5,50)", "data(6,60)", "data(7,70)", "data(8,80)")
    }

    @Test fun testListOfStructOther() {
        def("entity data { x: integer = 123; }")
        chkOp("create data(set<struct<data>>());", "ct_err:attr_implic_unknown:0:set<struct<data>>")
    }

    @Test fun testListOfStructSqlParamsLimit() {
        def("entity data { x: integer; }")

        chkSqlCtr(0)
        chkOp("val c = create data( range(2500) @* {} (struct<data>($)) ); print(c.size());", "OK")
        chkSqlCtr(4)

        val expSum = 3123750
        chk("2499*2500/2", "int[$expSum]")
        chk("data @{} ( @sum 1, @sum .x )", "(int[2500],int[$expSum])")
    }

    @Test fun testListOfStructFastRowid() {
        tst.sqlInsertFastRowidCountThreshold = 3
        def("entity data { x: integer; }")

        chkSql()
        chkOp("create data(range(1) @* {} (struct<data>(100+$)) );")
        chkSqlRegex(*SLOW_ROWID_REGEX)

        chkOp("create data(range(2) @* {} (struct<data>(100+$)) );")
        chkSqlRegex(*SLOW_ROWID_REGEX)

        chkOp("create data(range(3) @* {} (struct<data>(100+$)) );")
        chkSqlRegex(*FAST_ROWID_REGEX)

        chkOp("create data(range(4) @* {} (struct<data>(100+$)) );")
        chkSqlRegex(*FAST_ROWID_REGEX)
    }

    @Test fun testListOfStructFastRowidDefaultThreshold() {
        def("entity data { x: integer; }")

        chkSql()
        chkOp("create data(range(9) @* {} (struct<data>(100+$)) );")
        chkSqlRegex(*SLOW_ROWID_REGEX)

        chkOp("create data(range(10) @* {} (struct<data>(100+$)) );")
        chkSqlRegex(*FAST_ROWID_REGEX)
    }

    @Test fun testListOfStructVersionControl() {
        def("entity data { x: integer; }")
        val err = "VER:feature:create_list_of_structs"
        chkVerCt("function f() { create data(list<struct<data>>()); }", "0.13.5", err)
        chkVerCt("function f() { create data(list<struct<mutable data>>()); }", "0.13.5", err)
    }

    @Test fun testRollbackRowidSingleInsert() {
        tstCtx.useSql = true
        tst.strictToString = false
        def("entity data { v: integer; }")
        def("function insert_one(v: integer, ok: boolean) { create data(v); require(ok); }")

        chkOp("insert_one(1000, true);")
        chk("data @* {} (_=.rowid, _=.v)", "[(1,1000)]")

        chkOp("insert_one(1001, false);", "req_err:null")
        chk("data @* {} (_=.rowid, _=.v)", "[(1,1000)]")

        chkOp("insert_one(1002, true);")
        chk("data @* {} (_=.rowid, _=.v)", "[(1,1000), (2,1002)]")
    }

    @Test fun testRollbackRowidMultiInsertSlowMode() {
        tst.sqlInsertFastRowidCountThreshold = 100
        chkRollbackRowidMultiInsert(SLOW_ROWID_REGEX)
    }

    @Test fun testRollbackRowidMultiInsertFastMode() {
        tst.sqlInsertFastRowidCountThreshold = 3
        chkRollbackRowidMultiInsert(FAST_ROWID_REGEX)
    }

    private fun chkRollbackRowidMultiInsert(sqlRegex: Array<String>) {
        tstCtx.useSql = true
        tst.strictToString = false

        def("entity data { x: integer; }")
        def("""
            function insert_many(n: integer, x0: integer, ok: boolean) {
                create data(range(n) @*{} (struct<data>(x0 + $)));
                require(ok);
            }
        """)

        resetSqlBuffer()
        chkOp("insert_many(3, 1000, true);")
        chkSqlRegex(*sqlRegex)
        chk("data @* {} (_=.rowid, _=.x)", "[(1,1000), (2,1001), (3,1002)]")

        resetSqlBuffer()
        chkOp("insert_many(10, 2000, false);", "req_err:null")
        chkSqlRegex(*sqlRegex)
        chk("data @* {} (_=.rowid, _=.x)", "[(1,1000), (2,1001), (3,1002)]")

        resetSqlBuffer()
        chkOp("insert_many(3, 3000, true);")
        chkSqlRegex(*sqlRegex)
        chk("data @* {} (_=.rowid, _=.x)", "[(1,1000), (2,1001), (3,1002), (4,3000), (5,3001), (6,3002)]")
    }

    companion object {
        private val SLOW_ROWID_REGEX = arrayOf(
            """\QINSERT INTO "c0.data"("rowid", "x") VALUES ("c0.make_rowid"(), ?)\E.*""",
        )

        private val FAST_ROWID_REGEX = arrayOf(
            """\QSELECT "c0.make_rowids"(?)\E""",
            """\QINSERT INTO "c0.data"("rowid", "x") VALUES (?, ?), (?, ?), (?, ?)\E.*""",
        )
    }
}
