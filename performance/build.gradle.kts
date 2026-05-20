import kotlinx.benchmark.gradle.BenchmarksPlugin
import kotlinx.benchmark.gradle.JvmBenchmarkTarget
import org.gradle.process.CommandLineArgumentProvider

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.kotlinx.benchmark)
}

description = "Performance suite: JMH microbenchmarks and end-to-end profiler for the Rell stack"

allOpen.annotation("org.openjdk.jmh.annotations.State")

dependencies {
    implementation(libs.kotlinx.benchmark.runtime)
    implementation(projects.rellBase)
    implementation(project(":rell-base", "testArtifacts"))
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
    // flame-graph output that retains source-line attribution.
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
            // -PbenchmarkInclude="MnaBenchmark"` to scope a run to one suite.
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
    }
}

/**
 * Merge multiple kotlinx-benchmark JSON outputs into a single JSON file and render
 * the combined HTML report. Inputs are read from the `bench.merge.inputs` Gradle
 * property (comma-separated absolute paths). The merged JSON and the HTML go to
 * `build/reports/benchmarks/merged/`.
 */
val benchMergeInputs: Provider<String> = providers.gradleProperty("bench.merge.inputs")

val mergeBenchmarkJsons by tasks.registering(JavaExec::class) {
    group = BenchmarksPlugin.BENCHMARKS_TASK_GROUP
    description = "Merge multiple kotlinx-benchmark JSON results into one file."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "net.postchain.rell.performance.report.MergeBenchmarkJsonsKt"

    val mergedJson = layout.buildDirectory.file("reports/benchmarks/merged/main.json").get().asFile
    outputs.file(mergedJson).withPropertyName("mergedJson")

    val rawInputs = benchMergeInputs
    argumentProviders += CommandLineArgumentProvider {
        val raw = rawInputs.orNull
            ?: error("Set -Pbench.merge.inputs=<path1>,<path2>[,...] (comma-separated absolute paths)")
        val paths = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        require(paths.size >= 2) { "Need at least two inputs to merge; got ${paths.size}" }
        listOf("--output", mergedJson.absolutePath) + paths
    }
}

val mergedBenchmarkHtmlReport by tasks.registering(JavaExec::class) {
    group = BenchmarksPlugin.BENCHMARKS_TASK_GROUP
    description = "Render the merged kotlinx-benchmark JSON as HTML."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "net.postchain.rell.performance.report.BenchmarkReportKt"
    dependsOn(mergeBenchmarkJsons)

    val input = layout.buildDirectory.file("reports/benchmarks/merged/main.json").get().asFile
    val output = layout.buildDirectory.file("reports/benchmarks/merged/report.html").get().asFile
    inputs.file(input).withPropertyName("mergedJson")
    outputs.file(output).withPropertyName("mergedHtmlReport")

    argumentProviders += CommandLineArgumentProvider {
        listOf("--input", input.absolutePath, "--output", output.absolutePath)
    }
}

/**
 * Smoke test: compile + run each ft4 workload once on the legacy interpreter, no JMH framing.
 * Catches Rell source errors and obvious runtime breakage without paying for a full
 * benchmark cycle.
 */
val smokeFt4 by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Run each Ft4Benchmark workload once on the legacy interpreter."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "net.postchain.rell.performance.benchmarks.Ft4BenchmarkKt"
}

/** Same as `smokeFt4`, but for the mna-blockchain workloads in MnaBenchmark. */
val smokeMna by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Run each MnaBenchmark workload once on the legacy interpreter."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "net.postchain.rell.performance.benchmarks.MnaBenchmarkKt"
}

/** Same as `smokeFt4`, but for the cross-codebase struct workloads in StructBenchmark. */
val smokeStruct by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Run each StructBenchmark workload once on the legacy interpreter."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "net.postchain.rell.performance.benchmarks.StructBenchmarkKt"
}

val smokeAll by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Run every benchmark workload once on the legacy interpreter."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "net.postchain.rell.performance.benchmarks.SmokeAllKt"
}

// ─── End-to-end profiler tasks (Kotlin port of the legacy `profiling/` Python suite) ────
//
// `profile`        — orchestrate build / node / async-profiler / workload / HTML report
// `workload`       — generate transactions and queries against a running node
// `provisionAsprof`— download async-profiler for the current OS/arch

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
    // Sampling-accuracy flags:
    //  - DebugNonSafepoints: emit debug info at every site (not just safepoint polls), so
    //    async-profiler attributes samples to the *actual* leaf instead of the next safepoint.
    //  - PreserveFramePointer: keep RBP as the frame pointer so async-profiler's stack unwind
    //    sees correct caller chains.
    //  - UnlockDiagnosticVMOptions: unlocks DebugNonSafepoints.
    //
    // Hot-loop framing:
    //  - dontinline,*ProfileSampleHotLoop.runOnce*: keep `runOnce` as a real frame at sample
    //    time. With C2 aggressive inlining the rep loop folds into a single mega-frame
    //    and the profile loses the per-call call site.
    jvmArgs = listOf(
        "-XX:+UnlockDiagnosticVMOptions",
        "-XX:+DebugNonSafepoints",
        "-XX:+PreserveFramePointer",
        "-XX:CompileCommand=dontinline,net/postchain/rell/performance/profiler/ProfileSampleHotLoop.runOnce",
    )
}

val profile by tasks.registering(JavaExec::class) {
    group = "performance"
    description = "End-to-end profiler: build chr, start node, attach async-profiler, run workload, render report."
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
