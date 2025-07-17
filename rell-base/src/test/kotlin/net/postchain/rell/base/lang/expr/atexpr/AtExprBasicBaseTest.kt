/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.expr.atexpr

import net.postchain.rell.base.testutils.SqlTestUtils
import kotlin.test.Test

abstract class AtExprBasicBaseTest: AtExprBaseTest() {
    private val fromUser = impFrom("user")
    private val fromCompany = impFrom("company")

    override fun objInserts() = listOf(
        Ins.company(100, "Facebook"),
        Ins.company(200, "Apple"),
        Ins.company(300, "Amazon"),
        Ins.company(400, "Microsoft"),
        Ins.company(500, "Google"),

        Ins.user(10, 100, "Mark", "Zuckerberg"),
        Ins.user(20, 200, "Steve", "Jobs"),
        Ins.user(21, 200, "Steve", "Wozniak"),
        Ins.user(30, 300, "Jeff", "Bezos"),
        Ins.user(40, 400, "Bill", "Gates"),
        Ins.user(41, 400, "Paul", "Allen"),
        Ins.user(50, 500, "Sergey", "Brin"),
        Ins.user(51, 500, "Larry", "Page"),
    )

    private fun initDataUserCompany() {
        impDefType("company", "name")
        impDefType("user", "firstName:text", "lastName:text", "company:company")
    }

    @Test fun testEmptyWhere() {
        initDataUserCompany()
        chk("$fromUser @* {}", "list<user>[user[10],user[20],user[21],user[30],user[40],user[41],user[50],user[51]]")
        chk("$fromCompany @* {}", "list<company>[company[100],company[200],company[300],company[400],company[500]]")
    }

    @Test fun testNoRecordsFound() {
        initDataUserCompany()
        chk("$fromUser @ { .lastName == 'Socrates' }", "rt_err:at:wrong_count:0")
    }

    @Test fun testManyRecordsFound() {
        initDataUserCompany()
        chk("$fromUser @ { .firstName == 'Steve' }", "rt_err:at:wrong_count:2")
    }

    @Test fun testFindByObjectReference() {
        initDataUserCompany()
        chkEx("{ val corp = $fromCompany @ { .name == 'Facebook' }; return $fromUser @ { .company == corp }; }", "user[10]")
    }

    @Test fun testAttributeByVariableName() {
        initDataUserCompany()
        chkEx("{ val firstName = 'Bill'; return $fromUser @ { firstName }; }", "user[40]")
        chkEx("{ val lastName = 'Gates'; return $fromUser @ { lastName }; }", "user[40]")
        chkEx("{ val name = 'Microsoft'; return $fromCompany @ { name }; }", "company[400]")
        chkEx("{ val name = 'Bill'; return (u:$fromUser) @ { name }; }",
                "ct_err:at_where:var_manyattrs_type:0:name:text:[u:user.firstName,u:user.lastName]")
        chkEx("{ val name = 12345; return $fromCompany @ { name }; }", "ct_err:at_where:var_noattrs:0:name:integer")
        chkEx("{ val company = $fromCompany @ { .name == 'Facebook' }; return $fromUser @ { company }; }", "user[10]")
    }

    @Test fun testAttributeByExpressionType() {
        initDataUserCompany()
        chkEx("{ val corp = $fromCompany @ { .name == 'Facebook' }; return $fromUser @ { corp }; }", "user[10]")
        chkEx("{ val corp = $fromCompany @ { .name == 'Microsoft' }; return $fromUser @* { corp }; }",
                "list<user>[user[40],user[41]]")
    }

    @Test fun testAttributeByExpressionTypeNullable() {
        initDataUserCompany()
        val code = """{
            val c = _nullable($fromCompany @ { .name == 'Facebook' });
            if (c == null) return null;
            return $fromUser @* { c };
        }"""
        chkEx(code, "list<user>[user[10]]")
    }

    @Test fun testSingleEntityAlias() {
        initDataUserCompany()
        chk("(u: $fromUser) @ { u.firstName == 'Bill' }", "user[40]")
        chk("(u: $fromUser) @ { user.firstName == 'Bill' }", "ct_err:unknown_member:[user]:firstName")
    }

    @Test fun testNameResolutionLocalVsAttr() {
        initDataUserCompany()
        chkEx("{ return $fromUser @* { .firstName == 'Mark' }; }", "list<user>[user[10]]")
        chkEx("{ val firstName = 'Bill'; return $fromUser @* { firstName == 'Mark' }; }", "list<user>[]")
        chkEx("{ val firstName = 'Bill'; return $fromUser @* { firstName == firstName }; }",
                "list<user>[user[10],user[20],user[21],user[30],user[40],user[41],user[50],user[51]]")
        chkEx("{ val firstName = 'Bill'; return $fromUser @ { firstName }; }", "user[40]")
        chkEx("{ val firstName = 'Bill'; return $fromUser @ { .firstName == firstName }; }", "user[40]")
        chkEx("{ val firstName = 'Bill'; return $fromUser @* {} ( firstName ); }",
                "list<text>[text[Bill],text[Bill],text[Bill],text[Bill],text[Bill],text[Bill],text[Bill],text[Bill]]")
        chkEx("{ val firstName = 'Bill'; return $fromUser @* {} ( .firstName ); }",
                "list<text>[text[Mark],text[Steve],text[Steve],text[Jeff],text[Bill],text[Paul],text[Sergey],text[Larry]]")
    }

    @Test fun testNestedAtExpression() {
        initDataUserCompany()
        chk("$fromUser @* { .company == $fromCompany @ { .name == 'Facebook' } }", "list<user>[user[10]]")
        chk("$fromUser @* { .company == $fromCompany @ { .name == 'Apple' } }", "list<user>[user[20],user[21]]")
        chk("$fromUser @* { .company == $fromCompany @ { .name == 'Amazon' } }", "list<user>[user[30]]")
        chk("$fromUser @* { .company == $fromCompany @ { .name == 'Microsoft' } }", "list<user>[user[40],user[41]]")
        chk("$fromUser @* { .company == $fromCompany @ { .name == 'Google' } }", "list<user>[user[50],user[51]]")
        chk("$fromUser @* { $fromCompany @ { .name == 'Facebook' } }", "list<user>[user[10]]")
        chk("$fromUser @* { $fromCompany @ { .name == 'Apple' } }", "list<user>[user[20],user[21]]")
        chk("$fromUser @* { $fromCompany @ { .name == 'Amazon' } }", "list<user>[user[30]]")
        chk("$fromUser @* { $fromCompany @ { .name == 'Microsoft' } }", "list<user>[user[40],user[41]]")
        chk("$fromUser @* { $fromCompany @ { .name == 'Google' } }", "list<user>[user[50],user[51]]")
    }

    @Test fun testFieldSelectionSimple() {
        initDataUserCompany()
        chk("$fromUser @ { .firstName == 'Bill' }.lastName", "text[Gates]")
        chk("$fromUser @ { .firstName == 'Mark' }.lastName", "text[Zuckerberg]")
        chk("$fromUser @ { .firstName == 'Bill' }.company.name", "text[Microsoft]")
        chk("$fromUser @ { .firstName == 'Mark' }.company.name", "text[Facebook]")
        chk("$fromUser @ { .firstName == 'Mark' }.user.lastName", "ct_err:expr_attr_unknown:user")
        chk("(u: $fromUser) @ { .firstName == 'Mark' }.user.lastName", "ct_err:expr_attr_unknown:user")
        chk("(u: $fromUser) @ { .firstName == 'Mark' }.u.lastName", "ct_err:expr_attr_unknown:u")
    }

    @Test fun testTupleFieldAccess() {
        initDataUserCompany()
        val base = "val t = $fromUser @ { .firstName == 'Bill' } ( .firstName, .lastName, companyName = .company.name );"
        chkEx("{ $base return t.firstName; }", "text[Bill]")
        chkEx("{ $base return t.lastName; }", "text[Gates]")
        chkEx("{ $base return t.companyName; }", "text[Microsoft]")
        chkEx("{ $base return t.foo; }", "ct_err:unknown_member:[(firstName:text,lastName:text,companyName:text)]:foo")
    }

    @Test fun testLimit() {
        initDataUserCompany()

        chk("$fromUser @* {} limit 0", "list<user>[]")
        chk("$fromUser @* {} limit 1", "list<user>[user[10]]")
        chk("$fromUser @* {} limit 2", "list<user>[user[10],user[20]]")
        chk("$fromUser @* {} limit 3", "list<user>[user[10],user[20],user[21]]")
        chk("$fromUser @* {} limit 4", "list<user>[user[10],user[20],user[21],user[30]]")
        chk("$fromUser @* {} limit 5", "list<user>[user[10],user[20],user[21],user[30],user[40]]")
        chk("$fromUser @* {} limit 6", "list<user>[user[10],user[20],user[21],user[30],user[40],user[41]]")
        chk("$fromUser @* {} limit 7", "list<user>[user[10],user[20],user[21],user[30],user[40],user[41],user[50]]")

        chk("$fromUser @* {} ( .lastName ) limit 0", "list<text>[]")
        chk("$fromUser @* {} ( .lastName ) limit 1", "list<text>[text[Zuckerberg]]")
        chk("$fromUser @* {} ( .lastName ) limit 2", "list<text>[text[Zuckerberg],text[Jobs]]")
        chk("$fromUser @* {} ( .lastName ) limit 3", "list<text>[text[Zuckerberg],text[Jobs],text[Wozniak]]")
        chk("$fromUser @* {} ( .lastName ) limit 4", "list<text>[text[Zuckerberg],text[Jobs],text[Wozniak],text[Bezos]]")

        chk("$fromUser @ {} limit 0", "rt_err:at:wrong_count:0")
        chk("$fromUser @ {} limit 1", "user[10]")
        chk("$fromUser @ {} limit 2", "rt_err:at:wrong_count:2")
        chk("$fromUser @? {} limit 0", "null")
        chk("$fromUser @? {} limit 1", "user[10]")
        chk("$fromUser @? {} limit 2", "rt_err:at:wrong_count:2")

        chk("$fromUser @* {} limit -1", "rt_err:expr:at:limit:negative:-1")
        chk("$fromUser @* {} limit -999999", "rt_err:expr:at:limit:negative:-999999")

        chk("$fromUser @ {} limit 'Hello'", "ct_err:expr_at_limit_type:[integer]:[text]")
    }

    @Test fun testOffset() {
        initDataUserCompany()

        chk("$fromUser @* {}", "list<user>[user[10],user[20],user[21],user[30],user[40],user[41],user[50],user[51]]")
        chk("$fromUser @* {} offset 0", "list<user>[user[10],user[20],user[21],user[30],user[40],user[41],user[50],user[51]]")
        chk("$fromUser @* {} offset 1", "list<user>[user[20],user[21],user[30],user[40],user[41],user[50],user[51]]")
        chk("$fromUser @* {} offset 2", "list<user>[user[21],user[30],user[40],user[41],user[50],user[51]]")
        chk("$fromUser @* {} offset 3", "list<user>[user[30],user[40],user[41],user[50],user[51]]")
        chk("$fromUser @* {} offset 4", "list<user>[user[40],user[41],user[50],user[51]]")
        chk("$fromUser @* {} offset 5", "list<user>[user[41],user[50],user[51]]")
        chk("$fromUser @* {} offset 6", "list<user>[user[50],user[51]]")
        chk("$fromUser @* {} offset 7", "list<user>[user[51]]")
        chk("$fromUser @* {} offset 8", "list<user>[]")
        chk("$fromUser @* {} offset 9", "list<user>[]")

        chk("$fromUser @* {} offset -1", "rt_err:expr:at:offset:negative:-1")
        chk("$fromUser @* {} offset -99999999", "rt_err:expr:at:offset:negative:-99999999")
        chk("$fromUser @* {} offset 999999999", "list<user>[]")

        chk("$fromUser @ {} offset 'Hello'", "ct_err:expr_at_offset_type:[integer]:[text]")
    }

    @Test fun testLimitOffset() {
        initDataUserCompany()

        chk("$fromUser @? {} limit 1 offset 0", "user[10]")
        chk("$fromUser @? {} limit 1 offset 1", "user[20]")
        chk("$fromUser @? {} limit 1 offset 2", "user[21]")
        chk("$fromUser @? {} limit 1 offset 3", "user[30]")
        chk("$fromUser @? {} limit 1 offset 4", "user[40]")
        chk("$fromUser @? {} limit 1 offset 5", "user[41]")
        chk("$fromUser @? {} limit 1 offset 6", "user[50]")
        chk("$fromUser @? {} limit 1 offset 7", "user[51]")
        chk("$fromUser @? {} limit 1 offset 8", "null")

        chk("$fromUser @* {} limit 3 offset 0", "list<user>[user[10],user[20],user[21]]")
        chk("$fromUser @* {} limit 3 offset 1", "list<user>[user[20],user[21],user[30]]")
        chk("$fromUser @* {} limit 3 offset 2", "list<user>[user[21],user[30],user[40]]")
        chk("$fromUser @* {} limit 3 offset 3", "list<user>[user[30],user[40],user[41]]")
        chk("$fromUser @* {} limit 3 offset 4", "list<user>[user[40],user[41],user[50]]")
        chk("$fromUser @* {} limit 3 offset 5", "list<user>[user[41],user[50],user[51]]")
        chk("$fromUser @* {} limit 3 offset 6", "list<user>[user[50],user[51]]")
        chk("$fromUser @* {} limit 3 offset 7", "list<user>[user[51]]")
        chk("$fromUser @* {} limit 3 offset 8", "list<user>[]")

        chk("$fromUser @? {} offset 3 limit 1", "user[30]")
        chk("$fromUser @* {} offset 6 limit 3", "list<user>[user[50],user[51]]")
    }

    @Test fun testCardinalityOne() {
        initDataUserCompany()
        chk("$fromUser @ { .firstName == 'Chuck' }", "rt_err:at:wrong_count:0")
        chk("$fromUser @ { .firstName == 'Bill' }", "user[40]")
        chk("$fromUser @? { .firstName == 'Chuck' }", "null")
        chk("$fromUser @? { .firstName == 'Bill' }", "user[40]")
        chk("$fromUser @? { .firstName == 'Steve' }", "rt_err:at:wrong_count:2")
    }

    @Test fun testCardinalityMany() {
        initDataUserCompany()
        chk("$fromUser @* { .firstName == 'Chuck' }", "list<user>[]")
        chk("$fromUser @* { .firstName == 'Bill' }", "list<user>[user[40]]")
        chk("$fromUser @+ { .firstName == 'Chuck' }", "rt_err:at:wrong_count:0")
        chk("$fromUser @+ { .firstName == 'Bill' }", "list<user>[user[40]]")
        chk("$fromUser @+ { .firstName == 'Steve' }", "list<user>[user[20],user[21]]")
    }

    @Test fun testSort() {
        tst.strictToString = false
        initDataUserCompany()

        chk("$fromUser @* {} ( .firstName )", "[Mark, Steve, Steve, Jeff, Bill, Paul, Sergey, Larry]")
        chk("$fromUser @* {} ( @sort .firstName )", "[Bill, Jeff, Larry, Mark, Paul, Sergey, Steve, Steve]")
        chk("$fromUser @* {} ( @sort_desc .firstName )", "[Steve, Steve, Sergey, Paul, Mark, Larry, Jeff, Bill]")

        chk("$fromUser @* { .company.name == 'Apple' } ( @sort _=.firstName, @sort _=.lastName )", "[(Steve,Jobs), (Steve,Wozniak)]")
        chk("$fromUser @* { .company.name == 'Apple' } ( @sort _=.firstName, @sort_desc _=.lastName )", "[(Steve,Wozniak), (Steve,Jobs)]")

        chk("$fromUser @* {} ( @sort _=.company.name, _=.lastName )",
                "[(Amazon,Bezos), (Apple,Jobs), (Apple,Wozniak), (Facebook,Zuckerberg), (Google,Brin), (Google,Page), " +
                        "(Microsoft,Gates), (Microsoft,Allen)]")

        chk("$fromUser @* {} ( @sort _=.company.name, @sort _=.lastName )",
                "[(Amazon,Bezos), (Apple,Jobs), (Apple,Wozniak), (Facebook,Zuckerberg), (Google,Brin), (Google,Page), " +
                        "(Microsoft,Allen), (Microsoft,Gates)]")

        chk("(u: $fromUser) @* {} ( @sort_desc u )",
                "[user[51], user[50], user[41], user[40], user[30], user[21], user[20], user[10]]")

        chk("(u: $fromUser) @* {} ( @sort_desc _=.company, _=u )",
                "[(company[500],user[50]), (company[500],user[51]), (company[400],user[40]), (company[400],user[41]), " +
                        "(company[300],user[30]), (company[200],user[20]), (company[200],user[21]), (company[100],user[10])]")
    }

    @Test fun testSortAnnotation() {
        tst.strictToString = false
        initDataUserCompany()

        chk("$fromUser @* {} ( @sort @sort .firstName )", "ct_err:modifier:dup:ann:sort")
        chk("$fromUser @* {} ( @sort_desc @sort_desc .firstName )", "ct_err:modifier:dup:ann:sort_desc")
        chk("$fromUser @* {} ( @sort @sort_desc .firstName )", "ct_err:modifier:bad_combination:ann:sort,ann:sort_desc")
        chk("$fromUser @* {} ( @sort_desc @sort .firstName )", "ct_err:modifier:bad_combination:ann:sort_desc,ann:sort")

        chk("$fromUser @* {} ( @sort() .firstName )", "[Bill, Jeff, Larry, Mark, Paul, Sergey, Steve, Steve]")
        chk("$fromUser @* {} ( @sort(123) .firstName )", "ct_err:ann:sort:args:1")
        chk("$fromUser @* {} ( @sort('desc') .firstName )", "ct_err:ann:sort:args:1")
    }

    @Test fun testSortLegacy() {
        tst.strictToString = false
        initDataUserCompany()

        tst.compatibilityVer("0.10.9")

        chk("$fromUser @* { .company.name == 'Apple' } ( @sort _=.firstName, sort _=.lastName )", "[(Steve,Jobs), (Steve,Wozniak)]")
        chk("$fromUser @* { .company.name == 'Apple' } ( @sort _=.firstName, -sort _=.lastName )", "[(Steve,Wozniak), (Steve,Jobs)]")

        chk("$fromUser @* {} ( @sort _=.company.name, sort _=.lastName )",
                "[(Amazon,Bezos), (Apple,Jobs), (Apple,Wozniak), (Facebook,Zuckerberg), (Google,Brin), (Google,Page), " +
                        "(Microsoft,Allen), (Microsoft,Gates)]")

        chk("$fromUser @* {} ( .firstName )", "[Mark, Steve, Steve, Jeff, Bill, Paul, Sergey, Larry]")

        chk("$fromUser @* {} ( sort .firstName )", "[Bill, Jeff, Larry, Mark, Paul, Sergey, Steve, Steve]")
        chkWarn("at:what:sort:deprecated:sort")

        chk("$fromUser @* {} ( -sort .firstName )", "[Steve, Steve, Sergey, Paul, Mark, Larry, Jeff, Bill]")
        chkWarn("at:what:sort:deprecated:sort_desc")

        chk("$fromUser @* {} ( @sort sort .firstName )", "ct_err:at:what:sort:specified_by_kw_and_ann")
        chk("$fromUser @* {} ( @sort_desc sort .firstName )", "ct_err:at:what:sort:specified_by_kw_and_ann")
        chk("$fromUser @* {} ( @sort -sort .firstName )", "ct_err:at:what:sort:specified_by_kw_and_ann")
        chk("$fromUser @* {} ( @sort_desc -sort .firstName )", "ct_err:at:what:sort:specified_by_kw_and_ann")
    }

    @Test fun testSortLegacyVersionControl() {
        tst.strictToString = false
        initDataUserCompany()

        chkSortLegacy("@sort", "@sort_desc")
        chkSortLegacyErr()

        tst.compatibilityVer("0.10.10")
        chkSortLegacy("@sort", "@sort_desc")
        chkSortLegacyErr()

        tst.compatibilityVer("0.10.9")
        chkSortLegacy("@sort", "@sort_desc")
        chkSortLegacy("sort", "-sort")
    }

    private fun chkSortLegacy(asc: String, desc: String) {
        chk("$fromUser @* {} ( .firstName )", "[Mark, Steve, Steve, Jeff, Bill, Paul, Sergey, Larry]")
        chk("$fromUser @* {} ( $asc .firstName )", "[Bill, Jeff, Larry, Mark, Paul, Sergey, Steve, Steve]")
        chk("$fromUser @* {} ( $desc .firstName )", "[Steve, Steve, Sergey, Paul, Mark, Larry, Jeff, Bill]")
        chk("$fromUser @* { .company.name == 'Apple' } ( $asc _=.firstName, $asc _=.lastName )", "[(Steve,Jobs), (Steve,Wozniak)]")
        chk("$fromUser @* { .company.name == 'Apple' } ( $asc _=.firstName, $desc _=.lastName )", "[(Steve,Wozniak), (Steve,Jobs)]")
    }

    private fun chkSortLegacyErr() {
        chk("$fromUser @* {} ( sort .firstName )", "ct_err:unknown_name:sort")
        chk("$fromUser @* {} ( -sort .firstName )", "ct_err:unknown_name:sort")
    }

    @Test fun testNullLiteral() {
        initDataUserCompany()
        chk("$fromUser @ { .firstName == 'Bill' } (_=.lastName, ''+null)", "(text[Gates],text[null])")
        //chk("user @ { firstName = 'Bill' } (lastName, null)", "(text[Gates],null)")
    }

    @Test fun testSubscriptExpr() {
        initDataUserCompany()
        chk("$fromUser @ { .firstName == 'Bill' } (_=.lastName, 'Hello'[1])", "(text[Gates],text[e])")
        chk("$fromUser @ { .firstName == 'Bill' } (.lastName[2])", "text[t]")
        chk("$fromUser @ { .firstName == 'Bill' } ('HelloWorld'[.lastName.size()])", "text[W]")
    }

    @Test fun testMultiLocalWhere() {
        initDataUserCompany()
        chkEx("{ val x = 123; return $fromUser @ { .firstName == 'Bill', x > 10, x > 20, x > 30 }; }", "user[40]")
    }

    @Test fun testOrBooleanCondition() {
        initDataUserCompany()

        chkEx("{ return $fromUser @* { .firstName == 'Bill' }; }", "list<user>[user[40]]")
        chkEx("{ return $fromUser @* { .firstName == 'Bob' }; }", "list<user>[]")

        chkEx("{ val anyUser = 'no'; return $fromUser @* { .firstName == 'Bob' or anyUser == 'yes' }; }", "list<user>[]")
        chkEx("{ val anyUser = 'yes'; return $fromUser @* { .firstName == 'Bob' or anyUser == 'yes' }; }",
                "list<user>[user[10],user[20],user[21],user[30],user[40],user[41],user[50],user[51]]")
        chkEx("{ val anyUser = 'yes'; return $fromUser @* { .firstName == 'Bob' or anyUser == 'yes', .company.name != 'Foo' }; }",
                "list<user>[user[10],user[20],user[21],user[30],user[40],user[41],user[50],user[51]]")

        chkEx("{ val userName = 'Bill'; return $fromUser @* { .firstName == userName or userName == '' }; }", "list<user>[user[40]]")
        chkEx("{ val userName = 'Bob'; return $fromUser @* { .firstName == userName or userName == '' }; }", "list<user>[]")
        chkEx("{ val userName = ''; return $fromUser @* { .firstName == userName or userName == '' }; }",
                "list<user>[user[10],user[20],user[21],user[30],user[40],user[41],user[50],user[51]]")
    }

    @Test fun testSubscript() {
        initDataUserCompany()
        chk("$fromUser @* { .firstName == 'Bill' }[0]", "user[40]")
    }

    @Test fun testIndependentEntityFieldExpression() {
        initDataUserCompany()
        val code = """{
            val u = $fromUser @ { .firstName == 'Paul' };
            return $fromUser @ { .company == u.company, .firstName != 'Paul' } ( .firstName );
        }"""
        chkEx(code, "text[Bill]")
    }

    @Test fun testSortOmit() {
        tst.strictToString = false
        initDataUserCompany()

        chk("$fromUser @* { .company.name == 'Microsoft' } ( _ = .firstName, _ = .lastName )", "[(Bill,Gates), (Paul,Allen)]")
        chk("$fromUser @* { .company.name == 'Microsoft' } ( @omit @sort _ = .firstName, _ = .lastName )", "[Gates, Allen]")
        chk("$fromUser @* { .company.name == 'Microsoft' } ( @omit @sort_desc _ = .firstName, _ = .lastName )", "[Allen, Gates]")
        chk("$fromUser @* { .company.name == 'Microsoft' } ( _ = .firstName, @omit @sort _ = .lastName )", "[Paul, Bill]")
        chk("$fromUser @* { .company.name == 'Microsoft' } ( _ = .firstName, @omit @sort_desc _ = .lastName )", "[Bill, Paul]")
    }

    @Test fun testRowid() {
        tst.strictToString = false
        initDataUserCompany()

        chk("$fromUser @* { .lastName == 'Jobs' } ( .rowid )", "[20]")
        chk("$fromUser @* { .lastName == 'Jobs' } ( .rowid, .firstName )", "[(rowid=20,firstName=Steve)]")
        chk("$fromUser @* { .lastName == 'Jobs' } ( .company.rowid )", "[200]")
        chk("$fromUser @* { .lastName == 'Jobs' } ( .company.rowid, .firstName )", "[(200,firstName=Steve)]")
        chk("(u:$fromUser) @* { .lastName == 'Jobs' } ( u.rowid )", "[20]")
        chk("(u:$fromUser) @* { .lastName == 'Jobs' } ( u.rowid, u.firstName )", "[(rowid=20,firstName=Steve)]")
        chk("(u:$fromUser) @* { .lastName == 'Jobs' } ( u.company.rowid )", "[200]")
        chk("(u:$fromUser) @* { .lastName == 'Jobs' } ( u.company.rowid, u.firstName )", "[(200,firstName=Steve)]")
    }

    @Test fun testFunctionCallInWhere() {
        tst.strictToString = false
        initDataUserCompany()

        chk("$fromUser @* { .firstName.starts_with('S') } ( .rowid )", "[20, 21, 50]")
        chk("$fromUser @* { .lastName.starts_with('B') } ( .rowid )", "[30, 50]")
        chk("$fromUser @* { .firstName.size() < .lastName.size() } ( .rowid )", "[10, 21, 30, 40, 41]")

        chk("$fromUser @* { min(.firstName.size(), .lastName.size()) == 5 } ( .rowid )", "[21]")
        chk("$fromUser @* { max(.firstName.size(), .lastName.size()) == 5 } ( .rowid )", "[20, 30, 40, 41, 51]")
    }

    private object Ins {
        fun company(id: Int, name: String): String = SqlTestUtils.mkins("c0.company", "name", "$id, '$name'")

        fun user(id: Int, companyId: Int, firstName: String, lastName: String): String =
                SqlTestUtils.mkins("c0.user", "firstName,lastName,company", "$id, '$firstName', '$lastName', $companyId")
    }
}
