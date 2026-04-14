/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.dokka.dri

import com.chromia.rell.dokka.model.IsTuple
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.mtype.*
import org.jetbrains.dokka.model.*
import org.jetbrains.dokka.model.properties.ExtraProperty
import org.jetbrains.dokka.model.properties.PropertyContainer

fun M_TypeSet.toProjection(presentableName: String? = null): Projection {
    return when (this) {
        is M_TypeSet_SubOf -> {
            Covariance(TypeParameter(boundType.toDRI(), "-" + boundType.strCode(), presentableName))
        }
        is M_TypeSet_SuperOf -> Contravariance(TypeParameter(boundType.toDRI(), "+" + boundType.strCode(), presentableName))
        is M_TypeSet_One -> Invariance(type.toBound())
        else -> Star
    }
}

fun M_GenericTypeParent.toTypeConstructor(): GenericTypeConstructor {
    val dri = genericType.commonType.toDRI()
    return GenericTypeConstructor(dri, args.map { it.toBound() })
}

fun M_Type.toBound(presentableName: String? = null): Bound {
    return when (this) {
        is M_Type_Generic -> {
            GenericTypeConstructor(
                    dri = toDRI(),
                    presentableName = presentableName,
                    projections = typeArgs.map { it.toProjection() }
            )
        }

        is M_Type_Tuple -> {
            GenericTypeConstructor(
                    dri = toDRI(),
                    projections = fieldTypes.mapIndexed { index, type -> type.toBound(fieldNames[index]) },
                    extra = PropertyContainer.withAll(IsTuple)
            )
        }

        is M_Type_Function -> {
            FunctionalTypeConstructor(
                    dri = toDRI(),
                    projections = paramTypes.mapIndexed { _, type -> type.toBound() } + resultType.toBound(),
            )
        }

        is M_Type_Param -> UnresolvedBound(param.name) // T, V, K
        is M_Type_Simple -> UnresolvedBound(strCode())
        is M_Type_Nullable -> Nullable(valueType.toBound())

        else -> TypeParameter(toDRI(), presentableName ?: strCode(), presentableName ?: strCode())
    }
}

// Return types and parameters
fun R_Type.toBound() = mType.toBound()

class GenericUnresolvedBoundExtra(vararg val bounds: Bound): ExtraProperty<UnresolvedBound> {
    override val key: ExtraProperty.Key<UnresolvedBound, *>
        get() = Companion

    companion object : ExtraProperty.Key<UnresolvedBound, GenericUnresolvedBoundExtra>
}

class FunctionUnresolvedBoundExtra(val params: List<Bound>, val result: Bound): ExtraProperty<UnresolvedBound> {
    operator fun component1(): List<Bound> {
        return params
    }

    operator fun component2(): Bound {
        return result
    }

    override val key: ExtraProperty.Key<UnresolvedBound, *>
        get() = Companion

    companion object : ExtraProperty.Key<UnresolvedBound, FunctionUnresolvedBoundExtra>
}

