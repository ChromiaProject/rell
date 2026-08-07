/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.llvm

import net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.runtime.Rt_AppContext
import net.postchain.rell.base.runtime.Rt_BooleanValue
import net.postchain.rell.base.runtime.Rt_ChainContext
import net.postchain.rell.base.runtime.Rt_ExecutionContext
import net.postchain.rell.base.runtime.Rt_GlobalContext
import net.postchain.rell.base.runtime.Rt_Interpreter
import net.postchain.rell.base.runtime.Rt_NopPrinter
import net.postchain.rell.base.runtime.Rt_NullOpContext
import net.postchain.rell.base.runtime.Rt_NullSqlContext
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.sql.NoConnSqlExecutor
import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.base.utils.immListOf
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Proves the value-ABI path is BOTH reachable for non-integer (boolean) functions AND
 * bit-exact with the interpreter — i.e. the old `args.all { it is Rt_IntValue }` gate on
 * [Llvm_Backend.callFunction] is gone (a boolean arg/result is no longer forced down the
 * interpreter fallback) and the native value entry actually computes the right answer.
 *
 * ## Why a boolean function
 *
 * The native lowerer accepts boolean as a fully-lowerable inline type (the 0/1 i64-slot envelope;
 * `isIntOrBoolType` in `jni_bridge.cpp` / the value-ABI type gate). `function f(a: boolean, b:
 * boolean): boolean = a and not b;` is the simplest body that is genuinely lowerable end-to-end
 * (short-circuit AND + NOT, both inline) AND carries a non-[net.postchain.rell.base.runtime.Rt_IntValue]
 * arg/result — its args are
 * [Rt_BooleanValue]. Under the old i64-only filter this delegated (jitHits stays 0). With the gate
 * removed and the value ABI wired it takes the native path and must equal the interpreter.
 *
 * ## Strictness — no UnsatisfiedLinkError escape hatch
 *
 * This test deliberately does NOT tolerate an [UnsatisfiedLinkError]: the C++ value-ABI exports
 * (`compileFunctionExtended` / `callValueFunction`) are required to be present and correct. A
 * boolean function that is fully lowerable MUST be JIT'd (`jitHits > 0`, `jitMisses` unchanged) and
 * its result MUST equal the interpreter's. If the native side were missing, the link error would
 * fail the test rather than masking a regression as a "pass".
 *
 * Decimal native is proven separately and just as strictly by
 * [RellLlvmDecimalTest] (the pure-decimal `compileDecimalFunctionByIndex` /
 * `callDecimalFunction` path runs decimal arithmetic entirely in native C++, with NO JVM
 * callback, and asserts the JIT result equals the interpreter); this test owns the boolean
 * value-ABI strictness and that one owns decimal.
 */
class Llvm_ValueAbiGateTest {
    @Test
    fun `fully-lowerable boolean function is JITed and matches the interpreter`() {
        val (backend, exeCtx) = setup("function f(a: boolean, b: boolean): boolean = a and not b;")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["f"]!!
        // a=true, b=false -> a and not b -> true and true -> true.
        val arg: List<Rt_Value> = listOf(Rt_BooleanValue.get(true), Rt_BooleanValue.get(false))

        // Interpreter reference (the wrapped Rt_InterpreterImpl), via the public unwrap hook.
        val interp = backend.unwrapInterpreterImpl() as Rt_Interpreter
        val expected = interp.callFunction(fn, exeCtx, arg, dbUpdateAllowed = false)
        assertEquals(true, (expected as Rt_BooleanValue).value)

        val hitsBefore = backend.jitHits
        val missesBefore = backend.jitMisses

        // No try/catch: the native value ABI MUST be linked. A missing symbol fails the test.
        val result = backend.callFunction(fn, exeCtx, arg, dbUpdateAllowed = false)

        // The non-integer function was JIT'd via the value path and matches the interpreter.
        assertEquals(expected.value, (result as Rt_BooleanValue).value)
        assertTrue(backend.jitHits > hitsBefore, "boolean function should have taken the JIT value path")
        assertEquals(missesBefore, backend.jitMisses, "boolean function must not fall back to the interpreter")
    }

    @Suppress("SameParameterValue")
    private fun setup(code: String): Pair<Llvm_Backend, Rt_ExecutionContext> {
        val sourceDir = C_SourceDir.mapDirOf(RellTestUtils.MAIN_FILE to code)
        val modSel = C_CompilerModuleSelection(immListOf(ModuleName.EMPTY), immListOf())
        val cRes = RellTestUtils.compileApp(sourceDir, modSel, RellTestUtils.DEFAULT_COMPILER_OPTIONS)
        check(cRes.errors.isEmpty()) { "Compilation errors: ${cRes.errors.map { it.code }}" }
        val rrApp = checkNotNull(cRes.rrApp) { "compileApp returned null RR_App" }

        val backend = Llvm_Backend.forCompilation(rrApp, cRes.compilationSysFns)

        val globalCtx = Rt_GlobalContext(
            RellTestUtils.DEFAULT_COMPILER_OPTIONS,
            Rt_NopPrinter,
            Rt_NopPrinter,
            typeCheck = false,
        )

        val appCtx = Rt_AppContext(globalCtx, Rt_ChainContext.NULL, backend)
        val sqlCtx = Rt_NullSqlContext.create(rrApp.sqlDefs)
        val exeCtx = Rt_ExecutionContext(appCtx, Rt_NullOpContext, sqlCtx, NoConnSqlExecutor)
        return backend to exeCtx
    }
}
