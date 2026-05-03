import kotlinx.benchmark.gradle.BenchmarksPlugin
import kotlinx.benchmark.gradle.JvmBenchmarkTarget
import org.gradle.process.CommandLineArgumentProvider

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.kotlinx.benchmark)
}

description = "Microbenchmarks for the Rell toolchain (parsers, etc.)"

allOpen.annotation("org.openjdk.jmh.annotations.State")

dependencies {
    implementation(libs.kotlinx.benchmark.runtime)
    implementation(projects.rellBase.frontend)
    implementation(projects.rellBase.runtimeInterpreter)
    implementation(projects.rellBase.testUtils)
    implementation(projects.rellToolbox.ast)
    implementation(libs.antlr.runtime)
    implementation(libs.kotlinx.html)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.kotlin)
    implementation(libs.kandy.lets.plot)
//    runtimeOnly(libs.slf4j.simple)
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
    kotlin.setSrcDirs(listOf("src"))
}

benchmark {
    configurations {
        named("main") {
            warmups = 3
            iterations = 5
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
