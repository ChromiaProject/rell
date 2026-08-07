/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.llvm

import net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.model.rr.RR_EnumAttr
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.runtime.Rt_AppContext
import net.postchain.rell.base.runtime.Rt_BooleanValue
import net.postchain.rell.base.runtime.Rt_ChainContext
import net.postchain.rell.base.runtime.Rt_ExecutionContext
import net.postchain.rell.base.runtime.Rt_GlobalContext
import net.postchain.rell.base.runtime.Rt_IntValue
import net.postchain.rell.base.runtime.Rt_NopPrinter
import net.postchain.rell.base.runtime.Rt_NullOpContext
import net.postchain.rell.base.runtime.Rt_NullSqlContext
import net.postchain.rell.base.runtime.Rt_RR_EnumValue
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.sql.NoConnSqlExecutor
import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.base.utils.immListOf
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * C1 closure — NATIVE inline enum values, bit-exact vs the interpreter.
 *
 * Enums now cross the JNI boundary inline: `value.cpp::from_jvm` cracks an `Rt_RR_EnumValue` to the
 * `ENUM` tag (`payload.i64` = `Int` ordinal, `scale` = enum-type index) via `Llvm_SysBridge.enumCrack`,
 * and `to_jvm` reboxes the EXACT `Rt_RR_EnumValue` via `Llvm_SysBridge.enumValue` (the same
 * construction the interpreter uses for an `RR_ConstantValue.Enum`). `lower_ops.cpp` lowers
 * `==`/`!=` as an ordinal `icmp` and `<`/`>`/`<=`/`>=` as a SIGNED ordinal `icmp` — bit-exact with
 * `rt_ops.kt` `R_CmpType_Enum` (`rrAttr.value.compareTo`) and `Rt_RR_EnumValue.equals` (ordinal +
 * type name), since both operands are statically the SAME enum type. `lower_expr.cpp` lowers enum
 * constants to the inline `ENUM` value and dispatches `when` over an enum key by ordinal.
 *
 * Each test pins jitHits>0 / jitMisses==0 AND that the result equals what the interpreter produces.
 * The TWO-enum tests prove the enum-type index round-trips correctly (returning the wrong enum type
 * would fail the `equals` / cross-type assertions). Mirrors [Llvm_DecimalBigintCmpTest].
 */
class Llvm_EnumCoverageTest {
    private val enumDecls = """
        enum Color { RED, GREEN, BLUE }
        enum Suit { CLUBS, DIAMONDS, HEARTS, SPADES }
    """.trimIndent()

    @Test
    fun `enum equality is native and bit-exact`() {
        val (backend, exeCtx) = setup("$enumDecls\nfunction eq(a: Color, b: Color): boolean = a == b;")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["eq"]!!

        assertEquals(true, callBool(backend, exeCtx, fn, color(backend, "RED"), color(backend, "RED")))
        assertEquals(false, callBool(backend, exeCtx, fn, color(backend, "RED"), color(backend, "BLUE")))
        assertEquals(true, callBool(backend, exeCtx, fn, color(backend, "BLUE"), color(backend, "BLUE")))
        assertEquals(3, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `enum inequality is native and bit-exact`() {
        val (backend, exeCtx) = setup("$enumDecls\nfunction ne(a: Color, b: Color): boolean = a != b;")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["ne"]!!

        assertEquals(false, callBool(backend, exeCtx, fn, color(backend, "GREEN"), color(backend, "GREEN")))
        assertEquals(true, callBool(backend, exeCtx, fn, color(backend, "RED"), color(backend, "GREEN")))
        assertEquals(2, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `enum ordering follows ordinal and is native`() {
        // Color order: RED(0) < GREEN(1) < BLUE(2). The native signed ordinal icmp must match
        // rt_ops.kt R_CmpType_Enum (rrAttr.value.compareTo).
        val (backend, exeCtx) = setup(
            "$enumDecls\n" +
                "function lt(a: Color, b: Color): boolean = a < b;\n" +
                "function gt(a: Color, b: Color): boolean = a > b;\n" +
                "function le(a: Color, b: Color): boolean = a <= b;\n" +
                "function ge(a: Color, b: Color): boolean = a >= b;"
        )
        val mod = backend.rrApp.module(ModuleName.EMPTY)!!
        val lt = mod.functions["lt"]!!
        val gt = mod.functions["gt"]!!
        val le = mod.functions["le"]!!
        val ge = mod.functions["ge"]!!

        val red = color(backend, "RED")
        val green = color(backend, "GREEN")
        val blue = color(backend, "BLUE")

        assertEquals(true, callBool(backend, exeCtx, lt, red, green))   // 0 < 1
        assertEquals(false, callBool(backend, exeCtx, lt, blue, green)) // 2 < 1
        assertEquals(true, callBool(backend, exeCtx, gt, blue, red))    // 2 > 0
        assertEquals(true, callBool(backend, exeCtx, le, green, green)) // 1 <= 1
        assertEquals(false, callBool(backend, exeCtx, le, blue, red))   // 2 <= 0
        assertEquals(true, callBool(backend, exeCtx, ge, green, green)) // 1 >= 1
        assertEquals(true, callBool(backend, exeCtx, ge, blue, red))    // 2 >= 0

        assertEquals(7, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `when over an enum key is native and bit-exact`() {
        // when with multiple arms + else; the key dispatch matches by ordinal.
        val (backend, exeCtx) = setup(
            "$enumDecls\n" +
                "function rank(c: Color): integer {\n" +
                "    return when (c) {\n" +
                "        Color.RED -> 10;\n" +
                "        Color.GREEN -> 20;\n" +
                "        else -> 99;\n" +
                "    };\n" +
                "}"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["rank"]!!

        assertEquals(10L, callInt(backend, exeCtx, fn, color(backend, "RED")))
        assertEquals(20L, callInt(backend, exeCtx, fn, color(backend, "GREEN")))
        assertEquals(99L, callInt(backend, exeCtx, fn, color(backend, "BLUE")))  // else arm
        assertEquals(3, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `enum constant compared against param is native`() {
        // The enum literal Color.BLUE lowers to an inline ENUM value; comparing it against a param
        // exercises the constant path. (The equality type gate reads the param operand's static
        // type, which is the enum; the constant lowers to the matching inline ENUM carrier.)
        val (backend, exeCtx) = setup(
            "$enumDecls\nfunction isBlue(c: Color): boolean { return when (c) { Color.BLUE -> true; else -> false; }; }"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["isBlue"]!!

        assertEquals(true, callBool(backend, exeCtx, fn, color(backend, "BLUE")))
        assertEquals(false, callBool(backend, exeCtx, fn, color(backend, "RED")))
        assertEquals(2, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `enum-typed param round-trips through the value ABI`() {
        // Pass an enum in, return it: the result must equal the input (from_jvm cracked it to the
        // ENUM tag, to_jvm reboxed the EXACT canonical Rt_RR_EnumValue from (typeIdx, ordinal)).
        val (backend, exeCtx) = setup("$enumDecls\nfunction id(c: Color): Color = c;")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["id"]!!

        for (name in listOf("RED", "GREEN", "BLUE")) {
            val input = color(backend, name)
            val result = backend.callFunction(fn, exeCtx, listOf(input))
            assertEquals(input, result)  // bit-exact identity round-trip
            assertEquals(name, (result as Rt_RR_EnumValue).rrAttr.nameStr)
        }
        assertEquals(3, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `two distinct enum types round-trip to the correct type`() {
        // A Color-returning and a Suit-returning identity function. If the enum-type index were
        // dropped on rebox (always allEnums[0]), the Suit round-trip would return a Color value and
        // the equals (which keys on the type name) would fail.
        val (backend, exeCtx) = setup(
            "$enumDecls\nfunction idColor(c: Color): Color = c;\nfunction idSuit(s: Suit): Suit = s;"
        )
        val mod = backend.rrApp.module(ModuleName.EMPTY)!!
        val idColor = mod.functions["idColor"]!!
        val idSuit = mod.functions["idSuit"]!!

        // Same ordinal (0) in both enums: RED and CLUBS. A type-index bug would make these collide.
        val red = color(backend, "RED")
        val clubs = suit(backend, "CLUBS")

        val colorOut = backend.callFunction(idColor, exeCtx, listOf(red))
        val suitOut = backend.callFunction(idSuit, exeCtx, listOf(clubs))

        assertEquals(red, colorOut)
        assertEquals(clubs, suitOut)
        // The two ordinal-0 values are NOT equal: they are different enum types (Rt_RR_EnumValue
        // .equals compares the type name). Proves the type index round-trips, not just the ordinal.
        assertEquals(false, colorOut == suitOut)
        assertEquals(2, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    // ---- helpers ----------------------------------------------------------------------

    private fun color(backend: Llvm_Backend, attr: String): Rt_Value = enumVal(backend, "Color", attr)
    @Suppress("SameParameterValue")
    private fun suit(backend: Llvm_Backend, attr: String): Rt_Value = enumVal(backend, "Suit", attr)

    /** Build the canonical input enum Rt_Value the same way the backend reboxes one (see Llvm_Backend.reboxEnum). */
    private fun enumVal(backend: Llvm_Backend, enumSimpleName: String, attrName: String): Rt_Value {
        val typeIdx = backend.rrApp.allEnums.indexOfFirst { it.base.simpleName == enumSimpleName }
        check(typeIdx >= 0) { "enum $enumSimpleName not found in app" }
        val rtType = backend.resolveType(RR_Type.Enum(typeIdx))
        val attr: RR_EnumAttr = backend.rrApp.allEnums[typeIdx].attr(attrName)
            ?: error("attr $attrName not found in enum $enumSimpleName")
        return Rt_RR_EnumValue(rtType, attr)
    }

    private fun callBool(
        backend: Llvm_Backend,
        exeCtx: Rt_ExecutionContext,
        fn: net.postchain.rell.base.model.rr.RR_FunctionDefinition,
        vararg args: Rt_Value,
    ): Boolean = (backend.callFunction(fn, exeCtx, args.toList()) as Rt_BooleanValue).value

    private fun callInt(
        backend: Llvm_Backend,
        exeCtx: Rt_ExecutionContext,
        fn: net.postchain.rell.base.model.rr.RR_FunctionDefinition,
        vararg args: Rt_Value,
    ): Long = (backend.callFunction(fn, exeCtx, args.toList()) as Rt_IntValue).value

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
