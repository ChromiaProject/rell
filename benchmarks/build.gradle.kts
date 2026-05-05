import kotlinx.benchmark.gradle.BenchmarksPlugin
import kotlinx.benchmark.gradle.JvmBenchmarkTarget
import org.gradle.process.CommandLineArgumentProvider

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.kotlinx.benchmark)
}

description = "Microbenchmarks for the Rell toolchain (interpreter, etc.)"

allOpen.annotation("org.openjdk.jmh.annotations.State")

dependencies {
    implementation(libs.kotlinx.benchmark.runtime)
    implementation(projects.rellBase)
    implementation(project(":rell-base", "testArtifacts"))
    implementation(libs.kotlinx.html)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.kotlin)
    implementation(libs.kandy.lets.plot)
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
    mainClass = "net.postchain.rell.benchmarks.report.MergeBenchmarkJsonsKt"

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
    mainClass = "net.postchain.rell.benchmarks.report.ReportGeneratorKt"
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
    mainClass = "net.postchain.rell.benchmarks.SmokeFt4Kt"
}
