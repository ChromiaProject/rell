/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.sql

import net.postchain.rell.base.model.DefinitionId
import net.postchain.rell.base.model.rr.RR_EntityDefinition
import net.postchain.rell.base.model.rr.RR_ObjectDefinition
import net.postchain.rell.base.runtime.*
import java.util.*

class SqlObjectsInit(private val exeCtx: Rt_ExecutionContext) {
    private var addDone = false
    private val states = mutableMapOf<DefinitionId, ObjState>()
    private val initStack: Deque<ObjState> = ArrayDeque()

    fun addObject(obj: RR_ObjectDefinition) {
        check(!addDone)
        val defId = obj.base.defId
        check(defId !in states) { "Object already in the map: '${obj.base.appLevelName}'" }
        states[defId] = ObjState(obj)
    }

    fun initObject(obj: RR_ObjectDefinition) {
        addDone = false
        pushState(obj.base.defId) {
            it.init()
        }
    }

    fun forceObject(obj: RR_ObjectDefinition) {
        addDone = false
        pushState(obj.base.defId) {
            it.force()
        }
    }

    private fun pushState(defId: DefinitionId, code: (ObjState) -> Unit) {
        val state = states.getValue(defId)
        initStack.push(state)
        try {
            code(state)
        } finally {
            initStack.pop()
        }
    }

    private inner class ObjState(val obj: RR_ObjectDefinition) {
        private var started: Boolean = false
        private var finished: Boolean = false

        fun init() {
            if (!finished) {
                init0()
            }
        }

        fun force() {
            check(!finished) { "Object must have been already initialized: '${obj.base.appLevelName}'" }
            init0()
        }

        private fun init0() {
            if (started) {
                throw Rt_Exception(cycleError())
            }

            started = true
            val entity = obj.rEntity
            val modsAllowed = exeCtx.globalCtx.compilerOptions.allowDbModificationsInObjectExprs
            val defCtx = Rt_DefinitionContext(exeCtx, modsAllowed, entity.base.defId)
            val frame = Rt_CallFrame(defCtx, entity.base.initFrame, null)
            insertObject(frame, entity)
            finished = true
        }

        private fun insertObject(frame: Rt_CallFrame, entity: RR_EntityDefinition) {
            val sqlCtx = frame.defCtx.sqlCtx
            val interpreter = frame.defCtx.exeCtx.appCtx.interpreter
            val table = entity.sqlMapping.table(sqlCtx)
            val rowid = entity.sqlMapping.rowidColumn

            val attrs = entity.attributes.values.toList()
            val values = attrs.map { attr ->
                val defaultExpr = checkNotNull(attr.defaultExpr) {
                    "Object attribute '${attr.name}' has no default value"
                }

                val initDefCtx = Rt_DefinitionContext(frame.defCtx.exeCtx, frame.defCtx.dbUpdateAllowed, entity.base.defId)
                val initFrame = Rt_CallFrame(initDefCtx, entity.base.initFrame, null)
                interpreter.evaluateExpr(defaultExpr, initFrame)
            }

            val b = SqlBuilder()
            b.append("INSERT INTO ")
            b.appendName(table)
            b.append("(")
            b.appendName(rowid)
            for (attr in attrs) {
                b.append(", ")
                b.appendName(attr.sqlMapping)
            }
            b.append(") VALUES (")
            b.append(0L)
            for (value in values) {
                b.append(", ")
                b.append(value)
            }
            b.append(") RETURNING ")
            b.appendName(rowid)

            val sql = b.build()
            sql.execute(frame.sysSqlExec)
        }

        private fun cycleError(): Rt_Error {
            check(!initStack.isEmpty()) { "Invalid state: '${obj.base.appLevelName}'" }

            val cycle = mutableListOf<ObjState>()
            var found = false
            for (state in initStack.reversed()) {
                found = found || state === this
                if (found) cycle.add(state)
            }

            val shortStr = cycle.joinToString(",") { it.obj.base.appLevelName }
            val fullStr = cycle.joinToString(", ") { it.obj.base.appLevelName }

            return Rt_CommonError("obj:init_cycle:$shortStr",
                    "Cannot initialize object '${obj.base.appLevelName}' because it depends on itself: $fullStr")
        }
    }
}
