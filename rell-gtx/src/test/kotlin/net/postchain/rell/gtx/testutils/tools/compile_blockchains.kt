/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.gtx.testutils.tools

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.utils.C_IdeCompletionsUtils
import net.postchain.rell.base.compiler.base.utils.C_Message
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.runtime.Rt_Printer
import net.postchain.rell.base.utils.CommonUtils
import net.postchain.rell.base.utils.PostchainGtvUtils
import net.postchain.rell.base.utils.doc.DocDefinition
import net.postchain.rell.base.utils.toImmList
import net.postchain.rell.module.RellPostchainModuleEnvironment
import net.postchain.rell.module.RellPostchainModuleFactory
import org.apache.commons.io.FileUtils
import java.io.File
import kotlin.math.max
import kotlin.math.min

private const val LINES_BEFORE = 20
private const val LINES_AFTER = 0

private const val LAST_HEIGHT = false
private const val SAVE_SYMBOLS = false
private const val USE_CURRENT_RELL_VERSION = false

fun main(args: Array<String>) {
    val dataDir = if (args.isNotEmpty()) File(args[0]) else File(System.getProperty("user.home"), "rell-blockchains")
    App(dataDir).processData()
}

private const val FILE_PATH_CONTAINS: String = ""

private class App(private val dataDir: File) {
    private val outDir = File("target")
    private val messagesFile = File(outDir, "messages.txt")
    private val symbolsFile = File(outDir, "symbols.txt")

    private val startTimeMs = System.currentTimeMillis()
    private var totCount = 0
    private var okCount = 0
    private val errFiles = mutableListOf<File>()
    private val errors = mutableSetOf<ErrorRec>()
    private val allMessages = mutableMapOf<File, List<C_Message>>()

    fun processData() {
        outDir.mkdirs()
        messagesFile.delete()
        symbolsFile.delete()

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
                for (line in it.printableLines) {
                    println("    $line")
                }
            }
        }

        val errCount = totCount - okCount
        val totTimeMs = System.currentTimeMillis() - startTimeMs
        println()
        println("Failed $errCount / $totCount (time: ${totTimeMs / 1000} sec)")

        saveMessages()
    }

    private fun saveMessages() {
        val lines = mutableListOf<String>()
        for (path in allMessages.keys.sorted()) {
            lines.add(path.path)
            for (msg in allMessages.getValue(path).sortedBy { it.code }.sortedBy { it.pos }) {
                lines.add("    $msg")
            }
            lines.add("")
        }

        appendLines(lines, messagesFile)
    }

    private fun saveDocSymbols(path: File, docModules: Map<R_ModuleName, DocDefinition>) {
        val lines = mutableListOf<String>()
        lines.add("-".repeat(100))
        lines.add(path.path)

        for (moduleName in docModules.keys.sorted()) {
            saveDocSymbols0(moduleName, listOf(), docModules.getValue(moduleName), lines, mutableSetOf())
        }

        lines.add("")
        appendLines(lines, symbolsFile)
    }

    private fun saveDocSymbols0(
        moduleName: R_ModuleName,
        path: List<String>,
        def: DocDefinition,
        lines: MutableList<String>,
        set: MutableSet<DocDefinition>,
    ) {
        if (!set.add(def)) {
            return
        }

        saveDocDefinition(moduleName, path, def, lines)

        val subDefs = def.docMembers
        for (name in subDefs.keys.sorted()) {
            val subDef = subDefs.getValue(name)
            saveDocSymbols0(moduleName, path + name, subDef, lines, set)
        }
    }

    private fun saveDocDefinition(
        moduleName: R_ModuleName,
        path: List<String>,
        def: DocDefinition,
        lines: MutableList<String>,
    ) {
        val fullName = "$moduleName:${path.joinToString(".")}"
        lines.add("")
        lines.add(fullName)
        lines.add("pos:" + (def.docSourcePos?.str() ?: "n/a"))

        val doc = def.docSymbol
        lines.add("kind:${doc.kind}")
        lines.add("name:${doc.symbolName.strCode()}")
        if (doc.mountName != null) lines.add("mount:${doc.mountName}")
        lines.add("declaration:")
        lines.add(C_IdeCompletionsUtils.docCodeToStr(doc.declaration.code))

        if (doc.comment != null) {
            lines.add("comment:")
            lines.add(doc.comment?.strCode() ?: "")
        }
    }

    private fun appendLines(lines: List<String>, file: File) {
        val text = lines.joinToString("\n")
        FileUtils.write(file, text, Charsets.UTF_8, true)
    }

    private fun processManual(dir: File) {
        processBlockchain(dir)
    }

    private fun processPmc(dir: File) {
        for (subDir in dir.listFiles().orEmpty().sorted()) {
            val name = subDir.name
            check(subDir.isDirectory) { subDir }
            check(name.matches(Regex("[0-9A-F]+(-[0-9]+)?"))) { subDir }
            processBlockchain(subDir)
        }
    }

    private fun processBlockchain(dir: File) {
        var confFiles = dir.listFiles().orEmpty().mapNotNull { file ->
            val name = file.name
            if (!file.isFile || !name.endsWith(".xml")) null else {
                check(name.endsWith(".conf.xml")) { file }
                val height = name.substringBefore('.').toInt()
                ConfFile(file, height)
            }
        }

        confFiles = confFiles.sortedBy { it.height }
        if (LAST_HEIGHT) {
            confFiles = confFiles.takeLast(1)
        }

        for (cf in confFiles) {
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
            ideDocSymbolsEnabled = true,
            useLatestRellVersion = USE_CURRENT_RELL_VERSION,
        )

        val (cRes, sourceDir) = RellPostchainModuleFactory.compileApp(gtv, env)
        allMessages[file] = cRes.messages

        if (SAVE_SYMBOLS) {
            val docModules = cRes.app?.modules.orEmpty()
                .filter { it.externalChain == null }
                .associateBy { it.name }
            saveDocSymbols(file, docModules)
        }

        if (cRes.errors.isNotEmpty()) {
            println("$file ERROR")
            for (e in cRes.errors) {
                println("    $e")
                val (lines, index) = getErrorLines(sourceDir, e.pos)
                val error = ErrorRec(e.pos, e.text, lines, index)
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

private fun getErrorLines(sourceDir: C_SourceDir, pos: S_Pos): Pair<List<String>, Int> {
    val path = pos.path()
    val file = checkNotNull(sourceDir.file(path)) { path }
    val text = file.readText()
    val lines = text.lines()

    val errLine = pos.line() - 1
    val startLine = max(errLine - LINES_BEFORE, 0)
    val endLine = min(errLine + 1 + LINES_AFTER, lines.size)
    return lines.subList(startLine, endLine) to (errLine - startLine)
}

private class ConfFile(val file: File, val height: Int)

private data class ErrorRec(
    val pos: S_Pos,
    val msg: String,
    private val lines: List<String>,
    private val lineIndex: Int,
): Comparable<ErrorRec> {
    init {
        check(lineIndex >= 0 && lineIndex < lines.size)
    }

    val printableLines: List<String> get() {
        val res = mutableListOf<String>()
        res.addAll(lines.subList(0, lineIndex + 1))
        res.add(" ".repeat(pos.column() - 1) + "^".repeat(5))
        res.addAll(lines.subList(lineIndex + 1, lines.size))
        return res.toImmList()
    }

    override fun compareTo(other: ErrorRec): Int {
        var d = pos.compareTo(other.pos)
        if (d == 0) d = msg.compareTo(other.msg)
        if (d == 0) d = lines[lineIndex].compareTo(other.lines[other.lineIndex])
        if (d == 0) d = CommonUtils.compareLists(lines, other.lines)
        return d
    }
}
