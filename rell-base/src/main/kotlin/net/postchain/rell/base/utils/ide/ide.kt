/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils.ide

import com.google.common.collect.Multimap
import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.ast.S_RellFile
import net.postchain.rell.base.compiler.base.core.C_Compiler
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.module.C_ModuleUtils
import net.postchain.rell.base.compiler.base.utils.*
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.runtime.Rt_RellVersion
import net.postchain.rell.base.runtime.Rt_RellVersionProperty
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.DocSymbol
import net.postchain.rell.base.utils.doc.DocSymbolKind
import net.postchain.rell.base.utils.doc.DocSymbolName
import org.apache.commons.configuration2.PropertiesConfiguration
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder
import org.apache.commons.configuration2.builder.fluent.Parameters
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler
import java.io.File

abstract class IdeFilePath {
    abstract override fun equals(other: Any?): Boolean
    abstract override fun hashCode(): Int
    abstract override fun toString(): String
}

class IdeModuleInfo(
    @JvmField val name: R_ModuleName,
    @JvmField val directory: Boolean,
    @JvmField val app: Boolean,
    @JvmField val test: Boolean,
    @JvmField val imports: ImmSet<R_ModuleName>,
)

class IdeCompilationResult(
    @JvmField val messages: ImmList<C_Message>,
    @JvmField val symbolInfos: ImmMap<S_Pos, IdeSymbolInfo>,
)

data class IdeCompletionParam(
    val name: String,
    val code: String,
)

data class IdeCompletion(
    val kind: DocSymbolKind,
    val symbolName: DocSymbolName,
    val params: ImmList<IdeCompletionParam>?,
    val result: String?,
    val location: String?,
    val docSymbol: DocSymbol?,
    val deprecated: Boolean,
) {
    override fun toString(): String {
        val paramsStr = params?.joinToString(", ", "(", ")") { it.code }
        return "$kind|$symbolName|$paramsStr|${result?:"-"}|${location?:"-"}"
    }
}

@Suppress("UNUSED")
object IdeApi {
    const val RELL_VERSION = RellVersions.VERSION_STR

    @JvmStatic fun parseModuleName(s: String): R_ModuleName? {
        return R_ModuleName.ofOpt(s)
    }

    @JvmStatic fun getModuleName(path: C_SourcePath, ast: S_RellFile): R_ModuleName? {
        val (moduleName, _) = C_ModuleUtils.getModuleInfo(path, ast)
        return moduleName
    }

    @JvmStatic fun getModuleInfo(path: C_SourcePath, ast: S_RellFile): IdeModuleInfo? {
        return ast.ideModuleInfo(path)
    }

    @JvmStatic fun buildOutlineTree(b: IdeOutlineTreeBuilder, ast: S_RellFile) {
        ast.ideBuildOutlineTree(b)
    }

    @JvmStatic fun getRellVersionInfo(): Map<Rt_RellVersionProperty, String>? {
        val ver = Rt_RellVersion.getInstance()
        return ver?.properties
    }

    @JvmStatic fun isValidDatabaseProperties(file: File): Boolean {
        val params = Parameters().properties()
                .setFile(file)
                .setListDelimiterHandler(DefaultListDelimiterHandler(','))

        val conf = FileBasedConfigurationBuilder(PropertiesConfiguration::class.java)
                .configure(params)
                .configuration

        val res = conf.containsKey("database.url")
        return res
    }

    /**
     * Compiles the file (module) and returns code completions at a specific location (text offset).
     * If compilation fails, returns an empty map.
     */
    @JvmStatic fun getCompletions(
        sourceDir: C_SourceDir,
        filePath: C_SourcePath,
        pos: Int,
        options: C_CompilerOptions,
    ): Multimap<String, IdeCompletion> {
        val sourceFile = sourceDir.file(filePath)
        sourceFile ?: return immMultimapOf()

        val actualOptions = C_IdeCompletionsUtils.getCompilerOptions(sourceDir, filePath, pos, options)
        actualOptions ?: return immMultimapOf()

        val ast = sourceFile.readAstEx(options.compatibility)
        val (moduleName, _) = C_ModuleUtils.getModuleInfo(filePath, ast)
        moduleName ?: return immMultimapOf()

        return try {
            val cRes = C_Compiler.compile(sourceDir, immListOf(moduleName), actualOptions)
            cRes.ideCompletions
        } catch (e: Exception) {
            immMultimapOf()
        }
    }

    @JvmStatic fun compile(
        sourceDir: C_SourceDir,
        modules: ImmList<R_ModuleName>,
        options: C_CompilerOptions,
    ): IdeCompilationResult {
        val res = C_Compiler.compile(sourceDir, modules, options)
        return IdeCompilationResult(res.messages, res.ideSymbolInfos)
    }

    @JvmStatic fun getAllComments(
        sourceDir: C_SourceDir,
        modules: ImmList<R_ModuleName>,
        options: C_CompilerOptions,
    ): Map<String, String> {
        val res = C_Compiler.compile(sourceDir, modules, options)
        res.app ?: return immMapOf()
        return C_DocUtils.getAllComments(res.app)
    }
}

@Suppress("UNUSED")
object IdeDirApi {
    @JvmField
    val EMPTY_DIR: C_SourceDir = C_SourceDir.EMPTY

    @JvmStatic
    fun mapDir(files: Map<C_SourcePath, C_SourceFile>): C_SourceDir = C_SourceDir.mapDir(files)

    @JvmStatic
    fun mapDirOf(files: Map<String, String>): C_SourceDir = C_SourceDir.mapDirOf(files)

    @JvmStatic
    fun diskDir(dir: File): C_SourceDir = C_SourceDir.diskDir(dir)

    @JvmStatic
    fun parseSourcePath(s: String): C_SourcePath? = C_SourcePath.parseOpt(s)

    @JvmStatic
    fun makeSourcePath(parts: List<String>): C_SourcePath = C_SourcePath.of(parts)

    @JvmStatic
    fun makeSourcePathOpt(parts: List<String>): C_SourcePath? = C_SourcePath.ofOpt(parts)
}
