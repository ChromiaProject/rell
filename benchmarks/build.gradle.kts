import kotlinx.benchmark.gradle.BenchmarksPlugin
import kotlinx.benchmark.gradle.JvmBenchmarkTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.kotlinx.benchmark)
}

description = "Microbenchmarks for the Rell toolchain (parsers, etc.)"

allOpen.annotation("org.openjdk.jmh.annotations.State")

// GraalVM is only required for actual JMH execution (libgraal/libjvmci). Compilation and the
// empty `:benchmarks:test` task must work on any JDK 21. The runtime assertion in
// InterpreterBenchmark (Truffle.getRuntime() check) is the real guarantee that the JVM has
// JVMCI/libgraal — toolchain vendor pinning was too brittle (Oracle GraalVM reports vendor
// "Oracle Corporation", not "GraalVM", so a strict match broke CI on the official image).

dependencies {
    implementation(libs.kotlinx.benchmark.runtime)
    implementation(projects.rellBase.frontend)
    implementation(projects.rellBase.runtimeInterpreter)
    // Truffle peer backend so InterpreterBenchmark can exercise both backends behind a JMH
    // @Param. Without this dep the bench would only see the tree-walker.
    implementation(projects.rellBase.runtimeTruffle)
    implementation(libs.truffle.api)
    implementation(projects.rellBase.testUtils)
    implementation(projects.rellToolbox.ast)
    implementation(libs.antlr.runtime)
    implementation(libs.kotlinx.html)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.kotlin)
    implementation(libs.kandy.lets.plot)
}

val prepareSamples by tasks.registering(Copy::class) {
    from(rootProject.file("rell-toolbox/indexer/src/test/resources/realWorldExamples/deathmatch/rell/src/deathmatch/main.rell")) {
        rename { "deathmatch.rell" }
    }
    from(rootProject.file("rell-toolbox/indexer/src/test/resources/realWorldExamples/share-registry-backend-vinnova/rell/src/ecosec/operations_queries_api.rell")) {
        rename { "operations_queries_api.rell" }
    }
    from(rootProject.file("profiling/dapp/src/main.rell")) {
        rename { "profiling-dapp.rell" }
    }
    into(layout.buildDirectory.dir("bench-samples/samples"))
}

sourceSets.main {
    resources.srcDir(prepareSamples.map { it.destinationDir.parentFile })
}

benchmark {
    configurations {
        named("main") {
            warmups = null
            iterations = null
            iterationTime = 2
            iterationTimeUnit = "s"
            outputTimeUnit = "ms"
            mode = "avgt"
            reportFormat = "json"
        }
    }

    targets {
        register("main") {
            this as JvmBenchmarkTarget
            jmhVersion = "1.37"
        }
    }
}

/**
 * Profile the Truffle backend with async-profiler: long-running driver, no JMH framing,
 * collapsed flame graph straight to disk. Uses the same `TruffleTraceRunner` entry point
 * but with `agentpath:libasyncProfiler` so we get one CPU sample on every method-frame.
 *
 * After running, view the flame graph: `open profiling/reports/truffle-bench.html`.
 */
val profileTruffle by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Run Truffle benchmark under async-profiler (cpu mode, collapsed flamegraph)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "net.postchain.rell.benchmarks.TruffleTraceRunnerKt"

    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(21)
    }

    val asprofLib = rootProject.file("profiling/async-profiler/lib/libasyncProfiler.dylib").absolutePath
    val outDir = rootProject.layout.projectDirectory.dir("profiling/reports").asFile
    doFirst { outDir.mkdirs() }
    val htmlOut = "$outDir/truffle-bench.html"
    val collapsedOut = "$outDir/truffle-bench.collapsed"

    environment("TRACE_REPS", "300")

    jvmArgs = listOf(
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:+EnableJVMCI",
        "-XX:+UseJVMCICompiler",
        "-Dpolyglot.engine.WarnInterpreterOnly=false",
        "-Dpolyglot.engine.AllowExperimentalOptions=true",
        "-XX:+UnlockDiagnosticVMOptions",
        "-XX:+DebugNonSafepoints",
        "-agentpath:$asprofLib=start,event=cpu,interval=500000,file=$collapsedOut",
    )
}

/**
 * Diagnostic harness: runs [net.postchain.rell.benchmarks.TruffleTraceRunnerKt] under a
 * GraalVM JDK with all the polyglot/Truffle/JVMCI flags needed for partial-evaluation
 * tracing. Output is plain stdout (one line per Truffle compilation event), so we can grep
 * for `[engine] opt done`, `[engine] opt failed`, `[engine] transferToInterpreter`,
 * deoptimisation reasons, etc., without JMH's per-iteration framing in the way.
 */
val traceTruffle by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Run Truffle benchmark with -Dpolyglot.engine.TraceCompilation=true and friends."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "net.postchain.rell.benchmarks.TruffleTraceRunnerKt"

    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(21)
    }

    // - UnlockExperimentalVMOptions: needed before flipping JVMCI flags.
    // - EnableJVMCI / UseJVMCICompiler: turn on Graal as the JIT (replaces C2). Required
    //   for Truffle's PE pipeline to engage; with stock C2, Truffle stays in the
    //   interpreter shape we keep guarding against.
    // - TraceCompilation / TraceCompilationDetails: prints one line per call-target
    //   compile/deopt with reason + node count; this is what we actually want to read.
    // - CompilationStatistics on shutdown gives a histogram of node counts and PE costs.
    // - TraceTransferToInterpreter: each `transferToInterpreterAndInvalidate()` is logged
    //   with stack trace; lets us see which nodes are forcing deopts at runtime.
    jvmArgs = listOf(
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:+EnableJVMCI",
        "-XX:+UseJVMCICompiler",
        "-Dpolyglot.engine.WarnInterpreterOnly=false",
        "-Dpolyglot.engine.AllowExperimentalOptions=true",
        "-Dpolyglot.engine.TraceCompilation=true",
        "-Dpolyglot.engine.TraceCompilationDetails=true",
        "-Dpolyglot.engine.TraceInlining=true",
        "-Dpolyglot.engine.TraceTransferToInterpreter=true",
        "-Dpolyglot.engine.CompilationStatistics=true",
        "-Dpolyglot.compiler.MaximumGraalGraphSize=600000",
        "-Dpolyglot.engine.CompilationFailureAction=Diagnose",
        "-Dpolyglot.engine.TraceTransferToInterpreter=true",
    )
}

/**
 * Smoke test: compile + run each ft4 workload once on the tree-walker interpreter, no JMH
 * framing or GraalVM requirement. Catches Rell source errors and obvious runtime breakage
 * without paying for a full benchmark cycle.
 */
val smokeFt4 by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Run each Ft4Benchmark workload once on the interpreter backend."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "net.postchain.rell.benchmarks.Ft4BenchmarkKt"
}

val benchmarkHtmlReport by tasks.registering(JavaExec::class) {
    group = BenchmarksPlugin.BENCHMARKS_TASK_GROUP
    description = "Render the latest kotlinx-benchmark JSON result as HTML."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "net.postchain.rell.benchmarks.report.ReportGeneratorKt"
    val resultsDir = layout.buildDirectory.dir("reports/benchmarks/main").get().asFile
    val outputFile = layout.buildDirectory.file("reports/benchmarks/html/report.html").get().asFile
    inputs.dir(resultsDir).withPropertyName("benchmarkJson").optional(true)
    outputs.file(outputFile).withPropertyName("htmlReport")

    argumentProviders += CommandLineArgumentProvider {
        val jsons = resultsDir.walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .sortedByDescending { it.lastModified() }

        val input = checkNotNull(jsons.firstOrNull()?.absolutePath) {
            "No kotlinx-benchmark JSON found under $resultsDir; run :benchmarks:mainBenchmark first."
        }

        listOf("--input", input, "--output", outputFile.absolutePath)
    }
}

afterEvaluate {
    tasks.getByName("mainBenchmark") {
        finalizedBy(benchmarkHtmlReport)
        (this as JavaExec).javaLauncher = javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }
}
