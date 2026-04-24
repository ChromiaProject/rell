/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.serialization

import com.google.flatbuffers.FlatBufferBuilder
import net.postchain.rell.base.model.rr.*
import java.util.*

/**
 * Context for serializing an [RR_App] to FlatBuffers.
 *
 * Uses [RR_App]'s pre-built flat definition arrays and index maps.
 */
class SerializerContext(val app: RR_App) {
    val builder = FlatBufferBuilder(4096)

    // Per-module definition indices for building Module tables.
    val moduleEntityIndices = mutableMapOf<RR_Module, List<Int>>()
    val moduleObjectIndices = mutableMapOf<RR_Module, List<Int>>()
    val moduleStructIndices = mutableMapOf<RR_Module, List<Int>>()
    val moduleEnumIndices = mutableMapOf<RR_Module, List<Int>>()
    val moduleOperationIndices = mutableMapOf<RR_Module, List<Int>>()
    val moduleQueryIndices = mutableMapOf<RR_Module, List<Int>>()
    val moduleFunctionIndices = mutableMapOf<RR_Module, List<Int>>()
    val moduleConstantIndices = mutableMapOf<RR_Module, List<Int>>()

    // Identity-based index maps built from the flat arrays.
    private val entityIdentityIndex = IdentityHashMap<RR_EntityDefinition, Int>()
    private val objectIdentityIndex = IdentityHashMap<RR_ObjectDefinition, Int>()
    private val structIdentityIndex = IdentityHashMap<RR_StructDefinition, Int>()
    private val enumIdentityIndex = IdentityHashMap<RR_EnumDefinition, Int>()
    private val operationIdentityIndex = IdentityHashMap<RR_OperationDefinition, Int>()
    private val queryIdentityIndex = IdentityHashMap<RR_QueryDefinition, Int>()
    private val functionIdentityIndex = IdentityHashMap<RR_FunctionDefinition, Int>()
    private val constantIdentityIndex = IdentityHashMap<RR_GlobalConstantDefinition, Int>()

    init {
        // Build identity maps from flat arrays.
        for ((i, e) in app.allEntities.withIndex()) entityIdentityIndex[e] = i
        for ((i, o) in app.allObjects.withIndex()) objectIdentityIndex[o] = i
        for ((i, s) in app.allStructs.withIndex()) structIdentityIndex[s] = i
        for ((i, e) in app.allEnums.withIndex()) enumIdentityIndex[e] = i
        for ((i, o) in app.allOperations.withIndex()) operationIdentityIndex[o] = i
        for ((i, q) in app.allQueries.withIndex()) queryIdentityIndex[q] = i
        for ((i, f) in app.allFunctions.withIndex()) functionIdentityIndex[f] = i
        for ((i, c) in app.allConstants.withIndex()) constantIdentityIndex[c] = i

        // Build per-module indices.
        scanModules()
    }

    private fun scanModules() {
        for (module in app.modules) {
            moduleEntityIndices[module] = module.entities.values.map { resolveEntityIndex(it) }
            moduleObjectIndices[module] = module.objects.values.map { resolveObjectIndex(it) }
            moduleStructIndices[module] = module.structs.values.map { resolveStructIndex(it) }
            moduleEnumIndices[module] = module.enums.values.map { resolveEnumIndex(it) }
            moduleOperationIndices[module] = module.operations.values.map { resolveOperationIndex(it) }
            moduleQueryIndices[module] = module.queries.values.map { resolveQueryIndex(it) }
            moduleFunctionIndices[module] = module.functions.values.map { resolveFunctionIndex(it) }
            moduleConstantIndices[module] = module.constants.values.map { resolveConstantIndex(it) }
        }
    }

    // --- RR_ definition index resolution ---

    fun resolveEntityIndex(def: RR_EntityDefinition): Int =
        checkNotNull(entityIdentityIndex[def]) { "Entity not found in index: ${def.base.appLevelName}" }

    fun resolveStructIndex(def: RR_StructDefinition): Int =
        checkNotNull(structIdentityIndex[def]) { "Struct not found in index: ${def.base.appLevelName}" }

    fun resolveEnumIndex(def: RR_EnumDefinition): Int =
        checkNotNull(enumIdentityIndex[def]) { "Enum not found in index: ${def.base.appLevelName}" }

    fun resolveObjectIndex(def: RR_ObjectDefinition): Int =
        checkNotNull(objectIdentityIndex[def]) { "Object not found in index: ${def.base.appLevelName}" }

    fun resolveOperationIndex(def: RR_OperationDefinition): Int =
        checkNotNull(operationIdentityIndex[def]) { "Operation not found in index: ${def.base.appLevelName}" }

    fun resolveQueryIndex(def: RR_QueryDefinition): Int =
        checkNotNull(queryIdentityIndex[def]) { "Constant not found in index: ${def.base.appLevelName}" }

    fun resolveFunctionIndex(def: RR_FunctionDefinition): Int =
        checkNotNull(functionIdentityIndex[def]) { "Function not found in index: ${def.base.appLevelName}" }

    fun resolveConstantIndex(def: RR_GlobalConstantDefinition): Int =
        checkNotNull(constantIdentityIndex[def]) { "Constant not found in index: ${def.base.appLevelName}" }

    // --- String helpers ---

    fun createString(s: String): Int = builder.createString(s)

    fun createStringVector(strings: List<String>): Int {
        val offsets = strings.map { createString(it) }.toIntArray()
        return builder.createVectorOfTables(offsets)
    }
}
