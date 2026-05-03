/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.ide

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.core.C_CompilationResult
import net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.C_ParserFilePath
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.compiler.base.utils.readAstEx
import net.postchain.rell.base.compiler.parser.RellTokenInput
import net.postchain.rell.base.compiler.parser.RellTokenizer
import net.postchain.rell.base.compiler.parser.RellTokens
import net.postchain.rell.base.lmodel.dsl.BaseLTest
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.runtime.Rt_TextValue
import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.DocSymbol
import net.postchain.rell.base.utils.ide.*
import kotlin.test.assertEquals

abstract class BaseIdeSymbolTest: BaseRellTest() {
    protected var docDeclarationsEnabled = true
    protected var docCommentsEnabled = false

    protected fun replaceSymInfo(
        info: String,
        name: String? = null,
        kind: String? = null,
        defId: String? = null,
        link: String? = null,
    ): String {
        val m = Regex("^([A-Za-z_][A-Za-z0-9_]*)=([^|]+)[|]([^|]+)[|]([^|]+)$").matchEntire(info)
        checkNotNull(m) { info }
        val (oldName, oldKind, oldDefId, oldLink) = m.destructured
        return "${name?:oldName}=${kind?:oldKind}|${defId?:oldDefId}|${link?:oldLink}"
    }

    protected fun chkSymsExpr(expr: String, vararg expected: String, err: String? = null) {
        chkSyms("function __main() = $expr;", "__main=DEF_FUNCTION|function[__main]|-", *expected, err = err)
    }

    protected fun chkSymsStmt(stmt: String, vararg expected: String, err: String? = null) {
        chkSyms("function __main() { $stmt }", "__main=DEF_FUNCTION|function[__main]|-", *expected, err = err)
    }

    protected fun chkSymsType(type: String, vararg expected: String, err: String? = null) {
        chkSyms(
            "struct __s { __x: $type; }",
            "__s=DEF_STRUCT|struct[__s]|-",
            "__x=MEM_STRUCT_ATTR|struct[__s].attr[__x]|-",
            *expected,
            err = err,
        )
    }

    protected fun chkSyms(
        code: String,
        vararg expected: String,
        err: String? = null,
        warn: String? = null,
        ide: Boolean = false,
    ) {
        chkSyms0(code, MAIN_FILE_PATH, err, warn, expected.toList(), ide = ide)
    }

    protected fun chkSymsFile(
        file: String,
        vararg expected: String,
        err: String? = null,
        warn: String? = null,
    ) {
        chkSyms0("", C_SourcePath.parse(file), err, warn, expected.toList(), ide = true)
    }

    private fun chkSyms0(
        code: String,
        file: C_SourcePath,
        expectedErr: String?,
        expectedWarn: String?,
        expected: List<String>,
        ide: Boolean,
    ) {
        val sourceDir = tst.createSourceDir(code)
        val cRes = compileCode(sourceDir, file, ide = ide)

        chkMessages(cRes, expectedErr, expectedWarn)

        val testEntries = getTestEntries(sourceDir, file, cRes, expected)
        assertSyms(testEntries)
    }

    private fun compileCode(sourceDir: C_SourceDir, file: C_SourcePath, ide: Boolean): C_CompilationResult {
        val moduleName = getModuleName(sourceDir, file)
        val modSel = C_CompilerModuleSelection(immListOf(moduleName))

        val cOpts = C_CompilerOptions.builder(tst.compilerOptions())
            .symbolInfoFile(file)
            .ide(ide)
            .ideDocSymbolsEnabled(true)
            .build()

        val cRes = RellTestUtils.compileApp(sourceDir, modSel, cOpts, tst.extraMod)
        return cRes
    }

    private fun getModuleName(sourceDir: C_SourceDir, file: C_SourcePath): ModuleName {
        val sourceFile = sourceDir.file(file)
        requireNotNull(sourceFile) { file.str() }
        val ast = sourceFile.readAstEx(tst.compatibilityVer)
        val res = IdeApi.getModuleName(file, ast)
        return requireNotNull(res) { file.str() }
    }

    private fun getTestEntries(
        sourceDir: C_SourceDir,
        file: C_SourcePath,
        cRes: C_CompilationResult,
        expectedStrings: List<String>,
    ): List<TestEntry> {
        val expectedPairs = expectedStrings.map {
            val parts = it.split("=", limit = 2)
            checkEquals(parts.size, 2) { it }
            parts.toPair()
        }

        val symsList = getActualEntries(sourceDir, file, cRes)

        val expectedEntries0 = getExpectedEntries(expectedPairs)
        val expKeys = expectedEntries0.map { it.key }
        val actualEntries0 = symsList.filter { it.key in expKeys }
        val actKeys = actualEntries0.map { it.key }
        assertEquals(expKeys, actKeys, "Keys differ")

        val testEntries = expKeys.indices.map { TestEntry(expectedEntries0[it], actualEntries0[it]) }
        return testEntries
    }

    private fun getExpectedEntries(expected: List<Pair<String, String>>): List<ExpectedEntry> {
        val res = mutableListOf<ExpectedEntry>()
        val queue = expected.toMutableList()
        while (queue.isNotEmpty()) {
            val (name, exp) = queue.removeFirst()
            val extras = mutableListOf<Pair<String, String>>()
            while (queue.isNotEmpty() && queue.first().first.startsWith("?")) {
                extras.add(queue.removeFirst())
            }
            res.add(ExpectedEntry(name, exp, extras.toImmList()))
        }
        return res.toImmList()
    }

    private fun assertSyms(testEntries: List<TestEntry>) {
        val diffList = mutableListOf<Pair<String, String>>()
        for (e in testEntries) {
            if (!matchSym(e.act.symStr, e.exp.symStr)) {
                val name = e.exp.key
                val expStr = encodeStr(e.exp.symStr)
                val actStr = encodeStr(e.act.symStr)
                diffList.add("$name=$expStr" to "$name=$actStr")
            }
            for (extra in e.exp.extra) {
                val doc = e.act.ideInfo.doc
                val actExtra = if (doc == null) "-" else when (extra.first) {
                    "?name" -> doc.symbolName.strCode()
                    "?head" -> BaseLTest.getDocHeaderStr(doc)
                    "?doc" -> docToStr(doc, declaration = docDeclarationsEnabled, comment = docCommentsEnabled)
                    else -> throw IllegalArgumentException(extra.first)
                }

                val expExtra = extra.second
                if (actExtra != expExtra) {
                    diffList.add("${extra.first}=$expExtra" to "${extra.first}=$actExtra")
                }
            }
        }

        val diffExpList = diffList.map { it.first }
        val diffActList = diffList.map { it.second }
        assertEquals(diffExpList, diffActList)
    }

    private fun encodeStr(s: String): String {
        return s.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t")
    }

    private fun matchSym(actual: String, expected: String): Boolean {
        val pat = Rt_TextValue.likePatternToRegex(expected, '?', '*')
        return pat.matcher(actual).matches()
    }

    private class ExpectedEntry(val key: String, val symStr: String, val extra: ImmList<Pair<String, String>>)
    private class ActualEntry(val key: String, val ideInfo: IdeSymbolInfo, val symStr: String)
    private class TestEntry(val exp: ExpectedEntry, val act: ActualEntry)

    internal companion object {
        private val MAIN_FILE_PATH = C_SourcePath.parse(RellTestUtils.MAIN_FILE)

        fun chkMessages(cRes: C_CompilationResult, err: String? = null, warn: String? = null) {
            val actualErr = if (cRes.errors.isEmpty()) "n/a" else RellTestUtils.msgsToString(cRes.errors)
            assertEquals(err ?: "n/a", actualErr)

            val actualWarn = if (cRes.warnings.isEmpty()) "n/a" else RellTestUtils.msgsToString(cRes.warnings)
            assertEquals(warn ?: "n/a", actualWarn)
        }

        private fun getActualEntries(
            sourceDir: C_SourceDir,
            file: C_SourcePath,
            cRes: C_CompilationResult,
        ): List<ActualEntry> {
            val syms = extractSymbols(sourceDir, file)

            for (pos in cRes.ideSymbolInfos.keys) {
                if (pos.path() == file) {
                    check(pos in syms) { "$pos:${cRes.ideSymbolInfos[pos]} $syms ${cRes.ideSymbolInfos}" }
                }
            }

            return syms.mapToImmList {
                val ideInfo = cRes.ideSymbolInfos.getValue(it.key)
                val symStr = ideInfoToStr(ideInfo, syms)
                ActualEntry(it.value, ideInfo, symStr)
            }
        }

        private fun extractSymbols(sourceDir: C_SourceDir, file: C_SourcePath): Map<S_Pos, String> {
            val sourceFile = requireNotNull(sourceDir.file(file)) { file.str() }

            val parserPath = C_ParserFilePath(file, sourceFile.idePath())
            val code = sourceFile.readText()

            val syms = mutableMapOf<S_Pos, String>()

            val tokenizer = RellTokenizer()
            val tp = tokenizer.tokenProducer(parserPath, code)

            while (true) {
                val tm = tp.nextToken() ?: break
                val ti = tm.input as RellTokenInput
                val m = ti.match
                if (ti.token.pattern == RellTokens.IDENTIFIER || ti.token.pattern == "$") syms[m.pos] = m.text
            }

            return syms.toImmMap()
        }

        private fun ideInfoToStr(ideInfo: IdeSymbolInfo, syms: Map<S_Pos, String>): String {
            val res = mutableListOf<String>()
            res.add(ideInfo.kind.name)
            res.add(ideInfo.defId?.encode() ?: "-")
            res.add(linkToStr(ideInfo.link, syms))
            return res.joinToString("|")
        }

        private fun linkToStr(link: IdeSymbolLink?, syms: Map<S_Pos, String>): String = when (link) {
            null -> "-"
            is IdeModuleSymbolLink -> link.encode()
            is IdeGlobalSymbolLink -> link.encode()
            is IdeLocalSymbolLink -> {
                val pos = link.localPos()
                val name = syms.getValue(pos)
                val idx = syms.entries.filter { it.value == name }.indexOfFirst { it.key == pos }
                "local[$name:$idx]"
            }
        }

        private fun docToStr(doc: DocSymbol, declaration: Boolean, comment: Boolean): String {
            val parts = mutableListOf<String>()

            parts.add(doc.kind.name)
            parts.add(doc.symbolName.strCode())

            val mountName = doc.mountName
            if (mountName != null) {
                parts.add(mountName)
            }

            if (declaration) {
                parts.add(doc.declaration.code.strCode())
            }

            if (comment) {
                parts.add(doc.comment?.strCode() ?: "-")
            }

            return parts.joinToString("|")
        }
    }
}
