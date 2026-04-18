/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.indexer

import io.github.oshai.kotlinlogging.KotlinLogging
import net.postchain.rell.base.compiler.ast.S_RellFile
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.C_Error
import net.postchain.rell.base.compiler.base.utils.C_SourceFile
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.compiler.base.utils.IdeSourcePathFilePath
import net.postchain.rell.base.model.R_LangVersion
import net.postchain.rell.base.utils.ide.IdeApi
import net.postchain.rell.base.utils.ide.IdeCompilationResult
import net.postchain.rell.base.utils.ide.IdeDirApi
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.toolbox.chromia.ChromiaModelProvider
import net.postchain.rell.toolbox.compiler.AstSourceFile
import net.postchain.rell.toolbox.compiler.RellCompilerApi
import net.postchain.rell.toolbox.parser.AntlrRellParser
import net.postchain.rell.toolbox.parser.RellCommonTokenStream
import net.postchain.rell.toolbox.parser.RellParser
import net.postchain.rell.toolbox.parser.SyntaxErrorCollector
import org.antlr.v4.runtime.TokenStream
import java.io.File
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

class RellResourceFactory(
    private val workspaceUri: URI,
    private val parser: AntlrRellParser,
    private val chromiaModelProvider: ChromiaModelProvider
) {
    val rellCompilerUtils = RellCompilerUtils()
    fun buildFileMap(sources: Map<URI, String>): ConcurrentHashMap<C_SourcePath, C_SourceFile> {
        val fileMap = ConcurrentHashMap<C_SourcePath, C_SourceFile>()
        sources.forEach { (fileUri, fileContent) ->
            val (sourcePath, sourceFile) = buildCSourceFile(fileUri, fileContent)
            fileMap[sourcePath] = sourceFile
        }
        return fileMap
    }

    private fun buildCSourceFile(fileUri: URI, fileContent: String): Pair<C_SourcePath, C_SourceFile> {
        val rellCompilerSourcePath = rellCompilerUtils.createCompilerSourcePath(fileUri, workspaceUri)
        val parseResult = this.buildParseTree(fileContent)
        val ast = buildRellAstWithCompilerErrors(
            rellCompilerSourcePath,
            parseResult.parseTree,
            parseResult.parser.tokenStream
        )
        return rellCompilerSourcePath to AstSourceFile.make(
            ast.first,
            IdeSourcePathFilePath(rellCompilerSourcePath),
            fileContent
        )
    }

    fun updateFileMap(fileMap: MutableMap<C_SourcePath, C_SourceFile>, fileUri: URI, fileContent: String) {
        val (sourcePath, sourceFile) = buildCSourceFile(fileUri, fileContent)
        fileMap[sourcePath] = sourceFile
    }

    fun buildRellResource(
        fileUri: URI,
        fileContent: String,
        fileMap: MutableMap<C_SourcePath, C_SourceFile>
    ): Resource {
        val rellCompilerSourcePath = rellCompilerUtils.createCompilerSourcePath(fileUri, workspaceUri)
        val parseResult = this.buildParseTree(fileContent)
        val ast = buildRellAstWithCompilerErrors(
            rellCompilerSourcePath,
            parseResult.parseTree,
            parseResult.parser.tokenStream
        )
        val compilationResult = compileResult(rellCompilerSourcePath, ast.first, fileMap, fileContent)
        val symbolInfo = compilationResult?.symbolInfos ?: mapOf()
        val locationInfo = createLocationInfo(symbolInfo)
        val tokenStream = parseResult.parser.tokenStream as RellCommonTokenStream
        val checksum = calculateChecksum(fileContent)

        return Resource(
            parseResult.parseTree,
            IdeApi.getModuleInfo(rellCompilerSourcePath, ast.first),
            fileUri,
            workspaceUri,
            ast.first,
            parseResult.syntaxErrors,
            compilationResult?.messages ?: listOf(),
            listOf(),
            listOf(),
            symbolInfo,
            symbolInfo.asSequence().filter { it.value.defId != null }.associate { it.value.defId!! to it.key },
            locationInfo,
            checksum,
            tokenStream
        )
    }

    fun buildRellResource(fileUri: URI, fileMap: MutableMap<C_SourcePath, C_SourceFile>): Resource {
        val fileContent = File(fileUri).readText()
        return buildRellResource(fileUri, fileContent, fileMap)
    }

    fun compileResult(
        compilerSrcPath: C_SourcePath,
        ast: S_RellFile,
        fileMap: MutableMap<C_SourcePath, C_SourceFile>,
        fileContent: String,
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

        if (moduleName == null) {
            logger.warn { "Can not find the moduleName for $compilerSrcPath" }
            return null
        }

        val rellLanguageVersion = chromiaModelProvider.getRellLanguageVersion()
        val options = C_CompilerOptions.builder()
            .compatibility(R_LangVersion.of(rellLanguageVersion))
            .symbolInfoFile(compilerSrcPath)
            .ideDocSymbolsEnabled(true)
            .ide(true)
            .build()
        val idePath = IdeSourcePathFilePath(compilerSrcPath)
        val mainFile = AstSourceFile.make(ast, idePath, fileContent)
        fileMap[compilerSrcPath] = mainFile
        val selfDir = IdeDirApi.mapDir(fileMap)
        return try {
            IdeApi.compile(
                selfDir,
                immListOf(moduleName),
                options
            )
        } catch (e: Exception) {
            logger.warn(e) { "Compilation failed for file: ${compilerSrcPath.str()}" }
            null
        }
    }

    fun buildParseTree(fileContent: String): ParsingResult {
        val errorListener = SyntaxErrorCollector()
        return try {
            val parser = parser.parserFor(fileContent, errorListeners = listOf(errorListener))
            val parseTree = parser.ruleX_RootParser()
            ParsingResult(parseTree, errorListener.errors, parser)
        } catch (e: Exception) {
            // If error occurred we build a parse tree from an empty string instead. This is so that we can continue
            // with the flow of building the whole project
            logger.debug { "Stacktrace for failure for building parse tree: $e" }
            val parser = parser.parserFor("", errorListeners = listOf(errorListener))
            val parseTree = parser.ruleX_RootParser()
            ParsingResult(parseTree, errorListener.errors, parser)
        }
    }

    fun buildRellAstWithCompilerErrors(
        rellCompilerSourcePath: C_SourcePath,
        parseTree: RellParser.RuleX_RootParserContext,
        tokenStream: TokenStream
    ): Pair<S_RellFile, List<C_Error>> {
        val rellCompilerFilePath = rellCompilerUtils.createRellCompilerFilePath(rellCompilerSourcePath)
        return RellCompilerApi.antlrToRellAst(rellCompilerFilePath, parseTree, tokenStream)
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
