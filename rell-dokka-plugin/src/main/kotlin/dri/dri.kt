/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.dokka.dri

import com.chromia.rell.dokka.config.RellModule
import net.postchain.rell.base.lmodel.*
import net.postchain.rell.base.model.*
import net.postchain.rell.base.mtype.M_Type
import net.postchain.rell.base.mtype.M_Type_Generic
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.links.Callable
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.links.withClass

val DriOfRoot = RellModule.MAIN.dri
val DriOfUnit = DriOfRoot.withClass("unit")

data class DRIWithSourceSet(val dri: DRI, val sourceSet: DokkaConfiguration.DokkaSourceSet)

fun DRI.withSourceSet(sourceSet: DokkaConfiguration.DokkaSourceSet) = DRIWithSourceSet(this, sourceSet)

fun DRI.Companion.from(d: R_Definition): DRI {
    val packageName = d.defName.toPackageName()
    val className = when (d) {
        is R_GlobalConstantDefinition -> d.simpleName
        is R_EntityDefinition -> d.simpleName
        is R_StructDefinition -> d.simpleName
        is R_ObjectDefinition -> d.simpleName
        is R_EnumDefinition -> d.simpleName
        else -> null
    }
    val callable = when (d) {
        is R_RoutineDefinition -> Callable.from(d)
        else -> null
    }
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
    val extra = if (m is L_TypeDefMember_Alias) DRI().withAlias().extra else null
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
            val fullName = genericType.name
            when {
                ":" in fullName -> R_QualifiedName.of(fullName.replace(":", "."))
                else -> R_QualifiedName.of(fullName)
            }.toDRI()
        }

        else -> DriOfRoot.withClass(toString())
    }

}
