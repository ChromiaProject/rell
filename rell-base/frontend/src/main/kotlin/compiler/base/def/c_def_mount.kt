/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.def

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.core.C_MessageContext
import net.postchain.rell.base.compiler.base.core.C_Name
import net.postchain.rell.base.compiler.base.namespace.C_DeclarationType
import net.postchain.rell.base.compiler.base.utils.C_Errors
import net.postchain.rell.base.model.*
import net.postchain.rell.base.utils.*

private class C_MountConflictsProcessor(private val chain: String?, private val currentStamp: AppUid) {
    fun processConflicts(
            msgCtx: C_MessageContext,
            allEntries: List<C_MntEntry>,
            errorsEntries: MutableSet<C_MntEntry>
    ): ImmList<C_MntEntry> {
        val map = mutableMultimapOf<MountName, C_MntEntry>()
        for (entry in allEntries) {
            map.put(entry.mountName, entry)
        }

        val res = mutableListOf<C_MntEntry>()

        for (name in map.keySet()) {
            val entries = map.get(name)
            val (sysEntries, userEntries) = entries.partition { it.pos == null }

            if (entries.size > 1) {
                for (entry in userEntries) {
                    if (errorsEntries.add(entry)) {
                        val otherEntry = entries.first { it !== entry }
                        handleConflict(msgCtx, entry, otherEntry)
                    }
                }
            }

            res.addAll(sysEntries)
            if (sysEntries.isEmpty() && !userEntries.isEmpty()) {
                res.add(userEntries[0])
            }
        }

        return res.toImmList()
    }

    private fun handleConflict(msgCtx: C_MessageContext, entry: C_MntEntry, otherEntry: C_MntEntry) {
        if (entry.def.appLevelName == otherEntry.def.appLevelName || entry.stamp != currentStamp) {
            // Same module and same qualified name -> must be also a name conflict error.
            return
        }

        if (msgCtx.globalCtx.compilerOptions.mountConflictError) {
            val error = C_Errors.errMountConflict(chain, entry.mountName, entry.def, entry.pos!!, otherEntry)
            msgCtx.error(error)
        }
    }
}

class C_MntEntry(
    val type: C_DeclarationType,
    val def: R_Definition,
    val pos: S_Pos?,
    val mountName: MountName,
    val stamp: AppUid,
) {
    companion object {
        private val SYSTEM_MOUNT_NAMES = let {
            // "block" and "transaction" are reserved mount names for backwards compatibility reasons
            val all = R_SqlConstants.SYSTEM_OBJECTS + listOf("block", "transaction")
            all.mapToImmList { MountName.of(it) }
        }

        private val SYSTEM_MOUNT_PREFIX = MountName.of("sys")

        fun processMountConflicts(msgCtx: C_MessageContext, stamp: AppUid, mntTables: C_MountTables): C_MountTables {
            val resChains = mntTables.chains.mapValuesToImmMap { (chain, t) ->
                val nullableChain = if (chain == "") null else chain

                val entities = processSystemMountConflicts(msgCtx, stamp, t.entities)

                C_ChainMountTables(
                        processMountConflicts0(msgCtx, nullableChain, stamp, entities),
                        processMountConflicts0(msgCtx, nullableChain, stamp, t.operations),
                        processMountConflicts0(msgCtx, nullableChain, stamp, t.queries)
                )
            }
            return C_MountTables(resChains)
        }

        private fun processMountConflicts0(
            msgCtx: C_MessageContext,
            chain: String?,
            stamp: AppUid,
            mntTable: C_MntTable
        ): C_MntTable {
            val processor = C_MountConflictsProcessor(chain, stamp)
            val resEntries = processor.processConflicts(msgCtx, mntTable.entries, mutableSetOf())
            return C_MntTable(resEntries)
        }

        private fun processSystemMountConflicts(msgCtx: C_MessageContext, stamp: AppUid, mntTable: C_MntTable): C_MntTable {
            val errEnabled = msgCtx.globalCtx.compilerOptions.mountConflictError
            val b = C_MntTableBuilder(stamp)

            for (entry in mntTable.entries) {
                val isConflict = entry.mountName in SYSTEM_MOUNT_NAMES || entry.mountName.startsWith(SYSTEM_MOUNT_PREFIX)
                if (entry.pos != null && isConflict && errEnabled) {
                    msgCtx.error(C_Errors.errMountConflictSystem(entry.mountName, entry.def, entry.pos))
                } else {
                    b.add(entry)
                }
            }

            return b.build()
        }
    }
}

class C_MountTablesBuilder(private val stamp: AppUid) {
    private val chains = mutableMapOf<String, C_ChainMountTablesBuilder>()

    fun add(tables: C_MountTables) {
        for ((chain, t) in tables.chains) {
            val b = chainBuilder(chain)
            b.entities.add(t.entities)
            b.operations.add(t.operations)
            b.queries.add(t.queries)
        }
    }

    fun addEntity(name: C_Name, rEntity: R_EntityDefinition) {
        addEntity0(C_DeclarationType.ENTITY, name.pos, rEntity, rEntity)
    }

    fun addSysEntity(rEntity: R_EntityDefinition) {
        addEntity0(C_DeclarationType.ENTITY, null, rEntity, rEntity)
    }

    fun addObject(name: C_Name, rObject: R_ObjectDefinition) {
        addEntity0(C_DeclarationType.OBJECT, name.pos, rObject, rObject.rEntity)
    }

    private fun addEntity0(type: C_DeclarationType, namePos: S_Pos?, def: R_Definition, rEntity: R_EntityDefinition) {
        val chain = rEntity.external?.chain?.name ?: ""
        val b = chainBuilder(chain)
        b.entities.add(type, def, namePos, rEntity.mountName)
    }

    fun addOperation(name: C_Name, o: R_OperationDefinition) {
        val b = chainBuilder("")
        b.operations.add(C_DeclarationType.OPERATION, o, name.pos, o.mountName)
    }

    fun addQuery(name: C_Name, q: R_QueryDefinition) {
        val b = chainBuilder("")
        b.queries.add(C_DeclarationType.QUERY, q, name.pos, q.mountName)
    }

    fun addQuery(q: R_QueryDefinition) {
        val b = chainBuilder("")
        b.queries.add(C_DeclarationType.QUERY, q, null, q.mountName)
    }

    private fun chainBuilder(chain: String) = chains.getOrPut(chain) { C_ChainMountTablesBuilder(stamp) }

    fun build(): C_MountTables {
        val resChains = chains.mapValuesToImmMap { (_, v) ->
            C_ChainMountTables(v.entities.build(), v.operations.build(), v.queries.build())
        }
        return C_MountTables(resChains)
    }
}

class C_MountTables(val chains: ImmMap<String, C_ChainMountTables>) {
    companion object { val EMPTY = C_MountTables(immMapOf()) }
}

class C_ChainMountTables(val entities: C_MntTable, val operations: C_MntTable, val queries: C_MntTable)

class C_ChainMountTablesBuilder(stamp: AppUid) {
    val entities = C_MntTableBuilder(stamp)
    val operations = C_MntTableBuilder(stamp)
    val queries = C_MntTableBuilder(stamp)
}

class C_MntTableBuilder(private val stamp: AppUid) {
    private val entries = mutableListOf<C_MntEntry>()
    private var finished = false

    fun add(table: C_MntTable) {
        check(!finished)
        entries.addAll(table.entries)
    }

    fun add(entry: C_MntEntry) {
        check(!finished)
        entries.add(entry)
    }

    fun add(type: C_DeclarationType, def: R_Definition, namePos: S_Pos?, mountName: MountName) {
        check(!finished)
        entries.add(C_MntEntry(type, def, namePos, mountName, stamp))
    }

    fun build(): C_MntTable {
        check(!finished)
        finished = true
        return C_MntTable(entries.toImmList())
    }
}

class C_MntTable(val entries: ImmList<C_MntEntry>) {
    companion object { val EMPTY = C_MntTable(immListOf()) }
}
