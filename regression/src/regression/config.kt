/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
package net.postchain.rell.regression

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream

@JsonIgnoreProperties(ignoreUnknown = true)
data class ProjectsFile(
    val projects: List<ProjectSpec> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ProjectSpec(
    val name: String,
    val url: String,
    val ref: String? = null,
    val rellPath: String = ".",
    /*
     * Sequence of chr invocations executed from each project's rellPath. The default chains
     * `install` → `build` → `test`, so the regression run also exercises each project's own
     * test suite (e.g. ft4-lib's `tests.*` modules) against the locally-bootstrapped `chr`.
     * Override with `[["install"], ["build"]]` for projects without a `test:` block in
     * chromia.yml — chr exits non-zero with "No tests to run" otherwise.
     */
    val commands: List<List<String>> = listOf(listOf("install"), listOf("build"), listOf("test")),
    // Pre-compile text patches applied to files in the cloned tree before chr is invoked.
    // Use these to work around upstream regressions that the project's owners haven't picked
    // up yet — e.g. chromia-build-tools tightened its blockchain-name regex to reject hyphens,
    // and originals-dip's chromia.yml still has `dip-whitelist`. The patches re-apply on every
    // compile (clones get git-reset on the next clone phase, so they don't persist).
    val patches: List<PatchSpec> = emptyList(),
    /**
     * Optional per-project override of the global per-step timeout (default: 30 min, see
     * `compile.kt#DEFAULT_PER_PROJECT_TIMEOUT`). Applies to every chr invocation in [commands],
     * not just `test`. Set higher for projects whose suites legitimately exceed the default cap
     * on this hardware (e.g. mna-blockchain: ~36 min for 564 tests at avg 3.7s/test).
     */
    val timeoutMinutes: Int? = null,
    val expectedFailure: Boolean = false,
    val notes: String = "",
    // Populated by loadProjects from the source file's basename — never read from JSON.
    val cohort: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PatchSpec(
    /** Path relative to the project's clone root (NOT rellPath), e.g. "rell/chromia.yml". */
    val file: String,
    /** Literal text to find. Must occur at least once in the file. */
    val replace: String,
    /** Literal text to substitute. */
    val with: String,
)

enum class Status {
    CLONE_FAILED,   // git clone/fetch returned non-zero
    PASSED,         // chr exit 0 (whether or not expectedFailure was set in the JSON)
    FAILED,         // chr non-zero and !expectedFailure — this is what reviewers should look at
    EXPECTED_FAIL,  // chr non-zero, expectedFailure:true — historical / FT3-era projects we keep listed
}

/**
 * Rell execution backend. The local Rell `runtime-truffle` artefact rides along on chr's
 * classpath as a `runtimeOnly` dep of `rell-api-base`, so flipping backend at the regression
 * boundary is a JVM-arg toggle (`JAVA_ARGS=-Drell.execution.backend=truffle`) — no chr rebuild.
 *
 * Compilation (`chr install` / `chr build`) is identical between backends; only the test
 * phase (`chr test`) actually exercises the runtime. The regression sweep still runs the
 * full pipeline twice for symmetry and so each [CompileResult] is a self-contained record.
 */
enum class ExecutionBackend {
    INTERPRETER,
    TRUFFLE;

    fun label(): String = name.lowercase().replaceFirstChar(Char::uppercase)
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CompileResult(
    val name: String,
    val url: String,
    val cohort: String? = null,
    val ref: String? = null,
    val rellPath: String = ".",
    val commands: List<List<String>> = emptyList(),
    val failedStep: List<String>? = null,
    val notes: String = "",
    val expectedFailure: Boolean = false,
    val status: Status,
    val sha: String? = null,
    val durationMs: Long? = null,
    /** Wall-clock spent in non-test steps (install/build/...) for this run. Null on legacy results. */
    val nonTestDurationMs: Long? = null,
    /** Wall-clock spent in `chr test` steps for this run. Null on legacy results; 0 when no test step ran. */
    val testDurationMs: Long? = null,
    val exitCode: Int? = null,
    val timedOut: Boolean = false,
    val errorSummary: String? = null,
    val logRelPath: String? = null,
    /** Backend the test phase ran under. Null for legacy results.json predating the dual-backend sweep. */
    val executionBackend: ExecutionBackend? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ResultsFile(
    val generatedAtEpochMs: Long,
    val rellVersion: String,
    val results: List<CompileResult>,
)

internal val regressionMapper = jacksonObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

internal val regressionWriter = jacksonObjectMapper()
    .configure(SerializationFeature.INDENT_OUTPUT, true)

/**
 * Read every config file (required + optional-if-exists) and concatenate their `projects`
 * arrays. A project name appearing in multiple files wins on the *first* occurrence — that
 * means `public.json` (passed first via build.gradle.kts) takes priority over `private.json`.
 *
 * Each project gets its `cohort` stamped from the source file's basename (`public.json` →
 * `public`, `private.json` → `private`). The report uses that to split the table into one
 * section per cohort so public results stay visually distinct from any private overlay.
 */
internal fun loadProjects(required: List<Path>, optional: List<Path>): List<ProjectSpec> {
    val present = required + optional.filter { it.exists() }

    require(present.isNotEmpty()) {
        "No project config files exist (required=$required, optional=$optional)"
    }

    val seen = hashSetOf<String>()

    return present
        .flatMap { path ->
            val cohort = path.fileName.toString().removeSuffix(".json")

            regressionMapper
                .readValue<ProjectsFile>(path.inputStream())
                .projects
                .map { it.copy(cohort = cohort) }
        }
        .filter { seen.add(it.name) }
}
