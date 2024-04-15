/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.api.base

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.rell.base.compiler.base.core.C_CompilationResult
import net.postchain.rell.base.compiler.base.core.C_Compiler
import net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.C_CommonError
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.model.R_App
import net.postchain.rell.base.model.R_LangVersion
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.model.R_StructDefinition
import net.postchain.rell.base.runtime.GtvToRtDefaultValueEvaluator
import net.postchain.rell.base.runtime.Rt_Exception
import net.postchain.rell.base.runtime.Rt_GtvModuleArgsSource
import net.postchain.rell.base.utils.*
import java.io.File

object RellApiCompile {
    /**
     * Compile an app.
     *
     * Use-case 1: compile an app same way as **`multirun`** or **`multigen`**. Specify a single app module and no
     * test modules (a test module may add other app modules to the active set).
     *
     * Use-case 2: compile all app modules. Specify `null` as the list of app modules.
     *
     * Use-case 3: compile all app modules and all test modules. Pass `null` for the list of app modules and the *root*
     * module (`""`) in the list of test modules; [Config.includeTestSubModules] must be `true`.
     *
     * @param config Compile configuration.
     * @param sourceDir Source directory.
     * @param appModules List of app (non-test) modules. Empty means none, `null` means all.
     * @param testModules List of test modules. Empty means none.
     * Can contain also app modules, if [Config.includeTestSubModules] is `true`.
     */
    fun compileApp(
        config: Config,
        sourceDir: File,
        appModules: List<String>?,
        testModules: List<String> = immListOf(),
    ): R_App {
        val cSourceDir = C_SourceDir.diskDir(sourceDir)
        val rAppModules = appModules?.map { R_ModuleName.of(it) }?.toImmList()
        val rTestModules = testModules.map { R_ModuleName.of(it) }.toImmList()

        val options = RellApiBaseInternal.makeCompilerOptions(config)
        val (_, rApp) = RellApiBaseInternal.compileApp(config, options, cSourceDir, rAppModules, rTestModules)
        return rApp
    }

    /**
     * Compiles an app, returns a `Gtv`. The returned value is the `Gtv` node to be put at the path `gtx.rell` in a
     * blockchain configuration.
     */
    fun compileGtv(
        config: Config,
        sourceDir: File,
        mainModule: String?,
    ): Gtv {
        val cSourceDir = C_SourceDir.diskDir(sourceDir)
        val rMainModule = mainModule?.let { listOf(R_ModuleName.of(it)) }
        return RellApiBaseInternal.compileGtv(config, cSourceDir, rMainModule)
    }

    class Config(
        /** CLI environment used to print compilation messages and errors. */
        val cliEnv: RellCliEnv,
        /** Language version for backward compatibility (may affect some aspects of compilation). */
        val version: R_LangVersion,
        /** Module arguments. */
        val moduleArgs: Map<R_ModuleName, Gtv>,
        /** Submodules of all test modules are compiled in addition to the explicitly specified test modules, when `true`. */
        val includeTestSubModules: Boolean,
        /** Missing module arguments for a module (which defines `module_args`) causes compilation error, when `true`. */
        val moduleArgsMissingError: Boolean,
        /** Mount name conflicts cause compilation error, when `true`. */
        val mountConflictError: Boolean,
        /** Specifying a non-test module in the list of test modules causes an error, when `true`. */
        val appModuleInTestsError: Boolean,
        /** Additional GTX-modules to include */
        val additionalGtxModules: List<String>,
        /** Do not print non-error compilation messages (warnings) if compilation succeeds, when `true`. */
        val quiet: Boolean,
    ) {
        fun toBuilder() = Builder(this)

        companion object {
            val DEFAULT = Config(
                cliEnv = RellCliEnv.DEFAULT,
                version = RellVersions.VERSION,
                moduleArgs = immMapOf(),
                includeTestSubModules = true,
                moduleArgsMissingError = true,
                mountConflictError = true,
                appModuleInTestsError = true,
                additionalGtxModules = immListOf(),
                quiet = false,
            )
        }

        class Builder(proto: Config = DEFAULT) {
            private var cliEnv = proto.cliEnv
            private var version = proto.version
            private var moduleArgs = proto.moduleArgs
            private var includeTestSubModules = proto.includeTestSubModules
            private var moduleArgsMissingError = proto.moduleArgsMissingError
            private var mountConflictError = proto.mountConflictError
            private var appModuleInTestsError = proto.appModuleInTestsError
            private var additionalGtxModules = proto.additionalGtxModules
            private var quiet = proto.quiet

            /** @see [Config.cliEnv] */
            fun cliEnv(v: RellCliEnv) = apply { cliEnv = v }

            /** @see [Config.version] */
            fun version(v: String) = apply { version = R_LangVersion.of(v) }

            /** @see [Config.version] */
            fun version(v: R_LangVersion) = apply { version = v }

            /** @see [Config.moduleArgs] */
            fun moduleArgs(v: Map<String, Map<String, Gtv>>) = moduleArgs0(
                v.map { R_ModuleName.of(it.key) to GtvFactory.gtv(it.value) }.toImmMap()
            )

            /** @see [Config.moduleArgs] */
            fun moduleArgs(vararg v: Pair<String, Map<String, Gtv>>) = moduleArgs(v.toMap())

            /** @see [Config.moduleArgs] */
            fun moduleArgs0(v: Map<R_ModuleName, Gtv>) = apply { moduleArgs = v.toImmMap() }

            /** @see [Config.includeTestSubModules] */
            fun includeTestSubModules(v: Boolean) = apply { includeTestSubModules = v }

            /** @see [Config.moduleArgsMissingError] */
            fun moduleArgsMissingError(v: Boolean) = apply { moduleArgsMissingError = v }

            /** @see [Config.mountConflictError] */
            fun mountConflictError(v: Boolean) = apply { mountConflictError = v }

            /** @see [Config.appModuleInTestsError] */
            fun appModuleInTestsError(v: Boolean) = apply { appModuleInTestsError = v }
            /** @see [Config.additionalGtxModules] */
            fun additionalGtxModules(v: List<String>) = apply { additionalGtxModules = v.toImmList() }

            /** @see [Config.quiet] */
            fun quiet(v: Boolean) = apply { quiet = v }

            fun build(): Config {
                return Config(
                    cliEnv = cliEnv,
                    version = version,
                    moduleArgs = moduleArgs,
                    includeTestSubModules = includeTestSubModules,
                    moduleArgsMissingError = moduleArgsMissingError,
                    mountConflictError = mountConflictError,
                    appModuleInTestsError = appModuleInTestsError,
                    additionalGtxModules = additionalGtxModules,
                    quiet = quiet,
                )
            }
        }
    }
}

class RellApiCompilationResult(
    val cRes: C_CompilationResult,
)

object RellApiBaseInternal {
    fun compileApp(
        config: RellApiCompile.Config,
        options: C_CompilerOptions,
        sourceDir: C_SourceDir,
        appModules: List<R_ModuleName>?,
        testModules: List<R_ModuleName>,
    ): Pair<RellApiCompilationResult, R_App> {
        return wrapCompilation(config) {
            compileApp0(config, options, sourceDir, appModules, testModules)
        }
    }

    fun compileApp0(
        config: RellApiCompile.Config,
        options: C_CompilerOptions,
        sourceDir: C_SourceDir,
        appModules: List<R_ModuleName>?,
        testModules: List<R_ModuleName>,
    ): RellApiCompilationResult {
        val modSel = makeCompilerModuleSelection(config, appModules, testModules)
        val cRes = C_Compiler.compile(sourceDir, modSel, options)

        val rApp = cRes.app
        if (rApp != null && cRes.errors.isEmpty()) {
            validateAllModuleArgs(rApp, options, config.moduleArgs, config.moduleArgsMissingError)
        }

        return RellApiCompilationResult(cRes)
    }

    fun compileGtv(
        config: RellApiCompile.Config,
        sourceDir: C_SourceDir,
        modules: List<R_ModuleName>?,
    ): Gtv {
        val (gtv, _) = compileGtvEx(config, sourceDir, modules)
        return gtv
    }

    fun compileGtvEx(
        config: RellApiCompile.Config,
        sourceDir: C_SourceDir,
        modules: List<R_ModuleName>?,
    ): Pair<Gtv, RellGtxModuleApp> {
        val options = makeCompilerOptions(config)
        val (apiRes, rApp) = compileApp(config, options, sourceDir, modules, immListOf())

        val mainModules = modules ?: RellApiBaseUtils.getMainModules(rApp)

        val gtv = catchCommonError {
            compileGtv0(config, sourceDir, mainModules, apiRes.cRes.files)
        }

        return gtv to RellGtxModuleApp(rApp, options)
    }

    fun compileGtv0(
        config: RellApiCompile.Config,
        sourceDir: C_SourceDir,
        modules: List<R_ModuleName>,
        files: List<C_SourcePath>,
    ): Gtv {
        val sources = RellConfigGen.getModuleFiles(sourceDir, files)

        val map = mutableMapOf(
            "modules" to GtvFactory.gtv(modules.map { GtvFactory.gtv(it.str()) }),
            RellGtxConfigConstants.SOURCES_KEY to GtvFactory.gtv(sources.mapValues { (_, v) -> GtvFactory.gtv(v) }),
            RellGtxConfigConstants.LANG_VERSION_KEY to GtvFactory.gtv(config.version.str()),
            RellGtxConfigConstants.COMPILER_VERSION_KEY to GtvFactory.gtv(RellVersions.VERSION.str()),
        )

        val moduleArgs = config.moduleArgs
        if (moduleArgs.isNotEmpty()) {
            val argsGtv = GtvFactory.gtv(moduleArgs.mapKeys { (k, _) -> k.str() })
            map["moduleArgs"] = argsGtv
        }

        return GtvFactory.gtv(map.toImmMap())
    }

    fun makeCompilerOptions(config: RellApiCompile.Config): C_CompilerOptions {
        return C_CompilerOptions.DEFAULT.toBuilder()
            .compatibility(config.version)
            .mountConflictError(config.mountConflictError)
            .appModuleInTestsError(config.appModuleInTestsError)
            .build()
    }

    fun makeCompilerModuleSelection(
        config: RellApiCompile.Config,
        appModules: List<R_ModuleName>?,
        testModules: List<R_ModuleName>,
    ): C_CompilerModuleSelection {
        return C_CompilerModuleSelection(appModules, testModules, testSubModules = config.includeTestSubModules)
    }

    private fun validateAllModuleArgs(
        app: R_App,
        compilerOptions: C_CompilerOptions,
        actualArgs: Map<R_ModuleName, Gtv>,
        missingError: Boolean,
    ) {
        val expectedArgs = app.moduleMap
            .filterValues { it.moduleArgs != null }
            .mapValues { it.value.moduleArgs!! }
            .toImmMap()

        val defaultValuesSupported = Rt_GtvModuleArgsSource.DEFAULT_VALUES_SWITCH.isActive(compilerOptions)
        val missingModules = expectedArgs
            .filter { it.key !in actualArgs && !(defaultValuesSupported && it.value.hasDefaultConstructor) }
            .map { it.key }
            .sorted().toImmList()

        if (missingModules.isNotEmpty() && missingError) {
            val modulesCode = missingModules.joinToString(",") { it.str() }
            val modulesMsg = missingModules.joinToString(", ") { it.displayStr() }
            throw C_CommonError("module_args_missing:$modulesCode", "Missing module_args for module(s): $modulesMsg")
        }

        for (module in expectedArgs.keys.sorted()) {
            val expectedStruct = expectedArgs.getValue(module)
            val actualGtv = actualArgs[module]
            if (actualGtv != null) {
                validateOneModuleArgs(module, expectedStruct, actualGtv, compilerOptions)
            }
        }
    }

    private fun validateOneModuleArgs(
        module: R_ModuleName,
        expectedStruct: R_StructDefinition,
        actualGtv: Gtv,
        compilerOptions: C_CompilerOptions,
    ) {
        val defaultValueEvaluator = GtvToRtDefaultValueEvaluator {
            throw UnsupportedOperationException("Default values evaluation not supported during module args validation")
        }

        try {
            PostchainGtvUtils.moduleArgsGtvToRt(
                expectedStruct,
                actualGtv,
                validateOnly = true,
                defaultValueEvaluator = defaultValueEvaluator,
                compilerOptions = compilerOptions,
            )
        } catch (e: Rt_Exception) {
            val msg = "Bad module_args for module '${module.str()}': ${e.err.message()}"
            throw C_CommonError("module_args_bad:$module:${e.err.code()}", msg)
        } catch (e: Throwable) {
            val msg = "Bad module_args for module '${module.str()}': ${e.message}"
            throw C_CommonError("module_args_bad:$module", msg)
        }
    }


    fun wrapCompilation(
        config: RellApiCompile.Config,
        code: () -> RellApiCompilationResult,
    ): Pair<RellApiCompilationResult, R_App> {
        val cliEnv = config.cliEnv
        val res = catchCommonError {
            code()
        }
        val rApp = RellApiBaseUtils.handleCompilationResult(cliEnv, res.cRes, config.quiet)
        return res to rApp
    }

    fun <T> catchCommonError(code: () -> T): T {
        try {
            return code()
        } catch (e: C_CommonError) {
            throw RellCliBasicException(e.msg)
        }
    }
}
