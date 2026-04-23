// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.

// Standalone reference-corpus generator for differential testing of the native C++ numerics port
// (rell::num::RellBigInt / RellBigDec). Uses ONLY java.math from the JDK (no Rell dependencies), so
// it compiles with plain javac. It reproduces EXACTLY Rell's numeric envelope semantics
// (Lib_BigIntegerMath / Lib_DecimalMath / Rt_BigIntegerValue / Rt_DecimalValue) on top of the JDK's
// java.math.BigInteger / java.math.BigDecimal, which are themselves the bit-exact reference the C++
// port targets.
//
// Output: lines of the form
//     OP|A|B|EXPECTED
// one per test case, where OP is one of:
//     biadd bisub bimul bidiv birem bipow bisqrt     (big_integer)
//     decadd decsub decmul decdiv decrem             (decimal)
// A and B are the operands (decimals printed in their NORMALIZED toPlainString form), and EXPECTED is
// either the result or an error token:
//     OVERFLOW:bigint:overflow      big_integer result outside +-(10^131072 - 1)
//     OVERFLOW:decimal:overflow     decimal result outside the Rell range after scale() normalization
//     ERROR:<kind>                  div/mod by zero, negative sqrt, negative exponent, etc.
// For unary ops (bisqrt) B is the literal token "_" and is ignored by the consumer.
//
// Determinism: a fixed-seed java.util.Random plus a hand-picked edge-case table. Running this program
// twice produces byte-identical output.

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class GenNumericsCorpus {

    // ---- Rell constants (Lib_BigIntegerMath / Lib_DecimalMath) ----------------------------------

    static final int BIGINT_PRECISION = 131072;
    static final int DECIMAL_INT_DIGITS = 131072;
    static final int DECIMAL_FRAC_DIGITS = 20;

    // MAX = 10^131072 - 1, MIN = -MAX.
    static final BigInteger BIGINT_MAX = BigInteger.TEN.pow(BIGINT_PRECISION).subtract(BigInteger.ONE);
    static final BigInteger BIGINT_MIN = BIGINT_MAX.negate();

    static final String BIGINT_OVERFLOW = "OVERFLOW:bigint:overflow";
    static final String DECIMAL_OVERFLOW = "OVERFLOW:decimal:overflow";

    // ---- big_integer envelope (mirrors Rt_BigIntegerValue.getOrNull / Lib_BigIntegerMath) -------

    // Range check identical to Rt_BigIntegerValue.getOrNull: out of [MIN, MAX] -> overflow.
    static boolean bigintInRange(BigInteger v) {
        return v.compareTo(BIGINT_MIN) >= 0 && v.compareTo(BIGINT_MAX) <= 0;
    }

    static String biResult(BigInteger v) {
        return bigintInRange(v) ? v.toString() : BIGINT_OVERFLOW;
    }

    // ---- decimal envelope (mirrors Lib_DecimalMath.scale + Rt_DecimalValue.get) ------------------

    // Reproduces Lib_DecimalMath.scale(BigDecimal) faithfully. Returns the normalized BigDecimal, or
    // null to signal "decimal:overflow". The canonical stored form has scale 20, except ZERO -> scale 0.
    static BigDecimal scaleNormalize(BigDecimal v) {
        if (v.signum() == 0) {
            return BigDecimal.ZERO;
        }

        int scale = v.scale();
        int intDigits = v.precision() - scale;
        if (intDigits > DECIMAL_INT_DIGITS) {
            return null;
        } else if (intDigits < -DECIMAL_FRAC_DIGITS) {
            return BigDecimal.ZERO;
        }

        if (scale <= DECIMAL_FRAC_DIGITS) {
            return v.setScale(DECIMAL_FRAC_DIGITS);
        } else {
            // The number of integer digits may grow (by one) because of rounding - check again.
            BigDecimal v2 = v.setScale(DECIMAL_FRAC_DIGITS, RoundingMode.HALF_UP);
            int intDigits2 = v2.precision() - v2.scale();
            return (intDigits2 > DECIMAL_INT_DIGITS) ? null : v2;
        }
    }

    // Normalize-and-render. Returns toPlainString of the normalized value, or DECIMAL_OVERFLOW.
    static String decResult(BigDecimal v) {
        BigDecimal n = scaleNormalize(v);
        return (n == null) ? DECIMAL_OVERFLOW : n.toPlainString();
    }

    // Render an operand for printing: normalize it first so the printed A/B match what the native port
    // would store, and so the consumer feeds the same canonical operands to the native code. Operands
    // are always chosen to be in range (we never feed out-of-range operands), so scaleNormalize won't
    // return null here; guard anyway.
    static String decOperand(BigDecimal v) {
        BigDecimal n = scaleNormalize(v);
        if (n == null) {
            throw new IllegalStateException("operand out of range: " + v.toPlainString());
        }
        return n.toPlainString();
    }

    // ---- emit helpers ---------------------------------------------------------------------------

    static final List<String> LINES = new ArrayList<>();

    static void emitBin(String op, String a, String b, String expected) {
        LINES.add(op + "|" + a + "|" + b + "|" + expected);
    }

    // big_integer binary ops. a,b already in range.
    static void biBin(BigInteger a, BigInteger b) {
        String as = a.toString(), bs = b.toString();
        emitBin("biadd", as, bs, biResult(a.add(b)));
        emitBin("bisub", as, bs, biResult(a.subtract(b)));
        emitBin("bimul", as, bs, biResult(a.multiply(b)));

        // divide = truncate toward zero (BigInteger.divide). div/rem by zero -> ERROR.
        if (b.signum() == 0) {
            emitBin("bidiv", as, bs, "ERROR:div_zero");
            emitBin("birem", as, bs, "ERROR:div_zero");
        } else {
            emitBin("bidiv", as, bs, biResult(a.divide(b)));
            emitBin("birem", as, bs, biResult(a.remainder(b)));
        }
    }

    // big_integer pow: exp >= 0. Mirrors NumericType_BigInteger.pow overflow handling (which throws
    // ArithmeticException -> Rell surfaces it; for the corpus we collapse the two overflow paths into
    // the same OVERFLOW token because the result is out of range either way).
    static void biPow(BigInteger base, long exp) {
        String as = base.toString(), bs = Long.toString(exp);
        if (exp < 0) {
            emitBin("bipow", as, bs, "ERROR:neg_exp");
            return;
        }
        // Fast overflow gate by bit length (matches the Rell guard) avoids computing absurd results.
        long baseExp = Math.max(base.abs().bitLength() - 1, 0);
        try {
            long resExp = Math.multiplyExact(baseExp, exp);
            if (resExp + 1 > BIGINT_MAX.bitLength()) {
                emitBin("bipow", as, bs, BIGINT_OVERFLOW);
                return;
            }
        } catch (ArithmeticException overflow) {
            emitBin("bipow", as, bs, BIGINT_OVERFLOW);
            return;
        }
        BigInteger res = base.pow((int) exp);
        emitBin("bipow", as, bs, biResult(res));
    }

    // big_integer sqrt: floor of exact sqrt, non-negative input only (spec: bisqrt = BigInteger.sqrt).
    static void biSqrt(BigInteger a) {
        String as = a.toString();
        if (a.signum() < 0) {
            emitBin("bisqrt", as, "_", "ERROR:negative");
            return;
        }
        emitBin("bisqrt", as, "_", biResult(a.sqrt()));
    }

    // decimal binary ops. a,b already in range.
    static void decBin(BigDecimal a, BigDecimal b) {
        String as = decOperand(a), bs = decOperand(b);
        emitBin("decadd", as, bs, decResult(a.add(b)));
        emitBin("decsub", as, bs, decResult(a.subtract(b)));
        emitBin("decmul", as, bs, decResult(a.multiply(b)));

        // divide(a,b) = a.divide(b, 20, HALF_UP). div/rem by zero -> ERROR.
        if (b.signum() == 0) {
            emitBin("decdiv", as, bs, "ERROR:div_zero");
            emitBin("decrem", as, bs, "ERROR:div_zero");
        } else {
            emitBin("decdiv", as, bs, decResult(a.divide(b, DECIMAL_FRAC_DIGITS, RoundingMode.HALF_UP)));
            emitBin("decrem", as, bs, decResult(a.remainder(b)));
        }
    }

    // ---- operand pools --------------------------------------------------------------------------

    static List<BigInteger> bigintOperands(Random rnd) {
        List<BigInteger> xs = new ArrayList<>();

        // Hand-picked edge cases.
        xs.add(BigInteger.ZERO);
        xs.add(BigInteger.ONE);
        xs.add(BigInteger.valueOf(-1));
        xs.add(BigInteger.TWO);
        xs.add(BigInteger.valueOf(-2));
        xs.add(BigInteger.TEN);
        xs.add(BigInteger.valueOf(Long.MIN_VALUE));
        xs.add(BigInteger.valueOf(Long.MAX_VALUE));
        xs.add(BigInteger.valueOf(Integer.MIN_VALUE));
        xs.add(BigInteger.valueOf(Integer.MAX_VALUE));
        // 2^32 boundaries (uint32 limb edges in the native port).
        xs.add(BigInteger.ONE.shiftLeft(32));
        xs.add(BigInteger.ONE.shiftLeft(32).subtract(BigInteger.ONE));
        xs.add(BigInteger.ONE.shiftLeft(64));
        xs.add(BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE));
        xs.add(BigInteger.ONE.shiftLeft(96));
        // Powers of ten at the requested k values.
        for (int k : new int[] {1, 18, 19, 20, 131071}) {
            xs.add(BigInteger.TEN.pow(k));
            xs.add(BigInteger.TEN.pow(k).negate());
        }
        // Range boundaries.
        xs.add(BIGINT_MAX);
        xs.add(BIGINT_MIN);
        xs.add(BIGINT_MAX.subtract(BigInteger.ONE));
        xs.add(BIGINT_MIN.add(BigInteger.ONE));
        // Just over the boundary is never used as an operand (we only feed in-range values), but the
        // half-range value exercises the multiply overflow path.
        xs.add(BigInteger.TEN.pow(BIGINT_PRECISION / 2));
        xs.add(BigInteger.TEN.pow(BIGINT_PRECISION / 2).negate());

        // Pseudo-random spread across bit widths.
        for (int bits : new int[] {16, 31, 32, 33, 63, 64, 65, 96, 127, 128, 200, 512, 1024, 4096}) {
            for (int i = 0; i < 3; i++) {
                BigInteger v = new BigInteger(bits, rnd);
                if (rnd.nextBoolean()) v = v.negate();
                xs.add(v);
            }
        }
        return xs;
    }

    static List<BigDecimal> decimalOperands(Random rnd) {
        List<BigDecimal> xs = new ArrayList<>();

        // Hand-picked edge cases incl. rounding ties (HALF_UP = ties away from zero).
        for (String s : new String[] {
                "0", "1", "-1", "2", "-2", "10", "-10", "0.5", "-0.5",
                "2.5", "-2.5", "1.5", "-1.5", "0.125", "-0.125", "1.005", "-1.005",
                "0.05", "-0.05", "0.15", "0.25", "0.35", "0.45", "0.55", "0.65", "0.75", "0.85", "0.95",
                "9999999999.9999999999",
                "0.000000000000000000005",   // 21 frac digits -> rounds at scale 20
                "0.000000000000000000004999", // just below the tie at scale 20
                "0.000000000000000000015",   // tie at scale 20, HALF_UP -> away from zero
                "-0.000000000000000000015",
                "12345.678901234567890123456789", // long fractional, exercises HALF_UP truncation
                "0.000000000000000000000001",     // intDigits < -20 -> ZERO after normalization
                "100000000000000000.123456789012345678901234567890",
                "3.141592653589793238462643383279",
                "2.718281828459045235360287471352",
                "1E-25", "1E-30", "9.99999999999999999995",
        }) {
            xs.add(new BigDecimal(s));
        }

        // Long.MIN/MAX as decimals.
        xs.add(new BigDecimal(BigInteger.valueOf(Long.MIN_VALUE)));
        xs.add(new BigDecimal(BigInteger.valueOf(Long.MAX_VALUE)));

        // 10^k for the requested k (these are huge but valid; multiply with another keeps in range or
        // overflows -> exercises decimal:overflow).
        for (int k : new int[] {1, 18, 19, 20}) {
            xs.add(BigDecimal.TEN.pow(k));
            xs.add(BigDecimal.TEN.pow(k).negate());
        }
        // 10^131071 (one below the integer-digit limit). Cheap to construct; multiplying two of these
        // overflows the 131072 integer-digit envelope -> decimal:overflow.
        xs.add(BigDecimal.TEN.pow(131071));
        xs.add(BigDecimal.TEN.pow(131071).negate());

        // Near the integer-digit boundary.
        xs.add(BigDecimal.TEN.pow(DECIMAL_INT_DIGITS).subtract(BigDecimal.ONE)); // ~10^131072 - 1, biggest int part
        xs.add(BigDecimal.TEN.pow(DECIMAL_INT_DIGITS - 1));

        // Mixed-scale pseudo-random decimals.
        for (int i = 0; i < 40; i++) {
            int unscaledBits = 1 + rnd.nextInt(160);
            BigInteger unscaled = new BigInteger(unscaledBits, rnd);
            if (rnd.nextBoolean()) unscaled = unscaled.negate();
            int scale = rnd.nextInt(45) - 5; // scales from -5 .. 39, mixed
            xs.add(new BigDecimal(unscaled, scale));
        }
        return xs;
    }

    // ---- main -----------------------------------------------------------------------------------

    public static void main(String[] args) {
        Random rnd = new Random(0x5245_4C4CL); // "RELL", fixed seed for determinism.

        // big_integer: cross a representative subset to keep the corpus bounded but thorough.
        List<BigInteger> bi = bigintOperands(rnd);
        // Full cross-product would be large; cap b at a curated subset for the binary ops, but always
        // include every operand as 'a' to exercise unary ops and a wide spread of left operands.
        List<BigInteger> biB = new ArrayList<>();
        for (int i = 0; i < bi.size(); i += 2) biB.add(bi.get(i)); // every other operand as right side
        // Ensure key right-hand operands are present (zero, +-1, +-10, boundaries).
        biB.add(BigInteger.ZERO);
        biB.add(BigInteger.ONE);
        biB.add(BigInteger.valueOf(-1));
        biB.add(BigInteger.TEN);
        biB.add(BigInteger.valueOf(7));
        biB.add(BigInteger.valueOf(-7));

        for (BigInteger a : bi) {
            for (BigInteger b : biB) {
                biBin(a, b);
            }
            biSqrt(a);
        }

        // bipow: curated (base, exp) pairs incl. overflow and edge exponents.
        long[] exps = {0, 1, 2, 3, 5, 7, 10, 64, 1000, 131071, 131072, 1000000, -1};
        BigInteger[] powBases = {
                BigInteger.ZERO, BigInteger.ONE, BigInteger.valueOf(-1), BigInteger.TWO,
                BigInteger.valueOf(-2), BigInteger.TEN, BigInteger.valueOf(-10),
                BigInteger.valueOf(3), BigInteger.valueOf(7), BigInteger.valueOf(123456789),
                BigInteger.ONE.shiftLeft(32), BigInteger.TEN.pow(100),
        };
        for (BigInteger base : powBases) {
            for (long exp : exps) {
                biPow(base, exp);
            }
        }

        // decimal: cross-product over a curated right-hand subset.
        List<BigDecimal> dec = decimalOperands(rnd);
        List<BigDecimal> decB = new ArrayList<>();
        for (int i = 0; i < dec.size(); i += 2) decB.add(dec.get(i));
        decB.add(BigDecimal.ZERO);
        decB.add(BigDecimal.ONE);
        decB.add(new BigDecimal("-1"));
        decB.add(new BigDecimal("3"));
        decB.add(new BigDecimal("7"));
        decB.add(new BigDecimal("0.1"));
        decB.add(new BigDecimal("2.5"));

        for (BigDecimal a : dec) {
            for (BigDecimal b : decB) {
                decBin(a, b);
            }
        }

        StringBuilder sb = new StringBuilder(LINES.size() * 32);
        for (String line : LINES) {
            sb.append(line).append('\n');
        }
        System.out.print(sb);
        System.out.flush();
    }

    private GenNumericsCorpus() {}
}
