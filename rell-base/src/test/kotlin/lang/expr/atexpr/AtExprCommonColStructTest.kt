/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.expr.atexpr

import kotlin.test.Test

class AtExprCommonColStructTest: AtExprCommonColBaseTest() {
    override fun impKind() = AtExprTestKind_Col_Struct()

    @Test fun testWhereImplicitOuterDirect_ColStruct() {
        tst.strictToString = false

        def("""
            struct id { s: text; }
            struct value { v: text; }
            struct ref { value; foo_value: value; bar_value: value; }
            struct outer { value; foo_value: value; bar_value: value; ref; }
            struct inner1 { id; value; }
            struct inner2 { id; foo_value: value; bar_value: value; }
            struct inner3 { id; value; foo_value: value; bar_value: value; }

            function get_ref() = ref(value = value('A'), foo_value = value('B'), bar_value = value('C'));
            function get_outer() = [outer(value = value('A'), foo_value = value('B'), bar_value = value('C'), ref = get_ref())];
            function get_inner1() = [
                inner1(id('I10'), value = value('A')),
                inner1(id('I11'), value = value('B')),
                inner1(id('I12'), value = value('C'))
            ];
            function get_inner2() = [
                inner2(id('I20'), foo_value = value('B'), bar_value = value('X')),
                inner2(id('I21'), foo_value = value('X'), bar_value = value('C'))
            ];
            function get_inner3() = [
                inner3(id('I30'), value = value('A'), foo_value = value('X'), bar_value = value('X')),
                inner3(id('I31'), value = value('X'), foo_value = value('B'), bar_value = value('X')),
                inner3(id('I32'), value = value('X'), foo_value = value('X'), bar_value = value('C'))
            ];
        """)

        chk("(o:get_outer()) @* {} ( (i:get_inner1()) @ { o.value } ( i.id.s ) )", "[I10]")
        chk("(o:get_outer()) @* {} ( (i:get_inner1()) @ { o.foo_value } ( i.id.s ) )", "[I11]")
        chk("(o:get_outer()) @* {} ( (i:get_inner1()) @ { o.bar_value } ( i.id.s ) )", "[I12]")
        chk("(o:get_outer()) @* {} ( (i:get_inner1()) @ { o.ref.value } ( i.id.s ) )", "[I10]")
        chk("(o:get_outer()) @* {} ( (i:get_inner1()) @ { o.ref.foo_value } ( i.id.s ) )", "[I11]")
        chk("(o:get_outer()) @* {} ( (i:get_inner1()) @ { o.ref.bar_value } ( i.id.s ) )", "[I12]")

        val err21 = "ct_err:at_where:var_manyattrs_type:0:value:value:[i:inner2.foo_value,i:inner2.bar_value]"
        val err22 = "ct_err:at_attr_type_ambig:0:value:[i:inner2.foo_value,i:inner2.bar_value]"
        chk("(o:get_outer()) @* {} ( (i:get_inner2()) @ { o.value } ( i.id.s ) )", err21)
        chk("(o:get_outer()) @* {} ( (i:get_inner2()) @ { o.foo_value } ( i.id.s ) )", "[I20]")
        chk("(o:get_outer()) @* {} ( (i:get_inner2()) @ { o.bar_value } ( i.id.s ) )", "[I21]")
        chk("(o:get_outer()) @* {} ( (i:get_inner2()) @ { o.ref.value } ( i.id.s ) )", err22)
        chk("(o:get_outer()) @* {} ( (i:get_inner2()) @ { o.ref.foo_value } ( i.id.s ) )", err22)
        chk("(o:get_outer()) @* {} ( (i:get_inner2()) @ { o.ref.bar_value } ( i.id.s ) )", err22)

        val err3 = "ct_err:at_attr_type_ambig:0:value:[i:inner3.value,i:inner3.foo_value,i:inner3.bar_value]"
        chk("(o:get_outer()) @* {} ( (i:get_inner3()) @ { o.value } ( i.id.s ) )", "[I30]")
        chk("(o:get_outer()) @* {} ( (i:get_inner3()) @ { o.foo_value } ( i.id.s ) )", "[I31]")
        chk("(o:get_outer()) @* {} ( (i:get_inner3()) @ { o.bar_value } ( i.id.s ) )", "[I32]")
        chk("(o:get_outer()) @* {} ( (i:get_inner3()) @ { o.ref.value } ( i.id.s ) )", err3)
        chk("(o:get_outer()) @* {} ( (i:get_inner3()) @ { o.ref.foo_value } ( i.id.s ) )", err3)
        chk("(o:get_outer()) @* {} ( (i:get_inner3()) @ { o.ref.bar_value } ( i.id.s ) )", err3)
    }
}
