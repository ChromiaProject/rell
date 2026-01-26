/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.def

import net.postchain.rell.base.model.R_Attribute
import net.postchain.rell.base.model.R_EntityDefinition
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.testutils.RellCodeTester
import kotlin.test.Test
import kotlin.test.assertEquals

class EntityTest: BaseRellTest() {
    @Test fun testAttrNoType() {
        chkCompile("entity foo { name; }", "OK")
        chkCompile("entity foo { name123; }", "ct_err:unknown_name:name123")
        chkCompile("entity foo { index name123; }", "ct_err:unknown_name:name123")
        chkCompile("entity foo { key name123; }", "ct_err:unknown_name:name123")
        chkCompile("entity foo { range; }", "ct_err:entity_attr_type:range:range")
        chkCompile("entity foo { range: integer; }", "OK")
    }

    @Test fun testIndex() {
        chkCompile("entity foo { name; index name; }", "OK")
        chkCompile("entity foo { name; index name; index name; }", "ct_err:entity:key_index:dup_attr:INDEX:name")

        chkCompile("entity foo { name1: text; name2: text; index name1, name2; }", "OK")
        chkCompile("entity foo { name1: text; name2: text; index name1; index name2; index name1, name2; }", "OK")
        chkCompile("entity foo { name1: text; name2: text; index name1, name2; index name2, name1; }",
                "ct_err:entity:key_index:dup_attr:INDEX:name1,name2")

        chkCompile("entity foo { name1: text; name2: text; index name1, name1; }", "ct_err:entity_keyindex_dup:name1")

        chkCompile("entity foo { name1: text; index name1, name2: text; }", "OK")
        chkCompile("entity foo { name1: text; index name, name1: text; }", "OK")

        chkCompile("entity foo { mutable name: text; index name; }", "OK")
        chkCompile("entity foo { index name; mutable name: text; }", "OK")
    }

    @Test fun testIndexWithoutAttr() {
        tstCtx.useSql = true
        def("entity foo { index name; }")
        def("entity bar { index name: text; }")
        chkOp("create foo(name = 'A');")
        chkOp("create bar(name = 'B');")
        chk("foo @ {} (.name)", "text[A]")
        chk("bar @ {} (.name)", "text[B]")
    }

    @Test fun testIndexWithAttr() {
        tstCtx.useSql = true
        def("entity A { name; index name; }")
        def("entity B { index name; name: text; }")
        def("entity C { name1: text; index name1, name2: text; }")
        def("entity D { mutable name: text; index name; }")

        chkOp("create A(name = 'A');")
        chkOp("create B(name = 'B');")
        chkOp("create C(name1 = 'C1', name2 = 'C2');")
        chkOp("create D(name = 'D1');")

        chk("A @ {} (.name)", "text[A]")
        chk("B @ {} (.name)", "text[B]")
        chk("C @ {} (_=.name1,_=.name2)", "(text[C1],text[C2])")
        chk("D @ {} (.name)", "text[D1]")

        chkOp("update D @ {} (name = 'D2');")
        chk("D @ {} (.name)", "text[D2]")
    }

    @Test fun testKey() {
        chkCompile("entity foo { name; key name; }", "OK")
        chkCompile("entity foo { name; key name; key name; }", "ct_err:entity:key_index:dup_attr:KEY:name")

        chkCompile("entity foo { name1: text; name2: text; key name1, name2; }", "OK")
        chkCompile("entity foo { name1: text; name2: text; key name1; key name2; key name1, name2; }", "OK")
        chkCompile("entity foo { name1: text; name2: text; key name1, name2; key name2, name1; }",
                "ct_err:entity:key_index:dup_attr:KEY:name1,name2")

        chkCompile("entity foo { name1: text; name2: text; key name1, name1; }", "ct_err:entity_keyindex_dup:name1")

        chkCompile("entity foo { name1: text; key name1, name2: text; }", "OK")
        chkCompile("entity foo { name1: text; key name, name1: text; }", "OK")

        chkCompile("entity foo { mutable name: text; key name; }", "OK")
        chkCompile("entity foo { key name; mutable name: text; }", "OK")
    }

    @Test fun testKeyWithoutAttr() {
        tstCtx.useSql = true
        def("entity foo { key name; }")
        def("entity bar { key name: text; }")
        chkOp("create foo(name = 'A');")
        chkOp("create bar(name = 'B');")
        chk("foo @ {} (.name)", "text[A]")
        chk("bar @ {} (.name)", "text[B]")
    }

    @Test fun testKeyWithAttr() {
        tstCtx.useSql = true
        def("entity A { name; key name; }")
        def("entity B { key name; name: text; }")
        def("entity C { name1: text; key name1, name2: text; }")
        def("entity D { mutable name: text; key name; }")

        chkOp("create A(name = 'A');")
        chkOp("create B(name = 'B');")
        chkOp("create C(name1 = 'C1', name2 = 'C2');")
        chkOp("create D(name = 'D1');")

        chk("A @ {} (.name)", "text[A]")
        chk("B @ {} (.name)", "text[B]")
        chk("C @ {} (_=.name1,_=.name2)", "(text[C1],text[C2])")
        chk("D @ {} (.name)", "text[D1]")

        chkOp("update D @ {} (name = 'D2');")
        chk("D @ {} (.name)", "text[D2]")
    }

    @Test fun testKeyIndexDupValue() {
        tstCtx.useSql = true
        def("entity foo { mutable k: text; mutable i: text; key k; index i; }")

        chkOp("create foo(k = 'K1', i = 'I1');")
        chkOp("create foo(k = 'K1', i = 'I2');", "rt_err:sqlerr:0")
        chkOp("create foo(k = 'K2', i = 'I1');")
        chkData("foo(1,K1,I1)", "foo(2,K2,I1)")

        chkOp("update foo @ { .k == 'K2' } ( k = 'K1' );", "rt_err:sqlerr:0")
        chkData("foo(1,K1,I1)", "foo(2,K2,I1)")
    }

    @Test fun testDeclarationOrder() {
        chkCompile("entity user { c: company; } entity company { name; }", "OK")
        chkCompile("query q() = user @* {}; entity user { name; }", "OK")
    }

    @Test fun testForwardReferenceInAttributeValue() {
        tstCtx.useSql = true
        def("entity foo { x: integer; k: integer = (bar@*{ .v > 0 }).size(); }")
        def("entity bar { v: integer; }")

        chkOp("""
            create foo(x = 1);
            create bar(-1);
            create foo(x = 2);
            create bar(5);
            create foo(x = 3);
            create bar(6);
            create foo(x = 4);
        """)

        chk("foo @ {.x == 1}(.k)", "int[0]")
        chk("foo @ {.x == 2}(.k)", "int[0]")
        chk("foo @ {.x == 3}(.k)", "int[1]")
        chk("foo @ {.x == 4}(.k)", "int[2]")
    }

    @Test fun testAnnotations() {
        chkCompile("@log entity user {}", "OK")
        chkCompile("entity user (foo) {}", "ct_err:entity_ann_bad:foo")
        chkCompile("@foo entity user {}", "ct_err:modifier:invalid:ann:foo")
        chkCompile("@log @log entity user {}", "ct_err:modifier:dup:ann:log")
        chkCompile("entity user (log, log) {}", "ct_err:entity_ann_dup:log")

        val a1 = tst.compileAppEx("entity user {}")
        val c1 = a1.sqlDefs.entities.first { it.simpleName == "user" }
        assertEquals(false, c1.flags.log)

        val a2 = tst.compileAppEx("@log entity user {}")
        val c2 = a2.sqlDefs.entities.first { it.simpleName == "user" }
        assertEquals(true, c2.flags.log)
    }

    @Test fun testBugSqlCreateTableOrder() {
        // Bug: SQL tables must be created in topological order because of foreign key constraints.
        def("entity user { name: text; company; }")
        def("entity company { name: text; }")
        tstCtx.useSql = true
        chkOp("val c = create company('Amazon'); create user ('Bob', c);")
        chkData("user(2,Bob,1)", "company(1,Amazon)")
        chk("company @* {} ( _=.name )", "list<text>[text[Amazon]]")
        chk("user @* {} ( _=.name, _=.company )", "list<(text,company)>[(text[Bob],company[1])]")
    }

    @Test fun testCycle() {
        chkCompile("entity foo { bar; } entity bar { foo; }", "ct_err:entity_cycle:foo,bar")
    }

    @Test fun testTablePrefix() {
        tstCtx.useSql = true
        tst.chkData() // Does database reset, creates system tables

        val tst1 = createTablePrefixTester(123, 100, "Amazon", "Bob")
        val tst2 = createTablePrefixTester(456, 200, "Google", "Alice")

        tst1.chkData("user(101,Bob,100)", "company(100,Amazon)")
        tst1.chk("company @* {}( _=company, _=.name )", "[(company[100],Amazon)]")
        tst1.chk("user @* {}( _=user, _=.name, _=.company.name )", "[(user[101],Bob,Amazon)]")

        tst2.chkData("user(201,Alice,200)", "company(200,Google)")
        tst2.chk("company @* {}( _=company, _=.name )", "[(company[200],Google)]")
        tst2.chk("user @* {}( _=user, _=.name, _=.company.name )", "[(user[201],Alice,Google)]")

        tst1.chkOp("val c = create company('Facebook'); create user ('Trudy', c);")
        tst2.chkOp("val c = create company('Microsoft'); create user ('James', c);")

        tst1.chkData("user(2,Trudy,1)", "user(101,Bob,100)", "company(1,Facebook)", "company(100,Amazon)")
        tst1.chk("company @* {}( _=company, _=.name )", "[(company[1],Facebook), (company[100],Amazon)]")
        tst1.chk("user @* {}( _=user, _=.name, _=.company.name )", "[(user[2],Trudy,Facebook), (user[101],Bob,Amazon)]")

        tst2.chkData("user(2,James,1)", "user(201,Alice,200)", "company(1,Microsoft)", "company(200,Google)")
        tst2.chk("company @* {}( _=company, _=.name )", "[(company[1],Microsoft), (company[200],Google)]")
        tst2.chk("user @* {}( _=user, _=.name, _=.company.name )", "[(user[2],James,Microsoft), (user[201],Alice,Google)]")
    }

    @Test fun testRowidAttr() {
        tstCtx.useSql = false

        chkCompile("entity foo { rowid: integer; }", "ct_err:unallowed_attr_name:rowid")
        chkCompile("entity foo { rowid: text; }", "ct_err:unallowed_attr_name:rowid")
        chkCompile("entity foo { index rowid: integer; }", "ct_err:unallowed_attr_name:rowid")
        chkCompile("entity foo { key rowid: integer; }", "ct_err:unallowed_attr_name:rowid")

        chkCompile("object foo { rowid: integer; }", "ct_err:[object_attr_novalue:foo:rowid][unallowed_attr_name:rowid]")
        chkCompile("object foo { rowid: text; }", "ct_err:[object_attr_novalue:foo:rowid][unallowed_attr_name:rowid]")

        chkCompile("struct foo { rowid: integer; }", "OK")
        chkCompile("struct foo { rowid: text; }", "OK")
    }

    @Test fun testEntityRowidAttr() {
        initEntityRowidAttr()

        chkEx("{ return _type_of((user @ { 'Bob' }).rowid); }", "text[rowid]")
        chkEx("{ val u = user @ { 'Bob' }; return _type_of(u.rowid); }", "text[rowid]")

        chkEx("{ val u = user @ { 'Bob' }; return u.rowid; }", "rowid[100]")
        chkEx("{ val u = user @ { 'Alice' }; return u.rowid; }", "rowid[200]")
        chkEx("{ val c = company @ { 'BobCorp' }; return c.boss.rowid; }", "rowid[100]")
        chkEx("{ val c = company @ { 'AliceCorp' }; return c.boss.rowid; }", "rowid[200]")

        chkEx("{ val u = user @? { 'Bob' }; return u.rowid; }", "ct_err:expr_mem_null:user?:rowid")
        chkEx("{ val u = user @? { 'Bob' }; return u?.rowid; }", "rowid[100]")
        chkEx("{ val u = user @? { 'Alice' }; return u?.rowid; }", "rowid[200]")
        chkEx("{ val u = user @? { 'Trudy' }; return u?.rowid; }", "null")

        chkEx("{ val u = user @ { 'Bob' }; u.rowid = 999; return 0; }", "ct_err:attr_not_mutable:user.rowid")
    }

    @Test fun testAttributeMountSqlMapping() {
        val app = tst.compileAppEx(
            """
                entity user {
                    @mount('fname') first_name: text;
                }
            """.trimIndent()
        )
        val entity = app.sqlDefs.entities.first { it.simpleName == "user" }
        val attr = entity.attributes.values.first { it.name == "first_name" }
        assertEquals("fname", attr.sqlMapping)
    }

    @Test fun testAttributeMountDuplicateSqlMapping() {
        chkCompile(
            "entity user { @mount('col') first_name: text; @mount('col') last_name: text; }",
            "ct_err:entity:attr:dup_sql_mapping:user:col"
        )
        chkCompile(
            "entity user { @mount('name') first_name: text; name: text; }",
            "ct_err:entity:attr:dup_sql_mapping:user:name"
        )
    }

    @Test fun testEntityRowidAttrAt() {
        initEntityRowidAttr()

        chk("user @ { 'Bob' } ( _type_of(.rowid) )", "text[rowid]")
        chk("company @ { 'BobCorp' } ( _type_of(.boss.rowid) )", "text[rowid]")

        chk("user @ { 'Bob' } ( .rowid )", "rowid[100]")
        chk("user @ { 'Alice' } ( .rowid )", "rowid[200]")
        chk("user @ { 'Bob' } ( user.rowid )", "rowid[100]")
        chk("user @ { 'Alice' } ( user.rowid )", "rowid[200]")
        chk("company @ { 'BobCorp' } ( .boss.rowid )", "rowid[100]")
        chk("company @ { 'AliceCorp' } ( .boss.rowid )", "rowid[200]")

        chk("user @ { .rowid == to_rowid(100) } ( .name )", "text[Bob]")
        chk("user @ { .rowid == to_rowid(200) } ( .name )", "text[Alice]")
        chk("company @ { .boss.rowid == to_rowid(100) } ( .name )", "text[BobCorp]")
        chk("company @ { .boss.rowid == to_rowid(200) } ( .name )", "text[AliceCorp]")

        chk("user @ { 100 }", "ct_err:at_where_type:0:integer")
        chkEx("{ val x = 100; return user @ { x }; }", "ct_err:at_where:var_noattrs:0:x:integer")
        chkEx("{ val x = to_rowid(100); return user @ { x }; }", "user[100]")
        chkEx("{ val rowid = to_rowid(100); return user @ { rowid }; }", "user[100]")
        chkEx("{ val rowid = 100; return user @ { rowid }; }", "ct_err:at_where:var_noattrs:0:rowid:integer")
        chkEx("{ val rowid = 'Alice'; return user @ { rowid }; }", "ct_err:at_where:var_noattrs:0:rowid:text")
        chkEx("{ val x = 'Alice'; return user @ { x }; }", "user[200]")
    }

    private fun initEntityRowidAttr() {
        tstCtx.useSql = true
        def("entity user { name; }")
        def("entity company { name; boss: user; }")
        def("function to_rowid(i: integer): rowid = rowid.from_gtv(i.to_gtv());")
        insert("c0.user", "name", "100,'Bob'")
        insert("c0.user", "name", "200,'Alice'")
        insert("c0.company", "name,boss", "300,'BobCorp',100")
        insert("c0.company", "name,boss", "400,'AliceCorp',200")
    }

    @Test fun testObjectRowidAttr() {
        tstCtx.useSql = true
        def("object state { mutable value: text = 'Unknown'; }")
        chk("state.rowid", "ct_err:unknown_member:[state]:rowid")
    }

    @Test fun testNullValueInDatabase() {
        tstCtx.useSql = true
        def("entity data { id: integer; value: integer; flag: boolean; name: text; amount: decimal; }")
        for (col in listOf("value", "flag", "name", "amount")) insert("""ALTER TABLE "c0.data" ALTER COLUMN "$col" DROP NOT NULL;""")
        insert("c0.data", "id,value,flag,name,amount", "1,1,NULL,FALSE,'Bob',123.456")
        insert("c0.data", "id,value,flag,name,amount", "2,2,0,NULL,'Bob',123.456")
        insert("c0.data", "id,value,flag,name,amount", "3,3,0,FALSE,NULL,123.456")
        insert("c0.data", "id,value,flag,name,amount", "4,4,0,FALSE,'Bob',NULL")

        chk("data @ { .id == 1 } ( .value )", "rt_err:sql_null:integer")
        chk("data @ { .id == 2 } ( .flag )", "rt_err:sql_null:boolean")
        chk("data @ { .id == 3 } ( .name )", "rt_err:sql_null:text")
        chk("data @ { .id == 4 } ( .amount )", "rt_err:sql_null:decimal")
    }

    @Test fun testAttrCombinedSyntax() {
        chkCompile("entity user { x: integer; }", "OK")

        chkKeyIndexSyntax("KW x: integer, y: text;", "OK")
        chkKeyIndexSyntax("KW x: integer = 123, y: text = 'abc';", "ct_err:attr:key_index:too_complex:x:KW:expr")
        chkKeyIndexSyntax("KW mutable x: integer, mutable y: text;", "ct_err:attr:key_index:too_complex:x:KW:modifiers")
        chkKeyIndexSyntax("KW mutable x: integer = 123, mutable y: text = 'abc';", "ct_err:attr:key_index:too_complex:x:KW:modifiers")

        chkKeyIndexSyntax("KW x: integer, y: text; KW x, y;", "ct_err:entity:key_index:dup_attr:KW:x,y")
        chkKeyIndexSyntax("KW x: integer, y: text; KW x;", "OK")
        chkKeyIndexSyntax("KW x: integer, y: text; KW y;", "OK")

        chkKeyIndexSyntax("x: integer; KW x;", "OK")
        chkKeyIndexSyntax("x: integer; KW x: integer;", "OK")
        chkKeyIndexSyntax("x: integer; KW x = 123;", "ct_err:entity:attr:expr_not_primary:x")
        chkKeyIndexSyntax("KW x = 123; x: integer;", "ct_err:entity:attr:expr_not_primary:x")
        chkKeyIndexSyntax("x: integer = 123; KW x;", "OK")
        chkKeyIndexSyntax("KW x; x: integer = 123;", "OK")

        chkKeyIndexSyntax("x: integer; KW mutable x;", "ct_err:entity:attr:mutable_not_primary:x")
        chkKeyIndexSyntax("KW mutable x; x: integer;", "ct_err:entity:attr:mutable_not_primary:x")
        chkKeyIndexSyntax("mutable x: integer; KW x;", "OK")
        chkKeyIndexSyntax("KW x; mutable x: integer;", "OK")

        chkKeyIndexSyntax("mutable x: integer; y: text; KW x, y;", "OK")

        chkCompile("entity foo { key index x: integer; }", "ct_err:syntax")
        chkCompile("entity foo { index key x: integer; }", "ct_err:syntax")
        chkKeyIndexSyntax("entity foo { mutable KW x: integer; }", "ct_err:syntax")
    }

    private fun chkKeyIndexSyntax(body: String, exp: String) {
        for (kw in listOf("key", "index")) {
            val code = "entity data { ${body.replace("KW", kw)} }"
            val realExp = exp.replace("KW", kw.uppercase())
            chkCompile(code, realExp)
        }
    }

    @Test fun testEntityDetails() {
        chkEntity("entity data { name; score: integer; }", "name:text; score:integer")
        chkEntity("entity data { name; }", "name:text")
        chkEntity("entity data { name: text; }", "name:text")
        chkEntity("entity data { mutable name; }", "mutable name:text")
        chkEntity("entity data { mutable name: text; }", "mutable name:text")
        chkEntity("entity data { name = 'bob'; }", "name:text=*")
        chkEntity("entity data { name: text = 'bob'; }", "name:text=*")
        chkEntity("entity data { mutable name: text = 'bob'; }", "mutable name:text=*")
    }

    @Test fun testEntityDetailsKeyIndexBasic() {
        chkKeyIndex("entity data { KW name; }", "name:text; KW name")
        chkKeyIndex("entity data { KW name: text; }", "name:text; KW name")
        chkKeyIndex("entity data { KW mutable name; }", "mutable name:text; KW name")
        chkKeyIndex("entity data { KW mutable name: text; }", "mutable name:text; KW name")
        chkKeyIndex("entity data { KW name = 'joe'; }", "name:text=*; KW name")
        chkKeyIndex("entity data { KW name: text = 'joe'; }", "name:text=*; KW name")
        chkKeyIndex("entity data { KW mutable name = 'joe'; }", "mutable name:text=*; KW name")
        chkKeyIndex("entity data { KW mutable name: text = 'joe'; }", "mutable name:text=*; KW name")
    }

    @Test fun testEntityDetailsKeyIndexSeparateType() {
        chkKeyIndex("entity data { name; KW name; }", "name:text; KW name")
        chkKeyIndex("entity data { KW name; name; }", "name:text; KW name")

        chkKeyIndex("entity data { name: text; KW name; }", "name:text; KW name")
        chkKeyIndex("entity data { KW name; name: text; }", "name:text; KW name")
        chkKeyIndex("entity data { name; KW name: text; }", "name:text; KW name")
        chkKeyIndex("entity data { KW name: text; name; }", "name:text; KW name")

        chkKeyIndex("entity data { name: text; KW name: text; }", "name:text; KW name")
        chkKeyIndex("entity data { KW name: text; name: text; }", "name:text; KW name")

        chkKeyIndex("entity data { name: integer; KW name; }", "name:integer; KW name")
        chkKeyIndex("entity data { KW name; name: integer; }", "name:integer; KW name")
        chkKeyIndex("entity data { name: integer; KW name: integer; }", "name:integer; KW name")
        chkKeyIndex("entity data { KW name: integer; name: integer; }", "name:integer; KW name")

        chkKeyIndex("entity data { name; KW name: integer; }", "ct_err:entity:attr:type_diff:[text]:[integer]")
        chkKeyIndex("entity data { KW name: integer; name; }", "ct_err:entity:attr:type_diff:[text]:[integer]")

        chkKeyIndex("entity data { value; KW value; }", "ct_err:unknown_name:value")
        chkKeyIndex("entity data { KW value; value; }", "ct_err:unknown_name:value")
        chkKeyIndex("entity data { value: text; KW value; }", "value:text; KW value")
        chkKeyIndex("entity data { KW value; value: text; }", "value:text; KW value")
        chkKeyIndex("entity data { value: text; KW value: text; }", "value:text; KW value")
        chkKeyIndex("entity data { KW value: text; value: text; }", "value:text; KW value")

        chkKeyIndex("entity data { value; KW value: text; }", "ct_err:unknown_name:value")
        chkKeyIndex("entity data { KW value: text; value; }", "ct_err:unknown_name:value")
    }

    @Test fun testEntityDetailsKeyIndexSeparateMutable() {
        chkKeyIndex("entity data { mutable name; KW name; }", "mutable name:text; KW name")
        chkKeyIndex("entity data { KW name; mutable name; }", "mutable name:text; KW name")
        chkKeyIndex("entity data { name; KW mutable name; }", "ct_err:entity:attr:mutable_not_primary:name")
        chkKeyIndex("entity data { KW mutable name; name; }", "ct_err:entity:attr:mutable_not_primary:name")
        chkKeyIndex("entity data { mutable name; KW mutable name; }", "ct_err:entity:attr:mutable_not_primary:name")
        chkKeyIndex("entity data { KW mutable name; mutable name; }", "ct_err:entity:attr:mutable_not_primary:name")
    }

    @Test fun testEntityDetailsKeyIndexSeparateExpr() {
        chkKeyIndex("entity data { name = 'joe'; KW name; }", "name:text=*; KW name")
        chkKeyIndex("entity data { KW name; name = 'joe'; }", "name:text=*; KW name")
        chkKeyIndex("entity data { name; KW name = 'joe'; }", "ct_err:entity:attr:expr_not_primary:name")
        chkKeyIndex("entity data { KW name = 'joe'; name; }", "ct_err:entity:attr:expr_not_primary:name")
        chkKeyIndex("entity data { name = 'joe'; KW name = 'joe'; }", "ct_err:entity:attr:expr_not_primary:name")
        chkKeyIndex("entity data { KW name = 'joe'; name = 'joe'; }", "ct_err:entity:attr:expr_not_primary:name")
        chkKeyIndex("entity data { name = 'joe'; KW name = 'dow'; }", "ct_err:entity:attr:expr_not_primary:name")
        chkKeyIndex("entity data { KW name = 'dow'; name = 'joe'; }", "ct_err:entity:attr:expr_not_primary:name")
    }

    @Test fun testEntityDetailsKeyIndexMultiAttrs() {
        chkKeyIndex("entity data { name; value: integer; KW name, value; }", "name:text; value:integer; KW name,value")
        chkKeyIndex("entity data { KW name, value; name; value: integer; }", "name:text; value:integer; KW name,value")
        chkKeyIndex("entity data { name; KW name, value; value: integer; }", "name:text; value:integer; KW name,value")

        chkKeyIndex("entity data { name; value: integer; KW name: text, value: integer; }", "name:text; value:integer; KW name,value")
        chkKeyIndex("entity data { KW name: text, value: integer; name; value: integer; }", "name:text; value:integer; KW name,value")

        chkKeyIndex("entity data { name = 'joe'; value: integer = 123; KW name: text, value: integer; }",
                "name:text=*; value:integer=*; KW name,value")
        chkKeyIndex("entity data { KW name: text, value: integer; name = 'joe'; value: integer = 123; }",
                "name:text=*; value:integer=*; KW name,value")

        chkKeyIndex("entity data { mutable name; value: integer; KW name: text, value: integer; }",
                "mutable name:text; value:integer; KW name,value")
        chkKeyIndex("entity data { name; mutable value: integer; KW name: text, value: integer; }",
                "name:text; mutable value:integer; KW name,value")

        chkKeyIndex("entity data { name; value: integer; KW mutable name: text, value: integer; }",
                "ct_err:[attr:key_index:too_complex:name:KW:modifiers][entity:attr:mutable_not_primary:name]")
        chkKeyIndex("entity data { KW mutable name: text, value: integer; name; value: integer; }",
                "ct_err:[attr:key_index:too_complex:name:KW:modifiers][entity:attr:mutable_not_primary:name]")
        chkKeyIndex("entity data { name; value: integer; KW name: text, mutable value: integer; }",
                "ct_err:[attr:key_index:too_complex:value:KW:modifiers][entity:attr:mutable_not_primary:value]")
        chkKeyIndex("entity data { KW name: text, mutable value: integer; name; value: integer; }",
                "ct_err:[attr:key_index:too_complex:value:KW:modifiers][entity:attr:mutable_not_primary:value]")
    }

    @Test fun testEntityDetailsKeyIndexNoPrimary() {
        chkKeyIndex("entity data { KW name, value: integer; }", "name:text; value:integer; KW name,value")
        chkKeyIndex("entity data { KW mutable name, value: integer; }", "ct_err:attr:key_index:too_complex:name:KW:modifiers")
        chkKeyIndex("entity data { KW name, mutable value: integer; }", "ct_err:attr:key_index:too_complex:value:KW:modifiers")
        chkKeyIndex("entity data { KW mutable name, mutable value: integer; }", "ct_err:attr:key_index:too_complex:name:KW:modifiers")
        chkKeyIndex("entity data { KW name = 'joe', value: integer; }", "ct_err:attr:key_index:too_complex:name:KW:expr")
        chkKeyIndex("entity data { KW name, value: integer = 123; }", "ct_err:attr:key_index:too_complex:value:KW:expr")
        chkKeyIndex("entity data { KW name = 'joe', value: integer = 123; }", "ct_err:attr:key_index:too_complex:name:KW:expr")
        chkKeyIndex("entity data { KW mutable name = 'joe', value: integer = 123; }", "ct_err:attr:key_index:too_complex:name:KW:modifiers")
        chkKeyIndex("entity data { KW name = 'joe', mutable value: integer = 123; }", "ct_err:attr:key_index:too_complex:name:KW:expr")
        chkKeyIndex("entity data { KW mutable name = 'joe', mutable value: integer = 123; }",
                "ct_err:attr:key_index:too_complex:name:KW:modifiers")
    }

    @Test fun testEntityDetailsKeyIndexLogTransaction() {
        chkKeyIndex("@log entity data { KW transaction; }", "transaction:transaction=*; KW transaction")

        chkKeyIndex("@log entity data { account: text; book: text; KW account, transaction; KW account, book, transaction; }",
                "transaction:transaction=*; account:text; book:text; KW account,transaction; KW account,book,transaction")

        chkKeyIndex("@log entity data { account: text; book: text; KW account, book, transaction; KW account, transaction; }",
                "transaction:transaction=*; account:text; book:text; KW account,book,transaction; KW account,transaction")
    }

    @Test fun testImplicitTypeConflict() {
        tst.ideDefIdConflictError = false
        val defs = "namespace a { enum foo {} } namespace b { enum foo {} }"
        chkKeyIndex("$defs entity data { a.foo; b.foo; }", "ct_err:dup_attr:foo")
        chkKeyIndex("$defs entity data { a.foo; KW b.foo; }", "ct_err:entity:attr:type_diff:[a.foo]:[b.foo]")
        chkKeyIndex("$defs entity data { KW b.foo; a.foo; }", "ct_err:entity:attr:type_diff:[a.foo]:[b.foo]")
    }

    private fun chkKeyIndex(code: String, exp: String) {
        chkEntityKeyIndex0(code, exp, "key")
        chkEntityKeyIndex0(code, exp, "index")
    }

    private fun chkEntityKeyIndex0(code: String, exp: String, kw: String) {
        val code2 = code.replace("KW", kw)
        val exp2 = exp.replace("KW", if (exp.startsWith("ct_err:")) kw.uppercase() else kw)
        chkEntity(code2, exp2)
    }

    @Test fun testPathQueryCount() {
        tstCtx.useSql = true
        def("entity e1 { v: integer; }")
        def("entity e2 { e1; }")
        def("entity e3 { e2; }")
        def("entity e4 { e3; }")
        def("entity e5 { e4; }")
        insert("c0.e1", "v", "11,123")
        insert("c0.e2", "e1", "12,11")
        insert("c0.e3", "e2", "13,12")
        insert("c0.e4", "e3", "14,13")
        insert("c0.e5", "e4", "15,14")

        chkSqlCtr(0)
        chkEx("{ val e = e5@{}; return e; }", "e5[15]")
        chkSqlCtr(1)
        chkEx("{ val e = e5@{}; return e.e4; }", "e4[14]")
        chkSqlCtr(2)
        chkEx("{ val e = e5@{}; return e.e4.e3; }", "e3[13]")
        chkSqlCtr(2)
        chkEx("{ val e = e5@{}; return e.e4.e3.e2; }", "e2[12]")
        chkSqlCtr(2)
        chkEx("{ val e = e5@{}; return e.e4.e3.e2.e1; }", "e1[11]")
        chkSqlCtr(2)
        chkEx("{ val e = e5@{}; return e.e4.e3.e2.e1.v; }", "int[123]")
        chkSqlCtr(2)
    }

    @Test fun testPathQueryCountNullable() {
        tstCtx.useSql = true
        def("entity e1 { v: integer; }")
        def("entity e2 { e1; }")
        def("entity e3 { e2; }")
        def("entity e4 { e3; }")
        def("entity e5 { e4; }")
        insert("c0.e1", "v", "11,123")
        insert("c0.e2", "e1", "12,11")
        insert("c0.e3", "e2", "13,12")
        insert("c0.e4", "e3", "14,13")
        insert("c0.e5", "e4", "15,14")

        chkSqlCtr(0)
        chkEx("{ val e = e5@?{}; return e; }", "e5[15]")
        chkSqlCtr(1)
        chkEx("{ val e = e5@?{}; return e?.e4; }", "e4[14]")
        chkSqlCtr(2)
        chkEx("{ val e = e5@?{}; return e?.e4?.e3; }", "e3[13]")
        chkSqlCtr(2)
        chkEx("{ val e = e5@?{}; return e?.e4?.e3?.e2; }", "e2[12]")
        chkSqlCtr(2)
        chkEx("{ val e = e5@?{}; return e?.e4?.e3?.e2?.e1; }", "e1[11]")
        chkSqlCtr(2)
        chkEx("{ val e = e5@?{}; return e?.e4?.e3?.e2?.e1?.v; }", "int[123]")
        chkSqlCtr(2)
        chkEx("{ val e = e5@?{false}; return e?.e4?.e3?.e2?.e1?.v; }", "null")
        chkSqlCtr(1)
    }

    @Test fun testAttributeReadSql() {
        tstCtx.useSql = true
        def("entity data { value: integer; }")
        insert("c0.data", "value", "10,123")

        chkSql()
        chkEx("{ val e = data@{}; return e.value; }", "int[123]")
        chkSql(
            """SELECT A00."rowid" FROM "c0.data" A00""",
            """SELECT A00."value" FROM "c0.data" A00 WHERE A00."rowid" = ?""",
        )
    }

    @Test fun testToStructSql() {
        tstCtx.useSql = true
        def("entity data { value: integer; }")
        insert("c0.data", "value", "10,123")

        chkSql()
        chkEx("{ val e = data@{}; return e.to_struct(); }", "struct<data>[value=int[123]]")
        chkSql(
            """SELECT A00."rowid" FROM "c0.data" A00""",
            """SELECT A00."value" FROM "c0.data" A00 WHERE A00."rowid" = ?""",
        )
    }

    @Test fun testBadAttrModifiersSemanticErr() {
        chkCompile("entity x { abstract y: integer; }", "ct_err:modifier:invalid:kw:abstract")
        chkCompile("entity x { override y: integer; }", "ct_err:modifier:invalid:kw:override")
        chkCompile("entity x { mutable abstract y: integer; }", "ct_err:modifier:invalid:kw:abstract")
        chkCompile("entity x { mutable override y: integer; }", "ct_err:modifier:invalid:kw:override")
        chkCompile("entity x { abstract mutable y: integer; }", "ct_err:modifier:invalid:kw:abstract")
        chkCompile("entity x { override mutable y: integer; }", "ct_err:modifier:invalid:kw:override")
        chkCompile("entity x { abstract override y: integer; }",
            "ct_err:[modifier:invalid:kw:abstract][modifier:invalid:kw:override]")
        chkCompile("entity x { abstract y: integer = 10; override z: integer; }",
            "ct_err:[modifier:invalid:kw:abstract][modifier:invalid:kw:override]")
    }

    private fun chkEntity(code: String, exp: String) {
        val act = tst.processApp(code) { app ->
            val e = app.rApp.moduleMap.getValue(R_ModuleName.EMPTY).entities.getValue("data")
            entityToString(e)
        }
        assertEquals(exp, act)
    }

    private fun entityToString(e: R_EntityDefinition): String {
        val attrs = e.attributes.values.map { attrToString(it) }
        val keys = e.keys.map { "key ${it.attribs.joinToString(",")}" }
        val idxs = e.indexes.map { "index ${it.attribs.joinToString(",")}" }
        val parts = attrs + keys + idxs
        return parts.joinToString("; ")
    }

    private fun attrToString(a: R_Attribute): String {
        val mut = if (a.mutable) "mutable " else ""
        val expr = if (a.expr != null) "=*" else ""
        return "$mut${a.name}:${a.type.str()}$expr"
    }

    private fun createTablePrefixTester(chainId: Long, rowid: Long, company: String, user: String): RellCodeTester {
        val t = RellCodeTester(tstCtx)
        t.def("entity user { name: text; company; }")
        t.def("entity company { name: text; }")
        t.chainId = chainId
        t.insert("c${chainId}.company", "name","${rowid},'$company'")
        t.insert("c${chainId}.user", "name,company","${rowid+1},'$user',${rowid}")
        t.dropTables = false
        t.strictToString = false
        return t
    }
}
