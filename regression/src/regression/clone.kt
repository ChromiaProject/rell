/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
package net.postchain.rell.regression

import com.github.ajalt.clikt.core.Context
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

class CloneCommand : RegressionSubcommand("clone") {
    override fun help(context: Context) =
        "Clone every project listed in the JSON configs into the workdir (or update an existing clone)."

    override fun run() = cloneAll(loadProjects(configFiles, configOptionalFiles), workdir)
}

/**
 * For each project: shallow-clone if missing, otherwise fetch + reset to the configured ref.
 *
 * The toolkit deliberately does *not* clone in parallel — clones land on a single network
 * pipe (GitLab + Bitbucket + GitHub each rate-limit per-IP) and the failure modes are
 * easier to debug serially. A multi-minute clone phase is a small fraction of the full
 * regression run (compile time dominates).
 */
fun cloneAll(projects: List<ProjectSpec>, workdir: Path) {
    workdir.createDirectories()
    val total = projects.size
    for ((idx, project) in projects.withIndex()) {
        val tag = "[${idx + 1}/$total] ${project.name}"
        val target = workdir / project.name
        log("clone", "$tag ← ${project.url}${project.ref?.let { " @ $it" } ?: ""}")
        try {
            cloneOrUpdate(project, target)
            log("clone", "$tag — ok")
        } catch (e: Exception) {
            // Failure is recorded by the compile phase when it looks for a missing or
            // invalid clone — no need to abort the whole run on one broken repo.
            log("clone", "$tag — FAIL: ${e.message?.lines()?.firstOrNull()?.take(120)}")
        }
    }
}

private fun cloneOrUpdate(project: ProjectSpec, target: Path) {
    val parent = target.parent ?: error("workdir has no parent: $target")
    parent.createDirectories()

    if ((target / ".git").isDirectory()) {
        // Existing clone — update it. Using `fetch --depth 1` keeps the working tree small
        // even across multiple ref changes; `reset --hard` discards any local edits a
        // developer might have made while poking around. Anyone editing inside workdir/
        // should expect their changes to be discarded on the next regression run.
        runGit(target, listOf("fetch", "--depth", "1", "origin", project.ref ?: "HEAD"))
        runGit(target, listOf("reset", "--hard", "FETCH_HEAD"))
        return
    }

    if (target.exists()) {
        error("$target exists but is not a git repo; remove it or fix workdir layout")
    }

    val args = mutableListOf("clone", "--depth", "1")
    project.ref?.let { args += listOf("--branch", it) }
    args += listOf(project.url, target.toAbsolutePath().toString())
    runGit(parent, args)
}

private fun runGit(cwd: Path, args: List<String>) {
    // Redirect to a temp file rather than reading proc.inputStream — `readText()` blocks until
    // process EOF, which races with `waitFor(timeout)`: if the child hangs (network outage,
    // unauthenticated SSH stuck in handshake), the inputStream read never returns and the
    // timeout doesn't get a chance to fire. Burned 14 minutes on a wedged Bitbucket HTTPS clone
    // before realising the timeout was unreachable.
    val tempLog = java.io.File.createTempFile("regression-git-", ".log")
    try {
        val pb = ProcessBuilder(listOf("git") + args)
            .directory(cwd.toFile())
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.to(tempLog))
        pb.environment()["GIT_TERMINAL_PROMPT"] = "0"
        // ConnectTimeout=10: when ssh-agent has no key (or the key isn't authorised), default ssh
        // sits in TCP retransmit for ~60+s per host. 10s is more than enough to distinguish "no
        // route" from "auth failure"; everything past that is dead time per private repo.
        pb.environment()["GIT_SSH_COMMAND"] =
            "ssh -o BatchMode=yes -o StrictHostKeyChecking=accept-new -o ConnectTimeout=10"
        val proc = pb.start()
        val finished = proc.waitFor(Duration.ofMinutes(3).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
        if (!finished) {
            proc.destroyForcibly()
            error("git ${args.joinToString(" ")} timed out after 3 min\n${tempLog.readText().take(800)}")
        }
        if (proc.exitValue() != 0) {
            error("git ${args.joinToString(" ")} failed (exit ${proc.exitValue()})\n${tempLog.readText().take(800)}")
        }
    } finally {
        tempLog.delete()
    }
}

/** Returns the current HEAD sha of `repo`, or null if the directory is not a clone. */
internal fun readGitSha(repo: Path): String? {
    if (!(repo / ".git").isDirectory()) return null
    return try {
        val pb = ProcessBuilder("git", "rev-parse", "HEAD")
            .directory(repo.toFile())
            .redirectErrorStream(true)
        val proc = pb.start()
        val out = proc.inputStream.bufferedReader().readText().trim()
        proc.waitFor()
        if (proc.exitValue() == 0) out else null
    } catch (_: Exception) {
        null
    }
}
