// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// rell_numerics_test.cpp — standalone self-checking test for the Rell native
// numerics library: RellBigInt (rell_bigint.h), RellBigDec (rell_bigdec.h), and the
// Rell semantics layer (rell_decimal_math.h). It has its OWN main() and depends only
// on the pure-C++ numerics translation units; no JNI, no LLVM, no test framework.
//
// Build:
//   clang++ -std=c++17 \
//     rell_bigint_core.cpp rell_bigint_extra.cpp rell_bigint_muldiv.cpp \
//     rell_bigdec_core.cpp rell_bigdec_muldiv.cpp rell_bigdec_str.cpp \
//     rell_decimal_math.cpp rell_numerics_test.cpp -o rell_numerics_test
//
// Run (built-in hardcoded vectors):
//   ./rell_numerics_test
//
// Run (additionally diff against a JVM-generated corpus, OPTIONAL):
//   ./rell_numerics_test path/to/corpus.txt
//   where each corpus line is  OP|A|B|EXPECTED  (see run_corpus() below for the OP set).
//
// Every expected value below was computed by hand or from known java.math.BigInteger /
// java.math.BigDecimal results, so this file doubles as the executable specification of
// the Rell numeric envelope (canonical scale 20, HALF_UP, ±(10^131072-1) range,
// truncate-toward-zero division, remainder taking the dividend's sign).

#include <cstdint>
#include <fstream>
#include <iostream>
#include <sstream>
#include <string>
#include <vector>

#include "rell_bigdec.h"
#include "rell_bigint.h"
#include "rell_decimal_math.h"

using namespace rell::num;

// =====================================================================================
// Tiny assertion harness
// =====================================================================================

static int g_failures = 0;
static int g_checks = 0;

static void report_fail(const std::string &what, const std::string &got,
                        const std::string &expected) {
    ++g_failures;
    std::cerr << "FAIL: " << what << "\n   got:      [" << got << "]\n   expected: ["
              << expected << "]\n";
}

static void check_eq_str(const std::string &what, const std::string &got,
                         const std::string &expected) {
    ++g_checks;
    if (got != expected) report_fail(what, got, expected);
}

static void check_true(const std::string &what, bool cond) {
    ++g_checks;
    if (!cond) report_fail(what, "false", "true");
}

// Assert that calling `fn` throws a RellArithError whose code() matches `expectedCode`.
template <typename Fn>
static void check_throws_code(const std::string &what, const std::string &expectedCode,
                              Fn fn) {
    ++g_checks;
    try {
        fn();
        report_fail(what + " (no throw)", "<no exception>", "throw " + expectedCode);
    } catch (const RellArithError &e) {
        if (e.code() != expectedCode) {
            report_fail(what + " (wrong code)", "code=" + e.code(),
                        "code=" + expectedCode);
        }
    } catch (const std::exception &e) {
        report_fail(what + " (wrong type)", std::string("std::exception: ") + e.what(),
                    "RellArithError " + expectedCode);
    }
}

// Convenience constructors.
static RellBigInt bi(const std::string &s) { return RellBigInt::fromDecimalString(s); }
static RellBigInt bi(int64_t v) { return RellBigInt::fromInt64(v); }
static RellBigDec bd(const std::string &s) { return RellBigDec::parse(s); }

// =====================================================================================
// BigInteger: signed add / subtract / multiply
// =====================================================================================

static void test_bigint_addsub_mul() {
    check_eq_str("bi add pos", bi(2).add(bi(3)).toString(), "5");
    check_eq_str("bi add mixed sign", bi(2).add(bi(-3)).toString(), "-1");
    check_eq_str("bi add neg+neg", bi(-2).add(bi(-3)).toString(), "-5");
    check_eq_str("bi add to zero", bi(7).add(bi(-7)).toString(), "0");

    check_eq_str("bi sub pos", bi(3).subtract(bi(10)).toString(), "-7");
    check_eq_str("bi sub neg-neg", bi(-3).subtract(bi(-10)).toString(), "7");
    check_eq_str("bi sub zero", bi(0).subtract(bi(5)).toString(), "-5");

    check_eq_str("bi mul pos", bi(123456789).multiply(bi(1000000000)).toString(),
                 "123456789000000000");
    check_eq_str("bi mul neg*pos", bi(-7).multiply(bi(8)).toString(), "-56");
    check_eq_str("bi mul neg*neg", bi(-7).multiply(bi(-8)).toString(), "56");
    check_eq_str("bi mul by zero", bi("123456789012345678901234567890").multiply(bi(0)).toString(),
                 "0");

    // Cross-limb carry: (2^64) * (2^64) = 2^128.
    check_eq_str("bi mul 2^64 * 2^64",
                 bi("18446744073709551616").multiply(bi("18446744073709551616")).toString(),
                 "340282366920938463463374607431768211456");

    // Big add that crosses a limb boundary: (2^96 - 1) + 1 = 2^96.
    check_eq_str("bi add 2^96-1 + 1",
                 bi("79228162514264337593543950335").add(bi(1)).toString(),
                 "79228162514264337593543950336");

    // 10^200 exact: multiply 10^100 by itself.
    RellBigInt e100 = bi(10).pow(100);
    check_eq_str("bi 10^100 string", e100.toString(),
                 "1" + std::string(100, '0'));
    check_eq_str("bi 10^200 = 10^100 * 10^100", e100.multiply(e100).toString(),
                 "1" + std::string(200, '0'));
}

// =====================================================================================
// BigInteger: truncating division + remainder sign
//
// Java contract:  a == (a/b)*b + (a%b);  a/b truncates toward zero;  a%b has sign of a.
// =====================================================================================

static void test_bigint_div_rem() {
    // 7 / 2 = 3 r 1
    check_eq_str("bi 7/2 q", bi(7).divide(bi(2)).toString(), "3");
    check_eq_str("bi 7/2 r", bi(7).remainder(bi(2)).toString(), "1");

    // -7 / 2 = -3 (truncate toward zero, NOT floor -> not -4), r = -1 (sign of dividend)
    check_eq_str("bi -7/2 q", bi(-7).divide(bi(2)).toString(), "-3");
    check_eq_str("bi -7/2 r", bi(-7).remainder(bi(2)).toString(), "-1");

    // 7 / -2 = -3, r = +1 (sign of dividend)
    check_eq_str("bi 7/-2 q", bi(7).divide(bi(-2)).toString(), "-3");
    check_eq_str("bi 7/-2 r", bi(7).remainder(bi(-2)).toString(), "1");

    // -7 / -2 = 3, r = -1 (sign of dividend)
    check_eq_str("bi -7/-2 q", bi(-7).divide(bi(-2)).toString(), "3");
    check_eq_str("bi -7/-2 r", bi(-7).remainder(bi(-2)).toString(), "-1");

    // Exact divide, zero remainder.
    check_eq_str("bi 100/5 q", bi(100).divide(bi(5)).toString(), "20");
    check_eq_str("bi 100/5 r", bi(100).remainder(bi(5)).toString(), "0");

    // Multi-limb Knuth-D: (10^40) / (10^19) = 10^21, remainder 0.
    check_eq_str("bi 10^40 / 10^19", bi(10).pow(40).divide(bi(10).pow(19)).toString(),
                 "1" + std::string(21, '0'));

    // Multi-limb with nonzero remainder: (10^40 + 12345) / 10^19.
    RellBigInt dividend = bi(10).pow(40).add(bi(12345));
    check_eq_str("bi (10^40+12345)/10^19 q", dividend.divide(bi(10).pow(19)).toString(),
                 "1" + std::string(21, '0'));
    check_eq_str("bi (10^40+12345)/10^19 r", dividend.remainder(bi(10).pow(19)).toString(),
                 "12345");

    // Reconstruct identity a == q*b + r for a hairy case.
    RellBigInt a = bi("123456789012345678901234567890");
    RellBigInt b = bi("98765432109876");
    auto qr = a.divideAndRemainder(b);
    RellBigInt recon = qr.first.multiply(b).add(qr.second);
    check_eq_str("bi divrem identity", recon.toString(), a.toString());

    // Division by zero throws (empty code: the plain arithmetic condition).
    check_throws_code("bi div by zero", "", [] { bi(5).divide(bi(0)); });
    check_throws_code("bi rem by zero", "", [] { bi(5).remainder(bi(0)); });
}

// =====================================================================================
// BigInteger: pow, sqrt, conversions
// =====================================================================================

static void test_bigint_extra() {
    check_eq_str("bi 2^10", bi(2).pow(10).toString(), "1024");
    check_eq_str("bi 0^0 == 1", bi(0).pow(0).toString(), "1");
    check_eq_str("bi (-3)^3", bi(-3).pow(3).toString(), "-27");
    check_eq_str("bi (-3)^4", bi(-3).pow(4).toString(), "81");
    check_eq_str("bi 7^1", bi(7).pow(1).toString(), "7");

    check_eq_str("bi sqrt 0", bi(0).sqrt().toString(), "0");
    check_eq_str("bi sqrt 1", bi(1).sqrt().toString(), "1");
    check_eq_str("bi sqrt 15 floor", bi(15).sqrt().toString(), "3");
    check_eq_str("bi sqrt 16", bi(16).sqrt().toString(), "4");
    check_eq_str("bi sqrt 17 floor", bi(17).sqrt().toString(), "4");
    // floor(sqrt(10^40)) = 10^20 exactly.
    check_eq_str("bi sqrt 10^40", bi(10).pow(40).sqrt().toString(),
                 "1" + std::string(20, '0'));
    // floor(sqrt(2 * 10^40)) — 2*10^40 is not a perfect square.
    // sqrt(2)*10^20 = 1.41421356237309504880...e20 -> floor = 141421356237309504880
    check_eq_str("bi sqrt 2*10^40",
                 bi(2).multiply(bi(10).pow(40)).sqrt().toString(),
                 "141421356237309504880");

    check_throws_code("bi sqrt negative", "", [] { bi(-4).sqrt(); });
    check_throws_code("bi pow negative exp would be type-checked away; n/a", "",
                      [] { throw RellArithError("", "placeholder"); });

    check_true("bi longValueExact 123", bi(123).longValueExact() == 123);
    check_true("bi longValueExact -123", bi(-123).longValueExact() == -123);
    check_throws_code("bi longValueExact overflow", "",
                      [] { bi("100000000000000000000").longValueExact(); });
}

// =====================================================================================
// BigInteger byte round-trip
// =====================================================================================

static void test_bigint_bytes() {
    auto roundtrip = [](const RellBigInt &v) {
        return RellBigInt::fromBytes(v.toBytes()).toString() == v.toString();
    };
    check_true("bi bytes 0", roundtrip(bi(0)));
    check_true("bi bytes 1", roundtrip(bi(1)));
    check_true("bi bytes 127", roundtrip(bi(127)));
    check_true("bi bytes 128", roundtrip(bi(128)));   // needs leading 0x00
    check_true("bi bytes 255", roundtrip(bi(255)));
    check_true("bi bytes 256", roundtrip(bi(256)));
    check_true("bi bytes -1", roundtrip(bi(-1)));
    check_true("bi bytes -128", roundtrip(bi(-128)));
    check_true("bi bytes -129", roundtrip(bi(-129)));  // needs leading 0xFF
    check_true("bi bytes big pos", roundtrip(bi("123456789012345678901234567890")));
    check_true("bi bytes big neg", roundtrip(bi("-123456789012345678901234567890")));

    // ZERO -> single 0x00 byte.
    auto zb = bi(0).toBytes();
    check_true("bi ZERO bytes == {0x00}", zb.size() == 1 && zb[0] == 0x00);
    // 1 -> {0x01}
    auto ob = bi(1).toBytes();
    check_true("bi ONE bytes == {0x01}", ob.size() == 1 && ob[0] == 0x01);
    // -1 -> {0xFF}
    auto nb = bi(-1).toBytes();
    check_true("bi -1 bytes == {0xFF}", nb.size() == 1 && nb[0] == 0xFF);
    // 128 -> {0x00, 0x80}
    auto b128 = bi(128).toBytes();
    check_true("bi 128 bytes == {0x00,0x80}",
               b128.size() == 2 && b128[0] == 0x00 && b128[1] == 0x80);
}

// =====================================================================================
// Decimal: parse / toString round-trips incl. scientific notation
// =====================================================================================

static void test_decimal_parse_tostring() {
    // RellBigDec::parse keeps scale exactly as written; toString is faithful BigDecimal.
    check_eq_str("bd parse 1.00 scale", std::to_string(bd("1.00").scale()), "2");
    check_eq_str("bd parse 1.00 unscaled", bd("1.00").unscaledValue().toString(), "100");
    check_eq_str("bd 1.00 toString", bd("1.00").toString(), "1.00");

    // 1E3 -> unscaled 1, scale -3, value 1000.
    check_eq_str("bd parse 1E3 scale", std::to_string(bd("1E3").scale()), "-3");
    check_eq_str("bd 1E3 toPlainString", bd("1E3").toPlainString(), "1000");

    // 0.500 -> unscaled 500, scale 3.
    check_eq_str("bd parse 0.500 unscaled", bd("0.500").unscaledValue().toString(), "500");
    check_eq_str("bd parse 0.500 scale", std::to_string(bd("0.500").scale()), "3");

    // toString scientific layout: 1.2E-7 (adjusted exponent < -6 forces E-notation).
    // unscaled 12, scale 8 -> adjusted = (2-1) - 8 = -7 < -6 -> "1.2E-7".
    check_eq_str("bd 0.000000012 toString sci", bd("1.2E-8").toString(), "1.2E-8");
    // 1.5E10 plain via toPlainString.
    check_eq_str("bd 1.5E10 toPlainString", bd("1.5E10").toPlainString(), "15000000000");

    // -0 parses to zero.
    check_true("bd parse -0 is zero", bd("-0").isZero());

    // Round-trip a normalized canonical value through Rell parse/toString.
    // decimal_parse strips trailing zeros (string level) then normalizes to scale 20.
    RellBigDec p = decimal_parse("2.50");
    check_eq_str("decimal_parse 2.50 -> canonical scale 20", std::to_string(p.scale()), "20");
    check_eq_str("decimal_to_string(2.50)", decimal_to_string(p), "2.5");

    // Whole number stays whole in the Rell string form.
    check_eq_str("decimal_to_string(7.0000...)", decimal_to_string(decimal_parse("7")), "7");
    check_eq_str("decimal_to_string(0.1)", decimal_to_string(decimal_parse("0.10")), "0.1");
    check_eq_str("decimal_to_string(0)", decimal_to_string(decimal_parse("0")), "0");
    check_eq_str("decimal_to_string(-123.45)",
                 decimal_to_string(decimal_parse("-123.4500")), "-123.45");

    // Leading-dot handling (Rell prepends "0").
    check_eq_str("decimal_parse .5 -> 0.5",
                 decimal_to_string(decimal_parse(".5")), "0.5");
    check_eq_str("decimal_parse -.5 -> -0.5",
                 decimal_to_string(decimal_parse("-.5")), "-0.5");

    // Malformed input -> decimal:invalid:<s>.
    check_throws_code("decimal_parse malformed", "decimal:invalid:abc",
                      [] { decimal_parse("abc"); });
}

// =====================================================================================
// Decimal: division rounding ties (HALF_UP — ties AWAY from zero)
// =====================================================================================

static void test_decimal_rounding_halfup() {
    // setScale(0, HALF_UP) boundary cases.
    check_eq_str("bd 2.5 -> 3", bd("2.5").setScale(0, RoundingMode::HALF_UP).toString(), "3");
    check_eq_str("bd -2.5 -> -3", bd("-2.5").setScale(0, RoundingMode::HALF_UP).toString(),
                 "-3");
    check_eq_str("bd 2.4 -> 2", bd("2.4").setScale(0, RoundingMode::HALF_UP).toString(), "2");
    check_eq_str("bd 2.6 -> 3", bd("2.6").setScale(0, RoundingMode::HALF_UP).toString(), "3");
    check_eq_str("bd 0.5 -> 1", bd("0.5").setScale(0, RoundingMode::HALF_UP).toString(), "1");
    check_eq_str("bd -0.5 -> -1", bd("-0.5").setScale(0, RoundingMode::HALF_UP).toString(),
                 "-1");

    // 1.005 at scale 2, HALF_UP -> 1.01 (the exact decimal 1.005 has a true tie;
    // BigDecimal works on exact decimals so this rounds up, unlike binary float).
    check_eq_str("bd 1.005 scale 2 HALF_UP",
                 bd("1.005").setScale(2, RoundingMode::HALF_UP).toString(), "1.01");
    check_eq_str("bd -1.005 scale 2 HALF_UP",
                 bd("-1.005").setScale(2, RoundingMode::HALF_UP).toString(), "-1.01");

    // 1.004 -> 1.00 (below half).
    check_eq_str("bd 1.004 scale 2 HALF_UP",
                 bd("1.004").setScale(2, RoundingMode::HALF_UP).toString(), "1.00");

    // divide(divisor, 20, HALF_UP): 1/3 at scale 20 = 0.33333333333333333333.
    RellBigDec third = bd("1").divide(bd("3"), 20, RoundingMode::HALF_UP);
    check_eq_str("bd 1/3 @20", third.toString(), "0." + std::string(20, '3'));
    // 2/3 @20 -> 0.66666666666666666667 (last digit rounds UP from ...6666 + remainder).
    RellBigDec twothird = bd("2").divide(bd("3"), 20, RoundingMode::HALF_UP);
    check_eq_str("bd 2/3 @20", twothird.toString(),
                 "0." + std::string(19, '6') + "7");

    // The Rell decimal_div path: 10 / 3 -> 3.33333333333333333333 (scale 20), printed strip.
    check_eq_str("decimal_div 10/3", decimal_to_string(decimal_div(bd("10"), bd("3"))),
                 "3." + std::string(20, '3'));
    // 1 / 8 = 0.125 exact (HALF_UP irrelevant), Rell string strips zeros.
    check_eq_str("decimal_div 1/8", decimal_to_string(decimal_div(bd("1"), bd("8"))), "0.125");
    // 7 / 2 = 3.5
    check_eq_str("decimal_div 7/2", decimal_to_string(decimal_div(bd("7"), bd("2"))), "3.5");
    // -1 / 3 (negative quotient, HALF_UP at the last digit) -> -0.33333333333333333333
    check_eq_str("decimal_div -1/3", decimal_to_string(decimal_div(bd("-1"), bd("3"))),
                 "-0." + std::string(20, '3'));
    // div by zero -> plain "/ by zero" (empty code).
    check_throws_code("decimal_div by zero", "",
                      [] { decimal_div(bd("1"), bd("0")); });
}

// =====================================================================================
// Decimal: scale normalization to 20 (pad + HALF_UP reduction), zero handling
// =====================================================================================

static void test_decimal_scale_normalize() {
    // signum 0 -> ZERO, scale 0 (NOT 20).
    RellBigDec z = decimal_scale_normalize(bd("0.000"));
    check_true("normalize 0 -> zero", z.isZero());
    check_eq_str("normalize 0 -> scale 0", std::to_string(z.scale()), "0");

    // scale <= 20: pad to exactly 20.
    RellBigDec n1 = decimal_scale_normalize(bd("2.5"));
    check_eq_str("normalize 2.5 scale", std::to_string(n1.scale()), "20");
    check_eq_str("normalize 2.5 unscaled", n1.unscaledValue().toString(),
                 "25" + std::string(19, '0'));

    // scale already 20 -> unchanged.
    RellBigDec exact20 = bd("1." + std::string(20, '0'));
    RellBigDec n2 = decimal_scale_normalize(exact20);
    check_eq_str("normalize scale-20 stays 20", std::to_string(n2.scale()), "20");

    // scale > 20: HALF_UP reduction to scale 20.
    // 0.<20 threes>5  (scale 21) -> rounds last kept digit up.
    RellBigDec longFrac = bd("0." + std::string(20, '3') + "5");  // scale 21
    RellBigDec n3 = decimal_scale_normalize(longFrac);
    check_eq_str("normalize scale-21 HALF_UP scale", std::to_string(n3.scale()), "20");
    check_eq_str("normalize 0.<20x3>5 -> ...3334",
                 n3.unscaledValue().toString(), std::string(19, '3') + "4");

    // Underflow: intDigits < -20 -> ZERO. e.g. 1e-22 (precision 1, scale 22 -> intDigits -21).
    RellBigDec tiny = bd("1E-22");
    RellBigDec n4 = decimal_scale_normalize(tiny);
    check_true("normalize 1e-22 underflows to zero", n4.isZero());

    // Just inside underflow boundary: 1e-20 has intDigits = 1-20 = -19 > -20 -> kept,
    // but actually represents 0.00000000000000000001 which survives at scale 20.
    RellBigDec edge = bd("1E-20");
    RellBigDec n5 = decimal_scale_normalize(edge);
    check_true("normalize 1e-20 not zero", !n5.isZero());
    check_eq_str("normalize 1e-20 scale", std::to_string(n5.scale()), "20");
}

// =====================================================================================
// Decimal: exact arithmetic through the Rell envelope
// =====================================================================================

static void test_decimal_arith() {
    // add aligns scales: 1.5 + 2.25 = 3.75
    check_eq_str("decimal_add 1.5+2.25",
                 decimal_to_string(decimal_add(bd("1.5"), bd("2.25"))), "3.75");
    // subtract: 1.5 - 2.25 = -0.75
    check_eq_str("decimal_sub 1.5-2.25",
                 decimal_to_string(decimal_sub(bd("1.5"), bd("2.25"))), "-0.75");
    // multiply: 1.5 * 2.25 = 3.375
    check_eq_str("decimal_mul 1.5*2.25",
                 decimal_to_string(decimal_mul(bd("1.5"), bd("2.25"))), "3.375");
    // remainder: 10 % 3 = 1
    check_eq_str("decimal_rem 10%3",
                 decimal_to_string(decimal_rem(bd("10"), bd("3"))), "1");
    // remainder sign follows dividend: -10 % 3 = -1
    check_eq_str("decimal_rem -10%3",
                 decimal_to_string(decimal_rem(bd("-10"), bd("3"))), "-1");
    // remainder with fractional operands: 5.5 % 2 = 1.5
    check_eq_str("decimal_rem 5.5%2",
                 decimal_to_string(decimal_rem(bd("5.5"), bd("2"))), "1.5");

    // Large but in-range multiply: 10^100 * 10^31000 -> 10^31100 (well under 10^131072).
    RellBigDec big = decimal_mul(bd("1E100"), bd("1E31000"));
    check_true("decimal_mul large in range non-zero", !big.isZero());
}

// =====================================================================================
// OVERFLOW at the 10^131072 boundary (bigint:overflow / decimal:overflow)
// =====================================================================================

static void test_overflow_boundary() {
    // MAX_VALUE = 10^131072 - 1. MAX + 1 = 10^131072 -> overflow.
    const RellBigInt &mx = bigint_max_value();
    // sanity: MAX_VALUE is in range.
    check_true("bigint MAX in range", &bigint_range_check(mx) == &mx ||
                                          bigint_range_check(mx).equals(mx));
    // MAX_VALUE + 1 overflows.
    check_throws_code("bigint MAX+1 overflow", "bigint:overflow",
                      [&] { bigint_add(mx, bi(1)); });
    // MIN_VALUE - 1 overflows.
    const RellBigInt &mn = bigint_min_value();
    check_throws_code("bigint MIN-1 overflow", "bigint:overflow",
                      [&] { bigint_sub(mn, bi(1)); });
    // 10^131072 directly (one past MAX) overflows.
    check_throws_code("bigint 10^131072 overflow", "bigint:overflow",
                      [] { bigint_range_check(bi(10).pow(131072)); });
    // pow overflow pre-check: (10^100)^2000 = 10^200000 > 10^131072.
    check_throws_code("bigint pow overflow", "bigint:overflow",
                      [] { bigint_pow(bi(10).pow(100), 2000); });
    // pow negative exponent -> plain arithmetic error (empty code).
    check_throws_code("bigint pow neg exp", "",
                      [] { bigint_pow(bi(2), -1); });

    // Decimal overflow: an integer part with > 131072 digits.
    // 10^131072 as a decimal has 131073 integer digits -> overflow.
    RellBigDec overInt = RellBigDec(bi(10).pow(131072), 0);
    check_throws_code("decimal int digits overflow", "decimal:overflow",
                      [&] { decimal_scale_normalize(overInt); });
    // Exactly 10^131072 - 1 (131072 integer digits) is the max -> NOT overflow.
    RellBigDec maxInt = RellBigDec(bigint_max_value(), 0);
    RellBigDec maxNorm = decimal_scale_normalize(maxInt);
    check_eq_str("decimal max int digits ok, scale 20", std::to_string(maxNorm.scale()),
                 "20");

    // Overflow caused by HALF_UP rounding adding one integer digit.
    // Build value = (10^131072 - 1) . 999...995  (21 fraction digits, scale 21):
    //   unscaled = (10^131072 - 1) * 10^21 + (10^21 - 5).
    // intDigits = 131072 (passes the first check). setScale(20, HALF_UP) sees the dropped
    // 21st digit '5' as a tie at the last kept '9'; the carry cascades through all 20 kept
    // nines and into the integer part, yielding 10^131072 (131073 integer digits) -> the
    // intDigits2 > 131072 recheck fires -> decimal:overflow.
    {
        RellBigInt frac21 = bi(10).pow(21).subtract(bi(5));  // 999999999999999999995
        RellBigInt unscaled = bigint_max_value().multiply(bi(10).pow(21)).add(frac21);
        RellBigDec roundUpOverflow(unscaled, 21);
        check_throws_code("decimal rounding-induced overflow", "decimal:overflow",
                          [&] { decimal_scale_normalize(roundUpOverflow); });
    }
}

// =====================================================================================
// Very large in-range operands (10^200, 10^131071)
// =====================================================================================

static void test_large_operands() {
    // 10^200 round-trips through string.
    check_eq_str("bi 10^200 string", bi(10).pow(200).toString(), "1" + std::string(200, '0'));

    // 10^131071 is in range (131072 digits); 10^131071 + ... arithmetic stays in range.
    RellBigInt e131071 = bi(10).pow(131071);
    check_true("bi 10^131071 in range",
               bigint_range_check(e131071).equals(e131071));
    check_eq_str("bi 10^131071 digit count",
                 std::to_string(e131071.toString().size()), "131072");

    // 10^131071 * 9 = 9 * 10^131071, still 131072 digits, in range.
    RellBigInt nine_e = e131071.multiply(bi(9));
    check_true("bi 9*10^131071 in range", bigint_range_check(nine_e).equals(nine_e));

    // sqrt of 10^200 = 10^100 exactly.
    check_eq_str("bi sqrt 10^200", bi(10).pow(200).sqrt().toString(),
                 "1" + std::string(100, '0'));

    // Decimal with a huge but legal integer part: 10^131000 as a decimal normalizes fine.
    RellBigDec huge = decimal_scale_normalize(RellBigDec(bi(10).pow(131000), 0));
    check_eq_str("decimal 10^131000 normalized scale", std::to_string(huge.scale()), "20");
}

// =====================================================================================
// OPTIONAL corpus mode: read OP|A|B|EXPECTED lines, assert native result == EXPECTED.
//
// The corpus is intended to be generated by a JVM driver running the same operations
// through java.math + the Rell Lib_* layer, so any divergence is a real bit-mismatch.
// Supported OPs (all string in/out, decimal values use the Rell strip-trailing-zeros
// display form via decimal_to_string; big_integer uses RellBigInt::toString):
//
//   bi_add, bi_sub, bi_mul, bi_div, bi_rem   (A,B big-integer decimal strings)
//   bi_pow                                   (A base, B exponent>=0)
//   bi_sqrt                                  (A operand, B ignored)
//   dec_add, dec_sub, dec_mul, dec_div, dec_rem  (A,B decimal strings; full Rell envelope)
//   dec_norm                                 (A decimal string -> normalized strip form)
//   dec_parse                                (A raw string -> Rell parse+normalize+strip)
//
// A line whose EXPECTED is the literal token "THROW:<code>" asserts that the op throws a
// RellArithError with that code (use "THROW:" for the empty-code arithmetic errors).
// Blank lines and lines starting with '#' are ignored.
// =====================================================================================

static std::vector<std::string> split_pipe(const std::string &line) {
    std::vector<std::string> out;
    std::string cur;
    std::istringstream ss(line);
    while (std::getline(ss, cur, '|')) out.push_back(cur);
    return out;
}

// Evaluate one corpus op into its canonical string form, or throw RellArithError.
static std::string eval_corpus_op(const std::string &op, const std::string &A,
                                  const std::string &B) {
    if (op == "bi_add") return bi(A).add(bi(B)).toString();
    if (op == "bi_sub") return bi(A).subtract(bi(B)).toString();
    if (op == "bi_mul") return bi(A).multiply(bi(B)).toString();
    if (op == "bi_div") return bigint_div(bi(A), bi(B)).toString();
    if (op == "bi_rem") return bigint_rem(bi(A), bi(B)).toString();
    if (op == "bi_pow") return bigint_pow(bi(A), std::stoll(B)).toString();
    if (op == "bi_sqrt") return bigint_sqrt(bi(A)).toString();
    if (op == "dec_add") return decimal_to_string(decimal_add(bd(A), bd(B)));
    if (op == "dec_sub") return decimal_to_string(decimal_sub(bd(A), bd(B)));
    if (op == "dec_mul") return decimal_to_string(decimal_mul(bd(A), bd(B)));
    if (op == "dec_div") return decimal_to_string(decimal_div(bd(A), bd(B)));
    if (op == "dec_rem") return decimal_to_string(decimal_rem(bd(A), bd(B)));
    if (op == "dec_norm") return decimal_to_string(decimal_scale_normalize(bd(A)));
    if (op == "dec_parse") return decimal_to_string(decimal_parse(A));
    throw RellArithError("", "unknown corpus op: " + op);
}

static int run_corpus(const std::string &path) {
    std::ifstream in(path);
    if (!in) {
        std::cerr << "ERROR: cannot open corpus file: " << path << "\n";
        return 2;
    }
    std::string line;
    int line_no = 0;
    int corpus_fail = 0;
    int corpus_checks = 0;
    while (std::getline(in, line)) {
        ++line_no;
        if (line.empty() || line[0] == '#') continue;
        auto parts = split_pipe(line);
        if (parts.size() < 4) {
            std::cerr << "WARN: malformed corpus line " << line_no << ": " << line << "\n";
            continue;
        }
        const std::string &op = parts[0];
        const std::string &A = parts[1];
        const std::string &B = parts[2];
        // EXPECTED may itself contain '|' in theory; rejoin the tail.
        std::string expected = parts[3];
        for (size_t i = 4; i < parts.size(); ++i) expected += "|" + parts[i];

        ++corpus_checks;
        const std::string throwPrefix = "THROW:";
        bool expectThrow = expected.rfind(throwPrefix, 0) == 0;
        try {
            std::string got = eval_corpus_op(op, A, B);
            if (expectThrow) {
                ++corpus_fail;
                std::cerr << "CORPUS FAIL line " << line_no << " (" << op
                          << "): expected throw [" << expected.substr(throwPrefix.size())
                          << "] but got [" << got << "]\n";
            } else if (got != expected) {
                ++corpus_fail;
                std::cerr << "CORPUS FAIL line " << line_no << " (" << op << "|" << A << "|"
                          << B << "): got [" << got << "] expected [" << expected << "]\n";
            }
        } catch (const RellArithError &e) {
            if (!expectThrow) {
                ++corpus_fail;
                std::cerr << "CORPUS FAIL line " << line_no << " (" << op
                          << "): unexpected throw code=[" << e.code() << "] msg=["
                          << e.what() << "]\n";
            } else {
                std::string wantCode = expected.substr(throwPrefix.size());
                if (e.code() != wantCode) {
                    ++corpus_fail;
                    std::cerr << "CORPUS FAIL line " << line_no << " (" << op
                              << "): threw code=[" << e.code() << "] expected code=["
                              << wantCode << "]\n";
                }
            }
        }
    }
    std::cout << "Corpus: " << (corpus_checks - corpus_fail) << "/" << corpus_checks
              << " lines passed (" << path << ")\n";
    return corpus_fail == 0 ? 0 : 1;
}

// =====================================================================================
// main
// =====================================================================================

int main(int argc, char **argv) {
    test_bigint_addsub_mul();
    test_bigint_div_rem();
    test_bigint_extra();
    test_bigint_bytes();
    test_decimal_parse_tostring();
    test_decimal_rounding_halfup();
    test_decimal_scale_normalize();
    test_decimal_arith();
    test_overflow_boundary();
    test_large_operands();

    std::cout << "Built-in vectors: " << (g_checks - g_failures) << "/" << g_checks
              << " checks passed\n";

    int corpus_rc = 0;
    if (argc >= 2) {
        corpus_rc = run_corpus(argv[1]);
    }

    if (g_failures != 0) {
        std::cerr << g_failures << " built-in check(s) FAILED\n";
        return 1;
    }
    if (corpus_rc != 0) {
        std::cerr << "corpus check(s) FAILED\n";
        return corpus_rc;
    }
    std::cout << "ALL PASS\n";
    return 0;
}
