/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.mtype

import net.postchain.rell.base.utils.immMapOf
import net.postchain.rell.base.utils.toImmMap

enum class M_TypeMatchSuperRelation(val convertRel: M_TypeMatchRelation) {
    EQUAL(M_TypeMatchRelation.EQUAL),
    SUB(M_TypeMatchRelation.SUB),
    SUPER(M_TypeMatchRelation.SUPER),
}

enum class M_TypeMatchRelation {
    EQUAL,
    SUB,
    SUPER,
    CONVERT,
}

fun interface M_TypeMatchEqualHandler {
    fun handle(type1: M_Type, type2: M_Type): Boolean
}

fun interface M_TypeMatchSuperHandler {
    fun handle(type1: M_Type, type2: M_Type, rel: M_TypeMatchSuperRelation): Boolean
}

fun interface M_TypeMatchConvertHandler {
    fun handle(type1: M_Type, type2: M_Type, rel: M_TypeMatchRelation): Boolean
}

class M_TypeException(val code: String, val msg: String): RuntimeException(msg)

enum class M_TypeVariance {
    NONE,
    IN,
    OUT,
}

sealed class M_TypeRep {
    abstract fun isSameType(type: M_Type): Boolean
    abstract fun type(): M_Type
    abstract fun typeSet(): M_TypeSet
}

class M_TypeRep_Type(private val type: M_Type): M_TypeRep() {
    override fun isSameType(type: M_Type) = type === this.type
    override fun type() = type
    override fun typeSet() = M_TypeSets.one(type)
}

class M_TypeRep_TypeSet(private val typeSet: M_TypeSet): M_TypeRep() {
    override fun isSameType(type: M_Type) = false
    override fun type(): M_Type = typeSet.captureType()
    override fun typeSet() = typeSet
}

enum class M_TypeExpandMode {
    SUPER,
    SUB,
}

sealed class M_Conversion
object M_Conversion_Direct: M_Conversion()
class M_Conversion_Nullable(val resultType: M_Type, val valueConversion: M_Conversion): M_Conversion()
abstract class M_Conversion_Generic: M_Conversion()

object M_TypeUtils {
    fun matchTypeIsSuperTypeOf(type1: M_Type, type2: M_Type, rel: M_TypeMatchSuperRelation): Boolean {
        return when (rel) {
            M_TypeMatchSuperRelation.EQUAL -> type1 == type2
            M_TypeMatchSuperRelation.SUB -> type2.isSuperTypeOf(type1)
            M_TypeMatchSuperRelation.SUPER -> type1.isSuperTypeOf(type2)
        }
    }

    fun getTypeArgs(mType: M_Type): Map<M_TypeParam, M_TypeSet> {
        return when (mType) {
            is M_Type_Generic -> {
                val genType = mType.genericType
                if (mType.typeArgs.isEmpty()) immMapOf() else {
                    mType.typeArgs.mapIndexed { i, typeSet -> genType.params[i] to typeSet }.toImmMap()
                }
            }
            else -> immMapOf()
        }
    }
}
