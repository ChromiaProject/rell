/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
@file:JvmName("ProfileKt")

package net.postchain.rell.performance.profiler

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import java.awt.Desktop
import java.io.File
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.io.path.*

private const val NODE_START_TIMEOUT_SEC = 120L

class ProfileCommand : CliktCommand(name = "profile") {
    override fun help(context: com.github.ajalt.clikt.core.Context) =
        "Build chr, start the test dapp on a local node, attach async-profiler, run the " +
            "workload, and render an HTML report."

    private val users by option("--users", help = "Number of test users (default 20)").int().default(20)
    private val posts by option("--posts", help = "Posts per user (default 10)").int().default(10)
    private val profileEvent by option("--profile-event",
        help = "async-profiler event: cpu, wall, alloc, lock (default cpu)").default("cpu")

    private val mapper = jacksonObjectMapper().configure(SerializationFeature.INDENT_OUTPUT, true)
    private val httpClient: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
    private val nodeUrl = System.getenv("NODE_URL") ?: "http://localhost:7740"

    override fun run() {
        log("profile", "=== Rell End-to-End Profiler ===")

        val localProps = loadLocalProperties()

        if (!PgStats.isReady()) {
            log("profile", "WARNING: PostgreSQL not reachable at ${PgStats.url}")
            die("profile", "Start it with: ./work/psql/psql-docker.sh " +
                "(or set POSTCHAIN_DB_URL to point at an existing instance)")
        }

        val asprofAgent = ensureAsprofAgent()
        val runDir = (perfDir() / "reports").also { resetDir(it) }
        log("profile", "Output directory: $runDir")
        log("profile", "Profile event: $profileEvent")
        log("profile", "Workload: $users users, $posts posts/user")

        val chrBin = ensureChrBuilt(localProps)
        log("profile", "  chr: $chrBin")

        val versions = readChrVersions(chrBin, localProps)
        log("profile",
            "  Versions: chr=${versions["chr"] ?: "?"}, rell=${versions["rell"] ?: "?"}, " +
                "postchain=${versions["postchain"] ?: "?"}, EIF=${versions["eif"] ?: "?"}, " +
                "java=${versions["java"] ?: "?"}")

        log("profile", "Step: Collecting system info...")
        writeSystemInfo(runDir, versions, localProps["JAVA_HOME"])

        log("profile", "Step: Starting Chromia node with test dapp...")
        if (nodeIsUp(nodeUrl)) die("profile", "A node is already running on $nodeUrl — stop it first")

        val nodeLog = runDir / "node.log"
        val nodeProcess = startNode(chrBin, localProps, nodeLog)
        Runtime.getRuntime().addShutdownHook(Thread { stopNode(nodeProcess) })

        try {
            waitForNode(nodeProcess, nodeLog)

            val pid = findJvmPid(listOf("postchain", "chromia", "mainkt"))
                ?: nodeProcess.pid().toString()
            log("profile", "  JVM PID: $pid")

            val jfrPath = runDir / "profile.jfr"
            val collapsedPath = runDir / "collapsed.txt"
            val flamegraphPath = runDir / "flamegraph.html"

            log("profile", "Step: Attaching async-profiler (event=$profileEvent)...")
            val profilerControl: AsyncProfilerControl? = try {
                AsyncProfilerControl(pid, asprofAgent).also { it.start(profileEvent, jfrPath) }
            } catch (e: Throwable) {
                log("profile", "  WARNING: Could not attach: ${e.message}")
                log("profile", "  On macOS you may need to allow ptrace; on Linux see")
                log("profile", "  kernel.perf_event_paranoid in performance/README.md")
                null
            }

            PgStats.collect(runDir, suffix = "-before")

            log("profile", "Step: Running workload...")
            runWorkload(chrBin, runDir)

            log("profile", "Step: Collecting profiling data...")
            profilerControl?.use { c ->
                runCatching { c.dump("collapsed", collapsedPath) }
                    .onSuccess {
                        if (collapsedPath.exists())
                            log("profile",
                                "  ${"%-18s".format(collapsedPath.fileName)} ${"%8s".format(humanSize(collapsedPath))}  — collapsed stacks (report input)")
                    }.onFailure {
                        log("profile", "  WARNING: failed to dump collapsed: ${it.message}")
                    }
                runCatching { c.dump("flamegraph", flamegraphPath) }
                    .onSuccess {
                        if (flamegraphPath.exists())
                            log("profile",
                                "  ${"%-18s".format(flamegraphPath.fileName)} ${"%8s".format(humanSize(flamegraphPath))}  — interactive flame graph")
                    }.onFailure {
                        log("profile", "  WARNING: failed to dump flamegraph: ${it.message}")
                    }
                runCatching { c.stop() }
                if (jfrPath.exists()) {
                    log("profile",
                        "  ${"%-18s".format(jfrPath.fileName)} ${"%8s".format(humanSize(jfrPath))}  " +
                            "— JFR (openable in IntelliJ IDEA: Run > Open Profiler Snapshot)")
                }
            }

            log("profile", "  Collecting PostgreSQL statistics...")
            PgStats.collect(runDir, suffix = "-after")
            PgStats.diff(runDir)
            log("profile", "  Collected: pg-table-stats.json, pg-index-stats.json, pg-sizes.json")
        } finally {
            log("profile", "Step: Stopping node...")
            stopNode(nodeProcess)
            log("profile", "  Node stopped")
        }

        log("profile", "Step: Generating HTML report...")
        net.postchain.rell.performance.report.generateProfileReport(runDir)

        val reportPath = runDir / "report.html"
        log("profile", "")
        log("profile", "=== Profiling complete ===")
        log("profile", "  Report:   $reportPath")
        log("profile", "  Run dir:  $runDir")

        openInBrowser(reportPath)
    }

    private fun ensureAsprofAgent(): Path {
        provisionAsprof()
        return asprofAgentPath()
            ?: die("profile", "async-profiler library not found after provisioning")
    }

    private fun ensureChrBuilt(localProps: Map<String, String>): Path {
        val chrBin = repoRoot() / "chromia-cli-local" / "chromia-cli" / "target" /
            "chromia-cli-dev-dist" / "bin" / "chr"
        if (chrBin.exists()) {
            log("profile", "Step: chr binary exists, skipping build")
            return chrBin
        }

        log("profile", "Step: chr not found at $chrBin — building Rell + chromia-cli...")
        val pb = ProcessBuilder("bash", (repoRoot() / "work" / "local-chr.sh").toString(), "--version")
            .directory(repoRoot().toFile()).inheritIO()
        applyJavaHome(pb.environment(), localProps)
        val proc = pb.start()
        if (!proc.waitFor(45, TimeUnit.MINUTES)) {
            proc.destroyForcibly()
            die("profile", "Build did not finish within 45 minutes — aborted")
        }
        val rc = proc.exitValue()
        if (rc != 0) die("profile", "Build failed with exit code $rc")
        if (!chrBin.exists()) die("profile", "chr binary not found at $chrBin")
        return chrBin
    }

    private fun readChrVersions(chrBin: Path, localProps: Map<String, String>): Map<String, String> {
        val versions = LinkedHashMap<String, String>()
        try {
            val pb = ProcessBuilder(chrBin.toString(), "--version").redirectErrorStream(true)
            applyJavaHome(pb.environment(), localProps)
            val proc = pb.start()
            val text = proc.inputStream.bufferedReader().readText()
            proc.waitFor(15, TimeUnit.SECONDS)
            val regex = Regex("(?i)^(.+?) version (.+?)\\s*$")
            text.lineSequence().forEach { raw ->
                val line = raw.trim()
                regex.matchEntire(line)?.let { m ->
                    versions[m.groupValues[1].lowercase().trim()] = m.groupValues[2].trim()
                }
            }
        } catch (_: Exception) {}

        return versions
    }

    private fun startNode(chrBin: Path, localProps: Map<String, String>, nodeLog: Path): Process {
        // -XX:+EnableDynamicAgentLoading: JDK 21+ blocks dynamic JVMTI agent attach by default.
        val javaArgs = buildString {
            append(System.getenv("JAVA_ARGS") ?: "")
            append(" --enable-native-access=ALL-UNNAMED")
            append(" -XX:+EnableDynamicAgentLoading")
            append(" -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints")
        }.trim()

        val pb = ProcessBuilder(chrBin.toString(), "node", "start", "--wipe")
            .directory((perfDir() / "dapp").toFile())
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(nodeLog.toFile()))

        pb.environment()["JAVA_ARGS"] = javaArgs
        applyJavaHome(pb.environment(), localProps)
        return pb.start().also { log("profile", "  Node PID: ${it.pid()}") }
    }

    private fun stopNode(node: Process) {
        if (!node.isAlive) return
        node.destroy()

        if (!node.waitFor(10, TimeUnit.SECONDS)) {
            node.destroyForcibly()
        }
    }

    private fun waitForNode(node: Process, nodeLog: Path) {
        log("profile", "  Waiting for node to become ready...")
        val deadline = System.nanoTime() + Duration.ofSeconds(NODE_START_TIMEOUT_SEC).toNanos()
        val start = System.nanoTime()

        while (System.nanoTime() < deadline) {
            if (!node.isAlive) die("profile", "Node process exited with code ${node.exitValue()}")
            if (nodeIsUp(nodeUrl)) {
                val elapsed = (System.nanoTime() - start) / 1_000_000_000
                log("profile", "  Node ready after ${elapsed}s")
                return
            }
            Thread.sleep(1000)
        }

        log("profile", "  Last 20 lines of node log:")

        try {
            nodeLog.readLines().takeLast(20).forEach { log("profile", "    $it") }
        } catch (_: Exception) {}

        die("profile", "Node did not start within ${NODE_START_TIMEOUT_SEC}s")
    }

    private fun nodeIsUp(url: String): Boolean = try {
        val req = HttpRequest.newBuilder(URI("$url/brid/iid_0"))
            .timeout(Duration.ofSeconds(2)).GET().build()
        httpClient.send(req, HttpResponse.BodyHandlers.discarding()).statusCode() == 200
    } catch (_: Exception) { false }

    private fun runWorkload(chrBin: Path, runDir: Path) {
        WorkloadCommand().main(arrayOf(
            "--chr", chrBin.toString(),
            "--users", users.toString(),
            "--posts", posts.toString(),
            "--node-url", nodeUrl,
            "--results", (runDir / "workload-results.json").toString(),
        ))
    }

    private fun applyJavaHome(env: MutableMap<String, String>, props: Map<String, String>) {
        props["JAVA_HOME"]?.let { home ->
            require(Path.of(home).isDirectory()) { "JAVA_HOME from local.properties does not exist: $home" }
            env["JAVA_HOME"] = home
            env["PATH"] = "$home/bin${File.pathSeparator}${env["PATH"] ?: ""}"
        }
    }

    private fun writeSystemInfo(runDir: Path, versions: Map<String, String>, javaHome: String?) {
        val nf = JsonNodeFactory.instance
        val info: ObjectNode = nf.objectNode()
        info.put("timestamp", DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
        info.put("hostname", InetAddress.getLocalHost().hostName)
        info.put("os", "${System.getProperty("os.name")} ${System.getProperty("os.version")}")
        info.put("arch", System.getProperty("os.arch"))
        info.put("cpus", Runtime.getRuntime().availableProcessors())
        val maxMemBytes = totalMemoryBytes()
        info.put("memory_gib", "%.1f".format(Locale.ROOT, maxMemBytes / (1024.0 * 1024 * 1024)).toDouble())
        info.put("java_home", javaHome ?: "(system default)")
        info.put("java_version", System.getProperty("java.runtime.version") ?: "unknown")
        info.put("java_vendor", System.getProperty("java.vendor") ?: "unknown")
        info.put("java_vendor_version", System.getProperty("java.vendor.version") ?: "")
        info.put("java_vm_name", System.getProperty("java.vm.name") ?: "unknown")
        info.put("java_vm_version", System.getProperty("java.vm.version") ?: "unknown")
        info.put("async_profiler", asprofAgentPath()?.fileName?.toString() ?: "unknown")
        val versionsNode = nf.objectNode()
        versions.forEach { (k, v) -> versionsNode.put(k, v) }
        info.set<ObjectNode>("versions", versionsNode)
        info.put("profiler_event", profileEvent)
        info.put("num_users", users)
        info.put("posts_per_user", posts)
        mapper.writeValue((runDir / "system-info.json").outputStream(), info)
    }

    private fun totalMemoryBytes(): Long {
        val osBean = java.lang.management.ManagementFactory.getOperatingSystemMXBean()
        val sunBean = osBean as? com.sun.management.OperatingSystemMXBean ?: return Runtime.getRuntime().maxMemory()
        return sunBean.totalMemorySize
    }

    @OptIn(ExperimentalPathApi::class)
    private fun resetDir(dir: Path) {
        if (dir.exists()) {
            dir.deleteRecursively()
        }

        dir.createDirectories()
    }

    private fun openInBrowser(path: Path) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(path.toUri())
            }
        } catch (_: Exception) {}
    }
}

fun main(args: Array<String>) = ProfileCommand().main(args)
