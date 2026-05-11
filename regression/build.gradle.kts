plugins {
    alias(libs.plugins.kotlin.jvm)
}

description = "Regression toolkit: clone external Rell projects and verify the local Rell compiler builds them."

sourceSets["main"].kotlin.setSrcDirs(listOf("src"))

dependencies {
    // Shared HTML report style
    implementation(projects.performance)

    implementation(libs.clikt)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.kotlin)
    implementation(libs.kotlinx.html)
}

// ─── Tasks ──────────────────────────────────────────────────────────────────────────────
//
// regressionClone   — clone every project listed in the JSON configs into workdir/
// regressionCompile — for each cloned project, run `chr install` against the local Rell
//                     build, captured per-project logs + duration + exit code
// regressionReport  — render reports/report.html from the captured results JSON
// regression        — convenience composite: clone + compile + report
// regressionPublic  — same as `regression` but reads only public.json (used by the CI job)
//
// Each task is JavaExec around the CLI in src/main/kotlin/regression/
// Invoke the CLI directly via `./gradlew :regression:regression --args=…`.

val publicConfig = layout.projectDirectory.file("public.json").asFile
val privateConfig = layout.projectDirectory.file("private.json").asFile
val workdir = layout.projectDirectory.dir("workdir").asFile
val reportsDir = layout.projectDirectory.dir("reports").asFile

fun JavaExec.configureRegression(subcommand: String, publicOnly: Boolean = false) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "net.postchain.rell.regression.CliKt"
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
    // local-chr.sh asserts cwd == repo root (it greps for ./build.gradle.kts / ./README.md),
    // and the Kotlin CLI mirrors that assumption via util.kt#repoRoot. Pin every task here so
    // a `./gradlew :regression:foo` always runs from the right place regardless of how the
    // developer invoked Gradle.
    workingDir = rootProject.projectDir
    val baseArgs = mutableListOf(subcommand, "--config", publicConfig.absolutePath)
    if (!publicOnly) baseArgs += arrayOf("--config-optional", privateConfig.absolutePath)

    baseArgs += arrayOf(
        "--workdir", workdir.absolutePath,
        "--reports-dir", reportsDir.absolutePath,
    )

    args = baseArgs
}

val regressionClone by tasks.registering(JavaExec::class) {
    description = "Clone every project in public.json (and private.json if present) into regression/workdir."
    configureRegression("clone")
}

val regressionCompile by tasks.registering(JavaExec::class) {
    description = "Bootstrap chr once, then run `chr install` against every cloned project; writes results.json."
    configureRegression("compile")
}

val regressionReport by tasks.registering(JavaExec::class) {
    description = "Render reports/report.html from regression/reports/results.json."
    configureRegression("report")
}

val regression by tasks.registering(JavaExec::class) {
    description = "End-to-end: clone every project, run chr against each, render an HTML report."
    configureRegression("all")
}

// CI uses a public-only flavour — `private.json` is gitignored and never lands on the runner.
// Exposed as a separate Gradle task so the CI yaml stays declarative: no env-coupling or
// shell-side flag plumbing required.
val regressionPublic by tasks.registering(JavaExec::class) {
    description = "Same as :regression but reads public.json only (used by the manual CI job)."
    configureRegression("all", publicOnly = true)
}
