import kotlinx.benchmark.gradle.BenchmarksPlugin
import kotlinx.benchmark.gradle.JvmBenchmarkTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.kotlinx.benchmark)
}

description = "Performance suite: JMH microbenchmarks and end-to-end profiler for the Rell stack"

allOpen.annotation("org.openjdk.jmh.annotations.State")

// GraalVM is only required for actual JMH execution (libgraal/libjvmci). Compilation and the
// empty `:performance:test` task must work on any JDK 21. The runtime assertion in
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
    // CLI entry points for the end-to-end profiler (profile / workload / provision / report).
    implementation(libs.clikt)
    // JDBC driver for the PostgreSQL-stat collector (postchain-bom platform applied by the root
    // build picks the version).
    implementation(libs.postgresql)
    // In-process `one.profiler.AsyncProfiler` for the `profile-sample` CLI.
    implementation(libs.async.profiler)
    // `one.convert.Main` re-renders the JFR file produced by async-profiler into a collapsed /
    // flame-graph output that retains source-line attribution — essential for the butterfly
    // view, where the LLM needs the actual call-site line, not just the method name.
    implementation(libs.jfr.converter)
}

val prepareSamples by tasks.registering(Copy::class) {
    description = "Stages real-world Rell sources from rell-toolbox fixtures and the local profiling dapp as " +
            "ParserBenchmark inputs."

    group = "build"

    from(
        rootProject.file(
            "rell-toolbox/indexer/src/test/resources/realWorldExamples/deathmatch/rell/src/deathmatch/main.rell",
        ),
    ) {
        rename { "deathmatch.rell" }
    }

    from(
        rootProject.file(
            "rell-toolbox/indexer/src/test/resources/" +
                    "realWorldExamples/share-registry-backend-vinnova/rell/src/ecosec/operations_queries_api.rell",
        ),
    ) {
        rename { "operations_queries_api.rell" }
    }

    from(layout.projectDirectory.file("dapp/src/main.rell")) {
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
            // Optional regex filter — invoke as `./gradlew :performance:mainBenchmark
            // -PbenchmarkInclude="MnaBenchmark"` to scope a run to one suite. The kotlinx-benchmark
            // plugin lacks a -P override, so the filter is wired here as a project property.
            (project.findProperty("benchmarkInclude") as? String)?.let { include(it) }
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
 * Diagnostic harness: runs TruffleTraceRunnerKt
 * under a GraalVM JDK with all the polyglot/Truffle/JVMCI flags needed for partial-evaluation
 * tracing. Output is plain stdout (one line per Truffle compilation event), so we can grep
 * for `engine opt done`, `engine opt failed`, `engine transferToInterpreter`,
 * deoptimisation reasons, etc., without JMH's per-iteration framing in the way.
 */
val traceTruffle by tasks.registering(JavaExec::class) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Run Truffle benchmark with -Dpolyglot.engine.TraceCompilation=true and friends."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "net.postchain.rell.performance.benchmarks.TruffleTraceRunnerKt"

    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(21)
    }

    // - UnlockExperimentalVMOptions: needed before flipping JVMCI flags.
    // - EnableJVMCI / UseJVMCICompiler: turn on Graal as the JIT (replaces C2). Required
    //   for Truffle's PE pipeline to engage.
    // - TraceCompilation / TraceCompilationDetails: prints one line per call-target
    //   compile/deopt with reason + node count; this is what we actually want to read.
    // - CompilationStatistics on shutdown gives a histogram of node counts and PE costs.
    // - TraceTransferToInterpreter: each `transferToInterpreterAndInvalidate()` is logged with stack trace;.
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
    mainClass = "net.postchain.rell.performance.benchmarks.Ft4BenchmarkKt"
}

/** Same as `smokeFt4`, but for the mna-blockchain workloads in MnaBenchmark. */
val smokeMna by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Run each MnaBenchmark workload once on the interpreter backend."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "net.postchain.rell.performance.benchmarks.MnaBenchmarkKt"
}

/** Same as `smokeFt4`, but for the cross-codebase struct workloads in StructBenchmark. */
val smokeStruct by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Run each StructBenchmark workload once on the interpreter backend."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "net.postchain.rell.performance.benchmarks.StructBenchmarkKt"
}

val benchmarkHtmlReport by tasks.registering(JavaExec::class) {
    group = BenchmarksPlugin.BENCHMARKS_TASK_GROUP
    description = "Render the latest kotlinx-benchmark JSON result as HTML."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "net.postchain.rell.performance.report.BenchmarkReportKt"
    val resultsDir = layout.buildDirectory.dir("reports/benchmarks/main").get().asFile
    val outputFile = layout.buildDirectory.file("reports/benchmarks/html/report.html").get().asFile
    inputs.dir(resultsDir).withPropertyName("benchmarkJson").optional(true)
    outputs.file(outputFile).withPropertyName("htmlReport")

    argumentProviders += CommandLineArgumentProvider {
        val jsons = resultsDir.walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .sortedByDescending { it.lastModified() }

        val input = checkNotNull(jsons.firstOrNull()?.absolutePath) {
            "No kotlinx-benchmark JSON found under $resultsDir; run :performance:mainBenchmark first."
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

// ─── End-to-end profiler tasks (Kotlin port of the legacy `profiling/` Python suite) ────
//
// `profile`        — orchestrate build / node / async-profiler / workload / HTML report
// `workload`       — generate transactions and queries against a running node
// `provisionAsprof`— download async-profiler for the current OS/arch
//
// Each task uses the same JavaExec wiring as the smoke tasks above so the user can
// pass `--args` from the command line. The CLI parsing lives in the Clikt commands.

val provisionAsprof by tasks.registering(JavaExec::class) {
    group = "performance"
    description = "Download async-profiler into performance/async-profiler for the current OS."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "net.postchain.rell.performance.profiler.ProvisionKt"
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
}

val workload by tasks.registering(JavaExec::class) {
    group = "performance"
    description = "Run the test-workload generator against a Chromia node (see --help)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "net.postchain.rell.performance.profiler.WorkloadKt"
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
}

val profileSample by tasks.registering(JavaExec::class) {
    group = "performance"
    description = "Profile a single sample query under async-profiler in-process; emits flat/tree/butterfly/collapsed text."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "net.postchain.rell.performance.profiler.ProfileSampleKt"
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
    // -Djdk.attach.allowAttachSelf=true is unnecessary because AsyncProfiler.getInstance()
    // uses System.load on the bundled native lib (no Attach-API self-attach involved).
    //
    // Sampling-accuracy flags:
    //  - DebugNonSafepoints: emit debug info at every site (not just safepoint polls), so
    //    async-profiler attributes samples to the *actual* leaf instead of the next safepoint.
    //    Without it, hot tight loops get charged to the wrong method.
    //  - PreserveFramePointer: keep RBP as the frame pointer so async-profiler's stack unwind
    //    (which uses libunwind / FP walking, not Java stackmaps) sees correct caller chains.
    //  - UnlockDiagnosticVMOptions: unlocks DebugNonSafepoints.
    //
    // Hot-loop framing:
    //  - dontinline,*ProfileSampleHotLoop.runOnce*: keep `runOnce` as a real frame at sample
    //    time. With C2/Graal aggressive inlining the rep loop folds into a single mega-frame
    //    and the profile loses the per-call call site.
    jvmArgs = listOf(
        "-XX:+UnlockDiagnosticVMOptions",
        "-XX:+DebugNonSafepoints",
        "-XX:+PreserveFramePointer",
        "-XX:CompileCommand=dontinline,net/postchain/rell/performance/profiler/ProfileSampleHotLoop.runOnce",
    )
}

val buildLocalChr by tasks.registering(JavaExec::class) {
    group = LifecycleBasePlugin.BUILD_GROUP
    description = "Build the local chr binary against the freshly published Rell snapshot (replaces local-chr.sh)."
    dependsOn(":publishRellToMavenLocal")
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "net.postchain.rell.performance.chr.BuildLocalChrKt"
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
    workingDir = rootProject.projectDir
    // Rell version for the chromia-cli POM patch; `-PchrRebuild` forces a clean re-clone.
    val rellVersion = project.version.toString()
    val rebuild = providers.gradleProperty("chrRebuild").map { listOf("--rebuild") }.orElse(emptyList())

    argumentProviders += CommandLineArgumentProvider {
        listOf(rellVersion) + rebuild.get()
    }

    // External clones + Maven state change out of band
    outputs.upToDateWhen { false }
}

val profile by tasks.registering(JavaExec::class) {
    group = "performance"
    description = "End-to-end profiler: build chr, start node, attach async-profiler, run workload, render report."
    dependsOn(buildLocalChr)
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "net.postchain.rell.performance.profiler.ProfileKt"
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
}

val regenerateProfileReport by tasks.registering(JavaExec::class) {
    group = "performance"
    description = "Regenerate the profile HTML from existing performance/reports/ run data."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "net.postchain.rell.performance.report.Profile_reportKt"
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
    args(layout.projectDirectory.dir("reports").asFile.absolutePath)
}
