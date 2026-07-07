/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.dynamic

import net.postchain.rell.base.compiler.base.core.C_CompilationResult
import net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.dynamic.RellQueryFileTest.Companion.RESOURCE_DIR
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.model.rr.RR_App
import net.postchain.rell.base.model.rr.RR_QueryDefinition
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.sql.NoConnSqlExecutor
import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.base.utils.immListOf
import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.toPath
import kotlin.test.fail

/**
 * Dynamic test provider that turns Rell source files into JUnit tests, so that a compiler/library
 * test suite can be authored as a plain `.rell` file and enjoy full IDE support.
 *
 * Each `*.rell` file under [RESOURCE_DIR] becomes a test suite (the file name), and each `query`
 * inside it becomes a test case (the query name). A test passes iff invoking its query does not
 * throw; assertion failures are surfaced by the `rell.test.assert_*` functions, so the queries are
 * fully self-checking and need no expected-value plumbing on the Kotlin side.
 *
 * The queries are executed through [RellTestUtils.forCompilation] (honouring the `rell.test.backend`
 * system property) and [RellTestUtils.maybeRoundTrip] (honouring `rell.test.roundtrip`), so the
 * suite is exercised bit-identically by the `test`, `testRoundTrip` and `testTruffle` (GraalVM)
 * runs — the same three-way correctness invariant as the hand-written Kotlin tests.
 *
 * Test queries must take no parameters. Execution is pinned to a single thread because a compiled
 * app's interpreter is shared across that file's queries.
 */
@Execution(ExecutionMode.SAME_THREAD)
class RellQueryFileTest {
    @TestFactory
    fun rellQueryFiles(): List<DynamicNode> {
        val dir = (javaClass.classLoader.getResource(RESOURCE_DIR)
            ?: error("Resource directory '$RESOURCE_DIR' not found on the test classpath")).toURI().toPath()
        val files = Files.newDirectoryStream(dir, "*.rell").use { it.toList() }
            .sortedBy { it.fileName.toString() }
        check(files.isNotEmpty()) { "No *.rell test files found under resource directory '$RESOURCE_DIR'" }
        return files.map { suiteNode(it) }
    }

    private fun suiteNode(file: Path): DynamicNode {
        val suite = file.fileName.toString()
        val cRes = compile(file.readText())

        if (cRes.errors.isNotEmpty()) {
            val msg = cRes.errors.joinToString("\n") { "${it.pos} ${it.code}: ${it.text}" }
            val failing = DynamicTest.dynamicTest("<compilation>") { fail("Compilation of '$suite' failed:\n$msg") }
            return DynamicContainer.dynamicContainer(suite, listOf(failing))
        }

        val rrApp = RellTestUtils.maybeRoundTrip(cRes.rrApp!!)
        val interpreter = RellTestUtils.forCompilation(rrApp, cRes.compilationSysFns)
        val exeCtx = executionContext(rrApp, interpreter)

        val queries = rrApp.module(ModuleName.EMPTY)?.queries.orEmpty()
        check(queries.isNotEmpty()) { "No queries found in test suite '$suite'" }

        val tests = queries.entries.map { (name, query) ->
            DynamicTest.dynamicTest(name) { runQuery(suite, name, query, interpreter, exeCtx) }
        }
        return DynamicContainer.dynamicContainer(suite, tests)
    }

    private fun compile(source: String): C_CompilationResult {
        val sourceDir = C_SourceDir.mapDirOf(RellTestUtils.MAIN_FILE to source)
        val modSel = C_CompilerModuleSelection(immListOf(ModuleName.EMPTY), immListOf())
        return RellTestUtils.compileApp(sourceDir, modSel, OPTIONS)
    }

    private fun executionContext(rrApp: RR_App, interpreter: Rt_Interpreter): Rt_ExecutionContext {
        val globalCtx = Rt_GlobalContext(OPTIONS, Rt_NopPrinter, Rt_NopPrinter, typeCheck = false)
        return Rt_ExecutionContext(
            appCtx = Rt_AppContext(globalCtx, Rt_ChainContext.NULL, interpreter),
            opCtx = Rt_NullOpContext,
            sqlCtx = Rt_NullSqlContext.create(rrApp.sqlDefs),
            sqlExec = NoConnSqlExecutor,
        )
    }

    private fun runQuery(
        suite: String,
        name: String,
        query: RR_QueryDefinition,
        interpreter: Rt_Interpreter,
        exeCtx: Rt_ExecutionContext,
    ) {
        check(query.params().isEmpty()) { "Test query '$suite/$name' must take no parameters" }
        try {
            interpreter.callQuery(query, exeCtx, immListOf())
        } catch (e: Rt_Exception) {
            fail("Query '$name' failed: ${e.err.code()}: ${e.err.message()}", e)
        }
    }

    companion object {
        private const val RESOURCE_DIR = "dynamic-tests"

        // Same base options as the rest of the test suite, plus the test library so that the
        // `.rell` files can call `rell.test.assert_*`.
        private val OPTIONS: C_CompilerOptions =
            C_CompilerOptions.builder(RellTestUtils.DEFAULT_COMPILER_OPTIONS).testLib(true).build()
    }
}
