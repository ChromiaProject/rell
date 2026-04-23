// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.

# Rell LLVM backend — native runtime ABI

Companion to `rell_runtime.h`. Documents the native value representation, the per-call arena,
the native<->JVM marshalling and call protocol, and the lowering contract. The header is the
normative source; this file is the prose.

Determinism is non-negotiable (consensus code). The single overriding rule: **the JIT must
never produce a result that differs bit-for-bit from `Rt_InterpreterImpl`.** Every escape
hatch below exists to honour that — when in doubt, route to the JVM.

---

## 1. Value representation — `RellValue` (16-byte tagged POD)

LLVM struct type: `%RellValue = type { i8, i32, i64 }` (`rellValueLlvmType()`), matching the
C++ `struct RellValue { RellTag tag; int32_t scale; union { int64_t i64; jobject handle; } }`.
Trivially copyable, passed by value through the LLVM calling convention.

| tag | scale | payload | meaning / invariant |
|---|---|---|---|
| `NONE` (0) | 0 | 0 | poison; also the "exception pending — abort" trampoline sentinel |
| `NULL_` (1) | 0 | — | Rell `null` = `Rt_NullValue` singleton (NOT a C++ nullptr) |
| `UNIT` (2) | 0 | — | `Rt_UnitValue` singleton |
| `BOOLEAN` (3) | 0 | i64 ∈ {0,1} | never any other bit pattern (intrinsics assume this) |
| `INTEGER` (4) | 0 | i64 | full range |
| `ROWID` (5) | 0 | i64 ≥ 0 | mirrors `Rt_RowidValue` init check; negative ⇒ caller bug |
| `DEC_LONG` (6) | natural stripped scale `s` | mantissa `m` | value = `m / 10^s`; intrinsics act only when `s ≤ 18` |
| `BIGINT_LONG` (7) | 0 | i64 | value is exactly i64; `|v| > Long.MAX` ⇒ HANDLE |
| `HANDLE` (8) | 0 | non-null jobject global ref | arena-owned ref to any `Rt_Value` |

`HANDLE` is the universal escape hatch. text / byte_array / json / gtv / collections / map /
set / struct / entity / object / tuple / virtual-* / range / function values are **always**
HANDLE. A decimal/bigint outside its inline envelope is **also** HANDLE — never a truncated
inline.

### Canonical forms (consensus-critical)

- **No silent truncation.** A decimal/bigint exceeding the envelope MUST be HANDLE.
- **Stripped-scale `DEC_LONG`.** Mantissas are produced via the same strip-trailing-zeros path
  as `Tf_LongScaleDecimal` (`1.5` → `(15, 1)`, not padded to scale 20). Native `==` on
  `(scale, mantissa)` then matches JVM `BigDecimal.equals` for same-leaf compares. Cross-leaf
  or scale-mismatched compares MUST fall back to the JVM.
- **`BOOLEAN` is `{0,1}` only.** **`ROWID ≥ 0`.**

### Envelope predicates

`dec_long_intrinsic_safe(scale)` = `0 ≤ scale ≤ 18` (the `kDecLongMaxScale` POW10 bound).
The C++ side does NOT reimplement `Lib_DecimalMath.scale()` / `Lib_BigIntegerMath` bounds; the
inline path is only entered for (a) JVM-validated constants/values handed across as
`(mantissa, scale)` / i64, or (b) results of pure intrinsics that stayed in-envelope. Rell
decimals are NOT `DECIMAL128`: the JVM envelope is `DECIMAL_INT_DIGITS = 131072` integer digits
with fraction held to `DECIMAL_FRAC_DIGITS = 20`. BigInteger inline envelope is "fits i64".

---

## 2. Per-call arena — `RellArena`

A bump owner of every JNI global ref created during one native function invocation, RAII-scoped
to the JNI entry trampoline that JIT'd code returns through.

- `adopt(jobject)` / `track(jobject)` → `NewGlobalRef` + record; pushed to the bump list.
- Destructor sweeps the list with `DeleteGlobalRef`, **tolerating a pending JVM exception**
  (never early-returns — always releases to avoid leaks on the error path).
- Inline-tag `RellValue`s carry no arena resources; only HANDLEs do.
- A `RellValue{HANDLE}` must never outlive the arena that created its global ref. JIT'd code
  does not store handles past the current call.

---

## 3. Marshalling protocol — native ⇄ JVM

Backed by a cached table (`ensureRellRefsInitialized`) of global `jclass` refs, primitive
`jfieldID`s, getter/factory `jmethodID`s, and `Companion` object refs, populated once under a
`std::once_flag` against the cached `g_vm` JNIEnv. A null lookup aborts hard (`throwRuntime`) —
it is a build/ABI breakage, never a soft-fail.

### 3a. Unwrap — `from_jvm(env, arena, jobject) -> RellValue`

Class-identity dispatch, in order:

1. null jobject where a value is required ⇒ **hard error** (`throwIllegalArgument`). Rell `null`
   is the `Rt_NullValue` singleton, not a C++ nullptr.
2. `Rt_NullValue.INSTANCE` ⇒ `NULL_`; `Rt_UnitValue.INSTANCE` ⇒ `UNIT`.
3. `Rt_IntValue` ⇒ read `value` field (`J`) ⇒ `INTEGER`.
4. `Rt_BooleanValue` ⇒ read `value` (`Z`) ⇒ `BOOLEAN`.
5. `Rt_RowidValue` ⇒ read `value` (`J`) ⇒ `ROWID` (≥ 0).
6. `Rt_BigIntegerValue` ⇒ if fits i64 (via `BigInteger.bitLength`) ⇒ `BIGINT_LONG`, else
   `arena.adopt` ⇒ HANDLE.
7. `Rt_DecimalValue` ⇒ if `Tf_LongScaleDecimal.tryFrom` accepts ⇒ `DEC_LONG (mantissa, scale)`,
   else `arena.adopt` ⇒ HANDLE.
8. anything else ⇒ `arena.adopt` ⇒ HANDLE.

Primitive reads use cached `jfieldID`s (real backing fields of `@JvmRecord`/`data class` vals).
Decimal/bigint envelope checks go through cached `jmethodID`s on JVM helpers so the exact JVM
semantics are reused, never re-derived in C++.

### 3b. Rebox — `to_jvm(env, arena, RellValue) -> jobject` (local ref)

| tag | factory |
|---|---|
| `NULL_` / `UNIT` | static `INSTANCE` field |
| `BOOLEAN` | `Rt_BooleanValue.get(Z)` (or cached `TRUE`/`FALSE`) |
| `INTEGER` | `Rt_IntValue.get(J)` (`@JvmStatic`) |
| `ROWID` | `Rt_RowidValue.Companion.get(J)` |
| `BIGINT_LONG` | `Rt_BigIntegerValue.Companion.get(J)` |
| `DEC_LONG` | long-scale decimal ctor `(mantissa, scale)` — preferred; skips BigDecimal materialisation |
| `HANDLE` | the arena-owned global ref **is** the `Rt_Value`; return directly |

Inline factories can throw `Rt_Exception` (decimal overflow, negative rowid). The caller MUST
`ExceptionCheck` after — see §5.

---

## 4. Universal JNI stdlib caller (correctness floor)

`rell_sysfn_call(env, sysfnId, args, nargs, ctx)`:

1. Push a JNI local frame sized `nargs + slack`.
2. Rebox each `RellValue` arg to a `jobject` via `to_jvm` into an `Object[]`.
3. Call `Llvm_SysBridge.dispatch(sysfnId, args, frameHandle)` (descriptor
   `(I[Ljava/lang/Object;J)Ljava/lang/Object;`), which indexes a dense `Array<R_SysFunction?>`
   built from `Rt_StdlibEnv.sysFunctions` and invokes `R_SysFunction.call(ctx, args)` — the SAM
   every arity funnels through. `Rt_CallContext` comes from the frame.
4. `ExceptionCheck`; on pending exception return `rv_none()` (left pending).
5. Unwrap the single result via `from_jvm`, `adopt`-ing an escaping result into `ctx->arena`.

`SysFnId` is a dense int32 interned at JIT time by a deterministic FlatBuffer walk collecting
every `FnTarget_{SysGlobal,SysMember,NativeUser}.fn_name()`,
`MemberCalculator_SysFunction.fn_name()`, and `SysQueryBody.fn_name()`. `SysFnTable` holds the
name↔id map; its id-ordered `names()` is read JVM-side to build the dense array, so the two id
spaces agree. `kSysFnIdNone` (-1) ⇒ not interned ⇒ soft-fail, never call.

This is the floor that makes 100% of the stdlib reachable: rebox args → JVM call → unwrap
result, with the arena owning every transient global ref.

---

## 5. SQL back-call

- `DbAtExpr` / `ColAtExpr` ⇒ `rell_db_eval_expr(env, nodeId, ctx)` ⇒
  `Rt_Interpreter.evaluateExpr(expr, frame)` (already on the interface; reached via
  `Llvm_Backend`).
- `UpdateStatement` / `DeleteStatement` ⇒ `rell_db_exec_stmt(env, nodeId, ctx)` ⇒
  `Rt_InterpreterImpl.executeStmt(stmt, frame)` (internal; reached via `Llvm_Backend.delegate`).
  Returns an int32 status (`Rt_StatementResult?` encoded; 0 == NORMAL; negative ⇒ pending
  exception).

Nodes are interned to dense `DbNodeId`s over a fixed deterministic RR-tree walk mirrored on both
sides; native never builds SQL itself. `kDbNodeIdNone` (-1) ⇒ soft-fail.

The live frame is carried as `RellCallCtx.frameHandle` — an opaque `jlong` handle to a JVM
`Llvm_CallEnv` wrapping the live `Rt_Frame`, threaded as a hidden trailing param into every
JIT'd function and every back-call. Native code treats it as opaque; the exact JVM-side encoding
is the Wiring phase's choice (the recommendation is the `jlong` handle, avoiding a second global
ref).

---

## 6. Exception / error propagation

After **every** JVM back-call (universal caller, db eval/exec, any throwing rebox/unwrap):
`ExceptionCheck`. On a pending exception: **do not clear it.** Stop native execution, let the
arena destructor run (`DeleteGlobalRef` is exception-safe), and return the poison sentinel
`rv_none()` (statements: a negative status). The JIT trampoline boundary turns the sentinel into
"exception pending — abort"; the JVM then sees the thrown `Rt_Exception`, identical to
interpreter behaviour and bit-exact for consensus.

**Soft-fail vs. exception.** "Outside the JIT envelope" is NOT an exception — it is a HANDLE
(route the value through the JVM) or an `EmitContext::fail()` function-level soft-fail
(`compileFunctionByIndex` returns 0 → the interpreter runs the whole function). Only genuine
`Rt_Exception`s and hard ABI faults (`throwRuntime`/`throwIllegalArgument`: null cached ID,
malformed handle, out-of-range index) raise across JNI.

---

## 7. Lowering contract — `EmitContext`

`EmitContext` bundles the emit state shared by `lower_expr` / `lower_ops` / `lower_stmt` /
`lower_call` and the `intrinsics_*` overlays:

- `ctx()` / `module()` / `builder()` — LLVM emit handles; `app()` — the FlatBuffer root for
  index lookups; `valueType()` — the `{ i8, i32, i64 }` runtime-value SSA type.
- `slots()` / `slotFor(VarPtr)` — the param/local slot map keyed on `{block_uid, offset}`
  (`VarPtrKey`/`VarPtrKeyHash`, identical to jni_bridge's).
- `sysFns()` / `dbNodes()` — the interning tables.
- `packInline` / `packInteger` / `unpackPayloadI64` / `unpackTag` / `unpackScale` — pure IR
  shape ops between a host i64 fast path and a runtime-value SSA. They do **not** validate the
  envelope; the caller must have proven the value stays inline before computing in i64 space.
- `fail(msg)` / `failExpr(msg)` / `failed()` — the soft-fail signal (mirrors jni_bridge's
  `Lowerer::fail`). The first message wins; a failed context makes the whole function soft-fail.
  **Never emit IR after `fail()`.**

### Runtime-value SSA

A Rell value flowing through JIT'd code is an `llvm::Value*` of `valueType()`. Pure intrinsics
that have proven their operands are `INTEGER`/`BOOLEAN`/`ROWID`/`DEC_LONG`/`BIGINT_LONG` extract
the i64 payload, compute in i64 space, and re-pack — staying inside the inline envelope. The
moment an operand or result might escape, the lowering routes through a runtime call
(`rell_sysfn_call`, or box to HANDLE) or `fail()`s.

### Intrinsic overlays

Each `intrinsic*` entry takes already-unpacked operands (proven in-envelope by the caller) and
returns the result runtime value, or `nullptr` with `*escaped = true` when operands/result fall
outside the safe envelope — the caller then emits the `rell_sysfn_call` slow path. `*escaped`
true and a non-null return are mutually exclusive.

---

## 8. Routing summary (what leaves inline IR)

- **Inline-capable:** `VarExpr` (int/bool/rowid param), `ConstantValueExpr`
  (Bool/Int/Rowid/Enum/Null/Unit + long-fit Decimal/BigInteger), `BinaryExpr`/`UnaryExpr` on
  integer (+ decimal/bigint/text/byte_array/math intrinsics with the envelope check), `IfExpr`,
  `CmpInfo` comparisons on inline types, simple control flow
  (Block/Return/If/While/For/Assign/Var).
- **JNI stdlib caller:** `FnTarget_SysGlobal`/`SysMember`/`NativeUser`,
  `MemberCalculator_SysFunction`, `SysQueryBody`, anything stdlib-reachable by name.
- **SQL / JNI interpreter back-call:** every `DbExpr` variant, `DbAtExpr`, `ColAtExpr`,
  `UpdateStatement`, `DeleteStatement`, `MemberCalculator_DataAttribute`.
- **Whole-function soft-fail:** `ErrorExpr`, `ErrorType`, abstract/test functions, params with
  `default_expr`, and anything not bit-exactly reproducible.

---

## 9. Native source files

`value.cpp` · `stdlib_bridge.cpp` · `sql_bridge.cpp` · `intrinsics_integer.cpp` ·
`intrinsics_decimal.cpp` · `intrinsics_biginteger.cpp` · `intrinsics_text.cpp` ·
`intrinsics_bytearray.cpp` · `intrinsics_math.cpp` · `lower_expr.cpp` · `lower_ops.cpp` ·
`lower_stmt.cpp` · `lower_call.cpp` — all include `rell_runtime.h`. `jni_bridge.cpp` (the ORC
JIT entry points) is integrated separately and is not edited here.
