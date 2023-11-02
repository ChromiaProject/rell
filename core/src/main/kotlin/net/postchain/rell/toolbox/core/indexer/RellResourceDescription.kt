package net.postchain.rell.toolbox.core.indexer

import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.C_Error
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
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
    private val errorListener = SyntaxErrorCollector()
    fun buildRellResource(workspaceURI: URI, uri: URI): Resource {
        //TODO verfiy correct path behaviour
        val parseTree = buildParseTreeWithSyntaxErrors(uri)
        val rellCompilerInfo = buildModuleInfo(workspaceURI, uri, parseTree.first)
        return Resource(parseTree.first, rellCompilerInfo.first!!)
    }

    fun buildModuleInfo(workspaceURI: URI, uri: URI, parseTree: RellParser.RuleX_RootParserContext): Pair<IdeModuleInfo?, List<C_Error>> {
        val relativePath = uri.toString().substring(workspaceURI.toString().length)
        val compilerSrcPath = IdeDirApi.parseSourcePath(relativePath)
        val idePath = IdeSourcePathFilePath(compilerSrcPath!!)
        val rcPath = RellcFilePath(compilerSrcPath, idePath)
        val ast = RellcAPI.antlrToRellAst(rcPath, parseTree)
        return Pair(ast.first!!.ideModuleInfo(compilerSrcPath), ast.second)
    }


    fun compileResult(workspaceURI: URI, uri: URI, parseTree: RellParser.RuleX_RootParserContext, sourceDir : C_SourceDir): IdeCompilationResult {
        //1 ModuleNames = IdeApi.getModuleName(cFilePath, ast);
        //2 Source Dir
        var options = COMPILER_OPTIONS
        //Handle paths

        val relativePath = uri.toString().substring(workspaceURI.toString().length)
        val compilerSrcPath = IdeDirApi.parseSourcePath(relativePath)
        val idePath = IdeSourcePathFilePath(compilerSrcPath!!)
        val rcPath = RellcFilePath(compilerSrcPath, idePath)
        // Build S_RellFile
        val ast = RellcAPI.antlrToRellAst(rcPath, parseTree)

        options = C_CompilerOptions.builder(options)
            .symbolInfoFile(compilerSrcPath)
            .build()
        //Module name
        val moduleNames = IdeApi.getModuleName(compilerSrcPath, ast.first!!)
        val x = IdeApi.compile(sourceDir, listOf(moduleNames!!), options)
        return x
    }

    fun buildParseTreeWithSyntaxErrors(uri: URI): Pair<RellParser.RuleX_RootParserContext, MutableList<SyntaxError>> {
        val parseTree = parser.parse(File(uri).readText(), errorListeners = listOf(errorListener))
        return Pair(parseTree, errorListener.errors)
    }
}
