/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

@file:OptIn(InternalRellApi::class)

package com.chromia.rell.dokka.analysis


import com.chromia.rell.dokka.dri.from
import com.chromia.rell.dokka.model.ExtensionFunction
import net.postchain.rell.api.base.InternalRellApi
import net.postchain.rell.api.base.RellApiBaseInternal
import net.postchain.rell.api.base.RellApiCompile.Config
import net.postchain.rell.api.base.RellCliEnv
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.model.R_Definition
import net.postchain.rell.base.model.R_FunctionDefinition
import net.postchain.rell.base.model.R_Module
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.mapToImmList
import org.jetbrains.dokka.links.DRI
import java.io.File

class RellAnalysis(
    sourceRoot: File,
    val entryPointModules: List<String>?,
    val additionalModules: List<String>? = null,
    customCliEnv: RellCliEnv? = null
) {

    private val allFunctions: List<R_FunctionDefinition>
    private val functionsByAppLevelName: Map<String, R_FunctionDefinition>
    private val extensionFunctionsByModule: Map<String, List<ExtensionFunction>>
    private val extensionFunctionsByAppLevelName: Map<String, ExtensionFunction>
    private val extendableFunctions: Set<String> // AppLevelName
    private val modules: List<R_Module>
    private val testModules: List<R_Module>

    init {
        val config = Config.Builder().apply {
            mountConflictError(false)
            includeTestSubModules(true)
            moduleArgsMissingError(false)
            docSymbolsEnabled(true)
            appModuleInTestsError(false)
            customCliEnv?.let { cliEnv(customCliEnv) }
        }.build()

        val modulesToCompile = (entryPointModules.orEmpty() + additionalModules.orEmpty()).distinct()

        val cSourceDir = C_SourceDir.diskDir(sourceRoot)
        val rAppModules = modulesToCompile.mapToImmList { ModuleName.of(it) }
        val options = RellApiBaseInternal.makeCompilerOptions(config)
        val (apiRes, _) = RellApiBaseInternal.compileApp(config, options, cSourceDir, rAppModules, immListOf())
        val app = checkNotNull(apiRes.cRes.app) { "Compilation failed" }

        modules = app.modules.filterNot { it.test }
        testModules = app.modules.filter { it.test }

        allFunctions = app.modules.flatMap { it.functions.values }
        functionsByAppLevelName = allFunctions.associateBy { it.defName.appLevelName }

        val extensionFunctionsByTargetFunction = app.functionExtensions.list
            .associate { extension -> extension.uid.name to extension.extensions }

        val allExtensionFunctions = extensionFunctionsByTargetFunction
            .flatMap { (target, list) -> list.map { f -> target to f } }
            .map { (target, f) -> ExtensionFunction(target, f.fnBase) }

        extensionFunctionsByAppLevelName = allExtensionFunctions.associateBy { it.defName.appLevelName }
        extensionFunctionsByModule = allExtensionFunctions.groupBy { it.defName.module }
        extendableFunctions = extensionFunctionsByTargetFunction.keys
    }

    fun findFunctionReference(appLevelName: String): DRI? {
        return functionsByAppLevelName[appLevelName]?.let { DRI.from(it) }
    }

    fun isExtendable(appLevelName: String) = extendableFunctions.contains(appLevelName)

    fun hasExtension(f: R_FunctionDefinition) = extensionFunctionsByAppLevelName.containsKey(f.appLevelName)

    fun getExtensionFunctions(moduleName: String) = extensionFunctionsByModule[moduleName] ?: listOf()

    fun modules() = modules

    fun testModules() = testModules

    fun hiddenPackages(): List<String> =
        (modules + testModules)
            .flatMap(::nonEntryPointQualifierFrom)
            .distinct()

    private fun nonEntryPointQualifierFrom(
        module: R_Module,
    ): List<String> {
        val definitions = module.allDefinitions

        return definitions
            .map { it.defName.appLevelName to it.defName.module }
            .filterNot { (_, moduleName) -> shouldExclude(module, moduleName) }
            .map { (appLevelName, moduleName) ->
                parseQualifiedName(appLevelName, moduleName)
            }
    }

    private fun shouldExclude(module: R_Module, moduleName: String) =
        if (module.test || isLibraryModule(moduleName)) {
            additionalModules?.any { moduleName == it } ?: true
        } else true

    private val R_Module.allDefinitions: List<R_Definition>
        get() = listOf(
            functions.values,
            constants.values,
            objects.values,
            enums.values,
            operations.values,
            queries.values
        ).flatten()

    private fun isLibraryModule(moduleName: String): Boolean = moduleName.run {
        startsWith("lib.") && entryPointModules
            .orEmpty()
            .none { startsWith(it) }
    }

    private fun parseQualifiedName(appLevelName: String, moduleName: String): String {
        val parts = appLevelName.split(":".toRegex(), limit = 2)

        return if (parts.size > 1) {
            val (modulePart, entityPart) = parts
            val lastDotIndex = entityPart.lastIndexOf(".")
            if (lastDotIndex > 0) {
                "${modulePart}.${entityPart.substring(0, lastDotIndex)}"
            } else {
                modulePart
            }
        } else {
            moduleName
        }
    }
}
