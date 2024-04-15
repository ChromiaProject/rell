/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.gtx.testutils.tools

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.runtime.Rt_Printer
import net.postchain.rell.base.utils.PostchainGtvUtils
import net.postchain.rell.module.RellPostchainModuleEnvironment
import net.postchain.rell.module.RellPostchainModuleFactory
import java.io.File

fun main(args: Array<String>) {
    val dataDir = if (args.isNotEmpty()) File(args[0]) else File(System.getProperty("user.home"), "rell-blockchains")
    App().processData(dataDir)
}

private const val FILE_PATH_CONTAINS: String = ""

private class App {
    private val startTimeMs = System.currentTimeMillis()
    private var totCount = 0
    private var okCount = 0
    private val errFiles = mutableListOf<File>()
    private val errors = mutableSetOf<ErrorRec>()

    fun processData(dataDir: File) {
        for (sub in dataDir.listFiles().orEmpty().sorted()) {
            if (sub.isDirectory) {
                val name = sub.name
                when {
                    name.startsWith("man-") -> processManual(sub)
                    name.startsWith("pmc-") -> processPmc(sub)
                    else -> throw IllegalStateException("Bad directory: $sub")
                }
            }
        }

        if (errFiles.isNotEmpty()) {
            println()
            println("Error files: ${errFiles.size}")
            errFiles.forEach { println(it) }
        }

        if (errors.isNotEmpty()) {
            println()
            println("Errors: ${errors.size}")
            errors.sorted().forEach {
                println("${it.pos} ${it.msg}")
                println("    ${it.line}")
            }
        }

        val errCount = totCount - okCount
        val totTimeMs = System.currentTimeMillis() - startTimeMs
        println()
        println("Failed $errCount / $totCount (time: ${totTimeMs / 1000} sec)")
    }

    private fun processManual(dir: File) {
        processBlockchain(dir)
    }

    private fun processPmc(dir: File) {
        for (subDir in dir.listFiles().orEmpty().sorted()) {
            val name = subDir.name
            check(subDir.isDirectory) { subDir }
            check(name.matches(Regex("[0-9A-F]+"))) { subDir }
            processBlockchain(subDir)
        }
    }

    private fun processBlockchain(dir: File) {
        val confFiles = dir.listFiles().orEmpty().mapNotNull { file ->
            val name = file.name
            if (!file.isFile || !name.endsWith(".xml")) null else {
                check(name.endsWith(".conf.xml")) { file }
                val height = name.substringBefore('.').toInt()
                ConfFile(file, height)
            }
        }

        for (cf in confFiles.sortedBy { it.height }) {
            processConfig(cf.file)
        }
    }

    private fun processConfig(file: File) {
        if (FILE_PATH_CONTAINS !in file.path) {
            return
        }

        ++totCount
        val text = file.readText()

        val gtv = try {
            PostchainGtvUtils.xmlToGtv(text)
        } catch (e: Exception) {
            println("$file ERROR BAD XML")
            return
        }

        val messages = mutableListOf<String>()
        val printer = Rt_Printer { messages.add(it) }

        val env = RellPostchainModuleEnvironment(
            outPrinter = printer,
            logPrinter = printer,
            wrapCtErrors = false,
            wrapRtErrors = false,
            forceTypeCheck = true,
            hiddenLib = false,
            copyOutputToPrinter = true,
            logCompilerMessages = false,
        )

        val (cRes, sourceDir) = RellPostchainModuleFactory.compileApp(gtv, env)

        if (cRes.errors.isNotEmpty()) {
            println("$file ERROR MODULE")
            for (e in cRes.errors) {
                println("    $e")
                val line = getErrorLine(sourceDir, e.pos)
                val error = ErrorRec(e.pos, e.text, line)
                errors.add(error)
            }
            errFiles.add(file)
            return
        }

        checkNotNull(cRes.app)

        println("$file OK")
        ++okCount
    }
}

private fun getErrorLine(sourceDir: C_SourceDir, pos: S_Pos): String {
    val path = pos.path()
    val file = checkNotNull(sourceDir.file(path)) { path }
    val text = file.readText()
    val lines = text.lines()
    return lines[pos.line() - 1]
}

private class ConfFile(val file: File, val height: Int)

private data class ErrorRec(val pos: S_Pos, val msg: String, val line: String): Comparable<ErrorRec> {
    override fun compareTo(other: ErrorRec): Int {
        var d = pos.compareTo(other.pos)
        if (d == 0) d = msg.compareTo(other.msg)
        if (d == 0) d = line.compareTo(other.line)
        return d
    }
}
