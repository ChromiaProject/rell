/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.llvm

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
     * Returns the Rell error code (e.g. `expr:+:overflow:a:b`) if the most recent [callI64Function]
     * overflowed an integer operation, or null otherwise. Clears the pending state. Thread-local:
     * each call thread has its own channel, matching the per-thread JNI invocation.
     */
    external fun pollIntOverflow(): String?

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
