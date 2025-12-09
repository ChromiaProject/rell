/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.core

import net.postchain.rell.base.compiler.base.def.*
import net.postchain.rell.base.compiler.base.lib.C_LibModule
import net.postchain.rell.base.compiler.base.module.*
import net.postchain.rell.base.compiler.base.namespace.C_NsAsm_App
import net.postchain.rell.base.compiler.base.namespace.C_NsAsm_AppAssembler
import net.postchain.rell.base.compiler.base.namespace.C_NsAsm_ModuleAssembler
import net.postchain.rell.base.compiler.base.namespace.C_NsAsm_ReplAssembler
import net.postchain.rell.base.compiler.base.utils.*
import net.postchain.rell.base.model.*
import net.postchain.rell.base.utils.*
import org.apache.commons.collections4.SetUtils
import java.util.concurrent.atomic.AtomicLong

private object C_InternalAppUtils {
    private val AT_EXPR_ID_SEQ = AtomicLong(0)
    private val AT_ENTITY_ID_SEQ = AtomicLong(0)

    fun nextAtExprId(): R_AtExprId {
        val id = AT_EXPR_ID_SEQ.getAndIncrement()
        return R_AtExprId(id)
    }

    fun nextAtEntityId(exprId: R_AtExprId): R_AtEntityId {
        val id = AT_ENTITY_ID_SEQ.getAndIncrement()
        return R_AtEntityId(exprId, id)
    }
}

internal class C_AppContext(
    val msgCtx: C_MessageContext,
    val symCtxProvider: C_SymbolContextProvider,
    val executor: C_CompilerExecutor,
    val repl: Boolean,
    private val oldReplState: C_ReplAppState,
    private val newModuleHeaders: ImmMap<R_ModuleName, C_ModuleHeader>,
    private val extraLibMod: C_LibModule?,
) {
    val globalCtx = msgCtx.globalCtx

    val appUid = C_GlobalContext.nextAppUid()
    private val containerUidGen = C_UidGen { id, _ -> R_ContainerUid(id, appUid) }

    private val defsBuilder = C_AppDefsBuilder(executor)
    val defsAdder: C_AppDefsAdder = defsBuilder

    val sysDefs = oldReplState.sysDefs ?: C_SystemDefs.create(this, appUid, extraLibMod)

    val functionReturnTypeCalculator = C_DeepDefinitionBody.createReturnTypeCalculator()

    val extendableFunctionCompiler = C_ExtendableFunctionCompiler(oldReplState.functionExtensions)

    private val appDefsLate = C_LateInit(C_CompilerPass.APPDEFS, C_AppDefs.EMPTY)

    private val nsAssembler = C_NsAsm_AppAssembler.create(executor, msgCtx, appUid, oldReplState.modules)
    private val modulesBuilder = C_ListBuilder<C_AppModule>()
    private val extraMountTables = C_ListBuilder<C_MountTables>()

    private val externalChainsRoot = R_ExternalChainsRoot()
    private val externalChains = mutableMapOf<String, C_ExternalChain>()

    private val allConstants = C_ListBuilder(oldReplState.constants)
    private val newConstants = C_ListBuilder<C_GlobalConstantDefinition>()

    private val nativeFunctions = mutableMapOf<R_FullName, R_FunctionHeader>()

    private val nsAsmAppLate = C_LateInit(C_CompilerPass.NAMESPACES, C_NsAsm_App.EMPTY)
    private val finishLate = C_LateInit<Finish?>(C_CompilerPass.APPLICATION, null)

    private var finished = false

    init {
        extraMountTables.add(oldReplState.mntTables)

        executor.onPass(C_CompilerPass.NAMESPACES) {
            val asmApp = nsAssembler.assemble()
            nsAsmAppLate.set(asmApp)
        }

        executor.onPass(C_CompilerPass.ABSTRACT) {
            val mods = modulesBuilder.commit().mapToImmList { it.descriptor }
            C_AbstractCompiler.compile(msgCtx, mods)
        }

        executor.onPass(C_CompilerPass.APPDEFS) {
            val appDefs = defsBuilder.build()
            appDefsLate.set(appDefs)
            C_StructGraphUtils.processStructs(appDefs.structs)
        }

        executor.onPass(C_CompilerPass.VALIDATION) {
            val cs = newConstants.commit()
            C_GlobalConstantDefinition.validateConstants(msgCtx, cs)
        }

        executor.onPass(C_CompilerPass.APPLICATION) {
            val rApp = createApp()
            val replState = createNewReplState(rApp)
            finishLate.set(Finish(rApp, replState))
        }
    }

    fun nextContainerUid() = containerUidGen.next("")

    fun nextAtExprId() = C_InternalAppUtils.nextAtExprId()
    fun nextAtEntityId(exprId: R_AtExprId) = C_InternalAppUtils.nextAtEntityId(exprId)

    fun addConstant(
        moduleKey: R_ModuleKey,
        defName: R_DefinitionName,
        maker: (R_GlobalConstantId) -> C_GlobalConstantDefinition
    ): C_GlobalConstantDefinition {
        val id = R_GlobalConstantId(allConstants.size, appUid, moduleKey, defName.appLevelName, defName.qualifiedName)
        val cDef = maker(id)
        allConstants.add(cDef.rDef)
        newConstants.add(cDef)
        return cDef
    }

    fun finish(): Finish? {
        check(!finished)
        finished = true
        executor.checkPass(C_CompilerPass.FINISH)
        return finishLate.get()
    }

    fun createModuleNsAssembler(
        moduleKey: C_ModuleKey,
        sysDefsScope: C_SystemDefsScope,
        exportSysEntities: Boolean,
    ): C_NsAsm_ModuleAssembler {
        return nsAssembler.addModule(moduleKey, sysDefsScope.nsProto, exportSysEntities)
    }

    fun createReplNsAssembler(linkedModule: C_ModuleKey?): C_NsAsm_ReplAssembler {
        return nsAssembler.addRepl(sysDefs.appScope.nsProto, linkedModule, oldReplState.nsAsmState)
    }

    fun addExternalChain(name: String): C_ExternalChain {
        executor.checkPass(C_CompilerPass.DEFINITIONS)
        return externalChains.computeIfAbsent(name) { createExternalChain(name) }
    }

    private fun createExternalChain(name: String): C_ExternalChain {
        val ref = R_ExternalChainRef(externalChainsRoot, name, externalChains.size)

        val blockEntity = C_Utils.createBlockEntity(this, ref)
        val transactionEntity = C_Utils.createTransactionEntity(this, ref, blockEntity)

        val extSysDefs = C_SystemDefs.create(globalCtx, appUid, blockEntity, transactionEntity, immListOf(), extraLibMod)
        return C_ExternalChain(name, ref, extSysDefs)
    }

    fun addModule(module: C_ModuleDescriptor, compiled: C_CompiledModule) {
        executor.checkPass(C_CompilerPass.MODULES)
        val appModule = C_AppModule(module, compiled.rModule, compiled.contents.mntTables)
        modulesBuilder.add(appModule)
    }

    fun addExtraMountTables(mntTables: C_MountTables) {
        executor.checkPass(C_CompilerPass.MODULES)
        extraMountTables.add(mntTables)
    }

    fun addNativeFunction(name: R_FullName, rHeader: R_FunctionHeader) {
        nativeFunctions[name] = rHeader
    }

    private val functionExtTableLazy: C_FunctionExtensionsTable by lazy {
        extendableFunctionCompiler.compileExtensions()
    }

    private fun createApp(): R_App {
        val appDefs = appDefsLate.get()
        val topologicalEntities = calcTopologicalEntities(appDefs.entities)

        val appOperationsMap = routinesToMap(appDefs.operations)
        val appQueriesMap = routinesToMap(sysDefs.common.queries + appDefs.queries)

        val valid = !msgCtx.messages().any { !it.type.ignorable }

        val modules = modulesBuilder.commit()
        val rModules = modules.mapToImmList { it.rModule }

        val newModuleArgs = rModules
            .mapNotNull {
                if (it.moduleArgs == null || it.externalChain != null) null else (it.name to it.moduleArgs)
            }
            .toImmMap()

        val oldSqlDefs = oldReplState.sqlDefs
        val sqlDefs = R_AppSqlDefs(
            entities = oldSqlDefs.entities + appDefs.entities.map { it.entity },
            objects = oldSqlDefs.objects + appDefs.objects,
            topologicalEntities = oldSqlDefs.topologicalEntities + topologicalEntities,
        )

        val rFnExtTable = functionExtTableLazy.toR()

        return R_App(
            valid = valid,
            uid = appUid,
            modules = rModules,
            operations = appOperationsMap,
            queries = appQueriesMap,
            constants = allConstants.commit(),
            moduleArgs = oldReplState.moduleArgs + newModuleArgs,
            functionExtensions = rFnExtTable,
            externalChainsRoot = externalChainsRoot,
            externalChains = externalChains.values.mapToImmList { it.ref },
            nativeFunctions = nativeFunctions.toImmMap(),
            sqlDefs = sqlDefs,
        )
    }

    private fun createNewReplState(app: R_App): C_ReplAppState {
        val mntTables = createAppMounts()

        val asmApp = nsAsmAppLate.get()

        val resModuleHeaders = oldReplState.moduleHeaders.toMutableMap()
        resModuleHeaders.putAllAbsent(newModuleHeaders)

        val modules = modulesBuilder.commit()

        val newPrecompiledModules = modules
                .mapNotNull { module ->
                    val ns = asmApp.modules[module.descriptor.key]
                    if (ns == null) null else {
                        val preModule = C_PrecompiledModule(module.descriptor, ns)
                        module.descriptor.key to preModule
                    }
                }
                .toMap()
                .toImmMap()

        val resModules = oldReplState.modules.toMutableMap()
        resModules.putAllAbsent(newPrecompiledModules)

        return C_ReplAppState(
            asmApp.newReplState,
            resModuleHeaders.toImmMap(),
            resModules.toImmMap(),
            sysDefs,
            app.sqlDefs,
            mntTables,
            allConstants.commit(),
            app.moduleArgs,
            functionExtTableLazy,
        )
    }

    private fun createAppMounts(): C_MountTables {
        val builder = C_MountTablesBuilder(appUid)
        builder.add(sysDefs.common.mntTables)

        for (extChain in externalChains.values) {
            builder.add(extChain.sysDefs.common.mntTables)
        }

        for (module in modulesBuilder.commit()) {
            builder.add(module.mntTables)
        }

        for (mntTables in extraMountTables.commit()) {
            builder.add(mntTables)
        }

        val tables = builder.build()
        return C_MntEntry.processMountConflicts(msgCtx, appUid, tables)
    }

    private fun calcTopologicalEntities(entities: List<C_Entity>): List<R_EntityDefinition> {
        val declaredEntities = entities.map { it.entity }.toImmSet()
        val graph = mutableMapOf<R_EntityDefinition, Collection<R_EntityDefinition>>()

        for (entity in entities) {
            val deps = mutableSetOf<R_EntityDefinition>()
            for (attr in entity.entity.attributes.values) {
                if (attr.type is R_EntityType && attr.type.rEntity in declaredEntities) {
                    deps.add(attr.type.rEntity)
                }
            }
            graph[entity.entity] = deps
        }

        val entityToPos = entities.filter { it.defPos != null }.associate { it.entity to it.defPos!! }

        val cycles = C_GraphUtils.findCycles(graph)
        if (cycles.isNotEmpty()) {
            val cycle = cycles[0]
            val shortStr = cycle.joinToString(",") { it.appLevelName }
            val str = cycle.joinToString { it.appLevelName }
            val entity = cycle[0]
            val pos = entityToPos[entity]
            check(pos != null) { entity.appLevelName }
            throw C_Error.stop(pos, "entity_cycle:$shortStr", "Entity cycle, not allowed: $str")
        }

        val res = C_GraphUtils.topologicalSort(graph)
        return res
    }

    private fun <T: R_MountedRoutineDefinition> routinesToMap(list: List<T>): ImmMap<R_MountName, T> {
        val res = mutableMapOf<R_MountName, T>()
        for (r in list) {
            val name = r.mountName
            if (name !in res) res[name] = r
        }
        return res.toImmMap()
    }

    class Finish(
        val rApp: R_App,
        val replState: C_ReplAppState,
    )

    private class C_AppModule(
        val descriptor: C_ModuleDescriptor,
        val rModule: R_Module,
        val mntTables: C_MountTables,
    )
}

class C_AppDefs(
    val entities: ImmList<C_Entity>,
    val objects: ImmList<R_ObjectDefinition>,
    val structs: ImmList<R_Struct>,
    val operations: ImmList<R_OperationDefinition>,
    val queries: ImmList<R_QueryDefinition>
) {
    companion object { val EMPTY = C_AppDefs(immListOf(), immListOf(), immListOf(), immListOf(), immListOf()) }
}

interface C_AppDefsAdder {
    fun addEntity(entity: C_Entity)
    fun addObject(obj: R_ObjectDefinition)
    fun addStruct(struct: R_Struct)
    fun addOperation(op: R_OperationDefinition)
    fun addQuery(q: R_QueryDefinition)
}

private class C_AppDefsBuilder(executor: C_CompilerExecutor): C_AppDefsAdder {
    private val entities = C_AppDefsTableBuilder<C_Entity, R_EntityDefinition>(executor) { it.entity }
    private val objects = C_AppDefsTableBuilder<R_ObjectDefinition, R_ObjectDefinition>(executor) { it }
    private val structs = C_AppDefsTableBuilder<R_Struct, R_Struct>(executor) { it }
    private val operations = C_AppDefsTableBuilder<R_OperationDefinition, R_OperationDefinition>(executor) { it }
    private val queries = C_AppDefsTableBuilder<R_QueryDefinition, R_QueryDefinition>(executor) { it }
    private var build = false

    override fun addEntity(entity: C_Entity) = add(entities, entity)
    override fun addObject(obj: R_ObjectDefinition) = add(objects, obj)
    override fun addStruct(struct: R_Struct) = add(structs, struct)
    override fun addOperation(op: R_OperationDefinition) = add(operations, op)
    override fun addQuery(q: R_QueryDefinition) = add(queries, q)

    private fun <T: Any, K> add(table: C_AppDefsTableBuilder<T, K>, value: T) {
        check(!build)
        table.add(value)
    }

    fun build(): C_AppDefs {
        check(!build)
        build = true
        return C_AppDefs(
                entities.build(),
                objects.build(),
                structs.build(),
                operations.build(),
                queries.build()
        )
    }
}

private class C_AppDefsTableBuilder<T, K>(
    private val executor: C_CompilerExecutor,
    private val keyGetter: (T) -> K,
) {
    private val keys: MutableSet<K> = SetUtils.newIdentityHashSet()
    private val defs = mutableListOf<T>()
    private var build = false

    fun add(def: T) {
        executor.checkPass(C_CompilerPass.DEFINITIONS)
        val k = keyGetter(def)
        check(keys.add(k)) { "Duplicate def: $def" }
        defs.add(def)
    }

    fun build(): ImmList<T> {
        check(!build)
        build = true
        return defs.toImmList()
    }
}
