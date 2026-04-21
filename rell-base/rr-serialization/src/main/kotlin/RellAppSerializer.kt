/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.serialization

import net.postchain.rell.base.model.rr.RR_App
import rell.ir.App
import rell.ir.ExternalChainRef
import rell.ir.Module

/**
 * Serializes an [RR_App] to a FlatBuffers byte array.
 *
 * The RR_ model is fully resolved — no `C_LateGetter`, no lazy fields, no error sentinels.
 * Serialization is a straightforward tree walk with no special cases.
 */
fun serializeRellApp(app: RR_App): ByteArray {
    val ctx = SerializerContext(app)
    val builder = ctx.builder

    // 1. Serialize all definitions from flat arrays (bottom-up).
    val entityOffsets = app.allEntities.map { ctx.serializeEntityDefinition(it) }.toIntArray()
    val objectOffsets = app.allObjects.map { ctx.serializeObjectDefinition(it) }.toIntArray()
    val structOffsets = app.allStructs.map { ctx.serializeStructDefinition(it) }.toIntArray()
    val enumOffsets = app.allEnums.map { ctx.serializeEnumDefinition(it) }.toIntArray()
    val operationOffsets = app.allOperations.map { ctx.serializeOperationDefinition(it) }.toIntArray()
    val queryOffsets = app.allQueries.map { ctx.serializeQueryDefinition(it) }.toIntArray()
    val functionOffsets = app.allFunctions.map { ctx.serializeFunctionDefinition(it) }.toIntArray()
    val constantOffsets = app.allConstants.map { ctx.serializeGlobalConstantDefinition(it) }.toIntArray()

    // 2. Serialize modules.
    val moduleOffsets = app.modules.map { module ->
        val name = ctx.serializeModuleName(module.name)

        fun List<Int>?.toUIntArr() = (this ?: emptyList()).map { it.toUInt() }.toUIntArray()

        val entityIdxVec = Module.createEntityIndicesVector(builder, ctx.moduleEntityIndices[module].toUIntArr())
        val objectIdxVec = Module.createObjectIndicesVector(builder, ctx.moduleObjectIndices[module].toUIntArr())
        val structIdxVec = Module.createStructIndicesVector(builder, ctx.moduleStructIndices[module].toUIntArr())
        val enumIdxVec = Module.createEnumIndicesVector(builder, ctx.moduleEnumIndices[module].toUIntArr())
        val opIdxVec = Module.createOperationIndicesVector(builder, ctx.moduleOperationIndices[module].toUIntArr())
        val queryIdxVec = Module.createQueryIndicesVector(builder, ctx.moduleQueryIndices[module].toUIntArr())
        val fnIdxVec = Module.createFunctionIndicesVector(builder, ctx.moduleFunctionIndices[module].toUIntArr())
        val constIdxVec = Module.createConstantIndicesVector(builder, ctx.moduleConstantIndices[module].toUIntArr())

        val imports = module.imports.map { ctx.serializeModuleName(it) }.toIntArray()
        val importsVec = if (imports.isNotEmpty()) builder.createVectorOfTables(imports) else 0

        val moduleArgsIdx = module.moduleArgs?.let { ctx.resolveStructIndex(it) } ?: -1

        val externalChain = module.externalChain?.let { ctx.createString(it) }

        Module.startModule(builder)
        Module.addName(builder, name)
        Module.addDirectory(builder, module.directory)
        Module.addAbstract(builder, module.abstract)
        Module.addExternal(builder, module.external)
        if (externalChain != null) Module.addExternalChain(builder, externalChain)
        Module.addTest(builder, module.test)
        Module.addDisabled(builder, module.disabled)
        Module.addSelected(builder, module.selected)
        Module.addEntityIndices(builder, entityIdxVec)
        Module.addObjectIndices(builder, objectIdxVec)
        Module.addStructIndices(builder, structIdxVec)
        Module.addEnumIndices(builder, enumIdxVec)
        Module.addOperationIndices(builder, opIdxVec)
        Module.addQueryIndices(builder, queryIdxVec)
        Module.addFunctionIndices(builder, fnIdxVec)
        Module.addConstantIndices(builder, constIdxVec)
        if (importsVec != 0) Module.addImports(builder, importsVec)
        Module.addModuleArgsStructIndex(builder, moduleArgsIdx)
        Module.endModule(builder)
    }.toIntArray()

    // 2b. Serialize system queries (in queries mount-name map but not in allQueries).
    // Round-trip invariant: every operation in app.operations must appear in app.allOperations,
    // because the deserializer reconstructs operationsMap from allOperations only. Any operation
    // not in allOperations would silently disappear after a round-trip.
    val allOperationSet = app.allOperations.toSet()
    require(app.operations.values.all { it in allOperationSet }) {
        val orphans = app.operations.values
            .filter { it !in allOperationSet }
            .map { it.base.appLevelName }
        "RR_App.operations contains operations missing from allOperations: $orphans"
    }
    val allQuerySet = app.allQueries.toSet()
    val sysQueryOffsets = app.queries.values
        .filter { it !in allQuerySet }
        .map { ctx.serializeQueryDefinition(it) }
        .toIntArray()

    // 2c. Serialize function extensions.
    val fnExtOffsets = app.functionExtensions.map { ext ->
        val bodies = ext.extensions.map { ctx.serializeFunctionBody(it) }.toIntArray()
        val bodiesVec = builder.createVectorOfTables(bodies)
        rell.ir.FunctionExtensions.createFunctionExtensions(builder, ext.uid, bodiesVec)
    }.toIntArray()

    // 3. Serialize external chains.
    val externalChainOffsets = app.externalChains.map { chain ->
        val chainName = ctx.createString(chain.name)
        ExternalChainRef.startExternalChainRef(builder)
        ExternalChainRef.addName(builder, chainName)
        ExternalChainRef.addIndex(builder, chain.index.toUInt())
        ExternalChainRef.endExternalChainRef(builder)
    }.toIntArray()

    // 4. Build definition vectors.
    val entitiesVec = App.createEntitiesVector(builder, entityOffsets)
    val objectsVec = App.createObjectsVector(builder, objectOffsets)
    val structsVec = App.createStructsVector(builder, structOffsets)
    val enumsVec = App.createEnumsVector(builder, enumOffsets)
    val operationsVec = App.createOperationsVector(builder, operationOffsets)
    val queriesVec = App.createQueriesVector(builder, queryOffsets)
    val functionsVec = App.createFunctionsVector(builder, functionOffsets)
    val constantsVec = App.createConstantsVector(builder, constantOffsets)
    val modulesVec = App.createModulesVector(builder, moduleOffsets)
    val externalChainsVec = if (externalChainOffsets.isNotEmpty()) {
        App.createExternalChainsVector(builder, externalChainOffsets)
    } else 0
    val sysQueriesVec = if (sysQueryOffsets.isNotEmpty()) {
        App.createSysQueriesVector(builder, sysQueryOffsets)
    } else 0
    val fnExtVec = if (fnExtOffsets.isNotEmpty()) {
        App.createFunctionExtensionsVector(builder, fnExtOffsets)
    } else 0

    // 5. Build root App table.
    App.startApp(builder)
    App.addVersion(builder, 1u)
    App.addEntities(builder, entitiesVec)
    App.addObjects(builder, objectsVec)
    App.addStructs(builder, structsVec)
    App.addEnums(builder, enumsVec)
    App.addOperations(builder, operationsVec)
    App.addQueries(builder, queriesVec)
    App.addFunctions(builder, functionsVec)
    App.addConstants(builder, constantsVec)
    App.addModules(builder, modulesVec)
    if (externalChainsVec != 0) App.addExternalChains(builder, externalChainsVec)
    if (sysQueriesVec != 0) App.addSysQueries(builder, sysQueriesVec)
    if (fnExtVec != 0) App.addFunctionExtensions(builder, fnExtVec)
    val appOffset = App.endApp(builder)

    App.finishAppBuffer(builder, appOffset)
    return builder.sizedByteArray()
}
