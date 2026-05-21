/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.tools

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.option
import mu.KLogging
import net.postchain.rell.api.base.*
import net.postchain.rell.base.compiler.base.core.C_Compiler
import net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.C_CommonError
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.model.R_LangVersion
import net.postchain.rell.base.model.rr.RR_App
import net.postchain.rell.base.model.rr.RR_FunctionDefinition
import net.postchain.rell.base.runtime.Rt_Interpreter
import net.postchain.rell.base.runtime.Rt_RellVersion
import net.postchain.rell.base.utils.*
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
        moduleName: ModuleName?,
        quiet: Boolean = false,
        compilerOptions: C_CompilerOptions
    ): RellCompiledApp {
        val cSourceDir = RellApiBaseUtils.createSourceDir(sourceDir)
        val modSel = C_CompilerModuleSelection(immListOfNotNull(moduleName))
        return compileApp(cSourceDir, modSel, quiet, compilerOptions)
    }

    fun compileApp(
        sourceDir: C_SourceDir,
        modSel: C_CompilerModuleSelection,
        quiet: Boolean,
        compilerOptions: C_CompilerOptions
    ): RellCompiledApp {
        val result = try {
            val res = C_Compiler.compile(sourceDir, modSel, compilerOptions)
            res
        } catch (e: C_CommonError) {
            RellCliEnv.DEFAULT.error(RellApiBaseUtils.errMsg(e.msg))
            throw RellCliExitException(1, e.msg)
        }

        RellApiBaseUtils.handleCompilationResult(RellCliEnv.DEFAULT, result, quiet)
        return RellCompiledApp(result.rrApp!!, result.compilationSysFns)
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

    fun checkModule(s: String): ModuleName {
        val res = ModuleName.ofOpt(s)
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
        logger.info(Rt_RellVersion.getInstance().buildDescriptor)
    }
}

class RellCompiledApp(
    val rrApp: RR_App,
    /**
     * Sys-function registrations produced by the compilation that yielded [rrApp]. Must be
     * threaded into the interpreter — see `Rt_InterpreterImpl.forCompilation` for why isolation
     * across compilations matters (stdlib meta-bodies capture compile-specific state).
     */
    val compilationSysFns: Map<String, Any> = emptyMap(),
) {
    fun createInterpreter(): Rt_Interpreter = RellApiInterpreterBackend.create(rrApp, compilationSysFns)

    fun getRRTestFunctions(moduleName: ModuleName, matcher: UnitTestMatcher): List<RR_FunctionDefinition> {
        val rrModule = rrApp.module(moduleName) ?: return emptyList()
        return UnitTestRunner.getRRTestFunctions(rrModule, matcher)
    }

    fun getAllRRTestFunctions(matcher: UnitTestMatcher): List<RR_FunctionDefinition> =
        UnitTestRunner.getRRTestFunctions(rrApp, matcher)
}

abstract class RellBaseCommand(name: String) : CliktCommand(name = name) {
    val sourceDir by option("-d", "--source-dir", metavar = "SOURCE_DIR",
        help = "Rell source code directory (default: current directory)")
}
