package com.chromia.rell.dokka.dri

import net.postchain.rell.base.model.R_DefinitionName
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.links.DRIExtraContainer
import org.jetbrains.dokka.links.DRIExtraProperty

fun DRI.withAlias() = copy(extra = DRIExtraContainer().also { it[AliasDRIExtra] = AliasDRIExtra }.encode())
fun DRI.isAlias() = DRIExtraContainer(extra)[AliasDRIExtra] != null

object AliasDRIExtra : DRIExtraProperty<AliasDRIExtra>()

fun String.escapeAnonymousFunctionName() = replace("function#", "function%23")

fun R_DefinitionName.toPackageName() =
        (listOf(module) + qualifiedName.substringBeforeLast(".", ""))
            .joinToString(".").trimEnd('.')
