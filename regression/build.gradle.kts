import groovy.json.JsonSlurper

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

// ─── Layout ───────────────────────────────────────────────────────────────────────────────────

val publicConfig = layout.projectDirectory.file("public.json").asFile
val privateConfig = layout.projectDirectory.file("private.json").asFile
val workdir = layout.projectDirectory.dir("workdir").asFile
val reportsDir = layout.projectDirectory.dir("reports").asFile

// ─── Threading model ────────────────────────────────────────────────────────────────────────────
//
// Gradle is the single controller of parallelism. The build script parses the JSON configs and
// generates one task per project (regressionFt4Lib, regressionDirectoryChain, …) plus the
// aggregate `regression` / `regressionPublic`. Each task:
//   1. fans `chr install` + `chr build` out across Gradle's worker pool (RegressionBuildWork
//      submitted via WorkerExecutor — bounded by --max-workers / org.gradle.workers.max), then
//   2. runs `chr test` for every project strictly serially, because every suite lands on the same
//      local PostgreSQL instance and concurrent runs would race on shared schemas.
// The old hand-rolled Executors pool + REGRESSION_COMPILE_JOBS env var are gone — the worker count
// is now just Gradle's worker budget.
//
// Each (project, backend) run goes through the single-unit CLI (`build-one` / `test-one`) and lands
// a result fragment under reports/parts/; `regressionReport` merges them into results.json and
// renders report.html.

// Build-script-side project list: only the names are needed to wire the tasks. The CLI re-reads the
// JSON for the full ProjectSpec. Reading the files here registers them as configuration-cache inputs,
// so editing public.json/private.json invalidates the cached task graph.
fun projectNames(file: File): List<String> {
    if (!file.exists()) return emptyList()
    @Suppress("UNCHECKED_CAST")
    val root = JsonSlurper().parseText(file.readText()) as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    val projects = (root["projects"] as? List<Map<String, Any?>>).orEmpty()
    return projects.mapNotNull { it["name"] as? String }.distinct()
}

val publicNames = projectNames(publicConfig)
val privateNames = projectNames(privateConfig)
// public wins on a name clash, mirroring loadProjects() in the CLI (public config passed first).
val allNames = (publicNames + privateNames).distinct()

// "ft4-lib" → "Ft4Lib", "directory-chain" → "DirectoryChain", "postchain-eif" → "PostchainEif".
fun taskSuffix(name: String): String =
    name.split('-', '_', '.', ' ').filter { it.isNotEmpty() }.joinToString("") { it.replaceFirstChar(Char::uppercase) }

// ─── Shared run wiring ──────────────────────────────────────────────────────────────────────────

val cliMain = "net.postchain.rell.regression.CliKt"
val runtimeCp = sourceSets["main"].runtimeClasspath
val rootDirPath: String = rootProject.projectDir.absolutePath

// The CLI asserts cwd == repo root (util.kt#repoRoot), so pin every invocation to the Rell repo
// root regardless of how the developer invoked Gradle.
val launcher21 = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
val javaExecProvider = launcher21.map { it.executablePath.asFile.absolutePath }

// Shared CLI options appended after each subcommand. `publicOnly` drops the optional private.json
// so the CI flavour never depends on a file that is gitignored and never reaches the runner.
fun baseArgs(publicOnly: Boolean): List<String> {
    val args = mutableListOf("--config", publicConfig.absolutePath)
    if (!publicOnly) args += listOf("--config-optional", privateConfig.absolutePath)
    args += listOf("--workdir", workdir.absolutePath, "--reports-dir", reportsDir.absolutePath)
    return args
}

// ─── Setup tasks ─────────────────────────────────────────────────────────────────────────────────
//
// The chr binary is built by the shared `:performance:buildLocalChr` task (which publishes Rell to
// ~/.m2 in-graph — no nested Gradle). Cloning the target projects is regression-specific and stays
// here; it runs serially because the clones share one network pipe.

val regressionClone by tasks.registering(JavaExec::class) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Clone every configured project into regression/workdir (serial — one network pipe)."
    classpath = runtimeCp
    mainClass = cliMain
    javaLauncher = launcher21
    workingDir = rootProject.projectDir
    args = listOf("clone") + baseArgs(publicOnly = false)
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

// ─── Per-project + aggregate run tasks ───────────────────────────────────────────────────────────

fun RegressionRunTask.configureRun(names: List<String>, publicOnly: Boolean) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    cliClasspath.from(runtimeCp)
    javaExecutable = javaExecProvider
    runWorkingDir = rootDirPath
    cliMainClass = cliMain
    projectNames = names
    backends = listOf("INTERPRETER", "TRUFFLE")
    sharedArgs = baseArgs(publicOnly)
    dependsOn(":performance:buildLocalChr", regressionClone)
    finalizedBy(regressionReport)
    outputs.upToDateWhen { false }
}

allNames.forEach { name ->
    tasks.register<RegressionRunTask>("regression${taskSuffix(name)}") {
        description = "Regression pipeline (install/build/test, both backends) for project '$name'."
        configureRun(listOf(name), publicOnly = false)
    }
}

val regression by tasks.registering(RegressionRunTask::class) {
    description = "Clone, build (parallel) and test (serial) every configured project; render the report."
    configureRun(allNames, publicOnly = false)
}

// CI uses a public-only flavour — `private.json` is gitignored and never lands on the runner.
val regressionPublic by tasks.registering(RegressionRunTask::class) {
    description = "Same as :regression but reads public.json only (used by the manual CI job)."
    configureRun(publicNames, publicOnly = true)
}

// ─── Worker plumbing ────────────────────────────────────────────────────────────────────────────

/** Parameters for one `build-one` invocation submitted to the Gradle worker pool. */
interface RegressionBuildParameters: WorkParameters {
    val javaExecutable: Property<String>
    val classpath: ConfigurableFileCollection
    val mainClass: Property<String>
    val args: ListProperty<String>
    val workingDir: Property<String>
}

/**
 * Runs one project's build phase (`build-one --name … --backend …`) as a forked CLI JVM. Submitted
 * with noIsolation so Gradle schedules these across its worker leases; the chr build itself is a
 * separate subprocess, so the orchestration JVM doesn't need stronger isolation. Exit value is
 * ignored: a project that fails to compile is recorded in its result fragment (CLI exits 0), and an
 * unexpected crash should not abort the rest of the sweep.
 */
abstract class RegressionBuildWork @Inject constructor(
    private val execOps: ExecOperations,
): WorkAction<RegressionBuildParameters> {
    override fun execute() {
        execOps.javaexec {
            executable = parameters.javaExecutable.get()
            classpath = parameters.classpath
            mainClass = parameters.mainClass.get()
            args = parameters.args.get()
            workingDir = File(parameters.workingDir.get())
            isIgnoreExitValue = true
        }
    }
}

/**
 * One task per project (and the aggregate): fan the build phase across the worker pool, await it,
 * then run the test phase strictly serially in this single-threaded task action — the serialisation
 * the shared PostgreSQL instance requires. Always runs (external clones change out of band), so no
 * inputs/outputs are declared for up-to-date checking.
 */
abstract class RegressionRunTask: DefaultTask() {
    @get:Inject
    abstract val workers: WorkerExecutor

    @get:Inject
    abstract val execOps: ExecOperations

    @get:Internal
    abstract val cliClasspath: ConfigurableFileCollection

    @get:Internal
    abstract val javaExecutable: Property<String>

    @get:Internal
    abstract val cliMainClass: Property<String>

    @get:Internal
    abstract val runWorkingDir: Property<String>

    @get:Internal
    abstract val sharedArgs: ListProperty<String>

    @get:Internal
    abstract val projectNames: ListProperty<String>

    @get:Internal
    abstract val backends: ListProperty<String>

    @TaskAction
    fun run() {
        // Build phase — parallel across the worker pool.
        val queue = workers.noIsolation()

        for (name in projectNames.get()) for (backend in backends.get()) {
            queue.submit(RegressionBuildWork::class.java) {
                javaExecutable = this@RegressionRunTask.javaExecutable
                classpath.from(cliClasspath)
                mainClass = cliMainClass
                workingDir = runWorkingDir
                args = listOf("build-one", "--name", name, "--backend", backend) + sharedArgs.get()
            }
        }

        workers.await()

        // Test phase — strictly serial; every suite shares one PostgreSQL instance.
        for (name in projectNames.get()) for (backend in backends.get()) {
            execOps.javaexec {
                executable = javaExecutable.get()
                classpath = cliClasspath
                mainClass = cliMainClass.get()
                args = listOf("test-one", "--name", name, "--backend", backend) + sharedArgs.get()
                workingDir = File(runWorkingDir.get())
                isIgnoreExitValue = true
            }
        }
    }
}
