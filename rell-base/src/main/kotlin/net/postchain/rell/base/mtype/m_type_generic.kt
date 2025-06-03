/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.mtype

import net.postchain.rell.base.utils.*
import kotlin.math.min

class M_TypeParam(
    val name: String,
    val variance: M_TypeVariance = M_TypeVariance.NONE,
    val bounds: M_TypeSet = M_TypeSets.ALL,
) {
    override fun toString() = strCode()

    fun strCode(): String {
        val v = when (variance) {
            M_TypeVariance.NONE -> ""
            M_TypeVariance.IN -> "+"
            M_TypeVariance.OUT -> "-"
        }
        val b = if (bounds == M_TypeSets.ALL) "" else ":${bounds.strCode()}"
        return "${v}${name}$b"
    }

    fun replaceTypeParams(map: Map<M_TypeParam, M_TypeSet>): M_TypeParam {
        val resBounds = bounds.replaceParams(map, false)
        return if (resBounds === bounds) this else M_TypeParam(name, variance, resBounds)
    }

    fun validate() {
        bounds.validate()
    }

    companion object {
        fun make(
            name: String,
            variance: M_TypeVariance = M_TypeVariance.NONE,
            subOf: M_Type? = null,
            superOf: M_Type? = null,
        ): M_TypeParam {
            check(subOf == null || superOf == null)
            val bounds = when {
                subOf != null -> M_TypeSets.subOf(subOf)
                superOf != null -> M_TypeSets.superOf(superOf)
                else -> M_TypeSets.ALL
            }
            return M_TypeParam(name, variance, bounds = bounds)
        }
    }
}

class M_GenericTypeParent(val genericType: M_GenericType, val args: ImmList<M_Type>) {
    init {
        checkEquals(this.args.size, genericType.params.size)
    }

    fun strCode(): String {
        return if (args.isEmpty()) genericType.name else {
            "${genericType.name}<${args.joinToString(",") { it.strCode() }}>"
        }
    }
}

abstract class M_GenericTypeAddon {
    open fun strCode(typeName: String, args: List<M_TypeSet>): String {
        val argsStr = if (args.isEmpty()) "" else args.joinToString(",", "<", ">") { it.strCode() }
        return "${typeName}$argsStr"
    }

    open fun isSpecialSuperTypeOf(type: M_Type): Boolean = false
    open fun isPossibleSpecialCompositeSuperTypeOf(type: M_Type_Composite): Boolean = false
    open fun getConversion(sourceType: M_Type): M_Conversion_Generic? = null
}

object M_GenericTypeAddon_None: M_GenericTypeAddon()

sealed class M_GenericType(
    val name: String,
    val params: ImmList<M_TypeParam>,
    val parent: M_GenericTypeParent?,
    val addon: M_GenericTypeAddon,
) {
    abstract val commonType: M_Type

    final override fun toString() = strCode()

    fun strCode(): String {
        return if (params.isEmpty()) name else "$name${params.joinToString(",", "<", ">") { it.strCode() }}"
    }

    abstract fun getType(args: List<M_TypeSet>): M_Type

    fun getType(vararg args: M_TypeSet): M_Type {
        return getType(args.toImmList())
    }

    fun getTypeSimple(args: List<M_Type>): M_Type {
        val mArgs = args.map { M_TypeSets.one(it) }
        return getType(mArgs)
    }

    fun getTypeSimple(vararg args: M_Type): M_Type {
        val mArgs = args.map { M_TypeSets.one(it) }
        return getType(mArgs)
    }

    companion object {
        fun make(
            name: String,
            params: List<M_TypeParam>,
            parent: M_GenericTypeParent? = null,
            addon: M_GenericTypeAddon = M_GenericTypeAddon_None,
        ): M_GenericType {
            return M_InternalGenericType(name, params.toImmList(), parent, addon)
        }

        fun checkParamBounds(genType: M_GenericType, param: M_TypeParam, arg: M_TypeSet): Pair<String, String>? {
            val valid = arg == M_TypeSets.ALL || param.bounds.isSuperSetOf(arg)
            return if (valid) null else {
                val nameCode = genType.name
                //TODO human-readable representation of M_TypeSet
                val code = "param_bounds:$nameCode:${param.name}:${param.bounds.strCode()}:${arg.strCode()}"
                val msg = "Type parameter '${param.name}' of type '${genType.name}' does not allow '${arg.strMsg()}'"
                code to msg
            }
        }
    }
}

private class M_InternalGenericType(
    name: String,
    params: ImmList<M_TypeParam>,
    parent: M_GenericTypeParent?,
    addon: M_GenericTypeAddon,
): M_GenericType(name, params, parent, addon) {
    init {
        if (parent != null) {
            val parentArgs = parent.args.map { M_TypeSets.one(it) }
            M_GenericTypeInternals.checkTypeArgs(parent.genericType, parentArgs)
        }
    }

    override val commonType: M_Type by lazy {
        M_GenericTypeInternals.newType(this, params.map { M_TypeSets.one(M_Types.param(it)) })
    }

    override fun getType(args: List<M_TypeSet>): M_Type {
        return if (args.isEmpty() && params.isEmpty()) commonType else M_GenericTypeInternals.newType(this, args)
    }
}

sealed class M_Type_Generic(
    val genericType: M_GenericType,
    typeArgs: List<M_TypeSet>,
): M_Type_Composite(typeArgs.size) {
    init {
        checkEquals(typeArgs.size, genericType.params.size)
    }

    val typeArgs: ImmList<M_TypeSet> = typeArgs.mapIndexedToImmList { i, arg ->
        val variance = getTypeArgVariance(i)
        when (variance) {
            M_TypeVariance.NONE -> arg
            M_TypeVariance.IN -> M_TypeSets.one(arg.canonicalInType())
            M_TypeVariance.OUT -> M_TypeSets.one(arg.canonicalOutType())
        }
    }
}

private class M_Type_Generic_Internal(
    private val genericType0: M_InternalGenericType,
    typeArgs: List<M_TypeSet>,
): M_Type_Generic(genericType0, typeArgs) {
    override val canonicalArgs: ImmList<M_TypeSet> get() = typeArgs

    private val parentType: M_Type_Generic_Internal? = genericType0.parent?.let { genParentType ->
        val argsMap = this.typeArgs.mapIndexed { i, arg -> genericType0.params[i] to arg }.toMap()
        val parentArgs = genParentType.args.map { it.replaceParams(argsMap, false).typeSet() }
        M_GenericTypeInternals.newType(genParentType.genericType as M_InternalGenericType, parentArgs)
    }

    private val parentList: ImmList<M_Type_Generic_Internal> = CommonUtils.chainToList(this) { it.parentType }

    private val parentListReversed: ImmList<M_Type_Generic_Internal> by lazy {
        parentList.asReversed().toImmList()
    }

    override fun strCode(): String {
        return genericType0.addon.strCode(genericType0.name, typeArgs)
    }

    override fun equalsComposite0(other: M_Type_Composite): Boolean {
        return other is M_Type_Generic_Internal && genericType0 == other.genericType0
    }

    override fun hashCodeComposite0(): Int {
        return genericType0.hashCode()
    }

    override fun getTypeArgVariance(index: Int) = genericType.params[index].variance

    override fun captureWildcards(): M_Type {
        val resArgs = typeArgs.map { arg -> arg.captureTypeSet() }
        return if (resArgs.indices.all { i -> resArgs[i] === typeArgs[i] }) {
            this
        } else {
            newInstance(resArgs)
        }
    }

    override fun matchTypeSuper0(type: M_Type, handler: M_TypeMatchSuperHandler): Boolean {
        return if (genericType.addon.isSpecialSuperTypeOf(type)) {
            true
        } else if (type is M_Type_Composite && genericType.addon.isPossibleSpecialCompositeSuperTypeOf(type)) {
            matchTypeSuperBase(type, handler)
        } else {
            super.matchTypeSuper0(type, handler)
        }
    }

    override fun getCommonSuperType0(type: M_Type): M_Type? {
        if (type !is M_Type_Generic_Internal) return null

        if (genericType0 == type.genericType0) {
            return super.getCommonSuperType0(type)
        }

        val path1 = parentListReversed
        val path2 = type.parentListReversed

        var i = min(path1.size, path2.size) - 1
        while (i >= 0) {
            val type1 = path1[i]
            val type2 = path2[i]
            if (type1.genericType0 == type2.genericType0) {
                return type1.getCommonSuperType0(type2)
            }
            --i
        }

        return null
    }

    override fun getParentType(): M_Type? = parentType

    override fun newInstance(newArgs: List<M_TypeSet>): M_Type_Composite {
        return M_GenericTypeInternals.newType(genericType0, newArgs)
    }

    override fun getCorrespondingSuperType(otherType: M_Type_Composite): M_Type_Composite? {
        if (otherType !is M_Type_Generic_Internal) {
            return null
        }
        return parentList.firstOrNull { it.genericType0 == otherType.genericType0 }
    }

    override fun getConversion0(sourceType: M_Type): M_Conversion? {
        return genericType.addon.getConversion(sourceType)
    }

    override fun validate() {
        for (type in parentList) {
            type.validate0()
        }
    }

    private fun validate0() {
        M_GenericTypeInternals.checkTypeArgs(genericType, typeArgs)
    }
}

private object M_GenericTypeInternals {
    fun newType(genType: M_InternalGenericType, args: List<M_TypeSet>): M_Type_Generic_Internal {
        return M_Type_Generic_Internal(genType, args)
    }

    fun checkTypeArgs(genType: M_GenericType, args: List<M_TypeSet>) {
        val name = genType.name
        val params = genType.params

        checkEquals(args.size, params.size) { name }

        for (i in params.indices) {
            val param = params[i]
            val arg = args[i]
            checkTypeArg(param, arg, genType)
        }
    }

    private fun checkTypeArg(param: M_TypeParam, arg: M_TypeSet, genType: M_GenericType) {
        val codeMsg = M_GenericType.checkParamBounds(genType, param, arg)
        if (codeMsg != null) {
            throw M_TypeException(codeMsg.first, codeMsg.second)
        }
    }
}
