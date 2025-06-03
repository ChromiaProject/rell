/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.expr.atexpr

import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.testutils.RellCodeTester
import org.junit.Test

class AtExprExistsTest: BaseRellTest(useSql = true) {
    private fun initDataUserGroupMembership() {
        tst.strictToString = false
        def("entity user { name; }")
        def("entity group { name; }")
        def("entity membership { user; group; }")
        insert("c0.user", "name", "100,'Bob'", "101,'Alice'", "102,'Trudy'", "103,'John'")
        insert("c0.group", "name", "200,'admin'", "201,'developer'", "202,'tester'")
        insert("c0.membership", "user,group", "300,100,200", "301,101,201", "302,102,200", "303,102,201")
    }

    private fun initDataUserCompany() {
        tst.strictToString = false
        def("entity user { name; city: text; mutable score: integer; }")
        def("entity company { name; city: text; }")
        def("entity job { user; company; }")

        insert("c0.user", "name,city,score",
                "100,'Bob','London',123",
                "101,'Alice','Paris',456",
                "102,'Trudy','Berlin',789",
                "103,'John','Berlin',321"
        )

        insert("c0.company", "name,city",
                "200,'Adidas','Paris'",
                "201,'Reebok','London'",
                "202,'Puma','Paris'",
                "203,'Nike','Madrid'"
        )
    }

    private fun resetDataUserCompany() {
        tst.resetRowid()
        chkOp("delete user @* {}; delete company @* {};")
        val t = RellCodeTester(tstCtx)
        t.dropTables = false
        t.insert(tst.inserts)
        t.init()
    }

    private fun initDataUserCompanyJob() {
        initDataUserCompany()
        insert("c0.job", "user,company",
                "301,100,201", // Bob - Reebok
                "302,101,200", // Alice - Adidas
                "303,102,202", // Trudy - Puma
                "304,102,201"  // Trudy - Reebok
        )
    }

    @Test fun testUnrelated() {
        initDataUserCompany()
        chk("user @* { exists(company @* {}) } ( .name )", "[Bob, Alice, Trudy, John]")
        chk("user @* { exists(company @* { company.city == 'London' }) } ( .name )", "[Bob, Alice, Trudy, John]")
        chk("user @* { exists(company @* { company.city == 'Berlin' }) } ( .name )", "[]")
        chk("user @* { exists(company @* { .city == 'London' }) } ( .name )", "[Bob, Alice, Trudy, John]")
    }

    @Test fun testBasicUserCompany() {
        initDataUserCompany()
        chkBasicUserCompany("exists", "not exists")
        chkBasicUserCompany("not empty", "empty")
    }

    private fun chkBasicUserCompany(exists: String, notExists: String) {
        chk("user @* { $exists(company @* { company.city == user.city }) } ( .name )", "[Bob, Alice]")
        chk("(u1: user) @* { $exists((u2: user) @* { u1.city == u2.city }) } ( .name )", "[Bob, Alice, Trudy, John]")
        chk("(u1: user) @* { $exists((u2: user) @* { u1 != u2, u1.city == u2.city }) } ( .name )", "[Trudy, John]")
        chk("(u1: user) @* { $exists((u2: user) @* { u1 < u2, u1.city == u2.city }) } ( .name )", "[Trudy]")

        chk("user @* { $notExists(company @* { company.city == user.city }) } ( .name )", "[Trudy, John]")
        chk("(u1: user) @* { $notExists((u2: user) @* { u1.city == u2.city }) } ( .name )", "[]")
        chk("(u1: user) @* { $notExists((u2: user) @* { u1 != u2, u1.city == u2.city }) } ( .name )", "[Bob, Alice]")
    }

    @Test fun testBasicUserGroupMembership() {
        initDataUserGroupMembership()
        chk("(u: user) @* { exists( (m: membership) @* { m.user == u, m.group.name == 'admin' } ) } ( .name )", "[Bob, Trudy]")
        chk("(u: user) @* { exists( (m: membership) @* { m.user == u, m.group.name == 'developer' } ) } ( .name )", "[Alice, Trudy]")
        chk("(u: user) @* { exists( (m: membership) @* { m.user == u, m.group.name == 'tester' } ) } ( .name )", "[]")
    }

    @Test fun testOuterReferencing() {
        initDataUserGroupMembership()

        chk("user @* { exists( membership @* { .user.name == user.name, .group.name == 'admin' } ) } ( user.name )", "[Bob, Trudy]")

        chk("user @* { exists( membership @* { .user.name == .name, .group.name == 'admin' } ) } ( .name )",
                "ct_err:at_expr:attr:belongs_to_outer:name:user:user")

        chk("user @* { exists( membership @* { .user == user, .group.name == 'admin' } ) } ( .name )", "[Bob, Trudy]")

        chk("(u: user) @* { exists( (m: membership) @* { .user.name == .name, .group.name == 'admin' } ) } ( .name )",
                "ct_err:at_expr:attr:belongs_to_outer:name:u:user")

        chk("(u: user) @* { exists( (m: membership) @* { m.user.name == .name, m.group.name == 'admin' } ) } ( .name )",
                "ct_err:at_expr:attr:belongs_to_outer:name:u:user")

        chk("(u: user) @* { exists( (m: membership) @* { m.user.name == u.name, m.group.name == 'admin' } ) } ( .name )", "[Bob, Trudy]")
        chk("(u: user) @* { exists( (m: membership) @* { m.user == u, m.group.name == 'admin' } ) } ( .name )", "[Bob, Trudy]")

        chk("user @* { exists( group @* { .name == 'admin' } ) } ( .name )", "[Bob, Alice, Trudy, John]")
        chk("user @* { exists( group @* { group.name == 'admin' } ) } ( .name )", "[Bob, Alice, Trudy, John]")
        chk("user @* { exists( group @* { user.name == 'Bob' } ) } ( .name )", "[Bob]")
    }

    @Test fun testExistsInWhatPart() {
        initDataUserCompany()

        chk("user @* {} ( _=.name, exists(company @* { company.city == user.city }) )",
                "[(Bob,true), (Alice,true), (Trudy,false), (John,false)]")
        chk("(u1: user) @* {} ( _=.name, exists((u2: user) @* { u1.city == u2.city }) )",
                "[(Bob,true), (Alice,true), (Trudy,true), (John,true)]")
        chk("(u1: user) @* {} ( _=.name, exists((u2: user) @* { u1 != u2, u1.city == u2.city }) )",
                "[(Bob,false), (Alice,false), (Trudy,true), (John,true)]")
        chk("(u1: user) @* {} ( _=.name, exists((u2: user) @* { u1 < u2, u1.city == u2.city }) )",
                "[(Bob,false), (Alice,false), (Trudy,true), (John,false)]")

        chk("user @* {} ( _=.name, not exists(company @* { company.city == user.city }) )",
                "[(Bob,false), (Alice,false), (Trudy,true), (John,true)]")
        chk("(u1: user) @* {} ( _=.name, not exists((u2: user) @* { u1.city == u2.city }) )",
                "[(Bob,false), (Alice,false), (Trudy,false), (John,false)]")
        chk("(u1: user) @* {} ( _=.name, not exists((u2: user) @* { u1 != u2, u1.city == u2.city }) )",
                "[(Bob,true), (Alice,true), (Trudy,false), (John,false)]")
    }

    @Test fun testOther() {
        initDataUserGroupMembership()
        chk("user @* { exists( membership @* { .user == user, .group.name == 'admin' } ) } ( .name )", "[Bob, Trudy]")
        chk("user @* { exists( membership @* { .user == user, .group.name == 'developer' } ) } ( .name )", "[Alice, Trudy]")
        chk("user @* { exists( membership @* { .user == user, .group.name == 'tester' } ) } ( .name )", "[]")
        chk("user @* { exists( membership @* { .user == $, .group.name == 'tester' } ) } ( .name )",
                "ct_err:binop_operand_type:==:[user]:[membership]")
        chk("(u: user) @* { exists( membership @* { .user == user, .group.name == 'tester' } ) } ( .name )",
                "ct_err:expr_novalue:type:[user]")
        chk("(u: user) @* { exists( membership @* { .user == $, .group.name == 'tester' } ) } ( .name )",
                "ct_err:binop_operand_type:==:[user]:[membership]")
        chk("user @* { exists( (m: membership) @* { .user == user, .group.name == 'admin' } ) } ( .name )", "[Bob, Trudy]")
    }

    @Test fun testAliasConflict() {
        initDataUserGroupMembership()

        chk("(u: user) @* { exists( (u: user) @* {} ) }", "ct_err:block:name_conflict:u")
        chk("(u: user) @* {} ( exists( (u: user) @* {} ) )", "ct_err:block:name_conflict:u")
        chk("(u: user) @* {} limit ((u: user) @* {}).size()", "[user[100], user[101], user[102], user[103]]")

        chk("(u: user) @* { exists( user @* {} ) }", "[user[100], user[101], user[102], user[103]]")
        chk("user @* { exists( (u: user) @* {} ) }", "[user[100], user[101], user[102], user[103]]")
        chk("user @* { exists( user @* {} ) }", "[user[100], user[101], user[102], user[103]]")
        chk("user @* { exists( user @* { user.name == 'Bob' } ) }", "ct_err:name:ambiguous:user")

        chk("(u: user) @* {} ( exists( user @* {} ) )", "[true, true, true, true]")
        chk("(u: user) @* {} ( exists( user @* { user.name == 'Bob' } ) )", "[true, true, true, true]")
        chk("user @* {} ( exists( (u: user) @* {} ) )", "[true, true, true, true]")
        chk("user @* {} ( exists( (u: user) @* { u.name == 'Bob' } ) )", "[true, true, true, true]")
        chk("user @* {} ( exists( user @* {} ) )", "[true, true, true, true]")
        chk("user @* {} ( exists( user @* { user.name == 'Bob' } ) )", "ct_err:name:ambiguous:user")
    }

    @Test fun testAliasConflictNoExists() {
        initDataUserGroupMembership()
        chk("(u: user) @* { (((u: user) @* {}).size() > 0 ) }", "ct_err:block:name_conflict:u")
        chk("(u: user) @* {} ( ((u: user) @* {}).size() )", "ct_err:block:name_conflict:u")
    }

    @Test fun testUseOuterEntityNoExists() {
        initDataUserCompany()
        chk("(u: user) @* {} ( (c: company) @ { c.city == 'London' } (c.name) )", "[Reebok, Reebok, Reebok, Reebok]")
        chk("(u: user) @* {} ( (c: company) @ { c.city == u.city } (c.name) )", "ct_err:at:entity:outer:u")
        chk("(u: user) @* { (c: company) @? { c.city == 'London' } != null } ( u.name )", "[Bob, Alice, Trudy, John]")
        chk("(u: user) @* { (c: company) @? { c.city == u.city } != null } ( u.name )", "ct_err:at:entity:outer:u")
    }

    @Test fun testLimitOffset() {
        initDataUserCompany()
        def("function f(x: integer) = x * x;")

        chk("(u: user) @* {exists( (c: company) @* { c.city == u.city } limit 1 )}", "[user[100], user[101]]")
        chk("(u: user) @* {exists( (c: company) @* { c.city == u.city } offset 1 )}", "[user[101]]")
        chk("(u: user) @* {exists( (c: company) @* { c.city == u.city } limit 1 offset 1 )}", "[user[101]]")
        chk("(u: user) @* {exists( (c: company) @* { c.city == u.city } limit 0 )}", "[]")
        chk("(u: user) @* {exists( (c: company) @* { c.city == u.city } offset 2 )}", "[]")
        chk("(u: user) @* {exists( (c: company) @* { c.city == u.city } limit u.name.size() )}", "ct_err:expr_sqlnotallowed")
        chk("(u: user) @* {exists( (c: company) @* { c.city == u.city } offset u.name.size() )}", "ct_err:expr_sqlnotallowed")

        chk("(u: user) @* {exists( (c: company) @* { c.city == u.city } limit f(0) )}", "[]")
        chk("(u: user) @* {exists( (c: company) @* { c.city == u.city } offset f(2) )}", "[]")
        chk("(u: user) @* {exists( (c: company) @* { c.city == u.city } limit f(1) )}", "[user[100], user[101]]")
        chk("(u: user) @* {exists( (c: company) @* { c.city == u.city } offset f(1) )}", "[user[101]]")
    }

    @Test fun testLimitOffsetComplex() {
        initDataUserCompany()

        chk("(u: user) @* {exists( company @*{} limit ((u2:user)@{u2!=u}(u2.name.size())) )}", "ct_err:at:entity:outer:u")
        chk("(u: user) @* {exists( company @*{} offset ((u2:user)@{u2!=u}(u2.name.size())) )}", "ct_err:at:entity:outer:u")

        chk("(u: user) @* {exists( company @*{} limit ((u2:user)@{u2.name=='Bob'}(u2.name.size())) )}",
                "[user[100], user[101], user[102], user[103]]")

        chk("(u: user) @* {exists( company @*{} offset ((u2:user)@{u2.name=='Alice'}(u2.name.size())) )}", "[]")
    }

    @Test fun testWhatPartInNestedAtExpr() {
        initDataUserCompany()

        chk("(u: user) @* { exists( (c: company) @* { c.city == u.city } ( c.name ) ) }", "[user[100], user[101]]")
        chk("(u: user) @* { exists( (c: company) @* { c.city == u.city } ( @omit c.name ) ) }", "ct_err:at:no_fields")

        chk("(u: user) @* { exists( (c: company) @* { c.city == u.city } ( @sort c.name ) ) }", "[user[100], user[101]]")
        chk("(u: user) @* { exists( (c: company) @* { c.city == u.city } ( @group c.name ) ) }", "[user[100], user[101]]")

        chk("(u: user) @* { exists( (c: company) @* { c.city == u.city } ( @sum 1 ) ) }",
                "[user[100], user[101], user[102], user[103]]")

        chk("(u: user) @* { exists( (c: company) @* { c.city == u.city } ( @group c.name, @sum 1 ) ) }",
                "[user[100], user[101]]")
    }

    @Test fun testWhatPartInNestedExprComplex() {
        initDataUserCompany()
        def("function f(c: company) = c.name.upper_case();")
        chk("(u: user) @* { exists( (c: company) @* { c.city == u.city } ( c.to_struct() ) ) }", "ct_err:expr_sqlnotallowed")
        chk("(u: user) @* { exists( (c: company) @* { c.city == u.city } ( c.to_struct().name ) ) }", "ct_err:expr_sqlnotallowed")
        chk("(u: user) @* { exists( (c: company) @* { c.city == u.city } ( f(c) ) ) }", "ct_err:expr_sqlnotallowed")
    }

    @Test fun testRtExprWithinNestedAt() {
        def("function rev(s: text) { var r = ''; for (i in range(s.size())) r = s[i]+r; return r; }")
        initDataUserCompany()

        chk("rev('hello')", "olleh")
        chk("(u: user) @* { exists( (c: company) @* { c.city + 'foo' == u.city + rev('oof') } ) }", "[user[100], user[101]]")
        chk("(u: user) @* { exists( (c: company) @* { c.city == u.city } ( c.city + rev('oof') ) ) }", "[user[100], user[101]]")
    }

    @Test fun testCardinality() {
        initDataUserCompany()

        chkCardinality("@* { c.city == u.city }", "[Bob, Alice]")
        chkCardinality("@+ { c.city == u.city }", "ct_err:at_expr:nested:cardinality:ONE_MANY")
        chkCardinality("@? { c.city == u.city }", "ct_err:at_expr:nested:cardinality:ZERO_ONE")
        chkCardinality("@  { c.city == u.city }", "ct_err:at_expr:nested:cardinality:ONE")

        chkCardinality("@* { c.city == 'London' }", "[Bob, Alice, Trudy, John]")
        chkCardinality("@* { c.city != 'London' }", "[Bob, Alice, Trudy, John]")
        chkCardinality("@* { c.city == 'Berlin' }", "[]")

        chkCardinality("@+ { c.city == 'London' }", "[Bob, Alice, Trudy, John]")
        chkCardinality("@+ { c.city != 'London' }", "[Bob, Alice, Trudy, John]")
        chkCardinality("@+ { c.city == 'Berlin' }", "rt_err:at:wrong_count:0")

        chkCardinality("@? { c.city == 'London' }", "[Bob, Alice, Trudy, John]")
        chkCardinality("@? { c.city != 'London' }", "rt_err:at:wrong_count:3")
        chkCardinality("@? { c.city == 'Berlin' }", "[]")

        chkCardinality("@  { c.city == 'London' }", "ct_err:expr_call_badargs:[exists]:[company]")
        chkCardinality("@  { c.city != 'London' }", "ct_err:expr_call_badargs:[exists]:[company]")
        chkCardinality("@  { c.city == 'Berlin' }", "ct_err:expr_call_badargs:[exists]:[company]")
    }

    private fun chkCardinality(expr: String, expected: String) {
        chk("(u: user) @* { exists( (c: company) $expr ) } (.name)", expected)
    }

    @Test fun testMixedCollectionAndDbAt() {
        def("function users() = list(set(list(user@*{})));")
        def("function companies() = list(set(list(company@*{})));")
        initDataUserCompany()

        chk("(u:user) @* {exists( (c:companies()) @* {} )} ( .name )", "[Bob, Alice, Trudy, John]")
        chk("(u:user) @* {exists( (c:companies()) @* { c.city == u.city } )} ( .name )", "ct_err:expr_sqlnotallowed")
        chk("(u:users()) @* {exists( (c:company) @* {} )} ( .name )", "[Bob, Alice, Trudy, John]")
        chk("(u:users()) @* {exists( (c:company) @* { c.city == u.city } )} ( .name )", "[Bob, Alice]")
    }

    @Test fun testOuterEntityAliasMatch() {
        tst.strictToString = false
        def("entity user { name; }")
        def("entity data1 { user; }")
        def("entity data2 { foo_user: user; bar_user: user; }")
        def("entity data3 { user; foo_user: user; bar_user: user; }")
        insert("c0.user", "name", "100,'Bob'", "101,'Alice'", "102,'Trudy'")
        insert("c0.data1", "user", "200,100", "201,101")
        insert("c0.data2", "foo_user,bar_user", "300,100,101")
        insert("c0.data3", "user,foo_user,bar_user", "400,100,101,102")

        chk("user @* { exists( data1 @* { user } ) } ( .name )", "[Bob, Alice]")
        chk("(foo_user: user) @* { exists( data1 @* { foo_user } ) } ( .name )", "[Bob, Alice]")
        chk("(bar_user: user) @* { exists( data1 @* { bar_user } ) } ( .name )", "[Bob, Alice]")

        chk("user @* { exists( data2 @* { user } ) } ( .name )",
                "ct_err:at_where:var_manyattrs_type:0:user:user:[data2:data2.foo_user,data2:data2.bar_user]")
        chk("(foo_user: user) @* { exists( data2 @* { foo_user } ) } ( .name )", "[Bob]")
        chk("(bar_user: user) @* { exists( data2 @* { bar_user } ) } ( .name )", "[Alice]")

        chk("user @* { exists( data3 @* { user } ) } ( .name )", "[Bob]")
        chk("(foo_user: user) @* { exists( data3 @* { foo_user } ) } ( .name )", "[Alice]")
        chk("(bar_user: user) @* { exists( data3 @* { bar_user } ) } ( .name )", "[Trudy]")

        chk("data1 @* {exists( user @* { user } )}", "ct_err:at_where:type:0:[boolean]:[user]")
        chk("data1 @* {exists( user @* { .user == user } )}", "ct_err:at_expr:attr:belongs_to_outer:user:data1:data1")
        chk("data1 @* {exists( user @* { data1.user == user } )}", "[data1[200], data1[201]]")
        chk("data1 @* {exists( (foo_user: user) @* { foo_user } )}", "ct_err:at_where:type:0:[boolean]:[user]")
        chk("data1 @* {exists( (foo_user: user) @* { data1.user == foo_user } )}", "[data1[200], data1[201]]")
    }

    @Test fun testPathExpressions() {
        tst.strictToString = false
        def("entity country { name; }")
        def("entity city { name; country; }")
        def("entity user { name; city; }")
        insert("c0.country", "name", "500,'Germany'", "501,'France'")
        insert("c0.city", "name,country", "400,'Berlin',500", "401,'Munich',500", "402,'Paris',501")
        insert("c0.user", "name,city", "100,'Bob',400", "101,'Alice',401", "102,'Trudy',402")

        fun chk0(where: String, exp: String) = chk("(u1: user) @* {exists( (u2: user) @* { $where }) } ( .name )", exp)
        chk0("u1.city.country.name == u2.city.country.name", "[Bob, Alice, Trudy]")
        chk0("u2.city.country.name == u1.city.country.name", "[Bob, Alice, Trudy]")
        chk0("u1 != u2, u1.city.country.name == u2.city.country.name", "[Bob, Alice]")
        chk0("u1 != u2, u2.city.country.name == u1.city.country.name", "[Bob, Alice]")
        chk0("u1.city.country.name != u2.city.country.name", "[Bob, Alice, Trudy]")
    }

    @Test fun testPathExpressions2() {
        tst.strictToString = false
        def("entity country { name; }")
        def("entity city { name; country; }")
        def("entity zone { name; city; }")
        def("entity street { name; zone; }")
        def("entity company { name; city; }")
        def("entity office { name; company; }")
        def("entity user { name; home: street; shop: street; office; }")

        insert("c0.country", "name", "200,'Germany'", "201,'France'")
        insert("c0.city", "name,country", "300,'Berlin',200", "301,'Paris',201")
        insert("c0.zone", "name,city", "400,'Be1',300", "402,'Pa1',301")
        insert("c0.street", "name,zone", "500,'Hof',400", "501,'Platz',400", "502,'Foo',402","503,'Bar',402")
        insert("c0.company", "name,city", "600,'Adidas',300", "601,'Reebok',301")
        insert("c0.office", "name,company", "700,'A1',600", "701,'A2',600", "702,'R1',601", "703,'R2',601")
        insert("c0.user", "name,home,shop,office", "100,'Bob',500,501,700", "101,'Alice',502,503,702", "102,'Trudy',501,503,703")

        fun chkUserOffice(w: String, e: String) = chk("(u: user) @* {exists( (o: office) @* { $w }) } ( .name )", e)
        fun chkUserUser(w: String, e: String) = chk("(u1: user) @* {exists( (u2: user) @* { $w }) } ( .name )", e)

        chkUserUser("u1 != u2, u1.home.zone.city.country == u2.home.zone.city.country", "[Bob, Trudy]")
        chkUserUser("u1 != u2, u2.home.zone.city.country == u1.home.zone.city.country", "[Bob, Trudy]")
        chkUserUser("u1 != u2, u1.home.zone.city.country == u2.shop.zone.city.country", "[Alice, Trudy]")
        chkUserUser("u1 != u2, u2.shop.zone.city.country == u1.home.zone.city.country", "[Alice, Trudy]")
        chkUserUser("u1 != u2, u1.shop.zone.city.country == u2.home.zone.city.country", "[Bob, Trudy]")
        chkUserUser("u1 != u2, u2.home.zone.city.country == u1.shop.zone.city.country", "[Bob, Trudy]")
        chkUserUser("u1 != u2, u1.shop.zone.city.country == u2.shop.zone.city.country", "[Alice, Trudy]")
        chkUserUser("u1 != u2, u2.shop.zone.city.country == u1.shop.zone.city.country", "[Alice, Trudy]")

        chkUserUser("u1 != u2, u1.home.zone.city.country == u2.office.company.city.country", "[Alice, Trudy]")
        chkUserUser("u1 != u2, u1.home.zone.city == u2.office.company.city", "[Alice, Trudy]")
        chkUserUser("u1 != u2, u1.shop.zone.city.country == u2.office.company.city.country", "[Alice, Trudy]")
        chkUserUser("u1 != u2, u1.shop.zone.city == u2.office.company.city", "[Alice, Trudy]")

        chkUserUser("u1 != u2, u1.office.company.city.country == u2.home.zone.city.country", "[Bob, Trudy]")
        chkUserUser("u1 != u2, u1.office.company.city == u2.home.zone.city", "[Bob, Trudy]")
        chkUserUser("u1 != u2, u1.office.company.city.country == u2.shop.zone.city.country", "[Alice, Trudy]")
        chkUserUser("u1 != u2, u1.office.company.city == u2.shop.zone.city", "[Alice, Trudy]")

        chkUserOffice("u.home.zone.city.country == o.company.city.country", "[Bob, Alice, Trudy]")
        chkUserOffice("u.shop.zone.city.country == o.company.city.country", "[Bob, Alice, Trudy]")
        chkUserOffice("u.home.zone.city == o.company.city", "[Bob, Alice, Trudy]")
        chkUserOffice("u.shop.zone.city == o.company.city", "[Bob, Alice, Trudy]")

        chkUserOffice("u.office != o, u.home.zone.city == o.company.city", "[Bob, Alice, Trudy]")
        chkUserOffice("u.office != o, u.shop.zone.city == o.company.city", "[Bob, Alice, Trudy]")
    }

    @Test fun testMultipleEntities() {
        initDataUserCompany()

        fun c(w: String, e: String) = chk("(u1:user,c1:company) @* {exists( (u2:user,c2:company) @* { $w } )} (u1.name+c1.name)", e)
        c("u1<u2, c1<c2", "[BobAdidas, BobReebok, BobPuma, AliceAdidas, AliceReebok, AlicePuma, TrudyAdidas, TrudyReebok, TrudyPuma]")
        c("u1<u2, c1<c2, u1.city == u2.city, c1.city == c2.city", "[TrudyAdidas]")
        c("u1<u2, c1<c2, u1.city == c2.city, c1.city == u2.city", "[BobAdidas]")
        c("u1<u2, c1<c2, u1.city != u2.city, c1.city != c2.city", "[BobAdidas, BobReebok, BobPuma, AliceAdidas, AliceReebok, AlicePuma]")
        c("u1<u2, c1<c2, u1.city != u2.city, c1.city == c2.city", "[BobAdidas, AliceAdidas]")
    }

    @Test fun testMultiNestedExists() {
        initDataUserCompanyJob()

        chk("(u:user) @* {exists( (c:company) @* { c.city==u.city, exists( (j:job)@*{j.company==c,j.user!=u})} )} (.name)",
                "[Bob, Alice]")

        chk("(u:user) @* {exists( (c:company) @* { c.city==u.city, exists( (j:job)@*{j.company!=c,j.user==u})} )} (.name)",
                "[Alice]")

        chk("(u:user) @* {exists( (u2:user) @* { u2!=u, u2.city==u.city, exists( (j:job)@*{j.user==u2} ) } )} (.name)", "[John]")

        chk("(u:user) @* {exists( (j:job) @* { j.user==u, exists( (c:company)@*{j.company!=c,c.city==u.city} ) } )} (.name)",
                "[Alice]")

        chk("(u:user) @* {exists( (j:job) @* { j.user==u, not exists( (c:company)@*{j.company!=c,c.city==u.city} ) } )} (.name)",
                "[Bob, Trudy]")
    }

    @Test fun testUpdateDelete() {
        initDataUserCompany()

        chkUpdateDelete("user @* {exists( company @* {} )}", "Bob,Alice,Trudy,John")
        chkUpdateDelete("user @* {exists( company @* { user.city == company.city } )}", "Bob,Alice")

        chkUpdateDelete("(u:user) @* {exists( (c:company) @* {} )}", "Bob,Alice,Trudy,John")
        chkUpdateDelete("(u:user) @* {exists( (c:company) @* { u.city == c.city } )}", "Bob,Alice")
        chkUpdateDelete("(u:user) @* {not exists( (c:company) @* { u.city == c.city } )}", "Trudy,John")

        chkUpdateDelete("(u1:user) @* {exists( (u2:user) @* { u1.city == u2.city } )}", "Bob,Alice,Trudy,John")
        chkUpdateDelete("(u1:user) @* {exists( (u2:user) @* { u1 != u2, u1.city == u2.city } )}", "Trudy,John")
        chkUpdateDelete("(u1:user) @* {exists( (u2:user) @* { u1 < u2, u1.city == u2.city } )}", "Trudy")
    }

    @Test fun testUpdateDeletePlaceholder() {
        initDataUserCompany()
        chkUpdateDelete("user @* {exists( company @* { $.city == user.city } )}", "Bob,Alice")
        chkUpdateDelete("(u:user) @* {exists( (c:company) @* { $.city == u.city } )}", "ct_err:expr:placeholder:none")
        chkUpdateDelete("(u:user) @* {exists( company @* { $.city == u.city } )}", "Bob,Alice")
        chkUpdateDelete("(u:user) @* {exists( (c:company) @* { c.city == u.city } )}", "Bob,Alice")
        chkUpdateDelete("user @* {exists( (c:company) @* { c.city == $.city } )}", "ct_err:at_expr:placeholder:belongs_to_outer")
    }

    private fun chkUpdateDelete(code: String, exp: String) {
        if (exp.startsWith("ct_err:")) {
            chkOp("update $code ( .score += 1 );", exp)
            chkOp("delete $code;", exp)
            return
        }

        val expUsers = exp.split(",").filter { it != "" }.toList()
        resetDataUserCompany()

        chkOp("update user @* {} ( .score = 0 );")
        chkUpdate(listOf())
        chkOp("update $code ( .score += 1 );")
        chkUpdate(expUsers)

        chkDelete(listOf())
        chkOp("delete $code;")
        chkDelete(expUsers)
    }

    private fun chkUpdate(expUsers: List<String>) {
        val users = listOf("Bob", "Alice", "Trudy", "John")
        val exp2 = users.map { it to if (it in expUsers) 1 else 0 }
                .joinToString(", ", prefix = "[", postfix = "]") { "${it.first}:${it.second}" }
        chk("user@*{}( .name+':'+.score )", exp2)
    }

    private fun chkDelete(expUsers: List<String>) {
        val users = listOf("Bob", "Alice", "Trudy", "John")
        val exp2 = users.filter { it !in expUsers }.joinToString(", ", prefix = "[", postfix = "]")
        chk("user @* {} (.name)", exp2)
    }

    @Test fun testUpdateDeleteAliasConflict() {
        initDataUserCompany()

        chkOp("update user @* {exists( user@*{} )} ( .score += 1 );", "OK")
        chkOp("delete user @* {exists( user@*{} )};", "OK")

        chkOp("update user @* {exists( user@*{} )} ( .score += user.score );", "OK")

        chkOp("update user @* {exists( user@*{ user.city != '?' } )} ( .score += 1 );", "ct_err:name:ambiguous:user")
        chkOp("delete user @* {exists( user@*{ user.city != '?' } )};", "ct_err:name:ambiguous:user")

        chkOp("update (u:user) @* {exists( (u:user)@*{} )} ( .score += 1 );", "ct_err:block:name_conflict:u")
        chkOp("delete (u:user) @* {exists( (u:user)@*{} )};", "ct_err:block:name_conflict:u")

        chkOp("update user @* { (user@?{ user.city != '?' })?? } ( .score += 1 );", "ct_err:name:ambiguous:user")
        chkOp("delete user @* { (user@?{ user.city != '?' })?? };", "ct_err:name:ambiguous:user")

        chkOp("update (u:user) @* { ((u:user)@?{})?? } ( .score += 1 );", "ct_err:block:name_conflict:u")
        chkOp("delete (u:user) @* { ((u:user)@?{})?? };", "ct_err:block:name_conflict:u")
    }

    @Test fun testUpdateDeleteOuterReferenceNoExists() {
        initDataUserCompany()
        chkOp("update user @* { (company @? { company.city == user.city }) ?? } ( .score += 1);", "ct_err:at:entity:outer:user")
        chkOp("delete user @* { (company @? { company.city == user.city }) ?? };", "ct_err:at:entity:outer:user")
        chkOp("update (u:user) @* { ((c:company) @? { c.city == u.city }) ?? } ( .score += 1);", "ct_err:at:entity:outer:u")
        chkOp("delete (u:user) @* { ((c:company) @? { c.city == u.city }) ?? };", "ct_err:at:entity:outer:u")
    }
}
