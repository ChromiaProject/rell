/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.type

import net.postchain.rell.base.lib.type.Lib_BigIntegerMath
import net.postchain.rell.base.lib.type.Rt_BigIntegerValue
import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.utils.toImmList
import org.junit.Test
import java.math.BigInteger

class BigIntegerTest: BaseRellTest() {
    @Test fun testType() {
        chkFull("query q(a: big_integer) = _type_of(a);", listOf(Rt_BigIntegerValue.ZERO), "text[big_integer]")
        chk("_type_of(big_integer(0))", "text[big_integer]")
    }

    @Test fun testLiteral() {
        chk("_type_of(0)", "text[integer]")
        chk("_type_of(0L)", "text[big_integer]")
        chk("_type_of(123456)", "text[integer]")
        chk("_type_of(123456L)", "text[big_integer]")

        chk("0", "int[0]")
        chk("0L", "bigint[0]")
        chk("123456", "int[123456]")
        chk("123456L", "bigint[123456]")

        chk("9223372036854775807", "int[9223372036854775807]")
        chk("9223372036854775808", "ct_err:lex:int:range:9223372036854775808")
        chk("9223372036854775808L", "bigint[9223372036854775808]")
        chk("79228162514264337593543950335L", "bigint[79228162514264337593543950335]")
    }

    @Test fun testLiteralMax() {
        chk("${"0".repeat(999)}L", "bigint[0]")
        chk("${"0".repeat(1000)}L", "ct_err:lex:bigint:length:1001")
        chk("${"0".repeat(100000)}L", "ct_err:lex:bigint:length:100001")

        chk("${"9".repeat(999)}L", "bigint[${"9".repeat(999)}]")
        chk("${"9".repeat(1000)}L", "ct_err:lex:bigint:length:1001")
        chk("${"9".repeat(100000)}L", "ct_err:lex:bigint:length:100001")
    }

    // Maybe exponent notation for big_integer will be supported in the future.
    /*@Test*/ fun testLiteralExp() {
        chk("_type_of(1E1)", "text[decimal]")
        chk("_type_of(1E1L)", "text[big_integer]")
        chk("_type_of(5E6)", "text[decimal]")
        chk("_type_of(5E6L)", "text[big_integer]")

        chk("1E1", "dec[10]")
        chk("1E1L", "bigint[10]")
        chk("5E6", "dec[5000000]")
        chk("5E6L", "bigint[5000000]")

        chk("0E0", "bigint[0]")
        chk("0E1", "bigint[0]")
        chk("1E0", "bigint[1]")
        chk("1E1", "bigint[10]")

        chk("123E4L", "bigint[1230000]")
        chk("123E+4L", "bigint[1230000]")
        chk("123e4L", "bigint[1230000]")
        chk("123e+4L", "bigint[1230000]")
    }

    @Test fun testLiteralHex() {
        chk("_type_of(0x0)", "text[integer]")
        chk("_type_of(0x0L)", "text[big_integer]")
        chk("_type_of(0x123abc)", "text[integer]")
        chk("_type_of(0x123abcL)", "text[big_integer]")

        chk("0x0", "int[0]")
        chk("0x0L", "bigint[0]")
        chk("0x123abc", "int[1194684]")
        chk("0x123abcL", "bigint[1194684]")

        chk("0x7fffffffffffffff", "int[9223372036854775807]")
        chk("0x8000000000000000", "ct_err:lex:int:range:0x8000000000000000")
        chk("0x8000000000000000L", "bigint[9223372036854775808]")
        chk("0xffffffffffffffffffffffffL", "bigint[79228162514264337593543950335]")
    }

    @Test fun testLiteralHexMax() {
        chk("0x${"0".repeat(997)}L", "bigint[0]")
        chk("0x${"0".repeat(998)}L", "ct_err:lex:bigint:length:1001")
        chk("0x${"0".repeat(100000)}L", "ct_err:lex:bigint:length:100003")

        chk("0x${"f".repeat(997)}L", "bigint[${BigInteger("f".repeat(997),16)}]")
        chk("0x${"f".repeat(998)}L", "ct_err:lex:bigint:length:1001")
        chk("0x${"f".repeat(100000)}L", "ct_err:lex:bigint:length:100003")
    }

    @Test fun testLiteralInvalid() {
        chk("123l", "ct_err:lex:number_end")
        chk("0x123l", "ct_err:lex:number_end")

        chk("123L4", "ct_err:lex:number_end")
        chk("123LL", "ct_err:lex:number_end")
        chk("123La", "ct_err:lex:number_end")

        chk("0x123L4", "ct_err:lex:number_end")
        chk("0x123LL", "ct_err:lex:number_end")
        chk("0x123La", "ct_err:lex:number_end")

        chk("1E1L4", "ct_err:lex:number_end")
        chk("1E1LL", "ct_err:lex:number_end")
        chk("1E1La", "ct_err:lex:number_end")
        chk("1EL", "ct_err:lex:number:no_digit_after_exp")
        chk("1E+L", "ct_err:lex:number:no_digit_after_exp")
        chk("1E-L", "ct_err:lex:number:no_digit_after_exp")

        chk("1E-1L", "ct_err:lex:number_end")
        chk("10E-1L", "ct_err:lex:number_end")
        chk("1000000E-1L", "ct_err:lex:number_end")
    }

    @Test fun testPromotionVarDeclaration() {
        chkEx("{ var x: big_integer = 123; return _type_of(x); }", "text[big_integer]")
        chkEx("{ var x: big_integer = 123; return x; }", "bigint[123]")
        chkEx("{ var x: big_integer? = 123; return x; }", "bigint[123]")
        chkEx("{ var x: big_integer? = _nullable_int(123); return x; }", "bigint[123]")
        chkEx("{ var x: big_integer? = _nullable_int(null); return x; }", "null")

        chkEx("{ var (x: big_integer, y: text) = (123L, 'Hello'); return x; }", "bigint[123]")
        chkEx("{ var (x: big_integer, y: text) = (123, 'Hello'); return x; }", "bigint[123]")
        chkEx("{ var (x: big_integer?, y: text) = (123, 'Hello'); return x; }", "bigint[123]")
        chkEx("{ var (x: big_integer?, y: text) = (_nullable_int(123), 'Hello'); return x; }", "bigint[123]")
        chkEx("{ var (x: big_integer?, y: text) = (_nullable_int(null), 'Hello'); return x; }", "null")
    }

    @Test fun testPromotionAssignment() {
        chkEx("{ var x = 123L; x = 456; return x; }", "bigint[456]")
        chkEx("{ var x = 123L; x += 456; return x; }", "bigint[579]")
        chkEx("{ var x = 123L; x *= 456; return x; }", "bigint[56088]")

        chkEx("{ var x = 123; x = 456L; return x; }", "ct_err:stmt_assign_type:[integer]:[big_integer]")
        chkEx("{ var x = 123; x += 456L; return x; }", "ct_err:binop_operand_type:+=:[integer]:[big_integer]")
        chkEx("{ var x = 123; x *= 456L; return x; }", "ct_err:binop_operand_type:*=:[integer]:[big_integer]")
    }

    @Test fun testPromotionUserFunction() {
        def("function f(x: big_integer, y: integer): big_integer = x + big_integer(y);")
        def("function g(x: big_integer, y: big_integer): big_integer = x * y;")

        chk("f(123, 456)", "bigint[579]")
        chk("f(123L, 456)", "bigint[579]")
        chk("f(123, 456L)", "ct_err:expr_call_argtype:[f]:1:y:integer:big_integer")
        chk("f(123L, 456L)", "ct_err:expr_call_argtype:[f]:1:y:integer:big_integer")

        chk("g(123, 456)", "bigint[56088]")
        chk("g(123L, 456)", "bigint[56088]")
        chk("g(123, 456L)", "bigint[56088]")
        chk("g(123L, 456L)", "bigint[56088]")
    }

    @Test fun testIncrement() {
        chkEx("{ var x: big_integer = 123; x++; return x; }", "bigint[124]")
        chkEx("{ var x: big_integer = 123; x--; return x; }", "bigint[122]")
        chkEx("{ var x: big_integer = 123; ++x; return x; }", "bigint[124]")
        chkEx("{ var x: big_integer = 123; --x; return x; }", "bigint[122]")

        chkEx("{ var x: big_integer = 123; val y = x++; return (x, y); }", "(bigint[124],bigint[123])")
        chkEx("{ var x: big_integer = 123; val y = x--; return (x, y); }", "(bigint[122],bigint[123])")
        chkEx("{ var x: big_integer = 123; val y = ++x; return (x, y); }", "(bigint[124],bigint[124])")
        chkEx("{ var x: big_integer = 123; val y = --x; return (x, y); }", "(bigint[122],bigint[122])")
    }

    @Test fun testCreateUpdate() {
        tstCtx.useSql = true
        def("entity user { name; mutable value: big_integer; }")

        val expMax = "9".repeat(Lib_BigIntegerMath.PRECISION)

        chkOp("{ create user('Bob', big_integer.MAX_VALUE); }")
        chk("(user @ { 'Bob' }).value", "bigint[$expMax]")

        chkOp("{ create user('Alice', big_integer.MIN_VALUE); }")
        chk("(user @ { 'Alice' }).value", "bigint[-$expMax]")

        chkOp("{ update user @ { 'Bob' } ( value = big_integer.MIN_VALUE ); }")
        chk("(user @ { 'Bob' }).value", "bigint[-$expMax]")

        chkOp("{ update user @ { 'Alice' } ( value = big_integer.MAX_VALUE ); }")
        chk("(user @ { 'Alice' }).value", "bigint[$expMax]")

        chkOp("{ update user @ { 'Bob' } ( value += big_integer(1) ); }")
        chk("(user @ { 'Bob' }).value", "bigint[-${expMax.dropLast(1)}8]")

        chkOp("{ update user @ { 'Alice' } ( value += 1 ); }", "rt_err:sqlerr:0") // Overflow
    }

    @Test fun testUpdateDivFull() {
        tstCtx.useSql = true
        def("entity user { name; mutable value: big_integer; }")
        tst.insert("c0.user", "name,value", "100,'Bob',1000000", "101,'Alice',-1000000")
        chkData("user(100,Bob,1000000)", "user(101,Alice,-1000000)")

        chkOp("update user @*{'Bob'} ( .value /= 333333 );")
        chkData("user(100,Bob,3)", "user(101,Alice,-1000000)")

        chkOp("update user @*{'Alice'} ( .value /= 333334 );")
        chkData("user(100,Bob,3)", "user(101,Alice,-2)")

        chkOp("update user @*{} ( .value /= 3 );")
        chkData("user(100,Bob,1)", "user(101,Alice,0)")
    }

    @Test fun testUpdateDivShort() {
        tstCtx.useSql = true
        def("entity user { name; mutable value: big_integer; }")
        tst.insert("c0.user", "name,value", "100,'Bob',1000000", "101,'Alice',-1000000")
        chkData("user(100,Bob,1000000)", "user(101,Alice,-1000000)")

        chkOp("val u = user @{'Bob'}; u.value /= 333333;")
        chkData("user(100,Bob,3)", "user(101,Alice,-1000000)")

        chkOp("val u = user @{'Alice'}; u.value /= 333334;")
        chkData("user(100,Bob,3)", "user(101,Alice,-2)")
    }

    @Test fun testUpdateModFull() {
        tstCtx.useSql = true
        def("entity user { name; mutable value: big_integer; }")
        tst.insert("c0.user", "name,value", "100,'Bob',111", "101,'Alice',-1000000")
        chkData("user(100,Bob,111)", "user(101,Alice,-1000000)")

        chkOp("update user @*{'Bob'} ( .value %= 77 );")
        chkData("user(100,Bob,34)", "user(101,Alice,-1000000)")

        chkOp("update user @*{'Alice'} ( .value %= 9999 );")
        chkData("user(100,Bob,34)", "user(101,Alice,-100)")

        chkOp("update user @*{} ( .value %= -7 );")
        chkData("user(100,Bob,6)", "user(101,Alice,-2)")
    }

    @Test fun testUpdateModShort() {
        tstCtx.useSql = true
        def("entity user { name; mutable value: big_integer; }")
        tst.insert("c0.user", "name,value", "100,'Bob',111", "101,'Alice',-1000000")
        chkData("user(100,Bob,111)", "user(101,Alice,-1000000)")

        chkOp("val u = user @{'Bob'}; u.value %= 77;")
        chkData("user(100,Bob,34)", "user(101,Alice,-1000000)")

        chkOp("val u = user @{'Alice'}; u.value %= 9999;")
        chkData("user(100,Bob,34)", "user(101,Alice,-100)")
    }

    class BigIntVals {
        val digs = Lib_BigIntegerMath.PRECISION
        val lim1 = limitMinus(1)
        val lim2 = limitMinus(2)
        val limDiv10 = limitDiv(10)
    }

    companion object {
        val LIMIT = BigInteger.TEN.pow(Lib_BigIntegerMath.PRECISION)

        fun limitMinus(minus: Long) = "" + (LIMIT - BigInteger.valueOf(minus))
        fun limitDiv(div: Long) = "" + (LIMIT / BigInteger.valueOf(div))

        val ADD_TEST_CASES = makeAddCases()
        val SUB_TEST_CASES = makeSubCases()

        fun makeAddCases(): List<DecimalTest.DecAddCase> {
            val list = mutableListOf<DecimalTest.DecAddCase>()

            addCase(list, "+", "123", "456", "579")
            addCase(list, "+", "12345", "67890", "80235")

            // Extreme values.
            val d = DecimalTest.DecVals()
            addCase(list, "+", "${d.lim2}", "1", "${d.lim1}")
            addCase(list, "+", "${d.lim2}", "2", null)
            addCase(list, "+", "-${d.lim2}", "-1", "-${d.lim1}")
            addCase(list, "+", "-${d.lim2}", "-2", null)

            return list.toImmList()
        }

        fun makeSubCases(): List<DecimalTest.DecAddCase> {
            val list = mutableListOf<DecimalTest.DecAddCase>()

            addCase(list, "-", "12345", "67890", "-55545")
            addCase(list, "-", "67890", "12345", "55545")

            // Extreme values.
            val d = DecimalTest.DecVals()
            addCase(list, "-", "-${d.lim2}", "1", "-${d.lim1}")
            addCase(list, "-", "-${d.lim2}", "2", null)
            addCase(list, "-", "${d.lim2}", "-1", "${d.lim1}")
            addCase(list, "-", "${d.lim2}", "-2", null)

            return list.toImmList()
        }

        private fun addCase(list: MutableList<DecimalTest.DecAddCase>, op: String, a: String, b: String, expected: String?) {
            list.add(DecimalTest.DecAddCase(op, a, b, expected))
        }
    }
}
