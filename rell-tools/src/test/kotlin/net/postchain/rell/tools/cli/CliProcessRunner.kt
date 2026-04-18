/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.tools.cli

import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Manages a long-running CLI process with non-blocking output reading.
 * Used for interactive REPL tests and server-based multirun tests.
 *
 * Starts a subprocess, reads stdout asynchronously (including partial lines
 * like `>>> `), and provides pattern-based output matching.
 *
 * stderr is merged into stdout via [ProcessBuilder.redirectErrorStream].
 */
class CliProcessRunner(command: String) : AutoCloseable {
    private val process: Process
    private val outputReader: AsyncLineReader
    private val ignoreMatchers = mutableListOf<Regex>()

    init {
        val cmd = CliTestUtils.buildCommandLine(command)
        process = ProcessBuilder(cmd)
            .directory(CliTestUtils.REPO_DIR.toFile())
            .redirectErrorStream(true)
            .start()
        outputReader = AsyncLineReader(process.inputStream)
    }

    /**
     * Write [text] to the subprocess's stdin using UTF-8.
     *
     * UTF-8 matches the JDK's default for [String.toByteArray], but we spell it out to avoid
     * coupling to platform default charset on the receiving side — the Rell tools read stdin
     * via a reader configured for UTF-8, so the ends agree.
     */
    fun input(text: String) {
        process.outputStream.write(text.toByteArray(Charsets.UTF_8))
        process.outputStream.flush()
    }

    fun ignoreOutput(vararg patterns: String) {
        patterns.forEach { ignoreMatchers.add(CliTestUtils.createMatcher(it)) }
    }

    /** Non-blocking read of the next completed output line. Returns null if none available. */
    fun readLine(): String? = outputReader.poll()

    /**
     * Blocking read of the next output line, with timeout.
     * If [skipIgnored], lines matching patterns added via [ignoreOutput] are silently skipped.
     */
    fun readLineBlocking(timeoutMs: Long = 30_000, skipIgnored: Boolean = false): String {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) fail("Timeout (${timeoutMs}ms) waiting for output")
            val line = outputReader.pollBlocking(remaining) ?: continue
            if (skipIgnored && ignoreMatchers.any { it.matches(line) }) continue
            return line
        }
    }

    /**
     * Assert that the next output lines match [expected] patterns (see [CliTestUtils.createMatcher]).
     * If [ignoreRest] is false, asserts that no more output follows.
     */
    fun checkOutput(expected: List<String>, ignoreRest: Boolean = false) {
        val actualLines = mutableListOf<String>()
        for (exp in expected) {
            val actual = readLineBlocking(skipIgnored = true)
            actualLines.add(actual)
            val matcher = CliTestUtils.createMatcher(exp)
            if (!matcher.matches(actual)) {
                fail(
                    "Output mismatch:\n  expected: $exp\n  actual:   $actual" +
                        "\nAll expected:\n${expected.joinToString("\n") { "  $it" }}" +
                        "\nAll actual:\n${actualLines.joinToString("\n") { "  $it" }}",
                )
            }
        }
        if (!ignoreRest) {
            // Brief grace period to catch any delayed straggler output.
            val extra = outputReader.pollBlocking(300)
            if (extra != null) fail("Unexpected extra output: $extra")
        }
    }

    /** Vararg variant of [checkOutput] for concise call sites; asserts no more output follows. */
    fun checkOutput(vararg expected: String) = checkOutput(expected.toList())

    /** Drain all currently available output, discarding it. Waits briefly for any buffered output to arrive. */
    fun skipOutput() {
        // Block briefly on the first line; subsequent drains are non-blocking.
        outputReader.pollBlocking(200)
        while (readLine() != null) { /* drain */ }
    }

    /**
     * Read lines until one fully matches [regex], returning the [MatchResult].
     * Fails if the process exits or times out before a match.
     */
    fun readUntilMatch(regex: Regex, timeoutMs: Long = 30_000): MatchResult {
        val seenLines = mutableListOf<String>()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) fail("Timeout (${timeoutMs}ms) waiting for pattern: $regex\nOutput seen:\n${seenLines.joinToString("\n")}")
            // Cap each wait at 200 ms so we can notice a dead process without delaying the match.
            val line = outputReader.pollBlocking(minOf(remaining, 200))
            if (line != null) {
                seenLines.add(line)
                val m = regex.matchEntire(line)
                if (m != null) return m
            } else if (!process.isAlive) {
                // Drain any buffered output the reader thread hasn't flushed yet.
                outputReader.awaitStreamClose(500)
                while (true) seenLines.add(outputReader.poll() ?: break)
                fail("Process terminated (exit ${process.exitValue()}) while waiting for: $regex\nOutput seen:\n${seenLines.joinToString("\n")}")
            }
        }
    }

    /** Wait for the process to exit within [timeoutSec]. */
    fun waitForExit(timeoutSec: Long = 10) {
        val exited = process.waitFor(timeoutSec, TimeUnit.SECONDS)
        if (!exited) fail("Process did not exit within ${timeoutSec}s")
    }

    override fun close() {
        if (process.isAlive) {
            process.destroyForcibly()
            process.waitFor(5, TimeUnit.SECONDS)
        }
        outputReader.close()
    }

    /**
     * Reads an [InputStream] asynchronously on a daemon thread, splitting on `\n`.
     *
     * Complete lines are delivered to a [LinkedBlockingQueue] so consumers can `poll(timeout)`
     * without busy-waiting.
     *
     * Partial lines (e.g. a REPL prompt `>>> ` without trailing newline) are flushed to the
     * queue by a scheduled watchdog when the input stream has been idle for > 200 ms — this
     * matches how Python's non-blocking `readlines()` behaves for the original pytests.
     */
    private class AsyncLineReader(stream: InputStream) : AutoCloseable {
        private val lines = LinkedBlockingQueue<String>()
        private val buffer = StringBuilder()
        private val lock = Any()
        @Volatile private var lastDataTime = System.currentTimeMillis()
        @Volatile private var streamClosed = false
        private val streamClosedLatch = CountDownLatch(1)
        private val watchdog = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "cli-output-watchdog").apply { isDaemon = true }
        }

        init {
            val thread = Thread {
                val buf = CharArray(4096)
                val reader = stream.reader(Charsets.UTF_8)
                try {
                    while (true) {
                        val n = reader.read(buf)
                        if (n < 0) break
                        synchronized(lock) {
                            buffer.appendRange(buf, 0, n)
                            lastDataTime = System.currentTimeMillis()
                            extractLines()
                        }
                    }
                } finally {
                    synchronized(lock) {
                        if (buffer.isNotEmpty()) {
                            lines.add(buffer.toString())
                            buffer.clear()
                        }
                        streamClosed = true
                    }
                    streamClosedLatch.countDown()
                }
            }
            thread.isDaemon = true
            thread.name = "cli-output-reader"
            thread.start()

            // Periodically flush partial-line content so REPL prompts like ">>> " become visible
            // to consumers even without a trailing newline.
            watchdog.scheduleWithFixedDelay({ flushPartialIfIdle() }, 100, 100, MILLISECONDS)
        }

        private fun extractLines() {
            while (true) {
                val idx = buffer.indexOf('\n')
                if (idx < 0) break
                lines.add(buffer.substring(0, idx))
                buffer.delete(0, idx + 1)
            }
        }

        private fun flushPartialIfIdle() {
            synchronized(lock) {
                if (buffer.isEmpty()) return
                if (!streamClosed && System.currentTimeMillis() - lastDataTime <= 200) return
                extractLines()
                if (buffer.isNotEmpty()) {
                    lines.add(buffer.toString())
                    buffer.clear()
                }
            }
        }

        fun poll(): String? = lines.poll()

        /** Block up to [timeoutMs] for a line to arrive. Returns null on timeout. */
        fun pollBlocking(timeoutMs: Long): String? = lines.poll(timeoutMs.coerceAtLeast(0), MILLISECONDS)

        /** Wait up to [timeoutMs] for the reader thread to notice EOF and flush remaining buffer. */
        fun awaitStreamClose(timeoutMs: Long) {
            streamClosedLatch.await(timeoutMs, MILLISECONDS)
        }

        override fun close() {
            watchdog.shutdownNow()
        }
    }
}

/**
 * Wrapper for running a Postchain multirun node and sending HTTP queries to it.
 */
class MultirunServer(command: String) : AutoCloseable {
    private val runner = CliProcessRunner(command)
    private var apiPort: Int = -1
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    /** Block until the Postchain node logs "POSTCHAIN APP STARTED" and exposes a REST port. */
    fun waitTillUp() {
        runner.readUntilMatch(Regex(""".+ INFO {2}PostchainApp - POSTCHAIN APP STARTED"""))
        val m = runner.readUntilMatch(Regex(""".+ INFO {2}PostchainApp -\s+REST API port: (\d+)"""))
        apiPort = m.groupValues[1].toInt()
    }

    fun sendPost(path: String, data: String): HttpResult {
        check(apiPort > 0) { "Server not started (call waitTillUp first)" }
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:$apiPort/$path"))
            .timeout(Duration.ofSeconds(5))
            .POST(HttpRequest.BodyPublishers.ofString(data))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        return HttpResult(response.statusCode(), response.body())
    }

    fun checkQuery(req: String, status: Int, text: String) {
        val result = sendPost("query/iid_1", req)
        assertEquals(status, result.statusCode, "Query status mismatch for: $req\nResponse: ${result.body}")
        assertEquals(text, result.body, "Query response mismatch for: $req")
    }

    fun skipOutput() = runner.skipOutput()
    fun checkOutput(expected: List<String>, ignoreRest: Boolean = false) = runner.checkOutput(expected, ignoreRest)

    override fun close() = runner.close()

    data class HttpResult(val statusCode: Int, val body: String)
}
