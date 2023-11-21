package net.postchain.rell.toolbox.core.indexer

import com.google.common.collect.ImmutableMap
import io.github.oshai.kotlinlogging.KotlinLogging
import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.ast.S_RellFile
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.C_Error
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.compiler.base.utils.IdeSourcePathFilePath
import net.postchain.rell.base.utils.ide.IdeApi
import net.postchain.rell.base.utils.ide.IdeCompilationResult
import net.postchain.rell.base.utils.ide.IdeDirApi
import net.postchain.rell.base.utils.ide.IdeSymbolInfo
import net.postchain.rell.toolbox.core.compiler.AntlrPos
import net.postchain.rell.toolbox.core.compiler.RellcAPI
import net.postchain.rell.toolbox.core.parser.AntlrRellParser
import net.postchain.rell.toolbox.core.parser.RellParser
import net.postchain.rell.toolbox.core.parser.SyntaxError
import net.postchain.rell.toolbox.core.parser.SyntaxErrorCollector
import org.antlr.v4.runtime.misc.Interval
import java.io.File
import java.net.URI
import java.util.TreeMap


class RellResourceFactory(private val workspaceUri: URI, private val parser: AntlrRellParser) {
    val rellCompilerPaths = RellCompilerPaths(workspaceUri)
    private val logger = KotlinLogging.logger {}

    fun buildRellResource(fileUri: URI, fileContent: String): Resource {
        val rellCompilerSourcePath = rellCompilerPaths.createCompilerSourcePath(fileUri)
        val antlrParseTree = buildParseTreeWithSyntaxErrors(fileContent)
        val ast = buildRellAstWithCompilerErrors(rellCompilerSourcePath, antlrParseTree.first)
        val compilationResult = compileResult(rellCompilerSourcePath, ast.first)
        val symbolInfo = compilationResult.symbolInfos
        val locationInfo = createLocationInfo(symbolInfo)

        return Resource(
            antlrParseTree.first,
            ast.first.ideModuleInfo(rellCompilerSourcePath),
            fileUri,
            workspaceUri,
            ast.first,
            antlrParseTree.second,
            compilationResult.messages,
            symbolInfo,
            locationInfo
        )
    }

    private fun createLocationInfo(symbolInfos: Map<S_Pos, IdeSymbolInfo>): Map<Interval, IdeSymbolInfo> {
        val intervalMap = symbolInfos.map {
            (it.key as AntlrPos).node.sourceInterval to it.value
        }
        val locationInfo = TreeMap<Interval, IdeSymbolInfo>(::intervalCompare)
        locationInfo.putAll(intervalMap)
        return locationInfo
    }

    private fun intervalCompare(intervalA: Interval, intervalB: Interval): Int {
        if (intervalA.properlyContains(intervalB)) {
            return 0
        } else if (intervalA.startsAfter(intervalB)) {
            return -1
        } else {
            return 1
        }
    }

    fun buildRellResource(fileUri: URI): Resource {
        val fileContent = File(fileUri).readText()
        return buildRellResource(fileUri, fileContent)
    }

    fun buildRellResource(resource: Resource, fileUri: URI): Resource {
        val rellCompilerSourcePath = rellCompilerPaths.createCompilerSourcePath(fileUri)
        val compilationResult = compileResult(rellCompilerSourcePath, resource.ast)
        val symbolInfo = compilationResult.symbolInfos
        val locationInfo = createLocationInfo(symbolInfo)

        return Resource(
            resource.parseTree,
            resource.moduleInfo,
            fileUri,
            workspaceUri,
            resource.ast,
            resource.syntaxErrors,
            compilationResult.messages,
            symbolInfo,
            locationInfo
        )
    }

    fun compileResult(
        compilerSrcPath: C_SourcePath, ast: S_RellFile
    ): IdeCompilationResult {
        val options = C_CompilerOptions.builder().symbolInfoFile(compilerSrcPath).ide(true).build()
        val commonSourceDir: C_SourceDir = C_SourceDir.diskDir(File(workspaceUri))
        val moduleName = IdeApi.getModuleName(compilerSrcPath, ast)
            ?: throw Exception("Can not find the moduleName for $compilerSrcPath")

        val idePath = IdeSourcePathFilePath(compilerSrcPath)
        val mainFile = AstSourceFile.make(ast, idePath)
        val fileMap = ImmutableMap.of(compilerSrcPath, mainFile)
        val selfDir = IdeDirApi.mapDir(fileMap)

        return IdeApi.compile(
            CompoundSourceDir(selfDir, commonSourceDir), listOf(moduleName), options
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
