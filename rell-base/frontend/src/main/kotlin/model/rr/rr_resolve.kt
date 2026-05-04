/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.rr

import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.stmt.R_BlockStatement
import net.postchain.rell.base.model.stmt.R_GuardStatement
import net.postchain.rell.base.model.stmt.R_Statement
import net.postchain.rell.base.utils.*

// Extension functions: R_ frame → RR_ frame conversions are in r_frame.kt

/**
 * Walks the R_ tree after all compiler passes complete, calls `.get()` on every `C_LateGetter`,
 * forces all lazy fields, drops compiler-only fields, and produces the fully resolved RR_ tree.
 *
 * This is the **only place** that calls `.get()` on `C_LateGetter` or accesses lazy `R_Type` fields.
 * After resolution, no runtime code should ever touch `C_*` classes.
 */
fun resolve(rApp: R_App, resolverRuntime: RR_ResolverRuntime): RR_App = RR_Resolver(rApp, resolverRuntime).resolveApp()

/**
 * Resolves both the app and a list of additional REPL statements in one pass,
 * so that the same resolver context (type maps, definition indices) is shared.
 * REPL statements are resolved before the flat definition arrays are finalized,
 * ensuring dynamically discovered definitions (e.g. REPL-defined functions) are included.
 */
fun resolveWithReplStatements(
    rApp: R_App,
    resolverRuntime: RR_ResolverRuntime,
    replStmts: List<R_Statement>,
    replFrame: R_CallFrame,
): Pair<RR_App, RR_ReplCode> {
    val resolver = RR_Resolver(rApp, resolverRuntime)
    val rrApp = resolver.resolveApp(replStmts)
    val rrFrame = replFrame.toRR()
    return rrApp to RR_ReplCode(rrFrame, resolver.lastResolvedReplStmts)
}

/** Resolved REPL code: frame descriptor + resolved statements. */
class RR_ReplCode(val frame: RR_FrameDescriptor, val stmts: ImmList<RR_Statement>)

private class RR_Resolver(private val rApp: R_App, private val resolverRuntime: RR_ResolverRuntime) {
    /** Resolved REPL statements, populated by [resolveApp] when replStmts is provided. */
    var lastResolvedReplStmts: ImmList<RR_Statement> = immListOf()
        private set

    // --- Definition index maps (R_ object → flat array index) ---
    private val entityIndexMap = LinkedHashMap<R_EntityDefinition, Int>()
    private val structIndexMap = LinkedHashMap<R_Struct, Int>()
    private val enumIndexMap = LinkedHashMap<R_EnumDefinition, Int>()
    private val objectIndexMap = LinkedHashMap<R_ObjectDefinition, Int>()
    private val operationIndexMap = LinkedHashMap<R_OperationDefinition, Int>()
    private val functionIndexMap = LinkedHashMap<R_FunctionDefinition, Int>()
    private val queryIndexMap = LinkedHashMap<R_QueryDefinition, Int>()
    private val constantIndexMap = LinkedHashMap<R_GlobalConstantDefinition, Int>()

    // --- Pending queues for the fixed-point resolution loop ---
    // Entries are appended in index order. The fixed-point loop drains them in O(total)
    // instead of re-scanning the entire index maps each iteration.
    private val pendingEntities = ArrayDeque<R_EntityDefinition>()
    private val pendingObjects = ArrayDeque<R_ObjectDefinition>()
    private val pendingStructs = ArrayDeque<R_Struct>()
    private val pendingEnums = ArrayDeque<R_EnumDefinition>()
    private val pendingOperations = ArrayDeque<R_OperationDefinition>()
    private val pendingFunctions = ArrayDeque<R_FunctionDefinition>()
    private val pendingQueries = ArrayDeque<R_QueryDefinition>()
    private val pendingConstants = ArrayDeque<R_GlobalConstantDefinition>()

    // --- Reverse map: R_FunctionBase → function index (for abstract function override resolution) ---
    private val fnBaseToIndexMap = LinkedHashMap<R_FunctionBase, Int>()

    // --- Caches ---
    private val typeCache = LinkedHashMap<R_Type, RR_Type>()
    private val visitedTypes = mutableSetOf<RR_Type>()
    private val structDefCache = LinkedHashMap<R_StructDefinition, RR_StructDefinition>()
    private val entityDefCache = LinkedHashMap<R_EntityDefinition, RR_EntityDefinition>()
    private val objectDefCache = LinkedHashMap<R_ObjectDefinition, RR_ObjectDefinition>()

    init {
        // Pre-scan all modules to build flat definition arrays with indices.
        // Register system entities (block, transaction) from sqlDefs first.
        for (entity in rApp.sqlDefs.entities) {
            if (entityIndexMap.putIfAbsent(entity, entityIndexMap.size) == null) {
                pendingEntities.add(entity)
            }
        }

        for (module in rApp.modules) {
            for (entity in module.entities.values) {
                if (entityIndexMap.putIfAbsent(entity, entityIndexMap.size) == null) {
                    pendingEntities.add(entity)
                }
            }
            for (obj in module.objects.values) {
                // Register the backing entity of the object if not already indexed.
                if (entityIndexMap.putIfAbsent(obj.rEntity, entityIndexMap.size) == null) {
                    pendingEntities.add(obj.rEntity)
                }
                if (objectIndexMap.putIfAbsent(obj, objectIndexMap.size) == null) {
                    pendingObjects.add(obj)
                }
            }
            for (struct in module.structs.values) {
                val idx = structIndexMap.size
                if (structIndexMap.putIfAbsent(struct.struct, idx) == null) {
                    struct.struct.rrDefIndex = idx
                }
            }
            for (enum in module.enums.values) {
                enumIndexMap.putIfAbsent(enum, enumIndexMap.size)
            }
            for (op in module.operations.values) {
                operationIndexMap.putIfAbsent(op, operationIndexMap.size)
            }
            for (fn in module.functions.values) {
                val idx = functionIndexMap.size
                if (functionIndexMap.putIfAbsent(fn, idx) == null) {
                    fnBaseToIndexMap[fn.fnBase] = idx
                }
            }
            for (q in module.queries.values) {
                queryIndexMap.putIfAbsent(q, queryIndexMap.size)
            }
            for (c in module.constants.values) {
                constantIndexMap.putIfAbsent(c, constantIndexMap.size)
            }
        }

        // Also, pre-register constants from rApp.constants (carried over from previous REPL states).
        // In REPL, precompiled modules may not be in rApp.modules, but their constants are in rApp.constants.
        // Module constants were already inserted above (without enqueuing) — only enqueue genuinely new ones,
        // which the per-module loop in resolveApp will not pre-populate into allConstantsList.
        for (c in rApp.constants) {
            if (constantIndexMap.putIfAbsent(c, constantIndexMap.size) == null) {
                pendingConstants.add(c)
            }
        }
    }

    // --- IR resolver (expression/statement conversion) ---

    private val resolverContext = object: RR_ResolverContext {
        override fun resolveType(rType: R_Type) = this@RR_Resolver.resolveType(rType)
        override fun resolveEntityIndex(def: R_EntityDefinition) = registerEntity(def)
        override fun resolveObjectIndex(def: R_ObjectDefinition) = registerObject(def)
        override fun resolveStructIndexByRStruct(struct: R_Struct) = registerStruct(struct)
        override fun resolveFunctionIndex(def: R_FunctionDefinition) = registerFunction(def)
        override fun resolveFunctionIndexByFnBase(fnBase: R_FunctionBase) = fnBaseToIndexMap[fnBase]
        override fun resolveQueryIndex(def: R_QueryDefinition) = registerQuery(def)
        override fun resolveOperationIndex(def: R_OperationDefinition) = registerOperation(def)
        override fun resolveConstantIndex(def: R_GlobalConstantDefinition) = registerConstant(def)
        override fun resolveEnumIndex(def: R_EnumDefinition) = registerEnum(def)

        override fun resolveFnBase(fnBase: R_FunctionBase) =
            this@RR_Resolver.resolveFnBase(fnBase, fnBase.defName)
    }

    private val irResolver = RR_IrResolver(resolverContext, resolverRuntime)

    fun resolveApp(
        replStmts: List<R_Statement>? = null,
    ): RR_App {
        // Force type info for all types referenced in the app.
        collectTypes()

        val modules = rApp.modules.mapToImmList { resolveModule(it) }
        val operations = rApp.operations.mapValuesToImmMap { resolveOperation(it.value) }
        val queries = rApp.queries.mapValuesToImmMap { resolveQuery(it.value) }
        val constants = rApp.constants.mapToImmList { resolveConstant(it) }
        val moduleArgs = rApp.moduleArgs.mapValuesToImmMap { resolveStructDef(it.value) }

        // Resolve REPL statements before building flat arrays, so that any dynamically-
        // registered definitions (e.g. REPL-defined functions) are included in the index maps.
        if (replStmts != null) {
            lastResolvedReplStmts = replStmts.mapToImmList { irResolver.resolveStmt(it) }
        }

        val nativeFunctions = rApp.nativeFunctions.mapValuesToImmMap { (_, header) ->
            RR_FunctionHeader(
                type = resolveType(header.type),
                params = header.params.mapToImmList { resolveParam(it) },
            )
        }

        // Build flat definition arrays from resolved modules.
        // Order matches the pre-scan index maps (entities, objects, structs, etc.).
        val allEntitiesList = mutableListOf<RR_EntityDefinition>()
        val allObjectsList = mutableListOf<RR_ObjectDefinition>()
        val allStructsList = mutableListOf<RR_StructDefinition>()
        val allEnumsList = mutableListOf<RR_EnumDefinition>()
        val rEnumToRREnum = mutableMapOf<R_EnumDefinition, RR_EnumDefinition>()
        val allOperationsList = mutableListOf<RR_OperationDefinition>()
        val allQueriesList = mutableListOf<RR_QueryDefinition>()
        val allFunctionsList = mutableListOf<RR_FunctionDefinition>()
        val allConstantsList = mutableListOf<RR_GlobalConstantDefinition>()
        // Tracks struct names that have already been added via a module struct, to avoid
        // duplicate adds for the same name (e.g. moduleArgs struct that is also a regular struct).
        val seenStructNames = mutableSetOf<String>()

        // Note: entities and objects are populated below via the index-map-driven recursive loop,
        // which is the canonical source of truth (insertion order in entityIndexMap == flat-array index).
        // Adding them in the per-module loop here would misalign indices when sqlDefs.entities carries
        // over from a previous REPL turn (the carry-over goes into entityIndexMap before the module loop).
        for (module in modules) {
            for (s in module.structs.values) {
                seenStructNames.add(s.struct.name)
                allStructsList.add(s)
            }
            for (e in module.enums.values) allEnumsList.add(e)
            for (op in module.operations.values) allOperationsList.add(op)
            for (q in module.queries.values) allQueriesList.add(q)
            for (f in module.functions.values) allFunctionsList.add(f)
            for (c in module.constants.values) allConstantsList.add(c)
            // Register module args struct if present.
            module.moduleArgs?.let { s ->
                if (seenStructNames.add(s.struct.name)) {
                    allStructsList.add(s)
                }
            }
        }

        // Drain pending queues into the flat lists. See [drainPendingQueues] for details.
        drainPendingQueues(
            allFunctionsList, allQueriesList, allOperationsList, allConstantsList,
            allStructsList, allEntitiesList, allEnumsList, allObjectsList,
        )

        // Ensure all sqlDefs entities/objects are in the index maps (REPL may carry over old state).
        // Use the registration helpers so any newly-discovered entries land in the pending queues,
        // then re-drain to materialize them in the flat lists.
        for (entity in rApp.sqlDefs.entities) registerEntity(entity)
        for (obj in rApp.sqlDefs.objects) {
            registerEntity(obj.rEntity)
            registerObject(obj)
        }
        drainPendingQueues(
            allFunctionsList, allQueriesList, allOperationsList, allConstantsList,
            allStructsList, allEntitiesList, allEnumsList, allObjectsList,
        )

        // Build R_EnumDefinition → RR_EnumDefinition reverse map for JVM context.
        for ((rEnum, idx) in enumIndexMap) {
            rEnumToRREnum[rEnum] = allEnumsList[idx]
        }

        // Build R_ definition ID → flat index reverse maps.
        val entityDefIdIdx = allEntitiesList.withIndex().associate { (i, e) -> e.base.defId to i }.toImmMap()
        val enumDefIdIdx = allEnumsList.withIndex().associate { (i, e) -> e.base.defId to i }.toImmMap()
        val objectDefIdIdx = allObjectsList.withIndex().associate { (i, o) -> o.base.defId to i }.toImmMap()
        val functionDefIdIdx = allFunctionsList.withIndex().associate { (i, f) -> f.base.defId to i }.toImmMap()
        val operationDefIdIdx = allOperationsList.withIndex().associate { (i, o) -> o.base.defId to i }.toImmMap()
        val constantDefIdIdx = allConstantsList.withIndex().associate { (i, c) -> c.base.defId to i }.toImmMap()

        // Build R_FunctionBase → RR_FunctionBase map for extendable function extensions.
        val rFnBaseMap = mutableMapOf<R_FunctionBase, RR_FunctionBase>()
        for ((fnBase, idx) in fnBaseToIndexMap) {
            if (idx < allFunctionsList.size) {
                rFnBaseMap[fnBase] = allFunctionsList[idx].fnBase
            }
        }
        // Also resolve extension function bases from the function extensions table.
        for (exts in rApp.functionExtensions.allExtensions()) {
            for (ext in exts.extensions) {
                if (ext.fnBase !in rFnBaseMap) {
                    rFnBaseMap[ext.fnBase] = resolveFnBase(ext.fnBase, ext.fnBase.defName)
                }
            }
        }

        // Build serializable RR_FunctionExtensions list.
        val rrFunctionExtensions = rApp.functionExtensions.allExtensions().mapToImmList { exts ->
            RR_FunctionExtensions(
                uid = exts.uid.id,
                extensions = exts.extensions.mapToImmList { ext ->
                    checkNotNull(rFnBaseMap[ext.fnBase]) {
                        "RR_FunctionBase not resolved for extension: ${ext.fnBase}"
                    }
                },
            )
        }

        val rrApp = RR_App(
            modules = modules,
            operations = operations,
            queries = queries,
            constants = constants,
            moduleArgs = moduleArgs,
            sqlDefs = run {
                // Deduplicate by defId — REPL accumulates old+new R_ objects for the same entity.
                val seenEntityIds = mutableSetOf<DefinitionId>()
                val seenObjectIds = mutableSetOf<DefinitionId>()
                RR_AppSqlDefs(
                    entities = rApp.sqlDefs.entities
                        .filter { seenEntityIds.add(entityIndexMap.getValue(it).let { idx -> allEntitiesList[idx].base.defId }) }
                        .mapToImmList { allEntitiesList[entityIndexMap.getValue(it)] },
                    objects = rApp.sqlDefs.objects
                        .filter { seenObjectIds.add(objectIndexMap.getValue(it).let { idx -> allObjectsList[idx].base.defId }) }
                        .mapToImmList { allObjectsList[objectIndexMap.getValue(it)] },
                    topologicalEntities = rApp.sqlDefs.topologicalEntities
                        .mapToImmList { allEntitiesList[entityIndexMap.getValue(it)] }
                        .distinctBy { it.base.defId }
                        .toImmList(),
                )
            },
            nativeFunctions = nativeFunctions,
            allEntities = allEntitiesList.toImmList(),
            allObjects = allObjectsList.toImmList(),
            allStructs = allStructsList.toImmList(),
            allEnums = allEnumsList.toImmList(),
            allOperations = allOperationsList.toImmList(),
            allQueries = allQueriesList.toImmList(),
            allFunctions = allFunctionsList.toImmList(),
            allConstants = allConstantsList.toImmList(),
            entityDefIdIndex = entityDefIdIdx,
            // Build name → index from the finalized struct list so on-demand additions
            // (mirror structs, stdlib types like `gtx_operation` registered via type resolution)
            // are reachable by name. Matches the deserializer's index-construction strategy.
            structNameIndex = allStructsList.withIndex()
                .associate { (i, s) -> s.struct.name to i }.toImmMap(),
            enumDefIdIndex = enumDefIdIdx,
            objectDefIdIndex = objectDefIdIdx,
            functionDefIdIndex = functionDefIdIdx,
            operationDefIdIndex = operationDefIdIdx,
            constantDefIdIndex = constantDefIdIdx,
            externalChains = rApp.externalChains.mapToImmList { RR_ExternalChainRef(it.name, it.index) },
            functionExtensions = rrFunctionExtensions,
        )

        // Ensure mirror struct types are in the type cache for runtime resolution.
        // Done AFTER flat arrays are built so struct indices aren't disrupted.
        for (module in rApp.modules) {
            for (entity in module.entities.values) {
                resolveType(entity.mirrorStructs.immutable.type)
                resolveType(entity.mirrorStructs.mutable.type)
            }
        }

        return rrApp
    }

    // --- Index registration helpers (used by both convertType and resolverContext) ---

    private fun registerEntity(rEntity: R_EntityDefinition): Int {
        val existing = entityIndexMap[rEntity]
        if (existing != null) return existing
        val idx = entityIndexMap.size
        entityIndexMap[rEntity] = idx
        pendingEntities.add(rEntity)
        return idx
    }

    private fun registerObject(rObject: R_ObjectDefinition): Int {
        val existing = objectIndexMap[rObject]
        if (existing != null) return existing
        val idx = objectIndexMap.size
        objectIndexMap[rObject] = idx
        pendingObjects.add(rObject)
        return idx
    }

    private fun registerStruct(rStruct: R_Struct): Int {
        val existing = structIndexMap[rStruct]
        if (existing != null) return existing
        val idx = structIndexMap.size
        structIndexMap[rStruct] = idx
        rStruct.rrDefIndex = idx
        pendingStructs.add(rStruct)
        return idx
    }

    private fun registerEnum(rEnum: R_EnumDefinition): Int {
        val existing = enumIndexMap[rEnum]
        if (existing != null) return existing
        val idx = enumIndexMap.size
        enumIndexMap[rEnum] = idx
        pendingEnums.add(rEnum)
        return idx
    }

    private fun registerFunction(rFn: R_FunctionDefinition): Int {
        val existing = functionIndexMap[rFn]
        if (existing != null) return existing
        val idx = functionIndexMap.size
        functionIndexMap[rFn] = idx
        fnBaseToIndexMap.putIfAbsent(rFn.fnBase, idx)
        pendingFunctions.add(rFn)
        return idx
    }

    private fun registerQuery(rQuery: R_QueryDefinition): Int {
        val existing = queryIndexMap[rQuery]
        if (existing != null) return existing
        val idx = queryIndexMap.size
        queryIndexMap[rQuery] = idx
        pendingQueries.add(rQuery)
        return idx
    }

    private fun registerOperation(rOp: R_OperationDefinition): Int {
        val existing = operationIndexMap[rOp]
        if (existing != null) return existing
        val idx = operationIndexMap.size
        operationIndexMap[rOp] = idx
        pendingOperations.add(rOp)
        return idx
    }

    private fun registerConstant(rConst: R_GlobalConstantDefinition): Int {
        val existing = constantIndexMap[rConst]
        if (existing != null) return existing
        val idx = constantIndexMap.size
        constantIndexMap[rConst] = idx
        pendingConstants.add(rConst)
        return idx
    }

    /**
     * Drain all pending definition queues into their flat lists. Each entry is processed
     * exactly once. Resolving one definition may register new ones (e.g., a function body
     * references another function via the [resolverContext] callbacks), which append to the
     * corresponding queue and are picked up by subsequent iterations of the outer loop.
     * Total work is O(total definitions), as opposed to the previous re-scan-with-sortedBy
     * approach which was O(d²) (or O(d² log d) with the redundant sort).
     *
     * Lists are passed in so the same helper can be reused both for the main drain and for
     * the post-loop sqlDefs carry-over re-drain.
     */
    private fun drainPendingQueues(
        allFunctionsList: MutableList<RR_FunctionDefinition>,
        allQueriesList: MutableList<RR_QueryDefinition>,
        allOperationsList: MutableList<RR_OperationDefinition>,
        allConstantsList: MutableList<RR_GlobalConstantDefinition>,
        allStructsList: MutableList<RR_StructDefinition>,
        allEntitiesList: MutableList<RR_EntityDefinition>,
        allEnumsList: MutableList<RR_EnumDefinition>,
        allObjectsList: MutableList<RR_ObjectDefinition>,
    ) {
        while (
            pendingFunctions.isNotEmpty() || pendingQueries.isNotEmpty()
            || pendingOperations.isNotEmpty() || pendingConstants.isNotEmpty()
            || pendingStructs.isNotEmpty() || pendingEntities.isNotEmpty()
            || pendingEnums.isNotEmpty() || pendingObjects.isNotEmpty()
        ) {
            while (pendingFunctions.isNotEmpty()) {
                allFunctionsList.add(resolveFunction(pendingFunctions.removeFirst()))
            }
            while (pendingQueries.isNotEmpty()) {
                allQueriesList.add(resolveQuery(pendingQueries.removeFirst()))
            }
            while (pendingOperations.isNotEmpty()) {
                allOperationsList.add(resolveOperation(pendingOperations.removeFirst()))
            }
            while (pendingConstants.isNotEmpty()) {
                allConstantsList.add(resolveConstant(pendingConstants.removeFirst()))
            }
            while (pendingStructs.isNotEmpty()) {
                allStructsList.add(resolveStructFromRStruct(pendingStructs.removeFirst()))
            }
            while (pendingEntities.isNotEmpty()) {
                allEntitiesList.add(resolveEntity(pendingEntities.removeFirst()))
            }
            while (pendingEnums.isNotEmpty()) {
                allEnumsList.add(resolveEnum(pendingEnums.removeFirst()))
            }
            while (pendingObjects.isNotEmpty()) {
                allObjectsList.add(resolveObject(pendingObjects.removeFirst()))
            }
        }
    }

    // --- Type resolution: R_Type → RR_Type ---

    fun resolveType(rType: R_Type): RR_Type = typeCache.getOrPut(rType) {
        val rr = convertType(rType)

        if (visitedTypes.add(rr)) {
            // Recursively ensure parent type is visited.
            rType.parentType?.let { resolveType(it) }
        }

        rr
    }

    private fun convertType(rType: R_Type): RR_Type = when (rType) {
        // Primitives
        is R_BooleanType -> RR_Type.Primitive(RR_PrimitiveKind.BOOLEAN)
        is R_IntegerType -> RR_Type.Primitive(RR_PrimitiveKind.INTEGER)
        is R_BigIntegerType -> RR_Type.Primitive(RR_PrimitiveKind.BIG_INTEGER)
        is R_DecimalType -> RR_Type.Primitive(RR_PrimitiveKind.DECIMAL)
        is R_TextType -> RR_Type.Primitive(RR_PrimitiveKind.TEXT)
        is R_ByteArrayType -> RR_Type.Primitive(RR_PrimitiveKind.BYTE_ARRAY)
        is R_RowidType -> RR_Type.Primitive(RR_PrimitiveKind.ROWID)
        is R_GUIDType -> RR_Type.Primitive(RR_PrimitiveKind.GUID)
        is R_SignerType -> RR_Type.Primitive(RR_PrimitiveKind.SIGNER)
        is R_JsonType -> RR_Type.Primitive(RR_PrimitiveKind.JSON)
        is R_GtvType -> RR_Type.Primitive(RR_PrimitiveKind.GTV)
        is R_RangeType -> RR_Type.Primitive(RR_PrimitiveKind.RANGE)
        is R_UnitType -> RR_Type.Primitive(RR_PrimitiveKind.UNIT)
        is R_NullType -> RR_Type.Null

        is R_EntityType -> RR_Type.Entity(registerEntity(rType.rEntity))

        is R_StructType -> {
            // Mirror structs may not be in the index — register on demand.
            RR_Type.Struct(registerStruct(rType.struct))
        }

        is R_EnumType -> RR_Type.Enum(registerEnum(rType.enum))

        is R_ObjectType -> RR_Type.Object(registerObject(rType.rObject))

        is R_OperationType -> RR_Type.Operation(operationIndexMap.getValue(rType.rOperation))

        // Composite types
        is R_NullableType -> RR_Type.Nullable(resolveType(rType.valueType))
        is R_ListType -> RR_Type.List(resolveType(rType.elementType))
        is R_SetType -> RR_Type.Set(resolveType(rType.elementType))
        is R_MapType -> RR_Type.Map(resolveType(rType.keyType), resolveType(rType.valueType))
        is R_TupleType -> RR_Type.Tuple(rType.fields.mapToImmList { RR_TupleField(it.name?.str, resolveType(it.type)) })
        is R_FunctionType -> RR_Type.Function(rType.params.mapToImmList { resolveType(it) }, resolveType(rType.result))

        // Virtual types
        is R_VirtualListType -> RR_Type.VirtualList(resolveType(rType.innerType.elementType))
        is R_VirtualSetType -> RR_Type.VirtualSet(resolveType(rType.innerType.elementType))
        is R_VirtualMapType -> RR_Type.VirtualMap(
            resolveType(rType.innerType.keyType),
            resolveType(rType.innerType.valueType)
        )

        is R_VirtualStructType -> RR_Type.VirtualStruct(structIndexMap.getValue(rType.innerType.struct))
        is R_VirtualTupleType -> RR_Type.VirtualTuple(rType.innerType.fields.mapToImmList {
            RR_TupleField(
                it.name?.str,
                resolveType(it.type)
            )
        })

        // Error type
        is R_CtErrorType -> RR_Type.Error

        // Catch-all for library-defined generic types
        else -> {
            val args = rType.getTypeArgs().mapToImmList { resolveType(it) }
            RR_Type.Generic(rType.name, args)
        }
    }

    // --- Module / definition resolution ---

    private fun resolveModule(rModule: R_Module): RR_Module = RR_Module(
        name = rModule.name,
        directory = rModule.directory,
        abstract = rModule.abstract,
        external = rModule.external,
        externalChain = rModule.externalChain,
        test = rModule.test,
        disabled = rModule.disabled,
        selected = rModule.selected,
        entities = rModule.entities.mapValuesToImmMap { resolveEntity(it.value) },
        objects = rModule.objects.mapValuesToImmMap { resolveObject(it.value) },
        structs = rModule.structs.mapValuesToImmMap { resolveStructDef(it.value) },
        enums = rModule.enums.mapValuesToImmMap { resolveEnum(it.value) },
        operations = rModule.operations.mapValuesToImmMap { resolveOperation(it.value) },
        queries = rModule.queries.mapValuesToImmMap { resolveQuery(it.value) },
        functions = rModule.functions.mapValuesToImmMap { resolveFunction(it.value) },
        constants = rModule.constants.mapValuesToImmMap { resolveConstant(it.value) },
        imports = rModule.imports,
        moduleArgs = rModule.moduleArgs?.let { resolveStructDef(it) },
    )

    private fun resolveBaseFromDef(def: R_Definition): RR_DefinitionBase = RR_DefinitionBase(
        defId = def.defId,
        defName = def.defName,
        initFrame = def.initFrameGetter.get().toRR(),
    )

    private fun resolveEntity(rEntity: R_EntityDefinition): RR_EntityDefinition {
        entityDefCache[rEntity]?.let { return@resolveEntity it }
        val attrs = resolveAttributes(rEntity.attributes)
        val resolved = RR_EntityDefinition(
            base = resolveBaseFromDef(rEntity),
            rName = rEntity.rName,
            flags = rEntity.flags,
            sqlMapping = resolveSqlMapping(rEntity.sqlMapping),
            external = rEntity.external?.let { RR_ExternalEntity(it.chain.name, it.metaCheck) },
            type = resolveType(rEntity.type),
            keys = rEntity.keys,
            indexes = rEntity.indexes,
            attributes = attrs,
        )
        entityDefCache[rEntity] = resolved
        return resolved
    }

    private fun resolveSqlMapping(mapping: R_EntitySqlMapping): RR_EntitySqlMapping {
        val kind: RR_EntitySqlMappingKind
        val chainIndex: Int
        when (mapping) {
            is R_EntitySqlMapping_Regular -> {
                kind = RR_EntitySqlMappingKind.REGULAR
                chainIndex = -1
            }

            is R_EntitySqlMapping_External -> {
                kind = RR_EntitySqlMappingKind.EXTERNAL
                chainIndex = mapping.chain.index
            }

            is R_EntitySqlMapping_Transaction -> {
                kind = RR_EntitySqlMappingKind.TRANSACTION
                chainIndex = mapping.chain?.index ?: -1
            }

            is R_EntitySqlMapping_Block -> {
                kind = RR_EntitySqlMappingKind.BLOCK
                chainIndex = mapping.chain?.index ?: -1
            }

            else -> {
                kind = RR_EntitySqlMappingKind.REGULAR
                chainIndex = -1
            }
        }
        return RR_EntitySqlMapping(
            mountName = mapping.mountName,
            metaName = mapping.metaName,
            rowidColumn = mapping.rowidColumn(),
            autoCreateTable = mapping.autoCreateTable(),
            isSystemEntity = mapping.isSystemEntity(),
            kind = kind,
            externalChainIndex = chainIndex,
        )
    }

    private fun resolveObject(rObject: R_ObjectDefinition): RR_ObjectDefinition {
        objectDefCache[rObject]?.let { return it }
        val resolved = RR_ObjectDefinition(
            base = resolveBaseFromDef(rObject),
            rEntity = resolveEntity(rObject.rEntity),
        )
        objectDefCache[rObject] = resolved
        return resolved
    }

    private fun resolveEnum(rEnum: R_EnumDefinition): RR_EnumDefinition = RR_EnumDefinition(
        base = resolveBaseFromDef(rEnum),
        attrs = rEnum.attrs.mapToImmList { RR_EnumAttr(it.rName, it.value) },
    )

    private fun resolveStructDef(rStructDef: R_StructDefinition): RR_StructDefinition {
        structDefCache[rStructDef]?.let { return@resolveStructDef it }
        val resolved = RR_StructDefinition(
            base = resolveBaseFromDef(rStructDef),
            struct = resolveStruct(rStructDef.struct),
            hasDefaultConstructor = rStructDef.hasDefaultConstructor,
        )
        structDefCache[rStructDef] = resolved
        return resolved
    }

    /**
     * Resolve an [R_Struct] that was registered during type resolution but has no backing module definition
     * (e.g. mirror structs). Creates a minimal [RR_StructDefinition].
     */
    private fun resolveStructFromRStruct(rStruct: R_Struct): RR_StructDefinition {
        val rrStruct = resolveStruct(rStruct)
        // Use the R_DefinitionBase from R_Struct if available, otherwise create a dummy base.
        val base = if (rStruct.rDefBase != null) {
            RR_DefinitionBase(
                defId = rStruct.rDefBase.defId,
                defName = rStruct.rDefBase.defName,
                initFrame = rStruct.rDefBase.initFrameGetter.get().toRR(),
            )
        } else {
            RR_DefinitionBase(
                defId = DefinitionId("", rStruct.name),
                defName = DefinitionName("", rStruct.name, rStruct.name),
                initFrame = R_CallFrame.ERROR.toRR(),
            )
        }
        return RR_StructDefinition(
            base = base,
            struct = rrStruct,
            hasDefaultConstructor = rStruct.attributesList.all { it.exprGetter?.get() != null },
        )
    }

    private fun resolveStruct(rStruct: R_Struct): RR_Struct {
        val attrs = resolveAttributes(rStruct.attributes)

        val mirrorInfo = rStruct.mirrorStructs?.let { ms ->
            val definition = rStruct.rDefBase?.defName?.appLevelName
            if (definition != null) {
                val mutable = ms.mutable === rStruct
                RR_MirrorStructInfo(ms.defTypeName, definition, mutable)
            } else null
        }

        return RR_Struct(
            name = rStruct.name,
            attributes = attrs,
            flags = RR_StructFlags(
                typeFlags = rStruct.flags.typeFlags,
                cyclic = rStruct.flags.cyclic,
                infinite = rStruct.flags.infinite,
            ),
            mirrorInfo = mirrorInfo,
        )
    }

    private fun resolveOperation(rOp: R_OperationDefinition): RR_OperationDefinition {
        val ints = rOp.internals.get()
        val body = irResolver.resolveStmt(ints.body)
        val guardBody = extractGuardBody(ints.body)?.let { irResolver.resolveStmt(it) }

        return RR_OperationDefinition(
            base = resolveBaseFromDef(rOp),
            mountName = rOp.mountName,
            modifiers = rOp.modifiers,
            params = ints.params.mapToImmList { resolveParam(it) },
            paramVars = ints.paramVars.mapToImmList { resolveParamVar(it) },
            body = body,
            guardBody = guardBody,
            frame = ints.frame.toRR(),
        )
    }

    private fun resolveQuery(rQuery: R_QueryDefinition): RR_QueryDefinition {
        val rBody = rQuery.bodyLate.get()
        val body = resolveQueryBody(rBody)

        return RR_QueryDefinition(
            base = resolveBaseFromDef(rQuery),
            mountName = rQuery.mountName,
            body = body,
        )
    }

    private fun resolveQueryBody(rBody: R_QueryBody): RR_QueryBody = when (rBody) {
        is R_UserQueryBody -> RR_UserQueryBody(
            retType = resolveType(rBody.retType),
            params = rBody.params.mapToImmList { resolveParam(it) },
            paramVars = rBody.paramVars.mapToImmList { resolveParamVar(it) },
            body = irResolver.resolveStmt(rBody.body),
            frame = rBody.frame.toRR(),
        )

        is R_SysQueryBody -> {
            resolverRuntime.registerSysFn(rBody.key, rBody.fn)
            RR_SysQueryBody(
                retType = resolveType(rBody.retType),
                params = rBody.params.mapToImmList { resolveParam(it) },
                fnName = rBody.key,
            )
        }
    }

    private fun resolveFunction(rFn: R_FunctionDefinition): RR_FunctionDefinition = RR_FunctionDefinition(
        base = resolveBaseFromDef(rFn),
        fnBase = resolveFnBase(rFn.fnBase, rFn.defName),
        isTest = rFn.isTest,
        disabled = rFn.disabled,
    )

    private fun resolveFnBase(rFnBase: R_FunctionBase, defName: DefinitionName): RR_FunctionBase {
        val header = rFnBase.getHeader()
        val body = rFnBase.getBody()
        return RR_FunctionBase(
            defId = body.frame.defId,
            defName = defName,
            params = header.params.mapToImmList { resolveParam(it) },
            resultType = resolveType(header.type),
            paramVars = body.paramVars.mapToImmList { resolveParamVar(it) },
            body = irResolver.resolveStmt(body.body),
            frame = body.frame.toRR(),
        )
    }

    private fun resolveConstant(rConst: R_GlobalConstantDefinition): RR_GlobalConstantDefinition {
        val body = rConst.bodyGetter.get()
        val metaGtvJson = constantMetaGtvJson(body)
        return RR_GlobalConstantDefinition(
            base = resolveBaseFromDef(rConst),
            constId = rConst.constId,
            type = resolveType(body.type),
            expr = irResolver.resolveExpr(body.expr),
            metaGtvJson = metaGtvJson,
        )
    }

    private fun constantMetaGtvJson(body: R_GlobalConstantBody): String? {
        val rrValue = body.value ?: return null
        return resolverRuntime.constantMetaGtvJson(rrValue, body.type)
    }

    private fun resolveParam(rParam: R_FunctionParam): RR_FunctionParam {
        val initFrame = rParam.initFrameGetter.get()
        val sizeConstraint = extractSizeConstraint(rParam.validator)
        val defaultExpr = rParam.resolveDefaultExpr()?.let { irResolver.resolveExpr(it) }

        return RR_FunctionParam(
            name = rParam.name,
            type = resolveType(rParam.type),
            initFrame = initFrame.toRR(),
            defaultExpr = defaultExpr,
            sizeConstraint = sizeConstraint,
        )
    }

    private fun resolveParamVar(rParamVar: R_ParamVar): RR_ParamVar = RR_ParamVar(
        type = resolveType(rParamVar.type),
        ptr = rParamVar.ptr.toRR(),
    )

    private fun resolveAttributes(attrs: ImmMap<Name, R_Attribute>): ImmMap<Name, RR_Attribute> =
        attrs.mapValuesToImmMap { resolveAttribute(it.value) }

    private fun resolveAttribute(rAttr: R_Attribute): RR_Attribute {
        val defaultValue = rAttr.exprGetter?.get()
        val defaultExpr = defaultValue?.rExpr?.let { irResolver.resolveExpr(it) }
        return RR_Attribute(
            index = rAttr.index,
            rName = rAttr.rName,
            type = resolveType(rAttr.type),
            mutable = rAttr.mutable,
            keyIndexKind = rAttr.keyIndexKind,
            canSetInCreate = rAttr.canSetInCreate,
            sqlMapping = rAttr.sqlMapping,
            defaultExpr = defaultExpr,
            isDbModification = defaultValue?.isDbModification ?: false,
            sizeConstraint = extractSizeConstraint(rAttr.validator),
        )
    }

    private fun extractSizeConstraint(validator: R_AttrValidator?): RR_SizeConstraint? = when (validator) {
        null -> null
        is R_SizeAttrValidator -> {
            val meta = validator.metadata
            val defTypeStr = meta.ownerDefType.name.lowercase(java.util.Locale.US)
            val codePrefix =
                "$defTypeStr:${meta.ownerName.simpleName}:${meta.valueTargetType.description}:${meta.name.str}"
            val kind = when (validator.sqlSizeAdapter) {
                R_SqlSizeAdapter.BYTE_ARRAY -> RR_SizeConstraintKind.BYTE_ARRAY
                R_SqlSizeAdapter.TEXT -> RR_SizeConstraintKind.TEXT
            }
            RR_SizeConstraint(min = validator.min, max = validator.max, kind = kind, codePrefix = codePrefix)
        }

        else -> null
    }

    private fun extractGuardBody(body: R_Statement): R_Statement? = when (body) {
        is R_GuardStatement -> body
        is R_BlockStatement -> body.getGuardStmts()
        else -> null
    }

    // --- Type info collection ---

    private fun collectTypes() {
        for (module in rApp.modules) {
            for (entity in module.entities.values) {
                for (attr in entity.attributes.values) {
                    resolveType(attr.type)
                }
            }
            for (struct in module.structs.values) {
                for (attr in struct.struct.attributesList) {
                    resolveType(attr.type)
                }
            }
            for (fn in module.functions.values) {
                val header = fn.fnBase.getHeader()
                resolveType(header.type)
                for (p in header.params) {
                    resolveType(p.type)
                }
            }
            for (op in module.operations.values) {
                val ints = op.internals.get()
                for (p in ints.params) {
                    resolveType(p.type)
                }
            }
            for (q in module.queries.values) {
                val body = q.bodyLate.get()
                resolveType(body.retType)
                for (p in body.params) {
                    resolveType(p.type)
                }
            }
            for (c in module.constants.values) {
                val body = c.bodyGetter.get()
                resolveType(body.type)
            }
            // Ensure module_args struct type is in the type info map.
            module.moduleArgs?.let { resolveType(it.type) }
        }
    }

}
