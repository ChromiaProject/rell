package com.chromia.rell.dokka.doc

import net.postchain.rell.base.utils.doc.DocDefinition
import net.postchain.rell.base.utils.doc.DocSourcePos
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.model.DocumentableSource
import kotlin.io.path.pathString

class RellDocumentableSource(private val docSourcePos: DocSourcePos, sourceSet: DokkaConfiguration.DokkaSourceSet) : DocumentableSource {
    override val path = sourceSet.sourceRoots.first().toPath().resolve(docSourcePos.path).pathString

    override fun computeLineNumber() = docSourcePos.line

    companion object {
        fun create(docDefinition: DocDefinition, sourceSet: DokkaConfiguration.DokkaSourceSet): DocumentableSource =
                docDefinition.docSourcePos?.let { RellDocumentableSource(it, sourceSet) } ?: NULL

        val NULL = object : DocumentableSource {
            override val path: String = ""
            override fun computeLineNumber() = null
        }
    }
}