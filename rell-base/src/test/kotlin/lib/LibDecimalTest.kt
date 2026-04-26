/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
@file:OptIn(net.postchain.rell.base.sql.RawSqlAccess::class)

package net.postchain.rell.base.lib

import net.postchain.rell.base.lib.type.Lib_DecimalMath
import net.postchain.rell.base.runtime.RawSqlBoundStatement
import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.testutils.RellCodeTester
import java.math.BigDecimal
import kotlin.test.Test

class LibDecimalTest: BaseRellTest() {
    @Test fun testConstants() {
        chk("decimal.PRECISION", "int[131092]")
        chk("decimal.SCALE", "int[20]")
        chk("decimal.INT_DIGITS", "int[131072]")
        chk("decimal.MIN_VALUE", "dec[0.00000000000000000001]")

        val expMax = "9".repeat(Lib_DecimalMath.DECIMAL_INT_DIGITS) + "." + "9".repeat(Lib_DecimalMath.DECIMAL_FRAC_DIGITS)
        chk("decimal.MAX_VALUE", "dec[$expMax]")
    }

    //TODO move decimal library tests from OperatorsBaseTest here (need to support executing same test as interpreted and DB)

    //TODO support pow(), or delete the function and the test (if supporting, add non-integer power test cases to the test)
    /*@Test*/ fun testPow() {
        chk("decimal('2').pow(16)", "dec[65536]")
        chk("decimal('2').pow(decimal('4'))", "ct_err:expr_call_badargs:decimal.pow:[decimal]")
        chk("decimal('2').pow(decimal('0.5'))", "ct_err:expr_call_badargs:decimal.pow:[decimal]")
        chk("decimal('2').pow(-1)", "rt_err:decimal.pow:negative_power:-1")

        chk("decimal('0').pow(0)", "dec[1]")
        chk("decimal('1').pow(0)", "dec[1]")
        chk("decimal('0').pow(1)", "dec[0]")

        chk("decimal('2').pow(4)", "dec[16]")
        chk("decimal('2').pow(16)", "dec[65536]")
        chk("decimal('2').pow(256)", "dec[340282366920938463463374607431768211456]")
        chk("decimal('123456789').pow(5)", "dec[28679718602997181072337614380936720482949]")

        chk("decimal('123.456').pow(2)", "dec[15241.383936]")
        chk("decimal('123.456').pow(3)", "dec[1881640.295202816]")
        chk("decimal('123.456').pow(4)", "dec[232299784.284558852096]")
        chk("decimal('123.456').pow(5)", "dec[28678802168.634497644363776]")
        chk("decimal('123.456').pow(6)", "dec[3540570200530.940541182574329856]")
        chk("decimal('123.456').pow(7)", "dec[437104634676747.79545223589646670234]")
        chk("decimal('123.456').pow(8)", "dec[53963189778652575.83535123483419320359]")
        chk("decimal('123.456').pow(10)", "dec[822473693827674765061.94409151963591676602]")

        chk("decimal('10').pow(${Lib_DecimalMath.DECIMAL_INT_DIGITS-1})", "...")
        chk("decimal('10').pow(${Lib_DecimalMath.DECIMAL_INT_DIGITS})", "*error*")
        chk("decimal.MAX_VALUE.pow(1)", "...")
        chk("decimal.MAX_VALUE.pow(2)", "*error*")
        chk("decimal.MAX_VALUE.pow(100)", "*error*")
        chk("decimal.MAX_VALUE.pow(1000000)", "*error*")
        chk("decimal.MAX_VALUE.pow(1000000000000)", "*error*")
    }

    //TODO support decimal.sqrt() on Java 9
    /*@Test*/ fun testSqrt() {
        chk("decimal('0').sqrt()", "dec[0]")
        chk("decimal('1').sqrt()", "dec[1]")
        chk("decimal('0.01').sqrt()", "dec[0.1]")
        chk("decimal('-1').sqrt()", "*error*")
        chk("decimal('-123').sqrt()", "*error*")

        chk("decimal('4').sqrt()", "dec[2]")
        chk("decimal('64').sqrt()", "dec[8]")
        chk("decimal('65536').sqrt()", "dec[256]")
        chk("decimal('4294967296').sqrt()", "dec[65536]")
        chk("decimal('18446744073709551616').sqrt()", "dec[4294967296]")

        chk("decimal('2').sqrt()", "dec[1.4142135623730950488]")
        chk("decimal('3').sqrt()", "dec[1.7320508075688772935]")
        chk("decimal('123').sqrt()", "dec[11.09053650640941716205]")
        chk("decimal('123456').sqrt()", "dec[351.36306009596398663933]")
        chk("decimal('1234567891011121314151618192021222324252627282930').sqrt()", "dec[1111111106510560137399502.57419163236096335087]")
    }

    @Test fun testToTextScientific() {
        chk("decimal('0').to_text(false)", "text[0]")
        chk("decimal('0').to_text(true)", "text[0]")
        chk("decimal('123.456').to_text(false)", "text[123.456]")
        chk("decimal('123.456').to_text(true)", "text[1.23456E+2]")
        chk("decimal('-123.456').to_text(false)", "text[-123.456]")
        chk("decimal('-123.456').to_text(true)", "text[-1.23456E+2]")

        chk("decimal('12.34e20').to_text(false)", "text[1234000000000000000000]")
        chk("decimal('12.34e20').to_text(true)", "text[1.234E+21]")
        chk("decimal('-12.34e20').to_text(false)", "text[-1234000000000000000000]")
        chk("decimal('-12.34e20').to_text(true)", "text[-1.234E+21]")
        chk("decimal('12.34e500').to_text(false)", "text[1234${"0".repeat(498)}]")
        chk("decimal('12.34e500').to_text(true)", "text[1.234E+501]")

        val p = Lib_DecimalMath.DECIMAL_INT_DIGITS - 3
        chk("decimal('123.45678910111213141516e$p').to_text(false)", "text[12345678910111213141516${"0".repeat(p-20)}]")
        chk("decimal('123.45678910111213141516e$p').to_text(true)", "text[1.23456789101112131415E+${p+2}]")

        chk("decimal('12.34e-18').to_text(false)", "text[0.00000000000000001234]")
        chk("decimal('12.34e-18').to_text(true)", "text[1.234E-17]")
        chk("decimal('-12.34e-18').to_text(false)", "text[-0.00000000000000001234]")
        chk("decimal('-12.34e-18').to_text(true)", "text[-1.234E-17]")

        val t = "0123456789"
        val f = t.repeat(3)
        chk("decimal('1.${f}').to_text(false)", "text[1.${t}${t}]")
        chk("decimal('1.${f}').to_text(true)", "text[1.${t}${t}]")
        chk("decimal('1.${f}e5').to_text(false)", "text[101234.56789${t}01235]")
        chk("decimal('1.${f}e5').to_text(true)", "text[1.0123456789${t}E+5]")
        chk("decimal('1.${f}e10').to_text(false)", "text[1${t}.${t}${t}]")
        chk("decimal('1.${f}e10').to_text(true)", "text[1.${t}${t}E+10]")
        chk("decimal('1.${f}e40').to_text(false)", "text[1${f}0000000000]")
        chk("decimal('1.${f}e40').to_text(true)", "text[1.${t}${t}E+40]")
        chk("decimal('1.${f}e60').to_text(false)", "text[1${f}000000000000000000000000000000]")
        chk("decimal('1.${f}e60').to_text(true)", "text[1.${t}${t}E+60]")
        chk("decimal('1.${f}${f}e60').to_text(false)", "text[1${f}${f}]")
        chk("decimal('1.${f}${f}e60').to_text(true)", "text[1.${t}${t}E+60]")
        chk("decimal('1.${f}${f}e70').to_text(false)", "text[1${f}${f}0000000000]")
        chk("decimal('1.${f}${f}e70').to_text(true)", "text[1.${t}${t}E+70]")
    }

    @Test fun testToTextScientific2() {
        // value = 0
        chkToTextSci("0", "0")
        chkToTextSci("0e+5", "0")
        chkToTextSci("0e-5", "0")

        // scale = 0
        chkToTextSci("1", "1.0")
        chkToTextSci("9", "9.0")
        chkToTextSci("123", "1.23E+2")
        chkToTextSci("123456", "1.23456E+5")
        chkToTextSci("12345678901234567890123", "1.23456789012345678901E+22")
        chkToTextSci("12345678901234567890101", "1.23456789012345678901E+22")
        chkToTextSci("12345678901234567890149", "1.23456789012345678901E+22")
        chkToTextSci("12345678901234567890151", "1.23456789012345678902E+22")
        chkToTextSci("12345678901234567890199", "1.23456789012345678902E+22")
        chkToTextSci("92345678901234567890123", "9.23456789012345678901E+22")

        // scale < 0
        chkToTextSci("10", "1.0E+1")
        chkToTextSci("100", "1.0E+2")
        chkToTextSci("1000", "1.0E+3")
        chkToTextSci("1${"0".repeat(100)}", "1.0E+100")
        chkToTextSci("1${"0".repeat(1000)}", "1.0E+1000")
        chkToTextSci("1${"0".repeat(10000)}", "1.0E+10000")
        chkToTextSci("1${"0".repeat(100000)}", "1.0E+100000")
        chkToTextSci("123456000", "1.23456E+8")
        chkToTextSci("123456000000", "1.23456E+11")
        chkToTextSci("123456${"0".repeat(1000)}", "1.23456E+1005")
        chkToTextSci("123456${"0".repeat(100000)}", "1.23456E+100005")
        chkToTextSci("12345678901234567890123${"0".repeat(1000)}", "1.23456789012345678901E+1022")
        chkToTextSci("12345678901234567890149${"0".repeat(1000)}", "1.23456789012345678901E+1022")
        chkToTextSci("12345678901234567890151${"0".repeat(1000)}", "1.23456789012345678902E+1022")
        chkToTextSci("12345678901234567890199${"0".repeat(1000)}", "1.23456789012345678902E+1022")
        chkToTextSci("92345678901234567890149${"0".repeat(1000)}", "9.23456789012345678901E+1022")

        // scale > 0, precision >= scale
        chkToTextSci("123.456", "1.23456E+2")
        chkToTextSci("0.123456", "1.23456E-1")
        chkToTextSci("1.23456", "1.23456")
        chkToTextSci("1.23456890123456789012", "1.23456890123456789012")
        chkToTextSci("1234567890.1234567890123", "1.23456789012345678901E+9")
        chkToTextSci("1234567890.1234567890149", "1.23456789012345678901E+9")
        chkToTextSci("1234567890.1234567890151", "1.23456789012345678902E+9")
        chkToTextSci("1234567890.1234567890199", "1.23456789012345678902E+9")
        chkToTextSci("12345678901234567890.123", "1.23456789012345678901E+19")
        chkToTextSci("12345678901234567890.149", "1.23456789012345678901E+19")
        chkToTextSci("12345678901234567890.151", "1.23456789012345678902E+19")
        chkToTextSci("123456789012345678901234567890.123", "1.23456789012345678901E+29")

        // scale > 0, precision < scale
        chkToTextSci("0.01", "1.0E-2")
        chkToTextSci("0.0000000001", "1.0E-10")
        chkToTextSci("0.0123456", "1.23456E-2")
        chkToTextSci("0.01234567890123456789", "1.234567890123456789E-2")
    }

    private fun chkToTextSci(v: String, exp: String) {
        chk("decimal('$v').to_text(true)", "text[$exp]")
        if (exp != "0") {
            chk("decimal('-$v').to_text(true)", "text[-$exp]")
        }
    }

    @Test fun testCreateUpdate() {
        tstCtx.useSql = true
        def("entity user { name; mutable value: decimal; }")

        val expMin = "0." + "0".repeat(Lib_DecimalMath.DECIMAL_FRAC_DIGITS - 1) + "1"
        val expMax = "9".repeat(Lib_DecimalMath.DECIMAL_INT_DIGITS) + "." + "9".repeat(Lib_DecimalMath.DECIMAL_FRAC_DIGITS)

        chkOp("{ create user('Bob', decimal.MAX_VALUE); }")
        chk("(user @ { 'Bob' }).value", "dec[$expMax]")

        chkOp("{ create user('Alice', decimal.MIN_VALUE); }")
        chk("(user @ { 'Alice' }).value", "dec[$expMin]")

        chkOp("{ update user @ { 'Bob' } ( value = decimal.MIN_VALUE ); }")
        chk("(user @ { 'Bob' }).value", "dec[$expMin]")

        chkOp("{ update user @ { 'Alice' } ( value = decimal.MAX_VALUE ); }")
        chk("(user @ { 'Alice' }).value", "dec[$expMax]")

        chkOp("{ update user @ { 'Bob' } ( value += decimal.MIN_VALUE ); }")
        chk("(user @ { 'Bob' }).value", "dec[${expMin.replace('1', '2')}]")

        chkOp("{ update user @ { 'Alice' } ( value += decimal.MIN_VALUE ); }", "rt_err:sqlerr:0") // Overflow
    }

    @Test fun testEqualsIp() {
        chkEquals { expr, a, b ->
            chkEx("{ val v = (a = $a, b = $b); return $expr; }", "boolean[true]")
        }
    }

    @Test fun testEqualsDb1() {
        tstCtx.useSql = true
        def("entity data { mutable a: decimal; mutable b: decimal; }")
        chkEquals { expr, a, b ->
            val t = RellCodeTester(tstCtx)
            t.def("entity data { a: decimal; b: decimal; }")
            t.chkOp("create data(a = $a, b = $b);")
            chkEqualsDb0(t, expr)
        }
    }

    @Test fun testEqualsDb2() {
        tstCtx.useSql = true
        def("entity data { mutable a: decimal; mutable b: decimal; }")
        chkEquals { expr, a, b ->
            val t = RellCodeTester(tstCtx)
            t.def("entity data { a: decimal; b: decimal; }")
            val type = Lib_DecimalMath.DECIMAL_SQL_TYPE_STR
            t.insert("c0.data", "a,b", "1,'$a'::$type,'$b'::$type")
            chkEqualsDb0(t, expr)
        }
    }

    @Test fun testEqualsDb3() {
        tstCtx.useSql = true
        def("entity data { mutable a: decimal; mutable b: decimal; }")
        chkEquals { expr, a, b ->
            val t = RellCodeTester(tstCtx)
            t.def("entity data { a: decimal; b: decimal; }")
            t.init()
            t.tstCtx.sqlMgr().transaction { sqlExec ->
                sqlExec.execute(RawSqlBoundStatement("INSERT INTO \"c0.data\"(rowid,a,b) VALUES (?,?,?);") { stmt ->
                    stmt.setInt(1, 1)
                    stmt.setBigDecimal(2, BigDecimal(a))
                    stmt.setBigDecimal(3, BigDecimal(b))
                })
            }
            chkEqualsDb0(t, expr)
        }
    }

    private fun chkEquals(block: (String, String, String) -> Unit) {
        chkEquals0(block, "#0 * 1e-3 == #1 * 1e3", "123456", "0.123456")
        chkEquals0(block, "#0 == #1", "123456e-3", "0.123456e3")
        chkEquals0(block, "#0 == #1", "123e5", "12300e3")
        chkEquals0(block, "#0 * 1e5 == #1 * 1e3", "123", "12300")
        chkEquals0(block, "#0 * 100000 == #1 * 1000", "123", "12300")
        chkEquals0(block, "#0 * 1e5 == #1 * 1e3", "123.4", "12340")
        chkEquals0(block, "#0 == #1", "123.4e5", "12340e3")
        chkEquals0(block, "#0 == #1", "123.450e6", "123450e3")
        chkEquals0(block, "#0 == #1", "123.456e6", "123456e3")
    }

    private fun chkEquals0(block: (String, String, String) -> Unit, expr: String, a: String, b: String) {
        val fullExpr = expr.replace("#0", "v.a").replace("#1", "v.b")
        block(fullExpr, a, b)
    }

    private fun chkEqualsDb0(t: RellCodeTester, expr: String) {
        t.chkEx("{ val v = data @{} (.a, .b); return $expr; }", "boolean[true]")
        t.chk("(v:data) @{} ( $expr )", "boolean[true]")
    }
}
