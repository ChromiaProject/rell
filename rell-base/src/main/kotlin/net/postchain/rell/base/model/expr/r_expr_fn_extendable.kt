/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.expr

import net.postchain.rell.base.lib.type.*
import net.postchain.rell.base.model.R_FunctionBase
import net.postchain.rell.base.model.R_FunctionDefinition
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.Rt_NullValue
import net.postchain.rell.base.runtime.Rt_CallContext
import net.postchain.rell.base.runtime.Rt_Exception
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.checkEquals

class R_ExtendableFunctionUid(val id: Int, private val name: String) {
    // No equals/hashCode on purpose.
    override fun toString() = "$id:$name"
}

class R_FunctionExtension(val fnBase: R_FunctionBase) {
    override fun toString() = fnBase.toString()
}

class R_FunctionExtensions(
    val uid: R_ExtendableFunctionUid,
    val extensions: ImmList<R_FunctionExtension>,
) {
    override fun toString() = uid.toString()
}

class R_FunctionExtensionsTable(private val list: ImmList<R_FunctionExtensions>) {
    init {
        for ((i, c) in this.list.withIndex()) {
            checkEquals(c.uid.id, i)
        }
    }

    fun getExtensions(uid: R_ExtendableFunctionUid): List<R_FunctionExtension> {
        val exts = list[uid.id]
        checkEquals(exts.uid, uid)
        return exts.extensions
    }
}

class R_ExtendableFunctionDescriptor(
        val uid: R_ExtendableFunctionUid,
        val combiner: R_ExtendableFunctionCombiner
)

sealed class R_ExtendableFunctionCombiner {
    abstract fun createRtCombiner(): Rt_ExtendableFunctionCombiner
}

object R_ExtendableFunctionCombiner_Unit: R_ExtendableFunctionCombiner() {
    override fun createRtCombiner(): Rt_ExtendableFunctionCombiner = Rt_ExtendableFunctionCombiner_Unit
}

object R_ExtendableFunctionCombiner_Boolean: R_ExtendableFunctionCombiner() {
    override fun createRtCombiner(): Rt_ExtendableFunctionCombiner = Rt_ExtendableFunctionCombiner_Boolean()
}

object R_ExtendableFunctionCombiner_Nullable: R_ExtendableFunctionCombiner() {
    override fun createRtCombiner(): Rt_ExtendableFunctionCombiner = Rt_ExtendableFunctionCombiner_Nullable()
}

class R_ExtendableFunctionCombiner_List(private val type: R_Type): R_ExtendableFunctionCombiner() {
    override fun createRtCombiner(): Rt_ExtendableFunctionCombiner = Rt_ExtendableFunctionCombiner_List(type)
}

class R_ExtendableFunctionCombiner_Map(private val mapType: R_MapType): R_ExtendableFunctionCombiner() {
    override fun createRtCombiner(): Rt_ExtendableFunctionCombiner = Rt_ExtendableFunctionCombiner_Map(mapType)
}

sealed class Rt_ExtendableFunctionCombiner {
    abstract fun addExtensionResult(value: Rt_Value): Boolean
    abstract fun getCombinedResult(): Rt_Value
}

private object Rt_ExtendableFunctionCombiner_Unit: Rt_ExtendableFunctionCombiner() {
    override fun addExtensionResult(value: Rt_Value) = false
    override fun getCombinedResult() = Rt_UnitValue
}

private class Rt_ExtendableFunctionCombiner_Boolean: Rt_ExtendableFunctionCombiner() {
    private var result: Rt_Value = Rt_BooleanValue.FALSE

    override fun addExtensionResult(value: Rt_Value): Boolean {
        val v = value.asBoolean()
        result = value
        return v
    }

    override fun getCombinedResult() = result
}

private class Rt_ExtendableFunctionCombiner_Nullable: Rt_ExtendableFunctionCombiner() {
    private var result: Rt_Value = Rt_NullValue

    override fun addExtensionResult(value: Rt_Value): Boolean {
        result = value
        return value != Rt_NullValue
    }

    override fun getCombinedResult() = result
}

private class Rt_ExtendableFunctionCombiner_List(private val type: R_Type): Rt_ExtendableFunctionCombiner() {
    private val result = mutableListOf<Rt_Value>()
    private var done = false

    override fun addExtensionResult(value: Rt_Value): Boolean {
        check(!done)
        val col = value.asCollection()
        result.addAll(col)
        return false
    }

    override fun getCombinedResult(): Rt_Value {
        check(!done)
        done = true
        return Rt_ListValue(type, result)
    }
}

private class Rt_ExtendableFunctionCombiner_Map(private val mapType: R_MapType): Rt_ExtendableFunctionCombiner() {
    private val result = mutableMapOf<Rt_Value, Rt_Value>()
    private var done = false

    override fun addExtensionResult(value: Rt_Value): Boolean {
        check(!done)
        val map = value.asMap()
        for ((k, v) in map) {
            val v0 = result.put(k, v)
            if (v0 != null) {
                val code = "extendable_fn:map:key_conflict:${k.strCode()}:${v0.strCode()}:${v.strCode()}"
                val msg = "Map key conflict: ${k.str()}"
                throw Rt_Exception.common(code, msg)
            }
        }
        return false
    }

    override fun getCombinedResult(): Rt_Value {
        check(!done)
        done = true
        return Rt_MapValue(mapType, result)
    }
}

class R_FunctionCallTarget_ExtendableUserFunction(
        private val baseFn: R_FunctionDefinition,
        private val descriptor: R_ExtendableFunctionDescriptor
): R_FunctionCallTarget() {
    override fun call(callCtx: Rt_CallContext, baseValue: Rt_Value?, values: List<Rt_Value>): Rt_Value {
        checkEquals(baseValue, null)

        val extensions = callCtx.appCtx.app.functionExtensions.getExtensions(descriptor.uid)

        val rtCombiner = descriptor.combiner.createRtCombiner()

        for (ext in extensions) {
            val value = ext.fnBase.call(callCtx, values)
            if (rtCombiner.addExtensionResult(value)) {
                break
            }
        }

        val res = rtCombiner.getCombinedResult()
        return res
    }

    override fun str() = baseFn.appLevelName
}
