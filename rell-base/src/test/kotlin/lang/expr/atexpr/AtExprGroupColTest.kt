/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.expr.atexpr

import net.postchain.rell.base.lang.type.DecimalTest
import kotlin.test.Test

class AtExprGroupColTest: AtExprGroupBaseTest() {
    override fun impKind() = AtExprTestKind_Col_Struct()

    @Test fun testDefaultSorting() {
        initDataCountries()
        val from = impFrom("data")

        chk("$from @* {} ( .name )", "[Germany, Austria, United Kingdom, USA, Mexico, China]")
        chk("$from @* {} ( @group .name )", "[Germany, Austria, United Kingdom, USA, Mexico, China]")
        chk("$from @* {} ( @group .region )", "[EMEA, AMER, APAC]")
        chk("$from @* {} ( @group .language )", "[German, English, Spanish, Chinese]")

        chk("$from @* {} ( @group _=.region, @group _=.language )",
                "[(EMEA,German), (EMEA,English), (AMER,English), (AMER,Spanish), (APAC,Chinese)]")
        chk("$from @* {} ( @group _=.language, @group _=.region )",
                "[(German,EMEA), (English,EMEA), (English,AMER), (Spanish,AMER), (Chinese,APAC)]")
    }

    @Test override fun testSumOverflowInteger() {
        super.testSumOverflowInteger()
        chkTypeSum("integer", "9223372036854775807 1 -1", impRtErr("expr:+:overflow:9223372036854775807:1"))
        chkTypeSum("integer", "-9223372036854775807-1 -1 1", impRtErr("expr:+:overflow:-9223372036854775808:-1"))
    }

    @Test override fun testSumOverflowDecimal() {
        super.testSumOverflowDecimal()
        val dv = DecimalTest.DecVals()
        chkTypeSum("decimal", "decimal('${dv.lim1}') 1.0 -1.0", impRtErr("expr:+:overflow"))
    }

    @Test override fun testTypeGroup() {
        super.testTypeGroup()

        def("struct const_struct { q: integer; }")

        chkTypeGroup("(integer,text)", "(123,'hi')", "(int[123],text[hi])")
        chkTypeGroup("const_struct", "const_struct(123)", "const_struct[q=int[123]]")

        chkTypeGroupNullable("boolean", "true", "boolean[true]")
        chkTypeGroupNullable("integer", "123", "int[123]")
        chkTypeGroupNullable("decimal", "123.456", "dec[123.456]")
        chkTypeGroupNullable("text", "'hi'", "text[hi]")
        chkTypeGroupNullable("byte_array", "x'beef'", "byte_array[beef]")
        chkTypeGroupNullable("rowid", "rowid(123)", "rowid[123]")
        chkTypeGroupNullable("color", "color.green", "color[green]")
        chkTypeGroupNullable("const_struct", "const_struct(123)", "const_struct[q=int[123]]")
    }

    private fun chkTypeGroupNullable(type: String, value: String, exp: String) {
        chkTypeGroup("$type?", value, exp)
        chkTypeGroup("$type?", "null", "null")
    }

    @Test override fun testTypeGroupFormal() {
        super.testTypeGroupFormal()

        def("struct const_struct { q: integer; }")
        def("struct mut_struct { mutable q: integer; }")

        chkTypeGroupFormal("boolean?", "text[boolean?]")
        chkTypeGroupFormal("integer?", "text[integer?]")
        chkTypeGroupFormal("decimal?", "text[decimal?]")
        chkTypeGroupFormal("text?", "text[text?]")
        chkTypeGroupFormal("byte_array?", "text[byte_array?]")
        chkTypeGroupFormal("rowid?", "text[rowid?]")
        chkTypeGroupFormal("color?", "text[color?]")
        chkTypeGroupFormal("const_struct?", "text[const_struct?]")

        chkTypeGroupFormal("(integer,text)", "text[(integer,text)]")

        chkTypeGroupFormal("mut_struct", "ct_err:expr_at_group_type:mut_struct")
        chkTypeGroupFormal("list<integer>", "ct_err:expr_at_group_type:list<integer>")
        chkTypeGroupFormal("set<integer>", "ct_err:expr_at_group_type:set<integer>")
        chkTypeGroupFormal("map<integer,text>", "ct_err:expr_at_group_type:map<integer,text>")
    }

    @Test override fun testTypeSum() {
        super.testTypeSum()

        def("struct address { line: text; }")

        chkTypeSum("address", "", "ct_err:at:what:aggr:bad_type:SUM:address")
        chkTypeSum("range", "", "ct_err:at:what:aggr:bad_type:SUM:range")
        chkTypeSum("(integer,text)", "", "ct_err:at:what:aggr:bad_type:SUM:(integer,text)")
        chkTypeSum("list<integer>", "", "ct_err:at:what:aggr:bad_type:SUM:list<integer>")
        chkTypeSum("set<integer>", "", "ct_err:at:what:aggr:bad_type:SUM:set<integer>")
        chkTypeSum("map<integer,text>", "", "ct_err:at:what:aggr:bad_type:SUM:map<integer,text>")

        chkTypeSum("integer?", "", "ct_err:at:what:aggr:bad_type:SUM:integer?")
        chkTypeSum("decimal?", "", "ct_err:at:what:aggr:bad_type:SUM:decimal?")
        chkTypeSum("boolean?", "", "ct_err:at:what:aggr:bad_type:SUM:boolean?")
        chkTypeSum("text?", "", "ct_err:at:what:aggr:bad_type:SUM:text?")
    }

    @Test override fun testTypeMinMax() {
        super.testTypeMinMax()

        def("struct address { line: text; }")

        chkTypeMinMaxOK("boolean", "false true", "boolean[false]", "boolean[true]")
        chkTypeMinMaxOK("byte_array", "x'1234' x'abcd'", "byte_array[1234]", "byte_array[abcd]")
        chkTypeMinMaxOK("range", "range(123) range(456)", "range[0,123,1]", "range[0,456,1]")
        chkTypeMinMaxOK("(integer,text)", "(123,'Bob') (456,'Alice')", "(int[123],text[Bob])", "(int[456],text[Alice])")
        chkTypeMinMaxOK("list<integer>", "[123] [456]", "list<integer>[int[123]]", "list<integer>[int[456]]")

        chkTypeMinMaxErr("address")
        chkTypeMinMaxErr("set<integer>")
        chkTypeMinMaxErr("map<integer,text>")

        chkTypeMinMaxErr("integer?")
        chkTypeMinMaxErr("decimal?")
        chkTypeMinMaxErr("text?")
        chkTypeMinMaxErr("rowid?")
        chkTypeMinMaxErr("color?")
        chkTypeMinMaxErr("user?")
    }

    @Test override fun testTypeMinMaxFormal() {
        super.testTypeMinMaxFormal()

        chkTypeMinMaxFormal("boolean")
        chkTypeMinMaxFormal("byte_array")
    }
}
