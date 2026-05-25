/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
package net.postchain.rell.performance.chr

import java.io.File
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.io.path.*

/**
 * Builds the local `chr` distribution against the Rell snapshot **already published to ~/.m2**.
 *
 * This is the Kotlin replacement for the former `work/local-chr.sh`. The script's two `./gradlew`
 * calls — `gradlew -q properties` (read the Rell version) and `gradlew publishToMavenLocal`
 * (publish Rell) — are gone: a nested Gradle deadlocks on the journal-cache lock when it runs
 * inside an outer Gradle build (the regression / profiler tasks). Instead:
 *
 *  - the Rell version is passed in by the caller (the Gradle task knows `project.version`), and
 *  - publishing Rell to ~/.m2 is the caller task's responsibility — it must `dependsOn`
 *    `:publishRellToMavenLocal` so the artifacts exist before [ensureChr] runs.
 *
 * Everything else (git sync of chromia-cli + chromia-cli-tools, the `./mvnw` version-patch and
 * install, copying the fresh Rell jars into the dist `lib/`) is ported verbatim and driven via
 * subprocesses — Gradle calling Maven, which never touches Gradle's journal lock.
 */
object LocalChr {
    const val CHR_REPO_URL = "https://gitlab.com/chromaway/core-tools/chromia-cli.git"
    const val CHR_REPO_DIR = "chromia-cli-local"
    const val CHR_TOOLS_REPO_URL = "https://gitlab.com/chromaway/core-tools/chromia-cli-tools.git"
    const val CHR_TOOLS_DIR = "chromia-cli-tools-local"

    // The branches carry the Rell 0.16 API fixes the released chromia-cli / -tools jars lack.
    const val GIT_BRANCH = "update-rell-0.16.0-snapshot"
    const val CHR_TOOLS_BRANCH = "update-rell-0.16.0-snapshot"
    const val CHR_TOOLS_VERSION = "dev" // value of <revision> in chromia-cli-tools/pom.xml

    private val GIT_TIMEOUT: Duration = Duration.ofMinutes(5)
    private val MVN_TIMEOUT: Duration = Duration.ofMinutes(60)

    /** Path the built chr binary lands at, relative to the Rell repo root. */
    fun chrExecutable(repoRoot: Path): Path =
        repoRoot / CHR_REPO_DIR / "chromia-cli" / "target" / "chromia-cli-dev-dist" / "bin" / "chr"

    /**
     * Sync the chromia-cli repos, patch them to [rellVersion], build/install them, and copy the
     * freshly published Rell jars into the chr distribution. Assumes Rell [rellVersion] is already
     * in ~/.m2 (the caller task depends on `:publishRellToMavenLocal`). Returns the chr binary path.
     */
    fun ensureChr(
        repoRoot: Path,
        rellVersion: String,
        rebuild: Boolean = false,
        log: (String) -> Unit = ::println,
    ): Path {
        requireRepoRoot(repoRoot)

        if (rebuild) {
            log("Rebuild requested — removing $CHR_REPO_DIR and $CHR_TOOLS_DIR")
            (repoRoot / CHR_REPO_DIR).toFile().deleteRecursively()
            (repoRoot / CHR_TOOLS_DIR).toFile().deleteRecursively()
        }

        log("Syncing chromia-cli repositories…")
        syncRepo(repoRoot / CHR_REPO_DIR, CHR_REPO_URL, GIT_BRANCH, "chromia-cli", repoRoot, log)
        syncRepo(repoRoot / CHR_TOOLS_DIR, CHR_TOOLS_REPO_URL, CHR_TOOLS_BRANCH, "chromia-cli-tools", repoRoot, log)

        log("Patching Rell version ($rellVersion) into chromia-cli…")
        patchVersions(repoRoot, rellVersion, log)

        log("Installing chromia-cli-tools ($CHR_TOOLS_VERSION) to local Maven…")
        installChromiaCliTools(repoRoot, rellVersion, log)

        log("Building chromia-cli distribution…")
        mvnInstall(repoRoot / CHR_REPO_DIR, log)

        val chrBin = chrExecutable(repoRoot)
        syncRellJars(repoRoot, rellVersion, log)

        check(chrBin.isExecutable()) { "Expected chr at $chrBin after build, but it is missing or not executable" }
        return chrBin
    }

    /** Run the built chr binary with [args]; returns its exit code. Builds nothing — call [ensureChr] first. */
    fun runChr(repoRoot: Path, args: List<String>, extraEnv: Map<String, String> = emptyMap()): Int {
        val chrBin = chrExecutable(repoRoot)
        check(chrBin.isExecutable()) { "chr not built at $chrBin — run :performance:buildLocalChr first" }

        val pb = ProcessBuilder(listOf(chrBin.absolutePathString()) + args)
            .directory(repoRoot.toFile())
            .inheritIO()
        pb.environment().apply {
            putAll(toolchainEnv())
            // chr's start script reads $JAVA_ARGS; native access is needed for the embedded node.
            put("JAVA_ARGS", ((get("JAVA_ARGS") ?: System.getenv("JAVA_ARGS") ?: "") + " --enable-native-access=ALL-UNNAMED").trim())
            putAll(extraEnv)
        }
        return pb.start().waitFor()
    }

    // ── Internals ────────────────────────────────────────────────────────────────────────────────

    private fun requireRepoRoot(repoRoot: Path) {
        require((repoRoot / "build.gradle.kts").isRegularFile() && (repoRoot / "README.md").isRegularFile()) {
            "LocalChr must run from the Rell repo root; $repoRoot is missing build.gradle.kts / README.md"
        }
    }

    private fun syncRepo(repoDir: Path, url: String, branch: String, label: String, repoRoot: Path, log: (String) -> Unit) {
        if ((repoDir / ".git").isDirectory()) {
            log("Updating $label to latest $branch…")
            git(repoDir, listOf("fetch", "--depth", "1", "--quiet", "origin", branch))
            git(repoDir, listOf("checkout", "--quiet", branch))
            git(repoDir, listOf("reset", "--hard", "--quiet", "origin/$branch"))
        } else {
            if (repoDir.exists()) {
                log("Existing $repoDir has no .git — removing for a fresh clone")
                repoDir.toFile().deleteRecursively()
            }
            log("Cloning $label ($branch)…")
            git(repoRoot, listOf("clone", "--depth", "1", "--quiet", "--branch", branch, url, repoDir.absolutePathString()))
        }
    }

    /** chromia-cli POM carries rell.version, rell.dokka.version (both = Rell snapshot) + chromia.cli.tools.version. */
    private fun patchVersions(repoRoot: Path, rellVersion: String, log: (String) -> Unit) {
        val cli = repoRoot / CHR_REPO_DIR
        setProperty(cli, "rell.version", rellVersion, log)
        setProperty(cli, "rell.dokka.version", rellVersion, log)
        setProperty(cli, "chromia.cli.tools.version", CHR_TOOLS_VERSION, log)
    }

    /** Sync the Rell version into chromia-cli-tools too, then install it so chromia-cli can consume it. */
    private fun installChromiaCliTools(repoRoot: Path, rellVersion: String, log: (String) -> Unit) {
        val tools = repoRoot / CHR_TOOLS_DIR
        setProperty(tools, "rell.version", rellVersion, log)
        mvnInstall(tools, log)
    }

    private fun setProperty(dir: Path, property: String, value: String, log: (String) -> Unit) =
        mvnw(dir, listOf("versions:set-property", "-Dproperty=$property", "-DnewVersion=$value"), MVN_TIMEOUT, log)

    private fun mvnInstall(dir: Path, log: (String) -> Unit) =
        mvnw(dir, listOf("-DskipTests", "-DskipITs", "install"), MVN_TIMEOUT, log)

    /** Copy freshly published net.postchain.rell + com.chromia.rell.dokka jars into the chr dist lib/. */
    private fun syncRellJars(repoRoot: Path, rellVersion: String, log: (String) -> Unit) {
        val distLib = repoRoot / CHR_REPO_DIR / "chromia-cli" / "target" / "chromia-cli-dev-dist" / "lib"
        check(distLib.isDirectory()) { "chromia-cli dist lib not found at $distLib" }

        val home = Path.of(System.getProperty("user.home"))
        val localRepos = listOf(
            home / ".m2" / "repository" / "net" / "postchain" / "rell",
            home / ".m2" / "repository" / "com" / "chromia" / "rell" / "dokka",
        )

        var synced = 0
        for (repo in localRepos) {
            if (!repo.isDirectory()) continue
            // Layout: <repo>/<artifact>/<version>/<artifact>-<version>.jar
            for (artifactDir in repo.listDirectoryEntries().filter { it.isDirectory() }) {
                val versionDir = artifactDir / rellVersion
                if (!versionDir.isDirectory()) continue
                for (jar in versionDir.listDirectoryEntries("*-$rellVersion.jar")) {
                    val target = distLib / jar.name
                    if (target.exists()) {
                        jar.copyTo(target, overwrite = true)
                        synced++
                    }
                }
            }
        }

        if (synced == 0) log("Warning: no local Rell jars synced for version $rellVersion")
        else log("Synced $synced local Rell jars into the chromia-cli distribution")
    }

    private fun git(dir: Path, args: List<String>) =
        runProcess(listOf("git") + args, dir, GIT_TIMEOUT)

    private fun mvnw(dir: Path, args: List<String>, timeout: Duration, log: (String) -> Unit) {
        val mvnw = dir / "mvnw"
        // git preserves the executable bit on a fresh clone, but a re-clone onto a noexec mount or a
        // checkout via a tool that drops the bit would not — make sure before invoking it.
        if (!mvnw.isExecutable()) mvnw.toFile().setExecutable(true)
        log("  ./mvnw ${args.joinToString(" ")}  (${dir.fileName})")
        // `-q` keeps Maven to warnings/errors only — the reactor/INFO spam is what makes the
        // bootstrap noisy; failures still print.
        runProcess(listOf(mvnw.absolutePathString(), "-q") + args, dir, timeout)
    }

    private fun runProcess(command: List<String>, dir: Path, timeout: Duration) {
        val pb = ProcessBuilder(command).directory(dir.toFile()).inheritIO()
        pb.environment().apply {
            put("GIT_TERMINAL_PROMPT", "0")
            putAll(toolchainEnv())
        }
        val proc = pb.start()
        if (!proc.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            proc.destroyForcibly()
            error("${command.first()} timed out after $timeout in $dir")
        }
        val rc = proc.exitValue()
        check(rc == 0) { "${command.joinToString(" ")} failed (exit $rc) in $dir" }
    }

    /**
     * Pin JAVA_HOME (and PATH) to the JDK this JVM runs on. The caller task uses the JDK-21
     * toolchain launcher, so the nested `./mvnw` — whose kotlin-bcv plugin demands JDK 21 — never
     * falls back to whatever the ambient shell happens to have active.
     */
    private fun toolchainEnv(): Map<String, String> {
        val home = System.getProperty("java.home")
        return mapOf(
            "JAVA_HOME" to home,
            "PATH" to "$home${File.separator}bin${File.pathSeparator}${System.getenv("PATH") ?: ""}",
        )
    }
}
