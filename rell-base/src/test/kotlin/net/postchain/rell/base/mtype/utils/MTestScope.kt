/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.mtype.utils

import net.postchain.rell.base.mtype.*
import net.postchain.rell.base.utils.*

class MTestScope private constructor(
    private val genTypes: ImmMap<String, M_GenericType>,
    val typeParams: ImmMap<String, M_TypeParam>,
    val types: ImmMap<String, MTestTypeDef>,
) {
    fun toBuilder(): Builder {
        return Builder(genTypes, typeParams, types)
    }

    class Builder(
        genTypes: Map<String, M_GenericType> = mapOf(),
        typeParams: Map<String, M_TypeParam> = mapOf(),
        types: Map<String, MTestTypeDef> = mapOf(),
    ) {
        private val genTypes = genTypes.toMutableMap()
        private val typeParams = typeParams.toMutableMap()
        private val types = types.toMutableMap()

        fun build(): MTestScope {
            return MTestScope(genTypes.toImmMap(), typeParams.toImmMap(), types.toImmMap())
        }

        fun copy(): Builder {
            return Builder(genTypes, typeParams, types)
        }

        fun simpleType(name: String, type: M_Type, typeParam: M_TypeParam? = null) {
            check(name !in types) { name }
            types[name] = MTestTypeDef_Special(type, typeParam)
        }

        fun specialType(name: String, type: MTestTypeDef) {
            check(name !in types)
            types[name] = type
        }

        fun genericType(
            name: String,
            params: List<String> = listOf(),
            parent: Pair<M_GenericType, List<String>>? = null,
            addon: M_GenericTypeAddon = M_GenericTypeAddon_None,
        ): M_GenericType {
            val mParams = params.map { param ->
                var paramName = param
                var variance = M_TypeVariance.NONE
                if (param.startsWith("-")) {
                    paramName = param.substring(1)
                    variance = M_TypeVariance.OUT
                } else if (param.startsWith("+")) {
                    paramName = param.substring(1)
                    variance = M_TypeVariance.IN
                }
                check(paramName.matches(Regex("[A-Z]+"))) { paramName }
                M_TypeParam(paramName, variance, M_TypeSets.ALL)
            }

            val mParent = if (parent == null) null else {
                val mParentArgs = parent.second.mapToImmList { arg ->
                    val param = mParams.first { it.name == arg }
                    M_Types.param(param)
                }
                M_GenericTypeParent(parent.first, mParentArgs)
            }

            val genType = M_GenericType.make(name, mParams, mParent, addon)
            genericType(genType)

            return genType
        }

        private fun genericType(genType: M_GenericType) {
            val name = genType.name
            check(name !in genTypes)
            genTypes[name] = genType

            check(name !in types)
            types[name] = MTestTypeDef_Generic(genType)
        }

        fun typeDef(code: String) {
            val scope = build()
            val genType = MTestParser.parseTypeDef(code, scope)
            genericType(genType)
        }

        fun paramDef(code: String) {
            val scope = build()
            val param = MTestParser.parseTypeParam(code, scope)
            paramDef(param)
        }

        fun paramDef(param: M_TypeParam) {
            if (typeParams[param.name] == param) return
            check(param.name !in typeParams) { "Type param name conflict: ${param.name}" }
            simpleType(param.name, M_Types.param(param), param)
            typeParams[param.name] = param
        }
    }

    private class M_GenericTypeAddon_Convertible(val sourceTypes: ImmSet<M_Type>): M_GenericTypeAddon() {
        override fun getConversion(sourceType: M_Type): M_Conversion_Generic? {
            return if (sourceType in sourceTypes) M_Conversion_Test else null
        }

        private object M_Conversion_Test: M_Conversion_Generic()
    }

    companion object {
        fun initDefault(b: Builder) {
            initBasic(b)
            initNumeric(b)
            initCollections(b)
            initConsumerSupplier(b)
            initMisc(b)
        }

        fun initBasic(b: Builder) {
            b.specialType("CAP", MTestTypeDef_Cap)
            b.simpleType("anything", M_Types.ANYTHING)
            b.simpleType("nothing", M_Types.NOTHING)
            b.simpleType("any", M_Types.ANY)
            b.simpleType("null", M_Types.NULL)
        }

        fun initNumeric(b: Builder) {
            val num = b.genericType("num")
            val int = b.genericType("int", parent = num to listOf())
            val real = b.genericType("real", parent = num to listOf())
            val int32 = numericType(b, "int32", parent = int, listOf())
            val int64 = numericType(b, "int64", parent = int, listOf(int32))
            val real32 = numericType(b, "real32", parent = real, listOf(int32, int64))
            numericType(b, "real64", parent = real, listOf(int32, int64, real32))
        }

        fun initCollections(b: Builder, prefix: String = "") {
            val iterable = b.genericType("${prefix}iterable", listOf("T"))
            b.genericType("${prefix}array", listOf("T"), parent = iterable to listOf("T"))
            val collection = b.genericType("${prefix}collection", listOf("T"), parent = iterable to listOf("T"))
            b.genericType("${prefix}list", listOf("T"), parent = collection to listOf("T"))
            b.genericType("${prefix}set", listOf("T"), parent = collection to listOf("T"))
            b.genericType("${prefix}map", listOf("K", "V"))
        }

        fun initConsumerSupplier(b: Builder) {
            b.genericType("supplier", listOf("-T"))
            b.genericType("consumer", listOf("+T"))
        }

        fun initRellPrimitives(b: Builder) {
            b.genericType("unit")
            val boolean = b.genericType("boolean")
            val text = b.genericType("text")
            b.genericType("gtv")

            val integer = numericType(b, "integer")
            val bigInteger = numericType(b, "big_integer", conversions = listOf(integer))
            val decimal = numericType(b, "decimal", conversions = listOf(integer, bigInteger))

            val comparables = listOf(boolean, text, integer, bigInteger, decimal)
            b.genericType("comparable", addon = object: M_GenericTypeAddon() {
                override fun isSpecialSuperTypeOf(type: M_Type): Boolean {
                    return when (type) {
                        is M_Type_Tuple -> type.fieldTypes.all { isSpecialSuperTypeOf(it) }
                        is M_Type_Generic -> comparables.any { type.genericType == it }
                        else -> false
                    }
                }
            })
        }

        fun initRellCollections(b: Builder) {
            val collection = b.genericType("collection", listOf("T"))
            b.genericType("list", listOf("T"), parent = collection to listOf("T"))
            b.genericType("set", listOf("T"), parent = collection to listOf("T"))
            b.genericType("map", listOf("K", "V"))

            b.genericType("pair", listOf("-K", "-V"), addon = object: M_GenericTypeAddon() {
                override fun isPossibleSpecialCompositeSuperTypeOf(type: M_Type_Composite): Boolean {
                    return type is M_Type_Tuple && type.fieldTypes.size == 2
                }
            })
        }

        fun initMisc(b: Builder) {
            b.genericType("str")
            b.genericType("bool")
            b.genericType("data", listOf("T"))
        }

        private fun numericType(
            b: Builder,
            name: String,
            parent: M_GenericType? = null,
            conversions: List<M_GenericType> = listOf(),
        ): M_GenericType {
            val convTypes = conversions.map { it.getType() }.toImmSet()
            val parentPair = if (parent == null) null else (parent to listOf<String>())
            return b.genericType(name, parent = parentPair, addon = M_GenericTypeAddon_Convertible(convTypes))
        }
    }
}

sealed class MTestTypeDef {
    abstract fun genericType(): M_GenericType?
    abstract fun mType(args: List<M_TypeSet>): M_Type
    abstract fun typeParam(): M_TypeParam?

    companion object {
        fun makeParam(param: M_TypeParam): MTestTypeDef {
            val type = M_Types.param(param)
            return MTestTypeDef_Special(type, param)
        }
    }
}

private class MTestTypeDef_Special(private val type: M_Type, private val typeParam: M_TypeParam?): MTestTypeDef() {
    override fun genericType() = null

    override fun mType(args: List<M_TypeSet>): M_Type {
        check(args.isEmpty()) { args }
        return type
    }

    override fun typeParam() = typeParam
}

private class MTestTypeDef_Generic(private val genType: M_GenericType): MTestTypeDef() {
    override fun genericType() = genType

    override fun mType(args: List<M_TypeSet>): M_Type {
        return genType.getType(args)
    }

    override fun typeParam() = null
}

private object MTestTypeDef_Cap: MTestTypeDef() {
    override fun genericType() = null

    override fun mType(args: List<M_TypeSet>): M_Type {
        check(args.size == 1)
        return args[0].captureType()
    }

    override fun typeParam() = null
}
