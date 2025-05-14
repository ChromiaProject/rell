/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.testutils.tools

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.core.C_Compiler
import net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.model.R_LangVersion
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.utils.RellVersions
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.toImmMultimapKey
import org.apache.commons.lang3.StringUtils
import java.io.File
import kotlin.system.exitProcess

private const val FILTER: String = ""

fun main(args: Array<String>) {
    val reposDir = if (args.isNotEmpty()) File(args[0]) else File(System.getProperty("user.home"), "rell-repos")
    check(reposDir.isDirectory) { reposDir }
    ReposCompiler().go(reposDir)
}

private class VerDir(
    val repo: String,
    val dir: File,
    val infoFile: File,
    val gitVer: String,
    val rellVer: R_LangVersion?,
)

private class RunInfo(
    val srcPath: String,
    val srcDir: File,
    val module: String,
    val test: Boolean,
    val rellVer: R_LangVersion?,
)

private class ReposCompiler {
    private val startTimeMs = System.currentTimeMillis()
    private var totCount = 0
    private val errList = mutableListOf<String>()
    private val errMsgs = mutableMapOf<MessageKey, MutableSet<MessageRec>>()

    fun go(reposDir: File) {
        val verDirs = mutableListOf<VerDir>()

        for (repoDir in reposDir.listFiles()!!.sorted()) {
            check(repoDir.isDirectory) { repoDir }
            for (subFile in repoDir.listFiles()!!) {
                if (!subFile.isDirectory) continue
                val verDir = subFile
                check(verDir.isDirectory) { verDir }
                val (gitVer, rellVer) = parseVerDirName(verDir)
                verDirs.add(VerDir(repoDir.name, verDir, File(verDir, "info.txt"), gitVer, rellVer))
            }
        }

        val noInfo = verDirs.filter { !it.infoFile.isFile }
        if (noInfo.isNotEmpty()) {
            println("No info.txt: ${noInfo.size}")
            for (verDir in noInfo.sortedBy { it.dir }) println(verDir.dir)
            exitProcess(1)
        }

        val repoMap = verDirs.toImmMultimapKey { it.repo }
        for (repo in repoMap.keySet().sorted()) {
            val verMap = repoMap[repo].toImmMultimapKey { it.gitVer }
            for (gitVer in verMap.keySet().sorted()) {
                val vers = verMap[gitVer].sortedBy { it.rellVer ?: R_LangVersion.of("0.0.0") }.reversed()
                val verDir = vers.firstOrNull { it.rellVer == null || RellVersions.VERSION >= it.rellVer }
                if (verDir != null) {
                    processVer(verDir)
                }
            }
        }

        println("---------------------------------------------------------------------------")

        if (errMsgs.isNotEmpty()) {
            println("Errors: ${errMsgs.keys.size}")
            for (key in errMsgs.keys.sorted()) {
                println("${key.pos} ${key.message}")
                println("    ${key.line.trim()}")
                for (msg in errMsgs.getValue(key).sortedBy { it.srcPath }) {
                    println("    ${msg.srcPath}/${key.pos}")
                }
            }
            println("---------------------------------------------------------------------------")
        }

        if (errList.isNotEmpty()) {
            println("Failed:")
            for (err in errList) {
                println(err)
            }
            println("---------------------------------------------------------------------------")
        }

        val totTimeMs = System.currentTimeMillis() - startTimeMs
        println("Failed ${errList.size} / $totCount (time: ${totTimeMs / 1000} sec)")
    }

    private fun parseVerDirName(verDir: File): Pair<String, R_LangVersion?> {
        val verName = verDir.name
        check(verName.matches(Regex("\\d{4}-\\d{2}-\\d{2}__[0-9a-f]{7,8}(__[0-9]+[.][0-9]+[.][0-9]+)?"))) { verDir }
        val parts = verName.split("__")
        check(parts.size == 2 || parts.size == 3) { parts }
        val gitVer = parts[0] + "__" + parts[1]
        val rellVer = if (parts.size == 2) null else R_LangVersion.of(parts[2])
        return gitVer to rellVer
    }

    private fun processVer(verDir: VerDir) {
        val infoFile = verDir.infoFile
        check(infoFile.isFile) { infoFile }

        val lines = infoFile.readLines()
        for (line in lines) {
            val runInfo = parseRunInfo(verDir, line)
            processSources(verDir.repo, verDir.dir.name, runInfo)
        }
    }

    private fun processSources(repoName: String, verName: String, runInfo: RunInfo) {
        val caseKey = "$repoName/$verName/${runInfo.module}"
        if (!"$caseKey/".startsWith(FILTER)) {
            return
        }

        val sourceDir = C_SourceDir.diskDir(runInfo.srcDir)

        val modules = immListOf(R_ModuleName.of(runInfo.module))
        val modSel = if (runInfo.test) {
            C_CompilerModuleSelection(immListOf(), modules)
        } else {
            C_CompilerModuleSelection(modules, modules)
        }

        val res = try {
            val opts = if (runInfo.rellVer == null) C_CompilerOptions.DEFAULT else {
                C_CompilerOptions.builder().compatibility(runInfo.rellVer).allowOlderCompatibilityVersion(true).build()
            }
            C_Compiler.compile(sourceDir, modSel, opts)
        } catch (e: Throwable) {
            println("crashed: ${runInfo.srcDir} [${runInfo.module}]")
            throw e
        }

        val err = res.app == null || res.errors.isNotEmpty()

        ++totCount
        if (err) {
            errList.add(caseKey)
        }

        val resStr = if (err) "*** ERROR ***" else "ok"
        println("$repoName - $verName - ${runInfo.srcPath} - [${runInfo.module}] ==> $resStr")

        val srcPath = "$repoName/$verName/${runInfo.srcPath}"
        for (msg in res.errors) {
            println("    $msg")
            val line = getSourceLine(runInfo.srcDir, msg.pos)
            val msgRec = MessageRec(srcPath, msg.pos, msg.text, line)
            errMsgs.computeIfAbsent(msgRec.key) { mutableSetOf() }.add(msgRec)
        }
    }
}

private fun getSourceLine(srcDir: File, pos: S_Pos): String {
    val file = File(srcDir, pos.path().str())
    val lines = file.readLines()
    return lines[pos.line() - 1]
}

private fun parseRunInfo(verDir: VerDir, line: String): RunInfo {
    val parts = StringUtils.splitPreserveAllTokens(line, ":")
    check(parts.size in 2 .. 4) { parts.toList() }

    val srcPath = parts[0]
    val srcDir = File(verDir.dir, srcPath)
    check(srcDir.isDirectory) { "Directory not found: [$srcDir]" }

    val module = parts[1].trim()

    val test = if (parts.size < 3) false else {
        val s = parts[2]
        if (s == "") false else {
            checkEquals(s, "test")
            true
        }
    }

    val rellVer = if (parts.size < 4) null else R_LangVersion.of(parts[3])

    return RunInfo(srcPath, srcDir, module, test, rellVer)
}

private data class MessageKey(val pos: S_Pos, val message: String, val line: String): Comparable<MessageKey> {
    override fun compareTo(other: MessageKey): Int {
        var d = pos.compareTo(other.pos)
        if (d == 0) d = message.compareTo(other.message)
        if (d == 0) d = line.compareTo(other.line)
        return d
    }
}

private data class MessageRec(val srcPath: String, val pos: S_Pos, val message: String, val line: String) {
    val key = MessageKey(pos, message, line)
}
