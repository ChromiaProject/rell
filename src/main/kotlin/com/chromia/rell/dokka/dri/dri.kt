package com.chromia.rell.dokka.dri

import net.postchain.rell.base.model.R_QualifiedName
import net.postchain.rell.base.mtype.M_Type
import net.postchain.rell.base.mtype.M_Type_Generic
import net.postchain.rell.base.mtype.M_Type_Nullable
import net.postchain.rell.base.mtype.M_Type_Tuple
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.links.DRIExtraContainer
import org.jetbrains.dokka.links.DRIExtraProperty
import org.jetbrains.dokka.model.Bound
import org.jetbrains.dokka.model.FunctionalTypeConstructor
import org.jetbrains.dokka.model.GenericTypeConstructor
import org.jetbrains.dokka.model.Nullable
import org.jetbrains.dokka.model.TypeParameter

// This works only for types within the same module..
//fun DRI.Companion.fromMType(mType: M_Type) = DRI(mType., mType.toString())

fun R_QualifiedName.toDRI(): DRI {
    val packageName = if (str().contains(".")) str().substringBeforeLast(".") else "[root]"
    val className = str().substringAfterLast(".")
    return DRI(packageName = packageName, classNames = className)
}

fun M_Type.toDRI(): DRI {

    return when (this) {
        is M_Type_Generic -> {
            val name = genericType.name.substringAfterLast(".")
            val packageName = if (genericType.name.contains(".")) genericType.name.substringBeforeLast(".") else "[root]"
            //val packageName = if (genericType.name.startsWith())
            DRI(packageName, name)
        }
        else -> DRI("[root]", this.toString())
    }

}

fun M_Type.toBound(presentableName: String? = null): Bound {
    return when (this) {
        is M_Type_Nullable -> Nullable(valueType.toBound())
        is M_Type_Generic -> {
            GenericTypeConstructor(
                    dri = toDRI(),
                    presentableName = presentableName ?: genericType.name,
                    projections = typeArgs.map { it.canonicalOutType().toBound() }
            )
        }
        is M_Type_Tuple -> {
            FunctionalTypeConstructor(
                    dri = toDRI(),
                    projections = fieldTypes.mapIndexed { index, type -> type.toBound(fieldNames[index].value)} // TODO: Named tuples
            )
        }
        else -> TypeParameter(toDRI(), presentableName ?: strCode(), presentableName ?: strCode())
    }
}

val DriOfUnit = DRI("[root]", "unit")
val DriOfRoot = DRI("[root]")


fun DRI.withAlias() = copy(extra = DRIExtraContainer().also { it[AliasDRIExtra] = AliasDRIExtra }.encode())
fun DRI.isAlias() = DRIExtraContainer(extra)[AliasDRIExtra] != null
 object AliasDRIExtra: DRIExtraProperty<AliasDRIExtra>()
