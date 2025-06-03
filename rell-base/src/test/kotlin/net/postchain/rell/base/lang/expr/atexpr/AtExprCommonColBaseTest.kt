/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.expr.atexpr

import org.junit.Test

abstract class AtExprCommonColBaseTest: AtExprCommonBaseTest() {
    @Test fun testAliasMatchByLocal_Col() {
        initDataUserCompany()
        chkEx("{ val user = $fromUser@*{}[0]; return $fromUser@*{ user } ( .name ); }", "ct_err:at_where:var_noattrs:0:user:user")
    }

    @Test fun testNestedAttributesDirect_Col() {
        initDataUserCompanyCity()

        chk("$fromUser @* {} ( _=.name, $fromCompany @ {} (.user_attr) limit 1 )",
                "ct_err:at_expr:attr:belongs_to_outer:user_attr:$:user")

        chk("(u:$fromUser) @* {} ( $fromCompany @ {} (u.name) limit 1 )", "[Bob, Alice, Trudy]")

        chkNestedAttributes("$fromUser @* {} ( _=.name, $fromCompany @ {} (.user_attr) limit 1 )",
                "ct_err:at_expr:attr:belongs_to_outer:user_attr:$:user",
                "ct_err:at_expr:attr:belongs_to_outer:user_attr:$:user",
                "ct_err:at_expr:attr:belongs_to_outer:user_attr:$:user"
        )
    }

    @Test fun testWhereImplicitOuterDirect() {
        initWhereImplicitOuter()

        fun f(i: String, w: String, e: String) =
                chk("(o:${impFrom("outer")}) @{} ( (i:${impFrom(i)}) @ { $w } (.inner_id.s) )", e)

        f("inner1", "o.value", "I10")
        f("inner1", "o.foo_value", "I11")
        f("inner1", "o.bar_value", "I12")
        f("inner1", "i.value", "ct_err:at_where:type:0:[boolean]:[value]")
        f("inner1", "o.ref.value", "I10")
        f("inner1", "o.ref.foo_value", "I11")
        f("inner1", "o.ref.bar_value", "I12")

        f("inner2", "o.value", "ct_err:at_where:var_manyattrs_type:0:value:value:[i:inner2.foo_value,i:inner2.bar_value]")
        f("inner2", "i.foo_value == o.value", "I22")
        f("inner2", "i.bar_value == o.value", "I23")
        f("inner2", "o.foo_value", "I20")
        f("inner2", "o.bar_value", "I21")
        f("inner2", "i.foo_value", "ct_err:at_where:type:0:[boolean]:[value]")
        f("inner2", "i.bar_value", "ct_err:at_where:type:0:[boolean]:[value]")

        f("inner2", "o.ref.value", "ct_err:at_attr_type_ambig:0:value:[i:inner2.foo_value,i:inner2.bar_value]")
        f("inner2", "o.ref.foo_value", "ct_err:at_attr_type_ambig:0:value:[i:inner2.foo_value,i:inner2.bar_value]")
        f("inner2", "o.ref.bar_value", "ct_err:at_attr_type_ambig:0:value:[i:inner2.foo_value,i:inner2.bar_value]")
        f("inner2", "i.foo_value == o.ref.foo_value", "I20")
        f("inner2", "i.bar_value == o.ref.bar_value", "I21")
        f("inner2", "i.foo_value == o.ref.value", "I22")
        f("inner2", "i.bar_value == o.ref.value", "I23")

        f("inner3", "o.value", "I30")
        f("inner3", "o.foo_value", "I31")
        f("inner3", "o.bar_value", "I32")
        f("inner3", "i.foo_value == o.value", "I33")
        f("inner3", "i.bar_value == o.value", "I34")
        f("inner3", "i.value", "ct_err:at_where:type:0:[boolean]:[value]")
        f("inner3", "i.foo_value", "ct_err:at_where:type:0:[boolean]:[value]")
        f("inner3", "i.bar_value", "ct_err:at_where:type:0:[boolean]:[value]")

        f("inner3", "o.ref.value", "ct_err:at_attr_type_ambig:0:value:[i:inner3.value,i:inner3.foo_value,i:inner3.bar_value]")
        f("inner3", "o.ref.foo_value", "ct_err:at_attr_type_ambig:0:value:[i:inner3.value,i:inner3.foo_value,i:inner3.bar_value]")
        f("inner3", "o.ref.bar_value", "ct_err:at_attr_type_ambig:0:value:[i:inner3.value,i:inner3.foo_value,i:inner3.bar_value]")
        f("inner3", "i.value == o.ref.value", "I30")
        f("inner3", "i.foo_value == o.ref.foo_value", "I31")
        f("inner3", "i.bar_value == o.ref.bar_value", "I32")
    }

    @Test fun testWhereImplicitBooleanNestedDirect() {
        initWhereImplicitBoolean()

        fun f(o: String, d: String, w: String, e: String) =
                chk("(o:${impFrom(o)}) @ {} ( (i:${impFrom(d)}) @* { $w } (i.id.s) )", e)

        f("outer0", "data1", "o.b",  "[A0]")
        f("outer0", "data1", "o.b1", "[]")
        f("outer0", "data1", "o.b2", "[]")
        f("outer1", "data1", "o.b",  "[A1]")
        f("outer1", "data1", "o.b1", "[A0, A1]")
        f("outer1", "data1", "o.b2", "[A0, A1]")

        f("outer0", "data2", "o.b",  "[]")
        f("outer0", "data2", "o.b1", "[B0]")
        f("outer0", "data2", "o.b2", "[B1]")
        f("outer1", "data2", "o.b",  "[B0, B1]")
        f("outer1", "data2", "o.b1", "[B1]")
        f("outer1", "data2", "o.b2", "[B0]")
    }
}
