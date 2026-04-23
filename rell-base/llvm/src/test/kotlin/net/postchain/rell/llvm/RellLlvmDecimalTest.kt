/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.llvm

import net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.model.rr.RR_App
import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.serialization.serializeRellApp
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Proves that decimal arithmetic runs NATIVELY through the LLVM backend with NO JVM call-back.
 *
 * The chain: compile a `decimal`-typed function → serialize the RR_App → JIT it via
 * [RellLlvmNative.compileDecimalFunctionByIndex] (the C++ [DecimalLowerer] emits an `i8*(i8**)`
 * function whose body is calls to the `extern "C" rell_num_decimal_*` op glue) → invoke it with
 * operand strings. The arithmetic is computed entirely in C++ by the faithful OpenJDK BigDecimal
 * port (`rell::num`); `rell_sysfn_call` / the interpreter are never reached. This is the first
 * stdlib family for which "JNI calling back is not needed" holds end-to-end through the backend.
 */
class RellLlvmDecimalTest {

    @Test
    fun `decimal add runs natively via rell_num ops, no JVM callback`() {
        val rrApp = compile("function add(a: decimal, b: decimal): decimal = a + b;")
        val fnPtr = jitDecimal(rrApp, "add")

        // Each result is computed by native rell::num::decimal_add — the JIT'd code never re-enters
        // the JVM. Results are the Rell stripped-canonical form.
        assertEquals("2.8", RellLlvmNative.callDecimalFunction(fnPtr, arrayOf("2.5", "0.3")))
        assertEquals("5", RellLlvmNative.callDecimalFunction(fnPtr, arrayOf("2", "3")))
        assertEquals("-1.5", RellLlvmNative.callDecimalFunction(fnPtr, arrayOf("0.5", "-2")))
        assertEquals("1000000.0000000001",
            RellLlvmNative.callDecimalFunction(fnPtr, arrayOf("1000000", "0.0000000001")))
    }

    @Test
    fun `decimal multiply-subtract chain runs natively`() {
        val rrApp = compile("function f(a: decimal, b: decimal, c: decimal): decimal = a * b - c;")
        val fnPtr = jitDecimal(rrApp, "f")
        // 2.5 * 4 - 3 = 7
        assertEquals("7", RellLlvmNative.callDecimalFunction(fnPtr, arrayOf("2.5", "4", "3")))
        // 1.5 * 1.5 - 0.25 = 2
        assertEquals("2", RellLlvmNative.callDecimalFunction(fnPtr, arrayOf("1.5", "1.5", "0.25")))
    }

    @Test
    fun `decimal divide rounds HALF_UP to scale 20, natively`() {
        val rrApp = compile("function d(a: decimal, b: decimal): decimal = a / b;")
        val fnPtr = jitDecimal(rrApp, "d")
        assertEquals("0.33333333333333333333", RellLlvmNative.callDecimalFunction(fnPtr, arrayOf("1", "3")))
    }

    private fun jitDecimal(rrApp: RR_App, fnName: String): Long {
        val fn = rrApp.module(ModuleName.EMPTY)!!.functions[fnName]!!
        val index = rrApp.allFunctions.indexOf(fn)
        check(index >= 0) { "function '$fnName' not found in allFunctions" }
        val ptr = RellLlvmNative.compileDecimalFunctionByIndex(serializeRellApp(rrApp), index)
        check(ptr != 0L) { "decimal function '$fnName' was not JIT-compiled (soft-failed to 0)" }
        return ptr
    }

    private fun compile(code: String): RR_App {
        val sourceDir = C_SourceDir.mapDirOf(RellTestUtils.MAIN_FILE to code)
        val modSel = C_CompilerModuleSelection(immListOf(ModuleName.EMPTY), immListOf())
        val cRes = RellTestUtils.compileApp(sourceDir, modSel, RellTestUtils.DEFAULT_COMPILER_OPTIONS)
        check(cRes.errors.isEmpty()) { "Compilation errors: ${cRes.errors.map { it.code }}" }
        return checkNotNull(cRes.rrApp) { "compileApp returned null RR_App" }
    }
}
