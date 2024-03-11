package com.chromia.rell.dokka.dri

import com.chromia.rell.dokka.model.IsTuple
import net.postchain.rell.base.lib.type.R_CollectionType
import net.postchain.rell.base.model.R_FunctionType
import net.postchain.rell.base.model.R_NullableType
import net.postchain.rell.base.model.R_PrimitiveType
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.mtype.M_Type
import net.postchain.rell.base.mtype.M_Type_Function
import net.postchain.rell.base.mtype.M_Type_Generic
import net.postchain.rell.base.mtype.M_Type_Nullable
import net.postchain.rell.base.mtype.M_Type_Param
import net.postchain.rell.base.mtype.M_Type_Simple
import net.postchain.rell.base.mtype.M_Type_Tuple
import org.jetbrains.dokka.model.Bound
import org.jetbrains.dokka.model.FunctionalTypeConstructor
import org.jetbrains.dokka.model.GenericTypeConstructor
import org.jetbrains.dokka.model.Nullable
import org.jetbrains.dokka.model.TypeParameter
import org.jetbrains.dokka.model.UnresolvedBound
import org.jetbrains.dokka.model.properties.PropertyContainer

fun M_Type.toBound(presentableName: String? = null): Bound {
    return when (this) {
        is M_Type_Generic -> {
            GenericTypeConstructor(
                    dri = toDRI(),
                    presentableName = presentableName ?: genericType.name,
                    projections = typeArgs.map { it.canonicalOutType().toBound() } // TODO: Not complete
            )
        }

        is M_Type_Tuple -> {
            GenericTypeConstructor(
                    dri = toDRI(),
                    projections = fieldTypes.mapIndexed { index, type -> type.toBound(fieldNames[index].value) }, // TODO: Named tuples
                    extra = PropertyContainer.withAll(IsTuple)
            )
        }

        is M_Type_Function -> {
            FunctionalTypeConstructor(
                    dri = toDRI(),
                    projections = paramTypes.mapIndexed { index, type -> type.toBound() } + resultType.toBound(),
            )
        }

        is M_Type_Param -> UnresolvedBound(param.name)
        is M_Type_Simple -> UnresolvedBound(strCode())
        is M_Type_Nullable -> Nullable(valueType.toBound())

        else -> TypeParameter(toDRI(), presentableName ?: strCode(), presentableName ?: strCode())
    }
}

// Return types and parameters
fun R_Type.toBound(): Bound {
    // Links for Entity, Struct, Object, Enum
    return when (this) {
        is R_NullableType -> Nullable(valueType.toBound())
        // No links to system lib from dapps
        is R_PrimitiveType -> UnresolvedBound(name)
        is R_CollectionType -> UnresolvedBound(name.substringBefore("<"), PropertyContainer.withAll(CollectionBoundExtra(elementType.toBound())))
        is R_FunctionType -> UnresolvedBound(name)
        else -> mType.toBound()
    }
}
