/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.api.shell

import net.postchain.gtv.Gtv
import net.postchain.rell.api.base.InternalRellApi
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.repl.*
import net.postchain.rell.base.runtime.Rt_GlobalContext
import net.postchain.rell.base.runtime.Rt_GtvModuleArgsSource
import net.postchain.rell.base.runtime.Rt_RellVersion
import net.postchain.rell.base.runtime.Rt_RellVersionProperty
import net.postchain.rell.base.sql.SqlManager
import net.postchain.rell.base.utils.toImmMap
import java.io.File

@InternalRellApi
public class ReplShellOptions(
        public val compilerOptions: C_CompilerOptions,
        public val inputChannelFactory: ReplInputChannelFactory,
        public val outputChannelFactory: ReplOutputChannelFactory,
        public val historyFile: File?,
        public val printIntroMessage: Boolean,
        public val moduleArgs: Map<ModuleName, Gtv>,
)

@InternalRellApi
public object ReplShell {
    public fun start(
            sourceDir: C_SourceDir,
            module: ModuleName?,
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
            Rt_GtvModuleArgsSource(options.moduleArgs.toImmMap(), options.compilerOptions),
        )

        val repl = ReplInterpreter.create(config) ?: return

        if (options.printIntroMessage) {
            printIntro(outChannel, repl, module)
        }

        val inChannel = options.inputChannelFactory.createInputChannel(options.historyFile)

        while (!repl.mustQuit()) {
            val line = inChannel.readLine(">>> ")
            if (line == null) {
                break
            } else if (line.isNotBlank()) {
                repl.execute(line)
            }
        }
    }

    private fun printIntro(outChannel: ReplOutputChannel, repl: ReplInterpreter, moduleName: ModuleName?) {
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
        val v = Rt_RellVersion.getInstance() ?: return "Version unknown"
        val ver = v.properties[Rt_RellVersionProperty.RELL_VERSION] ?: "[unknown version]"
        return "Rell $ver"
    }
}
