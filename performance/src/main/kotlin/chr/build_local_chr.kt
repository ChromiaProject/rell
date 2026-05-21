/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
@file:JvmName("BuildLocalChrKt")

package net.postchain.rell.performance.chr

import java.nio.file.Path

/**
 * Entry point for `:performance:buildLocalChr`. The task publishes the Rell snapshot to ~/.m2 first (via its
 * `dependsOn(":publishRellToMavenLocal")`), then runs this to build chr against it.
 * `args[0]` is Rell version;
 * `--rebuild` forces a clean re-clone of the chromia-cli repos.
 */
fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "usage: build-local-chr <rellVersion> [--rebuild]" }
    val rellVersion = args[0]
    val rebuild = "--rebuild" in args.drop(1)

    val repoRoot = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
    val chr = LocalChr.ensureChr(repoRoot, rellVersion, rebuild) { println("[build-local-chr] $it") }
    println("[build-local-chr] chr binary: $chr")
}
