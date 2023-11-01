package net.postchain.rell.toolbox.core.parser

import net.postchain.rell.base.compiler.ast.S_RellFile
import net.postchain.rell.base.compiler.base.utils.*
import net.postchain.rell.base.utils.ide.IdeDirApi.mapDir
import net.postchain.rell.base.utils.ide.IdeDirApi.parseSourcePath
import net.postchain.rell.base.utils.ide.IdeFilePath
import net.postchain.rell.toolbox.core.compiler.RellcAPI
import net.postchain.rell.toolbox.core.compiler.RellcFilePath

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

class TestSourceFile(private val parser: AntlrRellParser, private val path: C_SourcePath, val text: String) : C_SourceFile() {

    private val idePath: IdeFilePath

    init {
        idePath = IdeSourcePathFilePath(path)
    }

    override fun idePath(): IdeFilePath {
        return idePath
    }

    override fun readAst(): S_RellFile {
        val errorCollector = SyntaxErrorCollector()
        val root = parser.parse(text, errorListeners = listOf(errorCollector))
        val errors = errorCollector.errors
        if (errors.isNotEmpty()) {
            throw C_CommonError("syntax", errors.joinToString("\n"))
        }
        val rcPath = RellcFilePath(path, idePath)
        val pair = RellcAPI.antlrToRellAst(rcPath, root)
        val ast = pair.first
        check(ast != null)
        return ast
    }

    override fun readText(): String {
        return text
    }
}