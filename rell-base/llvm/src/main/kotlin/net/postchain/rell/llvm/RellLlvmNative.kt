/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.llvm

import net.postchain.rell.base.runtime.Rt_Exception
import net.postchain.rell.base.runtime.Rt_Value

/**
 * JNI bridge into the native rell-llvm shared library.
 *
 * The library is loaded once on first access via the `rell.llvm.libpath` system property
 * (set by the Gradle test task) so callers can use [summarizeApp] without arranging the
 * library on `java.library.path` themselves.
 */
object RellLlvmNative {
    init {
        val libPath = System.getProperty("rell.llvm.libpath")
            ?: error(
                "System property rell.llvm.libpath is not set. The Gradle test task wires this " +
                    "to the linkDebug output; outside of Gradle, set it to the absolute path of " +
                    "librell-llvm.{dylib,so}.",
            )
        System.load(libPath)
    }

    /**
     * Hands the FlatBuffers-serialized RR_App to native code and returns a human-readable
     * one-module-per-line summary produced on the C++ side.
     */
    external fun summarizeApp(bytes: ByteArray): String

    /**
     * Lowers the function at `functionIndex` in the FlatBuffers-serialized [appBytes] directly
     * to LLVM IR on the native side, JIT-compiles it via ORC, and returns the raw function
     * pointer for `int64 fn(int64*)`.
     *
     * Returns 0 if the body falls outside the prototype's compilable slice — the caller is
     * expected to interpret that as "not JITable" and route through `Rt_InterpreterImpl`.
     * Throws `RuntimeException` only on hard failures: JIT init, verifier rejection, lookup
     * miss, malformed FlatBuffers.
     */
    external fun compileFunctionByIndex(appBytes: ByteArray, functionIndex: Int): Long

    /**
     * Lowers the query at `functionIndex` in `App.queries` to LLVM IR and JIT-compiles it, exactly
     * like [compileFunctionByIndex]. A `UserQueryBody` is structurally identical to a function body
     * (integer return + integer params + statement body) and lowers through the same core; a
     * `SysQueryBody` (stdlib query) soft-fails. Returns 0 when the body is outside the JITable slice.
     */
    external fun compileQueryByIndex(appBytes: ByteArray, functionIndex: Int): Long

    /**
     * Lowers the global constant at `constIndex` in `App.constants` to an `i64 fn(i64*)` whose body
     * is `return <initializer>`, taking zero arguments. Integer-typed constants only; non-integer or
     * non-trivial initializers soft-fail (return 0).
     */
    external fun compileConstantByIndex(appBytes: ByteArray, constIndex: Int): Long

    /**
     * Invokes a previously JIT'd function with the given i64 arguments and returns its i64 result.
     *
     * If the JIT'd body performed an integer operation that overflowed (the tree-walker raises an
     * `Rt_Exception` via `Math.*Exact`), the i64 return value is meaningless; the caller MUST call
     * [pollIntOverflow] immediately afterwards and, if it returns a non-null code, raise the
     * corresponding `Rt_Exception` instead of using the result.
     */
    external fun callI64Function(fnPtr: Long, args: LongArray): Long

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
     * db-node table size. The JVM side ([Llvm_SysBridge.resolveSysFns]) consumes these to build the
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
        outListTypeCount: IntArray,
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

    /**
     * Returns the Rell error code (e.g. `expr:+:overflow:a:b`) if the most recent [callI64Function]
     * overflowed an integer operation, or null otherwise. Clears the pending state. Thread-local:
     * each call thread has its own channel, matching the per-thread JNI invocation.
     */
    external fun pollIntOverflow(): String?

    /**
     * Reports whether the most recent [callValueFunction] escaped the long-fit decimal/big_integer
     * envelope at runtime — a wide HANDLE operand, an i64 mantissa/scale overflow, or a decimal
     * `/`/`%` whose semantics only the interpreter owns. On an escape the native body produced a
     * meaningless result and [callValueFunction] returned `null`; the caller MUST re-run the whole
     * call on [net.postchain.rell.base.runtime.Rt_InterpreterImpl] (bit-exact). Clears the channel.
     * Thread-local, like [pollIntOverflow]. An escape is NOT a Rell error — it is a "compute this on
     * the interpreter instead" signal, distinct from the overflow/div0 error channel.
     */
    external fun pollJitEscape(): Boolean

    /**
     * Returns the Rell error code (`list:index:<size>:<index>`) if the most recent
     * [callValueFunction] hit a list subscript out of bounds, or null otherwise. Clears the pending
     * state. Thread-local, like [pollIntOverflow]. The Kotlin caller raises the [Rt_Exception] so the
     * error object (code + message) is consensus-identical to the tree-walker's `Rt_ListValue
     * .checkIndex`. A list OOB is a genuine Rell error (distinct from [pollJitEscape]).
     */
    external fun pollListError(): String?

    /**
     * Returns the Rell error code (`expr_bytearray_subscript_index:<size>:<index>`) if the most recent
     * [callValueFunction] hit a byte_array subscript out of bounds, or null otherwise. Clears the
     * pending state. Thread-local, like [pollIntOverflow]. The Kotlin caller raises the [Rt_Exception]
     * so the error object (code + message) is consensus-identical to the tree-walker's
     * `rr_interpreter.kt` `ByteArraySubscript`. Distinct from [pollListError] (different code/message).
     */
    external fun pollByteArrayError(): String?

    /**
     * Returns the Rell error code (`expr_text_subscript_index:<len>:<index>`) if the most recent
     * [callValueFunction] hit a text subscript out of bounds, or null otherwise. Clears the pending
     * state. Thread-local, like [pollIntOverflow]. The Kotlin caller raises the [Rt_Exception] so the
     * error object (code + message) is consensus-identical to the tree-walker's `rr_interpreter.kt`
     * `TextSubscript`. Distinct from [pollByteArrayError] / [pollListError] (different code/message).
     */
    external fun pollTextError(): String?

    /**
     * Returns `{code, message}` if the most recent [callValueFunction] hit a member text-op error
     * (`text.char_at` / `text.sub` / `text.index_of`/2 / `text.repeat` out of range), or null otherwise.
     * Clears the pending state. Thread-local, like [pollIntOverflow]. Unlike [pollTextError] (a fixed
     * subscript code with a rebuilt message), these ops raise op-specific codes AND messages, so the
     * native channel carries BOTH strings (built verbatim from `lib_type_text.kt`); the Kotlin caller
     * raises `Rt_Exception.common(code, message)` directly, consensus-identical to the tree-walker's.
     */
    external fun pollTextOpError(): Array<String>?

    /**
     * Compiles a pure-decimal function (decimal return + decimal params, body = `return <decimal
     * arith over params>`) to a JIT'd `i8*(i8**)` entry that runs the arithmetic natively via the
     * `rell_num_decimal_*` op glue (rell_numeric_ops.cpp) — ZERO JVM callback during computation.
     * Returns 0 for soft-fail (body outside the slice); the caller routes elsewhere.
     */
    external fun compileDecimalFunctionByIndex(appBytes: ByteArray, functionIndex: Int): Long

    /**
     * Invokes a JIT'd decimal function: each `args` element is an operand's plain decimal string;
     * returns the Rell-stripped result string. The native code computes entirely in C++
     * (rell::num) — it never calls back into the JVM. Throws on a native arithmetic error
     * (overflow / div0), the message prefixed with the exact Rell code.
     */
    external fun callDecimalFunction(fnPtr: Long, args: Array<String>): String
}
