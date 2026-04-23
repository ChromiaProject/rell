# Adversarial bit-exactness review — rell::num C++ numerics

Scope: `rell-base/llvm/src/main/cpp/rell_bigint_*.cpp`, `rell_bigdec_*.cpp`,
`rell_decimal_math.cpp` vs `java.math.BigInteger` / `java.math.BigDecimal` (OpenJDK 21,
jdk-21+35) and the Rell consensus envelope.

## Method

Built a differential test harness (`/tmp/numreview/`): the real `.cpp` files were linked
into a stdin-driven C++ driver and run head-to-head against a Java reference
(`java.math` + a faithful port of `Lib_DecimalMath.scale` / `removeTrailingZeros`).
Corpus = ~57,000 cases: random + adversarial edge cases for every operation, plus the
consensus boundary cases (intDigits exactly 131072, the −20 underflow collapse, rounding
carry across 10^131072, every RoundingMode tie at exactly .5, two's-complement byte
round-trips, max-normalization-shift divisors `2^(32k)`, qhat add-back triggers
`divisor*q − 1`).

Result: **BigInteger arithmetic is bit-exact (0 mismatches over ~35k cases). BigDecimal
arithmetic is bit-exact except `remainder`'s scale (value always correct).** The decimal
normalize envelope is bit-exact at every consensus boundary. The blockers below are
build/link defects, not arithmetic divergences.

---

## Findings (prioritized)

### S1 — BLOCKER — `RellBigInt::multiply` is declared but never defined
`rell_bigint.h:173` declares `RellBigInt multiply(const RellBigInt&) const`. The header
comment says it lives in `rell_bigint_core.cpp` ("calls mulMag"), but **no translation
unit defines it.** Only the private helper `mulMag` (rell_bigint_muldiv.cpp:355) exists.
Every consumer (`pow`, `sqrt`, `bigTenToThe`, `bigMultiplyPowerTen`, the whole BigDecimal
layer) calls `multiply`, so the library does not link at all:

```
Undefined symbols: "rell::num::RellBigInt::multiply(rell::num::RellBigInt const&) const"
```

Required fix: add the public method (it is one block — sign algebra + `mulMag`), e.g. in
`rell_bigint_core.cpp` next to `add`/`subtract`:

```cpp
RellBigInt RellBigInt::multiply(const RellBigInt &val) const {
    if (signum_ == 0 || val.signum_ == 0) return RellBigInt();
    std::vector<uint32_t> z = mulMag(mag_, val.mag_);
    return RellBigInt(std::move(z), signum_ * val.signum_);
}
```
(Verified: with exactly this body, all 35k+ BigInteger and dependent BigDecimal tests pass.)

### S2 — BLOCKER — duplicate (ODR-violating) definitions across translation units
Five member functions are defined in **two** `.cpp` files each, so the library fails to
link with `duplicate symbol`:

| symbol | defined in | also defined in |
|---|---|---|
| `RellBigInt::bitLength`        | rell_bigint_core.cpp:560 | rell_bigint_extra.cpp:62 |
| `RellBigInt::longValue`        | rell_bigint_core.cpp:287 | rell_bigint_extra.cpp:108 |
| `RellBigInt::longValueExact`   | rell_bigint_core.cpp:327 | rell_bigint_extra.cpp:128 |
| `RellBigDec::valueOf(int64,int32)` | rell_bigdec_core.cpp:28 | rell_bigdec_str.cpp:173 |
| `RellBigDec::valueOf(int64)`       | rell_bigdec_core.cpp:32 | rell_bigdec_str.cpp:177 |

Required fix: keep ONE definition of each and delete the other. The header's per-file
ownership comments put `bitLength`/`longValue`/`longValueExact` in `extra` and `valueOf`
in `str` — but note the **two copies are not textually identical** (e.g. the
`rell_bigint_core.cpp` `longValueExact` guards `mag_.size() <= 2 && bitLength() <= 63`,
while the `extra.cpp` one guards `bitLength() <= 63` only). Both produce identical results
on the test corpus, but pick the canonical one deliberately rather than at random. (For
the review build I kept the `extra.cpp` copies; all tests passed.)

### S3 — MEDIUM — `RellBigDec::remainder` returns the wrong SCALE (value is correct)
`rell_bigdec_muldiv.cpp:258`. The implementation forces the integral quotient to scale 0
(`RellBigDec iqDec(std::move(iq), 0)`), then `iqDec.multiply(divisor)` lands at scale
`0 + divisor.scale`, and the final `subtract` yields scale `max(thisScale,
divisorScale)`. Java's `BigDecimal.remainder` is `this.subtract(this
.divideToIntegralValue(divisor).multiply(divisor))`, and **`divideToIntegralValue` carries
its own preferred scale `this.scale − divisor.scale`** (clamped per Java's rules), which is
generally NOT 0. The result is a remainder with a different scale.

- 636/636 differential mismatches in the decimal corpus were `remainder`, and **every one
  is value-equal, scale-only** (e.g. `0.60 rem −44.0984`: CPP `6000 / 10^4`, Java
  `60 / 10^2` — same number, scale 4 vs 2; `4. rem 193845227597255.3344`: CPP scale 4,
  Java scale 0).
- This is a faithfulness divergence from `BigDecimal.remainder`.
- **Consensus impact: NONE as currently wired.** Rell feeds `%` operands that are already
  canonical scale-20, and `decimal_rem` runs the result through `decimal_scale_normalize`
  which re-pins to scale 20. Since the *value* is always identical, the normalized output
  matches the JVM in 100% of cases (verified: 0 normalized-result differences across the
  636 raw mismatches, and 0 raw mismatches when both operands are scale-20). It still
  diverges from `RellBigDec::remainder`'s stated contract ("faithful BigDecimal") and
  would bite any future caller that inspects the un-normalized scale.

Required fix: implement `divideToIntegralValue` with its real preferred-scale rule
(`preferredScale = saturate(this.scale − divisor.scale)`, with the OpenJDK adjustment when
the quotient's natural scale is below the preferred scale) and build `iqDec` at that scale
instead of 0, OR document the method as "value-faithful, scale-normalized-downstream" and
drop the "faithful to BigDecimal.remainder" claim. Given the Rell envelope, low urgency;
fix for contract honesty.

### S4 — LOW / robustness — `bigTenToThe` builds 10^n via `RellBigInt::pow` per call
`rell_bigdec_muldiv.cpp:37` computes `10^n = fromInt64(10).pow(n)` on every invocation
(precision(), every scale alignment, every setScale). Correct and bit-exact, but O(n)
multiplies each time with no cache; `decimal_scale_normalize` of a near-limit value can
call it with n up to ~131072. Not a correctness bug — flag only as the `// TODO(perf)` the
header already anticipates. No fix required for consensus.

### S5 — INFO — `RellBigDec::round`/`pow` intentionally absent
`rell_bigdec_muldiv.cpp:268-303` documents that `round(MathContext)` and decimal `pow`
are not implemented because the frozen header declares neither. Confirmed not on the Rell
decimal consensus path (Rell pins scale via divide/setScale; no decimal `**`). No action
unless a future op needs them; the sketched bodies in the comment look correct.

---

## What was verified bit-exact (no findings)

After patching S1/S2 locally:

- **BigInteger** add / subtract / multiply / divide / remainder over ~35k random + crafted
  cases (operands to ~300 digits): 0 mismatches.
- **Knuth-D division**: qhat estimate, skipCorrection, the (qhat,rhat) refinement loop,
  `mulsub` borrow, `divadd` add-back, and final un-normalization — exercised with
  `2^(32k)` divisors (maximal normalization shift, k≤40), all-ones divisors, and
  `divisor*q − 1` dividends (forces add-back). 0 mismatches.
- **remainder SIGN** follows the dividend (Java semantics): all sign combinations
  (`−7/3`, `7/−3`, `−7/−3`, large) correct.
- **pow** (incl. `0^0=1`, `−1^even/odd`, base = power of two), **sqrt** (floor, incl.
  200-digit operands and the {1,2,3}→1 cases), **bitLength** (incl. negatives and exact
  powers of two with the off-by-one), **two's-complement byte** `fromBytes`/`toBytes`
  round-trips (incl. sign-extension stripping, `Long.MIN`, 0): all bit-exact.
- **BigDecimal** add / subtract / multiply / divide(scale,mode) / setScale(scale,mode) for
  **all seven RoundingMode values** including HALF_UP/HALF_DOWN/HALF_EVEN ties at exactly
  .5 and negative values (`±0.5, ±1.5, ±2.5, ±0.125, 2.45/2.55, 12.345/12.355`, `1/3`,
  `5/2`, `−5/2`, etc.): 0 mismatches. HALF_EVEN to-even and CEILING/FLOOR sign direction
  confirmed.
- **precision**, **compareTo** (across differing scales, `2.0` vs `2.00`, `10` vs `1E1`),
  **toString** scientific-vs-plain threshold (adjusted exponent ≥ −6 boundary tested with
  `1E-6/1E-7/5E-6/5E-7/99E-7/...`), **toPlainString**, **stripTrailingZeros** (incl.
  negative-scale outcomes `600→6E2`): 0 mismatches.
- **decimal parse** (leading `.`, `+.`, `-.`, exponent forms, no-integer-part), **rell
  `removeTrailingZeros` toString**, and **`decimal_scale_normalize`**: 0 mismatches,
  including the consensus boundaries — intDigits exactly 131072 (allowed) vs 131073
  (overflow), the rounding carry `9…9.999…95` (131072 nines) crossing 10^131072 →
  overflow, the −20/−21 underflow → ZERO collapse, and ZERO's scale (0, not 20). Both
  sides throw overflow at the identical boundary.

## Reproduction

Harness preserved at `/tmp/numreview/` (`cpptest.cpp`, `Ref.java`, generated `*.txt`
corpora). To re-run, supply the S1 `multiply` body and de-duplicate the S2 symbols, then
link the seven `.cpp` files into `cpptest` and `diff` against `java Ref`.
