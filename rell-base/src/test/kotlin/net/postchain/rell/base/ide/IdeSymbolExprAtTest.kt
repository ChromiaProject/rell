/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.ide

import org.junit.Test

class IdeSymbolExprAtTest: BaseIdeSymbolTest() {
    @Test fun testAtFrom() {
        file("lib.rell", """
            module;
            entity data2 { name; }
            function fdata2() = [4,5,6];
            namespace a { namespace b {
                entity data3 { name; }
                function fdata3() = [7,8,9];
            }}
        """)
        file("module.rell", "import lib; entity data1 { name; } function fdata1() = [1,2,3];")

        val libRef = arrayOf("lib=DEF_IMPORT_MODULE|-|module.rell/import[lib]", "?head=IMPORT|:lib")
        val ab = arrayOf(
            "a=DEF_NAMESPACE|-|lib.rell/namespace[a]", "?head=NAMESPACE|lib:a",
            "b=DEF_NAMESPACE|-|lib.rell/namespace[a.b]", "?head=NAMESPACE|lib:a.b",
        )

        chkSymsExpr("data1 @* {}", "data1=DEF_ENTITY|-|module.rell/entity[data1]", "?head=ENTITY|:data1|data1")
        chkSymsExpr("fdata1() @* {}", "fdata1=DEF_FUNCTION|-|module.rell/function[fdata1]", "?head=FUNCTION|:fdata1")
        chkSymsExpr("lib.data2 @* {}", *libRef,
            "data2=DEF_ENTITY|-|lib.rell/entity[data2]", "?head=ENTITY|lib:data2|data2")
        chkSymsExpr("lib.fdata2() @* {}", *libRef,
            "fdata2=DEF_FUNCTION|-|lib.rell/function[fdata2]", "?head=FUNCTION|lib:fdata2")
        chkSymsExpr("lib.a.b.data3 @* {}", *libRef, *ab,
            "data3=DEF_ENTITY|-|lib.rell/entity[a.b.data3]", "?head=ENTITY|lib:a.b.data3|a.b.data3")
        chkSymsExpr("lib.a.b.fdata3() @* {}", *libRef, *ab,
            "fdata3=DEF_FUNCTION|-|lib.rell/function[a.b.fdata3]", "?head=FUNCTION|lib:a.b.fdata3")
    }

    @Test fun testAtItemDbImplicitSelect() {
        initAtItemDb()
        chkAtItemDbImplicit1("user @* { user.name != '' }")
        chkAtItemDbImplicit1("user @* {} ( user.name )")
        chkAtItemDbImplicit2("(user, company) @* { user.name != '', company.name != '' }")
        chkAtItemDbImplicit2("(user, company) @* {} ( user.name + company.name)")
        chkAtItemDbImplicit3("ns.data @* { data.name != '' }")
        chkAtItemDbImplicit3("ns.data @* {} ( data.name )")
    }

    @Test fun testAtItemDbImplicitUpdate() {
        initAtItemDb()
        chkAtItemDbImplicit1("update user @* { user.name != '' } ( '' )")
        chkAtItemDbImplicit1("update user @* {} ( user.name )")
        chkAtItemDbImplicit2("update (user, company) @* { user.name != '', company.name != '' } ( '' )")
        chkAtItemDbImplicit2("update (user, company) @* {} ( user.name + company.name )")
        chkAtItemDbImplicit3("update ns.data @* { data.name != '' } ( '' )")
        chkAtItemDbImplicit3("update ns.data @* {} ( data.name )")
    }

    @Test fun testAtItemDbImplicitDelete() {
        initAtItemDb()
        chkAtItemDbImplicit1("delete user @* { user.name != '' }")
        chkAtItemDbImplicit2("delete (user, company) @* { user.name != '', company.name != '' }")
        chkAtItemDbImplicit3("delete ns.data @* { data.name != '' }")
    }

    private fun chkAtItemDbImplicit1(stmt: String) {
        chkAtItemDb1(stmt, "user=LOC_AT_ALIAS|-|local[user:0]", "?doc=AT_VAR_DB|user|user: [user]")
    }

    private fun chkAtItemDbImplicit2(stmt: String) {
        chkSymsStmt("$stmt;",
            "user=DEF_ENTITY|-|lib.rell/entity[user]", "?head=ENTITY|:user|user",
            "company=DEF_ENTITY|-|lib.rell/entity[company]", "?head=ENTITY|:company|company",
            "user=LOC_AT_ALIAS|-|local[user:0]", "?doc=AT_VAR_DB|user|user: [user]",
            "company=LOC_AT_ALIAS|-|local[company:0]", "?doc=AT_VAR_DB|company|company: [company]",
        )
    }

    private fun chkAtItemDbImplicit3(stmt: String) {
        chkAtItemDb3(stmt, "data=LOC_AT_ALIAS|-|local[data:0]", "?doc=AT_VAR_DB|data|data: [ns.data]")
    }

    @Test fun testAtItemDbExplicitSelect() {
        initAtItemDb()
        chkAtItemDbExplicit1("(u: user) @* { u.name != '' }")
        chkAtItemDbExplicit1("(u: user) @* {} ( u.name )")
        chkAtItemDbExplicit2("(u: user, c: company) @* { u.name != '', c.name != '' }")
        chkAtItemDbExplicit2("(u: user, c: company) @* {} ( u.name + c.name )")
        chkAtItemDbExplicit3("(u: ns.data) @* { u.name != '' }")
        chkAtItemDbExplicit3("(u: ns.data) @* {} ( u.name )")
    }

    @Test fun testAtItemDbExplicitUpdate() {
        initAtItemDb()
        chkAtItemDbExplicit1("update (u: user) @* { u.name != '' } ( '' )")
        chkAtItemDbExplicit1("update (u: user) @* {} ( u.name )")
        chkAtItemDbExplicit2("update (u: user, c: company) @* { u.name != '', c.name != '' } ( '' )")
        chkAtItemDbExplicit2("update (u: user, c: company) @* {} ( u.name + c.name )")
        chkAtItemDbExplicit3("update (u: ns.data) @* { u.name != '' } ( '' )")
        chkAtItemDbExplicit3("update (u: ns.data) @* {} ( u.name )")
    }

    @Test fun testAtItemDbExplicitDelete() {
        initAtItemDb()
        chkAtItemDbExplicit1("delete (u: user) @* { u.name != '' }")
        chkAtItemDbExplicit2("delete (u: user, c: company) @* { u.name != '', c.name != '' }")
        chkAtItemDbExplicit3("delete (u: ns.data) @* { u.name != '' }")
    }

    private fun chkAtItemDbExplicit1(stmt: String) {
        chkAtItemDb1(stmt,
            "u=LOC_AT_ALIAS|-|local[u:0]", "?doc=AT_VAR_DB|u|u: [user]",
            "u=LOC_AT_ALIAS|-|-", "?doc=AT_VAR_DB|u|u: [user]",
        )
    }

    private fun chkAtItemDbExplicit2(stmt: String) {
        chkSymsStmt("$stmt;",
            "u=LOC_AT_ALIAS|-|-", "?doc=AT_VAR_DB|u|u: [user]",
            "user=DEF_ENTITY|-|lib.rell/entity[user]", "?head=ENTITY|:user|user",
            "c=LOC_AT_ALIAS|-|-", "?doc=AT_VAR_DB|c|c: [company]",
            "company=DEF_ENTITY|-|lib.rell/entity[company]", "?head=ENTITY|:company|company",
            "u=LOC_AT_ALIAS|-|local[u:0]", "?doc=AT_VAR_DB|u|u: [user]",
            "c=LOC_AT_ALIAS|-|local[c:0]", "?doc=AT_VAR_DB|c|c: [company]",
        )
    }

    private fun chkAtItemDbExplicit3(stmt: String) {
        chkAtItemDb3(stmt,
            "u=LOC_AT_ALIAS|-|local[u:0]", "?doc=AT_VAR_DB|u|u: [ns.data]",
            "u=LOC_AT_ALIAS|-|-", "?doc=AT_VAR_DB|u|u: [ns.data]",
        )
    }

    @Test fun testAtItemDbDollarSelect() {
        initAtItemDb()
        chkAtItemDbDollar1("user @* { $.name != '' }")
        chkAtItemDbDollar1("user @* {} ( $.name )")
        chkAtItemDbDollar2("(user, company) @* { $.name != '' }")
        chkAtItemDbDollar2("(user, company) @* {} ( $.name )")
        chkAtItemDbDollar3("ns.data @* { $.name != '' }")
        chkAtItemDbDollar3("ns.data @* {} ( $.name )")
    }

    @Test fun testAtItemDbDollarUpdate() {
        initAtItemDb()
        chkAtItemDbDollar1("update user @* { $.name != '' } ( '' )")
        chkAtItemDbDollar1("update user @* {} ( $.name )")
        chkAtItemDbDollar2("update (user, company) @* { $.name != '' } ( '' )")
        chkAtItemDbDollar2("update (user, company) @* {} ( $.name )")
        chkAtItemDbDollar3("update ns.data @* { $.name != '' } ( '' )")
        chkAtItemDbDollar3("update ns.data @* {} ( $.name )")
    }

    @Test fun testAtItemDbDollarDelete() {
        initAtItemDb()
        chkAtItemDbDollar1("delete user @* { $.name != '' }")
        chkAtItemDbDollar2("delete (user, company) @* { $.name != '' }")
        chkAtItemDbDollar3("delete ns.data @* { $.name != '' }")
    }

    private fun chkAtItemDbDollar1(stmt: String) {
        chkAtItemDb1(stmt, "$=LOC_AT_ALIAS|-|local[user:0]", "?doc=AT_VAR_DB|$|$: [user]",)
    }

    private fun chkAtItemDbDollar2(stmt: String) {
        chkSymsStmt("$stmt;",
            "user=DEF_ENTITY|-|lib.rell/entity[user]", "?head=ENTITY|:user|user",
            "company=DEF_ENTITY|-|lib.rell/entity[company]", "?head=ENTITY|:company|company",
            "$=LOC_AT_ALIAS|-|local[user:0]", "?doc=AT_VAR_DB|$|$: [user]",
            err = "name:ambiguous:$",
        )
    }

    private fun chkAtItemDbDollar3(stmt: String) {
        chkAtItemDb3(stmt, "$=LOC_AT_ALIAS|-|local[data:0]", "?doc=AT_VAR_DB|$|$: [ns.data]")
    }

    private fun initAtItemDb() {
        file("lib.rell", """
            entity user { mutable name; }
            entity company { name; }
            namespace ns { entity data { mutable name; } }
        """)
    }

    private fun chkAtItemDb1(stmt: String, itemRef: String, itemRefDoc: String, vararg itemDef: String) {
        val userRef = arrayOf("user=DEF_ENTITY|-|lib.rell/entity[user]", "?head=ENTITY|:user|user")
        chkSymsStmt("$stmt;", *itemDef, *userRef, itemRef, itemRefDoc, "name=???*|*|???*")
    }

    private fun chkAtItemDb3(stmt: String, itemRef: String, itemRefDoc: String, vararg itemDef: String) {
        val nsRef = arrayOf("ns=DEF_NAMESPACE|-|lib.rell/namespace[ns]", "?head=NAMESPACE|:ns")
        val dataRef = arrayOf("data=DEF_ENTITY|-|lib.rell/entity[ns.data]", "?head=ENTITY|:ns.data|ns.data")
        chkSymsStmt("$stmt;", *itemDef, *nsRef, *dataRef, itemRef, itemRefDoc, "name=???*|*|???*")
    }

    @Test fun testAtItemCol() {
        file("def.rell", "function data() = [123];")

        val dataRef = "data=DEF_FUNCTION|-|def.rell/function[data]"
        chkSymsExpr("(v: data()) @* { v > 0 }",
            "v=LOC_AT_ALIAS|-|-", "?doc=AT_VAR_COL|v|v: [integer]",
            dataRef,
            "v=LOC_AT_ALIAS|-|local[v:0]", "?doc=AT_VAR_COL|v|v: [integer]",
        )

        chkSymsExpr("(v: data()) @* {} ( v )",
            "v=LOC_AT_ALIAS|-|-", "?doc=AT_VAR_COL|v|v: [integer]",
            dataRef,
            "v=LOC_AT_ALIAS|-|local[v:0]", "?doc=AT_VAR_COL|v|v: [integer]",
        )

        chkSymsExpr("data() @* { $ > 0 }", dataRef, "$=LOC_AT_ALIAS|-|local[data:0]", "?doc=AT_VAR_COL|$|$: [integer]")
        chkSymsExpr("data() @* {} ( $ )", dataRef, "$=LOC_AT_ALIAS|-|local[data:0]", "?doc=AT_VAR_COL|$|$: [integer]")
    }

    @Test fun testAtWhatRegularDb() {
        initAtWhatDb()

        val attrId = "function[__main].tuple[_0].attr"
        val dataAttrLink = "module.rell/entity[data].attr"
        val head = "?head=ENTITY_ATTR|"
        chkSymsExpr("data @ {} ( .n1 )", "n1=MEM_ENTITY_ATTR_NORMAL|$attrId[n1]|$dataAttrLink[n1]", "$head:data.n1")
        chkSymsExpr("data @ {} ( .n2 )", "n2=MEM_ENTITY_ATTR_NORMAL_VAR|$attrId[n2]|$dataAttrLink[n2]", "$head:data.n2")
        chkSymsExpr("data @ {} ( .k1 )", "k1=MEM_ENTITY_ATTR_KEY|$attrId[k1]|$dataAttrLink[k1]", "$head:data.k1")
        chkSymsExpr("data @ {} ( .k2 )", "k2=MEM_ENTITY_ATTR_KEY_VAR|$attrId[k2]|$dataAttrLink[k2]", "$head:data.k2")
        chkSymsExpr("data @ {} ( .i1 )", "i1=MEM_ENTITY_ATTR_INDEX|$attrId[i1]|$dataAttrLink[i1]", "$head:data.i1")
        chkSymsExpr("data @ {} ( .i2 )", "i2=MEM_ENTITY_ATTR_INDEX_VAR|$attrId[i2]|$dataAttrLink[i2]", "$head:data.i2")
        chkSymsExpr("data @ {} ( .ref )", "ref=MEM_ENTITY_ATTR_NORMAL|$attrId[ref]|$dataAttrLink[ref]", "$head:data.ref")
        chkSymsExpr("data @ {} ( .rowid )", "rowid=MEM_ENTITY_ATTR_ROWID|$attrId[rowid]|-",
            "?doc=ENTITY_ATTR|:data.rowid|<key> rowid: [rowid]")
        chkSymsExpr("data @* {} ( .bad )", "bad=UNKNOWN|-|-", "?head=-", err = "expr_attr_unknown:bad")

        val ref = arrayOf("ref=MEM_ENTITY_ATTR_NORMAL|-|$dataAttrLink[ref]", "$head:data.ref")
        val refAttrLink = "module.rell/entity[ref].attr"
        chkSymsExpr("data @ {} ( .ref.n1 )", *ref, "n1=MEM_ENTITY_ATTR_NORMAL|-|$refAttrLink[n1]", "$head:ref.n1")
        chkSymsExpr("data @ {} ( .ref.n2 )", *ref, "n2=MEM_ENTITY_ATTR_NORMAL_VAR|-|$refAttrLink[n2]", "$head:ref.n2")
        chkSymsExpr("data @ {} ( .ref.k1 )", *ref, "k1=MEM_ENTITY_ATTR_KEY|-|$refAttrLink[k1]", "$head:ref.k1")
        chkSymsExpr("data @ {} ( .ref.k2 )", *ref, "k2=MEM_ENTITY_ATTR_KEY_VAR|-|$refAttrLink[k2]", "$head:ref.k2")
        chkSymsExpr("data @ {} ( .ref.i1 )", *ref, "i1=MEM_ENTITY_ATTR_INDEX|-|$refAttrLink[i1]", "$head:ref.i1")
        chkSymsExpr("data @ {} ( .ref.i2 )", *ref, "i2=MEM_ENTITY_ATTR_INDEX_VAR|-|$refAttrLink[i2]", "$head:ref.i2")
        chkSymsExpr("data @ {} ( .ref.rowid )", *ref, "rowid=MEM_ENTITY_ATTR_ROWID|-|-",
            "?doc=ENTITY_ATTR|:ref.rowid|<key> rowid: [rowid]")
        chkSymsExpr("data @* {} ( .ref.bad )", *ref, "bad=UNKNOWN|-|-", "?head=-", err = "unknown_member:[ref]:bad")

        chkSymsExpr("(a:data,b:data) @* {} ( .n1 )",
            "a=LOC_AT_ALIAS|-|-",
            "b=LOC_AT_ALIAS|-|-",
            "n1=MEM_ENTITY_ATTR_NORMAL|$attrId[n1]|module.rell/entity[data].attr[n1]", "$head:data.n1",
            err = "at_attr_name_ambig:n1:[a:data:n1,b:data:n1]",
        )
    }

    @Test fun testAtWhatRegularCol() {
        initAtWhatCol()

        val head = "?head=STRUCT_ATTR|"
        val ref = arrayOf("ref=MEM_STRUCT_ATTR|-|module.rell/struct[data].attr[ref]", "$head:data.ref")
        val dataLink = "module.rell/struct[data]"
        val refLink = "module.rell/struct[ref]"
        val attrId = "function[__main].tuple[_0].attr"

        chkSymsExpr("datas() @ {} ( .i )", "i=MEM_STRUCT_ATTR|$attrId[i]|$dataLink.attr[i]", "$head:data.i")
        chkSymsExpr("datas() @ {} ( .t )", "t=MEM_STRUCT_ATTR_VAR|$attrId[t]|$dataLink.attr[t]", "$head:data.t")
        chkSymsExpr("datas() @ {} ( .ref )", "ref=MEM_STRUCT_ATTR|$attrId[ref]|$dataLink.attr[ref]", "$head:data.ref")
        chkSymsExpr("datas() @ {} ( .ref.p )", *ref, "p=MEM_STRUCT_ATTR|-|$refLink.attr[p]", "$head:ref.p")
        chkSymsExpr("datas() @ {} ( .ref.q )", *ref, "q=MEM_STRUCT_ATTR_VAR|-|$refLink.attr[q]", "$head:ref.q")
        chkSymsExpr("datas() @* {} ( .bad )", "bad=UNKNOWN|-|-", "?head=-", err = "expr_attr_unknown:bad")
        chkSymsExpr("datas() @* {} ( .ref.bad )", *ref, "bad=UNKNOWN|-|-", "?head=-", err = "unknown_member:[ref]:bad")
    }

    @Test fun testAtWhatShortDb() {
        initAtWhatDb()

        val dataAttrLink = "module.rell/entity[data].attr"
        val head = "?head=ENTITY_ATTR|"
        chkSymsExpr("data @ {}.n1", "n1=MEM_ENTITY_ATTR_NORMAL|-|$dataAttrLink[n1]", "$head:data.n1")
        chkSymsExpr("data @ {}.n2", "n2=MEM_ENTITY_ATTR_NORMAL_VAR|-|$dataAttrLink[n2]", "$head:data.n2")
        chkSymsExpr("data @ {}.k1", "k1=MEM_ENTITY_ATTR_KEY|-|$dataAttrLink[k1]", "$head:data.k1")
        chkSymsExpr("data @ {}.k2", "k2=MEM_ENTITY_ATTR_KEY_VAR|-|$dataAttrLink[k2]", "$head:data.k2")
        chkSymsExpr("data @ {}.i1", "i1=MEM_ENTITY_ATTR_INDEX|-|$dataAttrLink[i1]", "$head:data.i1")
        chkSymsExpr("data @ {}.i2", "i2=MEM_ENTITY_ATTR_INDEX_VAR|-|$dataAttrLink[i2]", "$head:data.i2")
        chkSymsExpr("data @ {}.ref", "ref=MEM_ENTITY_ATTR_NORMAL|-|$dataAttrLink[ref]", "$head:data.ref")
        chkSymsExpr("data @ {}.rowid", "rowid=MEM_ENTITY_ATTR_ROWID|-|-",
            "?doc=ENTITY_ATTR|:data.rowid|<key> rowid: [rowid]")
        chkSymsExpr("data @ {}.bad", "bad=UNKNOWN|-|-", "?head=-", err = "expr_attr_unknown:bad")

        val ref = arrayOf("ref=MEM_ENTITY_ATTR_NORMAL|-|$dataAttrLink[ref]", "$head:data.ref")
        val refAttrLink = "module.rell/entity[ref].attr"
        chkSymsExpr("data @ {}.ref.n1", *ref, "n1=MEM_ENTITY_ATTR_NORMAL|-|$refAttrLink[n1]", "$head:ref.n1")
        chkSymsExpr("data @ {}.ref.n2", *ref, "n2=MEM_ENTITY_ATTR_NORMAL_VAR|-|$refAttrLink[n2]", "$head:ref.n2")
        chkSymsExpr("data @ {}.ref.k1", *ref, "k1=MEM_ENTITY_ATTR_KEY|-|$refAttrLink[k1]", "$head:ref.k1")
        chkSymsExpr("data @ {}.ref.k2", *ref, "k2=MEM_ENTITY_ATTR_KEY_VAR|-|$refAttrLink[k2]", "$head:ref.k2")
        chkSymsExpr("data @ {}.ref.i1", *ref, "i1=MEM_ENTITY_ATTR_INDEX|-|$refAttrLink[i1]", "$head:ref.i1")
        chkSymsExpr("data @ {}.ref.i2", *ref, "i2=MEM_ENTITY_ATTR_INDEX_VAR|-|$refAttrLink[i2]", "$head:ref.i2")
        chkSymsExpr("data @ {}.ref.rowid", *ref, "rowid=MEM_ENTITY_ATTR_ROWID|-|-",
            "?doc=ENTITY_ATTR|:ref.rowid|<key> rowid: [rowid]")
        chkSymsExpr("data @ {}.ref.bad", *ref, "bad=UNKNOWN|-|-", "?head=-", err = "unknown_member:[ref]:bad")
    }

    @Test fun testAtWhatShortCol() {
        initAtWhatCol()

        val head = "?head=STRUCT_ATTR|"
        val ref = arrayOf("ref=MEM_STRUCT_ATTR|-|module.rell/struct[data].attr[ref]", "$head:data.ref")

        chkSymsExpr("datas() @ {}.i", "i=MEM_STRUCT_ATTR|-|module.rell/struct[data].attr[i]", "$head:data.i")
        chkSymsExpr("datas() @ {}.t", "t=MEM_STRUCT_ATTR_VAR|-|module.rell/struct[data].attr[t]", "$head:data.t")
        chkSymsExpr("datas() @ {}.ref", *ref)
        chkSymsExpr("datas() @ {}.ref.p", *ref, "p=MEM_STRUCT_ATTR|-|module.rell/struct[ref].attr[p]", "$head:ref.p")
        chkSymsExpr("datas() @ {}.ref.q", *ref, "q=MEM_STRUCT_ATTR_VAR|-|module.rell/struct[ref].attr[q]", "$head:ref.q")
        chkSymsExpr("datas() @* {}.bad", "bad=UNKNOWN|-|-", "?head=-", err = "expr_attr_unknown:bad")
        chkSymsExpr("datas() @* {}.ref.bad", *ref, "bad=UNKNOWN|-|-", "?head=-", err = "unknown_member:[ref]:bad")
    }

    @Test fun testAtWhatFieldDb() {
        initAtWhatDb()

        val tupleAttrBase = "MEM_TUPLE_ATTR|function[__main].tuple[_0].attr"
        val attrLink = "module.rell/entity[data].attr"

        chkSymsExpr("data @ {} ( a = $ )",
            "a=$tupleAttrBase[a]|-", "?doc=TUPLE_ATTR|a|a: [data]",
            "$=LOC_AT_ALIAS|-|local[data:0]", "?doc=AT_VAR_DB|$|$: [data]",
        )
        chkSymsExpr("data @ {} ( x = .n1 )",
            "x=$tupleAttrBase[x]|-", "?doc=TUPLE_ATTR|x|x: [integer]",
            "n1=MEM_ENTITY_ATTR_NORMAL|-|$attrLink[n1]", "?head=ENTITY_ATTR|:data.n1",
        )
    }

    @Test fun testAtWhatFieldCol() {
        initAtWhatCol()

        val tupleAttrBase = "MEM_TUPLE_ATTR|function[__main].tuple[_0].attr"

        chkSymsExpr("datas() @ {} ( a = $ )",
            "a=$tupleAttrBase[a]|-", "?doc=TUPLE_ATTR|a|a: [data]",
            "$=LOC_AT_ALIAS|-|local[datas:0]", "?doc=AT_VAR_COL|$|$: [data]",
        )
        chkSymsExpr("datas() @ {} ( x = .i )",
            "x=$tupleAttrBase[x]|-", "?doc=TUPLE_ATTR|x|x: [integer]",
            "i=MEM_STRUCT_ATTR|-|module.rell/struct[data].attr[i]", "?head=STRUCT_ATTR|:data.i",
        )
    }

    private fun initAtWhatDb() {
        file("module.rell", """
            entity data {
                n1: integer = 0; mutable n2: integer = 0;
                key k1: integer = 0; key mutable k2: integer = 0;
                index i1: integer = 0; index mutable i2: integer = 0;
                ref;
            }
            entity ref {
                n1: integer = 0; mutable n2: integer = 0;
                key k1: integer = 0; key mutable k2: integer = 0;
                index i1: integer = 0; index mutable i2: integer = 0;
            }
        """)
    }

    private fun initAtWhatCol() {
        file("module.rell", """
            struct ref { p: integer; mutable q: text; }
            struct data { ref; i: integer; mutable t: text; }
            function datas() = list<data>();
        """)
    }

    @Test fun testAtWhatAnnotations() {
        file("module.rell", "entity data { x: integer; }")

        val xKind = "MEM_ENTITY_ATTR_NORMAL"
        val xHead = "?head=ENTITY_ATTR|:data.x"
        val xDef = arrayOf("x=$xKind|function[__main].tuple[_0].attr[x]|module.rell/entity[data].attr[x]", xHead)
        val xRef = arrayOf("x=$xKind|-|module.rell/entity[data].attr[x]", xHead)

        //TODO support annotation docs
        chkSymsExpr("data @* {} ( .x )", *xDef)
        chkSymsExpr("data @* {} ( @sort .x )", "sort=MOD_ANNOTATION|-|-", "?doc=-", *xDef)
        chkSymsExpr("data @* {} ( @sort_desc .x )", "sort_desc=MOD_ANNOTATION|-|-", "?doc=-", *xDef)
        chkSymsExpr("data @* {} ( @group .x )", "group=MOD_ANNOTATION|-|-", "?doc=-", *xDef)
        chkSymsExpr("data @* {} ( @min .x )", "min=MOD_ANNOTATION|-|-", "?doc=-", *xRef)
        chkSymsExpr("data @* {} ( @max .x )", "max=MOD_ANNOTATION|-|-", "?doc=-", *xRef)
        chkSymsExpr("data @* {} ( @sum .x )", "sum=MOD_ANNOTATION|-|-", "?doc=-", *xRef)
    }
}
