package net.postchain.rell.toolbox.core.indexer

import net.postchain.rell.base.compiler.ast.S_RellFile
import net.postchain.rell.base.compiler.base.utils.C_CommonError
import net.postchain.rell.base.compiler.base.utils.C_SourceFile
import net.postchain.rell.base.utils.ide.IdeFilePath


class AstSourceFile private constructor(ast: S_RellFile, ideFilePath: IdeFilePath) : C_SourceFile() {
    private val ast: S_RellFile
    private val ideFilePath: IdeFilePath

    init {
        this.ast = ast
        this.ideFilePath = ideFilePath
    }

    override fun idePath(): IdeFilePath {
        return ideFilePath
    }

    override fun readAst(): S_RellFile {
        return ast
    }

    override fun readText(): String {
        val cls = javaClass.getSimpleName()
        throw C_CommonError("$cls:readText", "readText() not supported")
    }

    companion object {
        fun make(ast: S_RellFile, ideFilePath: IdeFilePath): C_SourceFile {
            return AstSourceFile(ast, ideFilePath)
        }
    }
}
