package net.postchain.rell.toolbox.core.indexer

import net.postchain.rell.base.compiler.ast.S_RellFile
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.C_Error
import net.postchain.rell.base.compiler.base.utils.IdeSourcePathFilePath
import net.postchain.rell.base.utils.ide.IdeApi
import net.postchain.rell.base.utils.ide.IdeCompilationResult
import net.postchain.rell.base.utils.ide.IdeDirApi
import net.postchain.rell.base.utils.ide.IdeModuleInfo
import net.postchain.rell.toolbox.core.compiler.RellcAPI
import net.postchain.rell.toolbox.core.compiler.RellcFilePath
import net.postchain.rell.toolbox.core.parser.AntlrRellParser
import net.postchain.rell.toolbox.core.parser.RellParser
import net.postchain.rell.toolbox.core.parser.SyntaxError
import net.postchain.rell.toolbox.core.parser.SyntaxErrorCollector
import java.io.File
import java.net.URI

class RellResourceDescription {
    val parser = AntlrRellParser()

    fun buildRellResource(workspaceURI: URI, uri: URI): Resource {
        //TODO verfiy correct path behaviour
        val parseTree = buildParseTreeWithSyntaxErrors(uri)
        val rellCompilerInfo = buildModuleInfo(workspaceURI, uri, parseTree.first)
        return Resource(parseTree.first, rellCompilerInfo.first!!)
    }

    //TODO: Remove this? Use buildRellAstWithCompilerErrors
    fun buildModuleInfo(
        workspaceURI: URI,
        uri: URI,
        parseTree: RellParser.RuleX_RootParserContext
    ): Pair<IdeModuleInfo?, List<C_Error>> {
        val relativePath = uri.toString().substring(workspaceURI.toString().length)
        val compilerSrcPath = IdeDirApi.parseSourcePath(relativePath)
        val idePath = IdeSourcePathFilePath(compilerSrcPath!!)
        val rcPath = RellcFilePath(compilerSrcPath, idePath)
        val ast = RellcAPI.antlrToRellAst(rcPath, parseTree)
        return Pair(ast.first!!.ideModuleInfo(compilerSrcPath), ast.second)
    }


    fun compileResult(
        workspaceURI: URI,
        uri: URI,
        parseTree: RellParser.RuleX_RootParserContext
    ): IdeCompilationResult {
        val rellCompilerPaths = RellCompilerPaths(workspaceURI)

        val compilerSrcPath = rellCompilerPaths.createCompilerSourcePath(uri)
        val rellCompilerFilePath = rellCompilerPaths.createRellCompilerFilePath(compilerSrcPath)
        val compilerSourceDir = rellCompilerPaths.createCompilerSourceDir()

        val astWithCompilerErrors = buildRellAstWithCompilerErrors(rellCompilerFilePath, parseTree)

        val options = C_CompilerOptions.builder()
            .symbolInfoFile(compilerSrcPath)
            .build()

        val moduleNames = IdeApi.getModuleName(compilerSrcPath, astWithCompilerErrors.first)
        return IdeApi.compile(compilerSourceDir, listOf(moduleNames!!), options)
    }


    fun buildParseTreeWithSyntaxErrors(uri: URI): Pair<RellParser.RuleX_RootParserContext, MutableList<SyntaxError>> {
        val errorListener = SyntaxErrorCollector()
        val parseTree = parser.parse(File(uri).readText(), errorListeners = listOf(errorListener))
        return Pair(parseTree, errorListener.errors)
    }

    //TODO: Should we have S_RellFile here or use module info only (can be reached through S_RellFile)
    fun buildRellAstWithCompilerErrors(
        rellCompilerFilePath: RellcFilePath,
        parseTree: RellParser.RuleX_RootParserContext
    ): Pair<S_RellFile, List<C_Error>> {
        val ast = RellcAPI.antlrToRellAst(rellCompilerFilePath, parseTree)
        val sRellFile = ast.first
        if (sRellFile != null) {
            return Pair(sRellFile, ast.second)
        } else {
            throw Exception("Could not create S_RellFile for rell compiler file: $rellCompilerFilePath")
        }
    }
}
