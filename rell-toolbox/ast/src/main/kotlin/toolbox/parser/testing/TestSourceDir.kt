/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.parser.testing

import net.postchain.rell.base.compiler.ast.S_RellFile
import net.postchain.rell.base.compiler.base.utils.*
import net.postchain.rell.base.model.R_LangVersion
import net.postchain.rell.base.utils.ide.IdeDirApi.mapDir
import net.postchain.rell.base.utils.ide.IdeDirApi.parseSourcePath
import net.postchain.rell.base.utils.ide.IdeFilePath
import net.postchain.rell.toolbox.compiler.RellCompilerApi
import net.postchain.rell.toolbox.compiler.RellCompilerFilePath
import net.postchain.rell.toolbox.parser.AntlrRellParser
import net.postchain.rell.toolbox.parser.SyntaxErrorCollector

object TestSourceDir {
    fun create(parser: AntlrRellParser, files: Map<String, String>): C_SourceDir {
        val map = mutableMapOf<C_SourcePath, C_SourceFile>()
        for (path in files.keys) {
            val srcPath = parseSourcePath(path)
            val text = files[path]
            val srcFile: C_SourceFile = TestSourceFile(parser, srcPath!!, text!!)
            map[srcPath] = srcFile
        }
        return mapDir(map)
    }
}

class TestSourceFile(
    private val parser: AntlrRellParser,
    private val path: C_SourcePath,
    val text: String
) : C_SourceFile() {
    private val idePath: IdeFilePath = IdeSourcePathFilePath(path)

    override fun idePath(): IdeFilePath = idePath

    override fun readAst(version: R_LangVersion): S_RellFile {
        val errorCollector = SyntaxErrorCollector()
        val parser = parser.parserFor(text, errorListeners = listOf(errorCollector))
        val root = parser.file()
        val errors = errorCollector.errors
        if (errors.isNotEmpty()) {
            throw C_CommonError("syntax", errors.joinToString("\n"))
        }
        val rcPath = RellCompilerFilePath(path, idePath)
        val tokenStream = parser.tokenStream as? org.antlr.v4.runtime.BufferedTokenStream
        val pair = RellCompilerApi.antlrToRellAst(rcPath, root, tokenStream)
        return pair.first
    }

    override fun readText(): String = text
}
