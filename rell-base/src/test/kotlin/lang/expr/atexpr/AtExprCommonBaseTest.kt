/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.expr.atexpr

import net.postchain.rell.base.compiler.base.core.C_AtAttrShadowing
import kotlin.test.Test

abstract class AtExprCommonBaseTest: AtExprBaseTest() {
    protected val fromUser = impFrom("user")
    protected val fromCompany = impFrom("company")

    init {
        tst.strictToString = false
    }

    protected fun initDataUserCompany() {
        impDefType("company", "name")
        impDefType("user", "id:integer", "name", "pos:text", "employer:company")
        impCreateObjs("company",
                "name = 'Apple'",
                "name = 'Google'"
        )
        impCreateObjs("user",
                "id = 501, name = 'Bob', pos = 'Dev', employer = $fromCompany@{.name=='Apple'}",
                "id = 502, name = 'Alice', pos = 'Tester', employer = $fromCompany@{.name=='Google'}"
        )
    }

    protected fun initDataUserCompanyCity() {
        impDefType("company", "name", "city:text", "company_attr:text")
        impDefType("user", "id:integer", "name", "city:text", "user_attr:text")
        impCreateObjs("company",
                "name = 'Adidas', city = 'London', company_attr = 'C1'",
                "name = 'Reebok', city = 'Paris', company_attr = 'C2'",
                "name = 'Puma', city = 'London', company_attr = 'C3'"
        )
        impCreateObjs("user",
                "id = 501, name = 'Bob', city = 'Paris', user_attr = 'U1'",
                "id = 502, name = 'Alice', city = 'London', user_attr = 'U2'",
                "id = 503, name = 'Trudy', city = 'Berlin', user_attr = 'U3'"
        )
    }

    protected fun initDataTypes() {
        impDefType("id", "s:text")
        impDefType("single", "id", "i:integer", "t:text", "b:boolean")
        impDefType("double", "id", "i1:integer", "i2:integer", "t1:text", "t2:text", "b1:boolean", "b2:boolean")
        impCreateObjs("single",
                "id = ${impNew("id")}(s = 'Bob'), i = 123, t = 'A', b = false",
                "id = ${impNew("id")}(s = 'Alice'), i = 456, t = 'B', b = true"
        )
        impCreateObjs("double",
                "id = ${impNew("id")}(s = 'Bob'), i1 = 123, i2 = 456, t1 = 'A', t2 = 'B', b1 = false, b2 = true",
                "id = ${impNew("id")}(s = 'Alice'), i1 = 654, i2 = 321, t1 = 'C', t2 = 'D', b1 = true, b2 = false"
        )
    }

    @Test fun testNameResolutionAliasVsAttr() {
        initDataUserCompany()
        chk("$fromUser @* { .pos == 'Dev' } ( .name )", "[Bob]")
        chk("(pos: $fromUser) @* { pos == 'Dev' } ( .name )", "ct_err:binop_operand_type:==:[user]:[text]")
        chk("(pos: $fromUser) @* { .pos == 'Dev' } ( .name )", "[Bob]")
        chk("(pos: $fromUser) @* { pos.pos == 'Dev' } ( .name )", "[Bob]")
    }

    @Test fun testNameResolutionAliasVsLocal() {
        initDataUserCompany()
        chkEx("{ val u = 'Dev'; return $fromUser @* { .pos == u } ( .name ); }", "[Bob]")
        chkEx("{ val u = 'Dev'; return (u: $fromUser) @* { .pos == u } ( .name ); }", "ct_err:block:name_conflict:u")
        chkEx("{ val u = 'Dev'; return (u: $fromUser) @* { .pos == 'Dev' } ( .name ); }", "ct_err:block:name_conflict:u")
        chkEx("{ val u = 'Dev'; return (u: $fromUser) @* { u.pos == 'Dev' } ( .name ); }",
                "ct_err:[block:name_conflict:u][unknown_member:[text]:pos]")
    }

    @Test fun testAliasMatchByLocal() {
        initDataUserCompany()

        chkEx("{ val u = $fromUser@*{}[0]; return $fromUser@*{ u } ( .name ); }", "ct_err:at_where:var_noattrs:0:u:user")
        chkEx("{ val u = $fromUser@*{}[0]; return (user:$fromUser)@*{ u } ( .name ); }", "ct_err:at_where:var_noattrs:0:u:user")
        chkEx("{ val u = $fromUser@*{}[0]; return (user:$fromUser)@*{ user } ( .name ); }", "ct_err:at_where:type:0:[boolean]:[user]")
        chkEx("{ val u = $fromUser@*{}[0]; return $fromUser@*{ $ == u } ( .name ); }", "[Bob]")
        chkEx("{ val u = $fromUser@*{}[0]; return (user:$fromUser)@*{ user == u } ( .name ); }", "[Bob]")

        chkEx("{ val employer = $fromCompany@*{}[0]; return $fromUser@*{ employer } ( .name ); }", "[Bob]")
        chkEx("{ val c = $fromCompany@*{}[0]; return $fromUser@*{ c } ( .name ); }", "[Bob]")
    }

    @Test fun testAttrMatchByTypeConstExpr() {
        initDataTypes()

        val fromSingle = impFrom("single")
        chk("$fromSingle @* { 123 } ( .id.s )", "[Bob]")
        chk("$fromSingle @* { 456 } ( .id.s )", "[Alice]")
        chk("$fromSingle @* { 'A' } ( .id.s )", "[Bob]")
        chk("$fromSingle @* { 'B' } ( .id.s )", "[Alice]")
        chk("$fromSingle @* { false } ( .id.s )", "[]")
        chk("$fromSingle @* { true } ( .id.s )", "[Bob, Alice]")

        val fromDouble = impFrom("double")
        chk("(v:$fromDouble) @* { 123 } ( .id.s )", "ct_err:at_attr_type_ambig:0:integer:[v:double.i1,v:double.i2]")
        chk("(v:$fromDouble) @* { 'A' } ( .id.s )", "ct_err:at_attr_type_ambig:0:text:[v:double.t1,v:double.t2]")
        chk("$fromDouble @* { false } ( .id.s )", "[]")
        chk("$fromDouble @* { true } ( .id.s )", "[Bob, Alice]")
    }

    @Test fun testImplicitTupleFieldName() {
        initDataUserCompany()
        chk("$fromUser @* {} ( .name, .pos )", "[(name=Bob,pos=Dev), (name=Alice,pos=Tester)]")
        chk("$fromUser @* {} ( $.name, $.pos )", "[(name=Bob,pos=Dev), (name=Alice,pos=Tester)]")
        chk("(u: $fromUser) @* {} ( .name, .pos )", "[(name=Bob,pos=Dev), (name=Alice,pos=Tester)]")
        chk("(u: $fromUser) @* {} ( u.name, u.pos )", "[(name=Bob,pos=Dev), (name=Alice,pos=Tester)]")
    }

    @Test fun testPlaceholder() {
        initDataUserCompany()
        chk("$fromUser @* {} ( $.name, @omit @sort .id )", "[Bob, Alice]")
        chk("(u: $fromUser) @* {} ( $.name, @omit @sort .id )", "ct_err:expr:placeholder:none")
        chk("(u: $fromUser) @* {} ( u.name, @omit @sort .id )", "[Bob, Alice]")
    }

    @Test fun testNestedPlaceholder() {
        initDataUserCompany()
        chk("[7] @* {} ( $fromUser @* {} ( $.name, @omit @sort .id ) )", "[[Bob, Alice]]")
        chk("[7] @* {} ( $fromUser @* {} ( _=$.id, _=.name, @omit @sort .id ) )", "[[(501,Bob), (502,Alice)]]")
        chk("[7] @* {} ( $, $fromUser @* {} ( .name, @omit @sort .id ) )", "[(7,[Bob, Alice])]")
        chk("[7] @* {} ( (u: $fromUser) @* {} ( $.name, @omit @sort .id ) )",
                "ct_err:[at_expr:placeholder:belongs_to_outer][unknown_member:[integer]:name]")
        chk("[7] @* {} ( (u: $fromUser) @* {} ( $, _=u.name, @omit @sort .id ) )", "ct_err:at_expr:placeholder:belongs_to_outer")
        chk("(x: [7]) @* {} ( (u: $fromUser) @* {} ( $, _=u.name, @omit @sort .id ) )", "ct_err:expr:placeholder:none")
        chk("(x: [7]) @* {} ( (u: $fromUser) @* {} ( x, _=u.name, @omit @sort .id ) )", "[[(7,Bob), (7,Alice)]]")
    }

    @Test fun testNestedPlaceholderExists() {
        initDataUserCompanyCity()
        chk("$fromUser @* {exists( $fromCompany @* {} )} ( .name )", "[Bob, Alice, Trudy]")
        chk("$fromUser @* {exists( $fromCompany @* { $.name != '*' } )} ( .name )", "[Bob, Alice, Trudy]")
        chk("(u: $fromUser) @* {exists( (c: $fromCompany) @* { c.city == u.city } )} ( .name )", "[Bob, Alice]")
        chk("(u: $fromUser) @* {exists( (c: $fromCompany) @* { $.city == u.city } )} ( .name )", "ct_err:expr:placeholder:none")
        chk("(u: $fromUser) @* {exists( (c: $fromCompany) @* { c.city == $.city } )} ( .name )", "ct_err:expr:placeholder:none")
        chk("$fromUser @* {exists( (c: $fromCompany) @* { c.city == $.city } )} ( .name )", "ct_err:at_expr:placeholder:belongs_to_outer")
        chk("(u: $fromUser) @* {exists( $fromCompany @* { $.city == u.city } )} ( .name )", "[Bob, Alice]")
    }

    @Test fun testNestedPlaceholderIn() {
        initDataUserCompanyCity()
        chk("$fromUser @* { .city in $fromCompany @* {} ($.city) } ( .name )", "[Bob, Alice]")
        chk("$fromUser @* { .city in $fromCompany @* {} ($.company_attr) } ( .name )", "[]")
        chk("$fromUser @* { .city in $fromCompany @* {} (.company_attr) } ( .name )", "[]")
        chk("$fromUser @* { .city in $fromCompany @* {$.name != '*'} (.company_attr) } ( .name )", "[]")
        chk("(u: $fromUser) @* { .city in (c: $fromCompany) @* {} (c.city) } ( .name )", "[Bob, Alice]")
        chk("(u: $fromUser) @* { .city in (c: $fromCompany) @* {} ($.city) } ( .name )", "ct_err:expr:placeholder:none")
        chk("(u: $fromUser) @* { $.city in (c: $fromCompany) @* {} (c.city) } ( .name )", "ct_err:expr:placeholder:none")
        chk("$fromUser @* { .city in (c: $fromCompany) @* {} ($.city) } ( .name )", "ct_err:at_expr:placeholder:belongs_to_outer")
        chk("$fromUser @* { .city in (c: $fromCompany) @* {} (c.city) } ( .name )", "[Bob, Alice]")
        chk("$fromUser @* { $.city in (c: $fromCompany) @* {} (c.city) } ( .name )", "[Bob, Alice]")
        chk("(u: $fromUser) @* { .city in $fromCompany @* {} ($.city) } ( .name )", "[Bob, Alice]")
    }

    @Test fun testNestedAttributesDirect() {
        initDataUserCompanyCity()

        chk("$fromUser @* {} ( _=.name, $fromCompany @ {} ($.user_attr) limit 1 )",
                "ct_err:unknown_member:[company]:user_attr")

        chk("$fromUser @* {} ( (c:$fromCompany) @ {} (c.name) limit 1 )", "[Adidas, Adidas, Adidas]")

        chkNestedAttributes("(u:$fromUser) @* {} ( _=.name, (c:$fromCompany) @ {} (.name) limit 1 )",
                "ct_err:at_attr_name_ambig:name:[c:company:name,u:user:name]",
                "[(Bob,Adidas), (Alice,Adidas), (Trudy,Adidas)]",
                "[(Bob,Adidas), (Alice,Adidas), (Trudy,Adidas)]"
        )

        chkNestedAttributes("(u:$fromUser) @* {} ( _=.name, (c:$fromCompany) @ {} (.city) limit 1 )",
                "ct_err:at_attr_name_ambig:city:[c:company:city,u:user:city]",
                "[(Bob,London), (Alice,London), (Trudy,London)]",
                "[(Bob,London), (Alice,London), (Trudy,London)]"
        )

        chkNestedAttributes("$fromUser @* {} ( _=.name, $fromCompany @ {} (.company_attr) limit 1 )",
                "[(Bob,C1), (Alice,C1), (Trudy,C1)]",
                "[(Bob,C1), (Alice,C1), (Trudy,C1)]",
                "[(Bob,C1), (Alice,C1), (Trudy,C1)]"
        )
    }

    @Test fun testNestedAttributesExists() {
        initDataUserCompanyCity()

        chk("$fromUser @* {exists( $fromCompany @* {} ($.user_attr) )} ( .name )",
                "ct_err:unknown_member:[company]:user_attr")

        chk("(u:$fromUser) @* {exists( $fromCompany @* {} (u.name) )} ( .name )", "[Bob, Alice, Trudy]")
        chk("(u:$fromUser) @* {exists( (c:$fromCompany) @* {} (c.name) )} ( .name )", "[Bob, Alice, Trudy]")
        chk("(u:$fromUser) @* {exists( (c:$fromCompany) @* {} (u.user_attr) )} ( .name )", "[Bob, Alice, Trudy]")

        chkNestedAttributes("(u:$fromUser) @* {exists( (c:$fromCompany) @* {} (.name) )} ( .name )",
                "ct_err:at_attr_name_ambig:name:[c:company:name,u:user:name]",
                "ct_err:at_attr_name_ambig:name:[c:company:name,u:user:name]",
                "[Bob, Alice, Trudy]"
        )

        chkNestedAttributes("(u:$fromUser) @* {exists( (c:$fromCompany) @* {} (.city) )} ( .name )",
                "ct_err:at_attr_name_ambig:city:[c:company:city,u:user:city]",
                "ct_err:at_attr_name_ambig:city:[c:company:city,u:user:city]",
                "[Bob, Alice, Trudy]"
        )

        chkNestedAttributes("$fromUser @* {exists( $fromCompany @* {} (.company_attr) )} ( .name )",
                "[Bob, Alice, Trudy]",
                "[Bob, Alice, Trudy]",
                "[Bob, Alice, Trudy]"
        )

        chkNestedAttributes("(u:$fromUser) @* {exists( (c:$fromCompany) @* {} (.user_attr) )} ( .name )",
                "ct_err:at_expr:attr:belongs_to_outer:user_attr:u:user",
                "ct_err:at_expr:attr:belongs_to_outer:user_attr:u:user",
                "ct_err:at_expr:attr:belongs_to_outer:user_attr:u:user"
        )
    }

    @Test fun testNestedAttributesIn() {
        initDataUserCompanyCity()

        chk("$fromUser @* { .city in $fromCompany @* {} ($.user_attr) } ( .name )",
                "ct_err:unknown_member:[company]:user_attr")

        chk("(u:$fromUser) @* { .city in $fromCompany @* {} (u.user_attr) } ( .name )", "[]")
        chk("(u:$fromUser) @* { .city in $fromCompany @* {} (u.city) } ( .name )", "[Bob, Alice, Trudy]")
        chk("$fromUser @* { .city in (c:$fromCompany) @* {} (c.city) } ( .name )", "[Bob, Alice]")

        chkNestedAttributes("(u:$fromUser) @* { .city in (c:$fromCompany) @* {} (.name) } ( .name )",
                "ct_err:at_attr_name_ambig:name:[c:company:name,u:user:name]",
                "ct_err:at_attr_name_ambig:name:[c:company:name,u:user:name]",
                "[]"
        )

        chkNestedAttributes("(u:$fromUser) @* { .city in (c:$fromCompany) @* {} (.city) } ( .name )",
                "ct_err:at_attr_name_ambig:city:[c:company:city,u:user:city]",
                "ct_err:at_attr_name_ambig:city:[c:company:city,u:user:city]",
                "[Bob, Alice]"
        )

        chkNestedAttributes("$fromUser @* { .city in $fromCompany @* {} (.company_attr) } ( .name )",
                "[]",
                "[]",
                "[]"
        )

        chkNestedAttributes("(u:$fromUser) @* { .city in (c:$fromCompany) @* {} (.user_attr) } ( .name )",
                "ct_err:at_expr:attr:belongs_to_outer:user_attr:u:user",
                "ct_err:at_expr:attr:belongs_to_outer:user_attr:u:user",
                "ct_err:at_expr:attr:belongs_to_outer:user_attr:u:user"
        )
    }

    @Test fun testNestedAttributesImplicitByType() {
        impDefType("user", "name", "value:integer")
        impDefType("company", "name", "value:integer")
        impCreateObjs("user", "name = 'Adidas', value = 100", "name = 'Reebok', value = 101")
        impCreateObjs("company", "name = 'Puma', value = 200", "name = 'Adidas', value = 201")

        val err = "ct_err:at_attr_name_ambig:value:[c:company:value,u:user:value]"

        chkNestedAttributes("(u:$fromUser) @* {} ( (c:$fromCompany) @* { 201 } (c.name) )",
            "[[Adidas], [Adidas]]", "[[Adidas], [Adidas]]", "[[Adidas], [Adidas]]",
        )

        chkNestedAttributes("(u:$fromUser) @* {} ( (c:$fromCompany) @* { .value == 201 } (c.name) )",
            err,
            "[[Adidas], [Adidas]]",
            "[[Adidas], [Adidas]]",
        )

        chkNestedAttributes("(u:$fromUser) @* { exists( (c:$fromCompany) @* { 201 } ) } (.name)",
            "[Adidas, Reebok]", "[Adidas, Reebok]", "[Adidas, Reebok]"
        )

        chkNestedAttributes("(u:$fromUser) @* { exists( (c:$fromCompany) @* { .value == 201 } ) } (.name)",
            err,
            err,
            "[Adidas, Reebok]",
        )

        chkNestedAttributes("(u:$fromUser) @* { .name in (c:$fromCompany) @* { 201 } (c.name) } (.name)",
            "[Adidas]", "[Adidas]", "[Adidas]"
        )

        chkNestedAttributes("(u:$fromUser) @* { .name in (c:$fromCompany) @* { .value == 201 } (c.name) } (.name)",
            err,
            err,
            "[Adidas]",
        )
    }

    @Test fun testNestedAttributesImplicitByName() {
        impDefType("user", "name", "value:integer")
        impDefType("company", "name", "value:integer")
        impCreateObjs("user", "name = 'Adidas', value = 100", "name = 'Reebok', value = 101")
        impCreateObjs("company", "name = 'Puma', value = 200", "name = 'Adidas', value = 201")

        chkNestedAttributesEx("{ val value = 201; return (u:$fromUser) @* {} ( (c:$fromCompany) @* { value } (c.name) ); }",
            "[[Adidas], [Adidas]]", "[[Adidas], [Adidas]]", "[[Adidas], [Adidas]]",
        )

        chkNestedAttributesEx("{ val value = 201; return (u:$fromUser) @* { exists( (c:$fromCompany) @* { value } ) } (.name); }",
            "[Adidas, Reebok]", "[Adidas, Reebok]", "[Adidas, Reebok]"
        )

        chkNestedAttributesEx("{ val value = 201; return (u:$fromUser) @* { .name in (c:$fromCompany) @* { value } (c.name) } (.name); }",
            "[Adidas]", "[Adidas]", "[Adidas]"
        )
    }

    protected fun chkNestedAttributes(expr: String, expectedNone: String, expectedPartial: String, expectedFull: String) {
        chkNestedAttributesEx("= $expr;", expectedNone, expectedPartial, expectedFull)
    }

    private fun chkNestedAttributesEx(code: String, expectedNone: String, expectedPartial: String, expectedFull: String) {
        tst.atAttrShadowing = C_AtAttrShadowing.NONE
        chkEx(code, expectedNone)
        tst.atAttrShadowing = C_AtAttrShadowing.PARTIAL
        chkEx(code, expectedPartial)
        tst.atAttrShadowing = C_AtAttrShadowing.FULL
        chkEx(code, expectedFull)
    }

    @Test fun testAliasLocalVarConflict() {
        initDataUserCompany()
        chkEx("{ val user = $fromUser@*{}[0]; return $fromUser @* {} ( $.id ); }", "[501, 502]")
        chkEx("{ val user = 'Bob'; return $fromUser @* {} ( $.id ); }", "[501, 502]")
        chkEx("{ val u = $fromUser@*{}[0]; return (u: $fromUser) @* {} ( u.id ); }", "ct_err:block:name_conflict:u")
        chkEx("{ val u = 'Bob'; return (u: $fromUser) @* {} ( u.id ); }", "ct_err:[block:name_conflict:u][unknown_member:[text]:id]")
    }

    @Test fun testNestedAtWhereAttrByType() {
        initDataUserCompany()
        chk("$fromCompany @ { 'Apple' } (.name.upper_case())", "APPLE")
        chk("$fromCompany @ { 'Google' } (.name.upper_case())", "GOOGLE")
        chk("$fromUser @* { .name == 'Bob' } ( (c:$fromCompany) @ { 'Apple' } (c.name.upper_case()) )", "[APPLE]")
    }

    @Test fun testWhereImplicitLocalDirect() {
        initWhereImplicitLocal()

        var f = "${impFrom("outer")} @* {} ( .outer_id.s, ${impFrom("inner")} @ { %s } (.inner_id.s) )"
        chk(f.format("'Bob'"), "[(Adidas,Bob), (Reebok,Bob)]")
        chk(f.format("'Alice'"), "[(Adidas,Alice), (Reebok,Alice)]")
        chk(f.format("321"), "[(Adidas,Bob), (Reebok,Bob)]")
        chk(f.format("654"), "[(Adidas,Alice), (Reebok,Alice)]")

        f = "{ val %s = %s; return ${impFrom("outer")} @* {} ( .outer_id.s, ${impFrom("inner")} @ { %1\$s } (.inner_id.s) ); }"
        chkEx(f.format("name", "'Bob'"), "[(Adidas,Bob), (Reebok,Bob)]")
        chkEx(f.format("name", "'Alice'"), "[(Adidas,Alice), (Reebok,Alice)]")
        chkEx(f.format("value", "321"), "[(Adidas,Bob), (Reebok,Bob)]")
        chkEx(f.format("value", "654"), "[(Adidas,Alice), (Reebok,Alice)]")
    }

    @Test fun testWhereImplicitLocalExists() {
        initWhereImplicitLocal()

        var f = "${impFrom("outer")} @* {exists( ${impFrom("inner")}@*{ %s } )} ( .outer_id.s )"
        chk(f.format("'Bob'"), "[Adidas, Reebok]")
        chk(f.format("'Alice'"), "[Adidas, Reebok]")
        chk(f.format("'Trudy'"), "[]")
        chk(f.format("321"), "[Adidas, Reebok]")
        chk(f.format("654"), "[Adidas, Reebok]")
        chk(f.format("987"), "[]")

        f = "{ val %s = %s; return ${impFrom("outer")} @* {exists( ${impFrom("inner")}@*{ %1\$s } )} ( .outer_id.s ); }"
        chkEx(f.format("name", "'Bob'"), "[Adidas, Reebok]")
        chkEx(f.format("name", "'Alice'"), "[Adidas, Reebok]")
        chkEx(f.format("name", "'Trudy'"), "[]")
        chkEx(f.format("value", "321"), "[Adidas, Reebok]")
        chkEx(f.format("value", "654"), "[Adidas, Reebok]")
        chkEx(f.format("value", "987"), "[]")
    }

    @Test fun testWhereImplicitLocalIn() {
        initWhereImplicitLocal()

        var f = "${impFrom("outer")} @* { .sub_id in ${impFrom("inner")}@*{ %s }(.inner_id) } ( .outer_id.s )"
        chk(f.format("'Bob'"), "[Adidas]")
        chk(f.format("'Alice'"), "[Reebok]")
        chk(f.format("'Trudy'"), "[]")
        chk(f.format("321"), "[Adidas]")
        chk(f.format("654"), "[Reebok]")
        chk(f.format("987"), "[]")

        f = "{ val %s = %s; return ${impFrom("outer")} @* { .sub_id in ${impFrom("inner")}@*{ %1\$s }(.inner_id) } ( .outer_id.s ); }"
        chkEx(f.format("name", "'Bob'"), "[Adidas]")
        chkEx(f.format("name", "'Alice'"), "[Reebok]")
        chkEx(f.format("name", "'Trudy'"), "[]")
        chkEx(f.format("value", "321"), "[Adidas]")
        chkEx(f.format("value", "654"), "[Reebok]")
        chkEx(f.format("value", "987"), "[]")
    }

    private fun initWhereImplicitLocal() {
        def("entity id { s: text; }")
        insert("c0.id", "s", "100,'Adidas'", "101,'Reebok'", "200,'Bob'", "201,'Alice'")

        impDefType("outer", "outer_id:id", "sub_id:id", "name", "value:integer")
        impDefType("inner", "inner_id:id", "name", "value:integer", "bytes:byte_array")

        impCreateObjs("outer",
                "outer_id = id@{'Adidas'}, sub_id = id@{'Bob'}, name = 'Adidas', value = 123",
                "outer_id = id@{'Reebok'}, sub_id = id@{'Alice'}, name = 'Reebok', value = 456"
        )

        impCreateObjs("inner",
                "inner_id = id@{'Bob'}, name = 'Bob', value = 321, bytes = x'feed'",
                "inner_id = id@{'Alice'}, name = 'Alice', value = 654, bytes = x'beef'"
        )
    }

    @Test fun testWhereImplicitOuterExists() {
        initWhereImplicitOuter()

        fun f(i: String, w: String, e: String) =
                chk("(o:${impFrom("outer")}) @ {exists( (i:${impFrom(i)}) @* { $w })} (.outer_id.s)", e)

        f("inner1", "o.value", "O")
        f("inner1", "o.foo_value", "O")
        f("inner1", "o.bar_value", "O")
        f("inner1", "i.value", "ct_err:at_where:type:0:[boolean]:[value]")
        f("inner1", "o.ref.value", "O")
        f("inner1", "o.ref.foo_value", "O")
        f("inner1", "o.ref.bar_value", "O")

        f("inner2", "o.value", "ct_err:at_where:var_manyattrs_type:0:value:value:[i:inner2.foo_value,i:inner2.bar_value]")
        f("inner2", "i.foo_value == o.value", "O")
        f("inner2", "i.bar_value == o.value", "O")
        f("inner2", "o.foo_value", "O")
        f("inner2", "o.bar_value", "O")
        f("inner2", "i.foo_value", "ct_err:at_where:type:0:[boolean]:[value]")
        f("inner2", "i.bar_value", "ct_err:at_where:type:0:[boolean]:[value]")

        f("inner2", "o.ref.value", "ct_err:at_attr_type_ambig:0:value:[i:inner2.foo_value,i:inner2.bar_value]")
        f("inner2", "o.ref.foo_value", "ct_err:at_attr_type_ambig:0:value:[i:inner2.foo_value,i:inner2.bar_value]")
        f("inner2", "o.ref.bar_value", "ct_err:at_attr_type_ambig:0:value:[i:inner2.foo_value,i:inner2.bar_value]")
        f("inner2", "i.foo_value == o.ref.foo_value", "O")
        f("inner2", "i.bar_value == o.ref.bar_value", "O")
        f("inner2", "i.foo_value == o.ref.value", "O")
        f("inner2", "i.bar_value == o.ref.value", "O")

        f("inner3", "o.value", "O")
        f("inner3", "o.foo_value", "O")
        f("inner3", "o.bar_value", "O")
        f("inner3", "i.value", "ct_err:at_where:type:0:[boolean]:[value]")
        f("inner3", "i.foo_value", "ct_err:at_where:type:0:[boolean]:[value]")
        f("inner3", "i.bar_value", "ct_err:at_where:type:0:[boolean]:[value]")

        f("inner3", "o.ref.value", "ct_err:at_attr_type_ambig:0:value:[i:inner3.value,i:inner3.foo_value,i:inner3.bar_value]")
        f("inner3", "o.ref.foo_value", "ct_err:at_attr_type_ambig:0:value:[i:inner3.value,i:inner3.foo_value,i:inner3.bar_value]")
        f("inner3", "o.ref.bar_value", "ct_err:at_attr_type_ambig:0:value:[i:inner3.value,i:inner3.foo_value,i:inner3.bar_value]")
        f("inner3", "i.value == o.ref.value", "O")
        f("inner3", "i.foo_value == o.ref.foo_value", "O")
        f("inner3", "i.bar_value == o.ref.bar_value", "O")
    }

    @Test fun testWhereImplicitOuterIn() {
        initWhereImplicitOuter()

        fun f(i: String, w: String, e: String) =
                chk("(o:${impFrom("outer")}) @ {o.in_id in (i:${impFrom(i)}) @* { $w } (i.in_id) } (.outer_id.s)", e)

        f("inner1", "o.value", "O")
        f("inner1", "o.foo_value", "O")
        f("inner1", "o.bar_value", "O")
        f("inner1", "i.value", "ct_err:at_where:type:0:[boolean]:[value]")
        f("inner1", "o.ref.value", "O")
        f("inner1", "o.ref.foo_value", "O")
        f("inner1", "o.ref.bar_value", "O")

        f("inner2", "o.value", "ct_err:at_where:var_manyattrs_type:0:value:value:[i:inner2.foo_value,i:inner2.bar_value]")
        f("inner2", "i.foo_value == o.value", "O")
        f("inner2", "i.bar_value == o.value", "O")
        f("inner2", "o.foo_value", "O")
        f("inner2", "o.bar_value", "O")
        f("inner2", "i.foo_value", "ct_err:at_where:type:0:[boolean]:[value]")
        f("inner2", "i.bar_value", "ct_err:at_where:type:0:[boolean]:[value]")

        f("inner2", "o.ref.value", "ct_err:at_attr_type_ambig:0:value:[i:inner2.foo_value,i:inner2.bar_value]")
        f("inner2", "o.ref.foo_value", "ct_err:at_attr_type_ambig:0:value:[i:inner2.foo_value,i:inner2.bar_value]")
        f("inner2", "o.ref.bar_value", "ct_err:at_attr_type_ambig:0:value:[i:inner2.foo_value,i:inner2.bar_value]")
        f("inner2", "i.foo_value == o.foo_value", "O")
        f("inner2", "i.bar_value == o.bar_value", "O")

        f("inner3", "o.value", "O")
        f("inner3", "o.foo_value", "O")
        f("inner3", "o.bar_value", "O")
        f("inner3", "i.value", "ct_err:at_where:type:0:[boolean]:[value]")
        f("inner3", "i.foo_value", "ct_err:at_where:type:0:[boolean]:[value]")
        f("inner3", "i.bar_value", "ct_err:at_where:type:0:[boolean]:[value]")

        f("inner3", "o.ref.value", "ct_err:at_attr_type_ambig:0:value:[i:inner3.value,i:inner3.foo_value,i:inner3.bar_value]")
        f("inner3", "o.ref.foo_value", "ct_err:at_attr_type_ambig:0:value:[i:inner3.value,i:inner3.foo_value,i:inner3.bar_value]")
        f("inner3", "o.ref.bar_value", "ct_err:at_attr_type_ambig:0:value:[i:inner3.value,i:inner3.foo_value,i:inner3.bar_value]")
        f("inner3", "i.value == o.ref.value", "O")
        f("inner3", "i.foo_value == o.ref.foo_value", "O")
        f("inner3", "i.bar_value == o.ref.bar_value", "O")
    }

    protected fun initWhereImplicitOuter() {
        def("entity id { s: text; }")
        def("entity value { v: text; }")
        def("entity ref { value; foo_value: value; bar_value: value; }")

        "O,I10,I11,I12,I20,I21,I22,I23,I30,I31,I32,I33,I34,R,X".split(",")
                .mapIndexed { i, s -> "${100+i},'$s'" }.forEach { insert("c0.id", "s", it) }
        "A,B,C,Z".split(",")
                .mapIndexed { i, s -> "${200+i},'$s'" }.forEach { insert("c0.value", "v", it) }
        insert("c0.ref", "value,foo_value,bar_value", "300,200,201,202")

        impDefType("outer", "outer_id:id", "in_id:id", "ref", "value", "foo_value:value", "bar_value:value")
        impDefType("inner1", "inner_id:id", "in_id:id", "value")
        impDefType("inner2", "inner_id:id", "in_id:id", "foo_value:value", "bar_value:value")
        impDefType("inner3", "inner_id:id", "in_id:id", "value", "foo_value:value", "bar_value:value")

        impCreateObjs("outer",
                "outer_id = id@{'O'}, in_id = id@{'X'}, ref = ref@{}, value = value@{'A'}, foo_value = value@{'B'}, bar_value = value@{'C'}"
        )
        impCreateObjs("inner1",
                "inner_id = id@{'I10'}, in_id = id@{'X'}, value = value@{'A'}",
                "inner_id = id@{'I11'}, in_id = id@{'X'}, value = value@{'B'}",
                "inner_id = id@{'I12'}, in_id = id@{'X'}, value = value@{'C'}"
        )
        impCreateObjs("inner2",
                "inner_id = id@{'I20'}, in_id = id@{'X'}, foo_value = value@{'B'}, bar_value = value@{'Z'}",
                "inner_id = id@{'I21'}, in_id = id@{'X'}, foo_value = value@{'Z'}, bar_value = value@{'C'}",
                "inner_id = id@{'I22'}, in_id = id@{'X'}, foo_value = value@{'A'}, bar_value = value@{'Z'}",
                "inner_id = id@{'I23'}, in_id = id@{'X'}, foo_value = value@{'Z'}, bar_value = value@{'A'}"
        )
        impCreateObjs("inner3",
                "inner_id = id@{'I30'}, in_id = id@{'X'}, value = value@{'A'}, foo_value = value@{'Z'}, bar_value = value@{'Z'}",
                "inner_id = id@{'I31'}, in_id = id@{'X'}, value = value@{'Z'}, foo_value = value@{'B'}, bar_value = value@{'Z'}",
                "inner_id = id@{'I32'}, in_id = id@{'X'}, value = value@{'Z'}, foo_value = value@{'Z'}, bar_value = value@{'C'}",
                "inner_id = id@{'I33'}, in_id = id@{'X'}, value = value@{'Z'}, foo_value = value@{'A'}, bar_value = value@{'Z'}",
                "inner_id = id@{'I34'}, in_id = id@{'X'}, value = value@{'Z'}, foo_value = value@{'Z'}, bar_value = value@{'A'}"
        )
    }

    @Test fun testWhereImplicitBooleanSimpleConst() {
        initWhereImplicitBoolean()

        chk("${impFrom("data1")} @* { false } (.id.s)", "[]")
        chk("${impFrom("data1")} @* { true  } (.id.s)", "[A0, A1]")
        chk("${impFrom("data1")} @* { _nop(false) } (.id.s)", "[]")
        chk("${impFrom("data1")} @* { _nop(true)  } (.id.s)", "[A0, A1]")

        chk("${impFrom("data2")} @* { false } (.id.s)", "[]")
        chk("${impFrom("data2")} @* { true  } (.id.s)", "[B0, B1]")
        chk("${impFrom("data2")} @* { _nop(false) } (.id.s)", "[]")
        chk("${impFrom("data2")} @* { _nop(true)  } (.id.s)", "[B0, B1]")
    }

    @Test fun testWhereImplicitBooleanSimpleVar() {
        initWhereImplicitBoolean()

        chkEx("{ val x = false; return ${impFrom("data1")} @* { x } (.id.s); }", "[]")
        chkEx("{ val x = true;  return ${impFrom("data1")} @* { x } (.id.s); }", "[A0, A1]")
        chkEx("{ val b = false; return ${impFrom("data1")} @* { b } (.id.s); }", "[A0]")
        chkEx("{ val b = true;  return ${impFrom("data1")} @* { b } (.id.s); }", "[A1]")

        chkEx("{ val x = false;  return ${impFrom("data2")} @* { x } (.id.s); }", "[]")
        chkEx("{ val x = true;   return ${impFrom("data2")} @* { x } (.id.s); }", "[B0, B1]")
        chkEx("{ val b1 = false; return ${impFrom("data2")} @* { b1 } (.id.s); }", "[B0]")
        chkEx("{ val b1 = true;  return ${impFrom("data2")} @* { b1 } (.id.s); }", "[B1]")
        chkEx("{ val b2 = false; return ${impFrom("data2")} @* { b2 } (.id.s); }", "[B1]")
        chkEx("{ val b2 = true;  return ${impFrom("data2")} @* { b2 } (.id.s); }", "[B0]")
    }

    @Test fun testWhereImplicitBooleanSimpleAttr() {
        initWhereImplicitBoolean()

        chk("${impFrom("data1")} @* { .b      } (.id.s)", "[A1]")
        chk("${impFrom("data1")} @* { not .b  } (.id.s)", "[A0]")

        chk("${impFrom("data2")} @* { .b1          } (.id.s)", "[B1]")
        chk("${impFrom("data2")} @* { .b1 == true  } (.id.s)", "[B1]")
        chk("${impFrom("data2")} @* { .b1 == false } (.id.s)", "[B0]")
        chk("${impFrom("data2")} @* { .b2          } (.id.s)", "[B0]")
        chk("${impFrom("data2")} @* { .b2 == true  } (.id.s)", "[B0]")
        chk("${impFrom("data2")} @* { .b2 == false } (.id.s)", "[B1]")

        chk("${impFrom("data1")} @* { .ref.v          } (.id.s)", "[A1]")
        chk("${impFrom("data1")} @* { .ref.v == true  } (.id.s)", "[A1]")
        chk("${impFrom("data1")} @* { .ref.v == false } (.id.s)", "[A0]")

        chk("${impFrom("data2")} @* { .ref.v          } (.id.s)", "[B1]")
        chk("${impFrom("data2")} @* { .ref.v == true  } (.id.s)", "[B1]")
        chk("${impFrom("data2")} @* { .ref.v == false } (.id.s)", "[B0]")
    }

    @Test fun testWhereImplicitBooleanNestedExists() {
        initWhereImplicitBoolean()

        fun f(o: String, d: String, w: String, e: String, vararg warns: String) {
            chk("(o:${impFrom(o)}) @* {exists( (i:${impFrom(d)}) @* { $w } )} (.outer_id.s)", e)
            chkWarn(*warns)
        }

        f("outer0", "data1", "o.b",  "[O0]")
        f("outer0", "data1", "o.b1", "[]", "at:where:name_boolean_no_attr:b1")
        f("outer0", "data1", "o.b2", "[]", "at:where:name_boolean_no_attr:b2")
        f("outer1", "data1", "o.b",  "[O1]")
        f("outer1", "data1", "o.b1", "[O1]", "at:where:name_boolean_no_attr:b1")
        f("outer1", "data1", "o.b2", "[O1]", "at:where:name_boolean_no_attr:b2")

        f("outer0", "data2", "o.b",  "[]", "at:where:name_boolean_no_attr:b")
        f("outer0", "data2", "o.b1", "[O0]")
        f("outer0", "data2", "o.b2", "[O0]")
        f("outer1", "data2", "o.b",  "[O1]", "at:where:name_boolean_no_attr:b")
        f("outer1", "data2", "o.b1", "[O1]")
        f("outer1", "data2", "o.b2", "[O1]")
    }

    @Test fun testWhereImplicitBooleanNestedIn() {
        initWhereImplicitBoolean()

        fun f(o: String, d: String, w: String, e: String, vararg warns: String) {
            chk("(o:${impFrom(o)}) @* {o.in_id in (i:${impFrom(d)}) @* { $w } (i.in_id)} (.outer_id.s)", e)
            chkWarn(*warns)
        }

        f("outer0", "data1", "o.b",  "[O0]")
        f("outer0", "data1", "o.b1", "[]", "at:where:name_boolean_no_attr:b1")
        f("outer0", "data1", "o.b2", "[]", "at:where:name_boolean_no_attr:b2")
        f("outer1", "data1", "o.b",  "[O1]")
        f("outer1", "data1", "o.b1", "[O1]", "at:where:name_boolean_no_attr:b1")
        f("outer1", "data1", "o.b2", "[O1]", "at:where:name_boolean_no_attr:b2")

        f("outer0", "data2", "o.b",  "[]", "at:where:name_boolean_no_attr:b")
        f("outer0", "data2", "o.b1", "[O0]")
        f("outer0", "data2", "o.b2", "[O0]")
        f("outer1", "data2", "o.b",  "[O1]", "at:where:name_boolean_no_attr:b")
        f("outer1", "data2", "o.b1", "[O1]")
        f("outer1", "data2", "o.b2", "[O1]")
    }

    protected fun initWhereImplicitBoolean() {
        def("entity id { s: text; }")
        def("entity ref { i: integer; v: boolean; }")
        insert("c0.id", "s", "100,'A0'", "101,'A1'", "102,'B0'", "103,'B1'", "104,'O0'", "105,'O1'", "106,'X'")
        insert("c0.ref", "i,v", "200,0,FALSE", "201,1,TRUE")

        impDefType("data1", "id", "in_id:id", "ref", "b:boolean")
        impDefType("data2", "id", "in_id:id", "ref", "b1:boolean", "b2:boolean")
        impDefType("outer0", "outer_id:id", "in_id:id", "b:boolean", "b1:boolean", "b2:boolean")
        impDefType("outer1", "outer_id:id", "in_id:id", "b:boolean", "b1:boolean", "b2:boolean")

        impCreateObjs("data1",
                "id = id@{'A0'}, in_id = id@{'X'}, ref = ref@{0}, b = false",
                "id = id@{'A1'}, in_id = id@{'X'}, ref = ref@{1}, b = true"
        )
        impCreateObjs("data2",
                "id = id@{'B0'}, in_id = id@{'X'}, ref = ref@{0}, b1 = false, b2 = true",
                "id = id@{'B1'}, in_id = id@{'X'}, ref = ref@{1}, b1 = true,  b2 = false"
        )
        impCreateObjs("outer0", "outer_id = id@{'O0'}, in_id = id@{'X'}, b = false, b1 = false, b2 = false")
        impCreateObjs("outer1", "outer_id = id@{'O1'}, in_id = id@{'X'}, b = true,  b1 = true,  b2 = true")
    }

    @Test fun testTypeOfInWhere() {
        initDataUserCompany()
        chk("$fromCompany @* { $.name }", "ct_err:at_where:type:0:[boolean]:[text]")
        chk("$fromCompany @* { _type_of($) }", "ct_err:at_where:type:0:[boolean]:[text]")
        chk("$fromCompany @* { _type_of($.name) }", "ct_err:at_where:type:0:[boolean]:[text]")
    }
}
