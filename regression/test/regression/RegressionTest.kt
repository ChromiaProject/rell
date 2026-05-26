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
 * One DynamicTest per (project, backend), parsed from public.json (+ optional private.json).
 * JUnit Jupiter's parallel-test extension (`parallel.enabled=true` + `parallel.mode.default=concurrent`
 * set by the root build, plus the fixed parallelism the regression Test task injects) fans the
 * (project, backend) units out across worker threads. Each unit operates on its own
 * `workdir/<project>-<backend>/` clone (materialised by `refreshBackendCopy` in compile.kt), so
 * concurrent `chr install` calls never race on the shared `src/lib/<name>` clone tree.
 *
 * Each `runOneProject` invocation owns its own throw-away Testcontainers Postgres and writes a
 * fragment to reports/parts/; the custom HTML renderer (report.kt) — invoked by the
 * `regressionReport` finalizer — remains the user-facing artefact `pages:regression` publishes.
 *
 * Test-level pass/fail mirrors the fragment status so the IDE / JUnit XML view matches the HTML:
 * PASSED and EXPECTED_FAIL are JUnit "pass"; FAILED and CLONE_FAILED are JUnit "fail". Both
 * still write fragments. `ignoreFailures = true` on the Test task keeps a single failing unit
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

        val backends = listOf(ExecutionBackend.INTERPRETER, ExecutionBackend.TRUFFLE)
        return projects.flatMap { project ->
            backends.map { backend ->
                dynamicTest("${project.name} [${backend.name.lowercase()}]") {
                    runProjectBackend(project, backend, workdir, reportsDir)
                }
            }
        }
    }

    private fun runProjectBackend(
        project: ProjectSpec,
        backend: ExecutionBackend,
        workdir: Path,
        reportsDir: Path,
    ) {
        val perBackendTimeout = project.timeoutMinutes?.let { Duration.ofMinutes(it.toLong()) }
            ?: Duration.ofMinutes(30)
        // Cap = per-backend budget + slack for fragment IO and Postgres start/stop. JUnit kills
        // the thread on overrun; the chr ProcessBuilder owns its own timeout inside `runToLog`,
        // so the subprocess is still torn down cleanly.
        val cap = perBackendTimeout.plus(Duration.ofMinutes(2))

        assertTimeoutPreemptively(cap) {
            runOneProject(project, workdir, reportsDir, backend)
        }

        val frag = partsDir(reportsDir) / "${backend.name.lowercase()}-${project.name}.json"
        val status = regressionMapper.readValue<CompileResult>(frag.inputStream()).status
        check(status != Status.FAILED && status != Status.CLONE_FAILED) {
            "${project.name} [${backend.name.lowercase()}]: unexpected outcome — $status"
        }
    }

    private fun requiredPath(key: String): Path = Path(
        System.getProperty(key)
            ?: error("System property '$key' not set; the Gradle task must inject it."),
    )

    private fun optionalPath(key: String): Path? = System.getProperty(key)?.let(::Path)
}
