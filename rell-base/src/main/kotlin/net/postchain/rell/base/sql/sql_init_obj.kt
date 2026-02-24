/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.sql

import net.postchain.rell.base.model.R_ObjectDefinition
import net.postchain.rell.base.runtime.*
import java.util.*

class SqlObjectsInit(private val exeCtx: Rt_ExecutionContext) {
    private var addDone = false
    private val states = mutableMapOf<R_ObjectDefinition, ObjState>()
    private val initStack: Deque<ObjState> = ArrayDeque()

    fun addObject(obj: R_ObjectDefinition) {
        check(!addDone)
        check(obj !in states) { "Object already in the map: '${obj.appLevelName}'" }
        states[obj] = ObjState(obj)
    }

    fun initObject(obj: R_ObjectDefinition) {
        addDone = false
        pushState(obj) {
            it.init()
        }
    }

    fun forceObject(obj: R_ObjectDefinition) {
        addDone = false
        pushState(obj) {
            it.force()
        }
    }

    private fun pushState(obj: R_ObjectDefinition, code: (ObjState) -> Unit) {
        val state = states.getValue(obj)
        initStack.push(state)
        try {
            code(state)
        } finally {
            initStack.pop()
        }
    }

    private inner class ObjState(val obj: R_ObjectDefinition) {
        private var started: Boolean = false
        private var finished: Boolean = false

        fun init() {
            if (!finished) {
                init0()
            }
        }

        fun force() {
            check(!finished) { "Object must have been already initialized: '${obj.appLevelName}'" }
            init0()
        }

        private fun init0() {
            if (started) {
                throw Rt_Exception(cycleError())
            }

            started = true
            val modsAllowed = exeCtx.globalCtx.compilerOptions.allowDbModificationsInObjectExprs
            val frame = Rt_CallFrame.createInitFrame(exeCtx, obj.rEntity, modsAllowed)
            obj.insert(frame)
            finished = true
        }

        private fun cycleError(): Rt_Error {
            check(!initStack.isEmpty()) { "Invalid state: '${obj.appLevelName}'" }

            val cycle = mutableListOf<ObjState>()
            var found = false
            for (state in initStack.reversed()) {
                found = found || state === this
                if (found) cycle.add(state)
            }

            val shortStr = cycle.joinToString(",") { it.obj.appLevelName }
            val fullStr = cycle.joinToString(", ") { it.obj.appLevelName }

            return Rt_CommonError("obj:init_cycle:$shortStr",
                    "Cannot initialize object '${obj.appLevelName}' because it depends on itself: $fullStr")
        }
    }
}
