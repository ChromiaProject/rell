/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.api.base

import net.postchain.gtv.GtvNull
import net.postchain.rell.base.compiler.base.core.C_CompilationResult
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.model.R_App
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.utils.mapToImmList
import java.io.File

@InternalRellApi
public object RellApiBaseUtils {
    public fun createSourceDir(sourceDirPath: String?): C_SourceDir {
        val file = if (sourceDirPath == null) File(".") else File(sourceDirPath)
        return C_SourceDir.diskDir(file.absoluteFile)
    }

    public fun handleCompilationResult(cliEnv: RellCliEnv, res: C_CompilationResult, quiet: Boolean): R_App {
        val warnCnt = res.warnings.size
        val errCnt = res.errors.size

        val haveImportantMessages = res.app == null || res.messages.any { !it.type.ignorable }

        if (haveImportantMessages || (!quiet && res.messages.isNotEmpty())) {
            // Print all messages only if not quiet or if compilation failed, so warnings are not suppressed by the
            // quiet flag if there is an error.
            for (message in res.messages) {
                cliEnv.error(message.toString())
            }
            if (errCnt > 0 || warnCnt > 0) {
                cliEnv.error("Errors: $errCnt Warnings: $warnCnt")
            }
        }

        val app = res.app
        if (app == null) {
            if (errCnt == 0) {
                cliEnv.error(errMsg("Compilation failed"))
            }
            throw RellCliExitException(1, "Compilation failed")
        } else if (errCnt > 0) {
            throw RellCliExitException(1, "Compilation failed")
        }

        return app
    }

    public fun errMsg(msg: String): String = "${C_MessageType.ERROR.text}: $msg"

    public fun createGlobalContext(
        compilerOptions: C_CompilerOptions,
        typeCheck: Boolean,
        outPrinter: Rt_Printer = Rt_OutPrinter,
        logPrinter: Rt_Printer = Rt_LogPrinter(),
    ): Rt_GlobalContext {
        return Rt_GlobalContext(
                compilerOptions = compilerOptions,
                outPrinter = outPrinter,
                logPrinter = logPrinter,
                typeCheck = typeCheck,
        )
    }

    public fun createChainContext(): Rt_ChainContext {
        return Rt_ChainContext(GtvNull, Rt_ChainContext.ZERO_BLOCKCHAIN_RID)
    }

    public fun createSqlContext(app: R_App): Rt_SqlContext {
        val mapping = Rt_ChainSqlMapping(0)
        return Rt_RegularSqlContext.createNoExternalChains(app, mapping)
    }

    public fun getMainModules(app: R_App): List<R_ModuleName> {
        return app.modules.filter { !it.test && !it.abstract && !it.external }.mapToImmList { it.name }
    }
}

public abstract class RellCliException(msg: String): RuntimeException(msg)
public class RellCliBasicException(msg: String): RellCliException(msg)
public class RellCliExitException(public val code: Int, msg: String = "exit $code"): RellCliException(msg)

public class RellCliTarget(public val sourcePath: File, public val sourceDir: C_SourceDir, public val modules: List<R_ModuleName>)

public interface RellCliEnv {
    public fun print(msg: String)
    public fun error(msg: String)
    public companion object {
        @JvmStatic
        public val NULL: RellCliEnv = NullRellCliEnv
        @JvmStatic
        public val DEFAULT: RellCliEnv = MainRellCliEnv
    }
}

internal object NullRellCliEnv: RellCliEnv by PrinterRellCliEnv({}, {})
internal object MainRellCliEnv: RellCliEnv by PrinterRellCliEnv(::println, System.err::println)

public class PrinterRellCliEnv(private val printer: Rt_Printer, private val errorPrinter: Rt_Printer = printer): RellCliEnv {
    override fun print(msg: String) {
        printer.print(msg)
    }
    override fun error(msg: String) {
        errorPrinter.print(msg)
    }
}
