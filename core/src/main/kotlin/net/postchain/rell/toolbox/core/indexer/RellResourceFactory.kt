package net.postchain.rell.toolbox.core.indexer

import com.google.common.collect.ImmutableMap
import io.github.oshai.kotlinlogging.KotlinLogging
import net.postchain.rell.base.compiler.ast.S_RellFile
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.C_Error
import net.postchain.rell.base.compiler.base.utils.C_SourceFile
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.compiler.base.utils.IdeSourcePathFilePath
import net.postchain.rell.base.utils.ide.IdeApi
import net.postchain.rell.base.utils.ide.IdeCompilationResult
import net.postchain.rell.base.utils.ide.IdeDirApi
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
    private val logger = KotlinLogging.logger {}
    fun buildRellResource(uri: URI, fileCompilerSourceMap: MutableMap<C_SourcePath, C_SourceFile>): Resource {

        //TODO maybe change it to constructor
        val fileContent = File(uri).readText()
        val rellCompilerSourcePath = rellCompilerPaths.createCompilerSourcePath(uri)
        val antlrParseTree = buildParseTreeWithSyntaxErrors(fileContent)
        val ast = buildRellAstWithCompilerErrors(rellCompilerSourcePath, antlrParseTree.first)
        return Resource(
            antlrParseTree.first,
            ast.first.ideModuleInfo(rellCompilerSourcePath),
            ast.first,
            antlrParseTree.second,
            compileResult(rellCompilerSourcePath, ast.first, fileCompilerSourceMap).messages
        )
    }

    fun buildRellResource(resource: Resource, uri: URI, fileCompilerSourceMap: MutableMap<C_SourcePath, C_SourceFile>): Resource {
        val rellCompilerSourcePath = rellCompilerPaths.createCompilerSourcePath(uri)
        return Resource(
            resource.parseTree,
            resource.moduleInfo,
            resource.ast,
            resource.syntaxErrors,
            compileResult(rellCompilerSourcePath, resource.ast, fileCompilerSourceMap).messages
        )
    }

    fun compileResult(
        compilerSrcPath: C_SourcePath, ast: S_RellFile, compoundSourceMap: MutableMap<C_SourcePath, C_SourceFile>
    ): IdeCompilationResult {
        val options = C_CompilerOptions.builder().symbolInfoFile(compilerSrcPath).ide(true).build()

        val moduleName = IdeApi.getModuleName(compilerSrcPath, ast) ?: throw Exception("Can not find the moduleName for $compilerSrcPath")

        val idePath = IdeSourcePathFilePath(compilerSrcPath)
        val mainFile = AstSourceFile.make(ast, idePath)
        val fileMap = ImmutableMap.of(compilerSrcPath, mainFile)
        val selfDir = IdeDirApi.mapDir(fileMap)
        compoundSourceMap.putAll(fileMap)

        return IdeApi.compile(
            CompoundSourceDir(selfDir, IdeDirApi.mapDir(compoundSourceMap)), listOf(moduleName), options
        )
    }

    fun buildParseTreeWithSyntaxErrors(fileContent: String): Pair<RellParser.RuleX_RootParserContext, MutableList<SyntaxError>> {
        val errorListener = SyntaxErrorCollector()
        return try {
            val parseTree = parser.parse(fileContent, errorListeners = listOf(errorListener))
            Pair(parseTree, errorListener.errors)
        } catch (e: Exception) {
            //If error occurred we build a parse tree from an empty string instead. This is so that we can continue
            //with the flow of building the whole project
            logger.debug { "Stacktrace for failure for building parse tree: $e" }
            val parseTree = parser.parse("", errorListeners = listOf(errorListener))
            Pair(parseTree, errorListener.errors)
        }
    }

    fun buildRellAstWithCompilerErrors(
        rellCompilerSourcePath: C_SourcePath, parseTree: RellParser.RuleX_RootParserContext
    ): Pair<S_RellFile, List<C_Error>> {
        val rellCompilerFilePath = rellCompilerPaths.createRellCompilerFilePath(rellCompilerSourcePath)
        return RellcAPI.antlrToRellAst(rellCompilerFilePath, parseTree)
    }
}
