/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
package net.postchain.rell.regression

import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertTimeoutPreemptively
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.inputStream

/**
 * One DynamicTest per project, parsed from public.json (+ optional private.json). Each test
 * loops both backends (INTERPRETER, TRUFFLE) sequentially against the project — serialising
 * the backends within a project avoids the chr-install race in the shared `src/lib/<name>`
 * clone tree. Cross-project parallelism is JUnit Jupiter's job: `parallel.enabled=true` +
 * `parallel.mode.default=concurrent` (set by the root build) plus the fixed parallelism the
 * regression Test task injects drive the project fan-out.
 *
 * Each `runOneProject` invocation owns its own throw-away Testcontainers Postgres and writes a
 * fragment to reports/parts/; the custom HTML renderer (report.kt) — invoked by the
 * `regressionReport` finalizer — remains the user-facing artefact `pages:regression` publishes.
 *
 * Test-level pass/fail mirrors the fragment status so the IDE / JUnit XML view matches the HTML:
 * PASSED and EXPECTED_FAIL are JUnit "pass"; FAILED and CLONE_FAILED are JUnit "fail". Both
 * still write fragments. `ignoreFailures = true` on the Test task keeps a single failing project
 * from reddening the build.
 */
@Execution(ExecutionMode.CONCURRENT)
class RegressionTest {
    @TestFactory
    fun projects(): List<DynamicTest> {
        val publicConfig = requiredPath("regression.config.public")
        val privateConfig = optionalPath("regression.config.private")
        val workdir = requiredPath("regression.workdir")
        val reportsDir = requiredPath("regression.reportsDir")

        partsDir(reportsDir).createDirectories()

        val projects = loadProjects(
            required = listOf(publicConfig),
            optional = listOfNotNull(privateConfig?.takeIf { it.exists() }),
        )

        return projects.map { project ->
            dynamicTest(project.name) {
                runProject(project, workdir, reportsDir)
            }
        }
    }

    private fun runProject(project: ProjectSpec, workdir: Path, reportsDir: Path) {
        val backends = listOf(ExecutionBackend.INTERPRETER, ExecutionBackend.TRUFFLE)
        val perBackendTimeout = project.timeoutMinutes?.let { Duration.ofMinutes(it.toLong()) }
            ?: Duration.ofMinutes(30)
        // Total cap = sum of per-backend caps + slack for fragment IO. JUnit kills the thread on
        // overrun; the chr ProcessBuilder owns its own timeout inside `runToLog`, so the
        // subprocess is still torn down cleanly.
        val totalCap = perBackendTimeout.multipliedBy(backends.size.toLong()).plus(Duration.ofMinutes(2))

        assertTimeoutPreemptively(totalCap) {
            for (backend in backends) {
                runOneProject(project, workdir, reportsDir, backend)
            }
        }

        val statuses = backends.map { backend ->
            val frag = partsDir(reportsDir) / "${backend.name.lowercase()}-${project.name}.json"
            regressionMapper.readValue<CompileResult>(frag.inputStream()).status
        }
        val unexpected = backends.zip(statuses)
            .filter { (_, s) -> s == Status.FAILED || s == Status.CLONE_FAILED }
        check(unexpected.isEmpty()) {
            "${project.name}: unexpected outcome(s) — " +
                backends.zip(statuses).joinToString { (b, s) -> "$b=$s" }
        }
    }

    private fun requiredPath(key: String): Path = Path(
        System.getProperty(key)
            ?: error("System property '$key' not set; the Gradle task must inject it."),
    )

    private fun optionalPath(key: String): Path? = System.getProperty(key)?.let(::Path)
}
