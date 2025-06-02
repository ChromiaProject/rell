/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.def

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.core.C_CompilerPass
import net.postchain.rell.base.compiler.base.core.C_MessageContext
import net.postchain.rell.base.compiler.base.fn.C_FunctionCallTarget
import net.postchain.rell.base.compiler.base.fn.C_FunctionCallTargetBase
import net.postchain.rell.base.compiler.base.fn.C_FunctionCallTarget_Regular
import net.postchain.rell.base.compiler.base.module.*
import net.postchain.rell.base.compiler.base.utils.C_Errors
import net.postchain.rell.base.compiler.base.utils.C_LateGetter
import net.postchain.rell.base.compiler.base.utils.C_LateInit
import net.postchain.rell.base.compiler.vexpr.V_FunctionCallTarget
import net.postchain.rell.base.compiler.vexpr.V_FunctionCallTarget_AbstractUserFunction
import net.postchain.rell.base.model.R_FunctionBase
import net.postchain.rell.base.model.R_FunctionDefinition
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.utils.*

class C_AbstractUserGlobalFunction(
    fnPos: S_Pos,
    rFunction: R_FunctionDefinition,
    hasDefaultBody: Boolean,
    private val rFnBase: R_FunctionBase,
): C_UserGlobalFunction(rFunction) {
    val descriptor = C_AbstractFunctionDescriptor(fnPos, rFunction, hasDefaultBody, headerGetter)

    private val rOverrideLate = C_LateInit(C_CompilerPass.EXPRESSIONS, R_FunctionBase(rFunction.defName))

    override fun getAbstractDescriptor() = descriptor

    override fun compileCallTarget(base: C_FunctionCallTargetBase, retType: R_Type?): C_FunctionCallTarget {
        return C_FunctionCallTarget_AbstractUserFunction(base, retType, rFunction, rOverrideLate.getter)
    }

    fun compileOverride() {
        val actualFnBase = descriptor.getOverride() ?: rFnBase
        rOverrideLate.set(actualFnBase)
    }
}

private class C_FunctionCallTarget_AbstractUserFunction(
    base: C_FunctionCallTargetBase,
    retType: R_Type?,
    private val rBaseFunction: R_FunctionDefinition,
    private val overrideGetter: C_LateGetter<R_FunctionBase>
): C_FunctionCallTarget_Regular(base, retType) {
    override fun createVTarget(): V_FunctionCallTarget {
        return V_FunctionCallTarget_AbstractUserFunction(rBaseFunction, overrideGetter)
    }
}

class C_AbstractFunctionDescriptor(
        private val fnPos: S_Pos,
        private val rFunction: R_FunctionDefinition,
        val hasDefaultBody: Boolean,
        private val headerGetter: C_LateGetter<C_UserFunctionHeader>
) {
    private val overrideFnBaseLate = C_LateInit<R_FunctionBase?>(C_CompilerPass.ABSTRACT, null)

    fun functionPos() = fnPos
    fun functionName() = rFunction.appLevelName

    fun header() = headerGetter.get()

    fun setOverride(overrideFnBase: R_FunctionBase) {
        overrideFnBaseLate.set(overrideFnBase)
    }

    fun getOverride(): R_FunctionBase? {
        return overrideFnBaseLate.get()
    }
}

class C_OverrideFunctionDescriptor(val fnPos: S_Pos, private val rFnBase: R_FunctionBase) {
    private val abstractLate = C_LateInit<C_AbstractFunctionDescriptor?>(C_CompilerPass.MEMBERS, null)
    private var bind = false

    fun abstract() = abstractLate.get()

    fun setAbstract(abstract: C_AbstractFunctionDescriptor?) {
        abstractLate.set(abstract)
    }

    fun bind(): C_AbstractFunctionDescriptor? {
        check(!bind)
        bind = true
        val abstract = abstractLate.get()
        abstract?.setOverride(rFnBase)
        return abstract
    }
}

object C_AbstractCompiler {
    fun compile(msgCtx: C_MessageContext, modules: ImmList<C_ModuleDescriptor>) {
        val conflictsProcessor = C_OverrideConflictsProcessor(msgCtx)
        val actualOverrides = conflictsProcessor.processApp(modules)
        processMissingOverrides(msgCtx, modules, actualOverrides)
    }

    private fun processMissingOverrides(
            msgCtx: C_MessageContext,
            modules: ImmList<C_ModuleDescriptor>,
            actualOverrides: ImmSet<C_AbstractFunctionDescriptor>
    ) {
        val missingOverridesProcessor = C_MissingOverridesProcessor(msgCtx, actualOverrides)

        for (module in modules) {
            missingOverridesProcessor.processModule(module)
        }

        if (!msgCtx.globalCtx.compilerOptions.ide) {
            for (module in modules) {
                missingOverridesProcessor.processModuleExtra(module)
            }
        }
    }
}

private class C_OverrideConflictsProcessor(private val msgCtx: C_MessageContext) {
    private val errorLocations = mutableSetOf<C_OverrideLocation>()
    private val errorOverrides = mutableSetOf<C_OverrideFunctionDescriptor>()

    fun processApp(modules: List<C_ModuleDescriptor>): ImmSet<C_AbstractFunctionDescriptor> {
        val entries = mutableListOf<C_OverrideEntry>()
        for (module in modules) {
            val importsDescriptor = module.importsDescriptor()
            entries.addAll(processModule(importsDescriptor))
        }

        val goodEntries = processConflicts(entries, true)

        val res = mutableSetOf<C_AbstractFunctionDescriptor>()

        for (entry in goodEntries) {
            val abstract = entry.override.bind()
            if (abstract != null) res.add(abstract)
        }

        return res.toImmSet()
    }

    private fun processModule(module: C_ModuleImportsDescriptor): List<C_OverrideEntry> {
        val entries = mutableListOf<C_OverrideEntry>()
        for (file in module.files) {
            entries.addAll(processFile(module, file))
        }
        return processConflicts(entries, false)
    }

    private fun processFile(module: C_ModuleImportsDescriptor, file: C_FileImportsDescriptor): List<C_OverrideEntry> {
        val entries = collectOverrides(module, file)
        return processConflicts(entries, false)
    }

    private fun collectOverrides(
        module: C_ModuleImportsDescriptor,
        file: C_FileImportsDescriptor
    ): ImmList<C_OverrideEntry> {
        val res = mutableListOf<C_OverrideEntry>()

        val visitedModules = mutableSetOf<C_ContainerKey>()
        visitedModules.add(module.key)

        for (import in file.imports) {
            val sub = collectImportOverrides(import, visitedModules)
            res.addAll(sub)
        }

        collectDirectOverrides(null, module, file, res)

        return res.toImmList()
    }

    private fun collectImportOverrides(
            import: C_ImportDescriptor,
            visitedModules: MutableSet<C_ContainerKey>
    ): ImmList<C_OverrideEntry> {
        val res = mutableListOf<C_OverrideEntry>()

        val allModules = C_AbstractUtils.collectImportedModules(import, visitedModules, false)
        for (subModule in allModules) {
            for (file in subModule.files) {
                collectDirectOverrides(import, subModule, file, res)
            }
        }

        return res.groupBy { it.abstract }.values.mapToImmList { it[0] }
    }

    private fun collectDirectOverrides(
            import: C_ImportDescriptor?,
            module: C_ModuleImportsDescriptor,
            file: C_FileImportsDescriptor,
            res: MutableList<C_OverrideEntry>)
    {
        for (override in file.overrides) {
            val abstract = override.abstract()
            if (abstract != null) {
                val location = C_OverrideLocation(import, module.name, override.fnPos)
                res.add(C_OverrideEntry(abstract, override, immListOf(location)))
            }
        }
    }

    private fun processConflicts(entries: List<C_OverrideEntry>, appLevel: Boolean): List<C_OverrideEntry> {
        val grouped = entries.groupBy { Pair(it.abstract, it.override) }
                .map { (k, v) -> C_OverrideEntry(k.first, k.second, v.flatMapToImmList { it.locations }) }
                .groupBy { it.abstract }

        val res = mutableListOf<C_OverrideEntry>()

        for (groupEntries in grouped.values) {
            if (groupEntries.size >= 2) {
                for (entry in groupEntries) {
                    if (!errorOverrides.add(entry.override) && appLevel) continue
                    val otherEntry = groupEntries.first { it !== entry }
                    processConflict(entry, otherEntry)
                }
            }
            res.add(groupEntries[0])
        }

        return res
    }

    private fun processConflict(entry: C_OverrideEntry, otherEntry: C_OverrideEntry) {
        val otherLocation = otherEntry.locations[0]
        for (location in entry.locations) {
            processConflictLocation(entry.abstract, location, otherLocation)
        }
    }

    private fun processConflictLocation(
            abstract: C_AbstractFunctionDescriptor,
            location: C_OverrideLocation,
            otherLocation: C_OverrideLocation
    ) {
        if (!errorLocations.add(location)) return
        val pos = location.import?.pos ?: location.fnPos

        val locCode1 = locationCode(location)
        val locCode2 = locationCode(otherLocation)
        val fName = abstract.functionName()
        val code = "override:conflict:[$fName]:[$locCode1]:[$locCode2]"

        val locMsg1 = locationMsg(location)
        val locMsg2 = otherLocationMsg(otherLocation)
        val baseMsg = "Override conflict: override for function '$fName' defined"
        val msg = if (locMsg1 == null) "$baseMsg at $locMsg2" else "$baseMsg at $locMsg1 and at $locMsg2"

        msgCtx.error(pos, code, msg)
    }

    private fun locationCode(l: C_OverrideLocation): String {
        val importCode = if (l.import == null) "direct" else "import:${l.import.module.name.str()}"
        val pos = l.fnPos.strLine()
        return "$importCode:${l.fnModule.str()}:$pos"
    }

    private fun locationMsg(l: C_OverrideLocation): String? {
        return if (l.import == null) null else otherLocationMsg(l)
    }

    private fun otherLocationMsg(l: C_OverrideLocation): String {
        val pos = l.fnPos.strLine()
        return if (l.import == null) pos else "$pos (via import ${l.import.module.name.str()} at ${l.import.pos.strLine()})"
    }

    private class C_OverrideLocation(val import: C_ImportDescriptor?, val fnModule: R_ModuleName, val fnPos: S_Pos)

    private class C_OverrideEntry(
            val abstract: C_AbstractFunctionDescriptor,
            val override: C_OverrideFunctionDescriptor,
            val locations: ImmList<C_OverrideLocation>
    )
}

private class C_MissingOverridesProcessor(
        private val msgCtx: C_MessageContext,
        private val actualOverrides: ImmSet<C_AbstractFunctionDescriptor>
) {
    private val globalCtx = msgCtx.globalCtx
    private val errorAbstracts = mutableSetOf<C_AbstractFunctionDescriptor>()

    fun processModule(module: C_ModuleDescriptor) {
        if (!module.header.abstract) {
            processNonAbstractModule(module)
        }
    }

    fun processModuleExtra(module: C_ModuleDescriptor) {
        if (module.header.abstract) {
            for (file in module.importsDescriptor().files) {
                processFileExtra(file)
            }
        }
    }

    private fun processNonAbstractModule(module: C_ModuleDescriptor) {
        val allOverrides = collectAllOverrides(module)

        val importsDesc = module.importsDescriptor()
        for (file in importsDesc.files) {
            processFile(file, allOverrides)
        }
    }

    private fun collectAllOverrides(module: C_ModuleDescriptor): ImmSet<C_AbstractFunctionDescriptor> {
        val res = mutableSetOf<C_AbstractFunctionDescriptor>()

        for (file in module.importsDescriptor().files) {
            res.addAll(file.overrides.mapNotNull { it.abstract() })

            for ((_, impFile) in getImportedFiles(file, false)) {
                res.addAll(impFile.overrides.mapNotNull { it.abstract() })
            }
        }

        return res.toImmSet()
    }

    private fun processFile(file: C_FileImportsDescriptor, allOverrides: Set<C_AbstractFunctionDescriptor>) {
        for ((import, impFile) in getImportedFiles(file, true)) {
            for (abstract in impFile.abstracts) {
                if (!abstract.hasDefaultBody && abstract !in allOverrides) {
                    processMissingOverride(import, abstract)
                }
            }
        }
    }

    private fun processMissingOverride(import: C_ImportDescriptor, abstract: C_AbstractFunctionDescriptor) {
        val fName = abstract.functionName()
        val fPos = abstract.functionPos()
        C_Errors.errOverrideMissing(msgCtx, import.pos, fName, fPos)
        errorAbstracts.add(abstract)
    }

    private fun getImportedFiles(
            file: C_FileImportsDescriptor,
            abstractOnly: Boolean
    ): List<Pair<C_ImportDescriptor, C_FileImportsDescriptor>> {
        val res = mutableListOf<Pair<C_ImportDescriptor, C_FileImportsDescriptor>>()

        val visitedModules = mutableSetOf<C_ContainerKey>()
        for (import in file.imports) {
            val modules = C_AbstractUtils.collectImportedModules(import, visitedModules, abstractOnly)
            for (impModule in modules) {
                for (impFile in impModule.files) {
                    res.add(Pair(import, impFile))
                }
            }
        }

        return res
    }

    private fun processFileExtra(file: C_FileImportsDescriptor) {
        // Extra safety (not a real case so far).
        for (abstract in file.abstracts) {
            if (!abstract.hasDefaultBody && abstract !in actualOverrides && abstract !in errorAbstracts) {
                val fName = abstract.functionName()
                val fPos = abstract.functionPos()
                C_Errors.errOverrideMissing(msgCtx, fPos, fName, fPos)
            }
        }
    }
}

private object C_AbstractUtils {
    fun collectImportedModules(
            import: C_ImportDescriptor,
            visitedModules: MutableSet<C_ContainerKey>,
            abstractOnly: Boolean
    ): ImmList<C_ModuleImportsDescriptor> {
        val res = mutableListOf<C_ModuleImportsDescriptor>()
        collectImportedModules0(import, visitedModules, abstractOnly, res)
        return res.toImmList()
    }

    private fun collectImportedModules0(
            import: C_ImportDescriptor,
            visitedModules: MutableSet<C_ContainerKey>,
            abstractOnly: Boolean,
            res: MutableList<C_ModuleImportsDescriptor>
    ) {
        if (!visitedModules.add(import.module.containerKey)) return
        if (abstractOnly && !import.module.header.abstract) return

        res.add(import.module.importsDescriptor())

        val importsDesc = import.module.importsDescriptor()
        for (file in importsDesc.files) {
            for (subImport in file.imports) {
                collectImportedModules0(subImport, visitedModules, abstractOnly, res)
            }
        }
    }
}
