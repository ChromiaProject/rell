/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.expr

import com.google.common.collect.Sets
import net.postchain.rell.base.compiler.base.core.C_VarId
import net.postchain.rell.base.compiler.vexpr.V_Expr
import net.postchain.rell.base.model.*
import net.postchain.rell.base.utils.*

enum class C_VarChanged {
    YES,
    MAYBE,
}

enum class C_VarNulled {
    YES,
    NO,
    ;

    companion object {
        fun forBoolean(v: Boolean): C_VarNulled = if (v) YES else NO

        fun forVarType(varType: R_Type, valueType: R_Type): C_VarNulled? {
            return when {
                varType !is R_NullableType -> null
                valueType == R_NullType -> YES
                valueType !is R_NullableType -> NO
                else -> null
            }
        }
    }
}

class C_VarStateKey(
    val varId: C_VarId,
    val path: ImmList<C_VarPathItem> = immListOf(),
    val isFull: Boolean = true,
) {
    fun nameMsg(): String {
        val parts = listOf(varId.nameMsg()) + path.map { it.str() }
        return parts.joinToString("")
    }

    override fun toString() = nameMsg()
}

sealed class C_VarPathItem {
    abstract fun str(): String
    final override fun toString() = str()

    private data class C_VarPathItem_RAttr(private val attr: R_Attribute): C_VarPathItem() {
        override fun str() = ".${attr.name}"
    }

    private data class C_VarPathItem_TupleAttr(private val field: R_TupleField): C_VarPathItem() {
        override fun str(): String {
            val name = field.name?.rName
            return if (name != null) ".${name.str}" else "[${field.index}]"
        }
    }

    private data class C_VarPathItem_MemberAttr(private val name: R_Name, private val attr: C_MemberAttr): C_VarPathItem() {
        override fun str() = ".${name.str}"
    }

    companion object {
        fun forRAttr(rAttr: R_Attribute): C_VarPathItem = C_VarPathItem_RAttr(rAttr)
        fun forTupleAttr(field: R_TupleField): C_VarPathItem = C_VarPathItem_TupleAttr(field)
        fun forMemberAttr(name: R_Name, attr: C_MemberAttr): C_VarPathItem = C_VarPathItem_MemberAttr(name, attr)
    }
}

class C_VarStates private constructor(
    delta: C_VarStatesDelta,
) {
    private val delta = delta as C_VarStatesDelta_Impl

    fun getInited(key: C_VarStateKey): Boolean? = delta.getInited(key)
    fun getNulled(key: C_VarStateKey): Boolean? = delta.getNulled(key)

    fun and(delta: C_VarStatesDelta): C_VarStates {
        return copy(this.delta.and(delta))
    }

    fun changed(
        key: C_VarStateKey,
        changed: C_VarChanged = C_VarChanged.YES,
        nulled: C_VarNulled? = null,
    ): C_VarStates {
        return copy(delta.changed(key, changed, nulled))
    }

    private fun copy(delta: C_VarStatesDelta): C_VarStates {
        return if (delta === this.delta) this else C_VarStates(delta)
    }

    companion object {
        val EMPTY = C_VarStates(C_VarStatesDelta.EMPTY)
    }
}

sealed class C_VarStatesDelta {
    abstract fun isEmpty(): Boolean

    fun and(other: C_VarStatesDelta): C_VarStatesDelta {
        if (other.isEmpty()) return this
        if (isEmpty()) return other
        return combine(other, C_VarStateDelta::and)
    }

    fun or(other: C_VarStatesDelta): C_VarStatesDelta {
        return combine(other, C_VarStateDelta::or)
    }

    fun changed(
        key: C_VarStateKey,
        changed: C_VarChanged = C_VarChanged.YES,
        nulled: C_VarNulled? = null,
    ): C_VarStatesDelta {
        return if (key.path.isNotEmpty() || !key.isFull) this else {
            val map = immMapOf(key.varId to C_VarStateDelta.changed(changed = changed, nulled = nulled))
            and(C_VarStatesDelta_Impl.get(map))
        }
    }

    fun nulled(key: C_VarStateKey, nulled: C_VarNulled): C_VarStatesDelta {
        val varState = C_VarStateDelta.nulled(key, nulled)
        varState ?: return this
        val map = immMapOf(key.varId to varState)
        return and(C_VarStatesDelta_Impl.get(map))
    }

    protected abstract fun combine(
        other: C_VarStatesDelta,
        op: (C_VarStateDelta?, C_VarStateDelta?) -> C_VarStateDelta?,
    ): C_VarStatesDelta

    companion object {
        val EMPTY: C_VarStatesDelta = C_VarStatesDelta_Impl(immMapOf())

        fun forExpressions(vExprs: List<V_Expr>): C_VarStatesDelta {
            return vExprs.fold(EMPTY) { d, vExpr -> d.and(vExpr.varStatesDelta.always) }
        }

        fun forNotNull(value: V_Expr): C_VarStatesDelta {
            val varKey = value.varKey()
            varKey ?: return EMPTY
            return nulled(varKey, C_VarNulled.NO)
        }

        fun changed(
            key: C_VarStateKey,
            changed: C_VarChanged = C_VarChanged.YES,
            nulled: C_VarNulled? = null,
        ): C_VarStatesDelta {
            return EMPTY.changed(key, changed, nulled)
        }

        fun nulled(key: C_VarStateKey, nulled: C_VarNulled): C_VarStatesDelta {
            return EMPTY.nulled(key, nulled)
        }
    }
}

private class C_VarStatesDelta_Impl(
    private val map: ImmMap<C_VarId, C_VarStateDelta>,
): C_VarStatesDelta() {
    override fun isEmpty() = map.isEmpty()

    fun getInited(key: C_VarStateKey): Boolean? {
        return when {
            // Considering complex vars (with path) as initialized - base variable must be checked elsewhere.
            key.path.isNotEmpty() -> true
            !key.isFull -> false
            else -> {
                val d = map[key.varId]
                if (d == null) false else d.getInited()
            }
        }
    }

    fun getNulled(key: C_VarStateKey): Boolean? {
        if (!key.isFull) {
            return null
        }
        val d = map[key.varId]
        return d?.getNulled(key.path)
    }

    override fun combine(
        other: C_VarStatesDelta,
        op: (C_VarStateDelta?, C_VarStateDelta?) -> C_VarStateDelta?,
    ): C_VarStatesDelta {
        other as C_VarStatesDelta_Impl

        val resMap = mutableMapOf<C_VarId, C_VarStateDelta>()
        val ids = Sets.union(map.keys, other.map.keys)
        for (id in ids) {
            val d1 = map[id]
            val d2 = other.map[id]
            val resDelta = op(d1, d2)
            if (resDelta != null) {
                resMap[id] = resDelta
            }
        }

        return get(resMap.toImmMap())
    }

    override fun toString(): String {
        return map.mapKeys { it.key.nameMsg() }.toString()
    }

    companion object {
        fun get(map: Map<C_VarId, C_VarStateDelta>): C_VarStatesDelta {
            return if (map.isEmpty()) EMPTY else C_VarStatesDelta_Impl(map.toImmMap())
        }
    }
}

private class C_VarValueDelta private constructor(
    private val map: ImmMap<ImmList<C_VarPathItem>, C_VarNulled>,
) {
    fun getNulled(path: List<C_VarPathItem>): C_VarNulled? {
        var res = map[path]
        if (res == null) {
            for ((k, v) in map) {
                if (v == C_VarNulled.NO && k.startsWith(path)) {
                    res = v
                    break
                }
            }
        }
        return res
    }

    fun and(v: C_VarValueDelta): C_VarValueDelta {
        val map2 = Sets.union(map.keys, v.map.keys)
            .mapNotNull { path ->
                val n1 = map[path]
                val n2 = v.map[path]
                val n = when {
                    n1 == null -> n2
                    n2 == null -> n1
                    n1 == n2 -> n1
                    else -> null
                }
                if (n == null) null else (path to n)
            }
            .toImmMap()
        return C_VarValueDelta(map2)
    }

    fun or(v: C_VarValueDelta): C_VarValueDelta {
        val map2 = Sets.union(map.keys, v.map.keys)
            .mapNotNull { path ->
                val n1 = map[path]
                val n2 = v.map[path]
                if (n1 != null && n1 == n2) (path to n1) else null
            }
            .toImmMap()
        return C_VarValueDelta(map2)
    }

    override fun toString(): String {
        return map.toString()
    }

    companion object {
        val EMPTY = C_VarValueDelta(immMapOf())

        fun make(map: ImmMap<ImmList<C_VarPathItem>, C_VarNulled>): C_VarValueDelta {
            return if (map.isEmpty()) EMPTY else C_VarValueDelta(map)
        }

        fun make(nulled: C_VarNulled? = null): C_VarValueDelta {
            return if (nulled == null) EMPTY else {
                val map = immMapOf(immListOf<C_VarPathItem>() to nulled)
                C_VarValueDelta(map)
            }
        }
    }
}

/**
 * [oldValue] is an update of the old value of the variable (`null` means no update),
 * [newValue] describes a new value assigned to the variable (`null` means no new value assignment).
 *
 * Both values can be not `null` at the same time, which means there is a branching, where either an old value is
 * updated or a new value is assigned.
 */
class C_VarStateDelta private constructor(
    private val oldValue: C_VarValueDelta?,
    private val newValue: C_VarValueDelta?,
) {
    init {
        require(oldValue != null || newValue != null)
    }

    fun getInited(): Boolean? {
        return when {
            newValue == null -> false
            oldValue == null -> true
            else -> null
        }
    }

    fun getNulled(path: List<C_VarPathItem>): Boolean? {
        val oldNulled = oldValue?.getNulled(path)
        val newNulled = newValue?.getNulled(path)
        val nulled = when {
            oldValue == null || newValue == null -> oldNulled ?: newNulled
            oldNulled == newNulled -> oldNulled
            else -> null
        }
        return when (nulled) {
            null -> null
            C_VarNulled.YES -> true
            C_VarNulled.NO -> false
        }
    }

    override fun toString(): String {
        val list = mutableListOf<String>()
        if (oldValue != null) list.add("old: $oldValue")
        if (newValue != null) list.add("new: $newValue")
        return list.toString()
    }

    companion object {
        private val NULL = C_VarStateDelta(oldValue = C_VarValueDelta.EMPTY, newValue = null)

        fun changed(changed: C_VarChanged = C_VarChanged.YES, nulled: C_VarNulled? = null): C_VarStateDelta {
            val oldValue = if (changed == C_VarChanged.YES) null else C_VarValueDelta.make()
            return C_VarStateDelta(oldValue = oldValue, newValue = C_VarValueDelta.make(nulled = nulled))
        }

        fun nulled(key: C_VarStateKey, nulled: C_VarNulled): C_VarStateDelta? {
            if (!key.isFull && nulled != C_VarNulled.NO) {
                // For incomplete keys, only non-nullability of a prefix key can be recorder.
                return null
            }

            val map = immMapOf(key.path to nulled)
            val valueDelta = C_VarValueDelta.make(map)
            return C_VarStateDelta(oldValue = valueDelta, newValue = null)
        }

        fun and(d1: C_VarStateDelta?, d2: C_VarStateDelta?): C_VarStateDelta? {
            if (d1 == null) return d2
            if (d2 == null) return d1

            if (d2.oldValue == null) {
                return d2
            }

            if (d2.newValue == null) {
                val oldValue = d1.oldValue?.and(d2.oldValue)
                val newValue = d1.newValue?.and(d2.oldValue)
                return C_VarStateDelta(oldValue = oldValue, newValue = newValue)
            }

            if (d1.oldValue == null) {
                val newValue = d1.newValue!!.and(d2.oldValue).or(d2.newValue)
                return C_VarStateDelta(oldValue = null, newValue = newValue)
            }

            if (d1.newValue == null) {
                val oldValue = d1.oldValue.and(d2.oldValue)
                return C_VarStateDelta(oldValue = oldValue, newValue = d2.newValue)
            }

            val oldValue = d1.oldValue.and(d2.oldValue)
            val newValue = d1.newValue.and(d2.oldValue).or(d2.newValue)
            return C_VarStateDelta(oldValue = oldValue, newValue = newValue)
        }

        fun or(d1: C_VarStateDelta?, d2: C_VarStateDelta?): C_VarStateDelta {
            val t1 = d1 ?: NULL
            val t2 = d2 ?: NULL
            val oldValue = or0(t1.oldValue, t2.oldValue)
            val newValue = or0(t1.newValue, t2.newValue)
            return C_VarStateDelta(oldValue = oldValue, newValue = newValue)
        }

        private fun or0(v1: C_VarValueDelta?, v2: C_VarValueDelta?): C_VarValueDelta? {
            return when {
                v1 == null -> v2
                v2 == null -> v1
                else -> v1.or(v2)
            }
        }
    }
}

class C_ExprVarStatesDelta private constructor(
    val always: C_VarStatesDelta,
    val whenTrue: C_VarStatesDelta,
    val whenFalse: C_VarStatesDelta,
) {
    companion object {
        val EMPTY = C_ExprVarStatesDelta(C_VarStatesDelta.EMPTY, C_VarStatesDelta.EMPTY, C_VarStatesDelta.EMPTY)

        fun make(
            always: C_VarStatesDelta = C_VarStatesDelta.EMPTY,
            whenTrue: C_VarStatesDelta = C_VarStatesDelta.EMPTY,
            whenFalse: C_VarStatesDelta = C_VarStatesDelta.EMPTY,
        ): C_ExprVarStatesDelta {
            return if (always.isEmpty() && whenTrue.isEmpty() && whenFalse.isEmpty()) EMPTY else {
                C_ExprVarStatesDelta(always = always, whenTrue = whenTrue, whenFalse = whenFalse)
            }
        }

        fun forExpressions(vararg vExprs: V_Expr): C_ExprVarStatesDelta {
            return forExpressions(vExprs.toList())
        }

        fun forExpressions(vExprs: List<V_Expr>): C_ExprVarStatesDelta {
            val delta = C_VarStatesDelta.forExpressions(vExprs)
            return make(always = delta)
        }

        fun forNullCheck(value: V_Expr, nullIfTrue: Boolean): C_ExprVarStatesDelta {
            val always = value.varStatesDelta.always
            val varKey = value.varKey()
            varKey ?: return make(always = always)

            val whenTrue = C_VarStatesDelta.nulled(varKey, C_VarNulled.forBoolean(nullIfTrue))
            val whenFalse = C_VarStatesDelta.nulled(varKey, C_VarNulled.forBoolean(!nullIfTrue))
            return make(always = always, whenTrue = whenTrue, whenFalse = whenFalse)
        }
    }
}
