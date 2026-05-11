/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
@file:JvmName("CliKt")

package net.postchain.rell.regression

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Path

class RegressionCli: CliktCommand(name = "regression") {
    override fun help(context: Context) =
        "Regression toolkit: clone external Rell projects and verify the local Rell compiler builds them."

    override fun run() = Unit
}

/**
 * Shared options for every subcommand. Repeated declarations are ugly but each Clikt
 * subcommand owns its own option parser, and the trade-off of one short base class beats
 * wiring a Context-shared OptionGroup just for four flags.
 */
abstract class RegressionSubcommand(name: String): CliktCommand(name = name) {
    val configFiles: List<Path> by option(
        "--config",
        help = "Tracked project list (e.g. public.json). Repeatable.",
    ).path(mustExist = true, canBeDir = false).multiple(required = true)

    val configOptionalFiles: List<Path> by option(
        "--config-optional",
        help = "Optional project list, silently skipped if it does not exist (e.g. private.json).",
    ).path(canBeDir = false).multiple()

    val workdir: Path by option(
        "--workdir",
        help = "Directory used to clone the project trees into.",
    ).path().required()

    val reportsDir: Path by option(
        "--reports-dir",
        help = "Directory for results.json, captured logs, and the rendered HTML report.",
    ).path().required()
}

class AllCommand: RegressionSubcommand("all") {
    override fun help(context: Context) =
        "End-to-end: clone every project, run chr against each, render an HTML report."

    override fun run() {
        val projects = loadProjects(configFiles, configOptionalFiles)
        cloneAll(projects, workdir)
        renderHtml(results = compileAll(projects, workdir, reportsDir), reportsDir = reportsDir)
    }
}

fun main(args: Array<String>) = RegressionCli()
    .subcommands(CloneCommand(), CompileCommand(), ReportCommand(), AllCommand())
    .main(args)
