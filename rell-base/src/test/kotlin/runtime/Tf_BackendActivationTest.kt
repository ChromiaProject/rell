/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.runtime.truffle.Tf_Backend
import net.postchain.rell.base.runtime.truffle.Tf_Language
import net.postchain.rell.base.runtime.truffle.Tf_PolyglotBootstrap
import net.postchain.rell.base.runtime.truffle.values.Tf_StructShapeRegistry
import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.testutils.RellTestUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test

/**
 * Sanity check that the `rell.test.backend` selector actually swaps the runtime backend.
 *
 * Without this guard, a regression in [RellTestUtils.forCompilation] (the test-utils router
 * that picks between the tree-walker and Truffle based on the system property) could silently
 * route every "Truffle" run through `Rt_InterpreterImpl` — returning correct answers but
 * invalidating the differential-coverage value of the third test pass.
 *
 * The test asserts that the [Rt_Interpreter] constructed via [RellTestUtils.forCompilation]
 * matches the requested backend when the system property selects Truffle. The trivial
 * function tests exist to give the differential test runner cheap surface area that runs on
 * both backends.
 */
class Tf_BackendActivationTest : BaseRellTest(useSql = false) {

    @Test fun testFactoryMatchesSelectedBackend() {
        // Compile a trivial program; the resulting RR_App is what the factory consumes.
        val sourceDir = net.postchain.rell.base.compiler.base.utils.C_SourceDir.mapDirOf(
            RellTestUtils.MAIN_FILE to "function f() = 1;",
        )
        val cRes = RellTestUtils.compileApp(
            sourceDir,
            net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection(
                net.postchain.rell.base.utils.immListOf(net.postchain.rell.base.model.ModuleName.EMPTY),
                net.postchain.rell.base.utils.immListOf(),
            ),
            RellTestUtils.DEFAULT_COMPILER_OPTIONS,
        )
        val rrApp = checkNotNull(cRes.rrApp) { "Compilation failed: ${cRes.errors}" }

        val interpreter = RellTestUtils.forCompilation(rrApp, cRes.compilationSysFns)
        when (RellTestUtils.BACKEND) {
            "truffle" -> assertTrue(
                interpreter is Tf_Backend,
                "rell.test.backend=truffle but factory returned ${interpreter::class.qualifiedName}",
            )
            else -> assertTrue(
                interpreter !is Tf_Backend,
                "rell.test.backend=${RellTestUtils.BACKEND} but factory returned a Tf_Backend",
            )
        }
    }

    @Test fun testTrivialFunctionsProduceIdenticalResults() {
        // Trivial values; the differential coverage comes from this class running under both
        // the default `test` task and the new `testTruffle` task.
        chkFn("= 1 + 2;", "int[3]")
        chkFn("= \"hello\";", "text[hello]")
        chkFn("= [1, 2, 3];", "list<integer>[int[1],int[2],int[3]]")
        chkFnFull(
            "function f(): integer { var s = 0; for (i in range(10)) { s += i; } return s; }",
            "int[45]",
        )
    }

    @Test fun testBackendValueIsRecognised() {
        assertEquals(true, RellTestUtils.BACKEND in setOf("interpreter", "truffle"))
    }

    /**
     * Confirms the polyglot framework discovers `Tf_LanguageProvider` (via the
     * `META-INF/services/com.oracle.truffle.api.provider.TruffleLanguageProvider` resource) and
     * populates `Tf_Language.POLYGLOT_INSTANCE`. SOM struct shapes silently fall back to
     * `Rt_HeapStruct` when this fails, so an explicit assertion guards against packaging
     * regressions (e.g. the resources directory not being included in the test classpath).
     *
     * Skipped under the interpreter backend: SOM is Truffle-only.
     */
    @Test fun testPolyglotBootstrapPopulatesLanguageInstance() {
        if (RellTestUtils.BACKEND != "truffle") return
        val language = Tf_PolyglotBootstrap.ensure()
        assertNotNull(
            language,
            "polyglot bootstrap returned null — Tf_LanguageProvider service not discovered" +
                "; lastFailure=${Tf_PolyglotBootstrap.lastFailure?.let { it::class.qualifiedName + ": " + it.message }}",
        )
        assertSame(language, Tf_Language.POLYGLOT_INSTANCE.get())
    }

    /**
     * End-to-end SOM activation: compiles a struct, builds a `Tf_StructShape` via the registry,
     * and verifies a concrete shape is returned (not the heap-fallback `null`). Catches any
     * regression that breaks the bootstrap → `StaticShape.newBuilder` chain — `StaticShape` walks
     * the language's polyglot instance internally and silently NPE-trips the registry's catch
     * block when the language isn't framework-managed.
     */
    @Test fun testStructShapeRegistryReturnsRealShape() {
        if (RellTestUtils.BACKEND != "truffle") return
        val sourceDir = net.postchain.rell.base.compiler.base.utils.C_SourceDir.mapDirOf(
            RellTestUtils.MAIN_FILE to "struct s { x: integer; y: text; }",
        )
        val cRes = RellTestUtils.compileApp(
            sourceDir,
            net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection(
                net.postchain.rell.base.utils.immListOf(net.postchain.rell.base.model.ModuleName.EMPTY),
                net.postchain.rell.base.utils.immListOf(),
            ),
            RellTestUtils.DEFAULT_COMPILER_OPTIONS,
        )
        val rrApp = checkNotNull(cRes.rrApp) { "Compilation failed: ${cRes.errors}" }
        val structIndex = rrApp.allStructs.indexOfFirst { it.struct.name == "s" }
        assertTrue(structIndex >= 0, "struct 's' not in rrApp")
        val rrType = RR_Type.Struct(structIndex)
        val shape = Tf_StructShapeRegistry.shapeFor(rrApp, rrType)
        assertNotNull(shape, "SOM shape construction returned null — SOM fallback engaged unexpectedly")
        assertEquals(listOf("x", "y"), shape!!.attrNames)
    }
}
