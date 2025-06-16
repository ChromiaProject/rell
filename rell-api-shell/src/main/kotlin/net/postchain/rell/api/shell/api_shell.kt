/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.api.shell

import net.postchain.rell.api.base.InternalRellApi
import net.postchain.rell.api.base.RellApiBaseInternal
import net.postchain.rell.api.base.RellApiBaseUtils
import net.postchain.rell.api.base.RellApiCompile
import net.postchain.rell.api.gtx.PostchainReplInterpreterProjExt
import net.postchain.rell.api.gtx.PostchainSqlInitProjExt
import net.postchain.rell.api.gtx.RellApiGtxUtils
import net.postchain.rell.api.gtx.Rt_BlockRunnerConfig
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.repl.ReplInputChannelFactory
import net.postchain.rell.base.repl.ReplOutputChannelFactory
import net.postchain.rell.base.runtime.Rt_LogPrinter
import net.postchain.rell.base.runtime.Rt_OutPrinter
import net.postchain.rell.base.runtime.Rt_Printer
import net.postchain.rell.base.utils.CommonUtils
import net.postchain.rell.module.RellPostchainModuleEnvironment
import java.io.File

public object RellApiRunShell {
    /**
     * Start a REPL shell.
     *
     * @param config Configuration.
     * @param sourceDir Source directory.
     * @param module Current module: REPL commands will be executed in scope of that module; `null` means none.
     */
    public fun runShell(
        config: Config,
        sourceDir: File,
        module: String?,
    ) {
        val cSourceDir = C_SourceDir.diskDir(sourceDir)
        val rModule = module?.let { R_ModuleName.of(it) }
        RellApiShellInternal.runShell(config, cSourceDir, rModule)
    }

    public class Config(
        /** Compilation config. */
        public val compileConfig: RellApiCompile.Config,
        /** Database URL. */
        public val databaseUrl: String?,
        /** Enable SQL logging. */
        public val sqlLog: Boolean,
        /** Enable SQL error logging. */
        public val sqlErrorLog: Boolean,
        /** Printer used for Rell `print()` calls. */
        public val outPrinter: Rt_Printer,
        /** Printer used for Rell `log()` calls. */
        public val logPrinter: Rt_Printer,
        /** Shell commands history file, `null` means no history; default: `.rell_history` in the user's home directory. */
        public val historyFile: File?,
        /** Input channel factory (used to read commands). */
        public val inputChannelFactory: ReplInputChannelFactory,
        /** Output channel factory (used to print command execution results). */
        public val outputChannelFactory: ReplOutputChannelFactory,
        /** Print Rell version, help shortcut and current module (if any) on shell start. */
        public val printIntroMessage: Boolean,
    ) {
        public fun toBuilder(): Builder = Builder(this)

        public companion object {
            public val DEFAULT: Config = Config(
                compileConfig = RellApiCompile.Config.DEFAULT,
                databaseUrl = null,
                sqlLog = false,
                sqlErrorLog = false,
                outPrinter = Rt_OutPrinter,
                logPrinter = Rt_LogPrinter(),
                historyFile = RellApiShellInternal.getDefaultReplHistoryFile(),
                inputChannelFactory = ReplIo.DEFAULT_INPUT_FACTORY,
                outputChannelFactory = ReplIo.DEFAULT_OUTPUT_FACTORY,
                printIntroMessage = true,
            )
        }

        public class Builder(proto: Config = DEFAULT) {
            private var compileConfig = proto.compileConfig
            private var databaseUrl = proto.databaseUrl
            private var sqlLog = proto.sqlLog
            private var sqlErrorLog = proto.sqlErrorLog
            private var outPrinter = proto.outPrinter
            private var logPrinter = proto.logPrinter
            private var historyFile = proto.historyFile
            private var inputChannelFactory = proto.inputChannelFactory
            private var outputChannelFactory = proto.outputChannelFactory
            private var printIntroMessage = proto.printIntroMessage

            /** @see [Config.compileConfig] */
            public fun compileConfig(v: RellApiCompile.Config): Builder = apply { compileConfig = v }

            /** @see [Config.databaseUrl] */
            public fun databaseUrl(v: String?): Builder = apply { databaseUrl = v }

            /** @see [Config.sqlLog] */
            public fun sqlLog(v: Boolean): Builder = apply { sqlLog = v }

            /** @see [Config.sqlErrorLog] */
            public fun sqlErrorLog(v: Boolean): Builder = apply { sqlErrorLog = v }

            /** @see [Config.outPrinter] */
            public fun outPrinter(v: Rt_Printer): Builder = apply { outPrinter = v }

            /** @see [Config.logPrinter] */
            public fun logPrinter(v: Rt_Printer): Builder = apply { logPrinter = v }

            /** @see [Config.historyFile] */
            public fun historyFile(v: File?): Builder = apply { historyFile = v }

            /** @see [Config.inputChannelFactory] */
            public fun inputChannelFactory(v: ReplInputChannelFactory): Builder = apply { inputChannelFactory = v }

            /** @see [Config.outputChannelFactory] */
            public fun outputChannelFactory(v: ReplOutputChannelFactory): Builder = apply { outputChannelFactory = v }

            /** @see [Config.printIntroMessage] */
            public fun printIntroMessage(v: Boolean): Builder = apply { printIntroMessage = v }

            public fun build(): Config {
                return Config(
                    compileConfig = compileConfig,
                    databaseUrl = databaseUrl,
                    sqlLog = sqlLog,
                    sqlErrorLog = sqlErrorLog,
                    outPrinter = outPrinter,
                    logPrinter = logPrinter,
                    historyFile = historyFile,
                    inputChannelFactory = inputChannelFactory,
                    outputChannelFactory = outputChannelFactory,
                    printIntroMessage = printIntroMessage,
                )
            }
        }
    }
}

@InternalRellApi
public object RellApiShellInternal {
    public fun runShell(
        config: RellApiRunShell.Config,
        sourceDir: C_SourceDir,
        module: R_ModuleName?,
    ) {
        val compilerOptions = RellApiBaseInternal.makeCompilerOptions(config.compileConfig)

        val globalCtx = RellApiBaseUtils.createGlobalContext(
            compilerOptions,
            typeCheck = false,
            outPrinter = config.outPrinter,
            logPrinter = config.logPrinter,
        )

        val blockRunnerCfg = Rt_BlockRunnerConfig(
            forceTypeCheck = false,
            sqlLog = config.sqlLog,
            dbInitLogLevel = RellPostchainModuleEnvironment.DEFAULT_DB_INIT_LOG_LEVEL,
        )
        val projExt = PostchainReplInterpreterProjExt(PostchainSqlInitProjExt, blockRunnerCfg)

        val shellOptions = ReplShellOptions(
            compilerOptions = compilerOptions,
            inputChannelFactory = config.inputChannelFactory,
            outputChannelFactory = config.outputChannelFactory,
            historyFile = config.historyFile,
            printIntroMessage = config.printIntroMessage,
            moduleArgs = config.compileConfig.moduleArgs,
        )

        RellApiGtxUtils.runWithSqlManager(
            dbUrl = config.databaseUrl,
            sqlLog = config.sqlLog,
            sqlErrorLog = config.sqlErrorLog,
        ) { sqlMgr ->
            ReplShell.start(
                sourceDir,
                module,
                globalCtx,
                sqlMgr,
                projExt,
                shellOptions,
            )
        }
    }

    public fun getDefaultReplHistoryFile(): File? {
        val homeDir = CommonUtils.getHomeDir()
        return if (homeDir == null) null else File(homeDir, ".rell_history")
    }
}
