// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.

# ABI: Universal JNI stdlib caller + SQL JNI back-call

This document is the contract between three phases that touch the LLVM backend:

- the **ABI/value phase** (`rell_runtime.h`) which declares `RellValue`, the per-call arena,
  and the native-side bridge prototypes referenced here;
- the **Wiring phase** (Kotlin) which must implement the JVM-side dispatch helper and register
  its native methods;
- the **codegen phase** (`jni_bridge.cpp` lowering) which emits IR-level calls into the bridge
  entries declared here when a node falls outside the C++ intrinsic envelope.

It does NOT introduce any code into `jni_bridge.cpp`, `*.kt`, or `*.kts`. It is the spec those
phases implement. The naming, error-handling, and FlatBuffer-accessor idioms below match the
existing skeleton (`fail()` soft-fail → return 0 / null handle; `throwRuntime`/
`throwIllegalArgument` for hard failures; cached `JavaVM* g_vm` from `JNI_OnLoad`; flatc
accessors such as `target.target_as_FnTarget_SysGlobal()->fn_name()`).

The two mechanisms (a) universal stdlib caller and (b) SQL back-call share one substrate: a
**stable int32 id table** built at JIT time from the FlatBuffer, plus an **opaque call-context
handle** that native code threads from the JIT entry through every back-call so the JVM can
recover the live `Rt_CallFrame`/`Rt_CallContext`.

---

## 0. Shared substrate

### 0.1 `RellValue` and `RellCallCtx` (declared in `rell_runtime.h`, summarised here)

`RellValue` is the layered tagged value from the committed architecture: inline primitives
(`boolean`, `integer` i64, `rowid`, long-fitting `decimal`/`big_integer`) carried in a small
tagged struct; everything heap/complex carried as an **opaque handle** = a `jobject` global ref
to the JVM `Rt_Value`, owned by the per-call bump arena and released en masse at call end. The
exact struct is the ABI phase's deliverable; this document only relies on three operations it
must expose (names indicative — match `rell_runtime.h`):

```c++
// Box an inline RellValue into a JVM Rt_Value jobject (global ref, arena-owned), or return the
// already-held global ref for handle-carrying values. Used to marshal native args up to the JVM.
jobject rell_value_to_jobject(JNIEnv*, const RellValue&, RellCallCtx*);

// Wrap a JVM Rt_Value jobject coming back from the JVM into a RellValue: inline it if it is a
// primitive in the safe envelope, otherwise take a global ref into the arena and carry a handle.
RellValue rell_value_from_jobject(JNIEnv*, jobject, RellCallCtx*);

// Per-call bump arena: registers a global ref for bulk release; allocates id-table-free scratch.
jobject rell_arena_global_ref(RellCallCtx*, jobject local);
```

`RellCallCtx` is the native-side per-call context. It carries (at minimum):

- `JNIEnv* env;` — the attached env for the current thread (the JIT entry attaches via `g_vm`);
- `jobject frameHandle;` — an **opaque global ref to the live JVM `Rt_CallFrame`** for the
  function currently executing (see §0.3);
- the bump arena bookkeeping (free list / region pointer) for handle-carrying `RellValue`s;
- `jlong appTableHandle;` — pointer to the interned id→name resolution table (see §0.2) so the
  JVM dispatch helper can be reached; in practice this is folded into `frameHandle`'s owning
  `Llvm_CallEnv` on the JVM side and need not round-trip per call.

`RellCallCtx*` is passed as a hidden trailing parameter to **every** JIT'd function and forwarded
unchanged into every bridge call. The codegen phase threads it exactly as `args` is threaded
today (one extra pointer in the function signature: `i64 @rell_fn_N(i64* %args, ptr %ctx)` once
the value surface widens; on the current i64-only slice it is absent and added by the widening
work).

### 0.2 Interned sysfn id table (build at JIT time)

Stdlib functions are identified across the boundary by a **stable `int32 sysfnId`**, NOT by
string per call. Rationale: the RR tree references stdlib by name
(`FnTarget_SysGlobal.fn_name`, `FnTarget_SysMember.fn_name`, `FnTarget_NativeUser.fn_name`, and
`MemberCalculator_SysFunction.fn_name`), but passing a `jstring` and doing a JVM map lookup on
every hot call defeats the purpose of JITing. Instead:

**Native side, once per App (during `compileFunctionByIndex` / a new `summarize`-style pass):**
walk the whole FlatBuffer App and collect every distinct stdlib name reachable from any
`FunctionCallTarget`/`MemberCalculator`:

- `FunctionCallTargetUnion::FnTarget_SysGlobal` → `fn_name()`
- `FunctionCallTargetUnion::FnTarget_SysMember` → `fn_name()`
- `FunctionCallTargetUnion::FnTarget_NativeUser` → `fn_name()`
- `MemberCalculatorUnion::MemberCalculator_SysFunction` → `fn_name()`

Build a `std::unordered_map<std::string, int32_t> g_sysfnIds` (name → dense id, assigned in first-
seen order) and the inverse `std::vector<std::string> g_sysfnNames` (id → name). This table is
App-scoped; store it next to the JIT (guarded by `g_jitMutex`, like `g_fnCounter`). The codegen
phase resolves a node's name to its `int32` id at lowering time and emits that **constant** into
the IR — so the JIT'd call site carries a literal `i32 sysfnId`, never a string.

**JVM side, mirror:** the Wiring phase materialises the same `id → R_SysFunction` mapping. It
asks native for `g_sysfnNames` (a new trivial extern, e.g. `internedSysfnNames(): Array<String>`,
returning the table in id order), then resolves each name through the compilation-local
`Rt_StdlibEnv.sysFunctions` (an `ImmMap<String, R_SysFunction>` — see `rr_stdlib_env.kt`). The
result is a dense `Array<R_SysFunction>` indexed by `sysfnId`. Names that do not resolve in
`sysFunctions` (should not happen for a well-formed App) map to a `null` slot; a call into a
`null` slot is a **hard failure** (`throwRuntime`), never a silent wrong answer.

`NativeUser` names (`R_SysFunction` registered under a fully-qualified user name) resolve through
the *same* `sysFunctions` map — they live in the same registry — so no separate table is needed;
they share the `sysfnId` space.

Determinism note: id assignment order is irrelevant to results because ids are private to one
App's JIT session and the JVM rebuilds its array from the native-provided name order. Ids never
cross a consensus boundary.

### 0.3 Call-context / frame threading

The JVM owns the live `Rt_CallFrame` (`rt_frame.kt`). When `Llvm_Backend.callFunction` dispatches
into a JIT'd function, the Wiring phase:

1. creates the callee `Rt_CallFrame` exactly as `Rt_InterpreterImpl` does
   (`createFrame(exeCtx, fn.frame, dbUpdateAllowed, defId)`);
2. wraps it plus the `id → R_SysFunction` array and the `Rt_StdlibEnv` into a small JVM holder
   `Llvm_CallEnv` (Wiring phase type);
3. takes a JNI **global ref** to that holder and stores its raw `jobject` into
   `RellCallCtx.frameHandle` before entering native code; releases it after the call returns.

Native back-calls pass `frameHandle` straight back to the JVM dispatch helper, which recovers the
`Llvm_CallEnv` (and from it the `Rt_CallFrame`, `Rt_CallContext`, and the sysfn array). The native
side treats `frameHandle` as fully opaque — it never dereferences it.

The current `Rt_CallContext` needed by `R_SysFunction.call(ctx, args)` is obtained JVM-side via
`Rt_CallFrame.callCtx()` (see `rt_frame.kt:143` → `defCtx.toCallContext()`); native never
constructs it.

---

## (a) Universal JNI stdlib caller

### a.1 Native entry (declared in `rell_runtime.h`, implemented in the codegen/bridge `.cpp`)

```c++
// Universal stdlib dispatch. Calls BACK into the JVM's R_SysFunction registry for `sysfnId`.
//   sysfnId : dense id from the interned table (§0.2); emitted as an IR constant at the call site.
//   args    : pointer to `nargs` contiguous RellValue (the evaluated, already-marshalled args).
//   nargs   : argument count.
//   ctx     : the live RellCallCtx (carries env + frameHandle + arena).
// Returns the result as a RellValue (inlined if a primitive in the safe envelope, else a handle
// registered in ctx's arena). On a JVM-side Rell error the pending exception is left set and the
// JIT'd function unwinds (see a.4); this function does not swallow it.
RellValue rell_sysfn_call(JNIEnv* env, int32_t sysfnId, const RellValue* args,
                          int32_t nargs, RellCallCtx* ctx);
```

Implementation outline (mirrors the skeleton's JNI idioms):

1. Build a `jobjectArray` of length `nargs` of `Rt_Value`: for each `args[i]`, call
   `rell_value_to_jobject(env, args[i], ctx)` and `SetObjectArrayElement`. The array is a local
   ref; it is consumed by the single dispatch call and dropped immediately after (the arena owns
   the element global refs, not the array).
2. Call the static JVM dispatch method (cached `jclass`/`jmethodID`, see a.2) with
   `(sysfnId, jobjectArray, frameHandle)`.
3. If `env->ExceptionCheck()` is true → return a poison `RellValue` and let the caller unwind
   (a.4). Do NOT clear the exception.
4. Otherwise `rell_value_from_jobject(env, result, ctx)` and return it.

The `jclass net/postchain/rell/llvm/Llvm_SysBridge` and the `jmethodID` for `dispatch` are cached
once (guarded like `g_jit`), resolved lazily on first call using `env->FindClass` /
`GetStaticMethodID`; a `nullptr` from either is a `throwRuntime` hard failure.

### a.2 JVM-side dispatch method (Wiring phase implements — exact signature)

A new JVM helper object in package `net.postchain.rell.llvm`, with a `@JvmStatic` dispatch entry
whose JNI descriptor is stable. Native resolves it as:

- class: `net/postchain/rell/llvm/Llvm_SysBridge`
- method: `dispatch`
- descriptor: `(I[Ljava/lang/Object;J)Ljava/lang/Object;`

Kotlin signature the Wiring phase MUST provide (signature is load-bearing — native is coded
against this descriptor):

```kotlin
object Llvm_SysBridge {
    /**
     * Universal stdlib back-call. Invoked from JIT'd native code via `rell_sysfn_call`.
     *
     * @param sysfnId    dense id from the interned table; indexes the per-App R_SysFunction array.
     * @param args       boxed Rt_Value arguments (elements are Rt_Value; typed as Object[] so the
     *                   JNI descriptor stays `[Ljava/lang/Object;` and native needs no Rt_Value class ref).
     * @param frameHandle raw jobject (as jlong) of the global ref to the live Llvm_CallEnv (§0.3).
     * @return the Rt_Value result (typed as Any so the descriptor is `Ljava/lang/Object;`).
     */
    @JvmStatic
    fun dispatch(sysfnId: Int, args: Array<Any?>, frameHandle: Long): Any {
        val env = Llvm_CallEnv.fromHandle(frameHandle)         // reinterpret jobject; NewGlobalRef holder
        val fn = env.sysFns[sysfnId]
            ?: throw IllegalStateException("LLVM: unresolved sysfnId $sysfnId")
        val ctx: Rt_CallContext = env.frame.callCtx()           // Rt_CallFrame.callCtx()
        @Suppress("UNCHECKED_CAST")
        val rtArgs = (args as Array<Rt_Value>).asList()
        return fn.call(ctx, rtArgs)                             // R_SysFunction.call(ctx, args): Rt_Value
    }
}
```

Notes for the Wiring phase:

- `frameHandle` is passed as `jlong` (the `jobject` reinterpreted), NOT as a Java reference
  parameter, because native holds it as an opaque global ref in `RellCallCtx`. `Llvm_CallEnv.
  fromHandle` does the `JNIEnv*`-free reverse mapping — implement it by keeping the `Llvm_CallEnv`
  reachable from a side table keyed by the handle, OR (preferred) pass the env object directly as
  the parameter and change the descriptor to `(I[Ljava/lang/Object;Ljava/lang/Object;)…`. The ABI
  fixes only that native sends back whatever it received in `frameHandle`; choose the jlong-handle
  form to avoid a second global ref per call. If the object-parameter form is chosen, update the
  cached descriptor in a.1 accordingly and have native pass the `jobject` directly — this is the
  one open choice left to Wiring, flagged here explicitly.
- `R_SysFunction.call` is the SAM from `r_sys_function.kt`
  (`fun call(ctx: Rt_CallContext, args: List<Rt_Value>): Rt_Value`). All arities
  (`R_SysFunction_1/2/N`, `R_SysFunctionEx_*`) funnel through it, so the universal caller needs no
  arity awareness — the floor that makes 100% of stdlib reachable.
- `env.sysFns: Array<R_SysFunction?>` is the dense array from §0.2, built once per `Llvm_Backend`
  from `stdlib.sysFunctions` and the native-provided name order. Store it on `Llvm_CallEnv` (or on
  `Llvm_Backend` and reference it from the env) so `dispatch` is allocation-free except for the
  `asList()` view.

### a.3 Codegen contract (what the lowering pass emits)

For a `FunctionCallExpr` whose `FunctionCall` target is `FnTarget_SysGlobal`/`FnTarget_SysMember`/
`FnTarget_NativeUser`, or a `MemberExpr` whose calculator is `MemberCalculator_SysFunction`, when
the C++ intrinsic overlay does NOT cover the family (or an operand/result leaves the safe inline
envelope):

1. lower each arg `Expr` to a `RellValue` (recursively);
2. spill the args to a stack `RellValue[nargs]` (alloca);
3. resolve the target `fn_name()` to its `sysfnId` via `g_sysfnIds` at lowering time, emit it as
   an `i32` constant;
4. emit a call to `rell_sysfn_call(env, sysfnId, argsPtr, nargs, ctx)` (declared as an external
   symbol the JIT resolves to the bridge function);
5. propagate the returned `RellValue`.

`SysMember` and `MemberCalculator_SysFunction` carry the base value as `args[0]` (the receiver),
matching how `Rt_InterpreterImpl` threads the member base into the sys-fn arg list — the codegen
phase must place the lowered base first, then the call args, before invoking the universal caller.
If the receiver/base cannot be lowered, **soft-fail the whole function** (return 0 from
`compileFunctionByIndex`) — never emit a partial call.

### a.4 Errors and unwinding (determinism)

A Rell stdlib function may throw `Rt_Exception` (JVM). After any back-call, native MUST
`env->ExceptionCheck()`; if set, it must stop emitting/executing and propagate the unwind so the
pending JVM exception surfaces at the `callI64Function`/`callFunction` boundary unchanged — the
same exception the interpreter would have produced. The codegen phase models this as: every
`rell_sysfn_call` site is followed by an exception-check branch to a cleanup block that releases
the arena and returns a poison value; the outer JNI trampoline sees `ExceptionCheck()` and returns
without overwriting it. This keeps error identity/stack bit-exact with the interpreter (consensus
requirement). When in doubt about exact equivalence, the lowering pass soft-fails the function to
the interpreter rather than emitting the intrinsic.

---

## (b) SQL JNI back-call (DbAt / Update / Delete / ColAt)

SQL is never lowered to native IR. Any node that performs a database operation is dispatched
back into `Rt_InterpreterImpl` with the live frame, mirroring mechanism (a) but landing on the
interpreter's existing expr/stmt entry points instead of the sysfn registry.

### b.1 Which nodes route to the SQL back-call

- `ExprUnion::DbAtExpr` and `ExprUnion::ColAtExpr` → evaluate as an **expression**.
- `StmtUnion::UpdateStatement` and `StmtUnion::DeleteStatement` → execute as a **statement**.

(`ColAtExpr` is in-memory, not SQL, but it is routed through the same expression back-call because
the codegen phase does not lower at-iteration/summarization; treating it like `DbAtExpr` keeps one
path. This is a deliberate over-route, not a correctness gap.)

The codegen phase does NOT marshal the at-expression's internals across the boundary. Instead it
hands the JVM a reference to the **original RR node** plus the live frame, and lets the interpreter
evaluate it. The node reference is an index, not a pointer (see b.3).

### b.2 Native entries (declared in `rell_runtime.h`)

```c++
// Evaluate an RR_Expr (identified by nodeId) on the JVM interpreter against the live frame.
// Used for DbAtExpr / ColAtExpr. Returns the result as a RellValue (handle for collections/
// entities, inline for scalar at-results in the safe envelope).
RellValue rell_db_eval_expr(JNIEnv* env, int32_t nodeId, RellCallCtx* ctx);

// Execute an RR_Statement (identified by nodeId) on the JVM interpreter against the live frame.
// Used for UpdateStatement / DeleteStatement. Returns the statement outcome encoded as an int32
// (see b.4: 0 = normal/no-result, plus Return/Break/Continue encodings) — DB writes themselves
// produce no value.
int32_t rell_db_exec_stmt(JNIEnv* env, int32_t nodeId, RellCallCtx* ctx);
```

Both forward `ctx->frameHandle` to the JVM, exactly like the sysfn caller.

### b.3 Node identification

Like sysfn ids, DB nodes are interned to dense `int32 nodeId`s at JIT time. During the lowering of
a function body, when the codegen phase reaches a `DbAtExpr`/`ColAtExpr`/`UpdateStatement`/
`DeleteStatement` it cannot/should-not lower, it registers that node into a per-App table
`g_dbNodes` (a `std::vector` of `{functionIndex, path}` locators, or — simpler and preferred — the
Wiring phase exposes the already-resolved `List<RR_Expr>`/`List<RR_Statement>` of DB nodes per
function, and the codegen phase emits the index into it). The JVM side resolves `nodeId →
RR_Expr | RR_Statement` from the same per-App table built off `RR_App`.

Concretely, the Wiring phase precomputes, per `Llvm_Backend`, two dense lists by walking each
function body's RR tree in a fixed deterministic order:

```kotlin
val dbExprNodes: List<RR_Expr>        // DbAtExpr / ColAtExpr in walk order
val dbStmtNodes: List<RR_Statement>   // UpdateStatement / DeleteStatement in walk order
```

and the native side mirrors that same walk order over the FlatBuffer so the `nodeId` it emits
indexes the matching JVM list. (Two separate id spaces, one for exprs, one for stmts, matching the
two native entries.) Walk order is fixed and identical on both sides, so ids are deterministic and
never cross a consensus boundary.

### b.4 JVM-side SQL dispatch (Wiring phase implements — exact signatures)

Two more `@JvmStatic` entries on the same `Llvm_SysBridge` (or a sibling `Llvm_SqlBridge`):

```kotlin
object Llvm_SqlBridge {
    /** Evaluate dbExprNodes[nodeId] against the live frame. Used for DbAtExpr/ColAtExpr.
     *  Descriptor: (IJ)Ljava/lang/Object;  */
    @JvmStatic
    fun evalExpr(nodeId: Int, frameHandle: Long): Any {
        val env = Llvm_CallEnv.fromHandle(frameHandle)
        val expr = env.dbExprNodes[nodeId]
        // delegate exposes the public Rt_Interpreter entry: evaluateExpr(expr, frame)
        return env.interp.evaluateExpr(expr, env.frame)        // Rt_InterpreterImpl.evaluateExpr(RR_Expr, Rt_Frame)
    }

    /** Execute dbStmtNodes[nodeId] against the live frame. Used for UpdateStatement/DeleteStatement.
     *  Returns the encoded Rt_StatementResult (b.5).  Descriptor: (IJ)I  */
    @JvmStatic
    fun execStmt(nodeId: Int, frameHandle: Long): Int {
        val env = Llvm_CallEnv.fromHandle(frameHandle)
        val stmt = env.dbStmtNodes[nodeId]
        val res = env.interp.executeStmt(stmt, env.frame as Rt_CallFrame)  // internal entry; see b.5
        return Llvm_StmtResult.encode(res)
    }
}
```

Anchors in the existing code:

- `evaluateExpr(expr: RR_Expr, frame: Rt_Frame): Rt_Value` is the public `Rt_Interpreter` method
  (`rt_interpreter.kt:63`; impl `rr_interpreter.kt:158`). It already accepts the marker `Rt_Frame`
  and casts to `Rt_CallFrame` internally — so the SQL expr back-call needs nothing new on the
  interpreter.
- `executeStmt(stmt: RR_Statement, frame: Rt_CallFrame): Rt_StatementResult?` exists on
  `Rt_InterpreterImpl` (`rr_interpreter.kt:356`) but is not on the `Rt_Interpreter` interface. The
  Wiring phase reaches it through `Llvm_Backend.delegate` (the `Rt_InterpreterImpl` instance) —
  `Llvm_CallEnv.interp` should be typed as `Rt_InterpreterImpl`, not the interface, so both
  `evaluateExpr` and `executeStmt` are callable. Update statements run inside the SAME frame as the
  enclosing function, so passing `env.frame` (the live `Rt_CallFrame`) is correct and preserves
  `dbUpdateAllowed`/guard-block state (`Rt_CallFrame.dbUpdateAllowed()` etc.).

### b.5 Statement-result encoding

`executeStmt` returns `Rt_StatementResult?` (`rt_frame.kt`): `null`, `Return(value)`, `Break`,
`Continue`. For Update/Delete the result is always `null` (DB writes don't break control flow), so
the common encoding is trivial:

| native int32 | meaning |
|---|---|
| `0` | `null` — normal completion, continue execution |
| `1` | `Break` |
| `2` | `Continue` |
| `3` | `Return(null)` |
| `4` | `Return(value)` — value parked in `ctx` (see below) |

For Update/Delete only `0` is reachable; the richer encoding is specified so the same
`execStmt`/`encode` path can later carry control-flow-bearing statements if the codegen phase ever
routes them here. A `Return(value)` (code `4`) parks the `Rt_Value` into a per-call slot on
`Llvm_CallEnv` that native reads via a follow-up `rell_db_take_return(ctx): RellValue` — but since
the SQL slice never produces it, the codegen phase may simply treat any nonzero code other than the
ones it expects as a soft-fail / hard error. `Llvm_StmtResult.encode/decode` is a Wiring-phase
helper.

### b.6 Errors

Identical discipline to (a.4): after `rell_db_eval_expr`/`rell_db_exec_stmt`, native MUST
`ExceptionCheck()` and propagate any pending `Rt_Exception` unchanged. SQL errors, constraint
violations, and `no_db_update` guard errors (`Rt_CallFrame.checkDbUpdateAllowed`) thus surface
bit-identically to the interpreter.

---

## Summary of new symbols this ABI introduces

Native (declared in `rell_runtime.h`, implemented in the bridge/codegen `.cpp`, NOT in
`jni_bridge.cpp`):

- `RellValue rell_sysfn_call(JNIEnv*, int32_t sysfnId, const RellValue* args, int32_t nargs, RellCallCtx*)`
- `RellValue rell_db_eval_expr(JNIEnv*, int32_t nodeId, RellCallCtx*)`
- `int32_t   rell_db_exec_stmt(JNIEnv*, int32_t nodeId, RellCallCtx*)`
- App-scoped tables `g_sysfnIds` / `g_sysfnNames`, `g_dbExprNodes` / `g_dbStmtNodes` (id↔index),
  built by a FlatBuffer walk guarded by `g_jitMutex`.
- one trivial extern for the JVM to read `g_sysfnNames` in id order (Wiring builds its
  `Array<R_SysFunction?>` from it).

JVM (Wiring phase, package `net.postchain.rell.llvm`):

- `Llvm_SysBridge.dispatch(sysfnId: Int, args: Array<Any?>, frameHandle: Long): Any`
  — descriptor `(I[Ljava/lang/Object;J)Ljava/lang/Object;`
- `Llvm_SqlBridge.evalExpr(nodeId: Int, frameHandle: Long): Any` — descriptor `(IJ)Ljava/lang/Object;`
- `Llvm_SqlBridge.execStmt(nodeId: Int, frameHandle: Long): Int` — descriptor `(IJ)I`
- `Llvm_CallEnv` holder: `{ frame: Rt_CallFrame, interp: Rt_InterpreterImpl,
  sysFns: Array<R_SysFunction?>, dbExprNodes: List<RR_Expr>, dbStmtNodes: List<RR_Statement> }`
  + `fromHandle(jlong): Llvm_CallEnv`.
- `Llvm_StmtResult.encode/decode` for the §b.5 mapping.

**Open choice left to Wiring (flagged, not decided here):** whether `frameHandle` crosses as a
`jlong` opaque handle (descriptor uses `J`, native sends back the raw `jobject` it stored) or as a
direct `jobject` parameter (descriptor uses `Ljava/lang/Object;`). The jlong form avoids a second
global ref per call and is the recommended default; the object form is simpler. Native must match
whichever the Wiring phase picks in the cached method descriptor.

**Correctness floor:** any node a lowering pass cannot intrinsify routes through one of the three
back-calls above; anything those cannot model makes `compileFunctionByIndex` return 0 (soft-fail)
so the JVM interpreter handles the whole function. No path emits incorrect IR; determinism is
preserved because every non-intrinsic computation is performed by the same JVM code the interpreter
runs.
