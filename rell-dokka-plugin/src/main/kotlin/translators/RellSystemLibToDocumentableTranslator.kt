/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.dokka.translators

import com.chromia.rell.dokka.config.RellModule
import com.chromia.rell.dokka.doc.RellDocumentableSource
import com.chromia.rell.dokka.systemlib.SystemLibVisitor
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.DocumentableSource
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.transformers.sources.SourceToDocumentableTranslator

/**
 * Creates system lib documentation by taking a fake sourceset [RellModule] an
 * processing all module definitions into [Documentable].
 * See the [architecture](https://kotlin.github.io/dokka/1.9.10/developer_guide/architecture/architecture_overview/) of dokka for
 * more information on data types and data flow.
 */
object RellSystemLibToDocumentableTranslator : SourceToDocumentableTranslator {

    override fun invoke(sourceSet: DokkaConfiguration.DokkaSourceSet, context: DokkaContext): DModule {
        val module = requireNotNull(RellModule.find(sourceSet)) {
            "Module not found for source set ${sourceSet.sourceSetID}"
        }
        return SystemLibVisitor(sourceSet, context.logger).run {
            DModule(
                    "Rell System Library",
                    visitRellModule(module),
                    documentation = mapOf(),
                    sourceSets = setOf(sourceSet)
            )
        }
    }

    val NULL_DESCRIPTOR: DocumentableSource = RellDocumentableSource.NULL
}
