/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
@file:JvmName("WorkloadKt")

package net.postchain.rell.performance.profiler

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.system.exitProcess
import kotlin.system.measureNanoTime

class WorkloadCommand: CliktCommand(name = "workload") {
    override fun help(context: Context) =
        "Generate test transactions and queries against a running Chromia node."

    private val chrBin by option("--chr", help = "Path to chr binary").default(defaultChrBin())
    private val users by option("--users", help = "Number of test users").int().default(20)
    private val postsPerUser by option("--posts", help = "Posts per user").int().default(10)
    private val nodeUrl by option("--node-url", help = "Postchain REST API URL")
        .default(System.getenv("NODE_URL") ?: "http://localhost:7740")
    private val resultsPath by option("--results", help = "Output path for results JSON")
        .path().default(perfDir() / "reports" / "workload-results.json")
    private val txWorkers by option(
        "--tx-workers",
        help = "Concurrent chr tx submitters (higher values pack more tx per block)",
    )
        .int().default(16)

    override fun run() {
        resultsPath.parent.createDirectories()
        waitForNode(nodeUrl)
        val brid = fetchBrid(nodeUrl)
        log("workload", "Blockchain RID: $brid")
        log("workload", "Using $txWorkers parallel tx submitters")

        val totalNanos = measureNanoTime {
            val (p1Tasks, p1Time) = run {
                val tasks = (1..users).map { i ->
                    "create_user" to listOf("user_$i", "Bio for user $i — blockchain developer and tester")
                }
                log("workload", "Phase 1: Creating $users users...")
                val t = timed { runTxBatch(tasks) }
                log("workload", "  Created $users users in ${"%.3f".format(t)}s")
                tasks.size to t
            }

            val totalPosts = users * postsPerUser
            val (p2Tasks, p2Time) = run {
                val tasks = (1..users).flatMap { i ->
                    (1..postsPerUser).map { j ->
                        "create_post" to listOf(
                            "user_$i",
                            "Post $j by user $i",
                            "Content of post $j by user_$i. Exercises database storage and retrieval.",
                        )
                    }
                }
                log("workload", "Phase 2: Creating $totalPosts posts ($postsPerUser per user)...")
                val t = timed { runTxBatch(tasks) }
                log("workload", "  Created $totalPosts posts in ${"%.3f".format(t)}s")
                tasks.size to t
            }

            val (p3Tasks, p3Time) = run {
                val tasks = (1..users).map { i ->
                    "update_bio" to listOf("user_$i", "Updated bio #$i — now with more detail")
                }
                log("workload", "Phase 3: Updating $users user bios...")
                val t = timed { runTxBatch(tasks) }
                log("workload", "  Updated $users bios in ${"%.3f".format(t)}s")
                tasks.size to t
            }

            val queries = buildQueries(users, postsPerUser, rounds = 10)
            log("workload", "Phase 4: Running queries...")
            val p4Time = timed { runQueriesBatch(queries) }
            log("workload", "  Ran ${queries.size} queries in ${"%.3f".format(p4Time)}s")

            writeResults(
                totalTimeS = -1.0,
                phases = mapOf(
                    "create_users" to (p1Tasks to p1Time),
                    "create_posts" to (p2Tasks to p2Time),
                    "update_bios" to (p3Tasks to p3Time),
                    "queries" to (queries.size to p4Time),
                ),
                totalPosts = totalPosts,
            )
        }
        val totalSeconds = totalNanos / 1_000_000_000.0

        rewriteWithTotal(totalSeconds)
        log("workload", "Workload complete in ${"%.3f".format(totalSeconds)}s")
        log("workload", "Results written to $resultsPath")
    }

    private fun runTxBatch(tasks: List<Pair<String, List<String>>>) {
        val pool = Executors.newFixedThreadPool(txWorkers)
        try {
            val futures = tasks.map { (op, args) -> pool.submit(Callable { submitTx(op, args) }) }
            var failures = 0
            for (f in futures) if (!f.get()) failures++
            if (failures > 0) log("workload", "  WARNING: $failures/${tasks.size} tx failed")
        } finally {
            pool.shutdown()
            pool.awaitTermination(5, TimeUnit.MINUTES)
        }
    }

    private fun submitTx(op: String, args: List<String>): Boolean {
        // chr CLI expects each Rell argument as a GTV literal — text values must be quoted.
        val gtvArgs = args.map { "\"$it\"" }
        val brid = bridCache
            ?: error("BRID must be fetched before submitting transactions")
        val cmd = mutableListOf(chrBin, "tx", "--await", "--api-url", nodeUrl, "-brid", brid, op)
        cmd.addAll(gtvArgs)
        return try {
            val proc = ProcessBuilder(cmd)
                .directory((perfDir() / "dapp").toFile())
                .redirectErrorStream(true).start()
            val finished = proc.waitFor(60, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                false
            } else {
                proc.exitValue() == 0
            }
        } catch (_: Exception) {
            false
        }
    }

    private val mapper = jacksonObjectMapper().configure(SerializationFeature.INDENT_OUTPUT, true)
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private var bridCache: String? = null

    private fun fetchBrid(url: String): String {
        val req = HttpRequest.newBuilder(URI("$url/brid/iid_0")).GET()
            .timeout(Duration.ofSeconds(5)).build()
        val resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString())
        require(resp.statusCode() == 200) { "Failed to fetch BRID: HTTP ${resp.statusCode()}" }
        val brid = resp.body().trim().trim('"')
        bridCache = brid
        return brid
    }

    private fun waitForNode(url: String, timeoutSec: Long = 60) {
        log("workload", "Waiting for node at $url ...")
        val deadline = System.nanoTime() + timeoutSec * 1_000_000_000L
        while (System.nanoTime() < deadline) {
            try {
                val req = HttpRequest.newBuilder(URI("$url/brid/iid_0"))
                    .timeout(Duration.ofSeconds(2)).GET().build()
                if (httpClient.send(req, HttpResponse.BodyHandlers.discarding()).statusCode() == 200) {
                    log("workload", "Node is ready")
                    return
                }
            } catch (_: Exception) {
                Thread.sleep(1000)
            }
        }
        exitProcess(1)
    }

    private fun runQueriesBatch(queries: List<ObjectNode>) {
        val pool: ExecutorService = Executors.newFixedThreadPool(txWorkers)
        try {
            val futures = queries.map { q ->
                pool.submit { runQuery(q) }
            }

            for (f in futures) f.get()
        } finally {
            pool.shutdown()
            pool.awaitTermination(5, TimeUnit.MINUTES)
        }
    }

    private fun runQuery(body: ObjectNode) {
        try {
            val req = HttpRequest.newBuilder(URI("$nodeUrl/query/iid_0"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build()

            httpClient.send(req, HttpResponse.BodyHandlers.discarding())
        } catch (_: Exception) {}
    }

    @Suppress("SameParameterValue")
    private fun buildQueries(users: Int, postsPerUser: Int, rounds: Int): List<ObjectNode> {
        val nf: JsonNodeFactory = JsonNodeFactory.instance
        val out = ArrayList<ObjectNode>(rounds * (2 + users + 5 + users + 5 + users))

        repeat(rounds) {
            out += nf.objectNode().put("type", "count_users")
            out += nf.objectNode().put("type", "count_posts")

            for (i in 1..users)
                out += nf.objectNode().put("type", "get_user").put("name", "user_$i")

            for (page in 0 until 5)
                out += nf.objectNode().put("type", "list_users").put("page_size", 5).put("skip", page * 5)

            for (i in 1..users)
                out += nf.objectNode()
                    .put("type", "get_posts_by_user")
                    .put("author_name", "user_$i")
                    .put("max_count", postsPerUser)

            for (term in listOf("Post 1", "Post 5", "content", "blockchain", "user_1"))
                out += nf.objectNode()
                    .put("type", "search_posts")
                    .put("term", term)

            for (i in 1..users)
                out += nf.objectNode()
                    .put("type", "get_user_post_count")
                    .put("author_name", "user_$i")
        }
        return out
    }

    @Suppress("SameParameterValue")
    private fun writeResults(
        totalTimeS: Double,
        phases: Map<String, Pair<Int, Double>>,
        totalPosts: Int,
    ) {
        val nf: JsonNodeFactory = JsonNodeFactory.instance
        val root: ObjectNode = nf.objectNode().apply {
            put("total_time_s", totalTimeS)
            put("num_users", users)
            put("posts_per_user", postsPerUser)
            put("total_posts", totalPosts)
            val phasesNode = nf.objectNode()
            for ((name, p) in phases) {
                val (count, t) = p
                phasesNode.set<ObjectNode>(name, nf.objectNode().put("count", count).put("time_s", t))
            }
            set<ObjectNode>("phases", phasesNode)
        }
        mapper.writeValue(resultsPath.outputStream(), root)
    }

    private fun rewriteWithTotal(totalSeconds: Double) {
        val tree = mapper.readTree(resultsPath.inputStream()) as ObjectNode
        tree.put("total_time_s", totalSeconds)
        mapper.writeValue(resultsPath.outputStream(), tree)
    }
}

private inline fun timed(block: () -> Unit): Double = measureNanoTime(block) / 1_000_000_000.0

private fun defaultChrBin(): String = (repoRoot() / "chromia-cli-local" / "chromia-cli" / "target" /
        "chromia-cli-dev-dist" / "bin" / "chr").toString()

fun main(args: Array<String>) = WorkloadCommand().main(args)
