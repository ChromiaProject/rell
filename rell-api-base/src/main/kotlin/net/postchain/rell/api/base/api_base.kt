/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
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
import net.postchain.rell.base.model.*
import net.postchain.rell.base.runtime.GtvToRtDefaultValueEvaluator
import net.postchain.rell.base.runtime.Rt_Exception
import net.postchain.rell.base.runtime.Rt_GtvModuleArgsSource
import net.postchain.rell.base.utils.*
import java.io.File
import kotlin.annotation.AnnotationTarget.*

@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
@Retention(AnnotationRetention.BINARY)
@Target(
    CLASS,
    ANNOTATION_CLASS,
    PROPERTY,
    FIELD,
    LOCAL_VARIABLE,
    VALUE_PARAMETER,
    CONSTRUCTOR,
    FUNCTION,
    PROPERTY_GETTER,
    PROPERTY_SETTER,
    TYPEALIAS
)
@MustBeDocumented
public annotation class InternalRellApi

public object RellApiCompile {
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
    public fun compileApp(
        config: Config,
        sourceDir: File,
        appModules: List<String>?,
        testModules: List<String> = immListOf(),
    ): R_App {
        val cSourceDir = C_SourceDir.diskDir(sourceDir)
        val rAppModules = appModules?.mapToImmList { R_ModuleName.of(it) }
        val rTestModules = testModules.mapToImmList { R_ModuleName.of(it) }

        val options = RellApiBaseInternal.makeCompilerOptions(config)
        val (_, rApp) = RellApiBaseInternal.compileApp(config, options, cSourceDir, rAppModules, rTestModules)
        return rApp
    }

    /**
     * Compiles an app, returns a `Gtv`. The returned value is the `Gtv` node to be put at the path `gtx.rell` in a
     * blockchain configuration.
     */
    public fun compileGtv(
        config: Config,
        sourceDir: File,
        mainModule: String?,
    ): Gtv {
        val cSourceDir = C_SourceDir.diskDir(sourceDir)
        val rMainModule = mainModule?.let { listOf(R_ModuleName.of(it)) }
        return RellApiBaseInternal.compileGtv(config, cSourceDir, rMainModule)
    }

    public class Config(
        /** CLI environment used to print compilation messages and errors. */
        public val cliEnv: RellCliEnv,
        /** Language version for backward compatibility (may affect some aspects of compilation). */
        public val version: R_LangVersion,
        /** Module arguments. */
        public val moduleArgs: ImmMap<R_ModuleName, Gtv>,
        /** Submodules of all test modules are compiled in addition to the explicitly specified test modules, when `true`. */
        public val includeTestSubModules: Boolean,
        /** Automatically includes all submodules in the compilation scope **/
        public val includeAppSubModules: Boolean,
        /** Missing module arguments for a module (which defines `module_args`) causes compilation error, when `true`. */
        public val moduleArgsMissingError: Boolean,
        /** Mount name conflicts cause compilation error, when `true`. */
        public val mountConflictError: Boolean,
        /** Specifying a non-test module in the list of test modules causes an error, when `true`. */
        public val appModuleInTestsError: Boolean,
        /** Additional GTX-modules to include */
        public val additionalGtxModules: ImmList<String>,
        /** Enable adding documentation symbol to return objects */
        public val docSymbolsEnabled: Boolean,
        /** Do not print non-error compilation messages (warnings) if compilation succeeds, when `true`. */
        public val quiet: Boolean,
    ) {
        public fun toBuilder(): Builder = Builder(this)

        public companion object {
            public val DEFAULT: Config = Config(
                cliEnv = RellCliEnv.DEFAULT,
                version = RellVersions.VERSION,
                moduleArgs = immMapOf(),
                includeTestSubModules = true,
                includeAppSubModules = false,
                moduleArgsMissingError = true,
                mountConflictError = true,
                appModuleInTestsError = true,
                additionalGtxModules = immListOf(),
                docSymbolsEnabled = false,
                quiet = false,
            )
        }

        public class Builder(proto: Config = DEFAULT) {
            private var cliEnv = proto.cliEnv
            private var version = proto.version
            private var moduleArgs = proto.moduleArgs
            private var includeTestSubModules = proto.includeTestSubModules
            private var includeAppSubModules = proto.includeAppSubModules
            private var moduleArgsMissingError = proto.moduleArgsMissingError
            private var mountConflictError = proto.mountConflictError
            private var appModuleInTestsError = proto.appModuleInTestsError
            private var additionalGtxModules = proto.additionalGtxModules
            private var docSymbolsEnabled = proto.docSymbolsEnabled
            private var quiet = proto.quiet

            /** @see [Config.cliEnv] */
            public fun cliEnv(v: RellCliEnv): Builder = apply { cliEnv = v }

            /** @see [Config.version] */
            public fun version(v: String): Builder = apply { version = R_LangVersion.of(v) }

            /** @see [Config.version] */
            public fun version(v: R_LangVersion): Builder = apply { version = v }

            /** @see [Config.moduleArgs] */
            public fun moduleArgs(v: Map<String, Map<String, Gtv>>): Builder = moduleArgs0(
                v.map { R_ModuleName.of(it.key) to GtvFactory.gtv(it.value) }.toImmMap()
            )

            /** @see [Config.moduleArgs] */
            public fun moduleArgs(vararg v: Pair<String, Map<String, Gtv>>): Builder = moduleArgs(v.toMap())

            /** @see [Config.moduleArgs] */
            public fun moduleArgs0(v: Map<R_ModuleName, Gtv>): Builder = apply { moduleArgs = v.toImmMap() }

            /** @see [Config.includeTestSubModules] */
            public fun includeTestSubModules(v: Boolean): Builder = apply { includeTestSubModules = v }

            /** @see [Config.includeAppSubModules] */
            public fun includeAppSubModules(v: Boolean): Builder = apply { includeAppSubModules = v }

            /** @see [Config.moduleArgsMissingError] */
            public fun moduleArgsMissingError(v: Boolean): Builder = apply { moduleArgsMissingError = v }

            /** @see [Config.mountConflictError] */
            public fun mountConflictError(v: Boolean): Builder = apply { mountConflictError = v }

            /** @see [Config.appModuleInTestsError] */
            public fun appModuleInTestsError(v: Boolean): Builder = apply { appModuleInTestsError = v }

            /** @see [Config.additionalGtxModules] */
            public fun additionalGtxModules(v: List<String>): Builder = apply { additionalGtxModules = v.toImmList() }

            /** @see [Config.docSymbolsEnabled] */
            public fun docSymbolsEnabled(v: Boolean): Builder = apply { docSymbolsEnabled = v }

            /** @see [Config.quiet] */
            public fun quiet(v: Boolean): Builder = apply { quiet = v }

            public fun build(): Config {
                return Config(
                    cliEnv = cliEnv,
                    version = version,
                    moduleArgs = moduleArgs,
                    includeTestSubModules = includeTestSubModules,
                    includeAppSubModules = includeAppSubModules,
                    moduleArgsMissingError = moduleArgsMissingError,
                    mountConflictError = mountConflictError,
                    appModuleInTestsError = appModuleInTestsError,
                    additionalGtxModules = additionalGtxModules,
                    docSymbolsEnabled = docSymbolsEnabled,
                    quiet = quiet,
                )
            }
        }
    }
}

@InternalRellApi
public class RellApiCompilationResult(
    public val cRes: C_CompilationResult,
)

@InternalRellApi
public object RellApiBaseInternal {
    public fun compileApp(
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

    public fun compileApp0(
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

    public fun compileGtv(
        config: RellApiCompile.Config,
        sourceDir: C_SourceDir,
        modules: List<R_ModuleName>?,
    ): Gtv {
        val (gtv, _) = compileGtvEx(config, sourceDir, modules)
        return gtv
    }

    public fun compileGtvEx(
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

    internal fun compileGtv0(
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

    public fun makeCompilerOptions(config: RellApiCompile.Config): C_CompilerOptions {
        return catchCommonError {
            makeCompilerOptions0(config)
        }
    }

    public fun makeCompilerOptions0(config: RellApiCompile.Config): C_CompilerOptions {
        checkVersion(config)
        return C_CompilerOptions.DEFAULT.toBuilder()
            .compatibility(config.version)
            .mountConflictError(config.mountConflictError)
            .appModuleInTestsError(config.appModuleInTestsError)
            .ideDocSymbolsEnabled(config.docSymbolsEnabled)
            .build()
    }

    public fun makeCompilerModuleSelection(
        config: RellApiCompile.Config,
        appModules: List<R_ModuleName>?,
        testModules: List<R_ModuleName>,
    ): C_CompilerModuleSelection {
        return C_CompilerModuleSelection(
            appModules?.toImmList(),
            testModules.toImmList(),
            testSubModules = config.includeTestSubModules,
            appSubModules = config.includeAppSubModules
        )
    }

    private fun validateAllModuleArgs(
        app: R_App,
        compilerOptions: C_CompilerOptions,
        actualArgs: Map<R_ModuleName, Gtv>,
        missingError: Boolean,
    ) {
        val expectedArgs = app.moduleMap
            .filterValues { it.moduleArgs != null }
            .mapValuesToImmMap { it.value.moduleArgs!! }

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
        try {
            PostchainGtvUtils.moduleArgsGtvToRt(
                expectedStruct,
                actualGtv,
                validateOnly = true,
                defaultValueEvaluator = GtvToRtDefaultValueEvaluator.getError(),
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

    private fun wrapCompilation(
        config: RellApiCompile.Config,
        code: () -> RellApiCompilationResult,
    ): Pair<RellApiCompilationResult, R_App> {
        val res = catchCommonError {
            code()
        }
        val rApp = RellApiBaseUtils.handleCompilationResult(config.cliEnv, res.cRes, config.quiet)
        return res to rApp
    }

    private fun checkVersion(config: RellApiCompile.Config) {
        val v = config.version
        if (v !in RellVersions.SUPPORTED_VERSIONS) {
            throw C_CommonError("config:version:unknown:$v", "Unknown Rell version: $v")
        }

        RellVersions.checkCompatibilityVersion(v) { msg ->
            C_CommonError("config:version:unsupported:$v", msg)
        }
    }

    public fun <T> catchCommonError(code: () -> T): T {
        try {
            return code()
        } catch (e: C_CommonError) {
            throw RellCliBasicException(e.msg)
        }
    }
}
