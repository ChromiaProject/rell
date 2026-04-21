/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.rr

import net.postchain.rell.base.model.*
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.ImmMap
import net.postchain.rell.base.utils.ImmSet
import net.postchain.rell.base.utils.associateByToImmMap

/**
 * Immutable, serializable runtime application model.
 * Contains all definitions (entities, structs, enums, objects, operations, queries, functions)
 * and their compiled bodies as pure data, ready for interpretation or FlatBuffers serialization.
 */
data class RR_App(
    val modules: ImmList<RR_Module>,
    val operations: ImmMap<MountName, RR_OperationDefinition>,
    val queries: ImmMap<MountName, RR_QueryDefinition>,
    val constants: ImmList<RR_GlobalConstantDefinition>,
    val moduleArgs: ImmMap<ModuleName, RR_StructDefinition>,
    val sqlDefs: RR_AppSqlDefs,
    val nativeFunctions: ImmMap<FullName, RR_FunctionHeader>,

    // --- Flat definition arrays (indices match RR_Type.Entity/Struct/etc. defIndex) ---
    val allEntities: ImmList<RR_EntityDefinition>,
    val allObjects: ImmList<RR_ObjectDefinition>,
    val allStructs: ImmList<RR_StructDefinition>,
    val allEnums: ImmList<RR_EnumDefinition>,
    val allOperations: ImmList<RR_OperationDefinition>,
    val allQueries: ImmList<RR_QueryDefinition>,
    val allFunctions: ImmList<RR_FunctionDefinition>,
    val allConstants: ImmList<RR_GlobalConstantDefinition>,

    // --- Definition ID → flat index lookups ---
    val entityDefIdIndex: ImmMap<DefinitionId, Int>,
    val structNameIndex: ImmMap<String, Int>,
    val enumDefIdIndex: ImmMap<DefinitionId, Int>,
    val objectDefIdIndex: ImmMap<DefinitionId, Int>,
    val functionDefIdIndex: ImmMap<DefinitionId, Int>,
    val operationDefIdIndex: ImmMap<DefinitionId, Int>,
    val constantDefIdIndex: ImmMap<DefinitionId, Int>,

    val externalChains: ImmList<RR_ExternalChainRef>,

    /**
     * Extendable function dispatch table. Indices into this list match
     * `RR_FunctionCallTarget.Extendable.extendableUidId`.
     */
    val functionExtensions: ImmList<RR_FunctionExtensions>,
) {
    val moduleMap: ImmMap<ModuleName, RR_Module> by lazy {
        modules.associateByToImmMap { it.name }
    }
}

data class RR_AppSqlDefs(
    val entities: ImmList<RR_EntityDefinition>,
    val objects: ImmList<RR_ObjectDefinition>,
    val topologicalEntities: ImmList<RR_EntityDefinition>,
)

data class RR_ExternalChainRef(val name: String, val index: Int)

/**
 * Set of extensions registered for a single extendable function.
 * The position in `RR_App.functionExtensions` matches `extendableUidId`.
 */
data class RR_FunctionExtensions(
    val uid: Int,
    val extensions: ImmList<RR_FunctionBase>,
)

data class RR_Module(
    val name: ModuleName,
    val directory: Boolean,
    val abstract: Boolean,
    val external: Boolean,
    val externalChain: String?,
    val test: Boolean,
    val disabled: Boolean,
    val selected: Boolean,
    val entities: ImmMap<String, RR_EntityDefinition>,
    val objects: ImmMap<String, RR_ObjectDefinition>,
    val structs: ImmMap<String, RR_StructDefinition>,
    val enums: ImmMap<String, RR_EnumDefinition>,
    val operations: ImmMap<String, RR_OperationDefinition>,
    val queries: ImmMap<String, RR_QueryDefinition>,
    val functions: ImmMap<String, RR_FunctionDefinition>,
    val constants: ImmMap<String, RR_GlobalConstantDefinition>,
    val imports: ImmSet<ModuleName>,
    val moduleArgs: RR_StructDefinition?,
) {
    val key = ModuleKey(name, externalChain)

    override fun toString() = name.toString()
}
