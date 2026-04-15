/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.tools

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.option
import mu.KLogging
import net.postchain.rell.api.base.*
import net.postchain.rell.base.compiler.base.core.C_CompilationResult
import net.postchain.rell.base.compiler.base.core.C_Compiler
import net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.C_CommonError
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.model.R_App
import net.postchain.rell.base.model.R_LangVersion
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.runtime.Rt_RellVersion
import net.postchain.rell.base.utils.CommonUtils
import net.postchain.rell.base.utils.RellVersions
import net.postchain.rell.base.utils.immListOfNotNull
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.system.exitProcess

object RellToolsUtils: KLogging() {
    fun runCli(args: Array<String>, command: CliktCommand) {
        CommonUtils.failIfUnitTest() // Make sure unit test check works

        try {
            command.main(args)
        } catch (e: RellCliExitException) {
            exitProcess(e.code)
        } catch (e: RellCliException) {
            System.err.println("ERROR: ${e.message}")
            exitProcess(2)
        } catch (e: Throwable) {
            e.printStackTrace()
            exitProcess(3)
        }
    }

    fun compileApp(
        sourceDir: String?,
        moduleName: R_ModuleName?,
        quiet: Boolean = false,
        compilerOptions: C_CompilerOptions
    ): R_App {
        val cSourceDir = RellApiBaseUtils.createSourceDir(sourceDir)
        val modSel = C_CompilerModuleSelection(immListOfNotNull(moduleName))
        val res = compileApp(cSourceDir, modSel, quiet, compilerOptions)
        return res
    }

    fun compileApp(
        sourceDir: C_SourceDir,
        modSel: C_CompilerModuleSelection,
        quiet: Boolean,
        compilerOptions: C_CompilerOptions
    ): R_App {
        val res = compile(RellCliEnv.DEFAULT, sourceDir, modSel, quiet, compilerOptions)
        return res.app!!
    }

    private fun compile(
        cliEnv: RellCliEnv,
        sourceDir: C_SourceDir,
        modSel: C_CompilerModuleSelection,
        quiet: Boolean,
        compilerOptions: C_CompilerOptions
    ): C_CompilationResult {
        val res = compile0(compilerOptions, cliEnv, sourceDir, modSel)
        RellApiBaseUtils.handleCompilationResult(cliEnv, res, quiet)
        return res
    }

    private fun compile0(
        compilerOptions: C_CompilerOptions,
        cliEnv: RellCliEnv,
        sourceDir: C_SourceDir,
        modSel: C_CompilerModuleSelection
    ): C_CompilationResult {
        try {
            val res = C_Compiler.compile(sourceDir, modSel, compilerOptions)
            return res
        } catch (e: C_CommonError) {
            cliEnv.error(RellApiBaseUtils.errMsg(e.msg))
            throw RellCliExitException(1, e.msg)
        }
    }

    fun getTarget(sourceDir: String?, module: String): RellCliTarget {
        val sourcePath = checkDir(sourceDir ?: ".").absolute()
        val cSourceDir = C_SourceDir.diskDir(sourcePath.toFile())
        val moduleName = checkModule(module)
        return RellCliTarget(sourcePath.toFile(), cSourceDir, listOf(moduleName))
    }

    inline fun check(b: Boolean, msgCode: () -> String) {
        if (!b) {
            val msg = msgCode()
            throw RellCliBasicException(msg)
        }
    }

    fun checkDir(path: String): Path {
        val file = Path(path)
        check(file.isDirectory()) { "Directory not found: $path" }
        return file
    }

    fun checkFile(path: String): Path {
        val file = Path(path)
        check(file.isRegularFile()) { "File not found: $path" }
        return file
    }

    fun checkModule(s: String): R_ModuleName {
        val res = R_ModuleName.ofOpt(s)
        return res ?: throw RellCliBasicException("Invalid module name: '$s'")
    }

    fun checkVersion(s: String?): R_LangVersion {
        s ?: return RellVersions.VERSION
        val ver = try {
            R_LangVersion.of(s)
        } catch (_: IllegalArgumentException) {
            throw RellCliBasicException("Invalid source version: '$s'")
        }
        if (ver !in RellVersions.SUPPORTED_VERSIONS) {
            throw RellCliBasicException("Source version not supported: $ver")
        }
        return ver
    }

    fun printVersionInfo() {
        val ver = Rt_RellVersion.getInstance()?.buildDescriptor ?: "Rell version unknown"
        logger.info(ver)
    }
}

abstract class RellBaseCommand(name: String) : CliktCommand(name = name) {
    val sourceDir by option("-d", "--source-dir", metavar = "SOURCE_DIR",
        help = "Rell source code directory (default: current directory)")
}
