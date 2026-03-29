/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.tools.cli

import java.util.concurrent.TimeUnit
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Result of running a CLI command as a subprocess.
 */
data class CliResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

/**
 * Utility for launching Rell CLI tools as subprocesses and validating their output.
 *
 * Equivalent of the Python `testlib.py` module — runs the tool's main class in a separate JVM
 * using the current test classpath, then checks stdout/stderr/exit code.
 */
object CliTestUtils {
    val REPO_DIR = run {
        // Walk up from CWD or use known project structure
        var dir = Path(System.getProperty("user.dir"))
        while (dir.parent != null) {
            if ((dir / "work/testproj/src").isDirectory()) return@run dir
            dir = dir.parent
        }
        // Fallback: assume the Gradle project root
        Path(System.getProperty("user.dir"))
    }

    private val JAVA_BIN: String = ProcessHandle.current().info().command().orElse("java")
    private val CLASSPATH: String = System.getProperty("java.class.path")

    private const val LOG_TIME_REGEX = """\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}"""

    /** Main class names for each CLI tool script. */
    private val MAIN_CLASSES = mapOf(
        "rell.sh" to "net.postchain.rell.tools.RellCLIKt",
        "rellcfg.sh" to "net.postchain.rell.tools.RellConfigGenMainKt",
        "multigen.sh" to "net.postchain.rell.tools.runcfg.RellRunConfigGenKt",
        "multirun.sh" to "net.postchain.rell.tools.runcfg.RellRunConfigLaunchKt",
        "migrate-v0.10.sh" to "net.postchain.rell.tools.Migrator_v0_10Kt",
    )

    /**
     * Run a CLI command string (same format as the Python tests, e.g. "rell.sh -d work/testproj/src calc sum_digits_integer 1000").
     * The script name is resolved to the appropriate main class.
     */
    fun runCommand(command: String, timeoutSec: Long = 60): CliResult {
        val parts = command.split(" ")
        val script = parts[0]
        val args = parts.drop(1)

        val mainClass = MAIN_CLASSES[script]
            ?: error("Unknown script: $script (known: ${MAIN_CLASSES.keys})")

        val cmd = listOf(JAVA_BIN, "-cp", CLASSPATH, mainClass) + args

        val process = ProcessBuilder(cmd)
            .directory(REPO_DIR.toFile())
            .redirectErrorStream(false)
            .start()

        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val finished = process.waitFor(timeoutSec, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            fail("Command timed out after ${timeoutSec}s: $command")
        }

        return CliResult(process.exitValue(), stdout, stderr)
    }

    /**
     * Run a command and assert exit code, stdout, and stderr.
     * stdout/stderr can be a plain string for exact match or a list of patterns (see [createMatcher]).
     */
    fun chkCommand(
        command: String,
        stdout: Any = "",
        stderr: Any = "",
        code: Int = 0,
        stdoutIgnore: List<String> = emptyList(),
        stderrIgnore: List<String> = emptyList(),
    ) {
        val result = runCommand(command)
        assertEquals(code, result.exitCode, "Exit code mismatch for: $command\nstdout: ${result.stdout}\nstderr: ${result.stderr}")
        chkOutput(result.stdout, stdout, stdoutIgnore)
        chkOutput(result.stderr, stderr, stderrIgnore)
    }

    /**
     * Run a command that produces test results and validate them.
     * Skips output lines until the first expected line is found, then matches the rest.
     */
    fun chkTests(command: String, code: Int, expected: List<String>) {
        val result = runCommand(command)
        assertEquals(code, result.exitCode, "Exit code mismatch for: $command\nstdout: ${result.stdout}\nstderr: ${result.stderr}")
        assertEquals("", result.stderr, "Expected empty stderr for: $command")

        val actLines = result.stdout.trimEnd('\n').split('\n')
        val firstExpected = expected[0]
        val startIdx = actLines.indexOf(firstExpected)
        assertTrue(startIdx >= 0, "Could not find '$firstExpected' in output:\n${result.stdout}")

        val relevantLines = actLines.subList(startIdx, actLines.size)
        chkOutputLines(relevantLines, expected)
    }

    private fun chkOutput(actual: String, expected: Any, ignored: List<String> = emptyList()) {
        when (expected) {
            is String -> {
                val filteredActual = if (ignored.isEmpty()) actual else filterIgnored(actual, ignored)
                assertEquals(expected, filteredActual)
            }
            is List<*> -> {
                @Suppress("UNCHECKED_CAST")
                val expectedList = expected as List<String>
                val actLines = if (actual.isEmpty()) {
                    emptyList()
                } else {
                    assertTrue(actual.endsWith("\n"), "Expected output to end with newline, got: $actual")
                    actual.trimEnd('\n').split('\n')
                }
                val filteredLines = if (ignored.isEmpty()) actLines else {
                    val ignoreMatchers = ignored.map { createMatcher(it) }
                    actLines.filter { line -> ignoreMatchers.none { it.matches(line) } }
                }
                chkOutputLines(filteredLines, expectedList)
            }
            else -> error("Expected String or List<String>, got: ${expected::class}")
        }
    }

    private fun chkOutputLines(actual: List<String>, expected: List<String>) {
        for (i in expected.indices) {
            assertTrue(i < actual.size, "Expected line ${i + 1} of ${expected.size} ('${expected[i]}'), but output has only ${actual.size} lines")
            val matcher = createMatcher(expected[i])
            assertTrue(
                matcher.matches(actual[i]),
                "Line ${i + 1} mismatch:\n  expected: ${expected[i]}\n  actual:   ${actual[i]}"
            )
        }
        assertEquals(
            expected.size, actual.size,
            "Extra output lines after expected ${expected.size} lines:\n${actual.drop(expected.size).joinToString("\n")}"
        )
    }

    private fun filterIgnored(actual: String, ignored: List<String>): String {
        if (actual.isEmpty()) return actual
        val ignoreMatchers = ignored.map { createMatcher(it) }
        val lines = actual.trimEnd('\n').split('\n')
        val filtered = lines.filter { line -> ignoreMatchers.none { it.matches(line) } }
        return if (actual.endsWith("\n")) filtered.joinToString("\n", postfix = "\n") else filtered.joinToString("\n")
    }

    private val LOG_LEVELS = listOf("DEBUG", "INFO", "WARN", "ERROR")

    /**
     * Create a [Regex] from a pattern string.
     *
     * Supports prefixes:
     * - `<LOG:INFO>`, `<LOG:WARN>`, etc. — matches log lines with timestamp
     * - `<RE>` — regex match
     * - `<TEST>` — test result line (appends timing pattern)
     * - plain string — exact match
     */
    fun createMatcher(pattern: String): Regex {
        var s = pattern
        var logLevel: String? = null

        for (level in LOG_LEVELS) {
            val prefix = "<LOG:$level>"
            if (s.startsWith(prefix)) {
                logLevel = level
                s = s.removePrefix(prefix)
                break
            }
        }

        val isTest = s.startsWith("<TEST>")

        if (isTest) {
            s = s.removePrefix("<TEST>")
        }

        val isRegex = s.startsWith("<RE>")

        if (isRegex) {
            s = s.removePrefix("<RE>")
        }

        var regexStr = if (isRegex) s else Regex.escape(s)

        if (isTest) {
            regexStr = """$regexStr \(\d+(\.\d+)?s\)"""
        }

        if (logLevel != null) {
            regexStr = makeLogRegex(logLevel, regexStr)
        }

        return regexStr.toRegex()
    }

    private fun makeLogRegex(level: String, messageRegex: String): String {
        val paddedLevel = level.padEnd(5)
        return """$LOG_TIME_REGEX ${Regex.escape(paddedLevel)} $messageRegex"""
    }
}
