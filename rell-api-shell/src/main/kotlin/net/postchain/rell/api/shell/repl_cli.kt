/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.api.shell

import net.postchain.gtv.Gtv
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.repl.*
import net.postchain.rell.base.runtime.Rt_GlobalContext
import net.postchain.rell.base.runtime.Rt_GtvModuleArgsSource
import net.postchain.rell.base.runtime.Rt_RellVersion
import net.postchain.rell.base.runtime.Rt_RellVersionProperty
import net.postchain.rell.base.sql.SqlManager
import org.apache.commons.lang3.StringUtils
import java.io.File

class ReplShellOptions(
    val compilerOptions: C_CompilerOptions,
    val inputChannelFactory: ReplInputChannelFactory,
    val outputChannelFactory: ReplOutputChannelFactory,
    val historyFile: File?,
    val printIntroMessage: Boolean,
    val moduleArgs: Map<R_ModuleName, Gtv>,
)

object ReplShell {
    fun start(
        sourceDir: C_SourceDir,
        module: R_ModuleName?,
        globalCtx: Rt_GlobalContext,
        sqlMgr: SqlManager,
        projExt: ReplInterpreterProjExt,
        options: ReplShellOptions,
    ) {
        val outChannel = options.outputChannelFactory.createOutputChannel()

        val config = ReplInterpreterConfig(
            options.compilerOptions,
            sourceDir,
            module,
            globalCtx,
            sqlMgr,
            projExt,
            outChannel,
            Rt_GtvModuleArgsSource(options.moduleArgs, options.compilerOptions),
        )

        val repl = ReplInterpreter.create(config)
        if (repl == null) {
            return
        }

        if (options.printIntroMessage) {
            printIntro(outChannel, repl, module)
        }

        val inChannel = options.inputChannelFactory.createInputChannel(options.historyFile)

        while (!repl.mustQuit()) {
            val line = inChannel.readLine(">>> ")
            if (line == null) {
                break
            } else if (!StringUtils.isBlank(line)) {
                repl.execute(line)
            }
        }
    }

    private fun printIntro(outChannel: ReplOutputChannel, repl: ReplInterpreter, moduleName: R_ModuleName?) {
        val ver = getVersionInfo()
        outChannel.printInfo(ver)

        val quit = repl.getQuitCommand()
        val help = repl.getHelpCommand()
        outChannel.printInfo("Type '$quit' to quit or '$help' for help.")

        if (moduleName != null) {
            outChannel.printInfo("Current module: '$moduleName'")
        }
    }

    private fun getVersionInfo(): String {
        val v = Rt_RellVersion.getInstance()
        if (v == null) return "Version unknown"
        val ver = v.properties[Rt_RellVersionProperty.RELL_VERSION] ?: "[unknown version]"
        return "Rell $ver"
    }
}
