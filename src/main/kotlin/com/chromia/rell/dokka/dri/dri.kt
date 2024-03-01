package com.chromia.rell.dokka.dri

import com.chromia.rell.dokka.config.RellConfig
import net.postchain.rell.base.model.R_QualifiedName
import net.postchain.rell.base.mtype.M_Type
import net.postchain.rell.base.mtype.M_Type_Generic
import net.postchain.rell.base.mtype.M_Type_Nullable
import net.postchain.rell.base.mtype.M_Type_Tuple
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.links.DRIExtraContainer
import org.jetbrains.dokka.links.DRIExtraProperty
import org.jetbrains.dokka.links.withClass
import org.jetbrains.dokka.model.Bound
import org.jetbrains.dokka.model.FunctionalTypeConstructor
import org.jetbrains.dokka.model.GenericTypeConstructor
import org.jetbrains.dokka.model.Nullable
import org.jetbrains.dokka.model.TypeParameter
import org.jetbrains.kotlin.library.metadata.KlibMetadataProtoBuf.className

fun R_QualifiedName.toDRI(): DRI {
    val packageName = if (parts.size > 1) str().substringBeforeLast(".") else RellConfig.SystemLibSourceSet.MAIN.dri.packageName
    val className = last.str
    return DRI(packageName = packageName, classNames = className)
}

fun M_Type.toDRI(): DRI {

    return when (this) {
        is M_Type_Generic -> {
            val qualifiedName = R_QualifiedName.of(genericType.name)
            qualifiedName.toDRI()
        }
        else -> DriOfRoot.withClass(toString())
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

val DriOfRoot = RellConfig.SystemLibSourceSet.MAIN.dri
val DriOfUnit = DriOfRoot.withClass("unit")


fun DRI.withAlias() = copy(extra = DRIExtraContainer().also { it[AliasDRIExtra] = AliasDRIExtra }.encode())
fun DRI.isAlias() = DRIExtraContainer(extra)[AliasDRIExtra] != null
 object AliasDRIExtra: DRIExtraProperty<AliasDRIExtra>()
