package com.chromia.rell.dokka.config

import net.postchain.rell.base.compiler.base.lib.C_LibModule
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lib.test.Lib_RellTest
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.DokkaSourceSetID
import org.jetbrains.dokka.DokkaSourceSetImpl
import org.jetbrains.dokka.links.DRI
import java.io.File

enum class RellModule(scopeId: String, private val sourceSetName: String, val module: C_LibModule, private vararg val dependent: RellModule) {
    MAIN("root", "rell", Lib_Rell.MODULE),
    TEST("rell.test", "test", Lib_RellTest.MODULE, MAIN);

    val sourceSetId = DokkaSourceSetID(scopeId, sourceSetName)
    val dri = DRI(scopeId)

    fun sourceSet(includes: List<File>) = DokkaSourceSetImpl(
            displayName = sourceSetName,
            sourceSetID = sourceSetId,
            dependentSourceSets = dependent.map { it.sourceSetId }.toSet(),
            includes = includes.toSet()
    )

    companion object {
        fun find(sourceSet: DokkaConfiguration.DokkaSourceSet) = entries.find { sourceSet.sourceSetID == it.sourceSetId }
    }
}
