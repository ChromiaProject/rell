/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.serialization

import net.postchain.rell.base.model.FullName
import net.postchain.rell.base.model.rr.*
import net.postchain.rell.base.utils.*
import rell.ir.App
import java.nio.ByteBuffer

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

/**
 * Deserializes a FlatBuffers byte array back into an [RR_App].
 *
 * This is the reverse of [serializeRellApp]. Fields not serialized by the serializer
 * receive sensible defaults.
 */
fun deserializeRellApp(bytes: ByteArray): RR_App = safeDeserialize {
    // Bound the top-level input size: flatbuffers-java has no Verifier, so without
    // a cap a crafted multi-gigabyte buffer would drive allocation bombs inside
    // getRootAsApp's vtable walk.
    if (bytes.size > DeserLimits.MAX_BUFFER_SIZE) {
        throw RRDeserializationException(
            "RR_App buffer size ${bytes.size} exceeds MAX_BUFFER_SIZE=${DeserLimits.MAX_BUFFER_SIZE}",
        )
    }

    val fb = App.getRootAsApp(ByteBuffer.wrap(bytes))

    // 0. Verify the on-wire schema hash matches this module's build-time hash.
    val wireHashLen = checkedByteArrayLength(fb.schemaHashLength, "App.schema_hash")
    val wireHash = ByteArray(wireHashLen) { fb.schemaHash(it).toByte() }
    if (!wireHash.contentEquals(FBS_SCHEMA_HASH)) {
        throw RRDeserializationException(
            "RR_App schema hash mismatch: " +
                    "buffer hash=${wireHash.toHex()}, " +
                    "expected=${FBS_SCHEMA_HASH.toHex()}. " +
                    "The serialized buffer was produced by a different version of the .fbs schema files.",
        )
    }

    // 1. Deserialize flat definition arrays, under a published `objectsCount` bound
    //    so nested expression deserializers (DbAtExpr.object_def_index,
    //    ObjectValueExpr.object_def_index) can reject out-of-range indices without
    //    threading the object list through every signature. Entity/struct attribute
    //    default exprs can reference objects, so the bound wraps the whole definition
    //    pass - not just operations/queries/functions/constants.
    //
    //    Each per-array length is capped via checkedVectorLength so a buffer claiming
    //    `xxxLength = Int.MAX_VALUE` hits RRDeserializationException before we allocate.
    val objectsCount = checkedVectorLength(fb.objectsLength, "App.objects")
    return@deserializeRellApp withObjectsCount(objectsCount) {
        val allEntities = (0 until checkedVectorLength(fb.entitiesLength, "App.entities"))
            .mapToImmList { deserializeEntityDefinition(fb.entities(it)) }
        val allStructs = (0 until checkedVectorLength(fb.structsLength, "App.structs"))
            .mapToImmList { deserializeStructDefinition(fb.structs(it)) }
        val allEnums = (0 until checkedVectorLength(fb.enumsLength, "App.enums"))
            .mapToImmList { deserializeEnumDefinition(fb.enums(it)) }
        val allObjects = (0 until objectsCount)
            .mapToImmList { deserializeObjectDefinition(fb.objects(it), allEntities) }
        val allOperations = (0 until checkedVectorLength(fb.operationsLength, "App.operations"))
            .mapToImmList { deserializeOperationDefinition(fb.operations(it)) }
        val allQueries = (0 until checkedVectorLength(fb.queriesLength, "App.queries"))
            .mapToImmList { deserializeQueryDefinition(fb.queries(it)) }
        val allFunctions = (0 until checkedVectorLength(fb.functionsLength, "App.functions"))
            .mapToImmList { deserializeFunctionDefinition(fb.functions(it)) }
        val allConstants = (0 until checkedVectorLength(fb.constantsLength, "App.constants"))
            .mapToImmList { deserializeGlobalConstantDefinition(fb.constants(it)) }

        // 2. Patch entity types to reference their own defIndex.
        val patchedEntities = allEntities.mapIndexed { i, e ->
            e.copy(type = RR_Type.Entity(i))
        }.toImmList()

        // Also patch object entities to use patched entities.
        val patchedObjects = allObjects.mapIndexed { _, o ->
            val entityIdx = patchedEntities.indexOfFirst { it.base.defName == o.rEntity.base.defName }
            if (entityIdx >= 0) o.copy(rEntity = patchedEntities[entityIdx]) else o
        }.toImmList()

        // 3. Deserialize external chains.
        val externalChains = (0 until checkedVectorLength(fb.externalChainsLength, "App.external_chains"))
            .mapToImmList { i ->
                val ec = fb.externalChains(i)!!
                RR_ExternalChainRef(name = ec.name, index = ec.index.toInt())
            }

        // 4. Deserialize modules (reconstruct from per-module index vectors).
        val modules = (0 until checkedVectorLength(fb.modulesLength, "App.modules")).mapToImmList { i ->
            val m = fb.modules(i)
            val moduleName = deserializeModuleName(m.name)

            fun <T> resolveIndexed(
                count: Int,
                accessor: (Int) -> UInt,
                allDefs: List<T>,
                field: String,
            ): List<T> {
                val n = checkedVectorLength(count, field)
                return (0 until n).map { j -> allDefs[checkedUIntAsIndex(accessor(j), allDefs.size, field)] }
            }

            val moduleEntities =
                resolveIndexed(m.entityIndicesLength, { m.entityIndices(it) }, patchedEntities, "Module.entity_indices")

            val moduleObjects =
                resolveIndexed(m.objectIndicesLength, { m.objectIndices(it) }, patchedObjects, "Module.object_indices")

            val moduleStructs =
                resolveIndexed(m.structIndicesLength, { m.structIndices(it) }, allStructs, "Module.struct_indices")

            val moduleEnums =
                resolveIndexed(m.enumIndicesLength, { m.enumIndices(it) }, allEnums, "Module.enum_indices")

            val moduleOperations = resolveIndexed(
                m.operationIndicesLength,
                { m.operationIndices(it) },
                allOperations,
                "Module.operation_indices",
            )

            val moduleQueries =
                resolveIndexed(m.queryIndicesLength, { m.queryIndices(it) }, allQueries, "Module.query_indices")

            val moduleFunctions =
                resolveIndexed(
                    m.functionIndicesLength,
                    { m.functionIndices(it) },
                    allFunctions,
                    "Module.function_indices",
                )

            val moduleConstants =
                resolveIndexed(
                    m.constantIndicesLength,
                    { m.constantIndices(it) },
                    allConstants,
                    "Module.constant_indices",
                )

            val imports = (0 until checkedVectorLength(m.importsLength, "Module.imports"))
                .map { j -> deserializeModuleName(m.imports(j)) }.toImmSet()

            val moduleArgs = m.moduleArgsStructIndex?.let {
                checkedIndex(it, allStructs.size, "Module.module_args_struct_index")
                allStructs[it]
            }

            RR_Module(
                name = moduleName,
                directory = m.directory,
                abstract = m.`abstract`,
                external = m.`external`,
                externalChain = m.externalChain,
                test = m.test,
                disabled = m.disabled,
                selected = m.selected,
                entities = moduleEntities.associateBy { it.base.defName.qualifiedName }.toImmMap(),
                objects = moduleObjects.associateBy { it.base.defName.qualifiedName }.toImmMap(),
                structs = moduleStructs.associateBy { it.base.defName.qualifiedName }.toImmMap(),
                enums = moduleEnums.associateBy { it.base.defName.qualifiedName }.toImmMap(),
                operations = moduleOperations.associateBy { it.base.defName.qualifiedName }.toImmMap(),
                queries = moduleQueries.associateBy { it.base.defName.qualifiedName }.toImmMap(),
                functions = moduleFunctions.associateBy { it.base.defName.qualifiedName }.toImmMap(),
                constants = moduleConstants.associateBy { it.base.defName.qualifiedName }.toImmMap(),
                imports = imports,
                moduleArgs = moduleArgs,
            )
        }

        // 5. Build mount-name maps for operations and queries, including system queries.
        //    associateByFailOnDup: two operations claiming the same mount name is a
        //    consensus-divergence primitive - `associateBy` would silently keep the last one,
        //    with the "winner" depending on insertion order. Reject outright instead.
        val operationsMap = allOperations.associateByFailOnDup("App.operations mount name") { it.mountName }.toImmMap()
        val sysQueries = (0 until checkedVectorLength(fb.sysQueriesLength, "App.sys_queries"))
            .map { deserializeQueryDefinition(fb.sysQueries(it)!!) }
        val queriesMap = (allQueries + sysQueries)
            .associateByFailOnDup("App.queries mount name") { it.mountName }.toImmMap()

        // 6. Build module args map.
        val moduleArgsMap = modules.mapNotNull { m -> m.moduleArgs?.let { m.name to it } }.toMap().toImmMap()

        // 7. Build definition ID index maps.
        val entityDefIdIndex = patchedEntities.withIndex()
            .associate { (i, e) -> e.base.defId to i }.toImmMap()
        val structNameIndex = allStructs.withIndex()
            .associate { (i, s) -> s.struct.name to i }.toImmMap()
        val enumDefIdIndex = allEnums.withIndex()
            .associate { (i, e) -> e.base.defId to i }.toImmMap()
        val objectDefIdIndex = patchedObjects.withIndex()
            .associate { (i, o) -> o.base.defId to i }.toImmMap()
        val functionDefIdIndex = allFunctions.withIndex()
            .associate { (i, f) -> f.base.defId to i }.toImmMap()
        val operationDefIdIndex = allOperations.withIndex()
            .associate { (i, o) -> o.base.defId to i }.toImmMap()
        val constantDefIdIndex = allConstants.withIndex()
            .associate { (i, c) -> c.base.defId to i }.toImmMap()

        // 8. Build SQL defs.
        val sqlEntities = patchedEntities.filter { !it.flags.isObject }.toImmList()
        val topologicalEntities = topologicalSortEntities(sqlEntities)
        val sqlDefs = RR_AppSqlDefs(
            entities = sqlEntities,
            objects = patchedObjects,
            topologicalEntities = topologicalEntities,
        )

        RR_App(
            modules = modules,
            operations = operationsMap,
            queries = queriesMap,
            constants = allConstants,
            moduleArgs = moduleArgsMap,
            sqlDefs = sqlDefs,
            nativeFunctions = (0 until checkedVectorLength(fb.nativeFunctionsLength, "App.native_functions"))
                .associateToImmMap { i ->
                    val entry = fb.nativeFunctions(i)!!
                    val name = FullName.of(entry.name)
                    val params = (0 until checkedVectorLength(entry.paramsLength, "NativeFunctionEntry.params"))
                        .mapToImmList { j -> deserializeFunctionParam(entry.params(j)) }
                    name to RR_FunctionHeader(deserializeType(entry.type), params)
                },
            allEntities = patchedEntities,
            allObjects = patchedObjects,
            allStructs = allStructs,
            allEnums = allEnums,
            allOperations = allOperations,
            allQueries = allQueries,
            allFunctions = allFunctions,
            allConstants = allConstants,
            entityDefIdIndex = entityDefIdIndex,
            structNameIndex = structNameIndex,
            enumDefIdIndex = enumDefIdIndex,
            objectDefIdIndex = objectDefIdIndex,
            functionDefIdIndex = functionDefIdIndex,
            operationDefIdIndex = operationDefIdIndex,
            constantDefIdIndex = constantDefIdIndex,
            externalChains = externalChains,
            functionExtensions = (0 until checkedVectorLength(fb.functionExtensionsLength, "App.function_extensions"))
                .mapToImmList { i ->
                    val ext = fb.functionExtensions(i)!!
                    val bodies = (0 until checkedVectorLength(ext.extensionsLength, "FunctionExtensions.extensions"))
                        .mapToImmList { j ->
                            val extFb = ext.extensions(j)
                            val defName = extFb.defName?.let { deserializeDefinitionName(it) }
                                ?: throw RRDeserializationException(
                                    "RR_FunctionExtensions[$i].extensions[$j]: missing defName in serialized data",
                                )
                            deserializeFunctionBody(extFb, defName)
                        }
                    RR_FunctionExtensions(ext.uid, bodies)
                },
        )
    }
}

/**
 * Topologically sorts entities so that referenced entities (via FK attributes) come before referencing ones.
 * This ensures SQL CREATE TABLE statements execute in the correct order.
 */
private fun topologicalSortEntities(entities: ImmList<RR_EntityDefinition>): ImmList<RR_EntityDefinition> {
    val entitySet = entities.toSet()
    val entityByIndex = entities.associateBy { (it.type as? RR_Type.Entity)?.defIndex }

    // Build adjacency: entity -> set of entities it depends on (via FK attributes).
    val deps = entities.associateWith { entity ->
        entity.attributes.values.mapNotNull { attr ->
            val depIdx = (attr.type as? RR_Type.Entity)?.defIndex
            depIdx?.let { entityByIndex[it] }?.takeIf { it in entitySet }
        }.toSet()
    }

    // Build reverse adjacency: entity -> set of entities that depend on it.
    val dependents = mutableMapOf<RR_EntityDefinition, MutableSet<RR_EntityDefinition>>()
    val inDegree = entities.associateWithTo(mutableMapOf()) { 0 }
    for ((entity, entityDeps) in deps) {
        inDegree[entity] = entityDeps.size
        for (dep in entityDeps) {
            dependents.getOrPut(dep) { mutableSetOf() }.add(entity)
        }
    }

    // Kahn's algorithm: start with entities that have no dependencies.
    val queue = ArrayDeque(entities.filter { inDegree[it] == 0 })
    val result = mutableListOf<RR_EntityDefinition>()

    while (queue.isNotEmpty()) {
        val entity = queue.removeFirst()
        result.add(entity)
        for (dependent in dependents[entity] ?: emptySet()) {
            val newDeg = (inDegree[dependent] ?: 1) - 1
            inDegree[dependent] = newDeg
            if (newDeg == 0) queue.add(dependent)
        }
    }

    if (result.size < entities.size) {
        val cyclic = entities.filter { it !in result }.map { it.base.appLevelName }
        error("entity FK cycle in deserialized RR_App: $cyclic")
    }

    return result.toImmList()
}
