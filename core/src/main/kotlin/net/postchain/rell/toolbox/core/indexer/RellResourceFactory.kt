package net.postchain.rell.toolbox.core.indexer

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
import java.util.concurrent.ConcurrentHashMap


class RellResourceFactory(private val workspaceUri: URI, private val parser: AntlrRellParser) {
    val rellCompilerUtils = RellCompilerUtils()

    fun buildFileMap(sources: Map<URI, String>): ConcurrentHashMap<C_SourcePath, C_SourceFile> {
        val fileMap = ConcurrentHashMap<C_SourcePath, C_SourceFile>()
        sources.forEach { (fileUri, fileContent) ->
            val (sourcePath, sourceFile) = buildCSourceFile(fileUri, fileContent)
            fileMap[sourcePath] = sourceFile
        }
        return fileMap
    }

    fun buildCSourceFile(fileUri: URI, fileContent: String): Pair<C_SourcePath, C_SourceFile> {
        val rellCompilerSourcePath = rellCompilerUtils.createCompilerSourcePath(fileUri, workspaceUri)
        val antlrParseTree = buildParseTreeWithSyntaxErrors(fileContent)
        val ast = buildRellAstWithCompilerErrors(rellCompilerSourcePath, antlrParseTree.first)
        return rellCompilerSourcePath to AstSourceFile.make(ast.first, IdeSourcePathFilePath(rellCompilerSourcePath))
    }

    fun updateFileMap(fileMap: MutableMap<C_SourcePath, C_SourceFile>, fileUri: URI, fileContent: String? = null) {
        val (sourcePath, sourceFile) = buildCSourceFile(fileUri, fileContent ?: File(fileUri).readText())
        fileMap[sourcePath] = sourceFile
    }

    fun buildRellResource(fileUri: URI, fileContent: String, fileMap:  MutableMap<C_SourcePath, C_SourceFile>): Resource {
        val rellCompilerSourcePath = rellCompilerUtils.createCompilerSourcePath(fileUri, workspaceUri)
        val antlrParseTree = buildParseTreeWithSyntaxErrors(fileContent)
        val ast = buildRellAstWithCompilerErrors(rellCompilerSourcePath, antlrParseTree.first)
        val compilationResult = compileResult(rellCompilerSourcePath, ast.first, fileMap)
        val symbolInfo = compilationResult?.symbolInfos ?: mapOf()
        val locationInfo = createLocationInfo(symbolInfo)

        return Resource(
            antlrParseTree.first,
            ast.first.ideModuleInfo(rellCompilerSourcePath),
            fileUri,
            workspaceUri,
            ast.first,
            antlrParseTree.second,
            compilationResult?.messages ?: listOf(),
            symbolInfo,
            locationInfo
        )
    }

    fun buildRellResource(fileUri: URI, fileMap:  MutableMap<C_SourcePath, C_SourceFile>): Resource {
        val fileContent = File(fileUri).readText()
        return buildRellResource(fileUri, fileContent, fileMap)
    }

    fun compileResult(
        compilerSrcPath: C_SourcePath, ast: S_RellFile,
        fileMap:  MutableMap<C_SourcePath, C_SourceFile>
    ): IdeCompilationResult? {
        // Having a workspace uri that ends with rell means we have a single file indexer.
        // Similar to java we have decided to not compile single file,
        // because how it will affect context awareness of imported modules.
        if (workspaceUri.path.endsWith(".rell")) {
            return null
        }
        if (compilerSrcPath.str().contains("-")) {
            return rellCompilerUtils.createInvalidFileCompilationResult(compilerSrcPath)
        }

        val moduleName = IdeApi.getModuleName(compilerSrcPath, ast)
            ?: throw Exception("Can not find the moduleName for $compilerSrcPath")

        val options = C_CompilerOptions.builder().symbolInfoFile(compilerSrcPath).ide(true).build()
        val idePath = IdeSourcePathFilePath(compilerSrcPath)
        val mainFile = AstSourceFile.make(ast, idePath)
        fileMap[compilerSrcPath] = mainFile
        val selfDir = IdeDirApi.mapDir(fileMap)
        return try {
            IdeApi.compile(
                selfDir, listOf(moduleName), options
            )
        } catch (e: Throwable) {
            logger.error(e) { "Compilation failed for file: ${compilerSrcPath.str()}" }
            null
        }
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
        val rellCompilerFilePath = rellCompilerUtils.createRellCompilerFilePath(rellCompilerSourcePath)
        return RellcAPI.antlrToRellAst(rellCompilerFilePath, parseTree)
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
