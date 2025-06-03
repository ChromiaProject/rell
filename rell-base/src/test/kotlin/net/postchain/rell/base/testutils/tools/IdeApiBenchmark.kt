/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.testutils.tools

import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.ide.IdeApi
import net.postchain.rell.base.utils.ide.IdeCompilationResult
import net.postchain.rell.base.utils.immListOf
import java.io.File
import java.time.Duration

fun main() {
    val projPath = "../rell-eclipse/net.postchain.rellide.parent/net.postchain.rellide.ui.tests"
    val rellSrcPath = "$projPath/test-workspace/real-world/originals-dip/rell/src"

    val sourceDir = C_SourceDir.diskDir(File(rellSrcPath))
    val files = findFiles(sourceDir, C_SourcePath.EMPTY)

    val ideResults = mutableListOf<IdeCompilationResult>()

    val totalDur = measureTime {
        for ((i, path) in files.withIndex()) {
            val fileDur = measureTime {
                val ideRes = compileFile(sourceDir, path)
                ideResults.add(ideRes)
            }
            println("[$i/${files.size}] $path - ${durationToStr(fileDur)}")
        }
    }

    println()
    println("TOTAL ${durationToStr(totalDur)}")

    val rt = Runtime.getRuntime()
    rt.gc()

    println()
    println("Free memory:  ${rt.freeMemory()/1024.0/1024.0}")
    println("Max memory:   ${rt.maxMemory()/1024.0/1024.0}")
    println("Total memory: ${rt.totalMemory()/1024.0/1024.0}")

    checkEquals(ideResults.size, files.size) // Use results, make sure they are not GC'd.
}

fun durationToStr(duration: Duration): String {
    val durationMs = duration.toMillis()
    val durationSecStr = if (durationMs == 0L) "0" else String.format("%.3f", durationMs / 1000.0)
    return "${durationSecStr}s"
}

private fun measureTime(block: () -> Unit): Duration {
    val t0 = System.currentTimeMillis()
    block()
    return Duration.ofMillis(System.currentTimeMillis() - t0)
}

private fun compileFile(sourceDir: C_SourceDir, path: C_SourcePath): IdeCompilationResult {
    val ast = sourceDir.file(path)!!.readAst()
    val modInfo = IdeApi.getModuleInfo(path, ast)!!

    val options = C_CompilerOptions.builder()
        .ide(true)
        .ideDocSymbolsEnabled(true)
        .symbolInfoFile(path)
        .build()

    val ideRes = IdeApi.compile(sourceDir, immListOf(modInfo.name), options)
    val errors = ideRes.messages.filter { !it.type.ignorable }
    checkEquals(errors, listOf()) { path }
    return ideRes
}

private fun findFiles(sourceDir: C_SourceDir, path: C_SourcePath): List<C_SourcePath> {
    val res = mutableListOf<C_SourcePath>()
    for (file in sourceDir.files(path).sorted()) {
        val filePath = path.add(file)
        res.add(filePath)
    }
    for (dir in sourceDir.dirs(path).sorted()) {
        val subPath = path.add(dir)
        res.addAll(findFiles(sourceDir, subPath))
    }
    return res.toList()
}
