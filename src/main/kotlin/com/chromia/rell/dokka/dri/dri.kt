package com.chromia.rell.dokka.dri

import com.chromia.rell.dokka.config.RellModule
import com.chromia.rell.dokka.model.IsTuple
import net.postchain.rell.base.lmodel.L_NamespaceMember
import net.postchain.rell.base.lmodel.L_NamespaceMember_Function
import net.postchain.rell.base.lmodel.L_NamespaceMember_Namespace
import net.postchain.rell.base.lmodel.L_NamespaceMember_SpecialFunction
import net.postchain.rell.base.lmodel.L_TypeDefMember
import net.postchain.rell.base.lmodel.L_TypeDefMember_Alias
import net.postchain.rell.base.lmodel.L_TypeDefMember_Constant
import net.postchain.rell.base.lmodel.L_TypeDefMember_Constructor
import net.postchain.rell.base.lmodel.L_TypeDefMember_Function
import net.postchain.rell.base.lmodel.L_TypeDefMember_Property
import net.postchain.rell.base.model.R_Definition
import net.postchain.rell.base.model.R_EntityDefinition
import net.postchain.rell.base.model.R_EnumDefinition
import net.postchain.rell.base.model.R_GlobalConstantDefinition
import net.postchain.rell.base.model.R_ObjectDefinition
import net.postchain.rell.base.model.R_QualifiedName
import net.postchain.rell.base.model.R_StructDefinition
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.mtype.M_Type
import net.postchain.rell.base.mtype.M_Type_Function
import net.postchain.rell.base.mtype.M_Type_Generic
import net.postchain.rell.base.mtype.M_Type_Nullable
import net.postchain.rell.base.mtype.M_Type_Param
import net.postchain.rell.base.mtype.M_Type_Simple
import net.postchain.rell.base.mtype.M_Type_Tuple
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.links.Callable
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.links.withClass
import org.jetbrains.dokka.model.Bound
import org.jetbrains.dokka.model.FunctionalTypeConstructor
import org.jetbrains.dokka.model.GenericTypeConstructor
import org.jetbrains.dokka.model.Nullable
import org.jetbrains.dokka.model.TypeParameter
import org.jetbrains.dokka.model.UnresolvedBound
import org.jetbrains.dokka.model.properties.PropertyContainer

val DriOfRoot = RellModule.MAIN.dri
val DriOfUnit = DriOfRoot.withClass("unit")

data class DRIWithSourceSet(val dri: DRI, val sourceSet: DokkaConfiguration.DokkaSourceSet)

fun DRI.withSourceSet(sourceSet: DokkaConfiguration.DokkaSourceSet) = DRIWithSourceSet(this, sourceSet)

fun DRI.Companion.from(d: R_Definition): DRI {
    val packageName = d.cDefName.module.str()
    val className = when (d) {
        is R_GlobalConstantDefinition -> d.simpleName
        is R_EntityDefinition -> d.simpleName
        is R_StructDefinition -> d.simpleName
        is R_ObjectDefinition -> d.simpleName
        is R_EnumDefinition -> d.simpleName
        else -> null
    }
    val callable = null
    return DRI(packageName, className, callable)
}


fun DRI.Companion.from(m: L_NamespaceMember): DRI {
    val packageName = when {
        m is L_NamespaceMember_Namespace -> m.qualifiedName.str()
        m.qualifiedName.parts.size > 1 -> m.qualifiedName.str().substringBeforeLast(".")
        else -> RellModule.MAIN.dri.packageName
    }
    val className = when (m) {
        is L_NamespaceMember_Function -> null
        is L_NamespaceMember_SpecialFunction -> null
        else -> m.simpleName.str
    }
    val callable = when (m) {
        is L_NamespaceMember_Function -> Callable.from(m.function)
        is L_NamespaceMember_SpecialFunction -> TODO("Special function not handled")
        else -> null
    }
    return DRI(packageName, className, callable)
}

fun DRI.Companion.from(m: L_TypeDefMember, parent: DRI): DRI {
    val callable = when (m) {
        is L_TypeDefMember_Function -> Callable.from(m.function)
        is L_TypeDefMember_Constructor -> Callable.from(m.constructor)
        else -> null
    }
    val extra = if (m is L_TypeDefMember_Alias ) DRI().withAlias().extra else null
    val className = when (m) {
        is L_TypeDefMember_Constant -> m.constant.simpleName.str
        is L_TypeDefMember_Property -> m.property.simpleName.str
        else -> null
    }

    return parent.copy(callable = callable, extra = extra).let { if (className != null) it.withClass(className) else it }
}

fun R_QualifiedName.toDRI(): DRI {
    val packageName = if (parts.size > 1) str().substringBeforeLast(".") else RellModule.MAIN.dri.packageName
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

fun R_Type.toBound() = mType.toBound()
