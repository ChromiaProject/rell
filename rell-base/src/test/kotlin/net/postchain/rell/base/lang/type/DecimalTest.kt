/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.type

import net.postchain.rell.base.lib.type.Lib_DecimalMath
import net.postchain.rell.base.lib.type.Rt_DecimalValue
import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.testutils.RellCodeTester
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.toImmList
import org.junit.Test
import java.math.BigInteger

class DecimalTest: BaseRellTest() {
    @Test fun testType() {
        chkFull("query q(a: decimal) = _type_of(a);", listOf(Rt_DecimalValue.ZERO), "text[decimal]")
        chk("_type_of(decimal(0))", "text[decimal]")
    }

    @Test fun testLiteral() {
        chk("123456", "int[123456]")
        chkLit("123456.0", "dec[123456]")

        chkLit("1E0", "dec[1]")
        chkLit("1E+0", "dec[1]")
        chkLit("1E-0", "dec[1]")
        chkLit("1E1", "dec[10]")
        chkLit("1E10", "dec[10000000000]")
        chkLit("1E+10", "dec[10000000000]")
        chkLit("1E-10", "dec[0.0000000001]")
        chkLit("1e10", "dec[10000000000]")
        chkLit("1e+10", "dec[10000000000]")
        chkLit("1e-10", "dec[0.0000000001]")

        chkLit("123.456", "dec[123.456]")
        chkLit("123.456E3", "dec[123456]")
        chkLit("123.456E+3", "dec[123456]")
        chkLit("123.456E-3", "dec[0.123456]")
        chkLit("123.456E13", "dec[1234560000000000]")
        chkLit("123.456E+13", "dec[1234560000000000]")
        chkLit("123.456E-13", "dec[0.0000000000123456]")

        chkLit("0.123456", "dec[0.123456]")
        chkLit("0.123456E3", "dec[123.456]")
        chkLit("0.123456E+3", "dec[123.456]")
        chkLit("0.123456E-3", "dec[0.000123456]")

        chkLit(".123456", "dec[0.123456]")
        chkLit(".123456E5", "dec[12345.6]")
        chkLit(".123456E-5", "dec[0.00000123456]")
    }

    @Test fun testLiteralMax() {
        chk("9223372036854775807", "int[9223372036854775807]")
        chk("9223372036854775808", "ct_err:lex:int:range:9223372036854775808")
        chk("9223372036854775809", "ct_err:lex:int:range:9223372036854775809")
        chkLit("9223372036854775808.0", "dec[9223372036854775808]")
        chkLit("9223372036854775809.0", "dec[9223372036854775809]")

        chkLit("${"0".repeat(998)}.0", "dec[0]")
        chk("${"0".repeat(999)}.0", "ct_err:lex:decimal:length:1001")
        chk("${"0".repeat(100000)}.0", "ct_err:lex:decimal:length:100002")
        chkLit("${"9".repeat(998)}.0", "dec[${"9".repeat(998)}]")
        chk("${"9".repeat(999)}.0", "ct_err:lex:decimal:length:1001")
        chk("${"9".repeat(100000)}.0", "ct_err:lex:decimal:length:100002")

        chkLit("0.${"0".repeat(998)}", "dec[0]")
        chk("0.${"0".repeat(999)}", "ct_err:lex:decimal:length:1001")
        chk("0.${"0".repeat(100000)}", "ct_err:lex:decimal:length:100002")
        chkLit("0.${"1".repeat(998)}", "dec[0.11111111111111111111]")
        chkLit("0.${"9".repeat(998)}", "dec[1]")
        chk("0.${"9".repeat(999)}", "ct_err:lex:decimal:length:1001")
        chk("0.${"9".repeat(100000)}", "ct_err:lex:decimal:length:100002")

        chkLit("${"8".repeat(499)}.${"9".repeat(500)}", "dec[${"8".repeat(498)}9]")
        chkLit("${"8".repeat(499)}.${"8".repeat(500)}", "dec[${"8".repeat(499)}.88888888888888888889]")
        chkLit("${"9".repeat(499)}.${"9".repeat(500)}", "dec[1${"0".repeat(499)}]")
        chk("${"9".repeat(500)}.${"9".repeat(500)}", "ct_err:lex:decimal:length:1001")
        chk("${"9".repeat(499)}.${"9".repeat(501)}", "ct_err:lex:decimal:length:1001")
        chk("${"9".repeat(100000)}.${"9".repeat(500)}", "ct_err:lex:decimal:length:100501")
        chk("${"9".repeat(499)}.${"9".repeat(100000)}", "ct_err:lex:decimal:length:100500")
        chk("${"9".repeat(100000)}.${"9".repeat(100000)}", "ct_err:lex:decimal:length:200001")
    }

    @Test fun testLiteralPrecision() {
        chkLit("0.01234567890123456789012345", "dec[0.01234567890123456789]")
        chkLit("0.00000000000000000001", "dec[0.00000000000000000001]")
        chkLit("0.000000000000000000001", "dec[0]")
        chkLit("1234567890123456789012345.01234567890123456789012345", "dec[1234567890123456789012345.01234567890123456789]")
        chkLit("1234567890123456789012345.00000000000000000001", "dec[1234567890123456789012345.00000000000000000001]")
        chkLit("1234567890123456789012345.000000000000000000001", "dec[1234567890123456789012345]")
    }

    @Test fun testLiteralExpMin() {
        chkLit("1E-19", "dec[0.0000000000000000001]")
        chkLit("1E-20", "dec[0.00000000000000000001]")
        chkLit("1E-21", "dec[0]")
        chkLit("1E-22", "dec[0]")
        chkLit("1E-100", "dec[0]")
        chkLit("1E-1000", "dec[0]")
        chkLit("1E-1000000000", "dec[0]")
        chkLit("1E-2147483647", "dec[0]")
        chkLitInvalid("1E-2147483648")
        chkLitInvalid("1E-9223372036854775807")
        chkLitInvalid("1E-9223372036854775808")
        chkLitInvalid("1E-340282366920938463463374607431768211456")

        chkLit("123.456E-17", "dec[0.00000000000000123456]")
        chkLit("123.456E-18", "dec[0.00000000000000012346]")
        chkLit("123.456E-19", "dec[0.00000000000000001235]")
        chkLit("123.456E-20", "dec[0.00000000000000000123]")
        chkLit("123.456E-21", "dec[0.00000000000000000012]")
        chkLit("123.456E-22", "dec[0.00000000000000000001]")
        chkLit("123.456E-23", "dec[0]")
        chkLit("123.456E-100000", "dec[0]")

        val rep0 = fracBase("0")
        chkLit("0.${rep0}001", "dec[0]")
        chkLit("0.${rep0}004", "dec[0]")
        chkLit("0.${rep0}005", "dec[0.${rep0}01]")
        chkLit("0.${rep0}009", "dec[0.${rep0}01]")

        chkLit("12345678901234567890E-20", "dec[0.1234567890123456789]")
        chkLit("12345678901234567890E-30", "dec[0.0000000000123456789]")
        chkLit("12345678901234567890E-35", "dec[0.00000000000000012346]")
        chkLit("12345678901234567890E-36", "dec[0.00000000000000001235]")
        chkLit("12345678901234567890E-37", "dec[0.00000000000000000123]")
        chkLit("12345678901234567890E-38", "dec[0.00000000000000000012]")
        chkLit("12345678901234567890E-39", "dec[0.00000000000000000001]")
        chkLit("12345678901234567890E-40", "dec[0]")
        chkLit("12345678901234567890E-100", "dec[0]")
    }

    @Test fun testLiteralExpMax() {
        chkLit("1E+10", "dec[10000000000]")
        chkLit("1E+20", "dec[100000000000000000000]")
        chkLit("1E+100", "dec[1${"0".repeat(100)}]")
        chkLit("1E+1000", "dec[1${"0".repeat(1000)}]")
        chkLit("1E+10000", "dec[1${"0".repeat(10000)}]")
        chkLit("1E+100000", "dec[1${"0".repeat(100000)}]")
        chkLit("1E+131071", "dec[1${"0".repeat(131071)}]")
        chkLitRange("1E+131072")
        chkLitRange("1E+1000000000")
        chkLitInvalid("1E+9223372036854775807")
        chkLitInvalid("1E+9223372036854775808")
        chkLitInvalid("1E+340282366920938463463374607431768211456")

        chkLit("123456789E+10", "dec[1234567890000000000]")
        chkLit("123456789E+20", "dec[12345678900000000000000000000]")
        chkLit("123456789E+100", "dec[123456789${"0".repeat(100)}]")
        chkLit("123456789E+1000", "dec[123456789${"0".repeat(1000)}]")
        chkLit("123456789E+10000", "dec[123456789${"0".repeat(10000)}]")
        chkLit("123456789E+100000", "dec[123456789${"0".repeat(100000)}]")
        chkLit("123456789E+131063", "dec[123456789${"0".repeat(131063)}]")
        chkLitRange("123456789E+131064")

        chkLit("123456789.987654321E+10", "dec[1234567899876543210]")
        chkLit("123456789.987654321E+20", "dec[12345678998765432100000000000]")
        chkLit("123456789.987654321E+100", "dec[1234567899876543210${"0".repeat(90)}]")
        chkLit("123456789.987654321E+1000", "dec[1234567899876543210${"0".repeat(990)}]")
        chkLit("123456789.987654321E+10000", "dec[1234567899876543210${"0".repeat(9990)}]")
        chkLit("123456789.987654321E+100000", "dec[1234567899876543210${"0".repeat(99990)}]")
        chkLit("123456789.987654321E+131063", "dec[1234567899876543210${"0".repeat(131053)}]")
        chkLitRange("123456789.987654321E+131064")
    }

    @Suppress("RemoveCurlyBracesFromTemplate", "RemoveSingleExpressionStringTemplate")
    @Test fun testLiteralExpLongBase() {
        val x = "0.${"0".repeat(500)}123456"
        chkLit("${x}", "dec[0]")
        chkLit("${x}E+480", "dec[0]")
        chkLit("${x}E+481", "dec[0.00000000000000000001]")
        chkLit("${x}E+482", "dec[0.00000000000000000012]")
        chkLit("${x}E+483", "dec[0.00000000000000000123]")
        chkLit("${x}E+484", "dec[0.00000000000000001235]")
        chkLit("${x}E+485", "dec[0.00000000000000012346]")
        chkLit("${x}E+486", "dec[0.00000000000000123456]")
        chkLit("${x}E+500", "dec[0.123456]")
        chkLit("${x}E+506", "dec[123456]")
        chkLit("${x}E+131572", "dec[123456${"0".repeat(131066)}]")
        chkLitRange("${x}E+131573")

        val y = "123456${"0".repeat(500)}"
        chkLit("${y}.0", "dec[${y}]")
        chkLit("${y}E-500", "dec[123456]")
        chkLit("${y}E-506", "dec[0.123456]")
        chkLit("${y}E-520", "dec[0.00000000000000123456]")
        chkLit("${y}E-521", "dec[0.00000000000000012346]")
        chkLit("${y}E-522", "dec[0.00000000000000001235]")
        chkLit("${y}E-523", "dec[0.00000000000000000123]")
        chkLit("${y}E-524", "dec[0.00000000000000000012]")
        chkLit("${y}E-525", "dec[0.00000000000000000001]")
        chkLit("${y}E-526", "dec[0]")
        chkLit("${y}E-100000", "dec[0]")
        chkLit("${y}E+130566", "dec[${y}${"0".repeat(130566)}]")
        chkLitRange("${y}E+130567")
    }

    private fun chkLit(lit: String, exp: String) {
        chk(lit, exp)
        chk("decimal('$lit')", exp)
    }

    private fun chkLitInvalid(lit: String) {
        chk(lit, "ct_err:lex:decimal:invalid:$lit")
        chk("decimal('$lit')", "rt_err:decimal:invalid:$lit")
    }

    private fun chkLitRange(lit: String) {
        chk(lit, "ct_err:lex:decimal:range:$lit")
        chk("decimal('$lit')", "rt_err:decimal:overflow")
    }

    @Test fun testTrailingZerosEquals() {
        chk("decimal('0')", "dec[0]")
        chk("decimal('0.0')", "dec[0]")
        chk("decimal('0.00000')", "dec[0]")
        chk("decimal('0.0000000000')", "dec[0]")
        chk("decimal('0.0000000000000000000000000000000000000000')", "dec[0]")
        chk("decimal('0') == decimal('0.0')", "boolean[true]")
        chk("decimal('0') != decimal('0.0')", "boolean[false]")
        chk("decimal('0') == decimal('0.00000')", "boolean[true]")
        chk("decimal('0') == decimal('0.000000000')", "boolean[true]")
        chk("decimal('0.0') == decimal('0.00000')", "boolean[true]")
        chk("decimal('0.0') == decimal('0.0000000000000000000000000000000000000000')", "boolean[true]")

        chk("decimal('.0')", "dec[0]")
        chk("decimal('.00000')", "dec[0]")
        chk("decimal('.0000000000')", "dec[0]")
        chk("decimal('.0000000000000000000000000000000000000000')", "dec[0]")
        chk("decimal('0') == decimal('.0')", "boolean[true]")
        chk("decimal('0') != decimal('.0')", "boolean[false]")
        chk("decimal('0') == decimal('.00000')", "boolean[true]")
        chk("decimal('0') == decimal('.000000000')", "boolean[true]")
        chk("decimal('.0') == decimal('.00000')", "boolean[true]")
        chk("decimal('.0') == decimal('.0000000000000000000000000000000000000000')", "boolean[true]")

        chk("decimal('123')", "dec[123]")
        chk("decimal('123.0')", "dec[123]")
        chk("decimal('123.00000')", "dec[123]")
        chk("decimal('123.0000000000')", "dec[123]")
        chk("decimal('123.0000000000000000000000000000000000000000')", "dec[123]")
        chk("decimal('123') == decimal('123.0')", "boolean[true]")
        chk("decimal('123') != decimal('123.0')", "boolean[false]")
        chk("decimal('123') == decimal('123.00000')", "boolean[true]")
        chk("decimal('123') == decimal('123.000000000')", "boolean[true]")
        chk("decimal('123') == decimal('123.0000000000000000000000000000000000000000')", "boolean[true]")
        chk("decimal('123.0') == decimal('123.00000')", "boolean[true]")
        chk("decimal('123.0') == decimal('123.0000000000000000000000000000000000000000')", "boolean[true]")
    }

    @Test fun testTrailingZerosHashCode() {
        tst.strictToString = false

        var init = "var m = [ decimal(0) : 'A' ]"
        chkEx("{ $init; return decimal(0) in m; }", "true")
        chkEx("{ $init; return decimal('0') in m; }", "true")
        chkEx("{ $init; return decimal('0.0') in m; }", "true")
        chkEx("{ $init; return decimal('0.00000') in m; }", "true")
        chkEx("{ $init; return decimal('0.0000000000000000000000000000000000000000') in m; }", "true")
        chkEx("{ $init; return decimal('.0') in m; }", "true")
        chkEx("{ $init; return decimal('.00000') in m; }", "true")
        chkEx("{ $init; return decimal('.0000000000000000000000000000000000000000') in m; }", "true")
        chkEx("{ $init; return decimal('0.00000000000000000001') in m; }", "false")
        chkEx("{ $init; return decimal('-0.00000000000000000001') in m; }", "false")

        init = "var m = [ decimal('0.0') : 'A' ]"
        chkEx("{ $init; return decimal(0) in m; }", "true")
        chkEx("{ $init; return decimal('0') in m; }", "true")
        chkEx("{ $init; return decimal('0.0') in m; }", "true")
        chkEx("{ $init; return decimal('0.00000') in m; }", "true")
        chkEx("{ $init; return decimal('0.0000000000000000000000000000000000000000') in m; }", "true")
        chkEx("{ $init; return decimal('.0') in m; }", "true")
        chkEx("{ $init; return decimal('.00000') in m; }", "true")
        chkEx("{ $init; return decimal('.0000000000000000000000000000000000000000') in m; }", "true")
        chkEx("{ $init; return decimal('.00000000000000000001') in m; }", "false")
        chkEx("{ $init; return decimal('-.00000000000000000001') in m; }", "false")

        init = "var m = [ decimal(123) : 'A' ]"
        chkEx("{ $init; return decimal(123) in m; }", "true")
        chkEx("{ $init; return decimal('123') in m; }", "true")
        chkEx("{ $init; return decimal('123.0') in m; }", "true")
        chkEx("{ $init; return decimal('123.00000') in m; }", "true")
        chkEx("{ $init; return decimal('123.0000000000000000000000000000000000000000') in m; }", "true")
        chkEx("{ $init; return decimal('123.00000000000000000001') in m; }", "false")
    }

    @Test fun testTrailingZerosCryptoHash() {
        tst.strictToString = false

        var exp = "0x9303f4d128f03b2d281e1aa6896d74e3622c3257696a81c3db4bb044795ffd40"
        chk("decimal(0).hash()", exp)
        chk("decimal('0').hash()", exp)
        chkCryptoHash("0.0", exp)
        chkCryptoHash("0.00000", exp)
        chkCryptoHash("0.0000000000000000000000000000000000000000", exp)
        chkCryptoHash("0.00000000000000000001", "0xb2462e53a31ddc343da7b4f4ddfaa93ab943c08116f5f00ab3b4dadc20805827")
        chkCryptoHash(".0", exp)
        chkCryptoHash(".00000", exp)
        chkCryptoHash(".0000000000000000000000000000000000000000", exp)
        chkCryptoHash(".00000000000000000001", "0xb2462e53a31ddc343da7b4f4ddfaa93ab943c08116f5f00ab3b4dadc20805827")

        exp = "0x25644a82e10ca56e732b2aace38326a9b7e74ca99d88951c85f8c33b849e1a6c"
        chk("decimal(123).hash()", exp)
        chk("decimal('123').hash()", exp)
        chkCryptoHash("123.0", exp)
        chkCryptoHash("123.00000", exp)
        chkCryptoHash("123.0000000000000000000000000000000000000000", exp)
        chkCryptoHash("123.00000000000000000001", "0xce9c8046ff20a06b8ee1b08cfd0eb0d12d5655352f01eca10e7465c745da6b7a")

        chk("(decimal('9876543210') + decimal('123') - decimal('9876543210')).hash()", exp)
        chk("(decimal('9876.000000000054321') + decimal('123') - decimal('9876.000000000054321'))", "123")
        chk("(decimal('9876.000000000054321') + decimal('123') - decimal('9876.000000000054321')).to_gtv()", "\"123\"")
        chk("(decimal('9876.000000000054321') + decimal('123') - decimal('9876.000000000054321')).hash()", exp)
        chk("(decimal('0.0000000000000001') + decimal('123') - decimal('0.0000000000000001')).hash()", exp)
    }

    private fun chkCryptoHash(v: String, exp: String) {
        chk("decimal('$v').hash()", exp)
        chk("($v).hash()", exp)
    }

    @Test fun testEquals() {
        tst.strictToString = false
        chkEqualsSub("123", "456")
        chkEqualsSub("123", "456789")
        chkEqualsSub("0.123", "0.456789")
        chkEqualsSub("123456789", "0.123456789")
    }

    private fun chkEqualsSub(v1: String, v2: String) {
        chk("(decimal('$v1') - decimal('$v1')) == (decimal('$v2') - decimal('$v2'))", "true")
        chk("(decimal('$v1') - decimal('$v1')) != (decimal('$v2') - decimal('$v2'))", "false")
    }

    @Test fun testPromotionVarDeclaration() {
        chkEx("{ var x: decimal = 123; return _type_of(x); }", "text[decimal]")
        chkEx("{ var x: decimal = 123; return x; }", "dec[123]")
        chkEx("{ var x: decimal? = 123; return x; }", "dec[123]")
        chkEx("{ var x: decimal? = _nullable_int(123); return x; }", "dec[123]")
        chkEx("{ var x: decimal? = _nullable_int(null); return x; }", "null")

        chkEx("{ var (x: decimal, y: text) = (decimal(123), 'Hello'); return x; }", "dec[123]")
        chkEx("{ var (x: decimal, y: text) = (123, 'Hello'); return x; }", "dec[123]")
        chkEx("{ var (x: decimal?, y: text) = (123, 'Hello'); return x; }", "dec[123]")
        chkEx("{ var (x: decimal?, y: text) = (_nullable_int(123), 'Hello'); return x; }", "dec[123]")
        chkEx("{ var (x: decimal?, y: text) = (_nullable_int(null), 'Hello'); return x; }", "null")
    }

    @Test fun testPromotionAssignment() {
        chkEx("{ var x = decimal(123); x = 456; return x; }", "dec[456]")
        chkEx("{ var x = decimal(123); x += 456; return x; }", "dec[579]")
        chkEx("{ var x = decimal(123); x *= 456; return x; }", "dec[56088]")

        chkEx("{ var x = 123; x = decimal(456); return x; }", "ct_err:stmt_assign_type:[integer]:[decimal]")
        chkEx("{ var x = 123; x += decimal(456); return x; }", "ct_err:binop_operand_type:+=:[integer]:[decimal]")
        chkEx("{ var x = 123; x *= decimal(456); return x; }", "ct_err:binop_operand_type:*=:[integer]:[decimal]")

        chkEx("{ var x = 123L; x = decimal(456); return x; }", "ct_err:stmt_assign_type:[big_integer]:[decimal]")
        chkEx("{ var x = 123L; x += decimal(456); return x; }", "ct_err:binop_operand_type:+=:[big_integer]:[decimal]")
    }

    @Test fun testPromotionUserFunction() {
        def("function f(x: decimal, y: integer): decimal = x + decimal(y);")
        def("function g(x: decimal, y: decimal): decimal = x * y;")

        chk("f(123, 456)", "dec[579]")
        chk("f(decimal(123), 456)", "dec[579]")
        chk("f(123, decimal(456))", "ct_err:expr_call_argtype:[f]:1:y:integer:decimal")
        chk("f(decimal(123), decimal(456))", "ct_err:expr_call_argtype:[f]:1:y:integer:decimal")

        chk("g(123, 456)", "dec[56088]")
        chk("g(decimal(123), 456)", "dec[56088]")
        chk("g(123, decimal(456))", "dec[56088]")
        chk("g(decimal(123), decimal(456))", "dec[56088]")
    }

    @Test fun testPromotionStruct() {
        def("struct rec { mutable x: decimal = 123; mutable y: decimal? = _nullable_int(789); }")

        chkEx("{ val r = rec(); return r.x; }", "dec[123]")
        chkEx("{ val r = rec(x = decimal(456)); return r.x; }", "dec[456]")
        //chkEx("{ val r = rec(x = 456); return r.x; }", "dec[456]")
        chkEx("{ val r = rec(x = null); return 0; }", "ct_err:attr_bad_type:0:x:decimal:null")
        chkEx("{ val r = rec(x = _nullable_int(456)); return 0; }", "ct_err:attr_bad_type:0:x:decimal:integer?")
        chkEx("{ val r = rec(); r.x = decimal(456); return r.x; }", "dec[456]")
        chkEx("{ val r = rec(); r.x = 456; return r.x; }", "dec[456]")
        chkEx("{ val r = rec(); r.x = null; return 0; }", "ct_err:stmt_assign_type:[decimal]:[null]")
        chkEx("{ val r = rec(); r.x = _nullable_int(456); return 0; }", "ct_err:stmt_assign_type:[decimal]:[integer?]")
        chkEx("{ val r = rec(); r.x += 456; return r.x; }", "dec[579]")
        chkEx("{ val r = rec(); r.x *= 456; return r.x; }", "dec[56088]")

        chkEx("{ val r = rec(); return r.y; }", "dec[789]")
        chkEx("{ val r = rec(y = decimal(456)); return r.y; }", "dec[456]")
        //chkEx("{ val r = rec(y = 456); return r.y; }", "dec[456]")
        chkEx("{ val r = rec(y = null); return r.y; }", "null")
        //chkEx("{ val r = rec(y = _nullable_int(456)); return r.y; }", "dec[456]")
        chkEx("{ val r = rec(); r.y = decimal(456); return r.y; }", "dec[456]")
        chkEx("{ val r = rec(); r.y = 456; return r.y; }", "dec[456]")
        chkEx("{ val r = rec(); r.y = null; return r.y; }", "null")
        chkEx("{ val r = rec(); r.y = _nullable_int(456); return r.y; }", "dec[456]")
        chkEx("{ val r = rec(); r.y += 456; return r.y; }", "ct_err:binop_operand_type:+=:[decimal?]:[integer]")
        chkEx("{ val r = rec(); r.y *= 456; return r.y; }", "ct_err:binop_operand_type:*=:[decimal?]:[integer]")
    }

    @Test fun testPromotionCreateUpdate() {
        tstCtx.useSql = true
        def("entity user { name; mutable x: decimal = 123; }")
        insert("c0.user", "name,x", "333,'Bob',789")

        chk("(user @ { 'Bob' }).x", "dec[789]")

        chkOp("{ val u = create user(name = 'Alice'); }")
        chk("(user @ { 'Alice' }).x", "dec[123]")

//        chkOp("{ val u = create user(name = 'Alice', x = 456); }")
//        chk("(user @ { 'Alice' }).x", "dec[456]")

        chkOp("{ update user @ { 'Bob' } ( x = 456 ); }")
        chk("(user @ { 'Bob' }).x", "dec[456]")

        chkOp("{ update user @ { 'Bob' } ( x += 123 ); }")
        chk("(user @ { 'Bob' }).x", "dec[579]")
    }

    @Test fun testIncrement() {
        chkEx("{ var x: decimal = 123; x++; return x; }", "dec[124]")
        chkEx("{ var x: decimal = 123; x--; return x; }", "dec[122]")
        chkEx("{ var x: decimal = 123; ++x; return x; }", "dec[124]")
        chkEx("{ var x: decimal = 123; --x; return x; }", "dec[122]")

        chkEx("{ var x: decimal = 123; val y = x++; return (x, y); }", "(dec[124],dec[123])")
        chkEx("{ var x: decimal = 123; val y = x--; return (x, y); }", "(dec[122],dec[123])")
        chkEx("{ var x: decimal = 123; val y = ++x; return (x, y); }", "(dec[124],dec[124])")
        chkEx("{ var x: decimal = 123; val y = --x; return (x, y); }", "(dec[122],dec[122])")

        chkEx("{ var x: decimal = 123.456; x++; return x; }", "dec[124.456]")
        chkEx("{ var x: decimal = 123.456; x--; return x; }", "dec[122.456]")
        chkEx("{ var x: decimal = 123.456; ++x; return x; }", "dec[124.456]")
        chkEx("{ var x: decimal = 123.456; --x; return x; }", "dec[122.456]")
    }

    @Test fun testAggregationSum() {
        tstCtx.useSql = true
        def("entity data { v: decimal; }")

        for (case in ADD_TEST_CASES + SUB_TEST_CASES) {
            tst.inserts = listOf()
            val b = if (case.op == "+") case.b else if (case.b.startsWith("-")) case.b.substring(1) else "-${case.b}"
            insertDecimal(1, case.a)
            insertDecimal(2, b)

            val expr = "data @{} ( @sum .v )"
            if (case.expected != null) {
                tst.chk(expr, "dec[${case.expected}]")
            } else {
                tst.chk(expr, "rt_err:sqlerr:0")
            }
        }
    }

    private fun insertDecimal(id: Int, v: String) {
        tst.insert("c0.data", "v", "$id,'$v' :: ${Lib_DecimalMath.DECIMAL_SQL_TYPE_STR}")
    }

    @Test fun testBugEquals() {
        tstCtx.useSql = true
        chkBugEquals("42", "42.0", "42")
        chkBugEquals("42", "42", "42")
        chkBugEquals("42.0", "42.0", "42")
        chkBugEquals("42.0", "42", "42")
        chkBugEquals("42.12340000", "42.1234", "42.1234")
        chkBugEquals("1000", "1000.0", "1000")
        chkBugEquals("1000.000", "1000.0", "1000")
    }

    private fun chkBugEquals(v: String, e: String, e2: String) {
        val t = RellCodeTester(tstCtx)
        t.def("entity data { v: decimal; }")
        t.insert("c0.data", "v", "100,$v")

        t.chk("(data @ {}.v) == $e", "boolean[true]")
        t.chk("(data @ {} (.v)) == $e", "boolean[true]")
        t.chk("(data @ {} (.v == $e))", "boolean[true]")
        t.chk("data @ {}.v", "dec[$e2]")
    }

    class DecAddCase(val op: String, val a: String, val b: String, val expected: String?)

    class DecVals {
        val intDigs = Lib_DecimalMath.DECIMAL_INT_DIGITS
        val lim1 = limitMinus(1)
        val lim2 = limitMinus(2)
        val limDiv10 = limitDiv(10)
        val frac0 = fracBase("0")
        val frac3 = fracBase("3")
        val frac6 = fracBase("6")
        val frac9 = fracBase("9")
    }

    companion object {
        val LIMIT: BigInteger = BigInteger.TEN.pow(Lib_DecimalMath.DECIMAL_INT_DIGITS)

        fun limitMinus(minus: Long) = "" + (LIMIT - BigInteger.valueOf(minus))
        fun limitDiv(div: Long) = "" + (LIMIT / BigInteger.valueOf(div))
        fun fracBase(dig: String) = dig.repeat(Lib_DecimalMath.DECIMAL_FRAC_DIGITS - 2)

        val ADD_TEST_CASES = makeAddCases()
        val SUB_TEST_CASES = makeSubCases()

        private fun makeAddCases(): ImmList<DecAddCase> {
            val list = BigIntegerTest.makeAddCases().toMutableList()
            val d = DecVals()
            addCase(list, "+", "12.34", "56.78", "69.12")
            addCase(list, "+", "0.${d.frac3}12", "0.${d.frac0}34", "0.${d.frac3}46")
            addCase(list, "+", "${d.lim1}.${d.frac3}12", "0.${d.frac6}34", "${d.lim1}.${d.frac9}46")
            addCase(list, "+", "${d.lim1}.${d.frac3}95", "0.${d.frac6}04", "${d.lim1}.${d.frac9}99")
            addCase(list, "+", "-${d.lim1}.${d.frac3}95", "-0.${d.frac6}04", "-${d.lim1}.${d.frac9}99")
            addCase(list, "+", "-${d.lim1}.${d.frac3}95", "-0.${d.frac6}05", null)
            return list.toImmList()
        }

        private fun makeSubCases(): ImmList<DecAddCase> {
            val list = BigIntegerTest.makeSubCases().toMutableList()
            val d = DecVals()
            addCase(list, "-", "12.34", "56.78", "-44.44")
            addCase(list, "-", "0.${d.frac3}34", "0.${d.frac0}12", "0.${d.frac3}22")
            addCase(list, "-", "${d.lim1}.${d.frac9}34", "0.${d.frac6}12", "${d.lim1}.${d.frac3}22")
            addCase(list, "-", "-${d.lim1}.${d.frac3}95", "0.${d.frac6}04", "-${d.lim1}.${d.frac9}99")
            addCase(list, "-", "-${d.lim1}.${d.frac3}95", "0.${d.frac6}04", "-${d.lim1}.${d.frac9}99")
            addCase(list, "-", "-${d.lim1}.${d.frac3}95", "0.${d.frac6}05", null)
            return list.toImmList()
        }

        private fun addCase(list: MutableList<DecAddCase>, op: String, a: String, b: String, expected: String?) {
            list.add(DecAddCase(op, a, b, expected))
        }
    }
}
