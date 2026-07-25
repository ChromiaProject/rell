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
import net.postchain.rell.toolbox.parser.SyntaxErrorCollector
import java.io.File
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

class RellResourceFactory(
    private val workspaceUri: URI,
    private val parser: AntlrRellParser,
    private val chromiaModelProvider: ChromiaModelProvider
) {
    val rellCompilerUtils = RellCompilerUtils()

    fun parseFiles(sources: Map<URI, String>): Map<URI, ParsedRellFile> =
        sources.mapValues { (fileUri, fileContent) -> parseFile(fileUri, fileContent) }

    fun parseFile(fileUri: URI, fileContent: String): ParsedRellFile {
        val rellCompilerSourcePath = rellCompilerUtils.createCompilerSourcePath(fileUri, workspaceUri)
        val parseResult = this.buildParseTree(fileContent)
        val ast = buildRellAstWithCompilerErrors(
            rellCompilerSourcePath,
            parseResult.parseTree,
            parseResult.tokenStream,
        )
        return ParsedRellFile(rellCompilerSourcePath, parseResult, ast.first)
    }

    fun buildFileMap(
        sources: Map<URI, String>,
        parsedFiles: Map<URI, ParsedRellFile> = parseFiles(sources),
    ): ConcurrentHashMap<C_SourcePath, C_SourceFile> {
        val fileMap = ConcurrentHashMap<C_SourcePath, C_SourceFile>()

        for ((fileUri, fileContent) in sources) {
            val parsed = parsedFiles[fileUri] ?: parseFile(fileUri, fileContent)
            fileMap[parsed.sourcePath] = parsed.toSourceFile(fileContent)
        }

        return fileMap
    }

    fun updateFileMap(fileMap: MutableMap<C_SourcePath, C_SourceFile>, fileUri: URI, fileContent: String) {
        val parsed = parseFile(fileUri, fileContent)
        fileMap[parsed.sourcePath] = parsed.toSourceFile(fileContent)
    }

    fun buildRellResource(
        fileUri: URI,
        fileContent: String,
        fileMap: MutableMap<C_SourcePath, C_SourceFile>,
        parsedFile: ParsedRellFile? = null,
    ): Resource {
        val parsed = parsedFile ?: parseFile(fileUri, fileContent)
        val rellCompilerSourcePath = parsed.sourcePath
        val parseResult = parsed.parseResult
        val ast = parsed.ast
        val compilationResult = compileResult(rellCompilerSourcePath, ast, fileMap, fileContent)
        val symbolInfo = compilationResult?.symbolInfos ?: mapOf()
        val locationInfo = createLocationInfo(symbolInfo)
        val tokenStream = parseResult.tokenStream as RellCommonTokenStream
        val checksum = calculateChecksum(fileContent)

        return Resource(
            parseResult.parseTree,
            IdeApi.getModuleInfo(IdeDirApi.mapDir(fileMap), rellCompilerSourcePath, ast),
            fileUri,
            workspaceUri,
            ast,
            parseResult.syntaxErrors,
            compilationResult?.messages ?: listOf(),
            listOf(),
            listOf(),
            symbolInfo,
            symbolInfo.asSequence().filter { it.value.defId != null }.associate { it.value.defId!! to it.key },
            locationInfo,
            checksum,
            tokenStream,
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

        val options = C_CompilerOptions.builder().apply {
            compatibility(R_LangVersion.of(rellLanguageVersion))
            symbolInfoFile(compilerSrcPath)
            ideDocSymbolsEnabled(true)
            ide(true)
        }.build()

        val idePath = IdeSourcePathFilePath(compilerSrcPath)
        val mainFile = AstSourceFile.make(ast, idePath, fileContent)
        fileMap[compilerSrcPath] = mainFile
        val selfDir = IdeDirApi.mapDir(fileMap)
        return try {
            IdeApi.compile(
                selfDir,
                immListOf(moduleName),
                options,
            )
        } catch (e: Exception) {
            logger.warn(e) { "Compilation failed for file: ${compilerSrcPath.str()}" }
            null
        }
    }

    fun buildParseTree(fileContent: String): ParsingResult {
        val errorListener = SyntaxErrorCollector()

        return try {
            buildParseTreeFromSource(fileContent, errorListener)
        } catch (e: Exception) {
            // If error occurred we build a parse tree from an empty string instead. This is so that we can continue
            // with the flow of building the whole project
            logger.debug { "Stacktrace for failure for building parse tree: $e" }
            buildParseTreeFromSource("", errorListener)
        }
    }

    private fun buildParseTreeFromSource(source: String, errorListener: SyntaxErrorCollector): ParsingResult {
        // Canonical parser from rell-base/frontend (Rell.g4); produces the parse tree
        // consumed by both AST construction and downstream tooling.
        val manualParser = parser.parserFor(source, errorListeners = listOf(errorListener))
        val manualTree = manualParser.file()
        val manualTokens = manualParser.tokenStream as? org.antlr.v4.runtime.BufferedTokenStream

        return ParsingResult(
            parseTree = manualTree,
            syntaxErrors = errorListener.errors.toList(),
            tokenStream = manualTokens,
        )
    }

    fun buildRellAstWithCompilerErrors(
        rellCompilerSourcePath: C_SourcePath,
        parseTree: net.postchain.rell.base.compiler.parser.antlr.RellParser.FileContext,
        manualTokenStream: org.antlr.v4.runtime.BufferedTokenStream? = null,
    ): Pair<S_RellFile, List<C_Error>> {
        val rellCompilerFilePath = rellCompilerUtils.createRellCompilerFilePath(rellCompilerSourcePath)
        return RellCompilerApi.antlrToRellAst(rellCompilerFilePath, parseTree, manualTokenStream)
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
