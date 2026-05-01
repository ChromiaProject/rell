/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.testutils

import net.postchain.rell.base.compiler.base.core.C_CompilationResult
import net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.*
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.ide.IdeCodeSnippet
import net.postchain.rell.base.utils.ide.IdeSnippetMessage
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object TestSnippetsRecorder {
    private const val RELL_BASE_PACKAGE_NAME = "net.postchain.rell"

    private val ENABLED = System.getProperty("test.snippets.recorder.enabled", "false").toBoolean()
    private val ZIP_FILE: Boolean = System.getProperty("test.snippets.recorder.zipfile", "true").toBoolean()
    private val SOURCES_TARGET: String =
        System.getProperty("test.snippets.recorder.target", System.getProperty("user.home"))

    private val SOURCES_FILE: String = "${SOURCES_TARGET}/testsources-${RellVersions.VERSION_STR}.zip"

    private val sync = Any()
    private val snippets = mutableMapOf<String, MutableSet<IdeCodeSnippet>>()
    private var shutdownHookInstalled = false

    fun recordParsing(
        sourceDir: C_SourceDir,
        modules: C_CompilerModuleSelection,
        options: C_CompilerOptions,
        res: C_CompilationResult,
    ) {
        if (!ENABLED) return

        val files = sourceDirToMap(sourceDir)
        val messages = res.messages.mapToImmList { IdeSnippetMessage(it.pos.str(), it.type, it.code, it.text) }
        val parsing = makeParsing(files)
        val comments = makeComments(res)

        val snippetFilePath = populateSnippetFilePath()
        val snippet = IdeCodeSnippet(files, modules, options, messages, parsing, comments)
        addSnippet(snippetFilePath, snippet)
    }

    private fun populateSnippetFilePath(): String {
        val stackTraceElements = Thread.currentThread().stackTrace
        val caller = stackTraceElements.findLast { it.className.startsWith(RELL_BASE_PACKAGE_NAME) }

        return if (caller != null) {
            "${caller.className}/${caller.methodName}.json"
        } else {
            error("Unable to find caller of TestSnippetsRecorder.record()")
        }
    }

    private fun sourceDirToMap(sourceDir: C_SourceDir): ImmMap<String, String> {
        val map = mutableMapOf<C_SourcePath, String>()
        sourceDirToMap0(sourceDir, C_SourcePath.EMPTY, map)
        return map.mapKeysToImmMap { (k, _) -> k.str() }
    }

    private fun sourceDirToMap0(sourceDir: C_SourceDir, path: C_SourcePath, map: MutableMap<C_SourcePath, String>) {
        for (file in sourceDir.files(path)) {
            val subPath = path.add(file)
            check(subPath !in map) { "File already in the map: $subPath" }
            val text = sourceDir.file(subPath)!!.readText()
            map[subPath] = text
        }

        for (dir in sourceDir.dirs(path)) {
            val subPath = path.add(dir)
            sourceDirToMap0(sourceDir, subPath, map)
        }
    }

    private fun makeParsing(files: Map<String, String>): ImmMap<String, List<IdeSnippetMessage>> {
        val res = mutableMapOf<String, List<IdeSnippetMessage>>()

        for ((file, code) in files) {
            val sourcePath = C_SourcePath.parse(file)
            val idePath = IdeSourcePathFilePath(sourcePath)
            val messages = try {
                C_Parser.parse(sourcePath, idePath, code)
                listOf()
            } catch (e: C_Error) {
                listOf(IdeSnippetMessage(e.pos.str(), C_MessageType.ERROR, e.code, e.errMsg))
            }
            res[file] = messages
        }

        return res.toImmMap()
    }

    private fun makeComments(cRes: C_CompilationResult): ImmMap<String, String> {
        return cRes.app?.let { C_DocUtils.getAllComments(it) }.orEmpty()
    }

    private fun addSnippet(snippetFilePath: String, snippet: IdeCodeSnippet) {
        synchronized (sync) {
            snippets.getOrPut(snippetFilePath) { mutableSetOf() }.add(snippet)
            if (!shutdownHookInstalled) {
                val thread = Thread(TestSnippetsRecorder::saveSources)
                thread.name = "SaveSources"
                thread.isDaemon = false
                Runtime.getRuntime().addShutdownHook(thread)
                shutdownHookInstalled = true
            }
        }
    }

    private fun saveSources() {
        synchronized (sync) {
            try {
                if (ZIP_FILE) {
                    saveSourcesZipFile(File(SOURCES_FILE))
                } else {
                    saveSourcesToFolder(File(SOURCES_TARGET))
                }
            } catch (e: Throwable) {
                System.err.println("Snippets saving failed")
                e.printStackTrace()
            }
        }
    }

    private fun saveSourcesToFolder(f: File) {
        snippets.forEach { (path, snippetSet) ->
            val entries = IdeCodeSnippet.serialize(snippetSet)
            with(File(f, path)) {
                parentFile.mkdirs()
                writeText(entries)
            }
        }
        println("Test snippets (${snippets.values.sumOf { it.size }}) written to folder: $SOURCES_TARGET")
    }

    private fun saveSourcesZipFile(f: File) {
        f.parentFile.mkdirs()
        FileOutputStream(f).use { fout ->
            ZipOutputStream(fout).use { zout ->
                for ((filePath, snippetSet) in snippets) {
                    val serialized = IdeCodeSnippet.serialize(snippetSet)
                    zout.putNextEntry(ZipEntry(filePath))
                    zout.write(serialized.toByteArray())
                }
            }
        }
        val count = snippets.values.sumOf { it.size }
        printNotice(count, f)
    }

    private fun printNotice(count: Int, f: File) {
        println("Test snippets ($count) written to file: $f")
    }
}
