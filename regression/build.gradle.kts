plugins {
    alias(libs.plugins.kotlin.jvm)
}

description = "Regression toolkit: clone external Rell projects and verify the local Rell compiler builds them."

sourceSets["main"].kotlin.setSrcDirs(listOf("src"))
sourceSets["test"].kotlin.setSrcDirs(listOf("test"))

dependencies {
    // Shared HTML report style
    implementation(projects.performance)

    implementation(libs.clikt)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.kotlin)
    implementation(libs.kotlinx.html)

    implementation(libs.testcontainers)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.testcontainers.junit.jupiter)
}

// Layout

val publicConfig = layout.projectDirectory.file("public.json").asFile
val privateConfig = layout.projectDirectory.file("private.json").asFile
val workdir = layout.projectDirectory.dir("workdir").asFile
val reportsDir = layout.projectDirectory.dir("reports").asFile

// Threading
//
// One JUnit test JVM per Test task. The @TestFactory in test/regression/RegressionTest.kt parses
// the JSON config and emits one DynamicTest per (project, backend); JUnit Jupiter's parallel-test
// extension fans them out so both backends of the same project can run concurrently. Each backend
// operates on its own working copy at `workdir/<project>-<backend>/` (mirrored from the master
// clone in `workdir/<project>/` by `refreshBackendCopy` in src/regression/compile.kt), so the two
// `chr install` calls never race on the shared `src/lib/<name>` clone tree (which would otherwise
// collide on "destination path already exists", `.git/index.lock`, and partial-tree errors).
//
// Each concurrent (project, backend) unit leases its own throw-away Testcontainers Postgres
// (`withProjectPostgres` in src/regression/compile.kt) and runs chr out-of-process via
// ProcessBuilder, so chr's JVM heap is *outside* the test JVM's heap budget. Per-slot RAM is
// dominated by the chr subprocess (~2.5 GB) plus the Postgres container (~1.2 GB) - not by what
// this JVM holds.
//
// Each (project, backend) run writes a fragment to reports/parts/; `regressionReport`
// (finalizedBy) merges them into results.json and renders the custom report.html.

// Setup tasks
//
// The chr binary is built by the shared `:performance:buildLocalChr` task (which publishes Rell to
// ~/.m2 in-graph). Cloning the target projects is regression-specific and stays
// here as a JavaExec; it runs serially because the clones share one network pipe.

val cliMain = "net.postchain.rell.regression.CliKt"
val runtimeCp = sourceSets["main"].runtimeClasspath

// The CLI asserts cwd == repo root (util.kt#repoRoot), so pin every invocation to the Rell repo
// root regardless of how the developer invoked Gradle.
val launcher21 = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(21)
}

val regressionClone by tasks.registering(JavaExec::class) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Clone every configured project into regression/workdir."
    classpath = runtimeCp
    mainClass = cliMain
    javaLauncher = launcher21
    workingDir = rootProject.projectDir
    args = listOf(
        "clone",
        "--config", publicConfig.absolutePath,
        "--config-optional", privateConfig.absolutePath,
        "--workdir", workdir.absolutePath,
        "--reports-dir", reportsDir.absolutePath,
    )
    outputs.upToDateWhen { false }
}

val regressionReport by tasks.registering(JavaExec::class) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Merge reports/parts/*.json into results.json and render report.html."
    classpath = runtimeCp
    mainClass = cliMain
    javaLauncher = launcher21
    workingDir = rootProject.projectDir
    args = listOf("report", "--reports-dir", reportsDir.absolutePath)
    outputs.upToDateWhen { false }
}

// Regression Test tasks

// `:regression:test` would be auto-wired into `:check` via the Kotlin plugin's lifecycle;
// we don't want a 40-min IT on every build.
tasks.test {
    enabled = false
}

// Root build wires every Test task as `finalizedBy(jacocoTestReport)` and `jacocoTestReport`
// `dependsOn(every Test task)` - that fan-in would pull `regressionPublic` in whenever
// `regression` ran (and vice versa). The regression sweep runs chr subprocesses, not bytecode
// the agent can attach to, so coverage is meaningless here; disable jacoco for this module.
tasks.withType<JacocoReport>().configureEach {
    enabled = false
    setDependsOn(emptyList<Any>())
}

// Custom Test tasks don't auto-wire to the `test` source set (only the plugin's default `test`
// task does). Reuse what the plugin computed there.
val defaultTest = tasks.test.get()

fun Test.configureRegressionRun(includePrivate: Boolean) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = defaultTest.testClassesDirs
    classpath = defaultTest.classpath
    workingDir = rootProject.projectDir

    systemProperty("regression.workdir", workdir.absolutePath)
    systemProperty("regression.reportsDir", reportsDir.absolutePath)
    systemProperty("regression.config.public", publicConfig.absolutePath)

    if (includePrivate)
        systemProperty("regression.config.private", privateConfig.absolutePath)

    // Per-project parallelism: each slot pays ~3.7 GB (chr subprocess ~2.5 GB + Postgres container
    // ~1.2 GB) outside this JVM. On xlarge (16c/64G) we run 8 concurrent; locally the default 4
    // suits a 16 GiB dev box. Override via -PregressionParallelism. Overrides the root build's
    // -PjunitParallelThreads fallback (set for the mainline test suites, not this one).
    val parallelism = providers.gradleProperty("regressionParallelism").orElse("1").get()
    systemProperty("junit.jupiter.execution.parallel.config.strategy", "fixed")
    systemProperty("junit.jupiter.execution.parallel.config.fixed.parallelism", parallelism)

    // Holds Testcontainers refs, JSON state, and process handles for ~parallelism slots; chr's
    // ~2.5 GB and Postgres's ~1.2 GB live outside this JVM. 4 GB has comfortable headroom for 8.
    maxHeapSize = providers.gradleProperty("regressionTestHeap").orElse("4g").get()

    // The HTML report is the verdict. A failing project shouldn't redden the build.
    ignoreFailures = true

    // Always run: external projects change.
    outputs.upToDateWhen { false }

    dependsOn(":performance:buildLocalChr", regressionClone)

    setFinalizedBy(listOf(regressionReport))
}

val regression by tasks.registering(Test::class) {
    description = "Regression sweep over every configured project (public.json + private.json if present)."
    configureRegressionRun(includePrivate = true)
}

val regressionPublic by tasks.registering(Test::class) {
    description = "Same as :regression but reads public.json only (used by the manual CI job)."
    configureRegressionRun(includePrivate = false)
}
