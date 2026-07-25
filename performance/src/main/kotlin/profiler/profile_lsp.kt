/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
@file:JvmName("ProfileLspKt")

package net.postchain.rell.performance.profiler

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import com.github.ajalt.clikt.parameters.types.path
import net.postchain.rell.performance.report.renderLspReport
import java.awt.Desktop
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.nio.file.Path
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.TimeUnit
import kotlin.io.path.*
import one.convert.Arguments as JfrConverterArgs
import one.convert.Main as JfrConverterMain

/**
 * Profile the Rell language server's startup against a real workspace, twice:
 *
 *  - **cold** — the index cache directory is wiped, so `initialize` walks, parses, and
 *    compiles every file, then persists the Fory index cache;
 *  - **hot** — the same session is repeated against the cache written by the cold run,
 *    exercising the cache-deserialization path a returning user hits on every restart.
 *
 * Each run launches the language-server shadow JAR in a child JVM with async-profiler
 * attached from JVM start (`-agentpath` — the same provisioned library the end-to-end
 * profiler uses) and drives a minimal LSP session over stdio:
 * `initialize` → `initialized` → quiesce → `shutdown`/`exit`. Workspace indexing runs
 * inside the `initialize` request, so "time to initialize response" is the startup cost
 * an editor user experiences.
 *
 * Outputs per run: `profile-<run>.jfr`, `collapsed-<run>.txt`, `flamegraph-<run>.html`,
 * `milestones-<run>.json`; plus a shared `system-info.json` and the combined
 * `report.html` rendered with the shared report style.
 */
class ProfileLspCommand : CliktCommand(name = "profile-lsp") {
    override fun help(context: Context) =
        "Profile language-server startup (cold and hot index cache) and render an HTML report."

    private val serverJar by option(
        "--server-jar",
        help = "Path to the language-server shadow JAR (the Gradle task passes it automatically).",
    ).path(mustExist = true).required()

    private val workspace by option(
        "--workspace",
        help = "Workspace folder to open, e.g. a checkout of ft4-lib. Defaults to the largest " +
            "in-repo real-world example so the task runs in CI without external checkouts.",
    ).path().default(
        repoRoot() / "rell-toolbox" / "indexer" / "src" / "test" / "resources" /
            "realWorldExamples" / "alice-mysterious-seed",
    )

    private val outputDir by option(
        "--output-dir",
        help = "Directory for all artifacts (default: performance/reports/lsp-startup).",
    ).path().default(perfDir() / "reports" / "lsp-startup")

    private val intervalMs by option(
        "--interval-ms", help = "Wall-clock sampling interval in milliseconds (default: 2).",
    ).long().default(2)

    private val initializeTimeoutSec by option(
        "--initialize-timeout", help = "Seconds to wait for the initialize response (default: 600).",
    ).int().default(600)

    private val quiesceSec by option(
        "--quiesce", help = "Seconds of LSP silence treated as \"startup finished\" (default: 5).",
    ).int().default(5)

    private val mapper = jacksonObjectMapper()

    override fun run() {
        val ws = workspace.toAbsolutePath().normalize()
        require(ws.isDirectory()) { "Workspace does not exist: $ws" }

        val outDir = outputDir.toAbsolutePath().normalize().also { it.createDirectories() }
        provisionAsprof()
        val agentLib = asprofAgentPath() ?: die("profile-lsp", "async-profiler library not found after provisioning")

        // The index cache honors XDG_CACHE_HOME, which gives us hermetic cold/hot control
        // without touching the user's real ~/.cache/rell-language-server.
        val cacheHome = outDir / "cache-home"
        resetDir(cacheHome)

        val log4jConfig = writeLog4jOverride(outDir)

        log("profile-lsp", "server jar: $serverJar")
        log("profile-lsp", "workspace:  $ws")

        runSession("cold", outDir, ws, agentLib, log4jConfig, cacheHome)
        awaitCacheFile(cacheHome)
        runSession("hot", outDir, ws, agentLib, log4jConfig, cacheHome)

        writeSystemInfo(outDir)

        val report = outDir / "report.html"
        renderLspReport(outDir)
        log("profile-lsp", "report: $report")
        openInBrowser(report)
    }

    // ─── One profiled LSP session ────────────────────────────────────────────────────────

    private fun runSession(
        run: String,
        outDir: Path,
        ws: Path,
        agentLib: Path,
        log4jConfig: Path,
        cacheHome: Path,
    ): SessionMilestones {
        log("profile-lsp", "── $run run ──")
        val jfrFile = outDir / "profile-$run.jfr"

        val javaBin = Path(System.getProperty("java.home")) / "bin" / "java"
        val cmd = listOf(
            javaBin.absolutePathString(),
            // loglevel=NONE keeps the agent's "Profiling started" banner off stdout, which
            // carries LSP frames.
            "-agentpath:${agentLib.absolutePathString()}=start,event=wall,interval=${intervalMs}ms," +
                "jfr,file=${jfrFile.absolutePathString()},loglevel=NONE",
            "-Dlog4j2.configurationFile=${log4jConfig.absolutePathString()}",
            "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
            "-jar", serverJar.absolutePathString(),
        )

        val pb = ProcessBuilder(cmd)
        pb.environment()["XDG_CACHE_HOME"] = cacheHome.absolutePathString()
        pb.redirectError(ProcessBuilder.Redirect.appendTo((outDir / "server-stderr-$run.log").toFile()))
        val spawnedAt = System.nanoTime()
        val proc = pb.start()
        fun elapsed(): Double = (System.nanoTime() - spawnedAt) / 1e9

        val milestones = SessionMilestones(run = run)
        val client = LspStdioClient(proc.inputStream, proc.outputStream) {
            if (milestones.firstByteSec == null) milestones.firstByteSec = elapsed()
        }
        try {
            client.sendRequest(INITIALIZE_ID, "initialize", initializeParams(ws))
            val deadline = System.nanoTime() + initializeTimeoutSec * 1_000_000_000L
            while (client.initializeResponseAt == null) {
                if (!proc.isAlive) die("profile-lsp", "$run: server died before initialize response; " +
                    "see server-stderr-$run.log")
                if (System.nanoTime() > deadline) {
                    proc.destroyForcibly()
                    die("profile-lsp", "$run: timed out after ${initializeTimeoutSec}s waiting for initialize response")
                }
                Thread.sleep(20)
            }
            milestones.initializeResponseSec = (client.initializeResponseAt!! - spawnedAt) / 1e9

            client.sendNotification("initialized", mapper.createObjectNode())

            val capNanos = System.nanoTime() + 120 * 1_000_000_000L

            // The server answers initialize before the workspace is indexed, and is silent while it
            // indexes, so silence alone would look like "startup finished" seconds before any
            // diagnostic is published. Wait for the indexing progress to end first; a server that
            // never reports it falls through to the quiesce below once the cap expires.
            while (System.nanoTime() < capNanos && client.indexingEndAt == null) {
                Thread.sleep(50)
            }
            milestones.indexingCompleteSec = client.indexingEndAt?.let { (it - spawnedAt) / 1e9 }

            // Quiesce: startup is over once the server has been silent for --quiesce seconds.
            val quiesceNanos = quiesceSec * 1_000_000_000L
            while (System.nanoTime() < capNanos) {
                if (System.nanoTime() - client.lastActivityNanos > quiesceNanos) break
                Thread.sleep(100)
            }
            milestones.quiescentSec = elapsed() - quiesceSec

            milestones.diagnosticsFiles = client.diagnosticsFiles
            milestones.lastDiagnosticSec = client.lastDiagnosticAt?.let { (it - spawnedAt) / 1e9 }

            client.sendRequest(SHUTDOWN_ID, "shutdown", null)
            Thread.sleep(300)
            client.sendNotification("exit", null)
        } finally {
            if (!proc.waitFor(20, TimeUnit.SECONDS)) {
                proc.destroyForcibly()
                proc.waitFor(10, TimeUnit.SECONDS)
            }
        }
        milestones.exitSec = (System.nanoTime() - spawnedAt) / 1e9
        milestones.exitCode = proc.exitValue()

        convertJfr(jfrFile, outDir / "collapsed-$run.txt", "collapsed")
        convertJfr(jfrFile, outDir / "flamegraph-$run.html", "html")
        mapper.writerWithDefaultPrettyPrinter()
            .writeValue((outDir / "milestones-$run.json").toFile(), milestones)
        mapper.writerWithDefaultPrettyPrinter()
            .writeValue((outDir / "diagnostics-$run.json").toFile(), client.diagnosticsByUri)

        log("profile-lsp", "$run: initialize→response %.2f s, workspace indexed %.2f s, %d files diagnosed".formatRoot(
            milestones.initializeResponseSec ?: -1.0,
            milestones.indexingCompleteSec ?: -1.0,
            milestones.diagnosticsFiles))
        return milestones
    }

    private fun initializeParams(ws: Path): ObjectNode {
        val nf = JsonNodeFactory.instance
        val params = nf.objectNode()
        params.put("processId", ProcessHandle.current().pid())
        params.set<ObjectNode>("clientInfo", nf.objectNode().put("name", "rell-lsp-startup-profiler"))
        params.set<ObjectNode>("capabilities", nf.objectNode().apply {
            set<ObjectNode>("workspace", nf.objectNode().put("workspaceFolders", true))
            set<ObjectNode>("textDocument", nf.objectNode().set("publishDiagnostics", nf.objectNode()))
        })
        params.set<ObjectNode>("initializationOptions", nf.objectNode().put("indexCaching", true))
        val folders = nf.arrayNode()
        folders.add(nf.objectNode().put("uri", ws.toUri().toString().removeSuffix("/")).put("name", ws.name))
        params.set<ObjectNode>("workspaceFolders", folders)
        return params
    }

    /**
     * The cold run persists the index cache on a scheduled executor (initial delay 0, then
     * every minute) — the file normally exists by the time the session quiesces, but the
     * write races process shutdown, so wait for it explicitly before starting the hot run.
     */
    private fun awaitCacheFile(cacheHome: Path) {
        val cacheDir = cacheHome / "chromia" / "rell-language-server"
        val deadline = System.nanoTime() + 30 * 1_000_000_000L
        while (System.nanoTime() < deadline) {
            val cached = cacheDir.takeIf { it.isDirectory() }
                ?.useDirectoryEntries { entries -> entries.filter { it.extension == "cache" }.toList() }
                ?: emptyList()
            if (cached.isNotEmpty()) {
                cached.forEach { log("profile-lsp", "index cache: ${it.name} (${humanSize(it)})") }
                return
            }
            Thread.sleep(200)
        }
        die("profile-lsp", "cold run did not persist an index cache under $cacheDir — " +
            "the hot run would silently re-measure a cold start")
    }

    // ─── Artifacts ───────────────────────────────────────────────────────────────────────

    /**
     * The shadow JAR's bundled log4j2.properties routes the root logger to a console appender
     * (stdout — which carries LSP frames) and a Sentry appender whose plugin fails to resolve
     * under a bare `java -jar` launch. Override with an equivalent config that logs to stderr
     * and the usual rolling file, keeping the appender names `initializeLogger` looks up.
     */
    private fun writeLog4jOverride(outDir: Path): Path {
        val file = outDir / "log4j2-profiling.properties"
        file.writeText(
            $$"""
            status=warn
            name=LspStartupProfilerLogger
            appender.rolling.type=RollingFile
            appender.rolling.name=RollingFile
            appender.rolling.fileName=${sys:java.io.tmpdir}/rell-language-server/lsp.log
            appender.rolling.filePattern=${sys:java.io.tmpdir}/rell-language-server/lsp-%i.log
            appender.rolling.layout.type=PatternLayout
            appender.rolling.layout.pattern=%-5level %d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %c{1} - %msg%n
            appender.rolling.policies.type=Policies
            appender.rolling.policies.size.type=SizeBasedTriggeringPolicy
            appender.rolling.policies.size.size=10MB
            appender.rolling.strategy.type=DefaultRolloverStrategy
            appender.rolling.strategy.max=10
            appender.console.type=Console
            appender.console.name=ConsoleLogger
            appender.console.target=SYSTEM_ERR
            appender.console.layout.type=PatternLayout
            appender.console.layout.pattern=%-5level %d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %c{1} - %msg%n
            rootLogger.level=info
            rootLogger.appenderRefs=console
            rootLogger.appenderRef.console.ref=ConsoleLogger
            """.trimIndent(),
        )
        return file
    }

    private fun convertJfr(jfrFile: Path, output: Path, format: String) {
        val args = JfrConverterArgs("--output", format, "--wall", "--total", "--dot", "--norm")
        JfrConverterMain.convert(jfrFile.absolutePathString(), output.absolutePathString(), args)
        output.writeText(normalizeFrames(output.readText()))
    }

    private fun writeSystemInfo(outDir: Path) {
        val nf = JsonNodeFactory.instance
        val info = nf.objectNode()
        info.put("timestamp", DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
        info.put("hostname", InetAddress.getLocalHost().hostName)
        info.put("os", "${System.getProperty("os.name")} ${System.getProperty("os.version")}")
        info.put("arch", System.getProperty("os.arch"))
        info.put("cpus", Runtime.getRuntime().availableProcessors())
        val osBean = java.lang.management.ManagementFactory.getOperatingSystemMXBean()
        val memBytes = (osBean as? com.sun.management.OperatingSystemMXBean)?.totalMemorySize
            ?: Runtime.getRuntime().maxMemory()
        info.put("memory_gib", "%.1f".formatRoot(memBytes / (1024.0 * 1024 * 1024)).toDouble())
        info.put("java_home", System.getProperty("java.home"))
        info.put("java_version", System.getProperty("java.runtime.version") ?: "unknown")
        info.put("java_vendor", System.getProperty("java.vendor") ?: "unknown")
        info.put("java_vendor_version", System.getProperty("java.vendor.version") ?: "")
        info.put("java_vm_name", System.getProperty("java.vm.name") ?: "unknown")
        info.put("java_vm_version", System.getProperty("java.vm.version") ?: "unknown")
        info.put("server_jar", serverJar.fileName.toString())
        info.put("workspace", workspace.toAbsolutePath().normalize().toString())
        info.put("profiler_event", "wall")
        info.put("interval_ms", intervalMs)
        mapper.writeValue((outDir / "system-info.json").toFile(), info)
    }

    @OptIn(ExperimentalPathApi::class)
    private fun resetDir(dir: Path) {
        if (dir.exists()) dir.deleteRecursively()
        dir.createDirectories()
    }

    private fun openInBrowser(path: Path) {
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(path.toUri())
        } catch (_: Exception) {}
    }

    private fun String.formatRoot(vararg args: Any?): String = format(Locale.ROOT, *args)

    companion object {
        private const val INITIALIZE_ID = 1
        private const val SHUTDOWN_ID = 99
    }
}

/** Milestone timestamps for one profiled session, in seconds since process spawn. */
data class SessionMilestones(
    val run: String,
    var firstByteSec: Double? = null,
    var initializeResponseSec: Double? = null,
    var lastDiagnosticSec: Double? = null,
    var quiescentSec: Double? = null,
    var exitSec: Double? = null,
    var indexingCompleteSec: Double? = null,
    var diagnosticsFiles: Int = 0,
    var exitCode: Int? = null,
)

/**
 * Minimal JSON-RPC-over-stdio LSP client — just enough protocol to drive a startup.
 *
 * The header scanner is deliberately tolerant: stray library banners can precede the first
 * frame on stdout (e.g. kotlin-logging's initialization notice), so `Content-Length` is
 * located anywhere in the accumulated pre-frame bytes rather than required to open them.
 * Server→client requests are acknowledged with a `null` result, which satisfies everything
 * the server asks during startup (`client/registerCapability` etc.).
 */
/** Work-done progress token the language server reports workspace indexing under. */
private const val INDEXING_PROGRESS_TOKEN = "rell-indexing"

internal class LspStdioClient(
    private val input: InputStream,
    private val output: OutputStream,
    private val onFirstByte: () -> Unit,
) {
    private val mapper = jacksonObjectMapper()

    @Volatile var initializeResponseAt: Long? = null; private set
    @Volatile var lastActivityNanos: Long = System.nanoTime(); private set
    @Volatile var diagnosticsFiles: Int = 0; private set
    @Volatile var lastDiagnosticAt: Long? = null; private set

    /** When the server reported its workspace indexing finished, via the work-done progress token. */
    @Volatile var indexingEndAt: Long? = null; private set

    /** Last diagnostics published per file URI, so two runs can be diffed for behaviour changes. */
    val diagnosticsByUri: MutableMap<String, JsonNode> = ConcurrentSkipListMap()

    init {
        Thread(::readLoop, "lsp-client-reader").apply {
            isDaemon = true
            start()
        }
    }

    fun sendRequest(id: Int, method: String, params: Any?) = send(
        mapper.createObjectNode().apply {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            set<ObjectNode>("params", mapper.valueToTree(params))
        },
    )

    fun sendNotification(method: String, params: Any?) = send(
        mapper.createObjectNode().apply {
            put("jsonrpc", "2.0")
            put("method", method)
            set<ObjectNode>("params", mapper.valueToTree(params))
        },
    )

    @Synchronized
    private fun send(node: ObjectNode) {
        val body = mapper.writeValueAsBytes(node)
        output.write("Content-Length: ${body.size}\r\n\r\n".toByteArray(Charsets.US_ASCII))
        output.write(body)
        output.flush()
    }

    private fun readLoop() {
        var sawFirstByte = false
        while (true) {
            val headers = readUntilBlankLine() ?: return
            if (!sawFirstByte) {
                sawFirstByte = true
                onFirstByte()
            }
            val length = CONTENT_LENGTH.find(headers)?.groupValues?.get(1)?.toInt() ?: continue
            val body = input.readNBytes(length)
            if (body.size < length) return
            lastActivityNanos = System.nanoTime()
            handle(body)
        }
    }

    private fun readUntilBlankLine(): String? {
        val buf = StringBuilder()
        while (!buf.endsWith("\r\n\r\n")) {
            val b = input.read()
            if (b < 0) return null
            buf.append(b.toChar())
        }
        return buf.toString()
    }

    private fun handle(body: ByteArray) {
        val msg = try {
            mapper.readTree(body)
        } catch (_: Exception) {
            return
        }
        val id = msg.get("id")
        val method = msg.get("method")?.asText()
        when {
            // Server→client request: acknowledge and move on.
            id != null && method != null -> sendResponseNull(id.asText())

            id?.asInt() == 1 && (msg.has("result") || msg.has("error")) ->
                initializeResponseAt = System.nanoTime()

            method == "$/progress" -> {
                val params = msg.get("params")
                val token = params?.get("token")?.asText()
                val kind = params?.get("value")?.get("kind")?.asText()
                if (token == INDEXING_PROGRESS_TOKEN && kind == "end") indexingEndAt = System.nanoTime()
            }

            method == "textDocument/publishDiagnostics" -> {
                diagnosticsFiles++
                lastDiagnosticAt = System.nanoTime()
                msg.get("params")?.let { params ->
                    params.get("uri")?.asText()?.let { diagnosticsByUri[it] = params.get("diagnostics") }
                }
            }
        }
    }

    private fun sendResponseNull(id: String) = send(
        mapper.createObjectNode().apply {
            put("jsonrpc", "2.0")
            // lsp4j issues its own request ids as strings; echo whatever arrived.
            id.toIntOrNull()?.let { put("id", it) } ?: put("id", id)
            putNull("result")
        },
    )

    companion object {
        private val CONTENT_LENGTH = Regex("content-length:\\s*(\\d+)", RegexOption.IGNORE_CASE)
    }
}

fun main(args: Array<String>) = ProfileLspCommand().main(args)
