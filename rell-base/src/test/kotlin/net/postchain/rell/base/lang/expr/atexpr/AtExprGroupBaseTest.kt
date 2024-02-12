/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.expr.atexpr

import net.postchain.rell.base.lang.type.BigIntegerTest
import net.postchain.rell.base.lang.type.DecimalTest
import net.postchain.rell.base.testutils.RellCodeTester
import org.junit.Test

abstract class AtExprGroupBaseTest: AtExprBaseTest() {
    private val fromData = impFrom("data")

    protected fun initDataCountries() {
        tst.strictToString = false
        impDefType("data", "name", "region:text", "language:text", "gdp:integer")
        impCreateObjs("data",
                "name = 'Germany', region = 'EMEA', language = 'German', gdp = 3863",
                "name = 'Austria', region = 'EMEA', language = 'German', gdp = 447",
                "name = 'United Kingdom', region = 'EMEA', language = 'English', gdp = 2743",
                "name = 'USA', region = 'AMER', language = 'English', gdp = 21439",
                "name = 'Mexico', region = 'AMER', language = 'Spanish', gdp = 1274",
                "name = 'China', region = 'APAC', language = 'Chinese', gdp = 14140"
        )
    }

    @Test fun testGroupConstant() {
        initDataCountries()
        chk("$fromData @* {} ( @group 123 )", "[123]")
        chk("$fromData @* {} ( @group 123, .region )", "ct_err:at:what:no_aggr:1")
        chk("$fromData @* {} ( @group 123, @sum 1 )", "[(123,6)]")
    }

    @Test fun testGroupSimple() {
        initDataCountries()
        chk("$fromData @* {} ( @sort .region )", "[AMER, AMER, APAC, EMEA, EMEA, EMEA]")
        chk("$fromData @* {} ( @sort @group .region )", "[AMER, APAC, EMEA]")
        chk("$fromData @* {} ( @sort .language )", "[Chinese, English, English, German, German, Spanish]")
        chk("$fromData @* {} ( @sort @group .language )", "[Chinese, English, German, Spanish]")
    }

    @Test fun testGroupMulti() {
        initDataCountries()
        chk("$fromData @* {} ( @sort @group _=.region, @sort @group _=.language )",
                "[(AMER,English), (AMER,Spanish), (APAC,Chinese), (EMEA,English), (EMEA,German)]")
        chk("$fromData @* {} ( @sort @group _=.language, @sort @group _=.region )",
                "[(Chinese,APAC), (English,AMER), (English,EMEA), (German,EMEA), (Spanish,AMER)]")
    }

    @Test fun testGroupSort() {
        initDataCountries()
        chk("$fromData @* {} ( @sort @group .region )", "[AMER, APAC, EMEA]")
        chk("$fromData @* {} ( @sort_desc @group .region )", "[EMEA, APAC, AMER]")
        chk("$fromData @* {} ( @sort @group .language )", "[Chinese, English, German, Spanish]")
        chk("$fromData @* {} ( @sort_desc @group .language )", "[Spanish, German, English, Chinese]")
    }

    @Test fun testGroupSortMulti() {
        initDataCountries()

        chk("$fromData @* {} ( @sort @group _=.region, @sort @group _=.language )",
                "[(AMER,English), (AMER,Spanish), (APAC,Chinese), (EMEA,English), (EMEA,German)]")

        chk("$fromData @* {} ( @sort_desc @group _=.region, @sort @group _=.language )",
                "[(EMEA,English), (EMEA,German), (APAC,Chinese), (AMER,English), (AMER,Spanish)]")

        chk("$fromData @* {} ( @sort_desc @group _=.language, @sort @group _=.region )",
                "[(Spanish,AMER), (German,EMEA), (English,AMER), (English,EMEA), (Chinese,APAC)]")

        chk("$fromData @* {} ( @sort_desc @group _=.region, @sort_desc @group _=.language )",
                "[(EMEA,German), (EMEA,English), (APAC,Chinese), (AMER,Spanish), (AMER,English)]")

        chk("$fromData @* {} ( @sort_desc @group _=.language, @sort @group _=.region )",
                "[(Spanish,AMER), (German,EMEA), (English,AMER), (English,EMEA), (Chinese,APAC)]")

        chk("$fromData @* {} ( @sort_desc @group _=.language, @sort_desc @group _=.region )",
                "[(Spanish,AMER), (German,EMEA), (English,EMEA), (English,AMER), (Chinese,APAC)]")
    }

    @Test fun testCount() {
        initDataCountries()
        chk("$fromData @* {} ( @sort @group _=.region, @sum 1 )", "[(AMER,2), (APAC,1), (EMEA,3)]")
        chk("$fromData @* {} ( @group _=.region, @sort @sum 1 )", "[(APAC,1), (AMER,2), (EMEA,3)]")
        chk("$fromData @* {} ( @group _=.region, @sort_desc @sum 1 )", "[(EMEA,3), (AMER,2), (APAC,1)]")
        chk("$fromData @* { .gdp > 2000 } ( @sort @group _=.region, @sum 1 )", "[(AMER,1), (APAC,1), (EMEA,2)]")
    }

    @Test fun testSum() {
        initDataCountries()

        chk("$fromData @* {} ( @sort @group _=.region, @sum 0 )", "[(AMER,0), (APAC,0), (EMEA,0)]")
        chk("$fromData @* {} ( @sort @group _=.region, @sum 1 )", "[(AMER,2), (APAC,1), (EMEA,3)]")
        chk("$fromData @* {} ( @sort @group _=.region, @sum .gdp )", "[(AMER,22713), (APAC,14140), (EMEA,7053)]")
        chk("$fromData @* {} ( @sort @group _=.region, @sum .name.size() )", "[(AMER,9), (APAC,5), (EMEA,28)]")

        chk("$fromData @* {} ( @group _=.region, @sort @sum .gdp )", "[(EMEA,7053), (APAC,14140), (AMER,22713)]")
        chk("$fromData @* {} ( @group _=.region, @sort_desc @sum .gdp )", "[(AMER,22713), (APAC,14140), (EMEA,7053)]")

        chk("$fromData @* { .gdp > 2000 } ( @sort @group _=.region, @sum .gdp )", "[(AMER,21439), (APAC,14140), (EMEA,6606)]")
    }

    @Test fun testMinMax() {
        initDataCountries()

        chk("$fromData @* {} ( @sort @group _=.region, @min 1 )", "[(AMER,1), (APAC,1), (EMEA,1)]")
        chk("$fromData @* {} ( @sort @group _=.region, @max 1 )", "[(AMER,1), (APAC,1), (EMEA,1)]")

        chk("$fromData @* {} ( @sort @group _=.region, @min .gdp )", "[(AMER,1274), (APAC,14140), (EMEA,447)]")
        chk("$fromData @* {} ( @sort @group _=.region, @max .gdp )", "[(AMER,21439), (APAC,14140), (EMEA,3863)]")

        chk("$fromData @* {} ( @sort @group _=.region, @min .name.size() )", "[(AMER,3), (APAC,5), (EMEA,7)]")
        chk("$fromData @* {} ( @sort @group _=.region, @max .name.size() )", "[(AMER,6), (APAC,5), (EMEA,14)]")

        chk("$fromData @* {} ( @group _=.region, @sort @min .gdp )", "[(EMEA,447), (AMER,1274), (APAC,14140)]")
        chk("$fromData @* {} ( @group _=.region, @sort @max .gdp )", "[(EMEA,3863), (APAC,14140), (AMER,21439)]")

        chk("$fromData @* {} ( @group _=.region, @sort_desc @min .gdp )", "[(APAC,14140), (AMER,1274), (EMEA,447)]")
        chk("$fromData @* {} ( @group _=.region, @sort_desc @max .gdp )", "[(AMER,21439), (APAC,14140), (EMEA,3863)]")

        chk("$fromData @* { .gdp > 2000 } ( @sort @group _=.region, @min .gdp )", "[(AMER,21439), (APAC,14140), (EMEA,2743)]")
        chk("$fromData @* { .gdp < 2000 } ( @sort @group _=.region, @max .gdp )", "[(AMER,1274), (EMEA,447)]")
    }

    @Test fun testCountSumMinMax() {
        initDataCountries()
        chk("$fromData @* {} ( @sort @group _=.region, @sum 1, @sum .gdp, @min .gdp, @max .gdp )",
                "[(AMER,2,22713,1274,21439), (APAC,1,14140,14140,14140), (EMEA,3,7053,447,3863)]")
    }

    @Test fun testOmit() {
        initDataCountries()

        chk("$fromData @* {} ( @sort @group _=.region, @sum .gdp, @min .gdp, @max .gdp )",
                "[(AMER,22713,1274,21439), (APAC,14140,14140,14140), (EMEA,7053,447,3863)]")

        chk("$fromData @* {} ( @omit @sort @group _=.region, @sum .gdp, @min .gdp, @max .gdp )",
                "[(22713,1274,21439), (14140,14140,14140), (7053,447,3863)]")

        chk("$fromData @* {} ( @sort @group _=.region, @omit @sum .gdp, @min .gdp, @max .gdp )",
                "[(AMER,1274,21439), (APAC,14140,14140), (EMEA,447,3863)]")

        chk("$fromData @* {} ( @omit @sort @group _=.region, @omit @sum .gdp, @min .gdp, @max .gdp )",
                "[(1274,21439), (14140,14140), (447,3863)]")

        chk("$fromData @* {} ( @sort @group _=.region, @omit @sum .gdp, @omit @min .gdp, @omit @max .gdp )", "[AMER, APAC, EMEA]")

        chk("$fromData @* {} ( @omit @sort @group _=.region, @omit @sum .gdp, @omit @min .gdp, @omit @max .gdp )", "ct_err:at:no_fields")
    }

    @Test fun testAnnotationConflicts() {
        initDataCountries()
        chk("$fromData @* {} ( @group @group _=.region )", "ct_err:modifier:dup:ann:group")
        chk("$fromData @* {} ( @group @max _=.region )", "ct_err:modifier:bad_combination:ann:group,ann:max")
        chk("$fromData @* {} ( @min @max _=.region )", "ct_err:modifier:bad_combination:ann:min,ann:max")
        chk("$fromData @* {} ( @sort @group _=.region )", "[AMER, APAC, EMEA]")
        chk("$fromData @* {} ( @min _=.region )", "[AMER]")
    }

    @Test fun testAnnotationsOnWrongTargets() {
        chkCompile("@group entity user {}", "ct_err:modifier:invalid:ann:group")
        chkCompile("@min entity user {}", "ct_err:modifier:invalid:ann:min")
        chkCompile("@max entity user {}", "ct_err:modifier:invalid:ann:max")
        chkCompile("@sum entity user {}", "ct_err:modifier:invalid:ann:sum")

        chkCompile("@group namespace ns {}", "ct_err:modifier:invalid:ann:group")
        chkCompile("@min namespace ns {}", "ct_err:modifier:invalid:ann:min")
        chkCompile("@max namespace ns {}", "ct_err:modifier:invalid:ann:max")
        chkCompile("@sum namespace ns {}", "ct_err:modifier:invalid:ann:sum")
    }

    @Test fun testTupleFieldNames() {
        initDataCountries()

        chk("$fromData @* {} ( @sort @group .region, @sum country_count = 1, @sum total_gdp = .gdp )", "[" +
                "(region=AMER,country_count=2,total_gdp=22713), " +
                "(region=APAC,country_count=1,total_gdp=14140), " +
                "(region=EMEA,country_count=3,total_gdp=7053)" +
                "]")

        chk("$fromData @* {} ( @sort @group .region, @sum .gdp )", "[" +
                "(region=AMER,22713), " +
                "(region=APAC,14140), " +
                "(region=EMEA,7053)" +
                "]")

        chk("$fromData @ {} ( @sum 1 )", "6")
        chk("$fromData @ {} ( @sum .gdp )", "43906")
        chk("$fromData @ {} ( @sum 1, @sum .gdp )", "(6,43906)")
        chk("$fromData @ {} ( @sum .gdp, @min .gdp, @max .gdp )", "(43906,447,21439)")
    }

    @Test fun testNoGroup() {
        initDataCountries()

        chk("_type_of($fromData @* {} ( @sum .gdp ))", "list<integer>")
        chk("_type_of($fromData @* {} ( @min .gdp ))", "list<integer?>")
        chk("_type_of($fromData @* {} ( @max .gdp ))", "list<integer?>")
        chk("_type_of($fromData @* {} ( @min .gdp, @max .gdp ))", "list<(integer?,integer?)>")
        chk("_type_of($fromData @* {} ( @sum .gdp, @min .gdp, @max .gdp ))", "list<(integer,integer?,integer?)>")

        chk("_type_of($fromData @+ {} ( @sum .gdp ))", "list<integer>")
        chk("_type_of($fromData @+ {} ( @min .gdp ))", "list<integer?>")
        chk("_type_of($fromData @+ {} ( @max .gdp ))", "list<integer?>")
        chk("_type_of($fromData @+ {} ( @min .gdp, @max .gdp ))", "list<(integer?,integer?)>")
        chk("_type_of($fromData @+ {} ( @sum .gdp, @min .gdp, @max .gdp ))", "list<(integer,integer?,integer?)>")

        chk("_type_of($fromData @ {} ( @sum .gdp ))", "integer")
        chk("_type_of($fromData @ {} ( @min .gdp ))", "integer?")
        chk("_type_of($fromData @ {} ( @max .gdp ))", "integer?")
        chk("_type_of($fromData @ {} ( @min .gdp, @max .gdp ))", "(integer?,integer?)")
        chk("_type_of($fromData @ {} ( @sum .gdp, @min .gdp, @max .gdp ))", "(integer,integer?,integer?)")

        chk("_type_of($fromData @? {} ( @sum .gdp ))", "integer?")
        chk("_type_of($fromData @? {} ( @min .gdp ))", "integer?")
        chk("_type_of($fromData @? {} ( @max .gdp ))", "integer?")
        chk("_type_of($fromData @? {} ( @min .gdp, @max .gdp ))", "(integer?,integer?)?")
        chk("_type_of($fromData @? {} ( @sum .gdp, @min .gdp, @max .gdp ))", "(integer,integer?,integer?)?")

        chk("$fromData @* {} ( @sum 0 )", "[0]")
        chk("$fromData @* {} ( @sum 1 )", "[6]")
        chk("$fromData @* {} ( @sum .gdp )", "[43906]")
        chk("$fromData @* {} ( @min 0, @max 0 )", "[(0,0)]")
        chk("$fromData @* {} ( @min .gdp, @max .gdp )", "[(447,21439)]")
        chk("$fromData @* {} ( @sum .gdp, @min .gdp, @max .gdp )", "[(43906,447,21439)]")
        chk("$fromData @* { .name == 'X' } ( @sum .gdp, @min .gdp, @max .gdp )", "[(0,null,null)]")

        chk("$fromData @+ {} ( @sum 0 )", "[0]")
        chk("$fromData @+ {} ( @sum 1 )", "[6]")
        chk("$fromData @+ {} ( @sum .gdp )", "[43906]")
        chk("$fromData @+ {} ( @min 0, @max 0 )", "[(0,0)]")
        chk("$fromData @+ {} ( @min .gdp, @max .gdp )", "[(447,21439)]")
        chk("$fromData @+ {} ( @sum .gdp, @min .gdp, @max .gdp )", "[(43906,447,21439)]")
        chk("$fromData @+ { .name == 'X' } ( @sum .gdp, @min .gdp, @max .gdp )", "[(0,null,null)]")

        chk("$fromData @ {} ( @sum 0 )", "0")
        chk("$fromData @ {} ( @sum 1 )", "6")
        chk("$fromData @ {} ( @sum .gdp )", "43906")
        chk("$fromData @ {} ( @min 0, @max 0 )", "(0,0)")
        chk("$fromData @ {} ( @min .gdp, @max .gdp )", "(447,21439)")
        chk("$fromData @ {} ( @sum .gdp, @min .gdp, @max .gdp )", "(43906,447,21439)")
        chk("$fromData @ { .name == 'X' } ( @sum .gdp, @min .gdp, @max .gdp )", "(0,null,null)")

        chk("$fromData @? {} ( @sum 0 )", "0")
        chk("$fromData @? {} ( @sum 1 )", "6")
        chk("$fromData @? {} ( @sum .gdp )", "43906")
        chk("$fromData @? {} ( @min 0, @max 0 )", "(0,0)")
        chk("$fromData @? {} ( @min .gdp, @max .gdp )", "(447,21439)")
        chk("$fromData @? {} ( @sum .gdp, @min .gdp, @max .gdp )", "(43906,447,21439)")
        chk("$fromData @? { .name == 'X' } ( @sum .gdp, @min .gdp, @max .gdp )", "(0,null,null)")

        chk("$fromData @* {} ( @sort @group _=.region, @sum .gdp )", "[(AMER,22713), (APAC,14140), (EMEA,7053)]")

        chk("$fromData @* {} ( _=.name, @sum .gdp )", "ct_err:at:what:no_aggr:0")
        chk("$fromData @* {} ( @sum .gdp, _=.name )", "ct_err:at:what:no_aggr:1")
        chk("$fromData @* {} ( _=.region, @sum .gdp )", "ct_err:at:what:no_aggr:0")
        chk("$fromData @* {} ( @sum .gdp, _=.region )", "ct_err:at:what:no_aggr:1")
        chk("$fromData @* {} ( 123, @sum .gdp )", "ct_err:at:what:no_aggr:0")
        chk("$fromData @* {} ( @sum .gdp, 123 )", "ct_err:at:what:no_aggr:1")

        chkEx("{ val (country_count, total_gdp) = $fromData @{} ( @sum 1, @sum .gdp ); return (country_count, total_gdp); }",
                "(6,43906)")
    }

    @Test fun testNoRecords() {
        initDataCountries()
        tst.strictToString = true

        chk("$fromData @? {} ( @sum 1, @sum .gdp, @min .gdp, @max .gdp )", "(int[6],int[43906],int[447],int[21439])")
        chk("$fromData @? { .region == 'NONE' } ( @group 123, @sum 1, @sum .gdp, @min .gdp, @max .gdp )", "null")
        chk("$fromData @? { .region == 'NONE' } ( @sum 1, @sum .gdp, @min .gdp, @max .gdp )", "(int[0],int[0],null,null)")

        chk("$fromData @? {} ( @sum 1.0, @sum decimal(.gdp), @min decimal(.gdp), @max decimal(.gdp) )",
                "(dec[6],dec[43906],dec[447],dec[21439])")
        chk("$fromData @? { .region == 'NONE' } ( @group 123, @sum 1.0, @sum decimal(.gdp), @min decimal(.gdp), @max decimal(.gdp) )", "null")
        chk("$fromData @? { .region == 'NONE' } ( @sum 1.0, @sum decimal(.gdp), @min decimal(.gdp), @max decimal(.gdp) )",
                "(dec[0],dec[0],null,null)")
    }

    @Test open fun testTypeGroup() {
        def("enum color { red, green, blue }")
        def("entity user { name; }")
        tst.insert("c0.user", "name", "303,'Bob'")

        chkTypeGroup("boolean", "true", "boolean[true]")
        chkTypeGroup("integer", "123", "int[123]")
        chkTypeGroup("decimal", "123.456", "dec[123.456]")
        chkTypeGroup("text", "'Hi'", "text[Hi]")
        chkTypeGroup("byte_array", "x'1c'", "byte_array[1c]")
        chkTypeGroup("rowid", "rowid(123)", "rowid[123]")
        chkTypeGroup("color", "color.red", "color[red]")
        chkTypeGroup("user", "user@{'Bob'}", "user[303]")
    }

    protected fun chkTypeGroup(type: String, value: String, exp: String) {
        chkTypeCommon("$fromData @? {} ( @group .v )", type, value, exp)
    }

    @Test open fun testTypeGroupFormal() {
        def("enum color { red, green, blue }")
        def("entity user { name; }")

        chkTypeGroupFormal("boolean", "text[boolean]")
        chkTypeGroupFormal("integer", "text[integer]")
        chkTypeGroupFormal("decimal", "text[decimal]")
        chkTypeGroupFormal("text", "text[text]")
        chkTypeGroupFormal("byte_array", "text[byte_array]")
        chkTypeGroupFormal("rowid", "text[rowid]")
        chkTypeGroupFormal("color", "text[color]")
        chkTypeGroupFormal("user", "text[user]")
    }

    protected fun chkTypeGroupFormal(type: String, exp: String) {
        chkTypeCommon("_type_of($fromData @ {} ( @group .v ))", type, "", exp)
    }

    @Test open fun testTypeSum() {
        def("enum color { red, green, blue }")
        def("entity user { name; }")

        chkTypeSum("integer", "111 222", "int[333]")
        chkTypeSum("decimal", "67.89 98.76", "dec[166.65]")

        chkTypeSum("integer", "", "int[0]")
        chkTypeSum("decimal", "", "dec[0]")

        chkTypeSum("boolean", "", "ct_err:at:what:aggr:bad_type:SUM:boolean")
        chkTypeSum("text", "", "ct_err:at:what:aggr:bad_type:SUM:text")
        chkTypeSum("byte_array", "", "ct_err:at:what:aggr:bad_type:SUM:byte_array")
        chkTypeSum("rowid", "", "ct_err:at:what:aggr:bad_type:SUM:rowid")
        chkTypeSum("color", "", "ct_err:at:what:aggr:bad_type:SUM:color")
        chkTypeSum("user", "", "ct_err:at:what:aggr:bad_type:SUM:user")
    }

    @Test open fun testSumOverflowInteger() {
        chkTypeSum("integer", "0 9223372036854775807", "int[9223372036854775807]")
        chkTypeSum("integer", "9223372036854775807 0", "int[9223372036854775807]")
        chkTypeSum("integer", "1 9223372036854775807", impRtErr("expr:+:overflow:1:9223372036854775807"))
        chkTypeSum("integer", "9223372036854775807 1", impRtErr("expr:+:overflow:9223372036854775807:1"))
        chkTypeSum("integer", "9223372036854775807 -1 1", "int[9223372036854775807]")
        chkTypeSum("integer", "-1 9223372036854775807 1", "int[9223372036854775807]")

        chkTypeSum("integer", "-9223372036854775807-1", "int[-9223372036854775808]")
        chkTypeSum("integer", "-9223372036854775807-1 -1", impRtErr("expr:+:overflow:-9223372036854775808:-1"))
        chkTypeSum("integer", "-1 -9223372036854775807-1", impRtErr("expr:+:overflow:-1:-9223372036854775808"))
        chkTypeSum("integer", "-9223372036854775807-1 1 -1", "int[-9223372036854775808]")
        chkTypeSum("integer", "1 -9223372036854775807-1 -1", "int[-9223372036854775808]")
    }

    @Test open fun testSumOverflowBigInteger() {
        val v = BigIntegerTest.BigIntVals()
        chkTypeSum("big_integer", "big_integer('${v.lim1}')", "bigint[${v.lim1}]")
        chkTypeSum("big_integer", "big_integer('${v.lim1}') 1L", impRtErr("expr:+:overflow"))
        chkTypeSum("big_integer", "1L big_integer('${v.lim1}')", impRtErr("expr:+:overflow"))
        chkTypeSum("big_integer", "big_integer('${v.lim1}') -1L 1L", "bigint[${v.lim1}]")
        chkTypeSum("big_integer", "-1L big_integer('${v.lim1}') 1L", "bigint[${v.lim1}]")
    }

    @Test open fun testSumOverflowDecimal() {
        val dv = DecimalTest.DecVals()
        chkTypeSum("decimal", "decimal('${dv.lim1}')", "dec[${dv.lim1}]")
        chkTypeSum("decimal", "decimal('${dv.lim1}') 1.0", impRtErr("expr:+:overflow"))
        chkTypeSum("decimal", "1.0 decimal('${dv.lim1}')", impRtErr("expr:+:overflow"))
        chkTypeSum("decimal", "decimal('${dv.lim1}') -1.0 1.0", "dec[${dv.lim1}]")
        chkTypeSum("decimal", "-1.0 decimal('${dv.lim1}') 1.0", "dec[${dv.lim1}]")
    }

    protected fun chkTypeSum(type: String, values: String, exp: String) = chkTypeAggr("sum", type, values, exp)

    @Test open fun testTypeMinMax() {
        def("enum color { red, green, blue }")
        def("entity user { name; }")
        insert("c0.user", "name", "501,'Bob'", "502,'Alice'")

        chkTypeMinMaxOK("integer", "111 222", "int[111]", "int[222]")
        chkTypeMinMaxOK("decimal", "12.34 56.78", "dec[12.34]", "dec[56.78]")
        chkTypeMinMaxOK("text", "'abc' 'xyz'", "text[abc]", "text[xyz]")
        chkTypeMinMaxOK("rowid", "rowid(123) rowid(456)", "rowid[123]", "rowid[456]")
        chkTypeMinMaxOK("color", "color.red color.green", "color[red]", "color[green]")
        chkTypeMinMaxOK("user", "user@{'Bob'} user@{'Alice'}", "user[501]", "user[502]")
    }

    protected fun chkTypeMinMaxOK(type: String, values: String, expMin: String, expMax: String) {
        chkTypeAggr("min", type, values, expMin)
        chkTypeAggr("max", type, values, expMax)
    }

    protected fun chkTypeMinMaxErr(type: String) {
        chkTypeAggr("min", type, "", "ct_err:at:what:aggr:bad_type:MIN:$type")
        chkTypeAggr("max", type, "", "ct_err:at:what:aggr:bad_type:MAX:$type")
    }

    private fun chkTypeAggr(op: String, type: String, values: String, exp: String) {
        chkTypeCommon("$fromData @? {} ( @$op .v )", type, values, exp)
    }

    @Test fun testTypeSumFormal() {
        tst.strictToString = false
        chkTypeAggrFormal("sum", "integer", "integer", "integer")
        chkTypeAggrFormal("sum", "decimal", "decimal", "decimal")
    }

    @Test open fun testTypeMinMaxFormal() {
        tst.strictToString = false
        def("enum color { red, green, blue }")
        def("entity user { name; }")

        chkTypeMinMaxFormal("integer")
        chkTypeMinMaxFormal("decimal")
        chkTypeMinMaxFormal("text")
        chkTypeMinMaxFormal("rowid")
        chkTypeMinMaxFormal("color")
        chkTypeMinMaxFormal("user")
    }

    protected fun chkTypeMinMaxFormal(type: String) {
        chkTypeAggrFormal("min", type, "$type?", type)
        chkTypeAggrFormal("max", type, "$type?", type)
    }

    private fun chkTypeAggrFormal(op: String, type: String, expNoGroup: String, expGroup: String) {
        chkTypeAggrFormal(op, type, false, expNoGroup)
        chkTypeAggrFormal(op, type, true, expGroup)
    }

    private fun chkTypeAggrFormal(op: String, type: String, group: Boolean, exp: String) {
        val groupStr = if (group) "@omit @group 0," else ""
        val code = "_type_of($fromData @ {} ( $groupStr @$op .v ))"
        chkTypeCommon(code, type, "", exp)
    }

    private fun chkTypeCommon(code: String, type: String, values: String, exp: String) {
        val t = RellCodeTester(tstCtx)
        t.strictToString = tst.strictToString
        tst.defs.forEach { t.def(it) }
        t.insert(tst.inserts)
        impKind.impDefType(t, "data", "v:$type")
        val valueList = if (values.isEmpty()) listOf() else values.split(" ").map { "v = $it" }
        impCreateObjs(t, "data", *valueList.toTypedArray())
        t.chk(code, exp)
    }
}
