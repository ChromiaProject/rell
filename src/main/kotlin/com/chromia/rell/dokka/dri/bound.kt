package com.chromia.rell.dokka.dri

import com.chromia.rell.dokka.model.IsTuple
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.mtype.M_GenericTypeParent
import net.postchain.rell.base.mtype.M_Type
import net.postchain.rell.base.mtype.M_TypeSet
import net.postchain.rell.base.mtype.M_TypeSet_One
import net.postchain.rell.base.mtype.M_TypeSet_SubOf
import net.postchain.rell.base.mtype.M_TypeSet_SuperOf
import net.postchain.rell.base.mtype.M_Type_Function
import net.postchain.rell.base.mtype.M_Type_Generic
import net.postchain.rell.base.mtype.M_Type_Nullable
import net.postchain.rell.base.mtype.M_Type_Param
import net.postchain.rell.base.mtype.M_Type_Simple
import net.postchain.rell.base.mtype.M_Type_Tuple
import org.jetbrains.dokka.model.Bound
import org.jetbrains.dokka.model.Contravariance
import org.jetbrains.dokka.model.Covariance
import org.jetbrains.dokka.model.FunctionalTypeConstructor
import org.jetbrains.dokka.model.GenericTypeConstructor
import org.jetbrains.dokka.model.Invariance
import org.jetbrains.dokka.model.Nullable
import org.jetbrains.dokka.model.Projection
import org.jetbrains.dokka.model.Star
import org.jetbrains.dokka.model.TypeParameter
import org.jetbrains.dokka.model.UnresolvedBound
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
                    projections = typeArgs.mapNotNull { it.toProjection() } // TODO: Not complete
            )
        }

        is M_Type_Tuple -> {
            GenericTypeConstructor(
                    dri = toDRI(),
                    projections = fieldTypes.mapIndexed { index, type -> type.toBound(fieldNames[index].value) },
                    extra = PropertyContainer.withAll(IsTuple)
            )
        }

        is M_Type_Function -> {
            FunctionalTypeConstructor(
                    dri = toDRI(),
                    projections = paramTypes.mapIndexed { index, type -> type.toBound() } + resultType.toBound(),
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

