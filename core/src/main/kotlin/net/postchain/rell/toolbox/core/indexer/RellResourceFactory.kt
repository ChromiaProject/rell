package net.postchain.rell.toolbox.core.indexer

import net.postchain.rell.base.compiler.ast.S_RellFile
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.*
import net.postchain.rell.base.utils.ide.IdeApi
import net.postchain.rell.base.utils.ide.IdeCompilationResult
import net.postchain.rell.toolbox.core.compiler.RellcAPI
import net.postchain.rell.toolbox.core.parser.AntlrRellParser
import net.postchain.rell.toolbox.core.parser.RellParser
import net.postchain.rell.toolbox.core.parser.SyntaxError
import net.postchain.rell.toolbox.core.parser.SyntaxErrorCollector
import java.io.File
import java.net.URI

class RellResourceFactory(workspaceURI: URI) {
    val parser = AntlrRellParser()
    val rellCompilerPaths = RellCompilerPaths(workspaceURI)
    fun buildRellResource(uri: URI, fileCompilerSourceDir: C_SourceDir): Resource {
        //TODO maybe change it to constructor
        val fileContent = File(uri).readText()
        val rellCompilerSourcePath = rellCompilerPaths.createCompilerSourcePath(uri)
        val antlrParseTree = buildParseTreeWithSyntaxErrors(fileContent)
        val ast = buildRellAstWithCompilerErrors(rellCompilerSourcePath, antlrParseTree.first)
        return Resource(
            antlrParseTree.first,
            ast.first.ideModuleInfo(rellCompilerSourcePath),
            buildCSourceFile(rellCompilerSourcePath, fileContent),
            ast.first,
            antlrParseTree.second,
            compileResult(rellCompilerSourcePath, ast.first, fileCompilerSourceDir).messages
        )
    }

    private fun buildCSourceFile(rellCompilerSourcePath: C_SourcePath, fileContent: String): C_SourceFile {
        return C_TextSourceFile(rellCompilerSourcePath, fileContent)
    }

    fun compileResult(
        compilerSrcPath: C_SourcePath,
        sRellfile: S_RellFile,
        fileCompilerSourceDir: C_SourceDir
    ): IdeCompilationResult {
        val options = C_CompilerOptions.builder()
            .symbolInfoFile(compilerSrcPath)
            .build()

        val moduleNames = IdeApi.getModuleName(compilerSrcPath, sRellfile)
        return IdeApi.compile(fileCompilerSourceDir, listOf(moduleNames!!), options)
    }

    fun buildParseTreeWithSyntaxErrors(fileContent: String): Pair<RellParser.RuleX_RootParserContext, MutableList<SyntaxError>> {
        val errorListener = SyntaxErrorCollector()
        val parseTree = parser.parse(fileContent, errorListeners = listOf(errorListener))
        return Pair(parseTree, errorListener.errors)
    }

    fun buildRellAstWithCompilerErrors(
        rellCompilerSourcePath: C_SourcePath,
        parseTree: RellParser.RuleX_RootParserContext
    ): Pair<S_RellFile, List<C_Error>> {
        val rellCompilerFilePath = rellCompilerPaths.createRellCompilerFilePath(rellCompilerSourcePath)
        return RellcAPI.antlrToRellAst(rellCompilerFilePath, parseTree)
    }
}
