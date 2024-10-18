package net.postchain.rell.toolbox.compiler

import net.postchain.rell.base.compiler.ast.S_RellFile
import net.postchain.rell.base.compiler.base.utils.C_CommonError
import net.postchain.rell.base.compiler.base.utils.C_SourceFile
import net.postchain.rell.base.utils.ide.IdeFilePath

class AstSourceFile private constructor(
    private val ast: S_RellFile,
    private val ideFilePath: IdeFilePath,
    private val text: String,
) : C_SourceFile() {

    override fun idePath(): IdeFilePath {
        return ideFilePath
    }

    override fun readAst(): S_RellFile {
        return ast
    }

    override fun readText(): String {
        return text
    }

    companion object {
        fun make(ast: S_RellFile, ideFilePath: IdeFilePath, text: String): C_SourceFile {
            return AstSourceFile(ast, ideFilePath, text)
        }
    }
}
