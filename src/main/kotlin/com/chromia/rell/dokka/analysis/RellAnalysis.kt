package com.chromia.rell.dokka.analysis

import com.chromia.rell.dokka.dri.from
import com.chromia.rell.dokka.model.ExtensionFunction
import com.chromia.rell.dokka.reflection.getFunctionExtensionsByReflection
import com.chromia.rell.dokka.reflection.getNameByReflection
import net.postchain.rell.api.base.RellApiCompile
import net.postchain.rell.api.base.RellApiCompile.Config
import net.postchain.rell.base.model.R_App
import net.postchain.rell.base.model.R_FunctionDefinition
import net.postchain.rell.base.model.R_Module
import org.jetbrains.dokka.links.DRI
import java.io.File

class RellAnalysis(sourceRoot: File, val entryPointModules: List<String>?) {

    private val allFunctions: List<R_FunctionDefinition>
    private val functionsByAppLevelName: Map<String, R_FunctionDefinition>
    private val extensionFunctionsByModule: Map<String, List<ExtensionFunction>>
    private val extensionFunctionsByAppLevelName: Map<String, ExtensionFunction>
    private val extendableFunctions: Set<String> // AppLevelName
    private val modules: List<R_Module>
    private val testModules: List<R_Module>

    init {
        val config = Config.Builder()
                .mountConflictError(false)
                .includeTestSubModules(true)
                .moduleArgsMissingError(false)
                .docSymbolsEnabled(true)
                .appModuleInTestsError(false)
                .build()

        val app = RellApiCompile.compileApp(config, sourceRoot, entryPointModules)

        modules = app.modules.filterNot { it.test }
        testModules = app.modules.filter { it.test }

        allFunctions = app.modules.flatMap { it.functions.values }
        functionsByAppLevelName = allFunctions.associateBy { it.defName.appLevelName }
        val extensionFunctionsByTargetFunction = getFunctionExtensionsByReflection(app)
        val allExtensionFunctions = extensionFunctionsByTargetFunction
                .flatMap { (target, list) -> list.map { f -> target to f } }
                .map { (target, f) ->
                    ExtensionFunction(target, f.fnBase)
                }
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

    fun hiddenPackages(): List<String> {
        val allModules = modules + testModules
        val entryPointModuleNames = entryPointModules ?: emptyList()

        return allModules.flatMap { nonEntryPointQualifierFrom(it, entryPointModuleNames) }
    }

    private fun nonEntryPointQualifierFrom(
            module: R_Module,
            entryPointModuleNames: List<String>
    ): List<String> {
        val definitions = listOf(
                module.functions.values,
                module.constants.values,
                module.objects.values,
                module.enums.values,
                module.operations.values,
                module.queries.values
        ).flatten()

        return definitions
                .map { it.defName.appLevelName to it.defName.module }
                .filterNot { (_, moduleName) -> moduleName in entryPointModuleNames }
                .map { (appLevelName, moduleName) ->
                    parseQualifiedName(appLevelName, moduleName)
                }
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

    private fun getFunctionExtensionsByReflection(app: R_App) =
            app.functionExtensions.getFunctionExtensionsByReflection().associate { extension ->
                extension.uid.getNameByReflection() to extension.extensions
            }

}
