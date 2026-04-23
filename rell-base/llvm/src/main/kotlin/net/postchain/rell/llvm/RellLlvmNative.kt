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
     * Invokes a previously JIT'd function with the given i64 arguments and returns its i64 result.
     */
    external fun callI64Function(fnPtr: Long, args: LongArray): Long
}
