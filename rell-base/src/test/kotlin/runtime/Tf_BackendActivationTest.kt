/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.rell.base.runtime.truffle.Tf_Backend
import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.testutils.RellTestUtils
import org.junit.jupiter.api.Assertions.assertEquals
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
}
