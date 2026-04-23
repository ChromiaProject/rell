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
import net.postchain.rell.llvm.Llvm_Backend
import net.postchain.rell.base.sql.NoConnSqlExecutor
import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.base.utils.immListOf
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.TearDown
import java.io.File

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

            "llvm" -> {
                // RellLlvmNative.System.load reads an absolute path from `rell.llvm.libpath`. JMH forks
                // a clean JVM that does NOT inherit the parent's -D props, so we self-resolve the path
                // from a classpath resource (staged by :performance:writeLlvmLibPath from the built
                // librell-llvm) and set the property in this fork before the first JIT call.
                if (System.getProperty("rell.llvm.libpath").isNullOrBlank()) {
                    val libPath = RellBackendBenchmark::class.java.classLoader
                        .getResource("rell-llvm-libpath.txt")?.readText()?.trim()
                    checkNotNull(libPath?.takeIf { it.isNotEmpty() }) {
                        "rell-llvm-libpath.txt not on the benchmark classpath — :rell-base:llvm:buildNativeLibrary " +
                            "must run and :performance:writeLlvmLibPath must stage the path resource."
                    }
                    System.setProperty("rell.llvm.libpath", libPath)
                }
                Llvm_Backend.forCompilation(rrApp, cRes.compilationSysFns)
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

    /**
     * After a trial, if this run used the LLVM backend, record its one-time JIT compile cost to a
     * side file so the HTML report can render it as a bar distinct from the JMH execution score.
     *
     * JMH only reports steady-state execution time (ms/op); the compile overhead happens once on
     * the first call and is invisible in the score. [Llvm_Backend.jitCompileNanos] captures it.
     * `backend` and `sample` live on the concrete subclass as `@Param` fields, so they're read
     * reflectively here; the line is keyed by the JMH benchmark identity (`class.method`) plus the
     * sample so the renderer can join it back to the matching `llvm` result.
     */
    @TearDown(Level.Trial)
    fun recordLlvmCompileCost() {
        val backend = readStringField("backend") ?: return
        if (backend != "llvm") return
        val llvm = (interpreter as? Llvm_Backend) ?: return
        val sample = readStringField("sample") ?: "—"
        // class.method identity matching the JMH JSON `benchmark` field (sans the trailing method
        // — the renderer joins on benchmark+sample, and the JSON uses the fully-qualified method).
        val benchId = javaClass.name + ".runQuery"

        val outDir = File("build/reports/benchmarks/main")
        outDir.mkdirs()
        val line = """{"benchmark":"$benchId","sample":"$sample",""" +
            """"compileNanos":${llvm.jitCompileNanos},"jitHits":${llvm.jitHits},"jitMisses":${llvm.jitMisses}}"""
        File(outDir, "llvm-compile.jsonl").appendText(line + "\n")
    }

    // JMH runs against a generated `*_jmhType` subclass, so `@Param` fields like `backend`/`sample`
    // live on a superclass — walk the hierarchy to find them.
    private fun readStringField(name: String): String? {
        var cls: Class<*>? = javaClass
        while (cls != null) {
            try {
                return cls.getDeclaredField(name).also { it.isAccessible = true }.get(this) as? String
            } catch (_: NoSuchFieldException) {
                cls = cls.superclass
            }
        }
        return null
    }
}

internal fun loadRellResource(path: String): String =
    RellBackendBenchmark::class.java.classLoader.getResourceAsStream(path)
        ?.bufferedReader()
        ?.use { it.readText() }
        ?: error("Benchmark resource not found on classpath: $path")
