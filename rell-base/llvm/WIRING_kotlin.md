# JVM-side wiring for the extended LLVM native ABI

Ready-to-apply spec for the Kotlin changes the native ABI in `rell_runtime.h` requires.
Each section gives the **target file**, an **insertion point**, and the **exact code** to apply.
Apply via IntelliJ MCP (`replace_text_in_file` / `create_new_file`). Do not hand-wave; the blocks
below are the literal source.

Ground truth this spec was written against:
- `R_SysFunction` is a `fun interface` with `fun call(ctx: Rt_CallContext, args: List<Rt_Value>): Rt_Value`
  (`rell-base/runtime-core/src/main/kotlin/runtime/r_sys_function.kt`).
- `Rt_StdlibEnv(val sysFunctions: ImmMap<String, R_SysFunction>)`
  (`rell-base/runtime-core/.../rr_stdlib_env.kt`).
- `Rt_CallContext(val defCtx: Rt_DefinitionContext)`; a frame exposes `defCtx`, and
  `Rt_DefinitionContext.toCallContext()` already memoises a `Rt_CallContext`
  (`rt_context.kt:438`, `rt_frame.kt`).
- Interpreter back-calls already exist on the interface: `Rt_Interpreter.evaluateExpr(expr, frame)`;
  `Rt_InterpreterImpl.executeStmt(stmt, frame as Rt_CallFrame)` is internal-but-reachable via
  `Llvm_Backend.delegate` (`rr_interpreter.kt:158, :356`).
- `Llvm_Backend.delegate` is the wrapped `Rt_InterpreterImpl`; `outerInterp = this` is already wired.

Naming note: the native side (`rell_runtime.h` §5) calls the JVM dispatch class
**`Llvm_SysBridge`** with method **`dispatch`**. Because Kotlin does NOT erase `Array<Rt_Value>` /
`Rt_Value` to `Object[]` / `Object`, the real JVM descriptor is
**`(I[Lnet/postchain/rell/base/runtime/Rt_Value;J)Lnet/postchain/rell/base/runtime/Rt_Value;`** — the
native `GetStaticMethodID` and the boxed-args `NewObjectArray` element class must both be Rt_Value-
typed. The Kotlin class/method names below MUST match those JNI descriptors exactly — do not rename
without updating the C++.

---

## 1. New externs on `RellLlvmNative`

**Target:** `rell-base/llvm/src/main/kotlin/net/postchain/rell/llvm/RellLlvmNative.kt`
**Insertion point:** after the existing `callI64Function` declaration (keep all three existing
externs unchanged).

The existing `compileFunctionByIndex` / `callI64Function` stay as-is for the i64 fast slice. The
extended ABI needs:

1. A compile entry that **also returns the interned sysfn / db-node name tables** the native walk
   produced, so the JVM can build the dense `R_SysFunction` / db-node arrays the bridge indexes.
2. A call entry that **marshals arbitrary `Rt_Value` args/results** (not just i64), threading the
   per-call context handle the trampoline passes back into `Llvm_SysBridge`/SQL back-calls.

```kotlin
    /**
     * Extended compile entry. Lowers the function at [functionIndex] exactly like
     * [compileFunctionByIndex], but additionally interns — over the same deterministic RR-tree
     * walk the native lowerer performs — every stdlib fn name reachable from the body
     * (FnTarget_{SysGlobal,SysMember,NativeUser}, MemberCalculator_SysFunction, SysQueryBody) and
     * every DbAt/ColAt/Update/Delete node.
     *
     * On success returns a non-zero opaque handle to a native `CompiledFn` record (function
     * pointer + the per-function `SysFnTable` and `DbNodeTable`). [outSysFnNames] is filled, in
     * dense `SysFnId` order, with the interned stdlib fn names; [outDbNodeCount] receives the
     * db-node table size. The JVM side ([Llvm_SysBridge.install]) consumes these to build the
     * dense dispatch arrays whose indices the native `rell_sysfn_call` / SQL back-calls mirror.
     *
     * Returns 0 (and leaves the out-params untouched) when the body is outside the JIT envelope —
     * same soft-fail contract as [compileFunctionByIndex]. Throws only on hard faults.
     */
    external fun compileFunctionExtended(
        appBytes: ByteArray,
        functionIndex: Int,
        outSysFnNames: ArrayList<String>,
        outDbNodeCount: IntArray,
    ): Long

    /**
     * Invokes a previously JIT'd function (handle from [compileFunctionExtended]) with marshalled
     * [Rt_Value] arguments and returns the marshalled [Rt_Value] result.
     *
     * [ctxHandle] is an opaque jlong handle (an index into the JVM-side [Llvm_CallEnv] registry)
     * that the trampoline threads as the hidden trailing `RellCallCtx` param so back-calls
     * (`Llvm_SysBridge.dispatch`, the SQL evaluator) can recover the live `Rt_Frame` / arena /
     * sysfn table for this call. The native side unwraps each arg via `from_jvm`, runs the body,
     * and reboxes the result via `to_jvm`. A pending `Rt_Exception` thrown by any back-call
     * propagates out unchanged (the trampoline aborts; this method rethrows on the JVM side).
     */
    external fun callValueFunction(
        fnHandle: Long,
        args: Array<Rt_Value>,
        ctxHandle: Long,
    ): Rt_Value
```

Add the import at the top of the file (it currently imports nothing from runtime):

```kotlin
import net.postchain.rell.base.runtime.Rt_Value
```

> Why `ArrayList<String>` / `IntArray` out-params instead of a return struct: JNI can `Call*Method`
> into `java.util.ArrayList.add` and write an `int[0]` slot far more cheaply than synthesising a
> Kotlin data class from C++. Mirror jni_bridge's preference for primitive/JDK-collection ABI at
> the boundary.

---

## 2. New JVM dispatch bridge — `Llvm_SysBridge`

This is the JVM landing pad the native `rell_sysfn_call` invokes (§5 of `rell_runtime.h`). It owns,
per live call, a dense `Array<R_SysFunction?>` indexed by the native `SysFnId`, plus the
`Rt_CallContext` to pass to `R_SysFunction.call`. The native name→id interning is mirrored here
**by consuming the id-ordered name list** returned from `compileFunctionExtended` (so the JVM never
re-derives ids — it just looks each name up in `Rt_StdlibEnv.sysFunctions`).

**Target (new file):** `rell-base/llvm/src/main/kotlin/net/postchain/rell/llvm/Llvm_SysBridge.kt`

```kotlin
/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.llvm

import net.postchain.rell.base.runtime.R_SysFunction
import net.postchain.rell.base.runtime.Rt_CallContext
import net.postchain.rell.base.runtime.Rt_StdlibEnv
import net.postchain.rell.base.runtime.Rt_Value
import java.util.concurrent.atomic.AtomicLong

/**
 * JVM landing pad for native back-calls out of JIT'd code.
 *
 * The native runtime ([rell_runtime.h] §5/§6) cannot hold Rell objects; whenever JIT'd code hits a
 * stdlib function (any `R_SysFunction` reachable by name), a DbAt/ColAt expression, or an
 * Update/Delete statement that it cannot lower bit-exactly, it calls BACK into the JVM through this
 * object. Native code addresses everything by dense integer id:
 *
 *  - a [Rt_StdlibEnv] sysfn is reached by `sysfnId` — an index into [callEnvOf]`(ctxHandle).sysFns`.
 *  - the live [Rt_Frame] / [Rt_CallContext] is reached by `ctxHandle` — a key into [registry].
 *
 * The id↔name agreement with the native `SysFnTable` is established at compile time: the native
 * lowerer returns its id-ordered name list (see [RellLlvmNative.compileFunctionExtended]); the JVM
 * resolves each name once against [Rt_StdlibEnv.sysFunctions] into the dense [Llvm_CallEnv.sysFns]
 * array. There is no second, independent interning pass on the JVM side — that is what keeps the
 * two id spaces identical without a shared hash.
 */
object Llvm_SysBridge {

    /** Live call environments keyed by the opaque `ctxHandle` native threads as `RellCallCtx`. */
    private val registry = HashMap<Long, Llvm_CallEnv>()
    private val nextHandle = AtomicLong(1L) // 0 reserved for "no ctx" on the native side.

    /**
     * Registers a call environment for the duration of one JIT'd function invocation and returns
     * its opaque handle. [Llvm_Backend.invokeValueNative] pushes one of these around every native
     * call and pops it in a `finally`, so the map never leaks across calls.
     */
    @Synchronized
    fun register(env: Llvm_CallEnv): Long {
        val h = nextHandle.getAndIncrement()
        registry[h] = env
        return h
    }

    @Synchronized
    fun unregister(handle: Long) {
        registry.remove(handle)
    }

    @Synchronized
    private fun callEnvOf(handle: Long): Llvm_CallEnv =
        registry[handle] ?: error("Llvm_SysBridge: no call environment for handle $handle")

    /**
     * Native entry: invoke the stdlib function interned at [sysfnId] with the reboxed [args].
     *
     * Native JNI signature MUST be the Rt_Value-typed descriptor Kotlin actually compiles this to:
     * `dispatch(I[Lnet/postchain/rell/base/runtime/Rt_Value;J)Lnet/postchain/rell/base/runtime/Rt_Value;`
     * (Kotlin does NOT erase `Array<Rt_Value>`/`Rt_Value` to `Object[]`/`Object`). The `args` come
     * boxed as an `Rt_Value[]`, the `ctxHandle` is the call-env handle. The
     * returned `Rt_Value` is unwrapped by `from_jvm` on the native side. Any `Rt_Exception` thrown
     * by the sysfn propagates out across JNI unchanged (the native trampoline aborts on the pending
     * exception — see `rell_runtime.h` §7).
     */
    @JvmStatic
    fun dispatch(sysfnId: Int, args: Array<Rt_Value>, ctxHandle: Long): Rt_Value {
        val env = callEnvOf(ctxHandle)
        val fn = env.sysFns.getOrNull(sysfnId)
            ?: error("Llvm_SysBridge.dispatch: sysfnId $sysfnId out of range (size ${env.sysFns.size})")
        // `args` arrives as Array<Rt_Value>; R_SysFunction.call takes List<Rt_Value>.
        return fn.call(env.callCtx, args.asList())
    }

    /**
     * Builds the dense `R_SysFunction` array the native ids index into, by resolving each native-
     * interned name (id-ordered, from [RellLlvmNative.compileFunctionExtended]) against the
     * compilation-local [Rt_StdlibEnv]. A name absent from the env yields a `null` slot: native
     * code must never have interned an id it cannot reach, so a `null` hit at run time is a hard
     * bug, surfaced by [dispatch] above rather than silently mis-dispatched.
     */
    fun resolveSysFns(stdlib: Rt_StdlibEnv, sysFnNames: List<String>): Array<R_SysFunction?> =
        Array(sysFnNames.size) { i -> stdlib.sysFunctions[sysFnNames[i]] }
}

/**
 * Per-invocation call environment the native side reaches by `ctxHandle`. Wraps the live
 * [Rt_CallContext] (the JVM frame/exec context for the running JIT'd function) and the dense
 * sysfn dispatch table for the function being run.
 *
 * `frameHandle` in `rell_runtime.h` §6 == the handle this is registered under; this class is the
 * "Llvm_CallEnv wrapping the live Rt_Frame" referenced there.
 */
class Llvm_CallEnv(
    val callCtx: Rt_CallContext,
    val sysFns: Array<R_SysFunction?>,
)
```

> SAM funnel: all `R_SysFunction_N` / `_1` / `_2` / `Ex_*` arities override the single
> `call(ctx, args)` SAM (verified in `r_sys_function.kt`), so `dispatch` needs no per-arity logic.

---

## 3. `Llvm_Backend` changes

**Target:** `rell-base/llvm/src/main/kotlin/net/postchain/rell/llvm/Llvm_Backend.kt`

### 3a. Broaden the JIT cache record + the compile path to the extended entry

Replace the `CompiledFn` record and `jitCacheFor` so the cache carries the native handle plus the
id-ordered sysfn names captured at compile time, and resolves them once into the dense
`R_SysFunction?` array.

Replace (`private data class CompiledFn(...)`):

```kotlin
    private data class CompiledFn(
        val paramCount: Int,
        val fnHandle: Long,
        /** Dense `R_SysFunction?` table; index == native `SysFnId`. */
        val sysFns: Array<R_SysFunction?>,
    )
```

Replace the body of `jitCacheFor` (keep the `containsKey` "tried, not compilable" guard) so it
calls `compileFunctionExtended` and resolves the returned names:

```kotlin
    private fun jitCacheFor(fn: RR_FunctionDefinition): CompiledFn? {
        if (jitCache.containsKey(fn)) return jitCache[fn]
        val index = functionIndices[fn]
        val entry = if (index == null) {
            null
        } else {
            val sysFnNames = ArrayList<String>()
            val dbNodeCount = IntArray(1)
            val handle = RellLlvmNative.compileFunctionExtended(serializedApp, index, sysFnNames, dbNodeCount)
            if (handle == 0L) {
                null
            } else {
                CompiledFn(
                    paramCount = fn.fnBase.params.size,
                    fnHandle = handle,
                    sysFns = Llvm_SysBridge.resolveSysFns(stdlib, sysFnNames),
                )
            }
        }
        jitCache[fn] = entry
        return entry
    }
```

### 3b. Thread the call env and marshal arbitrary `Rt_Value` args/results

`callFunction` already has the `exeCtx` it needs to build a `Rt_CallContext`. Replace `invokeNative`
(the i64-only marshaller) with a value marshaller that registers an `Llvm_CallEnv` for the call,
threads its handle through `callValueFunction`, and unregisters in a `finally`.

`callFunction` keeps the same shape but calls the new `invokeValueNative` and drops the
`args.size == compiled.paramCount`-only / `Rt_IntValue`-only assumptions. Replace `callFunction`:

```kotlin
    override fun callFunction(
        fn: RR_FunctionDefinition,
        exeCtx: Rt_ExecutionContext,
        args: List<Rt_Value>,
        dbUpdateAllowed: Boolean,
    ): Rt_Value {
        val compiled = jitCacheFor(fn)
        if (compiled != null && args.size == compiled.paramCount) {
            jitHits++
            return invokeValueNative(compiled, fn, exeCtx, dbUpdateAllowed, args)
        }
        jitMisses++
        return delegate.callFunction(fn, exeCtx, args, dbUpdateAllowed)
    }
```

Replace `invokeNative`:

```kotlin
    private fun invokeValueNative(
        compiled: CompiledFn,
        fn: RR_FunctionDefinition,
        exeCtx: Rt_ExecutionContext,
        dbUpdateAllowed: Boolean,
        args: List<Rt_Value>,
    ): Rt_Value {
        // The native back-calls (Llvm_SysBridge.dispatch, SQL evaluation) need the live call
        // context for THIS invocation. Build it from the same defId/frame the interpreter would,
        // register it under an opaque handle, and tear it down after the native call returns.
        val defCtx = Rt_DefinitionContext(exeCtx, dbUpdateAllowed, fn.base.defId)
        val callEnv = Llvm_CallEnv(defCtx.toCallContext(), compiled.sysFns)
        val ctxHandle = Llvm_SysBridge.register(callEnv)
        try {
            return RellLlvmNative.callValueFunction(compiled.fnHandle, args.toTypedArray(), ctxHandle)
        } finally {
            Llvm_SysBridge.unregister(ctxHandle)
        }
    }
```

### 3c. Imports

`Rt_DefinitionContext` is already imported. Add (if the IDE doesn't auto-add):

```kotlin
import net.postchain.rell.base.runtime.R_SysFunction
```

(`R_SysFunction` is already imported in the current file for the companion — confirm before adding a
duplicate.) `Rt_IntValue` becomes unused once `invokeNative` is gone; remove its import to avoid an
unused-import warning:

```kotlin
import net.postchain.rell.base.runtime.Rt_IntValue   // DELETE — no longer referenced
```

> SQL back-call note (§6): `rell_db_eval_expr` resolves to `Llvm_Backend` (which IS `Rt_Interpreter`,
> so `evaluateExpr` is reachable), and `rell_db_exec_stmt` resolves to `delegate.executeStmt`. Both
> are reached from `Llvm_CallEnv` via the same `ctxHandle`; if the orchestrator wants `executeStmt`
> exposed to the bridge, add to `Llvm_CallEnv` a `val interp: Llvm_Backend` and call
> `interp.delegate.executeStmt(...)` from a future `Llvm_SysBridge.execStmt` entry. Not needed for
> the sysfn floor; flagged so the db-node wiring has a home. `delegate` is `internal`, reachable
> from same-module `Llvm_SysBridge`.

---

## 4. `RellTestUtils.forCompilation` — add the `"llvm"` case

**Target:** `rell-base/test-utils/src/main/kotlin/RellTestUtils.kt`
**Insertion point:** the `when (BACKEND)` in `forCompilation` (line ~75).

Replace:

```kotlin
    fun forCompilation(rrApp: RR_App, compilationSysFns: Map<String, Any>): Rt_Interpreter =
        when (BACKEND) {
            "truffle" -> Tf_Backend.forCompilation(rrApp, compilationSysFns)
            else -> Rt_InterpreterImpl.forCompilation(rrApp, compilationSysFns)
        }
```

with:

```kotlin
    fun forCompilation(rrApp: RR_App, compilationSysFns: Map<String, Any>): Rt_Interpreter =
        when (BACKEND) {
            "truffle" -> Tf_Backend.forCompilation(rrApp, compilationSysFns)
            "llvm" -> Llvm_Backend.forCompilation(rrApp, compilationSysFns)
            else -> Rt_InterpreterImpl.forCompilation(rrApp, compilationSysFns)
        }
```

Add the import (alongside the existing `import net.postchain.rell.base.runtime.truffle.Tf_Backend`):

```kotlin
import net.postchain.rell.llvm.Llvm_Backend
```

This also documents a fourth valid `rell.test.backend` value, `llvm`, in the `BACKEND` KDoc.

### 4a. Module dependency — CYCLE RISK (must read)

`forCompilation` referencing `Llvm_Backend` requires `:rell-base:test-utils` to depend on
`:rell-base:llvm`. **But `:rell-base:llvm` already declares**
`testImplementation(projects.rellBase.testUtils)` (build.gradle.kts line 316). Adding a *compile*
(`api`/`implementation`) dependency from test-utils → llvm therefore creates a Gradle dependency
cycle:

```
:rell-base:llvm  --testImplementation-->  :rell-base:test-utils  --implementation-->  :rell-base:llvm
```

Gradle tolerates cycles only when every edge crossing back is a `test*` configuration (test
classpaths are resolved separately from the main classpath). The current llvm→test-utils edge is
`testImplementation`, so the resolution is:

- **In `:rell-base:test-utils/build.gradle.kts`, add the edge as `implementation` (NOT `api`):**

  ```kotlin
  implementation(projects.rellBase.llvm)
  ```

  This is legal because the *only* back-edge (llvm → test-utils) is `testImplementation`, which does
  not participate in test-utils' main compile/runtime classpath. The main-classpath graph
  test-utils(main) → llvm(main) is acyclic; the llvm(test) → test-utils(main) edge closes the loop
  only inside llvm's test classpath, which Gradle resolves independently. **Verify** after applying
  with `./gradlew :rell-base:test-utils:compileKotlin :rell-base:llvm:compileTestKotlin` — a true
  cycle fails fast with "Circular dependency between the following tasks".

- **If Gradle still reports a cycle** (it will if any tooling promotes the back-edge to a main
  configuration, or if a future change makes llvm depend on test-utils at `implementation`):
  *do not* break it by weakening configurations. Instead **move `Llvm_Backend.forCompilation`
  routing out of test-utils** into a tiny reflective/late-bound indirection, mirroring how the
  backend is genuinely optional:

  ```kotlin
  // In RellTestUtils, avoid the static type reference:
  "llvm" -> {
      val cls = Class.forName("net.postchain.rell.llvm.Llvm_Backend")
      val m = cls.getMethod("forCompilation", RR_App::class.java, Map::class.java)
      m.invoke(null, rrApp, compilationSysFns) as Rt_Interpreter
  }
  ```

  with **no** Gradle dependency edge added (llvm is on the test runtime classpath transitively when
  the llvm test task runs, and the `"llvm"` branch is only taken under `-Drell.test.backend=llvm`).
  Prefer the direct dependency (first option) if it compiles cleanly; fall back to reflection only
  if the cycle is real. The Truffle precedent (`Tf_Backend` is a direct reference and
  `:rell-base:runtime-truffle` has no back-edge to test-utils) shows the direct form is the intended
  shape — the llvm cycle is purely an artifact of llvm's `testImplementation(testUtils)`, so the
  first option is almost certainly sufficient.
```
