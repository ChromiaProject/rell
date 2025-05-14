/*
 * Copyright (C) 2023 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.tools

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
import picocli.CommandLine
import java.io.File
import kotlin.system.exitProcess

object RellToolsUtils: KLogging() {
    fun <T: RellCliArgs> runCli(args: Array<String>, argsObj: T, block: (T) -> Unit) {
        CommonUtils.failIfUnitTest() // Make sure unit test check works

        parseCliArgs(args, argsObj)
        try {
            block(argsObj)
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

    private fun <T> parseCliArgs(args: Array<String>, argsObj: T) {
        val cl = CommandLine(argsObj)
        try {
            cl.parse(*args)
        } catch (e: CommandLine.PicocliException) {
            cl.usageHelpWidth = 1000
            cl.usage(System.err)
            exitProcess(1)
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
        val sourcePath = checkDir(sourceDir ?: ".").absoluteFile
        val cSourceDir = C_SourceDir.diskDir(sourcePath)
        val moduleName = checkModule(module)
        return RellCliTarget(sourcePath, cSourceDir, listOf(moduleName))
    }

    fun prepareDir(dir: File) {
        check(dir.isDirectory || dir.mkdirs()) { "Cannot create directory: $dir" }
    }

    fun check(b: Boolean, msgCode: () -> String) {
        if (!b) {
            val msg = msgCode()
            throw RellCliBasicException(msg)
        }
    }

    fun checkDir(path: String): File {
        val file = File(path)
        check(file.isDirectory) { "Directory not found: $path" }
        return file
    }

    fun checkFile(path: String): File {
        val file = File(path)
        check(file.isFile) { "File not found: $path" }
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
        } catch (e: IllegalArgumentException) {
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

abstract class RellCliArgs

abstract class RellBaseCliArgs: RellCliArgs() {
    @CommandLine.Option(names = ["-d", "--source-dir"], paramLabel = "SOURCE_DIR",
        description = ["Rell source code directory (default: current directory)"])
    var sourceDir: String? = null
}
