/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.expr.atexpr

import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.testutils.unwrap
import org.junit.Test

class AtExprJoinTest: BaseRellTest() {
    @Test fun testBasic() {
        initData()
        val what = "( _=p.name, _=h.city.name )"
        chk("(p: person, h: home) @* { h.person == p } $what", "[(Bob,London), (Alice,Paris), (Alice,Rome)]")
        chk("(p: person, h: home @* { h.person == p }) @* {} $what", "[(Bob,London), (Alice,Paris), (Alice,Rome)]")
        chk("(p: person, h: home) @* { h.person == p, p.score >= 20 } $what", "[(Alice,Paris), (Alice,Rome)]")
        chk("(p: person, h: home @* { h.person == p }) @* { p.score >= 20 } $what", "[(Alice,Paris), (Alice,Rome)]")
    }

    @Test fun testManyMatches() {
        initData()

        val what = "( _=p.name, _=h.city.name )"
        val over20 = "[(Alice,London), (Alice,Paris), (Alice,Rome), (Trudy,London), (Trudy,Paris), (Trudy,Rome)]"
        chk("(p: person, h: home @* { p.score >= 20 }) @* {} $what", over20)
        chk("(p: person, h: home) @* { p.score >= 20 } $what", over20)

        val paris = "[(Bob,Paris), (Alice,Paris), (Trudy,Paris)]"
        chk("(p: person, h: home @* { h.city.name.size() == 5 }) @* {} $what", paris)
        chk("(p: person, h: home) @* { h.city.name.size() == 5 } $what", paris)

        val all = """
            [(Bob,London), (Bob,Paris), (Bob,Rome),
            (Alice,London), (Alice,Paris), (Alice,Rome),
            (Trudy,London), (Trudy,Paris), (Trudy,Rome)]
        """.unwrap(" ")
        chk("(p: person, h: home) @* {} $what", all)
        chk("(p: person, h: home @* {}) @* {} $what", all)
        chk("(p: person, h: home @* { true }) @* {} $what", all)
        chk("(p: person, h: home @* { false }) @* {} $what", "[]")
    }

    @Test fun testRestrictions() {
        initData()

        chk("(p: person, (home) @* {}) @* {}", "ct_err:expr:at:join:complex_from")
        chk("(p: person, h: (home) @* {}) @* {}", "ct_err:expr:at:join:complex_from")
        chk("(p: person, h: (i: home) @* {}) @* {}", "ct_err:expr:at:join:complex_from")
        chk("(p: person, (a: home, b: home) @* {}) @* {}",
            "ct_err:[expr:at:join:complex_from][expr:at:join:many_items]")
        chk("(p: person, h: (a: home, b: home) @* {}) @* {}",
            "ct_err:[expr:at:join:complex_from][expr:at:join:many_items]")

        chk("(p: person, h: home @ {}) @* {}", "ct_err:expr:at:join:cardinality:ONE")
        chk("(p: person, h: home @? {}) @* {}", "ct_err:expr:at:join:cardinality:ZERO_ONE")
        chk("(p: person, h: home @+ {}) @* {}", "ct_err:expr:at:join:cardinality:ONE_MANY")

        chk("(p: person, h: home @* {}.city) @* {}", "ct_err:expr:at:join:explicit_what")
        chk("(p: person, h: home @* {} (.city)) @* {}", "ct_err:expr:at:join:explicit_what")

        chk("(p: person, h: home @* {} limit 1) @* {}", "ct_err:expr:at:join:extra:limit")
        chk("(p: person, h: home @* {} offset 1) @* {}", "ct_err:expr:at:join:extra:offset")
        chk("(p: person, h: home @* {} limit 1 offset 1) @* {}",
            "ct_err:[expr:at:join:extra:limit][expr:at:join:extra:offset]")

        chk("(p: person, h: [0] @* {}) @* {}", "ct_err:[at:from:mix_entity_iterable][expr:at:join:iterable]")
        chk("(p: person, [0] @* {}) @* {}", "ct_err:[at:from:mix_entity_iterable][expr:at:join:iterable]")
    }

    @Test fun testImplicitAttrs() {
        tst.strictToString = false
        def("entity foo { t1: text; t2: text; i: integer; b1: boolean; }")
        def("entity bar { t1: text; t2: text; j: integer; b2: boolean; }")

        chk("(foo, bar @* {}) @* { .t1 }",
            "ct_err:[at_attr_name_ambig:t1:[foo:foo:t1,bar:bar:t1]][at_where:type:0:[boolean]:[text]]")
        chk("(foo, bar @* {}) @* { .t2 }",
            "ct_err:[at_attr_name_ambig:t2:[foo:foo:t2,bar:bar:t2]][at_where:type:0:[boolean]:[text]]")

        chk("(foo, bar @* {}) @* { .i }", "ct_err:at_where:type:0:[boolean]:[integer]")
        chk("(foo, bar @* {}) @* { .j }", "ct_err:at_where:type:0:[boolean]:[integer]")
        chk("(foo, bar @* {}) @* { .b1 }", "[]")
        chk("(foo, bar @* {}) @* { .b2 }", "[]")
    }

    @Test fun testWhereNullInference() {
        initData()

        chkWhereNullInference("(p: person, h: home @* {h.person == p}) @* { p.score < i!! }", "integer")
        chkWhereNullInference("(p: person, h: home @* {p.score < i!!, h.person == p}) @* {}", "integer")

        //TODO make this case work as other cases - the expression "i!!" is actually always evaluated
        chkWhereNullInference("(p: person, h: home @* {h.person == p, p.score < i!!}) @* {}", "integer?")
    }

    private fun chkWhereNullInference(expr: String, expected: String) {
        chkEx("{ val i = _nullable_int(12345); print($expr); return _type_of(i); }", expected)
    }

    @Test fun testEntityAlias() {
        initData()

        val exp = "[(person[100],home[200]), (person[101],home[201]), (person[101],home[202])]"
        chk("(person, home @* { $.person == person }) @* {} (_=person,_=home)", exp)
        chk("(person, home @* { home.person == person }) @* {} (_=person,_=home)", exp)

        chk("(p: person, home @* { $.person == p }) @* {} (_=p, _=home)", exp)
        chk("(p: person, home @* { home.person == p }) @* {} (_=p, _=home)", exp)
        chk("(p: person, home @* { home.person == person }) @* {} (_=p, _=home)", "ct_err:expr_novalue:type:[person]")

        chk("(person, h: home @* { $.person == person }) @* {} (_=person, _=h)", "ct_err:expr:placeholder:none")
        chk("(person, h: home @* { h.person == person }) @* {} (_=person, _=h)", exp)

        chk("(p: person, h: home @* { $.person == p }) @* {} (_=p, _=h)", "ct_err:expr:placeholder:none")
        chk("(p: person, h: home @* { h.person == p }) @* {} (_=p, _=h)", exp)
        chk("(p: person, h: home @* { home.person == p }) @* {} (_=p, _=h)", "ct_err:unknown_member:[home]:person")
    }

    @Test fun testExists() {
        initData()
        chk("(p: person, h: home @* { h.person == p, exists( (c: company) @* { c.city == h.city } ) }) @* {}",
            "[(p=person[101],h=home[201])]")
        chk("(p: person, h: home @* { h.person == p, exists( (j: job) @* { j.person == p } ) }) @* {}",
            "[(p=person[101],h=home[201]), (p=person[101],h=home[202])]")
        chk("(c: company) @* { exists( (p: person, h: home @* { h.person == p, h.city == c.city }) @* {} ) }",
            "[company[300]]")
        chk("(c: company) @* { exists( (p: person, @outer h: home @* { h.person == p, h.city == c.city }) @* {} ) }",
            "[company[300], company[301]]")
    }

    @Test fun testSql() {
        initData()

        val sqlWhat = """A00."rowid", A01."rowid""""
        val sqlTail = """ORDER BY A00."rowid", A01."rowid""""
        val exp = "[(100,200), (101,201), (101,202)]"

        chk("(p: person, h: home @* { h.person == p }) @* {} (_=p.rowid, _=h.rowid)", exp)
        chkSql("""SELECT $sqlWhat FROM "c0.person" A00 JOIN "c0.home" A01 ON A01."person" = A00."rowid" $sqlTail""")

        val expAll = "[(100,200), (100,201), (100,202), (101,200), (101,201), (101,202), (102,200), (102,201), (102,202)]"
        chk("(p: person, h: home @* { true }) @* {} (_=p.rowid, _=h.rowid)", expAll)
        chkSql("""SELECT $sqlWhat FROM "c0.person" A00, "c0.home" A01 $sqlTail""")

        chk("(p: person, h: home @* { false }) @* {} (_=p.rowid, _=h.rowid)", "[]")
        chkSql("""SELECT $sqlWhat FROM "c0.person" A00 JOIN "c0.home" A01 ON ? $sqlTail""")
    }

    @Test fun testJoinPathExpression() {
        initData()

        chk("(h: home, c: company @* { h.city.country.name == c.city.country.name }) @* {}",
            "[(h=home[201],c=company[300])]")

        val expSql = """
            SELECT A00."rowid", A01."rowid"
            FROM "c0.home" A00
            JOIN "c0.city" A02 ON A00."city" = A02."rowid"
            JOIN "c0.country" A03 ON A02."country" = A03."rowid"
            JOIN ("c0.company" A01
            JOIN "c0.city" A04 ON A01."city" = A04."rowid"
            JOIN "c0.country" A05 ON A04."country" = A05."rowid")
            ON A03."name" = A05."name"
            ORDER BY A00."rowid", A01."rowid"
        """
        chkSql(expSql.unwrap(" "))
    }

    @Test fun testJoinFirstEntity() {
        initData()

        chk("(p: person @* {}) @* {}", "ct_err:expr:at:from:nested_at")
        chk("(p: person @* { .score >= 20 }) @* {}", "ct_err:expr:at:from:nested_at")

        // TODO Error messages are not right, shall be improved.
        chk("(p: person @* { .score >= 20 }, h: home) @* {}",
            "ct_err:[expr:at:from:nested_at][at:from:mix_entity_iterable]")
        chk("(p: person @* { .score >= 20 }, h: home @* { h.person == p }) @* {}",
            "ct_err:[expr:at:from:nested_at][at:from:many_iterables:2][unknown_name:h]")
    }

    @Test fun testMultipleJoins() {
        initData()

        val what = "( _=p.name, _=h.city.name, _=c.name )"
        val exp = "[(Alice,Paris,Bank)]"

        chk("(p: person, h: home @* { h.person == p }, c: company @* { c.city == h.city }) @* {} $what", exp)

        chk("(p: person, c: company @* { c.city == h.city }, h: home @* { h.person == p }) @* {} $what",
            "ct_err:unknown_name:h")

        chk("""
            (p: person, h: home @* { h.person == p }, c: company @* { c.city == h.city }, j: job @* { j.person == p })
            @* {} $what
        """, exp)

        chk("""
            (
                p: person,
                h: home @* { h.person == p },
                c: company @* { c.city == h.city },
                j: job @* { j.person == p, j.company == c }
            ) @* {} $what
        """, exp)
    }

    @Test fun testOuterJoin() {
        initData()

        chk("(p: person, h: home) @* { h.person == p } ( _=p.name, _=h )",
            "[(Bob,home[200]), (Alice,home[201]), (Alice,home[202])]")
        chk("(p: person, @outer h: home @* { h.person == p }) @* {} ( _=p.name, _=h )",
            "[(Bob,home[200]), (Alice,home[201]), (Alice,home[202]), (Trudy,null)]")
        chk("(p: person, @outer h: home @* { h.person == p }) @* { p.score >= 20 } ( _=p.name, _=h )",
            "[(Alice,home[201]), (Alice,home[202]), (Trudy,null)]")

        chk("(p: person, @outer h: home @* { h.person == p }) @* {} ( _=p.name, _=h?.city )",
            "[(Bob,city[210]), (Alice,city[211]), (Alice,city[213]), (Trudy,null)]")
        chk("(p: person, @outer h: home @* { h.person == p }) @* { p.score >= 20 } ( _=p.name, _=h?.city )",
            "[(Alice,city[211]), (Alice,city[213]), (Trudy,null)]")

        chk("(p: person, @outer h: home) @* { h.person == p, p.score >= 20 } ( _=p.name, _=h?.city?.name )",
            "ct_err:expr_mem_null:home?:person")
        chk("(p: person, @outer h: home @* { h.person == p }) @* { p.score >= 20 } ( _=p.name, _=h?.city?.name )",
            "[(Alice,Paris), (Alice,Rome), (Trudy,null)]")
        chk("(p: person, @outer h: home @* { h.person == p, p.score >= 20 }) @* {} ( _=p.name, _=h?.city?.name )",
            "[(Bob,null), (Alice,Paris), (Alice,Rome), (Trudy,null)]")
    }

    @Test fun testOuterJoinComplexWhat() {
        def("function f1(h: home?) = '' + h;")
        def("function f2(c: city?) = '' + c;")
        def("function f3(s: text?) = '' + s;")
        initData()

        chk("(p: person, h: home) @* { h.person == p } ( _=p.name, _=f1(h) )",
            "[(Bob,home[200]), (Alice,home[201]), (Alice,home[202])]")
        chk("(p: person, @outer h: home @* { h.person == p }) @* {} ( _=p.name, _=f1(h) )",
            "[(Bob,home[200]), (Alice,home[201]), (Alice,home[202]), (Trudy,null)]")
        chk("(p: person, @outer h: home @* { h.person == p }) @* {} ( _=p.name, _=f2(h?.city) )",
            "[(Bob,city[210]), (Alice,city[211]), (Alice,city[213]), (Trudy,null)]")
        chk("(p: person, @outer h: home @* { h.person == p }) @* {} ( _=p.name, _=f3(h?.city?.name) )",
            "[(Bob,London), (Alice,Paris), (Alice,Rome), (Trudy,null)]")
    }

    @Test fun testOuterJoinType() {
        initData()
        chk("_type_of((p: person, home) @ {} (home))", "home")
        chk("_type_of((p: person, h: home) @ {} (h))", "home")
        chk("_type_of((p: person, h: home @* { h.person == p }) @ {} (h))", "home")
        chk("_type_of((p: person, @outer home) @ {} (home))", "home?")
        chk("_type_of((p: person, @outer h: home) @ {} (h))", "home?")
        chk("_type_of((p: person, @outer h: home @* { h.person == p }) @ {} (h))", "home?")
        chk("_type_of((p: person, @outer h: home @* { h.person == p }) @ {} (h?.city))", "city?")
        chk("_type_of((p: person, @outer h: home @* { h.person == p }) @ {} (h?.city?.name))", "text?")
    }

    @Test fun testOuterJoinSqlSimple() {
        initData()

        chk("(p: person, @outer h: home @* { h.person == p }) @* {} ( _=p, _=h )",
            "[(person[100],home[200]), (person[101],home[201]), (person[101],home[202]), (person[102],null)]")
        chkSql("""
           SELECT A00."rowid", A01."rowid" FROM "c0.person" A00
           LEFT OUTER JOIN "c0.home" A01 ON A01."person" = A00."rowid"
           ORDER BY A00."rowid", A01."rowid"
        """.unwrap(" "))

        chk("(p: person, @outer h: home @* { h.person == p }) @* {} ( _=p, _=h )",
            "[(person[100],home[200]), (person[101],home[201]), (person[101],home[202]), (person[102],null)]")
        chkSql("""
           SELECT A00."rowid", A01."rowid" FROM "c0.person" A00
           LEFT OUTER JOIN "c0.home" A01 ON A01."person" = A00."rowid"
           ORDER BY A00."rowid", A01."rowid"
        """.unwrap(" "))

        chk("(p: person, @outer h: home @* { h.person == p }) @* {} ( _=p, _=h?.city )",
            "[(person[100],city[210]), (person[101],city[211]), (person[101],city[213]), (person[102],null)]")
        chkSql("""
           SELECT A00."rowid", A01."city" FROM "c0.person" A00
           LEFT OUTER JOIN "c0.home" A01 ON A01."person" = A00."rowid"
           ORDER BY A00."rowid", A01."rowid"
        """.unwrap(" "))
    }

    @Test fun testOuterJoinSqlComplex() {
        initData()

        chk("(p: person, @outer h: home @* { h.person == p }) @* {} ( _=p, _=h?.city?.country )",
            "[(person[100],country[220]), (person[101],country[221]), (person[101],country[223]), (person[102],null)]")
        chkSql("""
            SELECT A00."rowid", A02."country" FROM "c0.person" A00
            LEFT OUTER JOIN ("c0.home" A01 JOIN "c0.city" A02 ON A01."city" = A02."rowid") ON A01."person" = A00."rowid"
            ORDER BY A00."rowid", A01."rowid"
        """.unwrap(" "))

        chk("(p: person, @outer h: home @* { h.person == p }) @* {} ( _=p, _=h?.city?.country?.name )",
            "[(person[100],UK), (person[101],France), (person[101],Italy), (person[102],null)]")
        chkSql("""
            SELECT A00."rowid", A03."name" FROM "c0.person" A00
            LEFT OUTER JOIN ("c0.home" A01
            JOIN "c0.city" A02 ON A01."city" = A02."rowid"
            JOIN "c0.country" A03 ON A02."country" = A03."rowid")
            ON A01."person" = A00."rowid"
            ORDER BY A00."rowid", A01."rowid"
        """.unwrap(" "))
    }

    @Test fun testOuterJoinPathExpression() {
        initData()

        chk("(h: home, @outer c: company @* { h.city.country.name == c.city.country.name }) @* {}",
            "[(h=home[200],c=null), (h=home[201],c=company[300]), (h=home[202],c=null)]")

        val expSql = """
            SELECT A00."rowid", A01."rowid"
            FROM "c0.home" A00
            JOIN "c0.city" A02 ON A00."city" = A02."rowid"
            JOIN "c0.country" A03 ON A02."country" = A03."rowid"
            LEFT OUTER JOIN ("c0.company" A01
            JOIN "c0.city" A04 ON A01."city" = A04."rowid"
            JOIN "c0.country" A05 ON A04."country" = A05."rowid")
            ON A03."name" = A05."name"
            ORDER BY A00."rowid", A01."rowid"
        """
        chkSql(expSql.unwrap(" "))
    }

    @Test fun testOuterJoinRestrictions() {
        initData()
        def("function get_cities() = city @* {};")

        chk("(p: person, @outer c: get_cities()) @* {}",
            "ct_err:[expr:at:from:outer:collection][at:from:mix_entity_iterable]")

        chkEx("{ val vs = [1,2,3]; return (p: person, @outer v: vs) @* {}; }",
            "ct_err:[expr:at:from:outer:collection][at:from:mix_entity_iterable]")

        chk("(@outer person) @* {}", "ct_err:expr:at:from:bad_outer_join")
        chk("(x: [1,2,3], @outer y: [4,5,6]) @* {}", "ct_err:[expr:at:from:bad_outer_join][at:from:many_iterables:2]")
    }

    @Test fun testOuterJoinManyMatches() {
        initData()
        chk("(p: person, h: home @* { h.person == p }) @* {} ( _=p.name, _=h.city.name )",
            "[(Bob,London), (Alice,Paris), (Alice,Rome)]")
        chk("(p: person, @outer h: home @* { h.person == p }) @* {} ( _=p.name, _=h?.city?.name )",
            "[(Bob,London), (Alice,Paris), (Alice,Rome), (Trudy,null)]")
    }

    @Test fun testOuterJoinManyJoins() {
        initData()

        val joinHome = "@outer h: home @* { h.person == p }"
        chk("(p: person, $joinHome, @outer c: company @* { c.city == h.city }) @* {}",
            "ct_err:expr_mem_null:home?:city")

        val what = "(_=p.name, _=h?.city?.name, _=c?.name)"
        chk("(p: person, $joinHome, @outer c: company @* { c.city == h?.city }) @* {} $what",
            "[(Bob,London,null), (Alice,Paris,Bank), (Alice,Rome,null), (Trudy,null,null)]")
    }

    @Test fun testOuterJoinAttribute() {
        initData()

        chk("_type_of((p: person, h: home @* { .person == p }) @ {} (.city))", "city")
        chk("_type_of((p: person, @outer h: home @* { .person == p }) @ {} (.city))", "city?")

        val from = "p: person, @outer h: home @* { .person == p }"
        chk("($from) @* {} (_=p.name, _=h?.city?.name)", "[(Bob,London), (Alice,Paris), (Alice,Rome), (Trudy,null)]")

        chk("($from) @* {} (_=p.name, _=.city)", "[(Bob,city[210]), (Alice,city[211]), (Alice,city[213]), (Trudy,null)]")
        chk("($from) @* {} (_=p.name, _=.city.name)", "ct_err:expr_mem_null:city?:name")
        chk("($from) @* {} (_=p.name, _=.city?.name)", "[(Bob,London), (Alice,Paris), (Alice,Rome), (Trudy,null)]")
    }

    @Test fun testOuterJoinAttributeInAnotherJoin() {
        initData()

        val what = "(_=p.name, _=h?.city?.name, _=c?.name)"
        chk("(p: person, @outer h: home @* { .person == p }, @outer c: city @* { c == .city }) @* {} $what",
            "ct_err:expr_attr_unknown:city")
        chk("(p: person, @outer h: home @* { .person == p }, @outer c: city @* { c == h?.city }) @* {} $what",
            "[(Bob,London,London), (Alice,Paris,Paris), (Alice,Rome,Rome), (Trudy,null,null)]")
        chk("(p: person, @outer h: home @* { .person == p }, @outer c: city @* { .rowid == h?.city?.rowid }) @* {} $what",
            "[(Bob,London,London), (Alice,Paris,Paris), (Alice,Rome,Rome), (Trudy,null,null)]")
    }

    @Test fun testOuterJoinToStruct() {
        initData()
        val s1 = "struct<home>{person=person[100],city=city[210]}"
        val s2 = "struct<home>{person=person[101],city=city[211]}"
        val s3 = "struct<home>{person=person[101],city=city[213]}"
        chk("(p: person, h: home @* { .person == p }) @* {} ( h.to_struct() )", "[$s1, $s2, $s3]")
        chk("(p: person, @outer h: home @* { .person == p }) @* {} ( h.to_struct() )",
            "ct_err:expr_mem_null:home?:to_struct")
        chk("(p: person, @outer h: home @* { .person == p }) @* {} ( h?.to_struct() )", "[$s1, $s2, $s3, null]")
    }

    @Test fun testOuterJoinNullComparison() {
        initData()
        val from = "(p: person, @outer h: home @* { .person == p })"
        val what = "( _=p.name, _=h?.city?.name )"
        chk("$from @* { h != null } $what", "[(Bob,London), (Alice,Paris), (Alice,Rome)]")
        chk("$from @* { h == null } $what", "[(Trudy,null)]")
        chk("$from @* { h?.city != null } $what", "[(Bob,London), (Alice,Paris), (Alice,Rome)]")
        chk("$from @* { h?.city == null } $what", "[(Trudy,null)]")
    }

    @Test fun testOuterJoinNullableComparison() {
        initData()

        val from = "p: person, @outer h: home @* { .person == p }"
        val what = "_=p.name, _=h?.city?.name"
        chk("($from) @* {} ($what)", "[(Bob,London), (Alice,Paris), (Alice,Rome), (Trudy,null)]")
        chk("($from) @* { h?.city?.name != 'Rome' } ($what)", "[(Bob,London), (Alice,Paris), (Trudy,null)]")
        chk("($from) @* { h?.city?.name == 'Paris' } ($what)", "[(Alice,Paris)]")

        val from2 = "$from, @outer j: job @* { .person == p }"
        val what2 = "$what, j?.company?.city?.name"
        chk("($from2) @* {} ($what2)",
            "[(Bob,London,null), (Alice,Paris,Paris), (Alice,Rome,Paris), (Trudy,null,null)]")
        chk("($from2) @* { j?.company?.city == h?.city } ($what2)", "[(Alice,Paris,Paris), (Trudy,null,null)]")
        chk("($from2) @* { h?.city == j?.company?.city } ($what2)", "[(Alice,Paris,Paris), (Trudy,null,null)]")
        chk("($from2) @* { j?.company?.city != h?.city } ($what2)", "[(Bob,London,null), (Alice,Rome,Paris)]")
        chk("($from2) @* { h?.city != j?.company?.city } ($what2)", "[(Bob,London,null), (Alice,Rome,Paris)]")

        //TODO support operator (T? in list<T>)
        chk("($from) @* { h?.city?.name in ['London', 'Rome'] } ($what)",
            "ct_err:binop_operand_type:in:[text?]:[list<text>]")
        chk("($from) @* { h?.city?.name not in ['London', 'Rome'] } ($what)",
            "ct_err:binop_operand_type:in:[text?]:[list<text>]")
    }

    @Test fun testOuterJoinNullOperations() {
        initData()
        val from = "p: person, @outer h: home @* { .person == p }"
        chk("($from) @* { p.name in ['Bob','Trudy'] } ( h?.city?.name )", "[London, null]")
        chk("($from) @* { p.name in ['Bob','Trudy'] } ( h?.city?.rowid )", "[210, null]")
        chk("($from) @* { p.name in ['Bob','Trudy'] } ( h?.city?.name + '!' )", "[London!, null!]")
        chk("($from) @* { p.name in ['Bob','Trudy'] } ( h?.city?.rowid + '!' )", "[210!, null!]")
        chk("($from) @* { p.name in ['Bob','Trudy'] } ( '!' + h?.city?.name )", "[!London, !null]")
        chk("($from) @* { p.name in ['Bob','Trudy'] } ( '!' + h?.city?.rowid )", "[!210, !null]")
    }

    @Test fun testOuterJoinElvis() {
        initData()

        val from = "(p: person, @outer h: home @* { .person == p })"
        chk("$from @* {} ( _=p.name, _=h?.city?.name )", "[(Bob,London), (Alice,Paris), (Alice,Rome), (Trudy,null)]")
        chk("$from @* {} ( _=p.name, _=h?.city?.name ?: 'n/a' )",
            "[(Bob,London), (Alice,Paris), (Alice,Rome), (Trudy,n/a)]")

        chk("$from @* {} ( _=p.name, _=h?.city )",
            "[(Bob,city[210]), (Alice,city[211]), (Alice,city[213]), (Trudy,null)]")
        chk("$from @* {} ( _=p.name, _=h?.city ?: (city @{} limit 1) )",
            "[(Bob,city[210]), (Alice,city[211]), (Alice,city[213]), (Trudy,city[210])]")

        chk("$from @* {} ( _=p.name, _=h )", "[(Bob,home[200]), (Alice,home[201]), (Alice,home[202]), (Trudy,null)]")
        chk("$from @* {} ( _=p.name, _=h ?: (home @{} limit 1) )",
            "[(Bob,home[200]), (Alice,home[201]), (Alice,home[202]), (Trudy,home[200])]")
    }

    private fun initData() {
        tst.strictToString = false
        def("entity person { name; score: integer; }")
        def("entity country { name; }")
        def("entity city { name; country; }")
        def("entity home { person; city; key person, city; }")
        def("entity company { name; city; }")
        def("entity job { key person, company; }")

        insert("c0.person", "name,score", "100,'Bob',10", "101,'Alice',20", "102,'Trudy',30")
        insert("c0.country", "name", "220,'UK'", "221,'France'", "222,'Germany'", "223,'Italy'")
        insert("c0.city", "name,country", "210,'London',220", "211,'Paris',221", "212,'Berlin',222", "213,'Rome',223")
        insert("c0.home", "person,city", "200,100,210", "201,101,211", "202,101,213")
        insert("c0.company", "name,city", "300,'Bank',211", "301,'Shop',212")
        insert("c0.job", "person,company", "400,101,300")
    }
}
