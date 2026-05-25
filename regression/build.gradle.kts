import groovy.json.JsonSlurper
import java.util.Properties

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

    // Each `run-one` invocation owns its Postgres for the project's entire pipeline so the
    // build *and* test phases can fan out across the Gradle worker pool.
    implementation(libs.testcontainers)
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
// aggregate `regression` / `regressionPublic`. Each task fans every (project, backend) work item
// out across Gradle's worker pool (RegressionRunWork submitted via WorkerExecutor — bounded by
// --max-workers / org.gradle.workers.max). The work action runs the full `chr install` → `chr
// build` → `chr test` pipeline end-to-end; each invocation owns its own throw-away PostgreSQL
// (Testcontainers, spun up inside the CLI), so suites no longer race on a shared schema.
//
// Each (project, backend) run goes through the single-unit CLI (`run-one`) and lands a result
// fragment under reports/parts/; `regressionReport` merges them into results.json and renders
// report.html.

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

// Docker config forwarded to every CLI subprocess so Testcontainers can reach the daemon both
// locally (DOCKER_HOST in local.properties or the parent env) and in CI (DOCKER_HOST=tcp://docker:2375
// + TESTCONTAINERS_HOST_OVERRIDE=docker, set globally by .gitlab-ci.yml). Mirrors the root build's
// Test-task forwarding (build.gradle.kts:83) so dev boxes get the same wiring without special-casing.
val rootLocalProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val dockerEnv: Map<String, String> = listOf(
    "DOCKER_HOST",
    "DOCKER_TLS_CERTDIR",
    "TESTCONTAINERS_HOST_OVERRIDE",
    "TESTCONTAINERS_RYUK_DISABLED",
    "TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE",
).mapNotNull { key ->
    val value = rootLocalProps.getProperty(key) ?: providers.environmentVariable(key).orNull
    value?.let { key to it }
}.toMap()

fun RegressionRunTask.configureRun(names: List<String>, publicOnly: Boolean) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    cliClasspath.from(runtimeCp)
    javaExecutable = javaExecProvider
    runWorkingDir = rootDirPath
    cliMainClass = cliMain
    projectNames = names
    backends = listOf("INTERPRETER", "TRUFFLE")
    sharedArgs = baseArgs(publicOnly)
    forwardedEnv = dockerEnv
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

/** Parameters for one per-project work item; the action iterates over [backends] sequentially. */
interface RegressionRunParameters: WorkParameters {
    val javaExecutable: Property<String>
    val classpath: ConfigurableFileCollection
    val mainClass: Property<String>
    val projectName: Property<String>
    val backends: ListProperty<String>
    val sharedArgs: ListProperty<String>
    val workingDir: Property<String>
    val envVars: MapProperty<String, String>
}

/**
 * Runs both backends of one project, sequentially, as forked CLI JVMs. Serialising the backends
 * within a project avoids the chr-install race: `chr install` clones library deps into the
 * project source tree (`src/lib/<name>`), and two parallel installs against the same workdir
 * collide on `git clone` ("destination path already exists", `.git/index.lock`, partial-tree
 * "no such file or directory"). Cross-project parallelism is preserved — each project still
 * gets its own worker slot. Submitted with noIsolation; exit value is ignored (a failing run is
 * recorded in its fragment, and an unexpected crash should not abort the sweep).
 */
abstract class RegressionRunWork @Inject constructor(
    private val execOps: ExecOperations,
): WorkAction<RegressionRunParameters> {
    override fun execute() {
        val name = parameters.projectName.get()
        for (backend in parameters.backends.get()) {
            execOps.javaexec {
                executable = parameters.javaExecutable.get()
                classpath = parameters.classpath
                mainClass = parameters.mainClass.get()
                args = listOf("run-one", "--name", name, "--backend", backend) +
                    parameters.sharedArgs.get()
                workingDir = File(parameters.workingDir.get())
                environment(parameters.envVars.get())
                isIgnoreExitValue = true
            }
        }
    }
}

/**
 * One task per project (and the aggregate): fan every project across the worker pool. Each work
 * item runs both backends sequentially (see [RegressionRunWork]) so chr install doesn't race
 * against itself; the worker also leases a fresh per-backend Postgres inside the CLI, so suites
 * never share schema. Always runs (external clones change out of band), so no inputs/outputs are
 * declared for up-to-date checking.
 */
abstract class RegressionRunTask: DefaultTask() {
    @get:Inject
    abstract val workers: WorkerExecutor

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

    @get:Internal
    abstract val forwardedEnv: MapProperty<String, String>

    @TaskAction
    fun run() {
        val queue = workers.noIsolation()

        for (name in projectNames.get()) {
            queue.submit(RegressionRunWork::class.java) {
                javaExecutable = this@RegressionRunTask.javaExecutable
                classpath.from(cliClasspath)
                mainClass = cliMainClass
                workingDir = runWorkingDir
                projectName.set(name)
                backends.set(this@RegressionRunTask.backends)
                sharedArgs.set(this@RegressionRunTask.sharedArgs)
                envVars.set(this@RegressionRunTask.forwardedEnv)
            }
        }

        workers.await()
    }
}
