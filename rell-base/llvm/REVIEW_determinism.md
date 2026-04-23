# Determinism review — LLVM backend intrinsic / lowering fast paths

Adversarial bit-exactness audit of the native fast paths against the JVM interpreter
(`rt_ops.kt`, `Lib_DecimalMath`, `Lib_BigIntegerMath`, `java.math.*`, `java.lang.String`,
`Rt_Comparator`). Scope: `intrinsics_integer.cpp`, `intrinsics_decimal.cpp`,
`intrinsics_biginteger.cpp`, `intrinsics_text.cpp`, `intrinsics_bytearray.cpp`, `value.cpp`,
`lower_ops.cpp`.

Consensus rule applied throughout: when a native path cannot be PROVEN bit-exact, it must
soft-fail or route to JNI. Findings are ordered by severity.

Ground-truth confirmed from the tree:
- `rt_ops.kt:24-35` integer `+ - *` use `Math.{add,subtract,multiply}Exact` (throw on overflow);
  `/` and `%` are plain Kotlin `a / b` / `a % b` (div0 throws; `Long.MIN/-1` wraps, no throw).
- `rt_ops.kt:109-119` `compareByCmpType`: Boolean→`Boolean.compareTo`, Integer/Rowid→`Long.compareTo`,
  Enum→`rrAttr.value.compareTo` (an `Int` ordinal on a heap `Rt_RR_EnumValue`).
- `value.cpp from_jvm` has inline cases ONLY for null/unit/boolean/int/rowid/bigint-long;
  EVERYTHING else (incl. **enum**, decimal) becomes a `HANDLE` whose `payload.i64` is a `jobject`.

---

## CRITICAL

### C1 — Enum equality / comparison does icmp on a jobject pointer
**Files:** `lower_ops.cpp:86-128` (`lowerComparison`, `CmpType_ENUM`), `lower_ops.cpp:142-193`
(`lowerEquality` + `isI64EqualitySafeType`); root cause in `value.cpp:333-405` (`from_jvm`).

`from_jvm` has **no enum case** — `Rt_RR_EnumValue` falls through to `arena.adopt() → HANDLE`,
so an enum value's `payload.i64` is a **JNI global-ref pointer**, not its ordinal. Yet:
- `lowerComparison` accepts `CmpType_ENUM` (line 90) and runs `CreateICmpSLT/…` on `payloadI64`.
- `isI64EqualitySafeType` returns `true` for `type_as_EnumType()` (line 147), so `lowerEquality`
  runs `CreateICmpEQ` on `payloadI64`.

Both compare raw pointers. Divergences vs. JVM:
- `e1 == e2` for two equal enums materialised as distinct handles → native `false`, JVM `true`.
- Ordering follows allocation-address order, not ordinal — **non-deterministic across runs/nodes**.
This is a direct consensus split.

**Required fix (pick one, default to the safe one):**
- (a) Remove `CmpType_ENUM` from the inline set in `lowerComparison` (soft-fail it alongside
  TEXT/BYTE_ARRAY), AND remove the `EnumType` branch from `isI64EqualitySafeType` so enum EQ/NE
  soft-fails. This is the correct floor until enums have an inline ordinal representation.
- (b) Only if a later change adds an inline enum tag carrying the `Int` ordinal in `payload.i64`
  (and `from_jvm` populates it): then icmp is valid — but not before. Until then, (a).

---

### C2 — `intrinsicInteger` ADD/SUB/MUL/DIV/MOD return a NONE-poison value that `lower_ops` never converts to a slow path → wrong result silently used
**Files:** `intrinsics_integer.cpp:89-159` (poison via `CreateSelect(ovf, poison, inlineVal)`),
consumer `lower_ops.cpp:263-303` (`lowerIntegerArith`/`lowerDecimalArith`).

The intrinsic encodes overflow / div0 / `MIN÷-1` by selecting a `RellTag::NONE` poison value at
runtime, with the documented contract that "the function-level guard (lower_ops.cpp) turns a
NONE-tagged result into the rell_sysfn_call slow path." **That guard does not exist.**
`lowerIntegerArith` only inspects the compile-time `escaped` bool (which the integer intrinsic
never sets for these ops) and otherwise returns `res` verbatim. The NONE poison then flows on as
if it were a normal value:
- It is later `to_jvm`'d → `throwRuntime("to_jvm: NONE/poison RellValue cannot be reboxed")`
  (`value.cpp:467-471`) — i.e. a `RuntimeException`, NOT the `Rt_Exception("expr:/:div0:…")` /
  overflow `Rt_Exception` the interpreter raises. Wrong error class, wrong code string → consensus
  divergence on the *error path* (errors are consensus-observable in Rell).
- If the poison is instead consumed by another inline op (e.g. `(a*b)+c`), `unpackPayloadI64`
  reads `0` and the surrounding arithmetic proceeds on a bogus `0` → **silent wrong result**.

So for `a+b` etc. that overflow, or `a/0`, the JIT path does not reproduce the JVM behaviour.

**Required fix:** the overflow/div0 branch must reach the *actual* JVM op, not a poison sentinel.
Two acceptable shapes:
- Emit a real control-flow guard in `lower_ops.cpp`: on the `ovf`/`unsafe` i1, branch to a block
  that calls `rell_sysfn_call` with the integer-operator SysFnId and the boxed operands (this needs
  the operator's interned sysfn id threaded in — same gap decimal/bigint cite), then phi the
  results. OR
- Until that wiring exists, make integer overflow/div0 **escape** the whole op the way decimal/
  bigint do: have `intrinsicInteger` set `*escaped = true` for the ops whose safety it cannot prove
  statically (i.e. all of ADD/SUB/MUL/DIV/MOD on runtime operands), and let `lowerIntegerArith`
  soft-fail. That regresses the inline integer win but is the only *correct* posture until the
  runtime slow-path branch is wired. **Do not ship the poison-select as-is.**

(Same latent defect in `emitIntegerUnaryMinus`, `emitIntegerAbs`: their NONE poison has no
consumer-side guard either.)

---

## HIGH

### H1 — `lowerEquality` reads operand types only off `VarExpr`; non-VarExpr operands silently bypass the safety gate
**File:** `lower_ops.cpp:169-180`.

`lt`/`rt` are taken from `expr_as_VarExpr()->type()` and are `nullptr` for any non-VarExpr operand
(constants, nested calls, member reads). `isI64EqualitySafeType(nullptr)` returns `false`, so today
this *soft-fails* — which is safe. The risk is latent but real: the comment claims "every Expr
variant we can inline carries a type()", implying a future edit will broaden the type read. If that
edit reads a type that is decimal/big_integer/nullable/handle while still routing to the i64 icmp,
equality becomes wrong (`1.5 == 1.50`, structural equals, etc.). **Required fix:** before widening,
gate on the *result/operand type carried by the BinaryExpr op itself*, and explicitly exclude
DECIMAL, BIG_INTEGER, nullable, and any non-primitive; keep the current conservative soft-fail as
the default. Add a comment that DEC_LONG payload equality ≠ value equality.

### H2 — UNIT included in the i64-equality-safe set; doc claims NULL/UNIT but code covers only UNIT
**File:** `lower_ops.cpp:149-157` (returns true for `PrimitiveTypeKind_UNIT`); comment block
`131-140` claims "plus the NULL/UNIT singletons."

`unit == unit` is fine (both `rv_unit()`, payload 0, icmp 0==0 → true). But: (1) the only way two
operands are *statically* unit and reach here is degenerate; harmless but dead. (2) NULL is **not**
handled — a `null == null` / `x == null` is a Nullable-typed operand, `isI64EqualitySafeType`
returns false → soft-fail, which is correct, but the comment is misleading and invites someone to
"fix" it by adding NULL to the inline set. NULL inline-eq would be `icmp` on tag-NULL payloads
(both 0) → accidentally correct for `null==null` but WRONG for `someInt == null` where the int
operand isn't even NULL-tagged. **Required fix:** delete the NULL claim from the comment (or
implement null-eq as a tag comparison, not a payload icmp); drop UNIT from the set or annotate it
as dead. Keep payload-icmp strictly to INTEGER/ROWID/BOOLEAN.

### H3 — Comparison/equality always pack a fresh BOOLEAN but never assert operand tags
**File:** `lower_ops.cpp:106-127`, `182-192`.

`lowerComparison`/`lowerEquality` unpack `payloadI64` of whatever `lowerExpr` returned and trust the
static type gate to guarantee the tag. For INTEGER/ROWID/BOOLEAN that is sound. The fragility:
there is no defensive check that the lowered operand's runtime tag actually matches (a mis-lowered
sub-expression that yields a HANDLE-tagged value would be silently compared as a pointer, cf. C1).
**Required fix (defensive, cheap):** in debug builds assert the unpacked tag is the expected inline
tag, or document that `lowerExpr` MUST preserve the static type's inline tag and treat any mismatch
as a lowering bug to soft-fail. This is the guardrail that would have caught C1 at emit time.

---

## MEDIUM

### M1 — Decimal `MINUS_DECIMAL` lowered as `0 - x` with a scale-0 zero relies on the (disabled) const-scale path; today escapes → soft-fail, but the algebra is a future hazard
**Files:** `lower_ops.cpp:458-472`, `intrinsics_decimal.cpp:332-402`.

Currently safe because `intrinsicDecimal` all-escapes and `lowerDecimalArith` soft-fails on escape.
But when the fast path is enabled, `0` is passed as `(mantissa=0, scale=0)` and `x` keeps its scale.
`emitDecLongAddSub` aligns the smaller scale up by `10^delta`; for `0 - x` the zero's mantissa is 0
so alignment is exact, and result scale = `x`'s scale. Negation of a DEC_LONG is value-exact.
**However** the JVM negates via `Lib_DecimalMath.subtract(ZERO, x)` which then runs the canonical
`scale()` normalisation; if `x` is already canonical the result is canonical, but a hand-built
`(−mantissa, scale)` must match the JVM's stripped/normalised form bit-for-bit. **Required fix
(before enabling the fast path):** verify that negation preserves the *exact* DEC_LONG normal form
the interpreter would produce (trailing-zero stripping, `−0` handling), or route MINUS_DECIMAL via
the unary JVM path. Leave a test asserting `(-x)` DEC_LONG == JVM rebox.

### M2 — `emitDecLongMul` HALF_UP rounding is "away from zero", which matches the JVM only because Rell decimal uses HALF_UP — assert this constant
**File:** `intrinsics_decimal.cpp:241-278`.

The rescale rounds when `|rem|*2 >= divisor`, incrementing the magnitude by `±1` toward away-from-
zero. This is `RoundingMode.HALF_UP` (ties away from zero), which is what `Lib_DecimalMath` uses.
It is NOT `HALF_EVEN`/DECIMAL128. The code is correct *iff* the consensus rounding mode is HALF_UP.
The file hard-codes `kDecimalFracDigits=20` and the mode implicitly. **Required fix:** add a
compile-time cross-check (or at minimum a comment citing the exact `Lib_DecimalMath` rounding-mode
constant) so a JVM-side change to the rounding mode can't silently desync this emitter. The half-up
tie at exactly `|rem|*2 == divisor` is the divergence point vs. HALF_EVEN — call it out. (Path is
gated off today, so MEDIUM not HIGH.)

### M3 — `to_jvm` DEC_LONG → `BigDecimal.valueOf(mantissa, scale)` assumes the DEC_LONG normal form equals the interpreter's; negative/large scale boundary
**File:** `value.cpp:447-460`.

`BigDecimal.valueOf(long unscaled, int scale)` builds `unscaled × 10^−scale`, then
`Rt_DecimalValue.get` runs `Lib_DecimalMath.scale`. This is bit-exact **only if** every DEC_LONG in
circulation carries the same (mantissa, scale) the JVM would. Since DEC_LONGs are only produced by
JVM-validated constants or (gated-off) intrinsics, this holds today. Risk arises if an intrinsic
ever emits a non-canonical `(mantissa, scale)` (e.g. mantissa with trailing zeros, or scale > 18
slipping through). **Required fix:** keep the invariant "DEC_LONG is always the JVM's stripped
normal form" as an explicit, tested precondition of every emitter that packs DEC_LONG; the
`packDecLong` helpers do not enforce it.

### M4 — `intrinsicInteger` default branch soft-fails on EQ/NE but those never route here — dead but masks mis-dispatch
**File:** `intrinsics_integer.cpp:150-158`.

Fine as written (soft-fail, not wrong IR). Noted only because the same enum/decimal mis-dispatch
that caused C1 would here be caught by a soft-fail, whereas in `lowerComparison` it is NOT. Argues
for the H3 defensive tag assert.

---

## LOW / CONFIRMED-SAFE (documented for completeness)

- **L1 (safe):** Integer `Long.MIN / -1` and `Long.MIN % -1` — native routes both to JVM (the
  `unsafe` predicate folds `MIN/-1` into the slow path via the sanitised divisor). JVM yields
  `Long.MIN` / `0L` (Kotlin wrap, no throw). The slow path reproduces `a/b` / `a%b` exactly. The
  sanitised-divisor `CreateSDiv(a, 1)` avoids LLVM UB. **Correct** — contingent on C2's slow path
  actually existing; with the current poison-only path this is broken (see C2).
- **L2 (safe):** Integer comparison Boolean — `Boolean.compareTo` gives false<true; payload {0,1}
  signed icmp matches. Rowid — `Long.compareTo`, payload signed icmp matches (rowid≥0 invariant
  makes signed == unsigned here anyway). **Correct.**
- **L3 (safe):** `intrinsicText` / `intrinsicByteArray` — uniform escape to JNI. No native String
  semantics, so no UTF-16-vs-codepoint, no error-code-string drift, no surrogate-pair hazard.
  byte_array equality/ordering correctly noted as JVM-only (handle identity ≠ value identity).
  **Correct floor.**
- **L4 (safe):** `intrinsicBigInteger` — all-escape; i64-overflow-into-wide-bigint correctly
  deferred to the JVM (which boxes the wide value as a HANDLE). div0 message embeds full operands,
  reproduced JVM-side. **Correct.**
- **L5 (safe):** `intrinsicDecimal` add/sub/mul/div/mod — all-escape today; the gated const-scale
  emitters are not live. Provided the gate stays off until C2-style runtime slow-path wiring AND
  M1/M2/M3 are closed, this is the correct floor.
- **L6 (safe):** `from_jvm` BigInteger long-fit gate uses `bitLength() < 64` — exactly
  `Long.MIN..Long.MAX` (Long.MIN has bitLength 63, included; `±2^63` boundary excluded). `longValue`
  is the JVM's own. **Correct.**
- **L7 (safe):** AND/OR short-circuit — branch structure matches JVM short-circuit side-effect
  ordering; phi of the short-circuit constant vs. evaluated-right bit is correct.
- **L8 (note):** `from_jvm` decimal always → HANDLE (no C++ scale cracking). Correct and the reason
  decimal comparison MUST soft-fail (it does, `lower_ops.cpp:92-100`). Symmetric to the enum gap in
  C1 — the difference is decimal is *excluded* from the inline cmp set while enum is *wrongly
  included*.

---

## Priority summary

| Sev | Location | Concern | Required fix |
|-----|----------|---------|--------------|
| CRIT | `lower_ops.cpp:90,147` + `value.cpp from_jvm` | enum EQ/NE/`<` icmp on jobject pointer | drop ENUM from inline cmp set AND from `isI64EqualitySafeType` |
| CRIT | `intrinsics_integer.cpp:89-178` + `lower_ops.cpp:263-303` | overflow/div0 NONE-poison has no slow-path consumer → wrong error class / silent 0 | wire a real `rell_sysfn_call` overflow branch, or make integer ops `*escaped`+soft-fail until then |
| HIGH | `lower_ops.cpp:169-180` | eq type-gate reads only VarExpr; broadening it would admit decimal/handle | gate on op operand type, exclude DECIMAL/BIG_INTEGER/nullable/non-primitive |
| HIGH | `lower_ops.cpp:149,131-140` | NULL/UNIT inline-eq claim mismatched/unsafe | remove NULL claim, drop UNIT, payload-icmp = INTEGER/ROWID/BOOLEAN only |
| HIGH | `lower_ops.cpp:106-127,182-192` | no operand-tag assertion behind the static gate (would have caught C1) | debug-assert unpacked tag matches expected inline tag |
| MED | `lower_ops.cpp:458-472` | MINUS_DECIMAL `0-x` normal-form exactness (when fast path enabled) | verify DEC_LONG negation == JVM rebox, or route unary to JVM |
| MED | `intrinsics_decimal.cpp:241-278` | mul rescale assumes HALF_UP; tie point vs HALF_EVEN | assert/cite `Lib_DecimalMath` rounding-mode constant |
| MED | `value.cpp:447-460` | DEC_LONG→BigDecimal assumes canonical normal form | enforce/test "DEC_LONG is always JVM stripped normal form" invariant |

**Bottom line:** two CRITICAL consensus splits must be fixed before any JIT path that touches enums
or integer overflow/div0 is enabled. C1 produces wrong/non-deterministic results today for enum
equality and ordering; C2 means the integer "checked" fast path does not actually reproduce the
JVM's overflow/div0 `Rt_Exception` — it raises a `RuntimeException` or silently computes on `0`. The
text/byte_array/bigint/decimal overlays are correctly conservative (all-escape) and are safe as the
floor.
