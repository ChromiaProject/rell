/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.C_CodeMsg
import net.postchain.rell.base.compiler.base.utils.C_Constants
import net.postchain.rell.base.compiler.base.utils.C_FeatureSwitch
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.lib.type.Rt_UnitValue
import net.postchain.rell.base.model.R_DefinitionName
import net.postchain.rell.base.model.R_GlobalConstantId
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.model.R_StructDefinition
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.sql.NoConnSqlExecutor
import net.postchain.rell.base.utils.*

class Rt_GlobalConstantState(val constId: R_GlobalConstantId, val value: Rt_Value)

abstract class Rt_ModuleArgsSource {
    abstract fun getModuleArgs(exeCtx: Rt_ExecutionContext, moduleName: R_ModuleName): Rt_Value?

    companion object {
        val NULL: Rt_ModuleArgsSource = Rt_NullModuleArgsSource
    }
}

private object Rt_NullModuleArgsSource: Rt_ModuleArgsSource() {
    override fun getModuleArgs(exeCtx: Rt_ExecutionContext, moduleName: R_ModuleName) = null
}

class Rt_GtvModuleArgsSource(
    private val gtvs: Map<R_ModuleName, Gtv>,
    private val compilerOptions: C_CompilerOptions,
): Rt_ModuleArgsSource() {
    private val defaultValuesSupported = DEFAULT_VALUES_SWITCH.isActive(compilerOptions)

    override fun getModuleArgs(exeCtx: Rt_ExecutionContext, moduleName: R_ModuleName): Rt_Value? {
        val rModule = exeCtx.appCtx.app.moduleMap[moduleName]
        val argsStruct = Rt_Utils.checkNotNull(rModule?.moduleArgs) {
            // Must not happen, but we must check for null.
            val msg = "No ${C_Constants.MODULE_ARGS_STRUCT} struct defined for module '$moduleName'"
            "expr:no_module_args_def:$moduleName" toCodeMsg msg
        }

        val gtv = getArgsGtv(moduleName, argsStruct)
        gtv ?: return null

        val frame = Rt_CallFrame.createInitFrame(exeCtx, argsStruct, modsAllowed = false)
        val defaultValueEvaluator = if (!defaultValuesSupported) null else GtvToRtDefaultValueEvaluator { expr ->
            expr.evaluate(frame)
        }

        return PostchainGtvUtils.moduleArgsGtvToRt(
            argsStruct,
            gtv,
            defaultValueEvaluator = defaultValueEvaluator,
            compilerOptions = compilerOptions,
        )
    }

    private fun getArgsGtv(moduleName: R_ModuleName, struct: R_StructDefinition): Gtv? {
        val gtv = gtvs[moduleName]
        return when {
            gtv != null -> gtv
            defaultValuesSupported && struct.hasDefaultConstructor -> GtvFactory.gtv(immMapOf())
            else -> null
        }
    }

    companion object {
        val DEFAULT_VALUES_SWITCH = C_FeatureSwitch("0.13.5")
    }
}

class Rt_GlobalConstants(
    private val appCtx: Rt_AppContext,
    private val moduleArgsSource: Rt_ModuleArgsSource,
    oldState: State,
) {
    private val constantSlots = appCtx.app.constants.map { ConstantSlot(it.constId) }.toImmList()

    private val moduleArgsSlots = appCtx.app.moduleArgs.keys
        .map { it to ModuleArgsSlot(it) }
        .toImmMap()

    private var inited = false
    private var initExeCtx: Rt_ExecutionContext? = null

    init {
        check(oldState.constants.size <= constantSlots.size)
        for (i in oldState.constants.indices) {
            constantSlots[i].restore(oldState.constants[i])
        }

        for ((moduleName, value) in oldState.moduleArgs.entries) {
            val slot = moduleArgsSlots.getValue(moduleName)
            slot.restore(value)
        }
    }

    fun initialize() {
        check(!inited)
        check(initExeCtx == null)
        inited = true

        val sqlCtx = Rt_NullSqlContext.create(appCtx.app)
        initExeCtx = Rt_ExecutionContext(appCtx, Rt_NullOpContext, sqlCtx, NoConnSqlExecutor)

        try {
            for (slot in constantSlots) {
                slot.getValue()
            }
            for (slot in moduleArgsSlots.values) {
                slot.getValue()
            }
        } finally {
            initExeCtx = null
        }
    }

    fun getConstantValue(constId: R_GlobalConstantId): Rt_Value {
        val slot = constantSlots[constId.index]
        checkEquals(slot.constId, constId)
        return slot.getValue()
    }

    fun getModuleArgsValue(moduleName: R_ModuleName): Rt_Value? {
        val slot = moduleArgsSlots[moduleName]
        val value = slot?.getValue()
        val res = if (value != Rt_UnitValue) value else null
        return res
    }

    fun dump(): State {
        return State(
            constants = constantSlots.map { it.dump() }.toImmList(),
            moduleArgs = moduleArgsSlots.mapValues { it.value.dump() }.toImmMap()
        )
    }

    private abstract inner class AbstractSlot {
        private var value: Rt_Value? = null
        private var initing = false

        protected abstract fun errId(): C_CodeMsg
        protected abstract fun evaluate(exeCtx: Rt_ExecutionContext): Rt_Value

        protected fun restoreValue(v: Rt_Value) {
            check(value == null)
            check(!initing)
            value = v
        }

        protected fun dumpValue(): Rt_Value = value!!

        fun getValue(): Rt_Value {
            val v = value
            if (v != null) {
                return v
            }

            Rt_Utils.check(!initing) {
                val id = errId()
                "const:recursion:${id.code}" toCodeMsg "Recursive expression: ${id.msg}"
            }
            initing = true

            val exeCtx = checkNotNull(initExeCtx) { errId().msg }
            val v2 = evaluate(exeCtx)

            value = v2
            initing = false

            return v2
        }
    }

    private inner class ConstantSlot(val constId: R_GlobalConstantId): AbstractSlot() {
        override fun errId(): C_CodeMsg {
            return "const:${constId.strCode()}" toCodeMsg "constant ${constId.appLevelName}"
        }

        override fun evaluate(exeCtx: Rt_ExecutionContext): Rt_Value {
            val c = appCtx.app.constants[constId.index]
            checkEquals(c.constId, constId)
            return c.evaluate(exeCtx)
        }

        fun restore(state: Rt_GlobalConstantState) {
            checkEquals(state.constId, constId)
            restoreValue(state.value)
        }

        fun dump() = Rt_GlobalConstantState(constId, dumpValue())
    }

    private inner class ModuleArgsSlot(val moduleName: R_ModuleName): AbstractSlot() {
        override fun errId(): C_CodeMsg {
            val modNameStr = moduleName.str()
            val msg = R_DefinitionName.appLevelName(modNameStr, C_Constants.MODULE_ARGS_STRUCT)
            return "modargs:$modNameStr" toCodeMsg msg
        }

        override fun evaluate(exeCtx: Rt_ExecutionContext): Rt_Value {
            val value = moduleArgsSource.getModuleArgs(exeCtx, moduleName)
            return value ?: Rt_UnitValue
        }

        fun restore(value: Rt_Value) {
            restoreValue(value)
        }

        fun dump(): Rt_Value = dumpValue()
    }

    class State(
        val constants: List<Rt_GlobalConstantState> = immListOf(),
        val moduleArgs: Map<R_ModuleName, Rt_Value> = immMapOf(),
    )
}
