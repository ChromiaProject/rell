/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.expr.expr

import net.postchain.rell.base.testutils.BaseRellTest
import org.junit.Test

class NullAnalysisLogicTest: BaseRellTest(false) {
    @Test fun testAndOldOld() {
        initLib()

        val tpl = "{ var x = intz(123); {{ ASRT(%s); ASRT(%s); } RET }}"
        chkLogic(tpl, listOf("true", "true"), "maybe")
        chkLogic(tpl, listOf("x != null", "x != null"), "no")
        chkLogic(tpl, listOf("x != null", "x == null"), "maybe")
        chkLogic(tpl, listOf("x == null", "x != null"), "maybe")
        chkLogic(tpl, listOf("x == null", "x == null"), "yes")
    }

    @Test fun testAndOldNew() {
        initLib()

        val tpl = "{ var x = intz(123); {{ ASRT(%s); x = %s; } RET }}"
        chkLogic(tpl, listOf("x != null", "intz(456)"), "maybe")
        chkLogic(tpl, listOf("x != null", "456"), "no")
        chkLogic(tpl, listOf("x != null", "null"), "yes")
        chkLogic(tpl, listOf("x == null", "intz(456)"), "maybe")
        chkLogic(tpl, listOf("x == null", "456"), "no")
        chkLogic(tpl, listOf("x == null", "null"), "yes")
    }

    @Test fun testAndOldBoth() {
        initLib()

        val tpl = "{ var x = intz(123); {{ ASRT(%s); if (bool(1)) x = %s; else ASRT(%s); } RET }}"
        chkLogic(tpl, listOf("x != null", "intz(456)", "x != null"), "maybe")
        chkLogic(tpl, listOf("x != null", "intz(456)", "x == null"), "maybe")
        chkLogic(tpl, listOf("x != null", "456", "x != null"), "no")
        chkLogic(tpl, listOf("x != null", "456", "x == null"), "maybe")
        chkLogic(tpl, listOf("x != null", "null", "x != null"), "maybe")
        chkLogic(tpl, listOf("x != null", "null", "x == null"), "maybe")
        chkLogic(tpl, listOf("x == null", "intz(456)", "x != null"), "maybe")
        chkLogic(tpl, listOf("x == null", "intz(456)", "x == null"), "maybe")
        chkLogic(tpl, listOf("x == null", "456", "x != null"), "maybe")
        chkLogic(tpl, listOf("x == null", "456", "x == null"), "maybe")
        chkLogic(tpl, listOf("x == null", "null", "x != null"), "maybe")
        chkLogic(tpl, listOf("x == null", "null", "x == null"), "yes")
    }

    @Test fun testAndOldNone() {
        initLib()

        val tpl = "{ var x = intz(123); var y = intz(456); {{ ASRT(%s); y!!; } RET }}"
        chkLogic(tpl, listOf("x != null"), "no")
        chkLogic(tpl, listOf("x == null"), "yes")
    }

    @Test fun testAndNewOld() {
        initLib()

        val tpl = "{ var x = intz(123); {{ x = %s; ASRT(%s); } RET }}"
        chkLogic(tpl, listOf("intz(456)", "x != null"), "no")
        chkLogic(tpl, listOf("intz(456)", "x == null"), "yes")
        chkLogic(tpl, listOf("456", "x != null"), "no")
        chkLogic(tpl, listOf("456", "x == null"), "maybe")
        chkLogic(tpl, listOf("null", "x != null"), "maybe")
        chkLogic(tpl, listOf("null", "x == null"), "yes")
    }

    @Test fun testAndNewNew() {
        initLib()

        val tpl = "{ var x = intz(123); {{ x = %s; x = %s; } RET }}"
        chkLogic(tpl, listOf("intz(456)", "intz(789)"), "maybe")
        chkLogic(tpl, listOf("intz(456)", "789"), "no")
        chkLogic(tpl, listOf("intz(456)", "null"), "yes")
        chkLogic(tpl, listOf("456", "intz(789)"), "maybe")
        chkLogic(tpl, listOf("456", "789"), "no")
        chkLogic(tpl, listOf("456", "null"), "yes")
        chkLogic(tpl, listOf("null", "intz(789)"), "maybe")
        chkLogic(tpl, listOf("null", "789"), "no")
        chkLogic(tpl, listOf("null", "null"), "yes")
    }

    @Test fun testAndNewBoth() {
        initLib()

        val tpl = "{ var x = intz(123); {{ x = %s; if (bool(1)) x = %s; else ASRT(%s); } RET }}"
        chkLogic(tpl, listOf("intz(456)", "intz(789)", "x != null"), "maybe")
        chkLogic(tpl, listOf("intz(456)", "intz(789)", "x == null"), "maybe")
        chkLogic(tpl, listOf("intz(456)", "789", "x != null"), "no")
        chkLogic(tpl, listOf("intz(456)", "789", "x == null"), "maybe")
        chkLogic(tpl, listOf("intz(456)", "null", "x != null"), "maybe")
        chkLogic(tpl, listOf("intz(456)", "null", "x == null"), "yes")
        chkLogic(tpl, listOf("456", "intz(789)", "x != null"), "maybe")
        chkLogic(tpl, listOf("456", "intz(789)", "x == null"), "maybe")
        chkLogic(tpl, listOf("456", "789", "x != null"), "no")
        chkLogic(tpl, listOf("456", "789", "x == null"), "maybe")
        chkLogic(tpl, listOf("456", "null", "x != null"), "maybe")
        chkLogic(tpl, listOf("456", "null", "x == null"), "maybe")
        chkLogic(tpl, listOf("null", "intz(789)", "x != null"), "maybe")
        chkLogic(tpl, listOf("null", "intz(789)", "x == null"), "maybe")
        chkLogic(tpl, listOf("null", "789", "x != null"), "maybe")
        chkLogic(tpl, listOf("null", "789", "x == null"), "maybe")
        chkLogic(tpl, listOf("null", "null", "x != null"), "maybe")
        chkLogic(tpl, listOf("null", "null", "x == null"), "yes")
    }

    @Test fun testAndNewNone() {
        initLib()

        val tpl = "{ var x = intz(123); var y = intz(321); {{ x = %s; y!!; } RET }}"
        chkLogic(tpl, listOf("intz(456)"), "maybe")
        chkLogic(tpl, listOf("456"), "no")
        chkLogic(tpl, listOf("null"), "yes")
    }

    @Test fun testAndBothOld() {
        initLib()

        val tpl = "{ var x = intz(123); {{ if (bool(1)) x = %s; else ASRT(%s); ASRT(%s); } RET }}"
        chkLogic(tpl, listOf("intz(456)", "x != null", "x != null"), "no")
        chkLogic(tpl, listOf("intz(456)", "x != null", "x == null"), "maybe")
        chkLogic(tpl, listOf("intz(456)", "x == null", "x != null"), "maybe")
        chkLogic(tpl, listOf("intz(456)", "x == null", "x == null"), "yes")
        chkLogic(tpl, listOf("456", "x != null", "x != null"), "no")
        chkLogic(tpl, listOf("456", "x != null", "x == null"), "maybe")
        chkLogic(tpl, listOf("456", "x == null", "x != null"), "maybe")
        chkLogic(tpl, listOf("456", "x == null", "x == null"), "maybe")
        chkLogic(tpl, listOf("null", "x != null", "x != null"), "maybe")
        chkLogic(tpl, listOf("null", "x != null", "x == null"), "maybe")
        chkLogic(tpl, listOf("null", "x == null", "x != null"), "maybe")
        chkLogic(tpl, listOf("null", "x == null", "x == null"), "yes")
    }

    @Test fun testAndBothNew() {
        initLib()

        val tpl = "{ var x = intz(123); {{ if (bool(1)) x = %s; else ASRT(%s); x = %s; } RET }}"
        chkLogic(tpl, listOf("intz(456)", "x != null", "intz(789)"), "maybe")
        chkLogic(tpl, listOf("intz(456)", "x != null", "789"), "no")
        chkLogic(tpl, listOf("intz(456)", "x != null", "null"), "yes")
        chkLogic(tpl, listOf("intz(456)", "x == null", "intz(789)"), "maybe")
        chkLogic(tpl, listOf("intz(456)", "x == null", "789"), "no")
        chkLogic(tpl, listOf("intz(456)", "x == null", "null"), "yes")
        chkLogic(tpl, listOf("456", "x != null", "intz(789)"), "maybe")
        chkLogic(tpl, listOf("456", "x != null", "789"), "no")
        chkLogic(tpl, listOf("456", "x != null", "null"), "yes")
        chkLogic(tpl, listOf("456", "x == null", "intz(789)"), "maybe")
        chkLogic(tpl, listOf("456", "x == null", "789"), "no")
        chkLogic(tpl, listOf("456", "x == null", "null"), "yes")
        chkLogic(tpl, listOf("null", "x != null", "intz(789)"), "maybe")
        chkLogic(tpl, listOf("null", "x != null", "789"), "no")
        chkLogic(tpl, listOf("null", "x != null", "null"), "yes")
        chkLogic(tpl, listOf("null", "x == null", "intz(789)"), "maybe")
        chkLogic(tpl, listOf("null", "x == null", "789"), "no")
        chkLogic(tpl, listOf("null", "x == null", "null"), "yes")
    }

    @Test fun testAndBothBoth() {
        initLib()

        val tpl = "{ var x = intz(123); {{ if (bool(1)) x = %s; else ASRT(%s); if (bool(1)) x = %s; else ASRT(%s); } RET }}"
        chkLogic(tpl, listOf("intz(456)", "x != null", "intz(789)", "x != null"), "maybe")
        chkLogic(tpl, listOf("intz(456)", "x != null", "intz(789)", "x == null"), "maybe")
        chkLogic(tpl, listOf("intz(456)", "x != null", "789", "x != null"), "no")
        chkLogic(tpl, listOf("intz(456)", "x != null", "789", "x == null"), "maybe")
        chkLogic(tpl, listOf("intz(456)", "x != null", "null", "x != null"), "maybe")
        chkLogic(tpl, listOf("intz(456)", "x != null", "null", "x == null"), "maybe")
        chkLogic(tpl, listOf("intz(456)", "x == null", "intz(789)", "x != null"), "maybe")
        chkLogic(tpl, listOf("intz(456)", "x == null", "intz(789)", "x == null"), "maybe")
        chkLogic(tpl, listOf("intz(456)", "x == null", "789", "x != null"), "maybe")
        chkLogic(tpl, listOf("intz(456)", "x == null", "789", "x == null"), "maybe")
        chkLogic(tpl, listOf("intz(456)", "x == null", "null", "x != null"), "maybe")
        chkLogic(tpl, listOf("intz(456)", "x == null", "null", "x == null"), "yes")
        chkLogic(tpl, listOf("456", "x != null", "intz(789)", "x != null"), "maybe")
        chkLogic(tpl, listOf("456", "x != null", "intz(789)", "x == null"), "maybe")
        chkLogic(tpl, listOf("456", "x != null", "789", "x != null"), "no")
        chkLogic(tpl, listOf("456", "x != null", "789", "x == null"), "maybe")
        chkLogic(tpl, listOf("456", "x != null", "null", "x != null"), "maybe")
        chkLogic(tpl, listOf("456", "x != null", "null", "x == null"), "maybe")
        chkLogic(tpl, listOf("456", "x == null", "intz(789)", "x != null"), "maybe")
        chkLogic(tpl, listOf("456", "x == null", "intz(789)", "x == null"), "maybe")
        chkLogic(tpl, listOf("456", "x == null", "789", "x != null"), "maybe")
        chkLogic(tpl, listOf("456", "x == null", "789", "x == null"), "maybe")
        chkLogic(tpl, listOf("456", "x == null", "null", "x != null"), "maybe")
        chkLogic(tpl, listOf("456", "x == null", "null", "x == null"), "maybe")
    }

    @Test fun testOrOldOld() {
        initLib()

        val tpl = "{ var x = intz(123); { if (bool(1)) ASRT(%s); else ASRT(%s); RET }}"
        chkLogic(tpl, listOf("true", "true"), "maybe")
        chkLogic(tpl, listOf("x != null", "x != null"), "no")
        chkLogic(tpl, listOf("x != null", "x == null"), "maybe")
        chkLogic(tpl, listOf("x == null", "x != null"), "maybe")
        chkLogic(tpl, listOf("x == null", "x == null"), "yes")
    }

    @Test fun testOrOldNew() {
        initLib()

        val tpl = "{ var x = intz(123); { if (bool(1)) ASRT(%s); else x = %s; RET }}"
        chkLogic(tpl, listOf("x != null", "intz(456)"), "maybe")
        chkLogic(tpl, listOf("x != null", "456"), "no")
        chkLogic(tpl, listOf("x != null", "null"), "maybe")
        chkLogic(tpl, listOf("x == null", "intz(456)"), "maybe")
        chkLogic(tpl, listOf("x == null", "456"), "maybe")
        chkLogic(tpl, listOf("x == null", "null"), "yes")
    }

    @Test fun testOrOldBoth() {
        initLib()

        val tpl = "{ var x = intz(123); {{ if (bool(1)) ASRT(%s); else if (bool(1)) x = %s; else ASRT(%s); } RET }}"
        chkLogic(tpl, listOf("x != null", "intz(456)", "x != null"), "maybe")
        chkLogic(tpl, listOf("x != null", "intz(456)", "x == null"), "maybe")
        chkLogic(tpl, listOf("x != null", "456", "x != null"), "no")
        chkLogic(tpl, listOf("x != null", "456", "x == null"), "maybe")
        chkLogic(tpl, listOf("x != null", "null", "x != null"), "maybe")
        chkLogic(tpl, listOf("x != null", "null", "x == null"), "maybe")
        chkLogic(tpl, listOf("x == null", "intz(456)", "x != null"), "maybe")
        chkLogic(tpl, listOf("x == null", "intz(456)", "x == null"), "maybe")
        chkLogic(tpl, listOf("x == null", "456", "x != null"), "maybe")
        chkLogic(tpl, listOf("x == null", "456", "x == null"), "maybe")
        chkLogic(tpl, listOf("x == null", "null", "x != null"), "maybe")
        chkLogic(tpl, listOf("x == null", "null", "x == null"), "yes")
    }

    @Test fun testOrOldNone() {
        initLib()

        val tpl = "{ var x = intz(123); var y = intz(456); { if (bool(1)) ASRT(%s); else y!!; RET }}"
        chkLogic(tpl, listOf("x != null"), "maybe")
        chkLogic(tpl, listOf("x == null"), "maybe")
    }

    @Test fun testOrNewOld() {
        initLib()

        val tpl = "{ var x = intz(123); { if (bool(1)) x = %s; else ASRT(%s); RET }}"
        chkLogic(tpl, listOf("intz(456)", "x != null"), "maybe")
        chkLogic(tpl, listOf("intz(456)", "x == null"), "maybe")
        chkLogic(tpl, listOf("456", "x != null"), "no")
        chkLogic(tpl, listOf("456", "x == null"), "maybe")
        chkLogic(tpl, listOf("null", "x != null"), "maybe")
        chkLogic(tpl, listOf("null", "x == null"), "yes")
    }

    @Test fun testOrNewNew() {
        initLib()

        val tpl = "{ var x = intz(123); {{ if (bool(1)) x = %s; else x = %s; } RET }}"
        chkLogic(tpl, listOf("intz(456)", "intz(789)"), "maybe")
        chkLogic(tpl, listOf("intz(456)", "789"), "maybe")
        chkLogic(tpl, listOf("intz(456)", "null"), "maybe")
        chkLogic(tpl, listOf("456", "intz(789)"), "maybe")
        chkLogic(tpl, listOf("456", "789"), "no")
        chkLogic(tpl, listOf("456", "null"), "maybe")
        chkLogic(tpl, listOf("null", "intz(789)"), "maybe")
        chkLogic(tpl, listOf("null", "789"), "maybe")
        chkLogic(tpl, listOf("null", "null"), "yes")
    }

    @Test fun testOrNewBoth() {
        initLib()

        val tpl = "{ var x = intz(123); {{ if (bool(1)) x = %s; else if (bool(2)) x = %s; else ASRT(%s); } RET }}"
        chkLogic(tpl, listOf("intz(456)", "intz(789)", "x != null"), "maybe")
        chkLogic(tpl, listOf("intz(456)", "intz(789)", "x == null"), "maybe")
        chkLogic(tpl, listOf("intz(456)", "789", "x != null"), "maybe")
        chkLogic(tpl, listOf("intz(456)", "789", "x == null"), "maybe")
        chkLogic(tpl, listOf("intz(456)", "null", "x != null"), "maybe")
        chkLogic(tpl, listOf("intz(456)", "null", "x == null"), "maybe")
        chkLogic(tpl, listOf("456", "intz(789)", "x != null"), "maybe")
        chkLogic(tpl, listOf("456", "intz(789)", "x == null"), "maybe")
        chkLogic(tpl, listOf("456", "789", "x != null"), "no")
        chkLogic(tpl, listOf("456", "789", "x == null"), "maybe")
        chkLogic(tpl, listOf("456", "null", "x != null"), "maybe")
        chkLogic(tpl, listOf("456", "null", "x == null"), "maybe")
        chkLogic(tpl, listOf("null", "intz(789)", "x != null"), "maybe")
        chkLogic(tpl, listOf("null", "intz(789)", "x == null"), "maybe")
        chkLogic(tpl, listOf("null", "789", "x != null"), "maybe")
        chkLogic(tpl, listOf("null", "789", "x == null"), "maybe")
        chkLogic(tpl, listOf("null", "null", "x != null"), "maybe")
        chkLogic(tpl, listOf("null", "null", "x == null"), "yes")
    }

    @Test fun testOrNewNone() {
        initLib()

        val tpl = "{ var x = intz(123); var y = intz(321); {{ if (bool(1)) x = %s; else y!!; } RET }}"
        chkLogic(tpl, listOf("intz(456)"), "maybe")
        chkLogic(tpl, listOf("456"), "maybe")
        chkLogic(tpl, listOf("null"), "maybe")
    }

    @Test fun testOrBothBoth() {
        initLib()

        val tpl = """{
            var x = intz(123);
            if (bool(1)) {
                if (bool(2)) x = %s; else ASRT(%s);
            } else {
                if (bool(3)) x = %s; else ASRT(%s);
            }
            RET
        }"""

        chkLogic(tpl, listOf("intz(456)", "x != null", "intz(789)", "x != null"), "maybe")
        chkLogic(tpl, listOf("intz(456)", "x != null", "intz(789)", "x == null"), "maybe")
        chkLogic(tpl, listOf("intz(456)", "x != null", "789", "x != null"), "maybe")
        chkLogic(tpl, listOf("intz(456)", "x != null", "789", "x == null"), "maybe")
        chkLogic(tpl, listOf("intz(456)", "x != null", "null", "x != null"), "maybe")
        chkLogic(tpl, listOf("intz(456)", "x != null", "null", "x == null"), "maybe")
        chkLogic(tpl, listOf("intz(456)", "x == null", "intz(789)", "x != null"), "maybe")
        chkLogic(tpl, listOf("intz(456)", "x == null", "intz(789)", "x == null"), "maybe")
        chkLogic(tpl, listOf("intz(456)", "x == null", "789", "x != null"), "maybe")
        chkLogic(tpl, listOf("intz(456)", "x == null", "789", "x == null"), "maybe")
        chkLogic(tpl, listOf("intz(456)", "x == null", "null", "x != null"), "maybe")
        chkLogic(tpl, listOf("intz(456)", "x == null", "null", "x == null"), "maybe")

        chkLogic(tpl, listOf("456", "x != null", "intz(789)", "x != null"), "maybe")
        chkLogic(tpl, listOf("456", "x != null", "intz(789)", "x == null"), "maybe")
        chkLogic(tpl, listOf("456", "x != null", "789", "x != null"), "no")
        chkLogic(tpl, listOf("456", "x != null", "789", "x == null"), "maybe")
        chkLogic(tpl, listOf("456", "x != null", "null", "x != null"), "maybe")
        chkLogic(tpl, listOf("456", "x != null", "null", "x == null"), "maybe")
        chkLogic(tpl, listOf("456", "x == null", "intz(789)", "x != null"), "maybe")
        chkLogic(tpl, listOf("456", "x == null", "intz(789)", "x == null"), "maybe")
        chkLogic(tpl, listOf("456", "x == null", "789", "x != null"), "maybe")
        chkLogic(tpl, listOf("456", "x == null", "789", "x == null"), "maybe")
        chkLogic(tpl, listOf("456", "x == null", "null", "x != null"), "maybe")
        chkLogic(tpl, listOf("456", "x == null", "null", "x == null"), "maybe")

        chkLogic(tpl, listOf("null", "x != null", "intz(789)", "x != null"), "maybe")
        chkLogic(tpl, listOf("null", "x != null", "intz(789)", "x == null"), "maybe")
        chkLogic(tpl, listOf("null", "x != null", "789", "x != null"), "maybe")
        chkLogic(tpl, listOf("null", "x != null", "789", "x == null"), "maybe")
        chkLogic(tpl, listOf("null", "x != null", "null", "x != null"), "maybe")
        chkLogic(tpl, listOf("null", "x != null", "null", "x == null"), "maybe")
        chkLogic(tpl, listOf("null", "x == null", "intz(789)", "x != null"), "maybe")
        chkLogic(tpl, listOf("null", "x == null", "intz(789)", "x == null"), "maybe")
        chkLogic(tpl, listOf("null", "x == null", "789", "x != null"), "maybe")
        chkLogic(tpl, listOf("null", "x == null", "789", "x == null"), "maybe")
        chkLogic(tpl, listOf("null", "x == null", "null", "x != null"), "maybe")
        chkLogic(tpl, listOf("null", "x == null", "null", "x == null"), "yes")
    }

    private fun initLib() {
        tst.strictToString = false
        def("function intz(x: integer?): integer? = x;")
        def("function bool(x: integer): boolean = x != 0;")
    }

    private fun chkLogic(tpl: String, args: List<String>, exp: String) {
        val code = tpl
            .replace("ASRT", "_test.fake_assert")
            .replace("RET", "return _test.get_nulled(x);")
            .format(*args.toTypedArray())
        chkEx(code, exp)
    }
}
