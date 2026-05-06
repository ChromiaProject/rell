/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
package net.postchain.rell.performance.benchmarks

import com.oracle.truffle.api.Truffle
import net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.model.rr.RR_App
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.truffle.Tf_Backend
import net.postchain.rell.base.sql.NoConnSqlExecutor
import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.base.utils.immListOf

/** Compile-and-wire-up scaffolding shared by tree-walker and Truffle benchmarks. */
abstract class RellBackendBenchmark {
    lateinit var interpreter: Rt_Interpreter
        protected set

    lateinit var exeCtx: Rt_ExecutionContext
        protected set

    public fun setUpBackend(backend: String, resourcePath: String): RR_App {
        val rellSource = loadRellResource(resourcePath)
        val sourceDir = C_SourceDir.mapDirOf(RellTestUtils.MAIN_FILE to rellSource)
        val modSel = C_CompilerModuleSelection(immListOf(ModuleName.EMPTY), immListOf())
        val cRes = RellTestUtils.compileApp(sourceDir, modSel, RellTestUtils.DEFAULT_COMPILER_OPTIONS)

        check(cRes.errors.isEmpty()) {
            "Compilation errors: ${cRes.errors.joinToString("\n") { "${it.pos} ${it.code}: ${it.text}" }}"
        }

        val rrApp = cRes.rrApp!!

        interpreter = when (backend) {
            "truffle" -> {
                check(Truffle.getRuntime().name != "Interpreted") {
                    "Truffle is using the fallback runtime — must run on GraalVM with the JVMCI compiler enabled."
                }
                Tf_Backend.forCompilation(rrApp, cRes.compilationSysFns)
            }

            else -> Rt_InterpreterImpl.forCompilation(rrApp, cRes.compilationSysFns)
        }

        val globalCtx = Rt_GlobalContext(
            RellTestUtils.DEFAULT_COMPILER_OPTIONS,
            Rt_NopPrinter,
            Rt_NopPrinter,
            typeCheck = false,
        )

        exeCtx = Rt_ExecutionContext(
            appCtx = Rt_AppContext(globalCtx, Rt_ChainContext.NULL, interpreter),
            opCtx = Rt_NullOpContext,
            sqlCtx = Rt_NullSqlContext.create(rrApp.sqlDefs),
            sqlExec = NoConnSqlExecutor,
        )

        return rrApp
    }
}

internal fun loadRellResource(path: String): String =
    RellBackendBenchmark::class.java.classLoader.getResourceAsStream(path)
        ?.bufferedReader()
        ?.use { it.readText() }
        ?: error("Benchmark resource not found on classpath: $path")
