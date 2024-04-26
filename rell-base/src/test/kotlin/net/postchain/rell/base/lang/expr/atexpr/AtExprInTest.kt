/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.expr.atexpr

import net.postchain.rell.base.testutils.BaseRellTest
import org.junit.Test

class AtExprInTest: BaseRellTest() {
    private fun initData() {
        tst.strictToString = false
        def("entity city { name; country: text; }")
        def("entity user { name; city; job: text; }")
        def("entity work { company: text; city; job: text; }")

        insert("c0.city", "name,country",
                "300,'London','UK'",
                "301,'Paris','France'",
                "302,'Berlin','Germany'",
                "303,'Madrid','Spain'"
        )

        insert("c0.user", "name,city,job",
                "100,'Bob',300,'coder'",            // London
                "101,'Alice',301,'lawyer'",         // Paris
                "102,'Trudy',302,'tester'",         // Berlin
                "103,'John',302,'admin'"            // Berlin
        )

        insert("c0.work", "company,city,job",
                "200,'Adidas',301,'coder'",         // Paris
                "201,'Reebok',300,'tester'",        // London
                "202,'Puma',301,'lawyer'",          // Paris
                "203,'Nike',302,'admin'"            // Berlin
        )
    }

    @Test fun testBasic() {
        initData()

        chk("(u:user) @* { u.job in ((w:work)@*{}(w.job)) } (.name)", "[Bob, Alice, Trudy, John]")
        chk("(u:user) @* { u.job in ((w:work)@*{w.city == u.city}(w.job)) } (.name)", "[Alice, John]")
        chk("(u:user) @* { u.job in ((w:work)@*{w.city != u.city}(w.job)) } (.name)", "[Bob, Trudy]")

        chk("(u:user) @* { u.job in (w:work)@*{}(w.job) } (.name)", "[Bob, Alice, Trudy, John]")
        chk("(u:user) @* { u.job in (w:work)@*{w.city == u.city}(w.job) } (.name)", "[Alice, John]")
        chk("(u:user) @* { u.job in (w:work)@*{} }", "ct_err:binop_operand_type:in:[text]:[list<work>]")

        chk("(u:user) @* { u.job not in ((w:work)@*{}(w.job)) } (.name)", "[]")
        chk("(u:user) @* { u.job not in ((w:work)@*{w.city == u.city}(w.job)) } (.name)", "[Bob, Trudy]")
        chk("(u:user) @* { u.job not in ((w:work)@*{w.city != u.city}(w.job)) } (.name)", "[Alice, John]")
    }

    @Test fun testAttributeResolution() {
        initData()
        chk("(u:user) @* { u.job in ((w:work)@*{}(.job)) } (.name)", "[Bob, Alice, Trudy, John]")
        chk("(u:user) @* { u.job in ((w:work)@*{}(.name)) } (.name)", "ct_err:at_expr:attr:belongs_to_outer:name:u:user")
        chk("(u:user) @* { u.job in ((w:work)@*{}(w.job)) } (.name)", "[Bob, Alice, Trudy, John]")
        chk("(u:user) @* { u.job in ((w:work)@*{}(u.name)) } (.name)", "[]")
    }

    @Test fun testEntityResolution() {
        initData()
        chk("user @* { .job in (work@*{}(work.job)) } (.name)", "[Bob, Alice, Trudy, John]")
        chk("user @* { .job in ((w:work)@*{}(work.job)) } (.name)", "ct_err:unknown_member:[work]:job")
        chk("user @* { .job in ((w:work)@*{}(w.job)) } (.name)", "[Bob, Alice, Trudy, John]")
        chk("user @* { .job in (work@*{}($.job)) } (.name)", "[Bob, Alice, Trudy, John]")
        chk("user @* { .job in ((w:work)@*{}(work.job)) } (.name)", "ct_err:unknown_member:[work]:job")
        chk("(u:user) @* { .job in (work@*{}($.job)) } (.name)", "[Bob, Alice, Trudy, John]")
        chk("(u:user) @* { .job in ((w:work)@*{}($.job)) } (.name)", "ct_err:expr:placeholder:none")
        chk("(u:user) @* { .job in ((w:work)@*{}(u.job)) } (.name)", "[Bob, Alice, Trudy, John]")
    }

    @Test fun testEntityAliasConflict() {
        initData()
        chk("(u:user) @* { .job in ((u:work)@*{}(.company)) } (.name)", "ct_err:block:name_conflict:u")
        chk("user @* { .job in ((user:work)@*{}(.company)) } (.name)", "[]")
        chk("user @* { .job in ((user:work)@*{}(user.company)) } (.name)", "ct_err:name:ambiguous:user")
        chk("user @* { .job in ((user:work)@*{user.name=='Bob'}(.company)) } (.name)",
                "ct_err:[name:ambiguous:user][unknown_member:[work]:name]")
        chk("user @* { user in user@*{} } (.name)", "[Bob, Alice, Trudy, John]")
        chk("user @* { user in user@*{} (user) } (.name)", "ct_err:name:ambiguous:user")
        chk("user @* { user in user@*{ user.name != '?' } } (.name)", "ct_err:name:ambiguous:user")
    }

    @Test fun testDefaultWhatPart() {
        initData()
        chk("user @* { .city in city@*{} } (.name)", "[Bob, Alice, Trudy, John]")
        chk("user @* { .city in city@*{.country=='UK'} } (.name)", "[Bob]")
        chk("user @* { .city in city@*{.country=='Germany'} } (.name)", "[Trudy, John]")
    }

    @Test fun testCardinality() {
        initData()

        chk("user @* { .job in work @* {work.city==user.city} (work.job) } (.name)", "[Alice, John]")
        chk("user @* { .job in work @+ {work.city==user.city} (work.job) } (.name)", "ct_err:at_expr:nested:cardinality:ONE_MANY")
        chk("user @* { .job in work @? {work.city==user.city} (work.job) } (.name)", "ct_err:at_expr:nested:cardinality:ZERO_ONE")
        chk("user @* { .job in work @  {work.city==user.city} (work.job) } (.name)", "ct_err:at_expr:nested:cardinality:ONE")

        chk("user @* { .job in work @* {} (work.job) } (.name)", "[Bob, Alice, Trudy, John]")
        chk("user @* { .job in work @+ {} (work.job) } (.name)", "[Bob, Alice, Trudy, John]")
        chk("user @* { .job in work @? {} (work.job) } (.name)", "ct_err:binop_operand_type:in:[text]:[text?]")
        chk("user @* { .job in work @  {} (work.job) } (.name)", "ct_err:binop_operand_type:in:[text]:[text]")
    }

    @Test fun testLimitOffset() {
        initData()
        chk("user @* { .city in city @* {} } (.name)", "[Bob, Alice, Trudy, John]")
        chk("user @* { .city in city @* {} limit 0 } (.name)", "[]")
        chk("user @* { .city in city @* {} limit 1 } (.name)", "[Bob]")
        chk("user @* { .city in city @* {} offset 4 } (.name)", "[]")
        chk("user @* { .city in city @* {} offset 2 } (.name)", "[Trudy, John]")
        chk("user @* { .city in city @* {} offset 1 limit 1 } (.name)", "[Alice]")
        chk("user @* { .city in city @* {} ( city, @omit @sort city.name ) limit 1 } (.name)", "[Trudy, John]")
    }

    @Test fun testGroupingAndAggregation() {
        initGroupingAndAggregation()

        chk("nums @* { .n in data @*{} ( .v ) } (.n)", "[3, 5, 7, 11, 17]")
        chk("nums @* { .n in data @*{} ( @group .v ) } (.n)", "[3, 5, 7, 11, 17]")

        chk("nums @* { .n in data @*{} ( @min .v ) } (.n)", "ct_err:expr_nosql:in:[integer]:[list<integer?>]")
        chk("nums @* { .n in data @*{} ( @max .v ) } (.n)", "ct_err:expr_nosql:in:[integer]:[list<integer?>]")
        chk("nums @* { .n in data @*{} ( @sum .v ) } (.n)", "[56]")
        chk("nums @* { .n in data @*{} ( @sum 1 ) } (.n)", "[6]")

        chk("nums @* { .n in data @*{} ( @omit @group 0, @min .v ) } (.n)", "[3]")
        chk("nums @* { .n in data @*{} ( @omit @group 0, @max .v ) } (.n)", "[17]")
        chk("nums @* { .n in data @*{} ( @omit @group 0, @sum .v ) } (.n)", "[56]")
        chk("nums @* { .n in data @*{} ( @omit @group 0, @sum 1 ) } (.n)", "[6]")

        chk("nums @* { .n in data @*{} ( @omit @group .k, @min .v ) } (.n)", "[3, 5, 7]")
        chk("nums @* { .n in data @*{} ( @omit @group .k, @max .v ) } (.n)", "[5, 11, 17]")
        chk("nums @* { .n in data @*{} ( @omit @group .k, @sum .v ) } (.n)", "[5, 14, 37]")
        chk("nums @* { .n in data @*{} ( @omit @group .k, @sum 1 ) } (.n)", "[1, 2, 3]")
    }

    private fun initGroupingAndAggregation() {
        tst.strictToString = false
        def("entity data { k: text; v: integer; }")
        def("entity nums { n: integer; }")

        insert("c0.data", "k,v",
                "100,'a',3", "101,'a',11",
                "110,'b',5",
                "120,'c',7", "121,'c',13", "122,'c',17"
        )

        val nums = listOf(1, 2, 3, 5, 6, 7, 11, 14, 17, 37, 56, 33, 555, 777)
        insert("c0.nums", "n", *nums.mapIndexed { i, n -> "${200+i},$n" }.toTypedArray())
    }

    @Test fun testInExprInWhatPart() {
        initData()
        chk("(u:user) @* {} ( .job in ((w:work)@*{}(w.job)) )", "[true, true, true, true]")
        chk("(u:user) @* {} ( .job in ((w:work)@*{w.city == u.city}(w.job)) )", "[false, true, false, true]")
        chk("(u:user) @* {} ( .job in ((w:work)@*{w.city != u.city}(w.job)) )", "[true, false, true, false]")
        chk("(u:user) @* {} ( .job in (w:work)@*{}(w.job) )", "[true, true, true, true]")
        chk("(u:user) @* {} ( .job in (w:work)@*{w.city == u.city}(w.job) )", "[false, true, false, true]")
    }

    @Test fun testComplexWhat() {
        initData()
        def("function f(w: work) = w.job.upper_case();")
        def("function g(w: work) = w.job.size();")
        chk("user @* { .job in work @*{}( work.to_struct() ) }", "ct_err:binop_operand_type:in:[text]:[list<struct<work>>]")
        chk("user @* { .job in work @*{}( work.to_struct().job ) }", "ct_err:expr_sqlnotallowed")
        chk("user @* { .job in work @*{}( f(work) ) }", "ct_err:expr_sqlnotallowed")
        chk("user @* { .job in work @*{}( g(work) ) }", "ct_err:binop_operand_type:in:[text]:[list<integer>]")
    }

    @Test fun testMixedCollectionAndDbAt() {
        def("function users() = list(set(list(user@*{})));")
        def("function works() = list(set(list(work@*{})));")
        initData()

        chk("(u:user) @* { u.job in ((w:work)@*{}(w.job)) } (.name)", "[Bob, Alice, Trudy, John]")
        chk("(u:user) @* { u.job in ((w:work)@*{w.city==u.city}(w.job)) } (.name)", "[Alice, John]")

        chk("(u:users()) @* { u.job in ((w:work)@*{}(w.job)) } (.name)", "[Bob, Alice, Trudy, John]")
        chk("(u:users()) @* { u.job in ((w:work)@*{w.city==u.city}(w.job)) } (.name)", "[Alice, John]")

        chk("(u:user) @* { u.job in ((w:works())@*{}(w.job)) } (.name)", "[Bob, Alice, Trudy, John]")
        chk("(u:user) @* { u.job in ((w:works())@*{w.city==u.city}(w.job)) } (.name)", "ct_err:expr_sqlnotallowed")

        chk("(u:users()) @* { u.job in ((w:works())@*{}(w.job)) } (.name)", "[Bob, Alice, Trudy, John]")
        chk("(u:users()) @* { u.job in ((w:works())@*{w.city==u.city}(w.job)) } (.name)", "[Alice, John]")
    }

    @Test fun testSpecNotUnderAt() {
        initData()

        chkSql(1, "'Bob' in user @* {} (.name)", "true")
        chkSql(1, "'Alice' in user @* {} (.name)", "true")
        chkSql(1, "'Mary' in user @* {} (.name)", "false")

        chkSql(1, "'Bob' in user @+ {} (.name)", "true")
        chkSql(1, "'Alice' in user @+ {} (.name)", "true")
        chkSql(1, "'Mary' in user @+ {} (.name)", "false")
        chkSql(1, "'Bob' in user @+ {.job=='coder'} (.name)", "true")
        chkSql(1, "'Bob' in user @+ {.job=='tester'} (.name)", "false")
        chkSql(1, "'Bob' in user @+ {.job=='builder'} (.name)", "rt_err:at:wrong_count:0")

        chkSql(2, "city@{.name=='Paris'} in user @* {} (.city)", "true")
        chkSql(2, "city@{.name=='Paris'} in user @* {.job=='builder'} (.city)", "false")
        chkSql(2, "city@{.name=='Paris'} in user @+ {} (.city)", "true")
        chkSql(2, "city@{.name=='Paris'} in user @+ {.job=='builder'} (.city)", "rt_err:at:wrong_count:0")
        chkSql(0, "city@{.name=='Paris'} in user @ {} (.city)", "ct_err:binop_operand_type:in:[city]:[city]")
        chkSql(0, "city@{.name=='Paris'} in user @? {} (.city)", "ct_err:binop_operand_type:in:[city]:[city?]")
    }

    @Test fun testSpecUnderAtRhsNotAt() {
        initData()
        chkSql(1, "user @* { 'Berlin' in list(['London','Berlin','Paris']) } (.name)", "[Bob, Alice, Trudy, John]")
        chkSql(1, "user @* { 'Madrid' in list(['London','Berlin','Paris']) } (.name)", "[]")
        chkSql(1, "user @* { .city.name in list(['London','Berlin','Paris']) } (.name)", "[Bob, Alice, Trudy, John]")
    }

    @Test fun testSpecUnderAtRhsNotDbAt() {
        def("""
            function cities() = [
                (name='London',country='UK'),
                (name='Paris',country='France'),
                (name='Berlin',country='Germany'),
                (name='Madrid',country='Spain')
            ];
        """)
        initData()

        chkSql(1, "user @* { 'Berlin' in (c:cities()) @* {}(c.name) } (.name)", "[Bob, Alice, Trudy, John]")
        chkSql(1, "user @* { 'Zurich' in (c:cities()) @* {}(c.name) } (.name)", "[]")
        chkSql(1, "user @* { 'Berlin' in (c:cities()) @* {.country=='Italy'}(c.name) } (.name)", "[]")
        chkSql(1, "user @* { .city.name in (c:cities()) @* {}(c.name) } (.name)", "[Bob, Alice, Trudy, John]")

        chkSql(1, "user @* { 'Berlin' in (c:cities()) @+ {}(c.name) } (.name)", "[Bob, Alice, Trudy, John]")
        chkSql(1, "user @* { 'Zurich' in (c:cities()) @+ {}(c.name) } (.name)", "[]")
        chkSql(0, "user @* { 'Berlin' in (c:cities()) @+ {.country=='Italy'}(c.name) } (.name)", "rt_err:at:wrong_count:0")

        chkSql(0, "user @* { 'Berlin' in (c:cities()) @ {} } (.name)",
                "ct_err:binop_operand_type:in:[text]:[(name:text,country:text)]")
        chkSql(0, "user @* { cities()[0] in (c:cities()) @ {} } (.name)",
                "ct_err:binop_operand_type:in:[(name:text,country:text)]:[(name:text,country:text)]")
        chkSql(0, "user @* { 'Berlin' in (c:cities()) @? {} } (.name)",
                "ct_err:binop_operand_type:in:[text]:[(name:text,country:text)?]")
        chkSql(0, "user @* { cities()[0] in (c:cities()) @? {} } (.name)",
                "ct_err:binop_operand_type:in:[(name:text,country:text)]:[(name:text,country:text)?]")
    }

    @Test fun testSpecUnderAtRhsIndependentDbAtLhsNoDb() {
        def("function get_city(name) = city @ { name };")
        initData()

        chkSql(0, "'Paris'", "Paris")
        chkSql(1, "get_city('Paris')", "city[301]")
        chkSql(2, "user @* {} ( _=.name, get_city('Paris') ) limit 2", "[(Bob,city[301]), (Alice,city[301])]")

        chkSql(1, "user @* { 'Paris' in city @* {}(city.name) } (.name)", "[Bob, Alice, Trudy, John]")
        chkSql(1, "user @* { 'Paris' in city @* {.country=='Italy'}(city.name) } (.name)", "[]")
        chkSql(2, "user @* { get_city('Paris') in city @* {} } (.name)", "[Bob, Alice, Trudy, John]")
        chkSql(2, "user @* { get_city('Paris') in city @* {.country=='Italy'} } (.name)", "[]")

        chkSql(2, "user @* { 'Paris' in city @+ {}(city.name) } (.name)", "[Bob, Alice, Trudy, John]")
        chkSql(1, "user @* { 'Paris' in city @+ {.country=='Italy'}(city.name) } (.name)", "rt_err:at:wrong_count:0")
        chkSql(3, "user @* { get_city('Paris') in city @+ {} } (.name)", "[Bob, Alice, Trudy, John]")
        chkSql(2, "user @* { get_city('Paris') in city @+ {.country=='Italy'} } (.name)", "rt_err:at:wrong_count:0")

        chkSql(0, "user @* { get_city('Paris') in city @  {} } (.name)", "ct_err:binop_operand_type:in:[city]:[city]")
        chkSql(0, "user @* { get_city('Paris') in city @? {} } (.name)", "ct_err:binop_operand_type:in:[city]:[city?]")
    }

    @Test fun testSpecUnderAtRhsIndependentDbAtLhsDb() {
        initData()

        chkSql(1, "user @* { .city in city @* {} } (.name)", "[Bob, Alice, Trudy, John]")
        chkSql(1, "user @* { .city in city @* {.country=='UK'} } (.name)", "[Bob]")
        chkSql(1, "user @* { .city in city @* {.country=='Italy'} } (.name)", "[]")

        chkSql(2, "user @* { .city in city @+ {} } (.name)", "[Bob, Alice, Trudy, John]")
        chkSql(2, "user @* { .city in city @+ {.country=='UK'} } (.name)", "[Bob]")
        chkSql(1, "user @* { .city in city @+ {.country=='Italy'} } (.name)", "rt_err:at:wrong_count:0")

        chkSql(0, "user @* { .city in city @ {} } (.name)", "ct_err:binop_operand_type:in:[city]:[city]")
        chkSql(0, "user @* { .city in city @? {} } (.name)", "ct_err:binop_operand_type:in:[city]:[city?]")
    }

    @Test fun testSpecUnderAtRhsDependentDbAtLhsNoDb() {
        def("function get_city(name) = city @ { name };")
        initData()

        chkSql(0, "'Paris'", "Paris")
        chkSql(1, "get_city('Paris')", "city[301]")
        chkSql(2, "user @* {} ( _=.name, get_city('Paris') ) limit 2", "[(Bob,city[301]), (Alice,city[301])]")

        chkSql(2, "user @* { get_city('Paris') in work @* { work.job == user.job } (work.city) } (.name)", "[Bob, Alice]")
        chkSql(2, "user @* { get_city('London') in work @* { work.job == user.job } (work.city) } (.name)", "[Trudy]")

        chkSql(0, "user @* { get_city('Paris') in work @+ { work.job == user.job } (work.city) } (.name)",
                "ct_err:at_expr:nested:cardinality:ONE_MANY")
        chkSql(0, "user @* { get_city('Paris') in work @ { work.job == user.job } (work.city) } (.name)",
                "ct_err:at_expr:nested:cardinality:ONE")
        chkSql(0, "user @* { get_city('Paris') in work @? { work.job == user.job } (work.city) } (.name)",
                "ct_err:at_expr:nested:cardinality:ZERO_ONE")
    }

    @Test fun testSpecUnderAtRhsDependentDbAtLhsDb() {
        initData()

        chkSql(1, "user @* { .city in work @* { work.job == user.job } (work.city) } (.name)", "[Alice, John]")

        chkSql(0, "user @* { .city in work @+ { work.job == user.job } (work.city) } (.name)",
                "ct_err:at_expr:nested:cardinality:ONE_MANY")
        chkSql(0, "user @* { .city in work @ { work.job == user.job } (work.city) } (.name)",
                "ct_err:at_expr:nested:cardinality:ONE")
        chkSql(0, "user @* { .city in work @? { work.job == user.job } (work.city) } (.name)",
                "ct_err:at_expr:nested:cardinality:ZERO_ONE")
    }

    @Test fun testInCollection() {
        def("""
            function get_country_names(codes: list<text>) = codes @*{} (
                ['DE':'Germany','FR':'France','UK':'UK','ES':'Spain','IT':'Italy']
                [${'$'}]
            );
        """)
        def("""
            function get_country_names_map(codes: list<text>) {
                val m = ['DE':'Germany','FR':'France','UK':'UK','ES':'Spain','IT':'Italy'];
                val r = map<text,integer>();
                for (code in codes) r[m[code]] = r.size();
                return r;
            }
        """)
        initData()

        chk("city @* { .country in ['Germany','France'] } ( .name )", "[Paris, Berlin]")
        chk("city @* { .country in set(['Germany','France']) } ( .name )", "[Paris, Berlin]")
        chk("city @* { .country in list<text>() } ( .name )", "[]")
        chk("city @* { .country in set<text>() } ( .name )", "[]")

        chk("city @* { .country in get_country_names(['DE', 'FR']) } ( .name )", "[Paris, Berlin]")
        chk("city @* { .country in get_country_names(['DE']) } ( .name )", "[Berlin]")
        chk("city @* { .country in get_country_names(['FR']) } ( .name )", "[Paris]")
        chk("city @* { .country in get_country_names(['IT']) } ( .name )", "[]")

        chk("city @* { .country in ['Germany':1,'France':2] } ( .name )", "ct_err:expr_nosql:map<text,integer>")
        chk("city @* { .country in ['Germany':1,'France':2].keys() } ( .name )", "[Paris, Berlin]")
        chk("city @* { .country in get_country_names_map(['DE', 'FR']) } ( .name )", "ct_err:expr_nosql:map<text,integer>")
        chk("city @* { .country in get_country_names_map(['DE', 'FR']).keys() } ( .name )", "[Paris, Berlin]")
    }

    @Test fun testInCollectionTypes() {
        def("enum color { red, green, blue }")
        def("entity user { name; }")
        def("""
            entity data {
                b: boolean;
                t: text;
                i: integer;
                d: decimal;
                ba: byte_array;
                r: rowid;
                c: color;
                u: user;
            }
        """)
        insert("c0.user", "name", "100,'Bob'", "101,'Alice'")
        insert("c0.data", "b,t,i,d,ba,r,c,u", "200,TRUE,'Hello',123,45.67,E'\\\\xBEEF',33,1,100")

        chk("data @? { .b in [false] }", "null")
        chk("data @? { .b in [true] }", "data[200]")
        chk("data @? { .i in [321] }", "null")
        chk("data @? { .i in [123] }", "data[200]")
        chk("data @? { .d in [76.54] }", "null")
        chk("data @? { .d in [45.67] }", "data[200]")
        chk("data @? { .ba in [x'dead'] }", "null")
        chk("data @? { .ba in [x'beef'] }", "data[200]")
        chk("data @? { .r in [rowid.from_gtv(gtv.from_json('22'))] }", "null")
        chk("data @? { .r in [rowid.from_gtv(gtv.from_json('33'))] }", "data[200]")
        chk("data @? { .u in [user@{'Alice'}] }", "null")
        chk("data @? { .u in [user@{'Bob'}]   }", "data[200]")
        chk("data @? { .c in [color.red]   }", "null")
        chk("data @? { .c in [color.green] }", "data[200]")

        chk("data @? { .i not in [321] }", "data[200]")
        chk("data @? { .i not in [123] }", "null")
    }

    @Test fun testInCollectionOpposite() {
        initData()

        chk("city @* { 'Germany' in [.country] } ( .name )", "[Berlin]")
        chk("city @* { 'France' in [.country] } ( .name )", "[Paris]")
        chk("city @* { 'Italy' in [.country] } ( .name )", "[]")

        chk("user @* { 'Germany' in [user.city.country] } ( .name )", "[Trudy, John]")
        chk("user @* { 'France' in [user.city.country] } ( .name )", "[Alice]")
        chk("user @* { 'Italy' in [user.city.country] } ( .name )", "[]")

        chk("user @* { 'Germany' in [.job, .city.name, .city.country] } ( .name )", "[Trudy, John]")
        chk("user @* { 'France' in [.job, .city.name, .city.country] } ( .name )", "[Alice]")
        chk("user @* { 'Italy' in [.job, .city.name, .city.country] } ( .name )", "[]")
        chk("user @* { 'Berlin' in [.job, .city.name, .city.country] } ( .name )", "[Trudy, John]")
        chk("user @* { 'Paris' in [.job, .city.name, .city.country] } ( .name )", "[Alice]")
        chk("user @* { 'Rome' in [.job, .city.name, .city.country] } ( .name )", "[]")
        chk("user @* { 'coder' in [.job, .city.name, .city.country] } ( .name )", "[Bob]")
        chk("user @* { 'lawyer' in [.job, .city.name, .city.country] } ( .name )", "[Alice]")
        chk("user @* { 'tester' in [.job, .city.name, .city.country] } ( .name )", "[Trudy]")

        chk("city @* { 'Germany' not in [.country] } ( .name )", "[London, Paris, Madrid]")
        chk("city @* { 'Italy' not in [.country] } ( .name )", "[London, Paris, Berlin, Madrid]")
        chk("user @* { 'Germany' not in [.job, .city.name, .city.country] } ( .name )", "[Bob, Alice]")
        chk("user @* { 'France' not in [.job, .city.name, .city.country] } ( .name )", "[Bob, Trudy, John]")
    }

    @Test fun testInCollectionMaxSize() {
        def("""
            function get_values(n: integer): list<text> {
                val res = ['London', 'Paris', 'coder', 'lawyer'];
                while (res.size() < n) res.add('' + res.size());
                return res;
            }
        """)
        initData()

        chk("user @* { .city.name in get_values(2) } (.name)", "[Bob, Alice]")
        chk("user @* { .city.name in get_values(100) } (.name)", "[Bob, Alice]")
        chk("user @* { .city.name in get_values(1000) } (.name)", "[Bob, Alice]")
        chk("user @* { .city.name in get_values(10000) } (.name)", "[Bob, Alice]")
        chk("user @* { .city.name in get_values(30000) } (.name)", "[Bob, Alice]")
        chk("user @* { .city.name in get_values(32767) } (.name)", "[Bob, Alice]")
        chk("user @* { .city.name in get_values(32768) } (.name)", "rt_err:sql:too_many_params:32768")
        chk("user @* { .city.name in get_values(32769) } (.name)", "rt_err:sql:too_many_params:32769")
        chk("user @* { .city.name in get_values(50000) } (.name)", "rt_err:sql:too_many_params:50000")
        chk("user @* { .city.name in get_values(100000) } (.name)", "rt_err:sql:too_many_params:100000")

        chk("user @* { .city.name in get_values(16384), .job in get_values(16383) } (.name)", "[Bob, Alice]")
        chk("user @* { .city.name in get_values(16384), .job in get_values(16384) } (.name)",
                "rt_err:sql:too_many_params:32768")
    }

    @Test fun testNullable() {
        def("entity data { value: integer; }")
        insert("c0.data", "value", "1,123")

        chk("data @? { .value in list<integer?>() }", "ct_err:expr_nosql:in:[integer]:[list<integer?>]")
        chk("data @? { .value not in list<integer?>() }", "ct_err:expr_nosql:in:[integer]:[list<integer?>]")

        val from = "(d1: data, @outer d2: data)"
        chk("$from @? { d2?.value in list<integer>() }", "ct_err:binop_operand_type:in:[integer?]:[list<integer>]")
        chk("$from @? { d2?.value not in list<integer>() }", "ct_err:binop_operand_type:in:[integer?]:[list<integer>]")
        chk("$from @? { d2?.value in list<integer?>() }", "ct_err:expr_nosql:in:[integer?]:[list<integer?>]")
        chk("$from @? { d2?.value not in list<integer?>() }", "ct_err:expr_nosql:in:[integer?]:[list<integer?>]")
    }

    private fun chkSql(selects: Int, expr: String, expected: String) {
        chkSqlCtr(0)
        chk(expr, expected)
        chkSqlCtr(selects)
    }
}
