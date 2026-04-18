/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.expr.expr

import net.postchain.rell.base.compiler.base.core.C_AtAttrShadowing
import net.postchain.rell.base.testutils.BaseRellTest
import kotlin.test.Test

class UpdateDeleteTest: BaseRellTest(useSql = true) {
    override fun entityDefs() = listOf(
        "entity city { name: text; }",
        "entity person { name: text; mutable city; mutable street: text; mutable house: integer; mutable score: integer; }",
    )

    @Test fun testUpdatePersonSetScore() {
        createCitiesAndPersons()
        chkOp("update person @ { .name == 'James' } ( score = 125 );")
        chkDataCommon("person(4,James,3,Evergreen Ave,5,125)", "person(5,Mike,1,Grand St,7,250)")
    }

    @Test fun testUpdatePersonAddScore() {
        createCitiesAndPersons()
        chkOp("update person @ { .name == 'James' } ( score = .score + 50 );")
        chkDataCommon("person(4,James,3,Evergreen Ave,5,150)", "person(5,Mike,1,Grand St,7,250)")

        chkOp("var x = 33; update person @ { .name == 'James' } ( score = .score + x );")
        chkDataCommon("person(4,James,3,Evergreen Ave,5,183)", "person(5,Mike,1,Grand St,7,250)")

        chkOp("var x = 33; update person @ { .name == 'James' } ( score = .score + (x++) ); print(x);")
        chkDataCommon("person(4,James,3,Evergreen Ave,5,216)", "person(5,Mike,1,Grand St,7,250)")
        chkOut("34")

        chkOp("var x = 33; update person @ { .name == 'James' } ( score = .score + (++x) ); print(x);")
        chkDataCommon("person(4,James,3,Evergreen Ave,5,250)", "person(5,Mike,1,Grand St,7,250)")
        chkOut("34")
    }

    @Test fun testUpdatePersonMultiplyScore() {
        createCitiesAndPersons()
        chkOp("update person @ { .name == 'James' } ( score = .score * 3 );")
        chkDataCommon("person(4,James,3,Evergreen Ave,5,300)", "person(5,Mike,1,Grand St,7,250)")
    }

    @Test fun testUpdatePersonAll() {
        createCitiesAndPersons()
        chkOp("update person @* {} ( score = .score + 75 );")
        chkDataCommon("person(4,James,3,Evergreen Ave,5,175)", "person(5,Mike,1,Grand St,7,325)")
    }

    @Test fun testUpdatePersonSetFullAddress() {
        createCitiesAndPersons()
        chkOp("update person @ { .name == 'Mike' } ( city @ { 'San Francisco' }, street = 'Lawton St', house = 13 );")
        chkDataCommon("person(4,James,3,Evergreen Ave,5,100)", "person(5,Mike,2,Lawton St,13,250)")
    }

    @Test fun testUpdateMutable() {
        tst.atAttrShadowing = C_AtAttrShadowing.FULL
        def("entity city { name: text; }")
        def("entity person { name: text; home: city; mutable work: city; base: integer; mutable score: integer; }")

        chkOp("""
            val boston = create city('Boston');
            val seattle = create city('Seattle');
            val dallas = create city('Dallas');
            create person(name = 'Mike', home = boston, work = seattle, base = 100, score = 300);
            create person(name = 'Bob', home = seattle, work = dallas, base = 200, score = 500);
        """)

        chkData(
            "city(1,Boston)",
            "city(2,Seattle)",
            "city(3,Dallas)",
            "person(4,Mike,1,2,100,300)",
            "person(5,Bob,2,3,200,500)",
        )

        chkOp("update person @ { .name == 'Mike' } ( name = 'Bob' );", "ct_err:attr_not_mutable:person.name")
        chkOp("update person @ { .name == 'Bob' } ( home = city @ { .name == 'Boston' } );",
            "ct_err:attr_not_mutable:person.home")
        chkOp("update person @ { .name == 'Mike' } ( base = 999 );", "ct_err:attr_not_mutable:person.base")
        chkOp("val name = 'Bob'; update person @ { .name == 'Mike' } ( name );", "ct_err:attr_not_mutable:person.name")

        chkData(
            "city(1,Boston)",
            "city(2,Seattle)",
            "city(3,Dallas)",
            "person(4,Mike,1,2,100,300)",
            "person(5,Bob,2,3,200,500)",
        )

        chkOp("update person @ { .name == 'Bob' } ( city @ { .name == 'Dallas' } );")
        chkOp("update person @ { .name == 'Mike' } ( 777 );")

        chkData(
            "city(1,Boston)",
            "city(2,Seattle)",
            "city(3,Dallas)",
            "person(4,Mike,1,2,100,777)",
            "person(5,Bob,2,3,200,500)",
        )
    }

    @Test fun testDeletePerson() {
        createCitiesAndPersons()

        chkOp("delete person @ { .name == 'James' };")
        chkDataCommon("person(5,Mike,1,Grand St,7,250)")

        chkOp("delete person @ { .name == 'Mike' };")
        chkDataCommon()
    }

    @Test fun testDeleteCity() {
        createCities()

        chkOp("delete city @ { .name == 'San Francisco' };")
        chkData("city(1,New York)", "city(3,Los Angeles)")

        chkOp("delete city @* {};")
        chkData()
    }

    @Test fun testDeleteConstraint() {
        createCitiesAndPersons()

        chkData(
            "city(1,New York)", "city(2,San Francisco)", "city(3,Los Angeles)",
            "person(4,James,3,Evergreen Ave,5,100)", "person(5,Mike,1,Grand St,7,250)",
        )

        chkOp("delete city @ { .name == 'New York' };", "rt_err:sqlerr:0")
        chkData(
            "city(1,New York)", "city(2,San Francisco)", "city(3,Los Angeles)",
            "person(4,James,3,Evergreen Ave,5,100)", "person(5,Mike,1,Grand St,7,250)",
        )

        chkOp("delete person @ { .name == 'Mike' };")
        chkData(
            "city(1,New York)", "city(2,San Francisco)", "city(3,Los Angeles)",
            "person(4,James,3,Evergreen Ave,5,100)",
        )

        chkOp("delete city @ { .name == 'New York' };")
        chkData(
            "city(2,San Francisco)", "city(3,Los Angeles)",
            "person(4,James,3,Evergreen Ave,5,100)",
        )
    }

    @Test fun testUpdateEntityAlias() {
        createCitiesAndPersons()

        chkOp("update (p: person) @ { p.name == 'Mike' } ( score = 999 );")
        chkDataCommon("person(4,James,3,Evergreen Ave,5,100)", "person(5,Mike,1,Grand St,7,999)")

        chkOp("update (p: person) @ { person.name == 'Mike' } ( score = 777 );", "ct_err:unknown_member:[person]:name")
        chkDataCommon("person(4,James,3,Evergreen Ave,5,100)", "person(5,Mike,1,Grand St,7,999)")

        chkOp("update person @ { person.name == 'Mike' } ( score = 777 );")
        chkDataCommon("person(4,James,3,Evergreen Ave,5,100)", "person(5,Mike,1,Grand St,7,777)")
    }

    @Test fun testDeleteEntityAlias() {
        createCitiesAndPersons()

        chkOp("delete (p: person) @ { p.name == 'Mike' };")
        chkDataCommon("person(4,James,3,Evergreen Ave,5,100)")

        chkOp("delete (p: person) @ { person.name == 'James' };", "ct_err:unknown_member:[person]:name")
        chkDataCommon("person(4,James,3,Evergreen Ave,5,100)")

        chkOp("delete person @ { person.name == 'James' };")
        chkDataCommon()
    }

    @Test fun testUpdateExtraEntity() {
        createCitiesAndPersons()

        chkOp("update (p: person, c: city) @ { p.city == c, c.name == 'New York' } ( score = 999 );")
        chkDataCommon("person(4,James,3,Evergreen Ave,5,100)", "person(5,Mike,1,Grand St,7,999)")
    }

    @Test fun testDeleteExtraEntity() {
        createCitiesAndPersons()

        chkOp("delete (p: person, c: city) @ { p.city == c, c.name == 'New York' };")
        chkDataCommon("person(4,James,3,Evergreen Ave,5,100)")
    }

    @Test fun testErr() {
        chkOp("update foo @ {} ( x = 0 );", "ct_err:unknown_name:foo")
        chkOp("delete foo @ {};", "ct_err:unknown_name:foo")

        chkOp("update person @ {} ( foo = 123 );", "ct_err:attr_unknown_name:foo")

        chkOp("update person @ {} ( score = 123, score = 456 );", "ct_err:attr_dup_name:score")
        chkOp("update person @ {} ( score = 123, score = 123 );", "ct_err:attr_dup_name:score")

        chkOp("update person @ {} ();", "ct_err:syntax")
    }

    @Test fun testCompoundAssignmentInt() {
        createCitiesAndPersons()

        chkOp("update person @* {} ( score += 555 );")
        chkDataCommon("person(4,James,3,Evergreen Ave,5,655)", "person(5,Mike,1,Grand St,7,805)")

        chkOp("update person @* {} ( score -= 123 );")
        chkDataCommon("person(4,James,3,Evergreen Ave,5,532)", "person(5,Mike,1,Grand St,7,682)")

        chkOp("update person @* {} ( score *= 123 );")
        chkDataCommon("person(4,James,3,Evergreen Ave,5,65436)", "person(5,Mike,1,Grand St,7,83886)")

        chkOp("update person @* {} ( score /= 55 );")
        chkDataCommon("person(4,James,3,Evergreen Ave,5,1189)", "person(5,Mike,1,Grand St,7,1525)")

        chkOp("update person @* {} ( score %= 33 );")
        chkDataCommon("person(4,James,3,Evergreen Ave,5,1)", "person(5,Mike,1,Grand St,7,7)")
    }

    @Test fun testCompoundAssignmentText() {
        createCitiesAndPersons()

        chkOp("update person @* {} ( street += ' Foo ' );")
        chkDataCommon("person(4,James,3,Evergreen Ave Foo ,5,100)", "person(5,Mike,1,Grand St Foo ,7,250)")

        chkOp("update person @* {} ( street += 123 );")
        chkDataCommon("person(4,James,3,Evergreen Ave Foo 123,5,100)", "person(5,Mike,1,Grand St Foo 123,7,250)")
        chkOp("update person @* {} ( street += ' ' );")
        chkDataCommon("person(4,James,3,Evergreen Ave Foo 123 ,5,100)", "person(5,Mike,1,Grand St Foo 123 ,7,250)")

        chkOp("update person @* {} ( street += .score > 200 );")
        chkDataCommon("person(4,James,3,Evergreen Ave Foo 123 false,5,100)", "person(5,Mike,1,Grand St Foo 123 true,7,250)")
    }

    @Test fun testCompoundAssignmentErr() {
        chkOp("update person @ {} ( score += false );", "ct_err:binop_operand_type:+=:[integer]:[boolean]")
        chkOp("update person @ {} ( score += 'Hello' );", "ct_err:binop_operand_type:+=:[integer]:[text]")

        chkAssignmentErr("-=")
        chkAssignmentErr("*=")
        chkAssignmentErr("/=")
        chkAssignmentErr("%=")
    }

    private fun chkAssignmentErr(op: String) {
        chkOp("update person @ {} ( score $op false );", "ct_err:binop_operand_type:$op:[integer]:[boolean]")
        chkOp("update person @ {} ( score $op 'Hello' );", "ct_err:binop_operand_type:$op:[integer]:[text]")
        chkOp("update person @ {} ( street $op false );", "ct_err:binop_operand_type:$op:[text]:[boolean]")
        chkOp("update person @ {} ( street $op 123 );", "ct_err:binop_operand_type:$op:[text]:[integer]")
        chkOp("update person @ {} ( street $op 'Hello' );", "ct_err:binop_operand_type:$op:[text]:[text]")
    }

    @Test fun testUpdateDotAttribute() {
        createCitiesAndPersons()

        chkOp("update person @ { .name == 'James' } ( .score = 123 );")
        chkDataCommon("person(4,James,3,Evergreen Ave,5,123)", "person(5,Mike,1,Grand St,7,250)")

        chkOp("update person @* {} ( .score += 456 );")
        chkDataCommon("person(4,James,3,Evergreen Ave,5,579)", "person(5,Mike,1,Grand St,7,706)")

        chkOp("update person @* {} ( .score = .score - 789 );")
        chkDataCommon("person(4,James,3,Evergreen Ave,5,-210)", "person(5,Mike,1,Grand St,7,-83)")
    }

    @Test fun testRollback() {
        createCitiesAndPersons()

        chkDataCommon("person(4,James,3,Evergreen Ave,5,100)", "person(5,Mike,1,Grand St,7,250)")

        chkOp("""
            update person @ { .name == 'James' } ( score += 1000 );
            val x = 1 / 0;
            update person @ { .name == 'Mike' } ( score += 500 );
        """, "rt_err:expr:/:div0:1")

        chkDataCommon("person(4,James,3,Evergreen Ave,5,100)", "person(5,Mike,1,Grand St,7,250)")

        chkOp("""
            update person @ { .name == 'James' } ( score += 1000 );
            update person @ { .name == 'Mike' } ( score += 500 );
            val x = 1 / 0;
        """, "rt_err:expr:/:div0:1")

        chkDataCommon("person(4,James,3,Evergreen Ave,5,100)", "person(5,Mike,1,Grand St,7,250)")

        chkOp("""
            update person @ { .name == 'James' } ( score += 1000 );
            update person @ { .name == 'Mike' } ( score += 500 );
        """)

        chkDataCommon("person(4,James,3,Evergreen Ave,5,1100)", "person(5,Mike,1,Grand St,7,750)")
    }

    @Test fun testAccessChangedRecords() {
        createCitiesAndPersons()

        chkOp("""
            print(person @* {} ( .name, .score ));
            update person @ { .name == 'James' } ( score += 100 );
            print(person @* {} ( .name, .score ));
            update person @ { .name == 'Mike' } ( score += 200 );
            print(person @* {} ( .name, .score ));
            update person @* {} ( score /= 2 );
            print(person @* {} ( .name, .score ));
        """)

        chkOut(
            "[(name=James,score=100), (name=Mike,score=250)]",
            "[(name=James,score=200), (name=Mike,score=250)]",
            "[(name=James,score=200), (name=Mike,score=450)]",
            "[(name=James,score=100), (name=Mike,score=225)]",
        )
    }

    @Test fun testUpdateNameResolution() {
        createCitiesAndPersons()

        chkOp("val score = 10; update person @ { .name == 'Mike' } ( score );")
        chkDataCommon("person(4,James,3,Evergreen Ave,5,100)", "person(5,Mike,1,Grand St,7,10)")

        chkOp("val score = 'Hello'; update person @ { .name == 'Mike' } ( score );",
                "ct_err:[attr_bad_type:0:score:integer:text][stmt_assign_type:[integer]:[text]]") // TODO must be one error
    }

    @Test fun testUpdateNameConflictAliasVsLocal() {
        createCitiesAndPersons()

        chkOp("update (p: person) @ { .name == 'James' } ( .score = 123 );")
        chkDataCommon("person(4,James,3,Evergreen Ave,5,123)", "person(5,Mike,1,Grand St,7,250)")

        chkOp("val p = 123; update (p: person) @ { .name == 'James' } ( .score = 123 );", "ct_err:block:name_conflict:p")

        chkOp("update (person, p: person) @ { person.name == 'James' } ( .score = 456 );")
        chkDataCommon("person(4,James,3,Evergreen Ave,5,456)", "person(5,Mike,1,Grand St,7,250)")

        chkOp("val p = 123; update (person, p: person) @ { person.name == 'James' } ( .score = 789 );",
                "ct_err:block:name_conflict:p")
    }

    @Test fun testDeleteNameConflictAliasVsLocal() {
        createCitiesAndPersons()
        chkOp("val p = 123; delete (p: person) @ { .name == 'James' };", "ct_err:block:name_conflict:p")
        chkOp("val p = 123; delete (person, p: person) @ { person.name == 'James' };", "ct_err:block:name_conflict:p")
        chkDataCommon("person(4,James,3,Evergreen Ave,5,100)", "person(5,Mike,1,Grand St,7,250)")
    }

    @Test fun testUpdateCardinality() {
        fun james(score: Int) = "person(4,James,3,Evergreen Ave,5,$score)"
        fun mike(score: Int) = "person(5,Mike,1,Grand St,7,$score)"

        createCitiesAndPersons()
        chkDataCommon(james(100), mike(250))

        // @
        chkOp("update person @ { .name == 'Unknown' } ( score = 10 );", "rt_err:at:wrong_count:0")
        chkDataCommon(james(100), mike(250))
        chkOp("update person @ { .name == 'James' } ( score = 11 );")
        chkDataCommon(james(11), mike(250))
        chkOp("update person @ {} ( score = 12 );", "rt_err:at:wrong_count:2")
        chkDataCommon(james(11), mike(250))

        // @?
        chkOp("update person @? { .name == 'Unknown' } ( score = 20 );")
        chkDataCommon(james(11), mike(250))
        chkOp("update person @? { .name == 'James' } ( score = 21 );")
        chkDataCommon(james(21), mike(250))
        chkOp("update person @? {} ( score = 22 );", "rt_err:at:wrong_count:2")
        chkDataCommon(james(21), mike(250))

        // @+
        chkOp("update person @+ { .name == 'Unknown' } ( score = 30 );", "rt_err:at:wrong_count:0")
        chkDataCommon(james(21), mike(250))
        chkOp("update person @+ { .name == 'James' } ( score = 31 );")
        chkDataCommon(james(31), mike(250))
        chkOp("update person @+ {} ( score = 32 );")
        chkDataCommon(james(32), mike(32))

        // @*
        chkOp("update person @* { .name == 'Unknown' } ( score = 40 );")
        chkDataCommon(james(32), mike(32))
        chkOp("update person @* { .name == 'James' } ( score = 41 );")
        chkDataCommon(james(41), mike(32))
        chkOp("update person @* {} ( score = 42 );")
        chkDataCommon(james(42), mike(42))
    }

    @Test fun testDeleteCardinality() {
        val james = "person(4,James,3,Evergreen Ave,5,100)"
        val mike = "person(5,Mike,1,Grand St,7,250)"

        // @
        resetChkOp("delete person @ { .name == 'Unknown' };", "rt_err:at:wrong_count:0")
        chkDataCommon(james, mike)
        resetChkOp("delete person @ { .name == 'James' };")
        chkDataCommon(mike)
        resetChkOp("delete person @ {};", "rt_err:at:wrong_count:2")
        chkDataCommon(james, mike)

        // @?
        resetChkOp("delete person @? { .name == 'Unknown' };")
        chkDataCommon(james, mike)
        resetChkOp("delete person @? { .name == 'James' };")
        chkDataCommon(mike)
        resetChkOp("delete person @? {};", "rt_err:at:wrong_count:2")
        chkDataCommon(james, mike)

        // @+
        resetChkOp("delete person @+ { .name == 'Unknown' };", "rt_err:at:wrong_count:0")
        chkDataCommon(james, mike)
        resetChkOp("delete person @+ { .name == 'James' };")
        chkDataCommon(mike)
        resetChkOp("delete person @+ {};")
        chkDataCommon()

        // @*
        resetChkOp("delete person @* { .name == 'Unknown' };")
        chkDataCommon(james, mike)
        resetChkOp("delete person @* { .name == 'James' };")
        chkDataCommon(mike)
        resetChkOp("delete person @* {};")
        chkDataCommon()
    }

    @Test fun testDeleteNoAttributes() {
        def("entity person {}")
        chkOp("create person();")
        chkData("person(1)")
        chkOp("delete person@*{};")
        chkData()
    }

    @Test fun testUpdateShortSyntax() {
        fun james(score: Int) = "person(4,James,3,Evergreen Ave,5,$score)"
        fun mike(score: Int) = "person(5,Mike,1,Grand St,7,$score)"
        fun person(name: String) = "person @ { .name == '$name' }"

        createCitiesAndPersons()
        chkDataCommon(james(100), mike(250))

        resetChkOp("val p = ${person("James")}; p.score = 33;")
        chkDataCommon(james(33), mike(250))

        resetChkOp("val p = ${person("James")}; p.score += 33;")
        chkDataCommon(james(100+33), mike(250))

        resetChkOp("val p = ${person("James")}; p.score *= 33;")
        chkDataCommon(james(100*33), mike(250))

        resetChkOp("val p = ${person("Mike")}; p.score *= 33;")
        chkDataCommon(james(100), mike(250*33))

        resetChkOp("val p = ${person("Mike")}; p.score = 'Hello';", "ct_err:stmt_assign_type:[integer]:[text]")
        chkDataCommon(james(100), mike(250))

        resetChkOp("val p = ${person("Mike")}; p.score += 'Hello';", "ct_err:binop_operand_type:+=:[integer]:[text]")
        chkDataCommon(james(100), mike(250))

        resetChkOp("val p = ${person("Mike")}; val v: integer? = _nullable(123); p.score += v;",
                "ct_err:binop_operand_type:+=:[integer]:[integer?]")
        chkDataCommon(james(100), mike(250))

        resetChkOp("val p = ${person("Mike")}; p.name = 'Bond';", "ct_err:attr_not_mutable:person.name")
        chkDataCommon(james(100), mike(250))

        resetChkOp("val p = ${person("Mike")}; p.name += 'Bond';", "ct_err:attr_not_mutable:person.name")
        chkDataCommon(james(100), mike(250))

        resetData()
        chkEx("{ val p = ${person("Mike")}; p.score += 123; return 0; }", "ct_err:no_db_update:query")
        chkDataCommon(james(100), mike(250))
    }

    @Test fun testUpdateShortSyntaxComplexSource() {
        fun james(score: Int) = "person(4,James,3,Evergreen Ave,5,$score)"
        fun mike(score: Int) = "person(5,Mike,1,Grand St,7,$score)"
        fun person(name: String) = "person @ { .name == '$name' }"

        def(entityDefs())
        def("function f(x: integer) = x*x;")

        createCitiesAndPersons()
        chkDataCommon(james(100), mike(250))

        resetChkOp("val x = 33; val p = ${person("James")}; p.score = x;")
        chkDataCommon(james(33), mike(250))

        resetChkOp("val x = 33; val p = ${person("James")}; p.score += x;")
        chkDataCommon(james(100+33), mike(250))

        resetChkOp("var x = 33; val p = ${person("James")}; p.score = x++; print(x);")
        chkDataCommon(james(33), mike(250))
        chkOut("34")

        resetChkOp("var x = 33; val p = ${person("James")}; p.score += x++; print(x);")
        chkDataCommon(james(100+33), mike(250))
        chkOut("34")

        resetChkOp("var x = 33; val p = ${person("James")}; p.score = ++x; print(x);")
        chkDataCommon(james(34), mike(250))
        chkOut("34")

        resetChkOp("var x = 33; val p = ${person("James")}; p.score += ++x; print(x);")
        chkDataCommon(james(100+34), mike(250))
        chkOut("34")

        resetChkOp("val x = 33; val p = ${person("James")}; p.score *= f(x);")
        chkDataCommon(james(100*33*33), mike(250))

        resetChkOp("var x = 33; val p = ${person("James")}; p.score *= f(x++); print(x);")
        chkDataCommon(james(100*33*33), mike(250))
        chkOut("34")

        resetChkOp("var x = 33; val p = ${person("James")}; p.score *= f(++x); print(x);")
        chkDataCommon(james(100*34*34), mike(250))
        chkOut("34")
    }

    @Test fun testUpdateShortSyntaxNullable() {
        fun james(score: Int) = "person(4,James,3,Evergreen Ave,5,$score)"
        fun mike(score: Int) = "person(5,Mike,1,Grand St,7,$score)"
        fun person(name: String) = "person @? { .name == '$name' }"

        createCitiesAndPersons()
        chkDataCommon(james(100), mike(250))

        resetChkOp("val p = ${person("James")}; p.score = 33;", "ct_err:expr_mem_null:person?:score")
        chkDataCommon(james(100), mike(250))

        resetChkOp("val p = ${person("James")}; p!!.score = 33;")
        chkDataCommon(james(33), mike(250))

        resetChkOp("val p = ${person("Bob")}; p!!.score = 33;", "rt_err:null_value")
        chkDataCommon(james(100), mike(250))

        resetChkOp("val p = ${person("James")}; p?.score = 33;")
        chkDataCommon(james(33), mike(250))

        resetChkOp("val p = ${person("Bob")}; p?.score = 33;")
        chkDataCommon(james(100), mike(250))

        resetChkOp("val p = ${person("James")}; p?.score += 33;")
        chkDataCommon(james(133), mike(250))

        resetChkOp("val p = ${person("Bob")}; p?.score += 33;")
        chkDataCommon(james(100), mike(250))
    }

    @Test fun testUpdateShortSyntaxReference() {
        fun james(score: Int, city: Int) = "person(4,James,$city,Evergreen Ave,5,$score)"
        fun mike(score: Int, city: Int) = "person(5,Mike,$city,Grand St,7,$score)"
        fun person(name: String) = "person @ { .name == '$name' }"
        fun city(name: String) = "city @ { .name == '$name' }"

        createCitiesAndPersons()
        chkDataCommon(james(100, 3), mike(250, 1))

        resetChkOp("val p = ${person("James")}; p.city = ${city("San Francisco")};")
        chkDataCommon(james(100, 2), mike(250, 1))

        resetChkOp("val p = ${person("James")}; p.city += ${city("San Francisco")};",
            "ct_err:binop_operand_type:+=:[city]:[city]")
        resetChkOp("val p = ${person("James")}; p.city *= ${city("San Francisco")};",
            "ct_err:binop_operand_type:*=:[city]:[city]")
        chkDataCommon(james(100, 3), mike(250, 1))

        resetChkOp("val p = ${person("Mike")}; p.city = ${city("Los Angeles")};")
        chkDataCommon(james(100, 3), mike(250, 3))
    }

    @Test fun testUpdateShortSyntaxPath() {
        def("entity person { name; mutable score: integer; }")
        def("entity foo { p: person; }")
        def("entity bar { f: foo; }")
        tst.inserts = listOf()
        insert("c0.person", "name,score", "1,'James',100")
        insert("c0.person", "name,score", "2,'Mike',250")
        insert("c0.foo", "p", "1,1")
        insert("c0.bar", "f", "1,1")

        chkData("person(1,James,100)", "person(2,Mike,250)", "foo(1,1)", "bar(1,1)")

        chkOp("val b = bar @ {}; b.f.p.score = 50;")
        chkData("person(1,James,50)", "person(2,Mike,250)", "foo(1,1)", "bar(1,1)")

        chkOp("val b = bar @ {}; b.f.p.score += 33;")
        chkData("person(1,James,83)", "person(2,Mike,250)", "foo(1,1)", "bar(1,1)")

        chkOp("val b = bar @ {}; b.f.p.score *= 11;")
        chkData("person(1,James,913)", "person(2,Mike,250)", "foo(1,1)", "bar(1,1)")

        chkOp("val b0 = bar @ {}; update (p:person, b:bar) @* { p == b.f.p, b == b0 } ( score += 33 );")
        chkData("person(1,James,946)", "person(2,Mike,250)", "foo(1,1)", "bar(1,1)")
    }

    @Test fun testUpdateIncrementDecrement() {
        fun james(score: Int) = "person(4,James,3,Evergreen Ave,5,$score)"
        fun mike(score: Int) = "person(5,Mike,1,Grand St,7,$score)"

        createCitiesAndPersons()
        chkDataCommon(james(100), mike(250))

        // Increment/decrement is not supported now, but may be supported later.
        chkOp("update person @ { .name == 'James' } ( .score++ );", "ct_err:expr_bad_dst")
        chkOp("update person @ { .name == 'James' } ( .score-- );", "ct_err:expr_bad_dst")
        chkOp("update person @ { .name == 'James' } ( ++.score );", "ct_err:expr_bad_dst")
        chkOp("update person @ { .name == 'James' } ( --.score );", "ct_err:expr_bad_dst")
    }

    @Test fun testUpdateTypePromotion() {
        def("entity data { mutable x: decimal; }")
        insert("c0.data", "x", "1,123")

        chkOp("update data @* {} ( 456 );", "ct_err:attr_implic_unknown:0:integer")
        chk("data @* {} ( _=.x )", "list<decimal>[dec[123]]")

        chkOp("update data @* {} ( x = 456 );")
        chk("data @* {} ( _=.x )", "list<decimal>[dec[456]]")

        chkOp("update data @* {} ( x = 789.0 );")
        chk("data @* {} ( _=.x )", "list<decimal>[dec[789]]")

        chkOp("val d = data @{}; d.x = 987;")
        chk("data @* {} ( _=.x )", "list<decimal>[dec[987]]")

        chkOp("val d = data @{}; d.x = 654.0;")
        chk("data @* {} ( _=.x )", "list<decimal>[dec[654]]")

        chkOp("val x = 321; update data @* {} ( x );", "OK")
        chk("data @* {} ( _=.x )", "list<decimal>[dec[321]]")

        chkOp("update data @* {} ( x += 123 );", "OK")
        chk("data @* {} ( _=.x )", "list<decimal>[dec[444]]")
    }

    @Test fun testBugGamePlayerSubExpr() {
        def("entity user { name; mutable games_total: integer; mutable games_won: integer; }")
        def("entity game { player_1: user; player_2: user; }")
        insert("c0.user", "name,games_total,games_won", "1,'Bob',3,2")
        insert("c0.user", "name,games_total,games_won", "4,'Alice',6,5")
        insert("c0.game", "player_1,player_2", "7,1,4")

        chkOp("""
            val the_game = game @ {};
            val the_user = user @ { .name == 'Bob' };
            update user @ { the_game.player_1 == user } ( games_won += 1, games_total += 1 );
        """)
    }

    @Test fun testDeleteAndAccess() {
        createCitiesAndPersons()

        val code = """
            val u = person @ { .name == 'James' };
            delete u;
            print(u.score);
        """

        chkOp(code, "rt_err:expr_entity_attr_count:0")
    }

    @Test fun testPlaceholder() {
        createCitiesAndPersons()
        chkDataCommon("person(4,James,3,Evergreen Ave,5,100)", "person(5,Mike,1,Grand St,7,250)")

        chkOp("update person @* { $.name == 'James' } ( .score = $.score + 1 );")
        chkDataCommon("person(4,James,3,Evergreen Ave,5,101)", "person(5,Mike,1,Grand St,7,250)")

        chkOp("delete person @* { $.name == 'James' };")
        chkDataCommon("person(5,Mike,1,Grand St,7,250)")
    }

    @Test fun testPathAssignment() {
        initPathAssignment()

        chkData("group(300,Foo)", "company(100,Adidas,Boston,300)", "user(200,Bob,100)")

        chkSqlCtr(0)
        chkOp("val u = user @ {}; u.company.city = 'Seattle';")
        chkSqlCtr(2)
        chkData("group(300,Foo)", "company(100,Adidas,Seattle,300)", "user(200,Bob,100)")

        chkSqlCtr(0)
        chkOp("val u = user @ {}; u.company.group.name = 'Bar';")
        chkSqlCtr(2)
        chkData("group(300,Bar)", "company(100,Adidas,Seattle,300)", "user(200,Bob,100)")
    }

    @Test fun testPathAssignmentNullable() {
        initPathAssignment()

        chkData("group(300,Foo)", "company(100,Adidas,Boston,300)", "user(200,Bob,100)")

        chkOp("val u = user @? {}; u.company.city = 'Dallas';", "ct_err:expr_mem_null:user?:company")
        chkOp("val u = user @? {}; u?.company.city = 'Dallas';", "ct_err:expr_mem_null:company?:city")
        chkData("group(300,Foo)", "company(100,Adidas,Boston,300)", "user(200,Bob,100)")

        resetSqlBuffer()
        chkOp("val u = user @? {}; u?.company?.city = 'Dallas';")
        chkSqlCtr(2)
        chkData("group(300,Foo)", "company(100,Adidas,Dallas,300)", "user(200,Bob,100)")

        chkOp("val u = user @? { 'Alice' }; u?.company?.city = 'Chicago';")
        chkData("group(300,Foo)", "company(100,Adidas,Dallas,300)", "user(200,Bob,100)")
    }

    private fun initPathAssignment() {
        def("entity group { mutable name; }")
        def("entity company { mutable name; mutable city: text; mutable group; }")
        def("entity user { mutable name; mutable company; }")
        insert("c0.group", "name", "300,'Foo'")
        insert("c0.company", "name,city,group", "100,'Adidas','Boston',300")
        insert("c0.user", "name,company", "200,'Bob',100")
    }

    @Test fun testUpdatePathAmbiguousColumnShortSyntax() {
        initUpdatePathAmbiguousColumn()
        chkData("A(10,1,20)", "A(11,2,21)", "B(20,100,50,30)", "B(21,200,60,31)", "C(30,300,70)", "C(31,400,80)")

        chkOp("val a = A @ {1}; a.b.c.x = 111;")
        chkData("A(10,1,20)", "A(11,2,21)", "B(20,100,50,30)", "B(21,200,60,31)", "C(30,111,70)", "C(31,400,80)")
        chkOp("val a = A @ {2}; a.b.c.x = 222;")
        chkData("A(10,1,20)", "A(11,2,21)", "B(20,100,50,30)", "B(21,200,60,31)", "C(30,111,70)", "C(31,222,80)")

        chkOp("val a = A @ {1}; a.b.x = 333;")
        chkData("A(10,1,20)", "A(11,2,21)", "B(20,333,50,30)", "B(21,200,60,31)", "C(30,111,70)", "C(31,222,80)")
        chkOp("val a = A @ {2}; a.b.x = 444;")
        chkData("A(10,1,20)", "A(11,2,21)", "B(20,333,50,30)", "B(21,444,60,31)", "C(30,111,70)", "C(31,222,80)")

        chkOp("val a = A @ {1}; a.b.c.x += 5;")
        chkData("A(10,1,20)", "A(11,2,21)", "B(20,333,50,30)", "B(21,444,60,31)", "C(30,116,70)", "C(31,222,80)")
        chkOp("val a = A @ {2}; a.b.c.x += 5;")
        chkData("A(10,1,20)", "A(11,2,21)", "B(20,333,50,30)", "B(21,444,60,31)", "C(30,116,70)", "C(31,227,80)")

        chkOp("val a = A @ {1}; a.b.x += 5;")
        chkData("A(10,1,20)", "A(11,2,21)", "B(20,338,50,30)", "B(21,444,60,31)", "C(30,116,70)", "C(31,227,80)")
        chkOp("val a = A @ {2}; a.b.x += 5;")
        chkData("A(10,1,20)", "A(11,2,21)", "B(20,338,50,30)", "B(21,449,60,31)", "C(30,116,70)", "C(31,227,80)")
    }

    @Test fun testUpdatePathAmbiguousColumnLongSyntax() {
        initUpdatePathAmbiguousColumn()
        chkData("A(10,1,20)", "A(11,2,21)", "B(20,100,50,30)", "B(21,200,60,31)", "C(30,300,70)", "C(31,400,80)")

        chkOp("update (c:C, a:A) @ { a.id == 1, c == a.b.c } ( .x = 111 );")
        chkData("A(10,1,20)", "A(11,2,21)", "B(20,100,50,30)", "B(21,200,60,31)", "C(30,111,70)", "C(31,400,80)")
        chkOp("update (c:C, a:A) @ { a.id == 2, c == a.b.c } ( .x = 222 );")
        chkData("A(10,1,20)", "A(11,2,21)", "B(20,100,50,30)", "B(21,200,60,31)", "C(30,111,70)", "C(31,222,80)")

        chkOp("update (b:B, a:A) @ { a.id == 1, b == a.b } ( .x = 333 );")
        chkData("A(10,1,20)", "A(11,2,21)", "B(20,333,50,30)", "B(21,200,60,31)", "C(30,111,70)", "C(31,222,80)")
        chkOp("update (b:B, a:A) @ { a.id == 2, b == a.b } ( .x = 444 );")
        chkData("A(10,1,20)", "A(11,2,21)", "B(20,333,50,30)", "B(21,444,60,31)", "C(30,111,70)", "C(31,222,80)")

        chkOp("update (c:C, a:A) @ { a.id == 1, c == a.b.c } ( .x += 5 );")
        chkData("A(10,1,20)", "A(11,2,21)", "B(20,333,50,30)", "B(21,444,60,31)", "C(30,116,70)", "C(31,222,80)")
        chkOp("update (c:C, a:A) @ { a.id == 2, c == a.b.c } ( .x += 5 );")
        chkData("A(10,1,20)", "A(11,2,21)", "B(20,333,50,30)", "B(21,444,60,31)", "C(30,116,70)", "C(31,227,80)")

        chkOp("update (b:B, a:A) @ { a.id == 1, b == a.b } ( .x += 5 );")
        chkData("A(10,1,20)", "A(11,2,21)", "B(20,338,50,30)", "B(21,444,60,31)", "C(30,116,70)", "C(31,227,80)")
        chkOp("update (b:B, a:A) @ { a.id == 2, b == a.b } ( .x += 5 );")
        chkData("A(10,1,20)", "A(11,2,21)", "B(20,338,50,30)", "B(21,449,60,31)", "C(30,116,70)", "C(31,227,80)")
    }

    private fun initUpdatePathAmbiguousColumn() {
        def("entity A { id: integer; b: B; }")
        def("entity B { mutable x: integer; y: integer; c: C; }")
        def("entity C { mutable x: integer; y: integer; }")
        tst.inserts = listOf()
        insert("c0.C", "x,y", "30,300,70")
        insert("c0.C", "x,y", "31,400,80")
        insert("c0.B", "x,y,c", "20,100,50,30")
        insert("c0.B", "x,y,c", "21,200,60,31")
        insert("c0.A", "id,b", "10,1,20")
        insert("c0.A", "id,b", "11,2,21")
    }

    @Test fun testUpdateSql() {
        def("entity user { name; mutable value: integer; }")
        insert("c0.user", "name,value", "1,'Bob',123")

        val sql = """UPDATE "c0.user" A00 SET "value" = ? RETURNING A00."rowid""""
        chkOpSql("update user @? {} ( .value = 500 );", sql)
        chkOpSql("update user @  {} ( .value = 501 );", sql)
        chkOpSql("update user @+ {} ( .value = 502 );", sql)
        chkOpSql("update user @* {} ( .value = 503 );", sql)
    }

    @Test fun testDeleteSql() {
        def("entity user { name; mutable value: integer; }")
        insert("c0.user", "name,value", "1,'Bob',123")
        insert("c0.user", "name,value", "2,'Alice',456")

        val sql = """DELETE FROM "c0.user" A00 WHERE A00."name" = ? RETURNING A00."rowid""""
        chkOpSql("delete user @? { 'Trudy' };", sql)
        chkOpSql("delete user @  { 'Bob'   };", sql)
        chkOpSql("delete user @+ { 'Alice' };", sql)
        chkOpSql("delete user @* { 'Trudy' };", sql)
    }

    private fun chkOpSql(code: String, sql: String) {
        chkSql()
        chkOp(code, "OK")
        chkSql(sql)
    }

    private fun resetChkOp(code: String, expected: String = "OK") {
        resetData()
        chkOp(code, expected)
    }

    private fun resetData() {
        tst.resetRowid()
        chkOp("delete person @* {}; delete city @* {};")
        createCitiesAndPersons()
    }

    private fun createCities() {
        chkData()

        chkOp("create city ( 'New York' );")
        chkData("city(1,New York)")

        chkOp("create city ( 'San Francisco' );")
        chkData("city(1,New York)", "city(2,San Francisco)")

        chkOp("create city ( 'Los Angeles' );")
        chkData("city(1,New York)", "city(2,San Francisco)", "city(3,Los Angeles)")
    }

    private fun createCitiesAndPersons() {
        createCities()

        chkOp("create person ( name = 'James', city @ { 'Los Angeles' }, street = 'Evergreen Ave', house = 5, score = 100 );")
        chkDataCommon("person(4,James,3,Evergreen Ave,5,100)")

        chkOp("create person ( name = 'Mike', city @ { 'New York' }, street = 'Grand St', house = 7, score = 250 );")
        chkDataCommon("person(4,James,3,Evergreen Ave,5,100)", "person(5,Mike,1,Grand St,7,250)")
    }

    private fun chkDataCommon(vararg expectedArray: String) {
        val cities = arrayOf(
            "city(1,New York)",
            "city(2,San Francisco)",
            "city(3,Los Angeles)",
        )
        chkData(*(cities + expectedArray))
    }
}
