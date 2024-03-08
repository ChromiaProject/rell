package com.chromia.rell.dokka.dri

import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.links.DRIExtraContainer
import org.jetbrains.dokka.links.DRIExtraProperty

fun DRI.withAlias() = copy(extra = DRIExtraContainer().also { it[AliasDRIExtra] = AliasDRIExtra }.encode())
fun DRI.isAlias() = DRIExtraContainer(extra)[AliasDRIExtra] != null

object AliasDRIExtra : DRIExtraProperty<AliasDRIExtra>()
