/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.ide

import com.github.h0tk3y.betterParse.lexer.Token
import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.core.C_CompilationResult
import net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.lib.C_LibModule
import net.postchain.rell.base.compiler.base.utils.C_ParserFilePath
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.compiler.parser.RellTokenMatch
import net.postchain.rell.base.compiler.parser.RellTokenSequence
import net.postchain.rell.base.compiler.parser.RellTokenizer
import net.postchain.rell.base.compiler.parser.S_Grammar
import net.postchain.rell.base.lib.type.Rt_TextValue
import net.postchain.rell.base.lmodel.dsl.BaseLTest
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.testutils.RellCodeTester
import net.postchain.rell.base.testutils.RellTestContext
import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.DocSymbol
import net.postchain.rell.base.utils.ide.*
import kotlin.test.assertEquals

abstract class BaseIdeSymbolTest: BaseRellTest(false) {
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
        chkSyms("struct __s { __x: $type; }",
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

    private fun getModuleName(sourceDir: C_SourceDir, file: C_SourcePath): R_ModuleName {
        val sourceFile = sourceDir.file(file)
        sourceFile ?: throw IllegalArgumentException(file.str())
        val ast = sourceFile.readAst()
        val res = IdeApi.getModuleName(file, ast)
        return res ?: throw IllegalArgumentException(file.str())
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

    private class ExpectedEntry(val key: String, val symStr: String, val extra: List<Pair<String, String>>)
    private class ActualEntry(val key: String, val ideInfo: IdeSymbolInfo, val symStr: String)
    private class TestEntry(val exp: ExpectedEntry, val act: ActualEntry)

    companion object {
        private val MAIN_FILE_PATH = C_SourcePath.parse(RellTestUtils.MAIN_FILE)

        fun getDocSymbols(code: String, extraMod: C_LibModule? = null, err: Boolean = false): Map<String, DocSymbol> {
            val tst = RellCodeTester(RellTestContext(useSql = false))
            tst.extraMod = extraMod
            val sourceDir = tst.createSourceDir(code)
            val file = RellTestUtils.MAIN_FILE_PATH
            val modSel = C_CompilerModuleSelection(immListOf(R_ModuleName.EMPTY))

            val cOpts = C_CompilerOptions.builder()
                .symbolInfoFile(file)
                .testLib(true)
                .ide(true)
                .ideDocSymbolsEnabled(true)
                .build()

            val cRes = RellTestUtils.compileApp(sourceDir, modSel, cOpts, tst.extraMod)

            if (!err) {
                check(cRes.errors.isEmpty()) { cRes.errors }
            }

            val entries = getActualEntries(sourceDir, file, cRes)
            return entries
                .mapNotNull {
                    val doc = it.ideInfo.doc
                    if (doc == null) null else (it.key to doc)
                }
                .toImmMap()
        }

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

            return syms
                    .map {
                        val ideInfo = cRes.ideSymbolInfos.getValue(it.key)
                        val symStr = ideInfoToStr(ideInfo, syms)
                        ActualEntry(it.value, ideInfo, symStr)
                    }
                    .toImmList()
        }

        private fun extractSymbols(sourceDir: C_SourceDir, file: C_SourcePath): Map<S_Pos, String> {
            val sourceFile = sourceDir.file(file)
            sourceFile ?: throw IllegalArgumentException(file.str())

            val parserPath = C_ParserFilePath(file, sourceFile.idePath())
            val code = sourceFile.readText()

            val syms = mutableMapOf<S_Pos, String>()

            val tokenizer = S_Grammar.tokenizer
            val ts = tokenizer.tokenize(parserPath, code)

            for ((t, m) in ts.toList()) {
                if (t.pattern == RellTokenizer.IDENTIFIER || t.pattern == "$") syms[m.pos] = m.text
            }

            return syms.toImmMap()
        }

        private fun RellTokenSequence.toList(): List<Pair<Token, RellTokenMatch>> {
            var seq = this
            val res = mutableListOf<Pair<Token, RellTokenMatch>>()
            while (true) {
                val next = seq.nextOrNull()
                next ?: break
                res.add(next.match.type to next.rellMatch)
                seq = next.tail
            }
            return res.toList()
        }

        private fun ideInfoToStr(ideInfo: IdeSymbolInfo, syms: Map<S_Pos, String>): String {
            val res = mutableListOf<String>()
            res.add(ideInfo.kind.name)
            res.add(ideInfo.defId?.encode() ?: "-")
            res.add(linkToStr(ideInfo.link, syms))
            return res.joinToString("|")
        }

        private fun linkToStr(link: IdeSymbolLink?, syms: Map<S_Pos, String>): String {
            return when (link) {
                null -> "-"
                is IdeModuleSymbolLink -> link.encode()
                is IdeGlobalSymbolLink -> link.encode()
                is IdeLocalSymbolLink -> {
                    val pos = link.localPos()
                    val name = syms.getValue(pos)
                    val idx = syms.entries.filter { it.value == name }.indexOfFirst { it.key == pos }
                    "local[$name:$idx]"
                }
                else -> TODO(link.javaClass.simpleName)
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
