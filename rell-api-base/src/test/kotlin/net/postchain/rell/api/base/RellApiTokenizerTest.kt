/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.api.base

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class RellApiTokenizerTest {

    // valid identifiers are [a-zA-Z_][a-zA-Z0-9_] (see R_Name.isNameStart(), R_Name.isNamePart()).
    @Test fun testIsIdentifierGoodIdentsTrue() {
        chkIdent("abc")
        chkIdent("_abc")
        chkIdent("Abc")
        chkIdent("a8c")
    }

    @Test fun testIsIdentifierIllegalSpacesFalse() {
        chkNotIdent(" abc")
        chkNotIdent("abc ")
        chkNotIdent("a bc")

        chkNotIdent("\nabc")
        chkNotIdent("abc\n")
        chkNotIdent("a\nbc")

        chkNotIdent("\rabc")
        chkNotIdent("abc\r")
        chkNotIdent("a\rbc")

        chkNotIdent("\tabc")
        chkNotIdent("abc\t")
        chkNotIdent("a\tbc")
    }

    @Test fun testIsIdentifierBadIdentsFalse() {
        chkNotIdent("4bc")
        chkNotIdent("\$myvar")
        chkNotIdent("$")
        chkNotIdent("bob$")
        chkNotIdent("Sehenswürdigkeit")
        chkNotIdent("тест")
    }

    @Test fun testIsIdentifierTypesTrue() {
        chkIdent("big_integer")
        chkIdent("boolean")
        chkIdent("gtv")
        chkIdent("json")
        chkIdent("list")
    }

    @Test fun testIsIdentifierKeywordsFalse() {
        chkNotIdent("create")
        chkNotIdent("for")
        chkNotIdent("null")
        chkNotIdent("query")
        chkNotIdent("val")
    }

    @Test fun testIsIdentifierDeprecatedKeywordsTrue() {
        chkIdent("sort")
    }

    private fun chkIdent(s: String) = assertTrue(RellApiTokenizer.isIdentifier(s))
    private fun chkNotIdent(s: String) = assertFalse(RellApiTokenizer.isIdentifier(s))

    @Test fun testIsIntegerGoodIntegersTrue() {
        chkUInt("0")
        chkUInt("123456789")
        chkUInt("9223372036854775807")
        chkUInt("01234567")
        chkUInt("0x0")
        chkUInt("0x1234abcd")
        chkUInt("0x7FFFFFFFFFFFFFFF")

        chkSInt("0")
        chkSInt("123456789")
        chkSInt("9223372036854775807")
        chkSInt("01234567")
        chkSInt("0x0")
        chkSInt("0x1234abcd")
        chkSInt("0x7FFFFFFFFFFFFFFF")
    }

    @Test fun testIsIntegerSignedGoodIntegersAllowingSignTrue() {
        chkSInt("+123456789")
        chkSInt("+01234567")
        chkSInt("+0x0")
        chkSInt("+0x1234abcd")

        chkSInt("-123456789")
        chkSInt("-01234567")
        chkSInt("-0x0")
        chkSInt("-0x1234abcd")
    }

    @Test fun testIsIntegerMultipleSignsFalse() {
        chkNotSInt("+-123456789")
        chkNotSInt("-+01234567")
        chkNotSInt("++0x0")
        chkNotSInt("--0x1234abcd")
    }

    @Test fun testIsIntegerSpaceAfterSignFalse() {
        chkNotSInt("+ 123456789")
        chkNotSInt("- 0x1234abcd")

        chkNotSInt("+\n123456789")
        chkNotSInt("-\n0x1234abcd")

        chkNotSInt("+\t123456789")
        chkNotSInt("-\t0x1234abcd")

        chkNotSInt("+\r123456789")
        chkNotSInt("-\r0x1234abcd")
    }

    @Test fun testIsIntegerSignedGoodIntegersDisallowingSignFalse() {
        chkNotUInt("+123456789")
        chkNotUInt("+01234567")
        chkNotUInt("+0x0")
        chkNotUInt("+0x1234abcd")

        chkNotUInt("-123456789")
        chkNotUInt("-01234567")
        chkNotUInt("-0x0")
        chkNotUInt("-0x1234abcd")
    }

    @Test fun testIsIntegerBadIntegersFalse() {
        chkNotSInt("0x")
        chkNotSInt("0X0")
        chkNotSInt("0xg")
        chkNotSInt("0x0g")
        chkNotSInt("0xz")
        chkNotSInt("1a")
        chkNotSInt("1f")
        chkNotSInt("1z")
    }

    @Test fun testIsIntegerTooLargeIntegersFalse() {
        chkNotSInt("0x8000000000000000")
        chkNotSInt("9223372036854775808")
    }

    private fun chkUInt(s: String) = assertTrue(RellApiTokenizer.isInteger(s, allowSign = false))
    private fun chkNotUInt(s: String) = assertFalse(RellApiTokenizer.isInteger(s, allowSign = false))

    private fun chkSInt(s: String) = assertTrue(RellApiTokenizer.isInteger(s, allowSign = true))
    private fun chkNotSInt(s: String) = assertFalse(RellApiTokenizer.isInteger(s, allowSign = false))

    @Test fun testIsDecimalGoodDecimalsTrue() {
        chkSDec("123.456")
        chkSDec("0.123")
        chkSDec(".456")
        chkSDec("33E+10")
        chkSDec("55.77e-5")
        chkSDec("0.0")
        chkSDec("01234567.8")
        chkSDec("9223372036854775808.0")
        chkSDec("12.34E+0")
        chkSDec(".0")
        chkSDec(".1E3")
    }

    @Test fun testIsDecimalGoodDecimalsDisallowingSignTrue() {
        chkUDec("123.456")
        chkUDec("0.123")
        chkUDec(".456")
        chkUDec("33E+10")
        chkUDec("55.77e-5")
        chkUDec("0.0")
        chkUDec("01234567.8")
        chkUDec("9223372036854775808.0")
        chkUDec("12.34E+0")
        chkUDec(".0")
        chkUDec(".1E3")
    }

    @Test fun testIsDecimalSignedGoodDecimalsAllowingSignTrue() {
        chkSDec("+123.456")
        chkSDec("+0.123")
        chkSDec("+.456")
        chkSDec("+33E+10")
        chkSDec("+55.77e-5")

        chkSDec("-0.0")
        chkSDec("-01234567.8")
        chkSDec("-9223372036854775808.0")
        chkSDec("-12.34E+0")
        chkSDec("-.0")
        chkSDec("-.1E3")
    }

    @Test fun testIsDecimalSignedGoodDecimalsDisallowingSignFalse() {
        chkNotUDec("+123.456")
        chkNotUDec("+0.123")
        chkNotUDec("+.456")
        chkNotUDec("+33E+10")
        chkNotUDec("+55.77e-5")

        chkNotUDec("-0.0")
        chkNotUDec("-01234567.8")
        chkNotUDec("-12.34E+0")
        chkNotUDec("-.0")
        chkNotUDec("-.1E3")
    }

    @Test fun testIsDecimalBadDecimalsFalse() {
        chkNotSDec("123.")
        chkNotSDec("0.")
        chkNotSDec("1e")
        chkNotSDec("1E+ 5")
        chkNotSDec("1E+10F")
        chkNotSDec("0x123.0")
        chkNotSDec("0x123.456")
        chkNotSDec("0x.123")
    }

    @Test fun testIsDecimalMultipleSignsFalse() {
        chkNotSDec("+-123.456")
        chkNotSDec("-+.456")
        chkNotSDec("++33E+10")
        chkNotSDec("--.1E3")
    }

    @Test fun testIsDecimalSpaceAfterSignFalse() {
        chkNotSDec("+ 123.456")
        chkNotSDec("- .1E3")

        chkNotSDec("+\n123.456")
        chkNotSDec("-\n.1E3")

        chkNotSDec("+\t123.456")
        chkNotSDec("-\t.1E3")

        chkNotSDec("+\r123.456")
        chkNotSDec("-\r.1E3")
    }

    private fun chkUDec(s: String) = assertTrue(RellApiTokenizer.isDecimal(s, allowSign = false))
    private fun chkNotUDec(s: String) = assertFalse(RellApiTokenizer.isDecimal(s, allowSign = false))

    private fun chkSDec(s: String) = assertTrue(RellApiTokenizer.isDecimal(s, allowSign = true))
    private fun chkNotSDec(s: String) = assertFalse(RellApiTokenizer.isDecimal(s, allowSign = false))

    @Test fun testIsBigIntegerGoodBigIntegersTrue() {
        chkUBigInt("0L")
        chkUBigInt("9223372036854775807L")
        chkUBigInt("01234567L")
        chkUBigInt("0x0L")

        chkSBigInt("0xFL")
        chkSBigInt("0x1234abcdL")
        chkSBigInt("0x8000000000000000L")
        chkSBigInt("9223372036854775808L")
    }

    @Test fun testIsBigIntegerSignedGoodBigIntegersAllowingSignTrue() {
        chkSBigInt("+0L")
        chkSBigInt("+9223372036854775807L")
        chkSBigInt("+01234567L")
        chkSBigInt("+0x0L")

        chkSBigInt("-0xFL")
        chkSBigInt("-0x1234abcdL")
        chkSBigInt("-0x8000000000000000L")
        chkSBigInt("-9223372036854775808L")
    }

    @Test fun testIsBigIntegerSignedGoodBigIntegersDisallowingSignFalse() {
        chkNotUBigInt("+0L")
        chkNotUBigInt("+9223372036854775807L")
        chkNotUBigInt("+01234567L")
        chkNotUBigInt("+0x0L")

        chkNotUBigInt("-0xFL")
        chkNotUBigInt("-0x1234abcdL")
        chkNotUBigInt("-0x8000000000000000L")
        chkNotUBigInt("-9223372036854775808L")
    }

    @Test fun testIsBigIntegerMultipleSignsFalse() {
        chkNotSBigInt("+-9223372036854775807L")
        chkNotSBigInt("-+0x0L")
        chkNotSBigInt("++0x8000000000000000L")
        chkNotSBigInt("--9223372036854775808L")
    }

    @Test fun testIsBigIntegerSpaceAfterSignFalse() {
        chkNotSBigInt("+ 9223372036854775807L")
        chkNotSBigInt("- 0x0L")

        chkNotSBigInt("+\n0x8000000000000000L")
        chkNotSBigInt("-\n9223372036854775808L")

        chkNotSBigInt("+\r9223372036854775807L")
        chkNotSBigInt("-\r0x0L")

        chkNotSBigInt("+\t0x8000000000000000L")
        chkNotSBigInt("-\t9223372036854775808L")
    }

    @Test fun testIsBigIntegerBadBigIntegersFalse() {
        chkNotSBigInt("0xL")
        chkNotSBigInt("0X0L")
        chkNotSBigInt("0xgL")
        chkNotSBigInt("0x0gL")
        chkNotSBigInt("0xzL")
        chkNotSBigInt("1aL")
        chkNotSBigInt("1fL")
        chkNotSBigInt("1zL")
    }

    private fun chkUBigInt(s: String) = assertTrue(RellApiTokenizer.isBigInteger(s, allowSign = false))
    private fun chkNotUBigInt(s: String) = assertFalse(RellApiTokenizer.isBigInteger(s, allowSign = false))

    private fun chkSBigInt(s: String) = assertTrue(RellApiTokenizer.isBigInteger(s, allowSign = true))
    private fun chkNotSBigInt(s: String) = assertFalse(RellApiTokenizer.isBigInteger(s, allowSign = false))

    @Test fun testIsTextGoodTextsTrue() {
        chkText("""''""")
        chkText("""""""")
        chkText(""""Hello"""")
        chkText(""""'"""")
        chkText("""'"'""")
        chkText("""" """")
        chkText("""'\b'""")
        chkText(""""\t"""")
        chkText("""'\r'""")
        chkText(""""\n"""")
        chkText("""'\"'""")
        chkText("""'\''""")
        chkText(""""\\"""")
        chkText("""'\u0031\u0032\u0033'""")
        chkText(""""\u003A\u003B\u003C"""")
        chkText("""'\u003a\u003b\u003c'""")
    }

    @Test fun testIsTextBadTextsFalse() {
        val nl = "\n"
        chkNotText(""""\q"""")
        chkNotText("""'\u003g'""")
        chkNotText(""""\u003"""")
        chkNotText("""'\u003!'""")
        chkNotText(""""Hello${nl}World"""")
        chkNotText("""'Hello\${nl}World'""")
        chkNotText("""'Hello""")
    }

    @Test fun testIsTextGoodTextsWithSpaceDisallowedFalse() {
        chkNotText(" 'a'")
        chkNotText(" \"a\"")
        chkNotText("'a' ")
        chkNotText("\"a\" ")
    }

    private fun chkText(s: String) = assertTrue(RellApiTokenizer.isText(s))
    private fun chkNotText(s: String) = assertFalse(RellApiTokenizer.isText(s))

    @Test fun testIsByteArrayGoodByteArraysTrue() {
        chkByteArr("""x""""")
        chkByteArr("""x''""")
        chkByteArr("""x"1234ABCD"""")
        chkByteArr("""x'1234ABCD'""")
        chkByteArr("""x"1234abcd"""")
        chkByteArr("""x'1234abcd'""")
    }

    @Test fun testIsByteArrayBadByteArraysFalse() {
        chkNotByteArr("""x'1'""")
        chkNotByteArr("""x"123"""")
        chkNotByteArr("""x'ge'""")
        chkNotByteArr("""x"${'\n'}"""")
        chkNotByteArr("""x'\u0030\u0031'""")
        chkNotByteArr("""x" 1234"""")
        chkNotByteArr("""x"1234 """")
        chkNotByteArr("""X'1234'""")
    }

    @Test fun testIsByteArrayGoodByteArraysWithSpaceDisallowedFalse() {
        chkNotByteArr(" x'00'")
        chkNotByteArr(" x\"00\"")
        chkNotByteArr("x'00' ")
        chkNotByteArr("x\"00\" ")
    }

    private fun chkByteArr(s: String) = assertTrue(RellApiTokenizer.isByteArray(s))
    private fun chkNotByteArr(s: String) = assertFalse(RellApiTokenizer.isByteArray(s))
}
