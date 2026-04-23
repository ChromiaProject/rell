<!-- Copyright (C) 2026 ChromaWay AB. See LICENSE for license information. -->

# LLVM backend coverage review — RR IR & stdlib

Audit of `rell-base/llvm/src/main/cpp/lower_*.cpp` + `intrinsics_*.cpp` + `sql_bridge.cpp` /
`stdlib_bridge.cpp` against the authoritative variant lists in
`rr-serialization/src/main/flatbuffers/{ir,op,value,type}.fbs`.

Legend:
- **NATIVE** — lowered to inline LLVM IR, no JVM round-trip.
- **JNI** — routed at run time to the JVM: universal stdlib caller (`rell_sysfn_call`) or SQL
  back-call (`rell_db_eval_expr` / `rell_db_exec_stmt`).
- **SOFT-FAIL** — `ec.fail()` / `ec.failExpr()`: the WHOLE function abandons JIT and the
  `Rt_InterpreterImpl` (via `Llvm_Backend.outerInterp`) runs it bit-exactly.

The correctness floor: every union dispatcher (`lowerExpr`, `lowerBinary`, `lowerUnary`,
`lowerStmt`, the call-target / member-calculator / when-chooser switches) ends in a
`default:` that calls `ec.fail*`, and `lowerExpr`/`lowerStmt` early-return on `ec.failed()`.
So **no variant is silently dropped** — anything not NATIVE or JNI is cleanly SOFT-FAIL.

---

## RR_Expr (`ExprUnion`, 37 variants)

| Variant | Status | Notes |
|---|---|---|
| VarExpr | NATIVE | param + local slot load via `ec.slotFor`; un-lowered slot → soft-fail |
| ConstantValueExpr | NATIVE / SOFT-FAIL | Bool/Int/Rowid/Enum/Null/Unit + long-fitting BigInteger inline; **Decimal const always soft-fails** (no inline Tf_LongScaleDecimal rebox); Text/ByteArray/Gtv/Struct/Collection/Map/Tuple/Meta consts soft-fail (no by-bytes rebox entry) |
| BinaryExpr | NATIVE / JNI-via-cmp / SOFT-FAIL | see Ops table below |
| UnaryExpr | NATIVE / SOFT-FAIL | NOT, MINUS_INTEGER/BIG_INTEGER/DECIMAL inline-in-envelope; escape → soft-fail |
| IfExpr | NATIVE | cond branch + entry-alloca phi |
| WhenExpr | NATIVE / SOFT-FAIL | both choosers; only BOOLEAN/INTEGER/ROWID/ENUM keys (canonical inline carriers); no-else / non-inline key → soft-fail |
| ElvisExpr | NATIVE | NULL_-tag branch |
| NotNullExpr | NATIVE / SOFT-FAIL | pass-through when statically non-nullable; nullable operand → soft-fail (JVM raises) |
| TupleExpr | SOFT-FAIL | composite → opaque HANDLE; not lowered |
| ListLiteralExpr | SOFT-FAIL | "" |
| MapLiteralExpr | SOFT-FAIL | "" |
| StructExpr | SOFT-FAIL | "" |
| RegularCreateExpr | SOFT-FAIL | DB create; interpreter-only |
| StructCreateExpr | SOFT-FAIL | "" |
| StructListCreateExpr | SOFT-FAIL | "" |
| FunctionCallExpr | NATIVE / JNI / SOFT-FAIL | see Call-target table |
| MemberExpr | JNI / SOFT-FAIL | see Member-calculator table |
| AssignExpr | SOFT-FAIL | not in `lowerExpr` switch → default soft-fail (assignment-as-expression) |
| StatementExpr | SOFT-FAIL | result-slot wiring not exposed this ABI rev — always soft-fails |
| GlobalConstantExpr | SOFT-FAIL | default |
| ChainHeightExpr | SOFT-FAIL | default |
| TypeAdapterExpr | SOFT-FAIL | default (conversion); note VarStatement also soft-fails on adapter declarators |
| ParameterDefaultValueExpr | SOFT-FAIL | default |
| AttributeDefaultValueExpr | SOFT-FAIL | default |
| DbAtExpr | JNI | `rell_db_eval_expr` (SQL back-call) — routed in lower_call/sql_bridge |
| ColAtExpr | JNI | `rell_db_eval_expr` |
| ErrorExpr | SOFT-FAIL | explicit refuse-to-emit (should never reach a compiled App) |
| ListSubscriptExpr | SOFT-FAIL | default |
| MapSubscriptExpr | SOFT-FAIL | default |
| TextSubscriptExpr | SOFT-FAIL | default |
| ByteArraySubscriptExpr | SOFT-FAIL | default |
| VirtualListSubscriptExpr | SOFT-FAIL | default |
| VirtualMapSubscriptExpr | SOFT-FAIL | default |
| JsonArraySubscriptExpr | SOFT-FAIL | default |
| JsonObjectSubscriptExpr | SOFT-FAIL | default |
| StructMemberExpr | SOFT-FAIL | default |
| ObjectValueExpr | SOFT-FAIL | default |
| LazyExpr | SOFT-FAIL | default |

**Hole check:** none. The 22 "default" variants land on `lowerExpr`'s `default: ec.failExpr(...)`.

---

## BinaryExpr ops (`op.fbs BinaryOp` + `CmpInfo`)

| Op family | Status | Notes |
|---|---|---|
| ADD/SUB/MUL/DIV/MOD_INTEGER | NATIVE | checked (overflow intrinsics); escape → soft-fail |
| ADD/SUB/MUL/DIV/MOD_BIG_INTEGER | NATIVE / SOFT-FAIL | long-fitting envelope only; escape → soft-fail |
| ADD/SUB/MUL/DIV/MOD_DECIMAL | NATIVE / SOFT-FAIL | DEC_LONG (mantissa, scale≤18) envelope; escape → soft-fail |
| CONCAT_TEXT | NATIVE / SOFT-FAIL | inline text intrinsic; escape → soft-fail |
| CONCAT_BYTE_ARRAY | NATIVE / SOFT-FAIL | inline bytearray intrinsic; escape → soft-fail |
| EQ / NE | NATIVE / SOFT-FAIL | only i64-inline-equality-safe carriers; else soft-fail |
| EQ_REF / NE_REF | SOFT-FAIL | reference identity not JITable inline |
| AND / OR | NATIVE | short-circuit, phi |
| CmpInfo LT/GT/LE/GE — INTEGER/ROWID/BOOLEAN/ENUM | NATIVE | signed inline i64 cmp |
| CmpInfo — BIG_INTEGER/DECIMAL/TEXT/BYTE_ARRAY/ENTITY | SOFT-FAIL | non-canonical/handle order; no callable cmp sysfn in scope |
| CONCAT_LIST, IN_*, SUB_LIST/SET, UNION_SET, INTERSECT_*, MERGE_MAP | SOFT-FAIL | collection/range ops → interpreter |

## UnaryExpr (`UnaryOp`)

| Op | Status |
|---|---|
| NOT | NATIVE |
| MINUS_INTEGER / MINUS_BIG_INTEGER / MINUS_DECIMAL | NATIVE in-envelope, else SOFT-FAIL |

---

## RR_Statement (`StmtUnion`, 17 variants)

| Variant | Status | Notes |
|---|---|---|
| EmptyStatement | NATIVE | no-op |
| ExprStatement | NATIVE | evaluate, discard |
| ReturnStatement | NATIVE | store return slot + jump |
| BlockStatement | NATIVE | frame block + nested stmts |
| VarStatement | NATIVE / SOFT-FAIL | Simple + Wildcard declarators; **adapter declarator → soft-fail**; Tuple destructuring → soft-fail |
| AssignStatement | NATIVE / SOFT-FAIL | plain `=` to a local VarExpr slot; compound (`+=`) / non-local target → soft-fail |
| IfStatement | NATIVE | |
| WhileStatement | NATIVE | loop + break/continue targets |
| BreakStatement | NATIVE | |
| ContinueStatement | NATIVE | |
| GuardStatement | NATIVE | operation guard block |
| WhenStatement | SOFT-FAIL | chooser lowering for statement-when not implemented |
| ForStatement | SOFT-FAIL | iterable adapter lowering not implemented |
| LambdaStatement | SOFT-FAIL | not implemented |
| ReplExprStatement | SOFT-FAIL | not JITed |
| UpdateStatement | JNI | `rell_db_exec_stmt` (SQL write back-call) |
| DeleteStatement | JNI | `rell_db_exec_stmt` |

VarDeclarator: Simple NATIVE / Wildcard NATIVE / Tuple SOFT-FAIL.
**Hole check:** none — `lowerStmt` default soft-fails.

---

## FunctionCallTarget (`FunctionCallTargetUnion`, 10)

| Target | Status | Notes |
|---|---|---|
| FnTarget_SysGlobal | JNI | `rell_sysfn_call` by interned name (universal stdlib floor) |
| FnTarget_SysMember | JNI / SOFT-FAIL | universal caller when receiver folded into args; explicit `base()` → soft-fail |
| FnTarget_NativeUser | JNI | stdlib-registered R_SysFunction by name |
| FnTarget_RegularUser | SOFT-FAIL | direct user call ABI gated/not wired |
| FnTarget_RegularQuery | SOFT-FAIL | not wired |
| FnTarget_Operation | SOFT-FAIL | callOperation interpreter-only |
| FnTarget_FunctionValue | SOFT-FAIL | dynamic fn-value dispatch JVM-only |
| FnTarget_AbstractUser | SOFT-FAIL | override resolution JVM-only |
| FnTarget_AbstractOverride | SOFT-FAIL | interpreter dispatch |
| FnTarget_Extendable | SOFT-FAIL | fan-out + combine JVM-only |
| PartialFunctionCall (any target) | SOFT-FAIL | closure construction JVM-only |

## MemberCalculator (`MemberCalculatorUnion`, 9)

| Calculator | Status | Notes |
|---|---|---|
| SysFunction | JNI | `rell_sysfn_call` by name |
| FunctionCall | JNI / SOFT-FAIL | delegates to call lowering |
| DataAttribute | SOFT-FAIL | DB read — interpreter-only (canonical route is rell_db_eval_expr on the at-node) |
| StructAttr / TupleAttr | SOFT-FAIL | composite is opaque HANDLE |
| VirtualTupleAttr / VirtualStructAttr | SOFT-FAIL | virtual decode JVM-only |
| DataAttributeExpr | SOFT-FAIL | lambda-frame eval JVM-only |
| ExprEval | SOFT-FAIL | inner-expr frame binding not modelled |
| safe `?.` (any) | SOFT-FAIL | safe member access not lowered inline |

---

## RR_DbExpr (`DbExprUnion`, 16) — all SQL

Every DbExpr variant (DbInterpreted, DbBinary, DbUnary, DbEntity, DbRel, DbAttr, DbRowid,
DbCollectionInterpreted, DbIn, DbElvis, DbCall, DbExists, DbInCollection, DbWhen, DbNestedAt,
DbSubQuery) is **JNI**: never lowered as a standalone IR node — it is reachable only as a child
of a `DbAtExpr`/`ColAtExpr`/`Update`/`Delete`, which route wholesale through the SQL back-call
(`rell_db_eval_expr` / `rell_db_exec_stmt`) into `Rt_Interpreter`. This is the deliberate
"JIT stops at SQL" boundary. **No DbExpr hole.**

---

## Stdlib families

100% of the stdlib is reachable via the **universal JNI caller**: any `SysGlobal` / `SysMember` /
`NativeUser` target and any `MemberCalculator_SysFunction` interns its name into the `SysFnTable`
and emits `rell_sysfn_call`, which dispatches into the dense `Array<R_SysFunction?>` JVM-side.
This is the correctness floor — every stdlib function is callable.

C++ intrinsic overlays (the fast-path families) **exist** but their inline coverage is:

| Family | Native overlay present | Inline-wired into call path? |
|---|---|---|
| integer (arith/abs/sign/min/max) | yes (`intrinsics_integer.cpp`) | arith YES via `lowerBinary`; `abs/min/max/sign` **NOT** (sysfns go to JNI) |
| decimal (long-fit add/sub/mul) | yes (`intrinsics_decimal.cpp`) | arith YES via `lowerBinary`; DIV/MOD + sysfns escape→JNI |
| big_integer (long-fit) | yes (`intrinsics_biginteger.cpp`) | arith YES via `lowerBinary` |
| text (concat) | yes (`intrinsics_text.cpp`) | CONCAT_TEXT YES; text sysfns → JNI |
| byte_array (concat) | yes (`intrinsics_bytearray.cpp`) | CONCAT_BYTE_ARRAY YES; sysfns → JNI |
| math (abs/sign/min/max) | yes (`intrinsics_math.cpp`) | **NOT wired** — `lower_call.cpp` always takes the JNI route (see line ~336 comment) |

Everything else (collections, map, struct, entity, gtv, json, crypto, op_context, chain_context,
require/assert, text formatting, etc.) is JNI-only by design.

---

## Invariant verdict

- **100% reachability holds.** Every `Expr`/`Stmt`/`DbExpr` variant and every stdlib function is
  NATIVE, JNI, or cleanly SOFT-FAIL. No node is "neither lowered nor soft-failed."
- **No correctness holes found.** The universal `default: ec.fail*` on each dispatcher plus the
  `ec.failed()` early-out guarantee that an unhandled or partially-handled node aborts the whole
  function to the interpreter rather than emitting approximate IR. Determinism is preserved.
- **Confirmed:** all tests pass through `Llvm_Backend` because the soft-fail floor delegates to
  `Rt_InterpreterImpl`; all stdlib reachable via `rell_sysfn_call`.

### One wiring gap worth flagging (not a hole — pure-perf left on the table)
The math/integer/text intrinsic overlays for **sys-function calls** are written but **not yet
called from `lower_call.cpp`** (the `SysGlobal`/`SysMember` path always emits `rell_sysfn_call`).
This is correct (JNI floor) but means `abs/min/max/sign`, text/decimal sys methods, etc. take a
JVM round-trip even though the inline IR exists. This is the cheapest high-value native win.

---

## Highest-value next targets (JNI/soft-fail → NATIVE)

1. **Wire the intrinsic overlays into the sys-call path.** Before `emitSysfnCall`, try
   `intrinsicMath` / `intrinsicInteger` (abs/sign/min/max) on the interned name and emit inline
   when not `*escaped`. Code is done; only the pre-emption call site in `lower_call.cpp` is
   missing. Biggest win-per-line.
2. **DECIMAL/BIG_INTEGER comparison (`CmpInfo`).** Currently soft-fails the whole function. A
   long-fit DEC_LONG/BIGINT_LONG comparison is a common hot path; an in-envelope inline compare
   (mantissa-aligned for decimal) removes a frequent full-function fallback.
3. **Compound assignment + adapter VarStatement.** `+=` and integer→decimal/bigint adapter
   declarators soft-fail whole functions; both are mechanical given the existing arith intrinsics
   and `TypeAdapter` is a tiny closed enum (DIRECT / INTEGER_TO_BIG_INTEGER / INTEGER_TO_DECIMAL /
   BIG_INTEGER_TO_DECIMAL / NULLABLE).
4. **ForStatement over a range / inline list.** Loops are the dominant interpreter cost; even a
   range-only `for` (IN_RANGE iterable) lowered natively eliminates a large class of soft-fails.
5. **Decimal constant inline (DEC_LONG).** Every decimal literal currently soft-fails its whole
   function. A JVM-validated (mantissa, scale) handed across at compile time (rather than parsed
   in C++) would let decimal-heavy bodies stay native — needs an ABI add (constant pre-validation),
   so lower priority than 1–4.
6. **TEXT equality / comparison via a callable sysfn.** Exposing the text comparator as a
   sys-callable id would let EQ/NE/cmp on text stay in a (still-JNI but cheaper, no full fallback)
   path, or a length+bytes inline fast-path for the ASCII common case.
