package net.postchain.rell.toolbox.core.indexer

import net.postchain.rell.base.compiler.ast.S_RellFile
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.C_Error
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.utils.ide.IdeApi
import net.postchain.rell.base.utils.ide.IdeCompilationResult
import net.postchain.rell.toolbox.core.compiler.RellcAPI
import net.postchain.rell.toolbox.core.parser.AntlrRellParser
import net.postchain.rell.toolbox.core.parser.RellParser
import net.postchain.rell.toolbox.core.parser.SyntaxError
import net.postchain.rell.toolbox.core.parser.SyntaxErrorCollector
import java.io.File
import java.net.URI

class RellResourceDescription(private val workspaceURI: URI) {
    val parser = AntlrRellParser()
    private val rellCompilerPaths = RellCompilerPaths(workspaceURI)
    fun buildRellResource(uri: URI): Resource {
        val rellCompilerSourcePath = rellCompilerPaths.createCompilerSourcePath(uri)

        val parseTree = buildParseTreeWithSyntaxErrors(uri)
        val rellAst = buildRellAstWithCompilerErrors(rellCompilerSourcePath, parseTree.first)
        //TODO: Add compilation results to resource
//        val compileResult = compileResult(rellCompilerSourcePath, rellAst.first)
        return Resource(parseTree.first, rellAst.first.ideModuleInfo(rellCompilerSourcePath)!!)
    }

    fun compileResult(compilerSrcPath: C_SourcePath, sRellfile: S_RellFile): IdeCompilationResult {
        val compilerSourceDir = rellCompilerPaths.createCompilerSourceDir()

        val options = C_CompilerOptions.builder()
            .symbolInfoFile(compilerSrcPath)
            .build()

        val moduleNames = IdeApi.getModuleName(compilerSrcPath, sRellfile)
        return IdeApi.compile(compilerSourceDir, listOf(moduleNames!!), options)
    }

    fun buildParseTreeWithSyntaxErrors(uri: URI): Pair<RellParser.RuleX_RootParserContext, MutableList<SyntaxError>> {
        val errorListener = SyntaxErrorCollector()
        val parseTree = parser.parse(File(uri).readText(), errorListeners = listOf(errorListener))
        return Pair(parseTree, errorListener.errors)
    }

    //TODO: Should we have S_RellFile here or use module info only (can be reached through S_RellFile)
    fun buildRellAstWithCompilerErrors(
        rellCompilerSourcePath: C_SourcePath,
        parseTree: RellParser.RuleX_RootParserContext
    ): Pair<S_RellFile, List<C_Error>> {
        val rellCompilerFilePath = rellCompilerPaths.createRellCompilerFilePath(rellCompilerSourcePath)
        val ast = RellcAPI.antlrToRellAst(rellCompilerFilePath, parseTree)
        val sRellFile = ast.first
        if (sRellFile != null) {
            return Pair(sRellFile, ast.second)
        } else {
            throw Exception("Could not create S_RellFile for rell compiler file: $rellCompilerFilePath")
        }
    }
}
